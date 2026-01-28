import React, { useState, useEffect } from 'react';
import { useApp } from '../context/AppContext';
import { motion, AnimatePresence } from 'framer-motion';
import { Bell, Wifi, WifiOff, Users, ChevronDown, Check, Circle } from 'lucide-react';

interface SenderViewProps {
    sendCall: (targetId?: string) => boolean;
}

export const SenderView: React.FC<SenderViewProps> = ({ sendCall }) => {
    const { isConnected, connectionError, config, contacts, defaultRecipientId, callStatus, setCallStatus } = useApp();
    const [cooldown, setCooldown] = useState(0);
    // const [status, setStatus] = useState<'idle' | 'success' | 'error'>('idle'); // Use context callStatus
    const [targetId, setTargetId] = useState(defaultRecipientId || '');
    const [showContactSelector, setShowContactSelector] = useState(false);

    useEffect(() => {
        if (cooldown > 0) {
            const timer = setTimeout(() => setCooldown(cooldown - 1), 1000);
            return () => clearTimeout(timer);
        }
    }, [cooldown]);

    const handleCall = () => {
        if (cooldown > 0) return;

        const success = sendCall(targetId);
        if (success) {
            setCallStatus('sending');
            setCooldown(5);
            // Reset status after a while if no ack
            setTimeout(() => {
                setCallStatus(prev => prev === 'delivered' ? 'idle' : 'failed'); // Just idle if no ack or done
                setTimeout(() => setCallStatus('idle'), 2000);
            }, 5000);
        } else {
            setCallStatus('failed');
            setTimeout(() => setCallStatus('idle'), 3000);
        }
    };

    const getDisplayName = (id: string) => {
        const contact = contacts.find(c => c.id === id);
        return contact ? contact.name : id.slice(0, 8);
    };

    return (
        <div style={{ height: '100%' }} onClick={() => setShowContactSelector(false)}>
            <main style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', position: 'relative', height: '100%' }}>
                {/* 接続ステータスを上部中央に配置 */}
                <div style={{ position: 'absolute', top: '20px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '5px', width: '100%' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        {isConnected ? <Wifi size={16} color="var(--success-color)" /> : <WifiOff size={16} color="var(--danger-color)" />}
                        <span style={{ fontSize: '0.8rem', color: isConnected ? 'var(--success-color)' : 'var(--danger-color)' }}>
                            {isConnected ? '接続済み' : 'オフライン'}
                        </span>
                    </div>

                    {/* デバッグ情報表示 */}
                    {!isConnected && (
                        <div style={{ marginTop: '10px', padding: '10px', background: 'rgba(255,0,0,0.1)', borderRadius: '8px', width: '90%', fontSize: '0.7rem', color: 'var(--danger-color)', textAlign: 'center' }}>
                            <div style={{ fontWeight: 'bold', marginBottom: '4px' }}>接続先: {config.host}</div>
                            <div>{connectionError || '待機中...'}</div>
                        </div>
                    )}
                </div>

                <div style={{ marginBottom: '40px', width: '85%', maxWidth: '320px', position: 'relative' }} onClick={e => e.stopPropagation()}>
                    <button
                        onClick={() => setShowContactSelector(!showContactSelector)}
                        style={{
                            width: '100%',
                            backgroundColor: 'rgba(30, 41, 59, 0.5)',
                            border: '1px solid rgba(71, 85, 105, 0.5)',
                            borderRadius: '16px',
                            padding: '12px 20px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            color: '#fff',
                            fontSize: '0.95rem',
                            fontWeight: '500'
                        }}
                    >
                        <span style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                            <div style={{ padding: '6px', backgroundColor: 'rgba(59, 130, 246, 0.2)', borderRadius: '8px', display: 'flex' }}>
                                <Users size={16} color="#3b82f6" />
                            </div>
                            {targetId ? getDisplayName(targetId) : '全員 (一斉呼出)'}
                        </span>
                        <ChevronDown size={18} style={{ opacity: 0.5 }} />
                    </button>

                    {showContactSelector && (
                        <div style={{
                            position: 'absolute',
                            top: '100%',
                            left: 0,
                            right: 0,
                            marginTop: '8px',
                            backgroundColor: '#1e293b',
                            border: '1px solid #334155',
                            borderRadius: '16px',
                            boxShadow: '0 10px 25px rgba(0,0,0,0.5)',
                            zIndex: 100,
                            maxHeight: '200px',
                            overflowY: 'auto'
                        }}>
                            <button
                                onClick={() => { setTargetId(''); setShowContactSelector(false); }}
                                style={{
                                    width: '100%',
                                    textAlign: 'left',
                                    padding: '12px 16px',
                                    backgroundColor: targetId === '' ? '#334155' : 'transparent',
                                    border: 'none',
                                    borderBottom: '1px solid #334155',
                                    color: '#fff',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '12px'
                                }}
                            >
                                <Circle size={16} />
                                <span>全員 (一斉呼出)</span>
                                {targetId === '' && <Check size={14} style={{ marginLeft: 'auto', color: '#3b82f6' }} />}
                            </button>
                            {contacts.map(contact => (
                                <button
                                    key={contact.id}
                                    onClick={() => { setTargetId(contact.id); setShowContactSelector(false); }}
                                    style={{
                                        width: '100%',
                                        textAlign: 'left',
                                        padding: '12px 16px',
                                        backgroundColor: targetId === contact.id ? '#334155' : 'transparent',
                                        border: 'none',
                                        color: '#fff',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '12px'
                                    }}
                                >
                                    <div style={{ width: '24px', height: '24px', borderRadius: '50%', backgroundColor: '#3b82f6', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '10px', fontWeight: 'bold' }}>
                                        {contact.name[0].toUpperCase()}
                                    </div>
                                    <span style={{ flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{contact.name}</span>
                                    {targetId === contact.id && <Check size={14} style={{ marginLeft: 'auto', color: '#3b82f6' }} />}
                                </button>
                            ))}
                        </div>
                    )}
                </div>

                <AnimatePresence mode="wait">
                    {callStatus === 'sending' && (
                        <motion.div
                            initial={{ opacity: 0, scale: 0.8 }}
                            animate={{ opacity: 1, scale: 1 }}
                            exit={{ opacity: 0 }}
                            style={{ position: 'absolute', top: '35%', backgroundColor: 'rgba(59, 130, 246, 0.2)', color: '#60a5fa', padding: '8px 20px', borderRadius: '999px', fontSize: '1rem', fontWeight: 'bold', border: '1px solid rgba(59, 130, 246, 0.3)' }}
                        >
                            呼び出し中...
                        </motion.div>
                    )}
                    {callStatus === 'delivered' && (
                        <motion.div
                            initial={{ opacity: 0, scale: 0.8 }}
                            animate={{ opacity: 1, scale: 1 }}
                            exit={{ opacity: 0 }}
                            style={{ position: 'absolute', top: '35%', backgroundColor: 'rgba(34, 197, 94, 0.2)', color: '#22c55e', padding: '8px 20px', borderRadius: '999px', fontSize: '1rem', fontWeight: 'bold', border: '1px solid rgba(34, 197, 94, 0.3)' }}
                        >
                            <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <Check size={18} /> 相手に着信中
                            </span>
                        </motion.div>
                    )}
                    {callStatus === 'failed' && (
                        <motion.div
                            initial={{ opacity: 0, scale: 0.8 }}
                            animate={{ opacity: 1, scale: 1 }}
                            exit={{ opacity: 0 }}
                            style={{ position: 'absolute', top: '35%', backgroundColor: 'rgba(239, 68, 68, 0.2)', color: '#f87171', padding: '8px 20px', borderRadius: '999px', fontSize: '1rem', fontWeight: 'bold', border: '1px solid rgba(239, 68, 68, 0.3)' }}
                        >
                            送信失敗
                        </motion.div>
                    )}
                </AnimatePresence>

                <motion.button
                    whileTap={{ scale: 0.9 }}
                    onClick={handleCall}
                    disabled={cooldown > 0 || !isConnected}
                    style={{
                        position: 'relative',
                        width: 'min(65vw, 260px)',
                        height: 'min(65vw, 260px)',
                        borderRadius: '50%',
                        backgroundColor: cooldown > 0 ? '#1e293b' : '#3b82f6',
                        color: cooldown > 0 ? '#475569' : '#fff',
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        boxShadow: cooldown > 0 ? 'none' : '0 0 60px rgba(59, 130, 246, 0.4)',
                        border: 'none',
                        outline: 'none'
                    }}
                >
                    <Bell size={100} style={{ opacity: cooldown > 0 ? 0.3 : 1 }} />
                    {cooldown > 0 && (
                        <div style={{
                            position: 'absolute',
                            fontSize: '2rem',
                            fontWeight: 'bold',
                            color: '#fff',
                            textShadow: '0 2px 10px rgba(0,0,0,0.5)'
                        }}>
                            {cooldown}
                        </div>
                    )}
                </motion.button>
            </main>

            <footer style={{ textAlign: 'center', padding: '10px 0 20px', opacity: 0.3, fontSize: '0.7rem' }}>
                Smart Bell v1.0
            </footer>
        </div>
    );
};
