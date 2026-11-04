"""読みもののページ（遊び方・このサイトについて・プライバシーポリシー）の、言語ごとの文。
{contact} はお問い合わせ先のURLに置きかわる。カード名・カードの文・今夜のルールは、アプリの訳（assets/i18n）を使う。"""

AD_JA = ('<p>本サービスは広告を表示しています。Android アプリでは Google LLC の Google AdMob、ウェブ版・PC 版では忍者ツールズ株式会社の忍者AdMax を利用しています。'
         'これらのサービスは、広告の配信・効果測定・不正防止のために、Cookie、広告ID、端末やブラウザの情報、おおよその位置（IP アドレスから推定）などを使用する場合があります。'
         '第三者配信事業者は Cookie を使用して、利用者が本サービスや他のサイトに過去にアクセスした際の情報に基づいて広告を配信することがあります。</p>'
         '<ul><li><a href="https://policies.google.com/technologies/partner-sites?hl=ja">Google のサービスを使用するサイトやアプリから収集した情報の Google による使用</a></li>'
         '<li><a href="https://policies.google.com/privacy?hl=ja">Google プライバシーポリシー</a></li>'
         '<li><a href="https://www.shinobi.jp/etc/policy.html">忍者ツールズ プライバシーポリシー</a></li></ul>'
         '<p>Google のパーソナライズ広告は <a href="https://adssettings.google.com/">広告設定</a> で無効にできます（Android は端末の「設定 → Google → 広告」からも変更できます）。ヨーロッパなど同意が必要な地域では、広告を表示する前に同意を確認し、あとから「広告の設定」で変更できます。</p>')

L = {
    'en': {
        'html_lang': 'en', 'brand': 'Wachapa', 'nav': ['How to play', 'About', 'Privacy', 'Play'], 'contact': 'Contact',
        'desc': 'Chaos Arena, Line It Up, Word Wolf and more — 9 party games. Play together in the browser on iPhone, Android or PC with a room code.',
        'titles': ['How to Play & Cards', 'About This Site', 'Privacy Policy'],
        'howto_intro': '<h1>How to Play & Cards</h1><p>This site lets you play 9 party games for free — together in person or over a voice call. No app install needed: just open it in the browser on iPhone, Android or PC.</p>',
        'start': '<h2>Getting started</h2><div class="box"><ol><li>One person enters a name and taps "Create a room" to get a 6-digit room code.</li><li>Everyone else taps "Join a room" and enters the same code (Android app users switch to "Online" first).</li><li>The room creator (host) picks a game and taps "Start".</li></ol><p class="muted">On iPhone, tap Safari\'s Share button → "Add to Home Screen" to play full-screen like an app. For talking games, play while on a voice call (Discord, etc.) when you\'re apart.</p></div>',
        'games_h': '9 games', 'players': '{0}+ players', 'players_range': '{0}–{1} players',
        'games': {
            'icon_arena': ('Chaos Arena (featured)', 'On your turn, draw 1 card from the deck, play 1 card from your hand and do what it says. Put a pitfall in front of someone, make everyone play rock-paper-scissors at once, lose for saying "I"… anything can happen. Losers drop out; the last one standing — or whoever meets a card\'s win condition (15 coin points, it\'s your birth month, etc.) — wins. Sometimes everyone loses together. Star cards (★) are especially strong. One "Tonight\'s Rule" is added at the start. If you catch someone breaking a speech or gesture rule, tap "Foul!" on them.'),
            'icon_wordwolf': ('Word Wolf', 'Everyone gets a word, but one player (two with 7+) gets a slightly different one — that\'s the "Wolf". Nobody knows which side they\'re on. Talk about your word and vote for who you think is the Wolf. If the Wolf gets the most votes, they can still win by guessing the Citizens\' word. Words come in similar pairs, like "Pizza" and "Burger".'),
            'icon_ngword': ('Forbidden Words', 'Everyone gets a different forbidden word, but you can\'t see your own. Chat away and try to make the others say theirs. When you catch someone, tap "Said it!". The last one standing wins. On "Hard", the forbidden words are verbal tics like "really" or "like".'),
            'icon_spy': ("Who's the Spy?", 'Everyone gets the same "location" except the Spy, who doesn\'t know it. Take turns asking questions to find the Spy. If the Spy gets the most votes, they can still win by picking the location from 12 options.'),
            'icon_sync': ('Mind Sync', 'Everyone answers prompts like "Name a red food" on their own screen. The more people with the same answer, the more points. Differences in letter case and full-/half-width characters count as the same answer.'),
            'icon_guess': ('20 Questions', 'Only the quizmaster knows the answer. Everyone else asks yes/no questions and tries to guess it in time. The guesser and the quizmaster each get 1 point.'),
            'icon_question': ("Who's Most Likely?", 'Everyone anonymously votes on "Who here is most likely to…?" and the results are revealed. Nobody can see who voted for whom.'),
            'icon_scale': ('Line It Up (new)', 'Everyone gets secret numbers from 1 to 100. Using the topic (say "Strong animals": 1 = weak, 100 = strong), describe your number with a word — 90 might be "a lion", 5 might be "an ant". Never say the number itself! Read everyone\'s hints, talk it over, and play your card when you think yours is the lowest. If someone is still holding a lower number, that\'s a mistake: you lose a life and the lower cards are discarded. Play every card with a life to spare and you all win — it\'s a team game. 119 topics.'),
            'icon_draw': ('Draw & Pass', 'First, everyone writes a prompt (like "A cat flying through the sky"). It\'s passed to the next person, who draws it. The next person sees only the drawing and writes what they think it is, then the next draws that sentence… and so on for everyone. At the end, reveal frame by frame how the prompt changed — and laugh!'),
        },
        'cards_h': 'Chaos Arena cards', 'cards_p': '50 cards in total (14 of them ★ star cards). Some cards with the same name only reveal their effect after they\'re played ("Everyone RPS" has 4 versions with different losing throws, and "Fingers Out" has 4 versions).',
        'chaos_h': "Tonight's Rules", 'chaos_p': 'At the start of the Arena, one of these is added at random (you can turn it off in the lobby).', 'colon': ': ',
        'about': '''<h1>About This Site</h1>
<p>"Wachapa" is a collection of party games to play when you get together with friends or family — or over a voice call. Chaos Arena, Line It Up, Word Wolf, Forbidden Words, Who's the Spy?, Mind Sync, 20 Questions, Who's Most Likely? and Draw & Pass: all 9 are free to play.</p>
<h2>Supported devices</h2><ul><li>iPhone / iPad (Safari — add it to your Home Screen to use it like an app)</li><li>Android (browser or the Android app)</li><li>PC (Edge, Chrome and other browsers, or the PC version's launcher)</li></ul>
<p>Any combination of devices can play in the same room using a room code.</p>
<h2>How it works</h2><p>This site doesn't use the operator's own servers. The game runs on the device of whoever created the room, and players' devices talk to each other directly (a PeerJS signaling server is used only to find each other). The names and answers you enter are never sent to the operator.</p>
<h2>Why it's free</h2><p>Ads are shown in the lobby, on result screens and during breaks between games. The ad revenue keeps the site running.</p>
<h2>Operator</h2><p>Zen (made by an individual developer)</p>
<h2>Contact</h2><p>Please send feedback and bug reports <a href="{contact}">here (GitHub)</a>.</p>''',
        'privacy': '''<h1>Privacy Policy</h1>
<p>This page explains how "Wachapa" (including the web version on this site, the PC version and the Android app; the "Service") handles user information.</p>
<h2>1. Information collected by the operator</h2><p>The operator does not collect or store users' personal information (except as described in 4. Preventing abuse). The Service does not use the operator's servers.</p>
<h2>2. Communication between devices</h2>
<p>The names you enter and the game content are exchanged only between the devices in the same room and are never sent to the operator.</p>
<ul><li><b>Online</b>: devices connect directly via WebRTC (PeerJS). To find each other, devices communicate with the PeerJS signaling server (0.peerjs.com), and on networks where a direct connection isn't possible, traffic is relayed through PeerJS relay servers. Information needed for the connection, such as IP addresses, is shared with these servers and the other devices.</li>
<li><b>Nearby (Android app)</b>: devices connect directly with Google Nearby Connections (Bluetooth / Wi-Fi). The app asks for the "Nearby devices" and "Location" permissions to find nearby devices, but it never obtains, records or sends your location itself.</li></ul>
<h2>3. Information stored on your device</h2><p>To save you typing next time, your name, the last room code you entered and your language setting are stored on your device (app storage or the browser's localStorage). The web version also stores game files on your device so it starts quickly. All of this is removed when you delete the app or clear your browser data.</p>
<h2>4. Preventing abuse</h2><p>To prevent abuse, the Service creates a random number for each device (a "device number", unrelated to advertising IDs or your device's hardware identifiers), stores it on your device and sends it to the host's device when you join an online room.</p>
<ul><li>If the operator confirms abusive behavior, the operator may publish a one-way (SHA-256 hashed) form of that device number on this site (bans.json) as a ban list. Devices on the list can no longer join or create online rooms. Names and game content are never put on the list.</li>
<li>To deal with abuse, the operator may enter an online room as an administrator to check the room (player names, hashed device numbers and the game screen), remove players or close the room. Everyone in the room is notified when an administrator enters.</li>
<li>The device number is removed when you delete the app or clear your browser data. To request removal from the ban list, please contact us.</li></ul>
<h2>5. Advertising</h2>
<p>The Service shows ads. The Android app uses Google AdMob by Google LLC, and the web and PC versions use Ninja AdMax by Ninja Tools Inc. These services may use cookies, advertising IDs, device and browser information, and approximate location (estimated from your IP address) to deliver ads, measure their performance and prevent fraud. Third-party vendors may use cookies to show ads based on your past visits to the Service and other websites.</p>
<ul><li><a href="https://policies.google.com/technologies/partner-sites?hl=en">How Google uses information from sites or apps that use its services</a></li>
<li><a href="https://policies.google.com/privacy?hl=en">Google Privacy Policy</a></li><li><a href="https://www.shinobi.jp/etc/policy.html">Ninja Tools Privacy Policy (Japanese)</a></li></ul>
<p>You can turn off personalized Google ads in <a href="https://adssettings.google.com/">Ad Settings</a> (on Android, also via Settings → Google → Ads). In regions where consent is required, such as Europe, consent is requested before ads are shown and can be changed later from "Ad settings".</p>
<h2>6. Age requirement</h2><p>The Service is intended for users aged 13 and over.</p>
<h2>7. Changes</h2><p>This policy may be updated as needed. Changes take effect when they are posted on this page.</p>
<h2>8. Contact</h2><p>Please contact us <a href="{contact}">here (GitHub)</a>.</p>
<p class="muted">Effective date: November 3, 2026 / Updated: November 4, 2026</p>''',
    },
    'ko': {
        'html_lang': 'ko', 'brand': '와챠파', 'nav': ['게임 방법', '사이트 소개', '개인정보', '플레이'], 'contact': '문의',
        'desc': '혼돈의 아레나 · 모두의 잣대 · 워드울프 등 파티 게임 9종. iPhone · Android · PC 브라우저에서 방 코드로 함께 즐길 수 있어요.',
        'titles': ['게임 방법 · 카드 목록', '이 사이트에 대해', '개인정보 처리방침'],
        'howto_intro': '<h1>게임 방법 · 카드 목록</h1><p>이 사이트에서는 다 같이 모여서(또는 통화하면서) 즐기는 파티 게임 9종을 무료로 할 수 있어요. 앱을 설치할 필요 없이 iPhone · Android · PC 브라우저에서 바로 즐길 수 있어요.</p>',
        'start': '<h2>시작하는 방법</h2><div class="box"><ol><li>한 명이 이름을 입력하고 "방 만들기"를 누르면 6자리 방 코드가 나와요.</li><li>다른 사람들은 "방 참가하기"에서 같은 방 코드를 입력해요 (Android 앱은 "온라인"으로 바꾼 다음에).</li><li>방을 만든 사람(방장)이 게임을 고르고 "시작하기"를 눌러요.</li></ol><p class="muted">iPhone은 Safari의 공유 버튼 → "홈 화면에 추가"를 하면 앱처럼 전체 화면으로 즐길 수 있어요. 말로 하는 게임은 떨어져 있을 때 통화 앱(카톡 · 디스코드 등)을 켜 놓고 즐기세요.</p></div>',
        'games_h': '게임 9종', 'players': '{0}명~', 'players_range': '{0}~{1}명',
        'games': {
            'icon_arena': ('혼돈의 아레나 (강추)', '내 턴이 오면 덱에서 1장 뽑고, 손패에서 1장을 내서 적힌 대로 해요. 누군가의 앞에 함정을 놓거나, 다 같이 한꺼번에 가위바위보를 하거나, "나"라고 말하면 탈락이 되거나… 무슨 일이 일어날지 몰라요. 진 사람은 빠지고, 마지막 한 명이 되거나 카드의 승리 조건(금화 15포인트 모으기, 생일 달인 사람이 내기 등)을 채운 사람이 승리. 모두 함께 지는 일도 있어요. ★가 붙은 스타 카드는 특히 강력해요. 게임 시작 때 "오늘 밤의 룰"이 하나 추가돼요. 말이나 몸짓 룰을 어긴 사람을 발견하면 그 사람의 "위반!"을 누르세요.'),
            'icon_wordwolf': ('워드울프', '모두에게 제시어가 주어지지만, 한 명(7명 이상이면 두 명)만 조금 다른 제시어를 받아요. 그 사람이 "울프"예요. 내가 어느 쪽인지는 몰라요. 제시어에 대해 이야기하고 울프라고 생각하는 사람에게 투표해요. 울프가 최다 득표하면, 울프는 시민의 제시어를 맞혀서 역전승할 수 있어요. 제시어는 "짜장면"과 "짬뽕"처럼 비슷한 것끼리 짝지어져 있어요.'),
            'icon_ngword': ('금지어 게임', '모두에게 각자 다른 금지어가 붙어요. 내 단어만 안 보여요. 수다를 떨면서 다른 사람이 자기 금지어를 말하게 만드세요. 말한 사람을 발견하면 "말했다!"를 눌러요. 끝까지 남은 사람이 승리. "하드"로 하면 "진짜", "대박" 같은 말버릇이 금지어가 돼요.'),
            'icon_spy': ('스파이는 누구?', '모두에게 같은 "장소"가 주어지지만, 스파이만 장소를 몰라요. 차례로 질문하면서 스파이를 찾아요. 스파이가 최다 득표하면, 스파이는 12개 후보 중에서 장소를 맞혀 역전승할 수 있어요.'),
            'icon_sync': ('다 같이 싱크로', '"빨간 음식 하면?" 같은 문제에 모두 각자 화면으로 답해요. 같은 답을 한 사람이 많을수록 점수를 얻어요. 띄어쓰기나 대소문자 차이는 같은 답으로 쳐요.'),
            'icon_guess': ('스무고개', '출제자만 정답을 알아요. 다른 사람들은 "예/아니요"로 대답할 수 있는 질문을 해서 시간 안에 맞혀요. 맞힌 사람과 출제자에게 1점.'),
            'icon_question': ('솔직 질문', '"이 중에서 가장 ○○한 사람은?"에 모두 익명으로 투표하고 결과를 발표해요. 누가 누구에게 투표했는지는 표시되지 않아요.'),
            'icon_scale': ('모두의 잣대 (NEW)', '모두에게 1~100 중 비밀 숫자가 배달돼요. 주제(예: "강해 보이는 동물"이면 1 = 약함, 100 = 강함)에 맞춰 내 숫자를 "말"로 표현해요. 90이면 "사자", 5면 "개미"처럼요. 숫자 자체를 말하면 안 돼요! 모두의 힌트를 보고 이야기하면서, 내 숫자가 제일 작다고 생각되면 "내기". 아직 누군가 더 작은 숫자를 들고 있었다면 실수라서 목숨이 하나 줄어요(그 숫자는 버려져요). 목숨을 남긴 채 모두 다 내면 클리어! 다 함께 협력하는 게임이에요. 주제 119개.'),
            'icon_draw': ('그림 릴레이', '먼저 모두가 주제(예: "고양이가 하늘을 날고 있다")를 써요. 그게 옆 사람에게 넘어가면 그림으로 그려요. 다음 사람은 그 그림만 보고 무슨 그림인지 글로 답하고, 또 다음 사람이 그 글을 그림으로… 모두 돌아가며 반복해요. 마지막에 주제가 어떻게 바뀌었는지 한 장씩 공개하며 웃어 봐요.'),
        },
        'cards_h': '혼돈의 아레나 카드', 'cards_p': '모두 50장 (그중 ★ 스타 카드 14장). 같은 이름이라도 내고 나서야 효과를 알 수 있는 카드가 있어요 ("다 같이 가위바위보"는 지는 손이 4종류, "하나 둘 셋 손가락"은 4종류).',
        'chaos_h': '오늘 밤의 룰', 'chaos_p': '아레나를 시작할 때 다음 중 하나가 무작위로 추가돼요 (로비에서 "없음"으로 할 수도 있어요).', 'colon': ': ',
        'about': '''<h1>이 사이트에 대해</h1>
<p>"와챠파"는 친구나 가족과 모였을 때, 또는 통화하면서 즐길 수 있는 파티 게임 모음이에요. 혼돈의 아레나 · 모두의 잣대 · 워드울프 · 금지어 게임 · 스파이는 누구? · 다 같이 싱크로 · 스무고개 · 솔직 질문 · 그림 릴레이 9종을 무료로 즐길 수 있어요.</p>
<h2>사용할 수 있는 기기</h2><ul><li>iPhone · iPad (Safari. 홈 화면에 추가하면 앱처럼 쓸 수 있어요)</li><li>Android (브라우저 또는 Android 앱)</li><li>PC (Edge · Chrome 등의 브라우저 또는 PC 버전 실행 파일)</li></ul>
<p>어떤 기기끼리든 방 코드로 같은 방에서 즐길 수 있어요.</p>
<h2>작동 방식</h2><p>이 사이트는 운영자의 서버를 사용하지 않아요. 게임 진행은 방을 만든 사람의 기기 안에서 이루어지고, 참가자의 기기끼리 직접 통신해요 (서로를 찾을 때만 PeerJS 중개 서버를 사용해요). 그래서 입력한 이름이나 답이 운영자에게 전달되는 일은 없어요.</p>
<h2>무료로 즐길 수 있는 이유</h2><p>로비와 결과 화면, 게임 사이에 광고를 표시하고 있어요. 광고 수익으로 운영하고 있어요.</p>
<h2>운영자</h2><p>Zen (개인이 제작하고 있어요)</p>
<h2>문의</h2><p>의견이나 버그 신고는 <a href="{contact}">여기(GitHub)</a>로 보내 주세요.</p>''',
        'privacy': '''<h1>개인정보 처리방침</h1>
<p>"와챠파"(이 사이트의 웹 버전, PC 버전, Android 앱을 포함합니다. 이하 "본 서비스")에서 이용자의 정보를 어떻게 다루는지 설명합니다.</p>
<h2>1. 운영자가 수집하는 정보</h2><p>운영자는 이용자의 개인정보를 수집 · 저장하지 않습니다(4. 비매너 행위 방지 제외). 본 서비스는 운영자의 서버를 사용하지 않습니다.</p>
<h2>2. 기기 간 통신</h2>
<p>입력한 이름과 게임 내용은 같은 방에 참가한 기기 사이에서만 주고받으며, 운영자에게 전송되지 않습니다.</p>
<ul><li><b>온라인</b>: 기기끼리 WebRTC(PeerJS)로 직접 연결합니다. 상대를 찾기 위해 PeerJS 중개 서버(0.peerjs.com)와 통신하고, 직접 연결되지 않는 회선에서는 PeerJS 중계 서버를 거칩니다. 이때 통신에 필요한 IP 주소 등이 중개 · 중계 서버와 상대 기기에 전달됩니다.</li>
<li><b>근처에서 (Android 앱)</b>: Google Nearby Connections(블루투스 · Wi-Fi)로 근처 기기와 직접 연결합니다. 근처 기기를 찾기 위해 "근처 기기"와 "위치" 권한을 요청하지만, 위치 정보 자체를 가져오거나 기록 · 전송하지 않습니다.</li></ul>
<h2>3. 기기에 저장하는 정보</h2><p>다음 입력을 줄이기 위해 입력한 이름, 마지막으로 입력한 방 코드, 언어 설정을 기기 안(앱 저장 공간 또는 브라우저의 localStorage)에 저장합니다. 웹 버전은 빠르게 실행할 수 있도록 게임 파일을 기기에 저장합니다. 모두 앱을 삭제하거나 브라우저 데이터를 삭제하면 사라집니다.</p>
<h2>4. 비매너 행위 방지</h2><p>비매너 행위를 막기 위해 본 서비스는 기기마다 무작위 번호(기기 번호. 광고 ID나 기기 고유 식별 번호와는 관계없습니다)를 만들어 기기에 저장하고, 온라인 방에 참가할 때 방을 만든 사람의 기기로 보냅니다.</p>
<ul><li>운영자가 비매너 행위를 확인한 경우, 그 기기 번호를 원래대로 되돌릴 수 없는 형태(SHA-256 해시값)로 바꾼 것을 참가 금지 목록으로 이 사이트(bans.json)에 게시할 수 있습니다. 목록에 있는 기기는 온라인 방에 참가하거나 방을 만들 수 없게 됩니다. 이름이나 게임 내용은 목록에 올리지 않습니다.</li>
<li>운영자는 비매너 행위에 대응하기 위해 관리자로서 온라인 방에 들어가 방 상황(참가자 이름 · 기기 번호의 해시값 · 게임 화면)을 확인하거나, 참가자를 퇴장시키거나, 방을 닫을 수 있습니다. 관리자가 들어오면 방에 있는 모두에게 표시됩니다.</li>
<li>기기 번호는 앱을 삭제하거나 브라우저 데이터를 지우면 사라집니다. 참가 금지 해제를 원하시면 문의처로 연락해 주세요.</li></ul>
<h2>5. 광고</h2>
<p>본 서비스는 광고를 표시합니다. Android 앱은 Google LLC의 Google AdMob을, 웹 버전 · PC 버전은 Ninja Tools Inc.의 Ninja AdMax를 이용합니다. 이 서비스들은 광고 게재 · 효과 측정 · 부정 방지를 위해 쿠키, 광고 ID, 기기나 브라우저 정보, 대략적인 위치(IP 주소로 추정) 등을 사용할 수 있습니다. 제3자 광고 사업자는 쿠키를 사용하여 이용자가 과거에 본 서비스나 다른 사이트를 방문한 정보를 바탕으로 광고를 게재할 수 있습니다.</p>
<ul><li><a href="https://policies.google.com/technologies/partner-sites?hl=ko">Google 서비스를 사용하는 사이트 또는 앱의 정보를 Google이 사용하는 방법</a></li>
<li><a href="https://policies.google.com/privacy?hl=ko">Google 개인정보처리방침</a></li><li><a href="https://www.shinobi.jp/etc/policy.html">Ninja Tools 개인정보 처리방침 (일본어)</a></li></ul>
<p>Google의 맞춤 광고는 <a href="https://adssettings.google.com/">광고 설정</a>에서 끌 수 있습니다 (Android는 기기의 "설정 → Google → 광고"에서도 변경할 수 있습니다). 유럽 등 동의가 필요한 지역에서는 광고를 표시하기 전에 동의를 확인하며, 나중에 "광고 설정"에서 변경할 수 있습니다.</p>
<h2>6. 이용 연령</h2><p>본 서비스는 만 13세 이상을 대상으로 합니다.</p>
<h2>7. 개정</h2><p>본 방침은 필요에 따라 개정합니다. 개정된 내용은 이 페이지에 게시한 시점부터 효력을 가집니다.</p>
<h2>8. 문의</h2><p><a href="{contact}">여기(GitHub)</a>로 보내 주세요.</p>
<p class="muted">제정일: 2026년 11월 3일 / 개정일: 2026년 11월 4일</p>''',
    },
    'zh': {
        'html_lang': 'zh-CN', 'brand': 'Wachapa', 'nav': ['玩法', '关于本站', '隐私', '开始玩'], 'contact': '联系我们',
        'desc': '离谱竞技场、大家的尺子、谁是卧底等 9 款派对游戏。用房间号就能在 iPhone、安卓、PC 的浏览器上一起玩。',
        'titles': ['玩法 · 卡牌一览', '关于本站', '隐私政策'],
        'howto_intro': '<h1>玩法 · 卡牌一览</h1><p>在本站可以免费玩 9 款派对游戏，适合大家聚在一起（或边语音通话边）玩。无需安装 App，直接用 iPhone、安卓、PC 的浏览器就能玩。</p>',
        'start': '<h2>开始方法</h2><div class="box"><ol><li>一人输入名字后点“创建房间”，会显示 6 位房间号。</li><li>其他人点“加入房间”，输入同一个房间号（安卓 App 请先切换到“在线”）。</li><li>创建房间的人（房主）选择游戏后点“开始”。</li></ol><p class="muted">iPhone 用 Safari 的分享按钮 →“添加到主屏幕”，就能像 App 一样全屏玩。需要说话的游戏，不在一起时请边用语音通话（微信 · Discord 等）边玩。</p></div>',
        'games_h': '9 款游戏', 'players': '{0} 人起', 'players_range': '{0}～{1} 人',
        'games': {
            'icon_arena': ('离谱竞技场（强推）', '轮到你时，从牌堆抽 1 张牌，再从手牌中打出 1 张，按牌上写的做。在别人面前放陷阱、全员同时石头剪刀布、说了“我”就输……什么都可能发生。输了的人出局，成为最后一人、或满足卡牌胜利条件（攒够 15 分金币、本月生日的人打出等）的人获胜。也可能全员一起输。带★的明星卡特别强。每局开始时会追加一条“今晚的规则”。发现有人违反说话或动作类规则时，点他的“犯规！”。'),
            'icon_wordwolf': ('谁是卧底', '每个人都会拿到一个词语，但有一人（7 人以上时两人）的词语略有不同，他就是“卧底”。自己是哪一方并不知道。围绕词语聊天，投票选出你认为的卧底。卧底得票最多时，只要猜中平民的词语就能逆转获胜。词语都是“饺子”和“馄饨”这样相似的一对。'),
            'icon_ngword': ('禁语游戏', '每个人都有不同的禁语，只有自己的看不到。边聊天边引诱别人说出自己的禁语。抓到有人说了就点“说了！”。坚持到最后的人获胜。选“困难”的话，禁语会变成“真的”“然后”这类口头禅。'),
            'icon_spy': ('谁是间谍', '除了间谍，所有人都会拿到同一个“地点”。大家轮流提问找出间谍。间谍得票最多时，只要从 12 个选项中猜中地点就能逆转获胜。'),
            'icon_sync': ('心有灵犀', '大家在自己的屏幕上回答“说到红色的食物？”这类题目。答案相同的人越多，得分越高。全角半角、大小写的差别算作同一个答案。'),
            'icon_guess': ('是非猜猜看', '只有出题人知道答案。其他人提出能用“是/不是”回答的问题，在限时内猜出来。猜中的人和出题人各得 1 分。'),
            'icon_question': ('真心话投票', '大家匿名投票回答“我们之中最可能○○的人是谁？”，然后公布结果。不会显示谁投给了谁。'),
            'icon_scale': ('大家的尺子（新）', '每个人会拿到 1～100 之间的秘密数字。按照题目（比如“看起来很强的动物”：1＝弱，100＝强），用一个“词”来表示自己的数字——90 可以说“狮子”，5 可以说“蚂蚁”。绝对不能直接说出数字！看大家的提示、一起商量，觉得自己的数字最小时就“出牌”。如果还有人手里有更小的数字，就算失误，扣一条命（那些更小的数字会被弃掉）。留着命把牌全部出完就过关！这是全员合作的游戏，共有 119 个题目。'),
            'icon_draw': ('你画我猜接力', '首先每个人写下一个题目（比如“猫在天上飞”）。题目传给下一个人，由他画出来。再下一个人只看画，用文字写出画的是什么，接着下一个人再把这句话画出来……全员轮一遍。最后逐格公布题目是怎么一步步变样的，一起笑翻吧。'),
        },
        'cards_h': '离谱竞技场的卡牌', 'cards_p': '共 50 张（其中★明星卡 14 张）。有些同名卡牌要打出后才知道效果（“全员石头剪刀布”有 4 种输法，“一起比手指”也有 4 种）。',
        'chaos_h': '今晚的规则', 'chaos_p': '竞技场开始时，会从下面随机追加一条（也可以在大厅里设为“无”）。', 'colon': '：',
        'about': '''<h1>关于本站</h1>
<p>“Wachapa”是一套适合和朋友、家人聚会时，或边语音通话边玩的派对游戏合集。离谱竞技场、大家的尺子、谁是卧底、禁语游戏、谁是间谍、心有灵犀、是非猜猜看、真心话投票、你画我猜接力，9 款游戏都可以免费玩。</p>
<h2>支持的设备</h2><ul><li>iPhone · iPad（Safari，添加到主屏幕后可以像 App 一样使用）</li><li>安卓（浏览器或安卓 App）</li><li>电脑（Edge、Chrome 等浏览器，或 PC 版启动文件）</li></ul>
<p>不论什么设备组合，都能用房间号在同一个房间里一起玩。</p>
<h2>运作方式</h2><p>本站不使用运营者自己的服务器。游戏在创建房间者的设备上运行，玩家的设备之间直接通信（只有在寻找对方时使用 PeerJS 的信令服务器）。因此，你输入的名字和答案不会发送给运营者。</p>
<h2>为什么可以免费玩</h2><p>我们会在大厅、结果画面以及游戏间隙显示广告，靠广告收入来运营。</p>
<h2>运营者</h2><p>Zen（个人开发）</p>
<h2>联系我们</h2><p>意见和问题反馈请发送到<a href="{contact}">这里（GitHub）</a>。</p>''',
        'privacy': '''<h1>隐私政策</h1>
<p>本页面说明“Wachapa”（包括本站的网页版、PC 版及安卓 App，以下称“本服务”）如何处理用户信息。</p>
<h2>1. 运营者收集的信息</h2><p>运营者不收集、不保存用户的个人信息（“4. 防止恶意行为”所述情况除外）。本服务不使用运营者的服务器。</p>
<h2>2. 设备之间的通信</h2>
<p>你输入的名字和游戏内容只在同一房间内的设备之间传输，不会发送给运营者。</p>
<ul><li><b>在线</b>：设备之间通过 WebRTC（PeerJS）直接连接。为了找到对方，会与 PeerJS 信令服务器（0.peerjs.com）通信；在无法直接连接的网络环境下，会经由 PeerJS 中继服务器传输。此时，连接所需的 IP 地址等信息会传给这些服务器和对方的设备。</li>
<li><b>附近（安卓 App）</b>：通过 Google Nearby Connections（蓝牙 · Wi-Fi）与附近的设备直接连接。为了寻找附近的设备，App 会请求“附近的设备”和“位置信息”权限，但不会获取、记录或发送位置信息本身。</li></ul>
<h2>3. 保存在设备上的信息</h2><p>为了让你下次少输入一些内容，你输入的名字、最后输入的房间号和语言设置会保存在设备内（App 存储空间或浏览器的 localStorage）。网页版为了快速启动，还会把游戏文件保存在设备上。删除 App 或清除浏览器数据后，这些信息都会被删除。</p>
<h2>4. 防止恶意行为</h2><p>为防止恶意行为，本服务会为每台设备生成一个随机编号（设备编号，与广告 ID 或设备本身的识别码无关），保存在设备内，并在加入在线房间时发送给房主的设备。</p>
<ul><li>如果运营者确认存在恶意行为，可能会将该设备编号转换为无法还原的形式（SHA-256 哈希值），作为禁止加入名单发布在本站（bans.json）。名单上的设备将无法加入或创建在线房间。名字和游戏内容不会放入名单。</li>
<li>为处理恶意行为，运营者可能会以管理员身份进入在线房间，查看房间情况（玩家名字、设备编号的哈希值、游戏画面），将玩家移出房间或关闭房间。管理员进入时，房间里的所有人都会看到提示。</li>
<li>删除 App 或清除浏览器数据后，设备编号会消失。如需解除禁止，请联系我们。</li></ul>
<h2>5. 广告</h2>
<p>本服务会显示广告。安卓 App 使用 Google LLC 的 Google AdMob，网页版和 PC 版使用忍者工具株式会社（Ninja Tools Inc.）的忍者 AdMax。这些服务可能会为了投放广告、衡量效果和防止作弊，使用 Cookie、广告 ID、设备或浏览器信息、大致位置（根据 IP 地址推测）等。第三方广告商可能会使用 Cookie，根据用户过去访问本服务或其他网站的情况投放广告。</p>
<ul><li><a href="https://policies.google.com/technologies/partner-sites?hl=zh-CN">Google 如何使用来自使用其服务的网站或应用的信息</a></li>
<li><a href="https://policies.google.com/privacy?hl=zh-CN">Google 隐私权政策</a></li><li><a href="https://www.shinobi.jp/etc/policy.html">忍者工具隐私政策（日语）</a></li></ul>
<p>Google 的个性化广告可以在<a href="https://adssettings.google.com/">广告设置</a>中关闭（安卓也可以在设备的“设置 → Google → 广告”中更改）。在欧洲等需要征得同意的地区，会在显示广告前确认同意，之后也可以在“广告设置”中更改。</p>
<h2>6. 适用年龄</h2><p>本服务面向 13 岁及以上的用户。</p>
<h2>7. 修订</h2><p>本政策会根据需要进行修订。修订后的内容自发布在本页面时起生效。</p>
<h2>8. 联系我们</h2><p>请发送到<a href="{contact}">这里（GitHub）</a>。</p>
<p class="muted">制定日期：2026 年 11 月 3 日／修订日期：2026 年 11 月 4 日</p>''',
    },
}
