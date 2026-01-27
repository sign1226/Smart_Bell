import React from 'react';
import { BookOpen, Smartphone, Bell, Layout, Settings, X } from 'lucide-react';

interface HelpViewProps {
    onClose: () => void;
}

export const HelpView: React.FC<HelpViewProps> = ({ onClose }) => {
    return (
        <div style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'var(--bg-color)',
            zIndex: 1000,
            display: 'flex',
            flexDirection: 'column',
            padding: '20px',
            paddingTop: 'calc(env(safe-area-inset-top) + 20px)' // ステータスバー回避
        }}>
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <BookOpen size={24} color="var(--accent-color)" />
                    <h2 style={{ margin: 0 }}>使い方ガイド</h2>
                </div>
                <button onClick={onClose} style={{
                    background: 'none',
                    border: 'none',
                    color: '#fff',
                    padding: '10px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center'
                }}>
                    <X size={28} />
                </button>
            </header>

            <div style={{ flex: 1, overflowY: 'auto', paddingRight: '10px' }}>
                <section style={sectionStyle}>
                    <h3 style={h3Style}><Smartphone size={18} /> モードの選択</h3>
                    <p style={pStyle}>
                        アプリには2つのモードがあります。設定画面から切り替え可能です。
                    </p>
                    <ul style={ulStyle}>
                        <li><strong>送信機モード</strong>: ボタンを押して相手を呼び出します。</li>
                        <li><strong>受信機モード</strong>: 相手からの呼び出しを待ち受け、着信時に通知と画面でお知らせします。</li>
                    </ul>
                </section>

                <section style={sectionStyle}>
                    <h3 style={h3Style}><Layout size={18} /> ホーム画面ウィジェット</h3>
                    <p style={pStyle}>
                        ホーム画面を長押ししてウィジェットを追加すると、アプリを開かずにワンタップで呼び出しができます。
                    </p>
                    <div style={{ background: '#222', padding: '10px', borderRadius: '8px', fontSize: '0.8rem', opacity: 0.8 }}>
                        ※ウィジェットを正しく動作させるために、一度アプリの設定画面で「保存」を行う必要があります。
                    </div>
                </section>

                <section style={sectionStyle}>
                    <h3 style={h3Style}><Bell size={18} /> 重要な権限について</h3>
                    <p style={pStyle}>
                        受信機モードを確実に動作させるために以下の権限が推奨されます。
                    </p>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        <div style={permBoxStyle}>
                            <strong>他のアプリの上に表示</strong>
                            <p style={{ margin: '4px 0 0 0', fontSize: '0.8rem' }}>画面ロック中や他アプリ使用中に着信画面を割り込ませるために必要です。</p>
                        </div>
                        <div style={permBoxStyle}>
                            <strong>電池の最適化を無視</strong>
                            <p style={{ margin: '4px 0 0 0', fontSize: '0.8rem' }}>スリープ中でも接続を維持し、着信を逃さないために必要です。</p>
                        </div>
                    </div>
                </section>

                <section style={sectionStyle}>
                    <h3 style={h3Style}><Settings size={18} /> 接続トラブル</h3>
                    <p style={pStyle}>
                        接続ができない場合は、MQTTホスト（サーバー）のIPアドレスが正しいか、同じWi-Fiに接続されているかを確認してください。
                    </p>
                </section>

                <section style={sectionStyle}>
                    <h3 style={h3Style}><Layout size={18} /> Mosquitto 設定例</h3>
                    <p style={pStyle}>
                        サーバー側 (`mosquitto.conf`) で WebSocket を有効にする必要があります。
                    </p>
                    <pre style={{
                        background: '#000',
                        color: '#0f0',
                        padding: '10px',
                        borderRadius: '8px',
                        fontSize: '0.8rem',
                        overflowX: 'auto'
                    }}>
                        {`# 標準MQTT（念のため残しておく）
listener 1883
protocol mqtt
allow_anonymous true

# WebSockets用（アプリで使用する）
listener 8083
protocol websockets
allow_anonymous true`}
                    </pre>
                </section>
            </div>

            <button
                onClick={onClose}
                style={{
                    marginTop: '20px',
                    padding: '15px',
                    background: 'var(--accent-color)',
                    border: 'none',
                    borderRadius: '12px',
                    color: '#000',
                    fontWeight: 'bold',
                    fontSize: '1rem'
                }}
            >
                閉じる
            </button>
        </div>
    );
};

const sectionStyle: React.CSSProperties = {
    background: 'rgba(255,255,255,0.05)',
    padding: '16px',
    borderRadius: '16px',
    marginBottom: '16px'
};

const h3Style: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    margin: '0 0 12px 0',
    fontSize: '1.1rem',
    color: 'var(--accent-color)'
};

const pStyle: React.CSSProperties = {
    margin: '0 0 12px 0',
    fontSize: '0.9rem',
    lineHeight: '1.5',
    opacity: 0.9
};

const ulStyle: React.CSSProperties = {
    margin: 0,
    paddingLeft: '20px',
    fontSize: '0.9rem',
    opacity: 0.8
};

const permBoxStyle: React.CSSProperties = {
    background: 'rgba(0,0,0,0.2)',
    padding: '10px',
    borderRadius: '8px',
    borderLeft: '4px solid var(--accent-color)'
};
