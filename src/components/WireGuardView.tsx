import React, { useState, useEffect, useCallback } from 'react';
import { Shield, Signal, Globe, Activity, Settings, X, RefreshCw } from 'lucide-react';
import { registerPlugin } from '@getcapacitor/core';

const WireGuard = registerPlugin<any>('WireGuard');
const NetworkInfo = registerPlugin<any>('NetworkInfo');

export const WireGuardView: React.FC<{ onBack?: () => void }> = ({ onBack }) => {
    const [ssid, setSsid] = useState<string>('読み込み中...');
    const [isHomeWifi, setIsHomeWifi] = useState<boolean | null>(null);
    const [routerOk, setRouterOk] = useState<'OK' | 'NG' | '確認中'>('確認中');
    const [dnsOk, setDnsOk] = useState<'OK' | 'NG' | '確認中'>('確認中');
    const [dnsIp, setDnsIp] = useState<string>('---');
    const [isAutoControl, setIsAutoControl] = useState<boolean>(false);
    const [vpnStatus, setVpnStatus] = useState<string>('不明');
    
    // 設定値（本来はContextやStorageから取得）
    const [homeSsid, setHomeSsid] = useState<string>(localStorage.getItem('wg_home_ssid') || 'YourHomeSSID');
    const [homeProfile, setHomeProfile] = useState<string>(localStorage.getItem('wg_home_profile') || 'home');
    const [awayProfile, setAwayProfile] = useState<string>(localStorage.getItem('wg_away_profile') || 'away');

    const checkNetwork = useCallback(async () => {
        setRouterOk('確認中');
        setDnsOk('確認中');

        try {
            // SSID取得
            const ssidResult = await NetworkInfo.getSSID();
            const currentSsid = ssidResult.ssid || '未接続';
            setSsid(currentSsid);
            setIsHomeWifi(currentSsid === homeSsid);

            // Router Ping (Fetchで代用的な疎通確認)
            // 実際にはネイティブ側でICMP Pingを打つのが望ましいが、一旦簡易実装
            const startRouter = Date.now();
            try {
                // ローカルルーターへの簡易リクエスト（失敗しても応答速度等で判断）
                await fetch('http://192.168.1.1', { mode: 'no-cors', signal: AbortSignal.timeout(2000) });
                setRouterOk('OK');
            } catch (e) {
                setRouterOk('NG');
            }

            // DNS解決確認 (GoogleへのFetchで代用)
            try {
                const startDns = Date.now();
                await fetch('https://8.8.8.8', { mode: 'no-cors', signal: AbortSignal.timeout(3000) });
                setDnsOk('OK');
                setDnsIp('8.8.8.8');
            } catch (e) {
                setDnsOk('NG');
            }
        } catch (err) {
            console.error('Network check failed', err);
        }
    }, [homeSsid]);

    useEffect(() => {
        checkNetwork();
        const interval = setInterval(checkNetwork, 30000); // 30秒おき
        return () => clearInterval(interval);
    }, [checkNetwork]);

    const toggleVpn = async (profile: string, up: boolean) => {
        try {
            if (up) {
                await WireGuard.setTunnelUp({ name: profile });
                setVpnStatus(`${profile} 接続中`);
            } else {
                await WireGuard.setTunnelDown({ name: profile });
                setVpnStatus(`${profile} 切断`);
            }
        } catch (err) {
            console.error('VPN control failed', err);
        }
    };

    return (
        <div style={{ padding: '20px', backgroundColor: '#000', color: '#fff', minHeight: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                <h1 style={{ fontSize: '1.5rem', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <Shield className="text-blue-500" />
                    WireGuard 管理
                </h1>
                <button onClick={() => checkNetwork()} style={{ background: 'none', border: 'none', color: '#3b82f6' }}>
                    <RefreshCw size={24} />
                </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                {/* ネットワークステータス */}
                <div style={{ padding: '15px', background: '#111', borderRadius: '12px', border: '1px solid #333' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
                        <span style={{ opacity: 0.7 }}>SSID</span>
                        <span style={{ fontWeight: 'bold' }}>{ssid}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
                        <span style={{ opacity: 0.7 }}>自宅Wi-Fi</span>
                        <span style={{ color: isHomeWifi ? '#4ade80' : '#f87171' }}>{isHomeWifi ? '一致' : '不一致'}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
                        <span style={{ opacity: 0.7 }}>ルーター応答</span>
                        <span style={{ color: routerOk === 'OK' ? '#4ade80' : routerOk === 'NG' ? '#f87171' : '#fbbf24' }}>{routerOk}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                        <span style={{ opacity: 0.7 }}>インターネット疎通</span>
                        <span style={{ color: dnsOk === 'OK' ? '#4ade80' : dnsOk === 'NG' ? '#f87171' : '#fbbf24' }}>{dnsOk}</span>
                    </div>
                </div>

                {/* 操作パネル */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                    <button 
                        onClick={() => toggleVpn(homeProfile, true)}
                        style={{ padding: '15px', background: '#1e3a8a', border: 'none', borderRadius: '10px', color: '#fff', fontWeight: 'bold' }}
                    >
                        自宅Profile ON
                    </button>
                    <button 
                        onClick={() => toggleVpn(homeProfile, false)}
                        style={{ padding: '15px', background: '#312e81', border: 'none', borderRadius: '10px', color: '#fff', fontWeight: 'bold' }}
                    >
                        自宅Profile OFF
                    </button>
                    <button 
                        onClick={() => toggleVpn(awayProfile, true)}
                        style={{ padding: '15px', background: '#064e3b', border: 'none', borderRadius: '10px', color: '#fff', fontWeight: 'bold' }}
                    >
                        外出Profile ON
                    </button>
                    <button 
                        onClick={() => toggleVpn(awayProfile, false)}
                        style={{ padding: '15px', background: '#065f46', border: 'none', borderRadius: '10px', color: '#fff', fontWeight: 'bold' }}
                    >
                        外出Profile OFF
                    </button>
                </div>

                {/* 自動制御設定 */}
                <div style={{ padding: '15px', background: '#111', borderRadius: '12px', border: '1px solid #333', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>自動制御 (SSID連動)</span>
                    <input 
                        type="checkbox" 
                        checked={isAutoControl} 
                        onChange={(e) => setIsAutoControl(e.target.checked)}
                        style={{ width: '20px', height: '20px' }}
                    />
                </div>

                {/* ログ・ステータス */}
                <div style={{ padding: '15px', background: '#111', borderRadius: '12px', border: '1px solid #333', fontSize: '0.9rem' }}>
                    <div style={{ color: '#aaa', marginBottom: '5px' }}>状態: {vpnStatus}</div>
                    <div style={{ color: '#aaa' }}>DNS: {dnsIp}</div>
                </div>
            </div>

            {onBack && (
                <button 
                    onClick={onBack}
                    style={{ marginTop: '20px', width: '100%', padding: '12px', background: '#222', border: '1px solid #444', borderRadius: '8px', color: '#fff' }}
                >
                    閉じる
                </button>
            )}
        </div>
    );
};
