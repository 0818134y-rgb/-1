"""「配信・動画OK」ページの本文（日本語・英語・韓国語・中国語）。pages.py が streaming.html などにする。"""

L = {
    'ja': ('配信・動画のガイドライン', '''<h1>配信・動画のガイドライン</h1>
<p>わちゃパは、<b>ゲーム実況・生配信・動画投稿を歓迎しています</b>。下の範囲なら、運営者への連絡や許可は必要ありません。</p>
<h2>OK なこと</h2><div class="box"><ul>
<li>YouTube・Twitch・ニコニコ・TikTok・X などでの生配信、実況動画、切り抜き、ショート動画</li>
<li><b>収益化</b>（広告収入・メンバーシップ・投げ銭・スーパーチャットなど）。個人・グループ・VTuber・事務所所属のどれでもOKです</li>
<li>ゲーム内のBGM・効果音を、そのまま配信や動画に流すこと（BGMも効果音もわちゃパのために作ったもので、運営者が権利を主張することはありません）</li>
<li>ゲーム画面・キャラクターの絵・ロゴを、サムネイルや配信画面の飾りに使うこと</li>
<li>部屋コードを画面に映して、視聴者といっしょに遊ぶこと</li></ul></div>
<h2>してほしくないこと</h2><div class="box"><ul>
<li>わちゃパを自分が作ったゲームのように見せること</li>
<li>ゲームそのもの（ファイル）を改変・再配布・販売すること</li>
<li>キャラクターの絵を、わちゃパの紹介以外のグッズや商品にすること</li></ul></div>
<h2>できればお願いしたいこと（任意）</h2><ul>
<li>概要欄などに「わちゃパ https://wachapa.com/」と書いてもらえるとうれしいです</li>
<li>X（旧Twitter）でハッシュタグ <b>#わちゃパ</b> を付けてもらえると、運営者が見に行きます</li></ul>
<h2>視聴者参加で遊ぶときのコツ</h2><ul>
<li>1つの部屋に入れるのは8人までです。配信者がホスト（部屋を作る人）になりましょう</li>
<li>荒らしが入ったら、ロビーで「退出させる」を押せます。部屋コードはゲームを始める直前に映すと安心です</li>
<li>話すゲーム（ワードウルフ・NGワード・みんなのものさし）は、Discord などの通話をつなぎながら遊ぶと盛り上がります。お絵かき伝言・なんでもアリーナは、通話なしでも遊べます</li>
<li>PC のブラウザなら、配信ソフトでウィンドウをそのまま取り込めます</li></ul>
<p>企業の広告案件や、テレビ・雑誌などで使いたい場合は、<a href="{contact}">お問い合わせ</a>からご連絡ください。</p>'''),

    'en': ('Streaming & Video Guidelines', '''<h1>Streaming & Video Guidelines</h1>
<p>Wachapa <b>welcomes let's plays, live streams and videos</b>. Within the scope below, you don't need to contact us or ask for permission.</p>
<h2>You're welcome to</h2><div class="box"><ul>
<li>Live stream, make let's plays, clips and shorts on YouTube, Twitch, TikTok, X and any other platform</li>
<li><b>Monetize</b> them (ad revenue, memberships, tips, Super Chats and so on) — whether you're an individual, a group, a VTuber or with an agency</li>
<li>Play the in-game music and sound effects on your stream or video (all of it was made for Wachapa, and we will never claim rights to it)</li>
<li>Use game screenshots, character art and the logo in your thumbnails and stream overlays</li>
<li>Show the room code on screen and play together with your viewers</li></ul></div>
<h2>Please don't</h2><div class="box"><ul>
<li>Present Wachapa as a game you made</li>
<li>Modify, redistribute or sell the game files themselves</li>
<li>Turn the character art into merchandise or products other than for introducing Wachapa</li></ul></div>
<h2>Optional, but appreciated</h2><ul>
<li>Mention "Wachapa https://wachapa.com/" in your description</li>
<li>Use the hashtag <b>#wachapa</b> on X so we can come and watch</li></ul>
<h2>Tips for playing with viewers</h2><ul>
<li>Up to 8 players can be in one room. The streamer should be the host (the one who creates the room)</li>
<li>If a troll joins, you can tap "Remove" in the lobby. Showing the room code just before you start is safest</li>
<li>Talking games (Word Wolf, Forbidden Words, Line It Up) are best with a voice call such as Discord. Draw & Pass and Chaos Arena work without a call</li>
<li>On a PC browser, you can capture the window directly in your streaming software</li></ul>
<p>For advertising campaigns or use on TV, in magazines and so on, please <a href="{contact}">contact us</a>.</p>'''),

    'ko': ('방송 · 영상 가이드라인', '''<h1>방송 · 영상 가이드라인</h1>
<p>와챠파는 <b>게임 실황, 생방송, 영상 업로드를 환영해요</b>. 아래 범위라면 운영자에게 연락하거나 허락받을 필요가 없어요.</p>
<h2>해도 되는 것</h2><div class="box"><ul>
<li>YouTube · Twitch · 치지직 · SOOP · TikTok · X 등에서의 생방송, 실황 영상, 클립, 쇼츠</li>
<li><b>수익 창출</b>(광고 수익 · 멤버십 · 후원 · 슈퍼챗 등). 개인 · 그룹 · 버튜버 · 소속사 모두 OK예요</li>
<li>게임 속 BGM과 효과음을 그대로 방송이나 영상에 쓰기(BGM도 효과음도 와챠파를 위해 만든 것이라, 운영자가 권리를 주장하는 일은 없어요)</li>
<li>게임 화면 · 캐릭터 그림 · 로고를 썸네일이나 방송 화면 꾸미기에 쓰기</li>
<li>방 코드를 화면에 띄워 시청자와 함께 즐기기</li></ul></div>
<h2>하지 말아 주세요</h2><div class="box"><ul>
<li>와챠파를 직접 만든 게임처럼 보이게 하기</li>
<li>게임 자체(파일)를 수정 · 재배포 · 판매하기</li>
<li>캐릭터 그림을 와챠파 소개 이외의 굿즈나 상품으로 만들기</li></ul></div>
<h2>가능하면 부탁드려요(선택)</h2><ul>
<li>설명란 등에 "와챠파 https://wachapa.com/"을 적어 주시면 기뻐요</li>
<li>X(구 트위터)에 해시태그 <b>#wachapa</b>를 달아 주시면 운영자가 보러 갈게요</li></ul>
<h2>시청자 참여로 즐길 때의 팁</h2><ul>
<li>한 방에는 8명까지 들어갈 수 있어요. 방송인이 호스트(방을 만드는 사람)가 되세요</li>
<li>비매너 참가자가 들어오면 대기실에서 "내보내기"를 누를 수 있어요. 방 코드는 게임 시작 직전에 띄우면 안심이에요</li>
<li>말하는 게임(워드울프 · 금지어 게임 · 모두의 잣대)은 Discord 등으로 통화하면서 하면 더 재미있어요. 그림 릴레이와 혼돈의 아레나는 통화 없이도 즐길 수 있어요</li>
<li>PC 브라우저라면 방송 프로그램에서 창을 그대로 캡처할 수 있어요</li></ul>
<p>기업 광고 협업이나 TV · 잡지 등에서 사용하고 싶다면 <a href="{contact}">문의</a>해 주세요.</p>'''),

    'zh': ('直播 · 视频指南', '''<h1>直播 · 视频指南</h1>
<p>Wachapa <b>欢迎游戏实况、直播和投稿视频</b>。只要在下面的范围内，无需联系运营者或取得许可。</p>
<h2>可以做的事</h2><div class="box"><ul>
<li>在 YouTube、Twitch、哔哩哔哩、抖音、TikTok、X 等平台直播、投稿实况视频、剪辑和短视频</li>
<li><b>开启收益</b>（广告收入、会员、打赏、Super Chat 等）。个人、团体、虚拟主播、签约主播都可以</li>
<li>在直播或视频中直接使用游戏里的 BGM 和音效（BGM 和音效都是为 Wachapa 制作的，运营者不会主张任何权利）</li>
<li>把游戏画面、角色插画和 Logo 用在封面或直播画面装饰上</li>
<li>把房间号显示在画面上，和观众一起玩</li></ul></div>
<h2>请不要</h2><div class="box"><ul>
<li>把 Wachapa 说成是自己做的游戏</li>
<li>修改、二次发布或出售游戏本身（文件）</li>
<li>把角色插画做成介绍 Wachapa 以外用途的周边或商品</li></ul></div>
<h2>如果可以的话（可选）</h2><ul>
<li>在简介里写上“Wachapa https://wachapa.com/”，我们会很开心</li>
<li>在 X（原 Twitter）加上话题标签 <b>#wachapa</b>，运营者会去看</li></ul>
<h2>和观众一起玩的小技巧</h2><ul>
<li>一个房间最多 8 人。建议由主播当房主（创建房间的人）</li>
<li>如果有人捣乱，可以在大厅点“请出房间”。在开始游戏前再显示房间号会更安心</li>
<li>需要说话的游戏（谁是卧底、禁语游戏、大家的尺子）配合 Discord 等语音通话更热闹。你画我猜接力和离谱竞技场不通话也能玩</li>
<li>用 PC 浏览器的话，可以直接在直播软件里捕获窗口</li></ul>
<p>企业广告合作，或想在电视、杂志等使用时，请<a href="{contact}">联系我们</a>。</p>'''),
}
