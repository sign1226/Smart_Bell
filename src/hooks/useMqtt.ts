import { useEffect, useRef, useCallback } from 'react';
import mqtt, { MqttClient } from 'mqtt';
import { useApp } from '../context/AppContext';
import { KeepAwake } from '@capacitor-community/keep-awake';

export const useMqtt = () => {
    const { config, deviceId, setIsConnected, addHistory, addChatMessage, updateChatMessage, setIsRinging, mode, setIsRemoteOnline, setConnectionError, setOnlineStatuses, setCallStatus } = useApp();
    const clientRef = useRef<MqttClient | null>(null);

    const connect = useCallback(() => {
        if (clientRef.current) {
            clientRef.current.end();
        }

        // 接続先URLの構築。末尾の /mqtt は環境により不要な場合があるため、一旦ルートで試行。
        const url = `ws://${config.host}:${config.port}`;
        setConnectionError(`接続試行中... ${url}`);

        try {
            const client = mqtt.connect(url, {
                clientId: config.clientId,
                clean: true,
                connectTimeout: 5000,
                reconnectPeriod: 2000,
                will: {
                    topic: `smartbell/presence/${deviceId}/web`,
                    payload: 'offline',
                    qos: 1,
                    retain: true
                }
            });

            client.on('connect', () => {
                console.log('MQTT Connected');
                setIsConnected(true);
                setConnectionError(null);

                // Online/Presence notification (Retained)
                const presenceTopic = `smartbell/presence/${deviceId}/web`;
                client.publish(presenceTopic, 'online', { retain: true });

                // NativeのバックグラウンドServiceを開始
                import('../plugins/IncomingCall').then(({ default: IncomingCall }) => {
                    IncomingCall.startService({
                        host: config.host,
                        port: config.port,
                        topic: config.topic,
                        clientId: config.clientId,
                        deviceId: deviceId
                    }).catch(console.error);
                });

                client.subscribe(config.topic);
                client.subscribe(`smartbell/chat/all`);
                if (deviceId) {
                    client.subscribe(`smartbell/chat/${deviceId}`);
                    client.subscribe(`smartbell/call/${deviceId}`);
                }
                client.subscribe('smartbell/status');
                client.subscribe('smartbell/presence/#'); // Presence monitoring
            });

            client.on('message', (topic, message) => {
                try {
                    const payloadStr = message.toString();

                    // Presence handling
                    if (topic.startsWith('smartbell/presence/')) {
                        const parts = topic.split('/');
                        if (parts.length >= 4) {
                            const targetDeviceId = parts[2];
                            // const platform = parts[3]; // 'web' or 'android' - currently unused but good for debug
                            const status = payloadStr;

                            if (targetDeviceId !== deviceId) {
                                setOnlineStatuses((prev: Map<string, boolean>) => {
                                    const next = new Map(prev);
                                    // Simple logic: if we get 'online' from any platform, mark as online.
                                    // If 'offline', we might want to check if other platforms are online, 
                                    // but for simplicity, let's just update the status. 
                                    // A better approach would be to track platforms separately (e.g. Map<deviceId, Set<platform>>)
                                    // But since we want "is User online?", and a user might have main device (android) and web open.
                                    // Let's assume if we receive 'online' it overrides 'offline'.
                                    // But if we receive 'offline' from web, but android is online?
                                    // Ideally: track `deviceId: { web: boolean, android: boolean }`
                                    // For now, let's just set it to the latest status validation.
                                    // Refinement: The user wants to know if the device is online.
                                    // Let's rely on the concept that 'offline' means offline.
                                    // Actually, if I refresh page, I send offline then online.

                                    // Let's try to track count or just trust the latest retained message?
                                    // Retained messages are great.
                                    // If I subscribe, I get the last known status.

                                    // Let's just set the boolean for now.
                                    next.set(targetDeviceId, status === 'online');
                                    return next;
                                });
                            }
                        }
                        // Don't return, as we might want to process other logic (unlikely for presence topic but safe)
                    }

                    // Check if it's JSON before parsing
                    if (!payloadStr.startsWith('{')) return;

                    const payload = JSON.parse(payloadStr);

                    if (topic === config.topic || topic.startsWith('smartbell/call/')) {
                        if (payload.cmd === 'call') {
                            // 自分自身からの送信は無視 (fromId check)
                            if (payload.fromId === deviceId) return;

                            addHistory(payload);

                            // Send Ack back
                            if (clientRef.current && clientRef.current.connected) {
                                const ackTopic = `smartbell/call/${payload.fromId}`;
                                const ackPayload = {
                                    cmd: 'ack',
                                    from: config.clientId,
                                    fromId: deviceId,
                                    forCmd: 'call',
                                    timestamp: Date.now()
                                };
                                clientRef.current.publish(ackTopic, JSON.stringify(ackPayload));
                            }

                            // modeに関わらず着信通知を表示 (双方向発着信のため)

                            // 即座に高優先度通知と着信画面を表示 (Native Plugin)
                            import('../plugins/IncomingCall').then(({ default: IncomingCall }) => {
                                IncomingCall.show({ name: payload.from }).catch(err =>
                                    console.error('IncomingCall error:', err)
                                );
                            });

                            KeepAwake.keepAwake();
                        } else if (payload.cmd === 'ack') {
                            if (payload.forCmd === 'call') {
                                console.log("Call Ack received from", payload.from);
                                setCallStatus('delivered');
                                // Reset status after 5 seconds
                                setTimeout(() => {
                                    setCallStatus(prev => prev === 'delivered' ? 'idle' : prev);
                                }, 5000);
                            } else if (payload.forCmd === 'chat' && payload.msgId) {
                                console.log("Chat Ack received for", payload.msgId);
                                updateChatMessage(payload.msgId, { isDelivered: true });
                            }
                        }
                    } else if (topic.startsWith('smartbell/chat/')) {
                        if (payload.cmd === 'chat') {
                            // 自分自身からの送信は無視
                            if (payload.fromId === deviceId) return;

                            const msg = {
                                id: payload.id || crypto.randomUUID(),
                                from: payload.from || '誰か',
                                fromId: payload.fromId || '',
                                text: payload.text || '',
                                timestamp: payload.timestamp || Date.now(),
                                isSelf: false
                            };
                            addChatMessage(msg);

                            // Send Ack for chat if targeted to us
                            if (payload.id && (payload.targetId === deviceId || !payload.targetId)) {
                                const ackTopic = `smartbell/chat/${payload.fromId}`; // Ack topic for chat
                                const ackPayload = {
                                    cmd: 'ack',
                                    from: config.clientId,
                                    fromId: deviceId,
                                    forCmd: 'chat',
                                    msgId: payload.id,
                                    timestamp: Date.now()
                                };
                                clientRef.current?.publish(ackTopic, JSON.stringify(ackPayload));
                            }
                        } else if (payload.cmd === 'ack') {
                            if (payload.forCmd === 'chat' && payload.msgId) {
                                console.log("Chat Ack received for", payload.msgId);
                                updateChatMessage(payload.msgId, { isDelivered: true });
                            }
                        }
                    } else if (topic === 'smartbell/status') {
                        if (payload.from !== config.clientId) {
                            setIsRemoteOnline(true);
                            setTimeout(() => setIsRemoteOnline(false), 10000);
                        }
                    }
                } catch (e) {
                    // console.error('Failed to parse MQTT message', e); 
                    // Suppress verbose errors for non-json messages (like 'online'/'offline' strings)
                }
            });

            client.on('error', (err) => {
                console.error('MQTT Error', err);
                setIsConnected(false);
                setConnectionError(`接続エラー: ${err.message || '接続できません'}`);
            });

            client.on('close', () => {
                // console.log('MQTT Connection closed');
                setIsConnected(false);
            });

            clientRef.current = client;
        } catch (err: any) {
            console.error('Failed to connect to MQTT:', err);
            setIsConnected(false);
            setConnectionError(`例外発生: ${err.message}`);
        }
    }, [config, deviceId, setIsConnected, addHistory, mode, setIsRinging, setIsRemoteOnline, setConnectionError, setOnlineStatuses, setCallStatus, updateChatMessage, addChatMessage]);

    const sendCall = useCallback((targetId?: string) => {
        if (clientRef.current && clientRef.current.connected) {
            const payload = {
                from: config.clientId,
                fromId: deviceId,
                cmd: 'call',
                priority: 'normal',
                timestamp: Date.now()
            };
            const topic = targetId ? `smartbell/call/${targetId}` : config.topic;
            clientRef.current.publish(topic, JSON.stringify(payload));
            return true;
        }
        return false;
    }, [config, deviceId]);

    const sendHeartbeat = useCallback(() => {
        if (clientRef.current && clientRef.current.connected) {
            // Also refresh presence just in case
            clientRef.current.publish(`smartbell/presence/${deviceId}/web`, 'online', { retain: true });

            clientRef.current.publish('smartbell/status', JSON.stringify({
                from: config.clientId,
                timestamp: Date.now()
            }));
        }
    }, [config.clientId, deviceId]);

    useEffect(() => {
        const handleWidgetCall = (event: any) => {
            const { targetId } = event.detail || {};
            console.log('Widget call triggered for:', targetId);

            // Check connection and send
            if (clientRef.current && clientRef.current.connected) {
                setCallStatus('sending');
                const success = sendCall(targetId);
                if (!success) {
                    setCallStatus('failed');
                    setTimeout(() => setCallStatus('idle'), 3000);
                }
            } else {
                setCallStatus('failed');
                setTimeout(() => setCallStatus('idle'), 3000);
            }
        };

        window.addEventListener('widgetCall', handleWidgetCall);

        // Initial setup for LWT (Last Will)
        connect();
        const interval = setInterval(sendHeartbeat, 30000); // Increased heartbeat interval
        return () => {
            window.removeEventListener('widgetCall', handleWidgetCall);
            if (clientRef.current) {
                // Publish offline before closing if possible
                if (clientRef.current.connected) {
                    clientRef.current.publish(`smartbell/presence/${deviceId}/web`, 'offline', { retain: true });
                }
                clientRef.current.end();
            }
            clearInterval(interval);
        };
    }, [connect, sendHeartbeat, deviceId, sendCall, setCallStatus]);

    const sendChat = useCallback((text: string, targetId?: string) => {
        if (clientRef.current && clientRef.current.connected) {
            const topic = targetId ? `smartbell/chat/${targetId}` : 'smartbell/chat/all';
            const msgId = crypto.randomUUID();
            const payload = {
                id: msgId,
                cmd: 'chat',
                from: config.clientId,
                fromId: deviceId,
                targetId: targetId,
                text: text,
                timestamp: Date.now()
            };
            clientRef.current.publish(topic, JSON.stringify(payload));

            // Add to local history immediately
            addChatMessage({
                id: msgId,
                from: config.clientId,
                fromId: deviceId,
                text: text,
                timestamp: payload.timestamp,
                isSelf: true,
                isDelivered: false
            });
            return true;
        }
        return false;
    }, [config, deviceId, addChatMessage]);

    return { sendCall, sendChat };
};
