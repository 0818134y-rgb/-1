# 収益化の手順（広告 → 銀行振込）

アプリ・サイト側の準備（広告の表示、同意画面、審査に必要なページ、ads.txt の自動作成、公開用の署名）はすべて済んでいます。
残りは「本人でないとできない手続き」です。終わったら、出てきた ID を Claude に送れば、組み込んで公開し直します。

| 端末 | 広告サービス | 状態 |
|---|---|---|
| Android アプリ | Google AdMob（バナー＋ゲームの合間の全画面広告） | ID を入れれば公開できる |
| iPhone・PC・ブラウザ | Google AdSense（バナー＋自動広告＋ゲームの合間の広告） | 独自ドメインと審査が必要 |

収益は AdMob・AdSense それぞれで月ごとに確定し、基準額を超えると翌月21日ごろ銀行口座に振り込まれます。

---

## 1. AdMob（Android アプリの広告）

1. https://admob.google.com に Google アカウントで登録（国：日本、通貨：円）
2. 「お支払い」でお支払いプロファイル（氏名・住所）を登録
3. 「アプリ」→「アプリを追加」→ Android・「まだ公開されていない」→ 名前「わちゃパ」
4. 広告ユニットを2つ作る
   - 「バナー」…名前は「banner」
   - 「インタースティシャル」…名前は「break」
5. 「プライバシーとメッセージ」→「欧州の規制」のメッセージを作成して公開（同意画面。アプリ側は対応済み）
6. Claude に次の3つを送る
   - アプリ ID（ca-app-pub-xxxxxxxx~yyyyyyyy）
   - バナーの広告ユニット ID（ca-app-pub-xxxxxxxx/zzzz）
   - インタースティシャルの広告ユニット ID

> 自分で本物の広告をタップしないこと（AdMob の規約違反でアカウントが止まる）。テスト版の APK は常にテスト広告です。

## 2. Google Play（アプリを公開する）

1. https://play.google.com/console で個人の開発者アカウントを登録（登録料 25 ドル・1回だけ、本人確認あり）
2. アプリを作成 → Claude が作った AAB（app-release.aab）をアップロード
3. ストアの掲載情報は store/listing.md の文と画像をそのまま使う
4. 2023年11月以降に作った個人アカウントは、**12人以上のテスターで14日間のクローズドテスト**をしてから本番公開を申請する
5. 本番公開の審査が通ったら公開

## 3. 独自ドメイン（AdSense と app-ads.txt に必要）

AdSense は github.io のようなサブドメインでは審査を受けられないため、自分のドメインが必要です（年1,000〜2,000円ほど）。

1. お名前.com・Cloudflare・Xserverドメインなどでドメインを買う（例：party-game.jp）
2. DNS に次を登録
   - `@`（ドメインそのもの）の A レコード：185.199.108.153 / 185.199.109.153 / 185.199.110.153 / 185.199.111.153
   - `www` の CNAME：0818134y-rgb.github.io
3. Claude にドメイン名を送る（CNAME ファイルの作成と GitHub Pages の設定を案内します）
4. Play の掲載情報の「ウェブサイト」にこのドメインを書く（AdMob の app-ads.txt 確認に使われる）

## 4. iPhone・PC・ブラウザの広告（忍者AdMax）

AdMob に先に登録すると、Google が受け取り用の AdSense アカウントを自動で作りますが、このアカウントにはサイトを追加できません。
AdSense はポリシー上1人1アカウントのため、ウェブ版には Google 以外の広告サービス「忍者AdMax」を使います（審査なし・表示されるだけでも報酬・銀行振込）。

1. 忍者AdMax に登録（忍者ツールズのアカウント）し、振込先の銀行口座を登録
2. サイトに https://wachapa.com/ を登録
3. 広告枠を作る（サイズは近いものでよい）
   - スマホ用バナー 320×50 → ファイル名 banner_sp
   - PC 用バナー 728×90 → banner_pc
   - 休憩画面用 300×250 → break
4. それぞれの「広告タグ」をコピーして Claude に送る（pc/adtags/ に置いて公開し直す）

## 5. 銀行振込で受け取る（AdMob）

1. 「お支払い」→「お支払い方法」→ 銀行口座（銀行振込）を追加
2. 少額のテスト入金が届いたら、その金額を入力して口座を確認
3. 「お支払い」→「設定」→ 税務情報（日本在住の個人は W-8BEN）を提出
4. 収益が一定額に達すると本人確認（身分証）と、住所確認用の PIN が郵送で届く → PIN を入力
5. 以後、残高が支払い基準額を超えた月の翌月21日ごろに振り込まれる（AdSense の日本の基準額は 8,000円）

---

## Claude に送るもの（まとめ）

```
# プロジェクトの一番上に monetization.properties として保存される（公開リポジトリには入れない）
admob.appId=ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy
admob.banner=ca-app-pub-xxxxxxxxxxxxxxxx/zzzzzzzzzz
admob.interstitial=ca-app-pub-xxxxxxxxxxxxxxxx/wwwwwwwwww
site.domain=party-game.jp
adsense.client=ca-pub-xxxxxxxxxxxxxxxx
# 任意：ディスプレイ広告ユニットを自分で作った場合
adsense.banner=
adsense.break=
# H5 Games Ads が認められたら true
adsense.h5games=false
```
