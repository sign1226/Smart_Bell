import React, { useState, useEffect } from 'react';
import { useApp } from '../context/AppContext';
import { Bell, UserCircle, Users, Vibrate } from 'lucide-react';
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

    const [vibrationEnabled, setVibrationEnabled] = useState<boolean>(true);
    const [vibrationPattern, setVibrationPattern] = useState<string>('standard');
    const [isOptimizingBattery, setIsOptimizingBattery] = useState<boolean>(false);

    useEffect(() => {
        // バッテリー最適化の状態チェック
        IncomingCall.checkPermissions().then(perms => {
            setIsOptimizingBattery(!perms.batteryOptimization);
        });

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

        const savedVibEnabled = localStorage.getItem('bell_vibration_enabled');
        if (savedVibEnabled !== null) setVibrationEnabled(savedVibEnabled === 'true');

        const savedVibPattern = localStorage.getItem('bell_vibration_pattern');
        if (savedVibPattern) setVibrationPattern(savedVibPattern);
    }, []);

    const handleBatteryRequest = async () => {
        await IncomingCall.requestIgnoreBatteryOptimization();
        // リクエスト後に再チェック
        setTimeout(async () => {
            const perms = await IncomingCall.checkPermissions();
            setIsOptimizingBattery(!perms.batteryOptimization);
        }, 1000);
    };

    // Auto-save effect
    useEffect(() => {
        const timer = setTimeout(() => {
            setConfig(localConfig);
            localStorage.setItem('bell_config', JSON.stringify(localConfig));
            localStorage.setItem('bell_ringtone', selectedRingtone);
            localStorage.setItem('bell_chat_sound', selectedChatRingtone);
            localStorage.setItem('bell_vibration_enabled', vibrationEnabled.toString());
            localStorage.setItem('bell_vibration_pattern', vibrationPattern);

            // Native側に設定を保存 (ウィジェット/サービス用)
            IncomingCall.saveRingtoneSettings({
                uri: selectedRingtone,
                host: localConfig.host,
                port: localConfig.port,
                vibrationEnabled,
                vibrationPattern
            });
            IncomingCall.saveChatSettings({ uri: selectedChatRingtone });
        }, 1000);
        return () => clearTimeout(timer);
    }, [localConfig, selectedRingtone, selectedChatRingtone, vibrationEnabled, vibrationPattern, setConfig]);

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

            {isOptimizingBattery && (
                <section style={{
                    marginBottom: '25px',
                    padding: '15px',
                    background: 'rgba(239, 68, 68, 0.1)',
                    border: '1px solid #ef4444',
                    borderRadius: '12px'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#ef4444', marginBottom: '10px', fontWeight: 'bold' }}>
                        <Vibrate size={20} />
                        停止の制限がかかっています
                    </div>
                    <p style={{ fontSize: '0.85rem', marginBottom: '12px', opacity: 0.9 }}>
                        デバイスのシステム設定により、画面オフ時に通信が遮断される可能性があります。「制限なし」に設定することをお勧めします。
                    </p>
                    <button
                        onClick={handleBatteryRequest}
                        style={{
                            width: '100%',
                            padding: '10px',
                            background: '#ef4444',
                            color: 'white',
                            border: 'none',
                            borderRadius: '8px',
                            fontWeight: 'bold',
                            fontSize: '0.9rem'
                        }}
                    >
                        設定を変更する
                    </button>
                </section>
            )}

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
                    <Vibrate size={18} />
                    着信バイブレーション
                </label>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '15px', background: '#111', padding: '15px', borderRadius: '12px', border: '1px solid #333' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span>バイブレーション有効</span>
                        <input
                            type="checkbox"
                            checked={vibrationEnabled}
                            onChange={(e) => setVibrationEnabled(e.target.checked)}
                            style={{ width: '24px', height: '24px', accentColor: '#3b82f6' }}
                        />
                    </div>

                    {vibrationEnabled && (
                        <div>
                            <label style={{ display: 'block', fontSize: '0.8rem', opacity: 0.7, marginBottom: '8px' }}>パターン</label>
                            <select
                                value={vibrationPattern}
                                onChange={(e) => setVibrationPattern(e.target.value)}
                                style={{ width: '100%', padding: '10px', background: '#000', border: '1px solid #444', color: '#fff', borderRadius: '8px' }}
                            >
                                <option value="standard">標準 (1秒)</option>
                                <option value="short">短い (0.2秒)</option>
                                <option value="rapid">急ぎ (0.3秒間隔)</option>
                                <option value="heartbeat">心音 (トトッ...)</option>
                            </select>
                        </div>
                    )}
                </div>
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
