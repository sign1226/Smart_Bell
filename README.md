# SmartBell - モダンなMQTT呼出＆チャットアプリ

SmartBellは、MQTTプロトコルを活用したリアルタイム呼出（ドアベル/インターホン）およびチャット機能を備えたAndroidアプリケーションです。Capacitorを使用したハイブリッドなアプローチにより、リッチなUIと強力なネイティブ機能の両立を実現しています。

## 🌟 主な機能

- **🚀 ワンタップ呼出**: ホーム画面から巨大なベルアイコンをタップするだけで、登録されたデバイスを即座に鳴らすことができます。
- **💬 リアルタイムチャット**: 送信相手を選んでメッセージを送受信。Androidのヘッドアップ通知（バナー表示）に対応しており、重要な連絡を見逃しません。
- **🎨 ミニマリストデザイン**: 黒を基調としたプレミアムなダークモードUI。直感的なアイコン操作を重視した洗練されたデザインです。
- **📟 ホーム画面ウィジェット**: 特定の連絡先を呼び出すための専用ボタンをホーム画面に配置可能（配置時に呼出相手を選択可能）。
- **🔔 高度な通知**: アプリがバックグラウンドにあってもMQTT経由で信号を受信し、フルスクリーン着信画面やチャット通知を表示します。

## 🛠 技術スタック

- **Frontend**: React, TypeScript, Vite
- **Styling**: Vanilla CSS (Inline Styles for stability in WebView)
- **Native Bridge**: Capacitor
- **Backend Messaging**: MQTT (Paho Android Client)
- **Icons**: Lucide React
- **Animations**: Framer Motion

## 🚀 セットアップ方法

1. **環境構築**:
   - Node.js (v18+)
   - Android Studio & SDK

2. **依存関係のインストール**:
   ```bash
   npm install
   ```

3. **ビルドと実行**:
   ```bash
   npm run build
   npx cap sync android
   npx cap run android
   ```

4. **MQTTサーバーの設定**:
   アプリ内の「設定」タブから、MQTTブローカー（ホスト、ポート、トピック）を設定してください。WeaveやMosquittoなどのブローカーが推奨されます。

## 📂 プロジェクト構成

- `src/`: Reactフロントエンドソース
- `android/`: Androidネイティブプロジェクト（Java）
- `android/app/src/main/java/com/smartbell/pro/`: ネイティブ連携プラグイン、MQTTサービス、ウィジェットロジック

## 📝 ライセンス

このプロジェクトは[MITライセンス](LICENSE)の下で公開されています。
