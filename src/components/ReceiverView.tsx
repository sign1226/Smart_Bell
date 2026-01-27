import React, { useEffect } from 'react';
import { useApp } from '../context/AppContext';
import { CheckCircle, Clock, Settings } from 'lucide-react';

import IncomingCall from '../plugins/IncomingCall';

export const ReceiverView: React.FC = () => {
    const { isRinging, history, setMode } = useApp();

    useEffect(() => {
        if (isRinging) {
            IncomingCall.startRingtone().catch(e => console.log('Native ringtone failed', e));
        } else {
            IncomingCall.stopRingtone().catch(e => console.log('Native ringtone stop failed', e));
        }

        // Cleanup on unmount if ringing
        return () => {
            if (isRinging) {
                IncomingCall.stopRingtone();
            }
        };
    }, [isRinging]);

    return (
        <div className={isRinging ? 'flash-red' : ''} style={{ position: 'relative', height: '100%' }}>
            <main style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '20px' }}>
                <div style={{ textAlign: 'center', marginBottom: '10px' }}>
                    <h1 style={{ fontSize: '1.5rem', fontWeight: 'bold' }}>受信機</h1>
                </div>

                {/* ネイティブ側で全画面表示するため、React側の着信画面は削除 */}

                <section style={{
                    flex: 1,
                    background: 'var(--secondary-bg)',
                    borderRadius: 'var(--button-radius)',
                    padding: '20px',
                    overflowY: 'auto',
                    marginBottom: '80px'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '15px', borderBottom: '1px solid #333', paddingBottom: '10px' }}>
                        <Clock size={20} />
                        <h3 style={{ fontSize: '1.1rem' }}>呼び出し履歴</h3>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        {history.length === 0 ? (
                            <p style={{ opacity: 0.5, textAlign: 'center', padding: '20px' }}>履歴はありません</p>
                        ) : (
                            history.map((item, i) => (
                                <div key={i} style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    padding: '15px',
                                    backgroundColor: i === 0 ? '#333' : '#222',
                                    borderRadius: '8px',
                                    borderLeft: `4px solid ${i === 0 ? 'var(--accent-color)' : 'transparent'}`
                                }}>
                                    <div>
                                        <span style={{ fontWeight: 'bold' }}>{item.from}</span>
                                        <div style={{ fontSize: '0.8rem', opacity: 0.6 }}>{new Date(item.timestamp).toLocaleTimeString()}</div>
                                    </div>
                                    <CheckCircle size={20} color="var(--success-color)" />
                                </div>
                            ))
                        )}
                    </div>
                </section>
            </main>

            <button
                onClick={() => setMode('settings')}
                style={{
                    position: 'absolute',
                    bottom: '20px',
                    right: '20px',
                    background: '#333',
                    color: '#fff',
                    padding: '15px',
                    borderRadius: '50%',
                    boxShadow: '0 4px 10px rgba(0,0,0,0.3)',
                    zIndex: 20
                }}
            >
                <Settings size={24} />
            </button>

            <style>{`
                @keyframes flash-icon {
                  0% { transform: rotate(0deg) scale(1); }
                  25% { transform: rotate(15deg) scale(1.1); }
                  75% { transform: rotate(-15deg) scale(1.1); }
                  100% { transform: rotate(0deg) scale(1); }
                }
                .flash-icon {
                  animation: flash-icon 0.5s infinite;
                }
            `}</style>
        </div>
    );
};
