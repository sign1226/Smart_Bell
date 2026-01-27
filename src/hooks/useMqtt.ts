import { useEffect, useRef, useCallback } from 'react';
import mqtt, { MqttClient } from 'mqtt';
import { useApp } from '../context/AppContext';
import { KeepAwake } from '@capacitor-community/keep-awake';

export const useMqtt = () => {
    const { config, deviceId, setIsConnected, addHistory, addChatMessage, setIsRinging, mode, setIsRemoteOnline, setConnectionError } = useApp();
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
            });

            client.on('connect', () => {
                console.log('MQTT Connected');
                setIsConnected(true);
                setConnectionError(null);

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
            });

            client.on('message', (topic, message) => {
                try {
                    const payload = JSON.parse(message.toString());

                    if (topic === config.topic || topic.startsWith('smartbell/call/')) {
                        if (payload.cmd === 'call') {
                            // 自分自身からの送信は無視 (fromId check)
                            if (payload.fromId === deviceId) return;

                            addHistory(payload);
                            // modeに関わらず着信通知を表示 (双方向発着信のため)

                            // 即座に高優先度通知と着信画面を表示 (Native Plugin)
                            import('../plugins/IncomingCall').then(({ default: IncomingCall }) => {
                                IncomingCall.show({ name: payload.from }).catch(err =>
                                    console.error('IncomingCall error:', err)
                                );
                            });

                            KeepAwake.keepAwake();
                        }
                    } else if (topic.startsWith('smartbell/chat/')) {
                        if (payload.cmd === 'chat') {
                            // 自分自身からの送信は無視
                            if (payload.fromId === deviceId) return;

                            addChatMessage({
                                id: payload.id || Date.now().toString(),
                                from: payload.from,
                                fromId: payload.fromId,
                                text: payload.text,
                                timestamp: payload.timestamp,
                                isSelf: payload.fromId === deviceId
                            });
                        }
                    } else if (topic === 'smartbell/status') {
                        if (payload.from !== config.clientId) {
                            setIsRemoteOnline(true);
                            setTimeout(() => setIsRemoteOnline(false), 10000);
                        }
                    }
                } catch (e) {
                    console.error('Failed to parse MQTT message', e);
                }
            });

            client.on('error', (err) => {
                console.error('MQTT Error', err);
                setIsConnected(false);
                setConnectionError(`接続エラー: ${err.message || '接続できません'}`);
            });

            client.on('close', () => {
                console.log('MQTT Connection closed');
                setIsConnected(false);
            });

            clientRef.current = client;
        } catch (err: any) {
            console.error('Failed to connect to MQTT:', err);
            setIsConnected(false);
            setConnectionError(`例外発生: ${err.message}`);
        }
    }, [config, deviceId, setIsConnected, addHistory, mode, setIsRinging, setIsRemoteOnline, setConnectionError]);

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
            clientRef.current.publish('smartbell/status', JSON.stringify({
                from: config.clientId,
                timestamp: Date.now()
            }));
        }
    }, [config.clientId]);

    useEffect(() => {
        connect();
        const interval = setInterval(sendHeartbeat, 30000); // Increased heartbeat interval
        return () => {
            if (clientRef.current) clientRef.current.end();
            clearInterval(interval);
        };
    }, [connect, sendHeartbeat]);

    const sendChat = useCallback((text: string, targetId?: string) => {
        if (clientRef.current && clientRef.current.connected) {
            const payload = {
                cmd: 'chat',
                from: config.clientId,
                fromId: deviceId,
                text,
                timestamp: Date.now()
            };
            const topic = targetId ? `smartbell/chat/${targetId}` : `smartbell/chat/all`;
            clientRef.current.publish(topic, JSON.stringify(payload));

            // Add self message to history
            addChatMessage({
                id: Date.now().toString(),
                from: config.clientId,
                fromId: deviceId,
                text,
                timestamp: Date.now(),
                isSelf: true
            });
            return true;
        }
        return false;
    }, [config, deviceId, addChatMessage]);

    return { sendCall, sendChat };
};
