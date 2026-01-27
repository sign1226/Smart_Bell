import React, { useState, useEffect } from 'react';
import IncomingCall from '../plugins/IncomingCall';
import { AlertTriangle, ExternalLink } from 'lucide-react';
import { App } from '@capacitor/app';

export const PermissionGuide: React.FC = () => {
    const [permissions, setPermissions] = useState({
        overlay: true,
        batteryOptimization: true,
        notifications: true
    });

    const check = async () => {
        const status = await IncomingCall.checkPermissions();
        setPermissions(status);
        console.log('Permission status updated:', status);
    };

    useEffect(() => {
        check();

        let handler: any;

        const setupListener = async () => {
            handler = await App.addListener('appStateChange', ({ isActive }) => {
                if (isActive) {
                    check();
                }
            });
        };

        setupListener();

        const handleVisibilityChange = () => {
            if (document.visibilityState === 'visible') {
                check();
            }
        };

        window.addEventListener('focus', check);
        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            if (handler) {
                handler.remove();
            }
            window.removeEventListener('focus', check);
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, []);

    if (permissions.overlay && permissions.batteryOptimization && permissions.notifications) {
        return null;
    }

    return (
        <div style={{
            background: '#332b00',
            border: '1px solid #ffcc00',
            borderRadius: '12px',
            padding: '16px',
            marginBottom: '20px',
            color: '#ffcc00'
        }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
                <AlertTriangle size={20} />
                <h3 style={{ margin: 0, fontSize: '1.1rem' }}>推奨設定が不足しています</h3>
            </div>

            <p style={{ fontSize: '0.9rem', marginBottom: '16px', opacity: 0.9 }}>
                着信やメッセージを確実に受け取るために、以下の設定を有効にしてください。
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {!permissions.overlay && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div style={{ flex: 1 }}>
                            <div style={{ fontWeight: 'bold' }}>他のアプリの上に表示</div>
                            <div style={{ fontSize: '0.8rem', opacity: 0.8 }}>全画面での着信表示に必要です</div>
                        </div>
                        <button
                            onClick={() => IncomingCall.requestOverlayPermission()}
                            style={{
                                background: '#ffcc00',
                                color: '#000',
                                border: 'none',
                                padding: '8px 12px',
                                borderRadius: '6px',
                                fontWeight: 'bold',
                                fontSize: '0.8rem',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '4px',
                                flexShrink: 0
                            }}
                        >
                            設定を開く <ExternalLink size={14} />
                        </button>
                    </div>
                )}

                {!permissions.notifications && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div style={{ flex: 1 }}>
                            <div style={{ fontWeight: 'bold' }}>通知の許可</div>
                            <div style={{ fontSize: '0.8rem', opacity: 0.8 }}>チャットバナーの表示に必要です</div>
                        </div>
                        <button
                            onClick={async () => {
                                await IncomingCall.requestNotificationPermission();
                                check();
                            }}
                            style={{
                                background: '#ffcc00',
                                color: '#000',
                                border: 'none',
                                padding: '8px 12px',
                                borderRadius: '6px',
                                fontWeight: 'bold',
                                fontSize: '0.8rem',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '4px',
                                flexShrink: 0
                            }}
                        >
                            許可する <ExternalLink size={14} />
                        </button>
                    </div>
                )}

                {!permissions.batteryOptimization && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div style={{ flex: 1 }}>
                            <div style={{ fontWeight: 'bold' }}>電池の最適化を無視</div>
                            <div style={{ fontSize: '0.8rem', opacity: 0.8 }}>待機中の切断を防止します</div>
                        </div>
                        <button
                            onClick={() => IncomingCall.requestIgnoreBatteryOptimization()}
                            style={{
                                background: '#ffcc00',
                                color: '#000',
                                border: 'none',
                                padding: '8px 12px',
                                borderRadius: '6px',
                                fontWeight: 'bold',
                                fontSize: '0.8rem',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '4px',
                                flexShrink: 0
                            }}
                        >
                            設定を開く <ExternalLink size={14} />
                        </button>
                    </div>
                )}
            </div>

            <p style={{ fontSize: '0.8rem', marginTop: '16px', opacity: 0.7, textAlign: 'center' }}>
                ※設定後はアプリに戻ってください
            </p>
        </div>
    );
};
