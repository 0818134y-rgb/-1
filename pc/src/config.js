/*
 * ウェブ版（iPhone・PC・スマホのブラウザ）の設定。公開用は pc/build.py が monetization.properties から作り直す。
 */
window.PG_CONFIG = {
  adsenseClient: '',     // AdSense のサイト運営者 ID（例: 'ca-pub-1234567890123456'）
  bannerSlot: '',        // ロビー・結果画面の下に出すディスプレイ広告ユニットの ID
  breakSlot: '',         // ゲームの合間の休憩画面に出すディスプレイ広告ユニットの ID（H5 Games Ads を使わないとき）
  h5games: false,        // AdSense の「H5 Games Ads」の利用が認められたら true（合間にゲーム向けの全画面広告）
  adTest: false,         // true にすると H5 Games Ads がテスト広告になる
};
