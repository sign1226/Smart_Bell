import React, { useState, useEffect } from 'react';
import { useApp } from '../context/AppContext';
import { Bell, UserCircle, Users } from 'lucide-react';
import IncomingCall from '../plugins/IncomingCall';
import { ContactsView } from './ContactsView';

export const SettingsView: React.FC = () => {
    const { config, setConfig, deviceId, contacts, defaultRecipientId, setDefaultRecipientId } = useApp();
    const [localConfig, setLocalConfig] = useState(config);
    const [view, setView] = useState<'main' | 'contacts'>('main');

    // 着信音関連
    const [ringtones, setRingtones] = useState<{ title: string; uri: string }[]>([]);
    const [selectedRingtone, setSelectedRingtone] = useState<string>('');
    const [chatRingtones, setChatRingtones] = useState<{ title: string; uri: string }[]>([]);
    const [selectedChatRingtone, setSelectedChatRingtone] = useState<string>('');

    useEffect(() => {
        // 着信音一覧の取得
        IncomingCall.getRingtones({ type: 'ringtone' }).then(result => {
            if (result && result.ringtones) {
                setRingtones(result.ringtones);
            }
        }).catch(err => {
            console.error('Failed to get ringtones', err);
        });

        // 通知音一覧の取得
        IncomingCall.getRingtones({ type: 'notification' }).then(result => {
            if (result && result.ringtones) {
                setChatRingtones(result.ringtones);
            }
        }).catch(err => {
            console.error('Failed to get notification sounds', err);
        });

        // 保存済み設定の読み込み (localStorage経由)
        const savedChatSound = localStorage.getItem('bell_chat_sound');
        if (savedChatSound) setSelectedChatRingtone(savedChatSound);

        const savedRingtone = localStorage.getItem('bell_ringtone');
        if (savedRingtone) setSelectedRingtone(savedRingtone);
    }, []);

    // Auto-save effect
    useEffect(() => {
        const timer = setTimeout(() => {
            setConfig(localConfig);
            localStorage.setItem('bell_config', JSON.stringify(localConfig));
            localStorage.setItem('bell_ringtone', selectedRingtone);
            localStorage.setItem('bell_chat_sound', selectedChatRingtone);

            // Native側に設定を保存 (ウィジェット/サービス用)
            IncomingCall.saveRingtoneSettings({ uri: selectedRingtone, host: localConfig.host });
            IncomingCall.saveChatSettings({ uri: selectedChatRingtone });
        }, 1000);
        return () => clearTimeout(timer);
    }, [localConfig, selectedRingtone, selectedChatRingtone, setConfig]);

    if (view === 'contacts') {
        return <ContactsView onBack={() => setView('main')} />;
    }

    return (
        <div className="container" style={{ padding: '20px', position: 'relative', height: '100%', overflowY: 'auto', backgroundColor: 'black', color: 'white' }}>
            <h1 style={{ fontSize: '1.5rem', fontWeight: 'bold', marginBottom: '20px' }}>設定</h1>

            <section style={{ marginBottom: '25px' }}>
                <button
                    onClick={() => setView('contacts')}
                    style={{
                        width: '100%',
                        padding: '16px',
                        background: '#111',
                        border: '1px solid #333',
                        borderRadius: '12px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        color: 'white',
                    }}
                >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <UserCircle size={24} className="text-blue-500" />
                        <span style={{ fontWeight: '500' }}>連絡先の管理</span>
                    </div>
                </button>
            </section>

            <section style={{ marginBottom: '25px' }}>
                <label style={{ display: 'block', marginBottom: '5px', opacity: 0.7 }}>デバイスID (この端末)</label>
                <div style={{
                    padding: '12px',
                    background: '#111',
                    border: '1px solid #333',
                    color: '#888',
                    borderRadius: '8px',
                    fontFamily: 'monospace',
                    fontSize: '0.9rem',
                    wordBreak: 'break-all'
                }}>
                    {deviceId}
                </div>
            </section>

            <section style={{ marginBottom: '25px' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px', fontWeight: 'bold' }}>
                    <Bell size={18} />
                    呼出着信音
                </label>
                <select
                    value={selectedRingtone}
                    onChange={(e) => setSelectedRingtone(e.target.value)}
                    style={{ width: '100%', padding: '12px', background: '#111', border: '1px solid #333', color: '#fff', borderRadius: '8px' }}
                >
                    <option value="">システムデフォルト</option>
                    {ringtones.map((ringtone, index) => (
                        <option key={index} value={ringtone.uri}>
                            {ringtone.title}
                        </option>
                    ))}
                </select>
            </section>

            <section style={{ marginBottom: '25px' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px', fontWeight: 'bold' }}>
                    <Users size={18} />
                    デフォルト送信先
                </label>
                <select
                    value={defaultRecipientId}
                    onChange={(e) => setDefaultRecipientId(e.target.value)}
                    style={{ width: '100%', padding: '12px', background: '#111', border: '1px solid #333', color: '#fff', borderRadius: '8px' }}
                >
                    <option value="">全員 (一斉配信)</option>
                    {contacts.map((contact) => (
                        <option key={contact.id} value={contact.id}>
                            {contact.name}
                        </option>
                    ))}
                </select>
            </section>

            <section style={{ marginBottom: '25px' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px', fontWeight: 'bold' }}>
                    <Bell size={18} />
                    チャット通知音
                </label>
                <select
                    value={selectedChatRingtone}
                    onChange={(e) => setSelectedChatRingtone(e.target.value)}
                    style={{ width: '100%', padding: '12px', background: '#111', border: '1px solid #333', color: '#fff', borderRadius: '8px' }}
                >
                    <option value="">システムデフォルト</option>
                    {chatRingtones.map((ringtone, index) => (
                        <option key={index} value={ringtone.uri}>
                            {ringtone.title}
                        </option>
                    ))}
                </select>
            </section>

            <section style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                <div>
                    <label style={{ display: 'block', marginBottom: '5px', opacity: 0.7 }}>MQTTブローカー ホスト名</label>
                    <input
                        type="text"
                        value={localConfig.host}
                        onChange={e => setLocalConfig({ ...localConfig, host: e.target.value })}
                        style={{ width: '100%', padding: '12px', background: '#111', border: '1px solid #333', color: '#fff', borderRadius: '8px' }}
                    />
                </div>
                <div>
                    <label style={{ display: 'block', marginBottom: '5px', opacity: 0.7 }}>ポート番号 (WebSocket)</label>
                    <input
                        type="number"
                        value={localConfig.port}
                        onChange={e => setLocalConfig({ ...localConfig, port: parseInt(e.target.value) })}
                        style={{ width: '100%', padding: '12px', background: '#111', border: '1px solid #333', color: '#fff', borderRadius: '8px' }}
                    />
                </div>
                <div>
                    <label style={{ display: 'block', marginBottom: '5px', opacity: 0.7 }}>表示名 (送信者名など)</label>
                    <input
                        type="text"
                        value={localConfig.clientId}
                        onChange={e => setLocalConfig({ ...localConfig, clientId: e.target.value })}
                        style={{ width: '100%', padding: '12px', background: '#111', border: '1px solid #333', color: '#fff', borderRadius: '8px' }}
                    />
                </div>
            </section>

            <div style={{ marginTop: '30px', fontSize: '0.8rem', color: '#666', textAlign: 'center' }}>
                設定は自動的に保存されます。
                <br />
                反映されない場合はアプリを再起動してください。
            </div>

            <button
                onClick={() => window.location.reload()}
                style={{
                    display: 'block',
                    margin: '10px auto',
                    padding: '8px 16px',
                    background: '#222',
                    border: '1px solid #444',
                    borderRadius: '20px',
                    fontSize: '0.8rem',
                    color: '#aaa'
                }}
            >
                アプリをリロード
            </button>
        </div >
    );
};
