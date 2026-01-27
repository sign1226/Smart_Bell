if (typeof global === 'undefined') {
  (window as any).global = window;
}
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { LocalNotifications } from '@capacitor/local-notifications'
import { App as CapApp } from '@capacitor/app'

// 通知チャンネルの初期化
const initializeNotifications = async () => {
  try {
    // 通知権限のリクエスト
    const permission = await LocalNotifications.requestPermissions();
    console.log('Notification permission:', permission);

    // 通知チャンネルの作成（Android 8.0+で必要）
    await LocalNotifications.createChannel({
      id: 'bell_calls',
      name: '呼び出し通知',
      description: '調理場からの呼び出しを通知します',
      importance: 5, // 最高優先度
      visibility: 1, // ロック画面に表示
      sound: 'beep.wav',
      vibration: true,
      lights: true,
      lightColor: '#FF0000'
    });

    console.log('Notification channel created');
  } catch (error) {
    console.error('Failed to initialize notifications:', error);
  }
};

// アプリ起動時に初期化
initializeNotifications();

// バックグラウンド状態の監視
CapApp.addListener('appStateChange', ({ isActive }) => {
  console.log('App state changed. Is active:', isActive);
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
