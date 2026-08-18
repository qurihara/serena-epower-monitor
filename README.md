# serena-epower-monitor

セレナ e-POWER C28 の Android Auto 画面に、速度・駆動用バッテリー残量などを自前描画するアプリ。
Car Hardware API（androidx.car.app.hardware）で実際にどの車両データが取れるかの実測を兼ねる。

プロジェクトの経緯と記録は cosense を正本とする:
https://scrapbox.io/qurihara/%E3%82%BB%E3%83%AC%E3%83%8Ae-power_C28%E3%81%AE%E9%96%8B%E7%99%BA

## 仕組み

- ナビゲーションカテゴリの Car App Library アプリとして実装している。
  ナビゲーションカテゴリだけが Surface（地図領域）への自由描画を許されているため、
  そこに Canvas で任意の絵を描く。サイドロード運用なので Google Play の審査は受けない。
- `MonitorScreen` が SurfaceCallback で Surface を受け取り、100ms 間隔で描画する。
  同時に CarInfo のリスナー（速度・エネルギー残量・走行距離・車種名）を張り、
  値とステータス（SUCCESS / UNAVAILABLE / UNIMPLEMENTED など）を画面に列挙する。
  この列挙がそのまま「セレナC28で何が取れるか」の実測結果になる。

## ビルド

Android Studio 同梱の JDK 21 を使う。

```bash
cd /Users/kurihara/serena-epower-monitor
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

APK は `/Users/kurihara/serena-epower-monitor/app/build/outputs/apk/debug/app-debug.apk` にできる。

## Pixel 7 へのインストール

### 方法1: 配布ページからダウンロード（Macに接続できない場所でも可）

Pixel 7 の Chrome で https://unryu.org/serena-epower-monitor/ を開き、APK をダウンロードしてインストールする。
手順の詳細は配布ページに書いてある。APK を更新したときは、次を実行して配布ページに反映する。

```bash
cd /Users/kurihara/serena-epower-monitor
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk docs/epower-monitor-debug.apk
git add -A && git commit -m "APK更新" && git push
```

### 方法2: adb でインストール（Pixel 7 を USB 接続できる Mac で）

```bash
/Users/kurihara/Library/Android/sdk/platform-tools/adb install -r /Users/kurihara/serena-epower-monitor/app/build/outputs/apk/debug/app-debug.apk
```

## Android Auto 側の準備（初回のみ）

1. Pixel 7 の設定 → デバイス情報 → ビルド番号を7回タップして開発者向けオプションを有効にする。
2. 設定 → アプリ → Android Auto → 追加の設定（Android Auto の設定画面）を開く。
3. 「バージョンと権限情報」を10回タップして開発者モードを解錠する。
4. 右上メニュー → 開発者向け設定 → 「提供元不明のアプリ」を有効にする（英語表記では Unknown sources）。

これで車に接続すると、車の画面のランチャーに「e-POWERモニター」が現れる。

## DHU（Desktop Head Unit）でのテスト

実車に行かずに Mac 上でテストする方法。

1. Pixel 7 の Android Auto 開発者向け設定で「ヘッドユニットサーバーを開始」を選ぶ。
2. Pixel 7 を USB で Mac につなぎ、次を実行する。

```bash
/Users/kurihara/Library/Android/sdk/platform-tools/adb forward tcp:5277 tcp:5277
/Users/kurihara/Library/Android/sdk/extras/google/auto/desktop-head-unit
```

## 今後の予定

- 実車でどの CarValue が SUCCESS になるかを実測し、README と cosense に記録する。
- Surface への動画描画の実験（MediaCodec / ExoPlayer のフレームを Canvas に転写する方式の検討）。
- 取れた値に応じたエネルギーフロー可視化（e-POWER らしい発電・駆動・回生の表示）。
