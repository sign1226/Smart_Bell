import React, { useState, useEffect, useRef } from 'react';
import { useApp } from '../context/AppContext';
import { Send, UserPlus, Users, Check, Trash2 } from 'lucide-react';

interface ChatViewProps {
    sendChat: (text: string, targetId?: string) => boolean;
}

export const ChatView: React.FC<ChatViewProps> = ({ sendChat }) => {
    const { chatHistory, isConnected, contacts, addContact, clearChatHistory, defaultRecipientId } = useApp();
    const [message, setMessage] = useState('');
    const [targetId, setTargetId] = useState(defaultRecipientId || '');
    const [showContactSelector, setShowContactSelector] = useState(false);
    const messagesEndRef = useRef<HTMLDivElement>(null);

    // Auto-scroll to bottom
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [chatHistory]);

    const handleSend = () => {
        if (!message.trim() || !isConnected) return;

        if (sendChat(message, targetId)) {
            setMessage('');
        }
    };

    const handleSaveContact = (id: string, name?: string) => {
        const newName = prompt('連絡先名を入力してください:', name || id.slice(0, 8));
        if (newName) {
            addContact({ id, name: newName });
        }
    };

    const getDisplayName = (id: string) => {
        const contact = contacts.find(c => c.id === id);
        return contact ? contact.name : id.slice(0, 8);
    };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', height: '100%', backgroundColor: '#000', color: '#fff', position: 'relative' }}>
            {/* Header with Clear Button */}
            <div style={{
                padding: '12px 16px',
                borderBottom: '1px solid #1e293b',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                backgroundColor: 'rgba(0,0,0,0.8)',
                backdropFilter: 'blur(10px)',
                zIndex: 10
            }}>
                <h2 style={{ margin: 0, fontSize: '16px', fontWeight: '600' }}>チャット</h2>
                {chatHistory.length > 0 && (
                    <button
                        onClick={clearChatHistory}
                        style={{
                            padding: '8px',
                            backgroundColor: 'transparent',
                            border: 'none',
                            color: '#ef4444',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                            fontSize: '13px'
                        }}
                    >
                        <Trash2 size={16} />
                        履歴を削除
                    </button>
                )}
            </div>

            {/* Chat Area - Padding bottom adjusted for floating input */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '16px', paddingBottom: '120px', backgroundColor: '#000' }} onClick={() => setShowContactSelector(false)}>
                {chatHistory.map((msg, i) => {
                    const isContact = contacts.some(c => c.id === msg.fromId);
                    const displayName = getDisplayName(msg.fromId);

                    return (
                        <div key={msg.id || i} style={{
                            display: 'flex',
                            width: '100%',
                            justifyContent: msg.isSelf ? 'flex-end' : 'flex-start',
                            marginBottom: '16px'
                        }}>
                            <div style={{
                                display: 'flex',
                                maxWidth: '85%',
                                flexDirection: msg.isSelf ? 'row-reverse' : 'row',
                                gap: '12px',
                                alignItems: 'flex-end'
                            }}>
                                {/* Avatar */}
                                <div
                                    onClick={() => !msg.isSelf && !isContact && handleSaveContact(msg.fromId)}
                                    style={{
                                        position: 'relative',
                                        width: '40px',
                                        height: '40px',
                                        borderRadius: '50%',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        fontSize: '14px',
                                        fontWeight: 'bold',
                                        cursor: 'pointer',
                                        flexShrink: 0,
                                        backgroundColor: msg.isSelf ? '#2563eb' : (isContact ? '#4f46e5' : '#374151'),
                                        color: '#fff'
                                    }}
                                    title={!msg.isSelf && !isContact ? "連絡先に追加" : displayName}
                                >
                                    {msg.isSelf ? '自' : (isContact ? displayName[0] : '?')}
                                    {!msg.isSelf && !isContact && (
                                        <div style={{
                                            position: 'absolute',
                                            top: '-4px',
                                            right: '-4px',
                                            backgroundColor: '#22c55e',
                                            borderRadius: '50%',
                                            padding: '4px',
                                            border: '2px solid #000'
                                        }}>
                                            <UserPlus size={10} strokeWidth={3} color="#fff" />
                                        </div>
                                    )}
                                </div>

                                {/* Message Bubble */}
                                <div style={{
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: msg.isSelf ? 'flex-end' : 'flex-start'
                                }}>
                                    {!msg.isSelf && (
                                        <span style={{
                                            fontSize: '11px',
                                            color: '#9ca3af',
                                            paddingLeft: '4px',
                                            marginBottom: '4px',
                                            maxWidth: '150px',
                                            overflow: 'hidden',
                                            textOverflow: 'ellipsis',
                                            whiteSpace: 'nowrap'
                                        }}>
                                            {displayName}
                                        </span>
                                    )}
                                    <div style={{
                                        padding: '12px 16px',
                                        borderRadius: msg.isSelf ? '20px 20px 4px 20px' : '20px 20px 20px 4px',
                                        fontSize: '15px',
                                        lineHeight: '1.4',
                                        backgroundColor: msg.isSelf ? '#2563eb' : '#374151',
                                        color: '#fff',
                                        boxShadow: '0 2px 8px rgba(0,0,0,0.3)'
                                    }}>
                                        {msg.text}
                                    </div>
                                    <span style={{
                                        fontSize: '10px',
                                        color: '#6b7280',
                                        paddingLeft: '4px',
                                        marginTop: '4px',
                                        fontWeight: 500
                                    }}>
                                        {new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                    </span>
                                </div>
                            </div>
                        </div>
                    );
                })}
                <div ref={messagesEndRef} />
            </div>

            {/* Combined Input & Target Selector Area - Floating above dock */}
            <div style={{
                position: 'fixed',
                bottom: '140px',
                left: '50%',
                transform: 'translateX(-50%)',
                width: 'calc(100% - 32px)',
                maxWidth: '432px',
                zIndex: 900,
                display: 'flex',
                gap: '8px',
                alignItems: 'center',
                backgroundColor: 'rgba(15, 23, 42, 0.8)',
                backdropFilter: 'blur(12px)',
                padding: '8px 12px',
                borderRadius: '999px',
                border: '1px solid #334155',
                boxShadow: '0 8px 32px rgba(0,0,0,0.4)'
            }}>
                <div style={{ position: 'relative' }}>
                    <button
                        onClick={() => setShowContactSelector(!showContactSelector)}
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            backgroundColor: '#1e293b',
                            border: '1px solid #475569',
                            borderRadius: '999px',
                            width: '40px',
                            height: '40px',
                            color: '#94a3b8'
                        }}
                    >
                        {targetId ? <div style={{ fontSize: '12px', fontWeight: 'bold', color: '#3b82f6' }}>{getDisplayName(targetId)[0].toUpperCase()}</div> : <Users size={18} />}
                    </button>

                    {showContactSelector && (
                        <div style={{
                            position: 'absolute',
                            bottom: '50px',
                            left: '0',
                            backgroundColor: '#1e293b',
                            border: '1px solid #475569',
                            borderRadius: '16px',
                            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
                            zIndex: 1001,
                            width: '240px',
                            maxHeight: '300px',
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
                                    color: '#fff',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '12px'
                                }}
                            >
                                <Users size={16} />
                                <span style={{ fontSize: '14px' }}>全員 (一斉配信)</span>
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
                                    <div style={{ width: '24px', height: '24px', borderRadius: '50%', backgroundColor: '#0f172a', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '10px', fontWeight: 'bold' }}>
                                        {contact.name[0].toUpperCase()}
                                    </div>
                                    <span style={{ fontSize: '14px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{contact.name}</span>
                                    {targetId === contact.id && <Check size={14} style={{ marginLeft: 'auto', color: '#3b82f6' }} />}
                                </button>
                            ))}
                        </div>
                    )}
                </div>

                <input
                    type="text"
                    value={message}
                    onChange={e => setMessage(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleSend()}
                    placeholder={targetId ? `${getDisplayName(targetId)}さんに送信...` : "メッセージを入力..."}
                    style={{
                        flex: 1,
                        backgroundColor: 'transparent',
                        border: 'none',
                        outline: 'none',
                        fontSize: '15px',
                        color: '#fff'
                    }}
                />

                <button
                    onClick={handleSend}
                    disabled={!isConnected || !message.trim()}
                    style={{
                        padding: '8px',
                        borderRadius: '50%',
                        backgroundColor: 'transparent',
                        color: !isConnected || !message.trim() ? '#475569' : '#3b82f6',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        transition: 'opacity 0.2s'
                    }}
                >
                    <Send size={24} />
                </button>
            </div>
        </div>
    );
};
