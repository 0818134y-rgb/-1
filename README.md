# ワードウルフ＆NG（仮）

その場にいる全員のスマホで遊ぶ、6種類のパーティーゲームのAndroidアプリ（ワードウルフ・NGワード・スパイはだれだ・みんなでシンクロ・質問で当てろ・ぶっちゃけ質問）。サーバーなし（Nearby Connections）、収益は AdMob 広告。仕様は [SPEC.md](SPEC.md)。

## 構成

| 場所 | 中身 |
| --- | --- |
| `app/src/main/java/.../game/` | ゲームの進行（`Engine`）と通信の型。Android に依存しない純Kotlin |
| `app/src/main/java/.../net/NearbyLink.kt` | Nearby Connections の包み |
| `app/src/main/java/.../ads/AdController.kt` | 全画面広告の先読みと表示 |
| `app/src/main/java/.../ui/` | 画面（Jetpack Compose）と `AppViewModel` |
| `app/src/main/assets/topics.json` | お題。`tools/make_topics.py` で作る |
| `docs/privacy.html` | プライバシーポリシーの下書き |

## ビルドとテスト

```
./gradlew :app:testDebugUnitTest   # ゲーム進行のテストと画面の画像化（app/build/screens/）
./gradlew :app:assembleDebug       # 試遊用APK
```

Android Studio で開く場合は、このフォルダをそのまま開けば動きます。

## お題を増やす

`tools/make_topics.py` のリストに追記して `python3 tools/make_topics.py` を実行。重複があると止まります。

## Google Play に出す前にやること

1. `app/build.gradle.kts` の `applicationId` を決める（公開後は変えられない）。
2. AdMob でアプリと広告ユニット（バナー・インタースティシャル）を作り、`admobAppId`・`AD_BANNER_ID`・`AD_INTERSTITIAL_ID` を差し替える。
3. `docs/privacy.html` に連絡先を書き、GitHub Pages などで公開して `PRIVACY_URL` に入れる。
4. 署名用の鍵を作って `bundleRelease` で AAB を作る（鍵は自分で保管。なくすと更新できない）。
5. Play Console でクローズドテスト（12人・14日間）を通してから本番公開。

## v0.5.0：オンライン対戦と PC 版
- タイトルの「近くで／オンライン」で切り替え。オンラインは PeerJS（P2P）で、6けたの部屋コードで参加する。自前サーバーなし
- ゲームの進行（Engine）は `shared/`（Kotlin Multiplatform）。Android は JVM 版、PC 版は JavaScript 版を使う
  - `gradle :shared:jvmTest` … ルールのテスト／`gradle :shared:compileProductionExecutableKotlinJs` … PC 版用の JS
- 通信スクリプトは `app/src/main/assets/online/net.js`（Android は見えない WebView で、PC 版はそのまま使う）
- PC 版：`pc/src/` → `python3 pc/build.py` で `pc/out/PartyGamePC.zip`（遊ぶ.bat で Edge/Chrome のアプリ窓で開く）
  - 広告は AdSense。`game/config.js` と `site.txt` を設定（ウェブに公開したページでのみ表示）
- なんでもアリーナのカードは定番の理不尽カードゲームの効果に合わせて作り直し（全50枚、うち★14枚）
