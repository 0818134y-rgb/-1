"""ウェブ版に置く読みもののページ（遊び方・カード一覧・このサイトについて・プライバシーポリシー）を作る。
AdSense の審査では、ゲームの画面だけでなく、内容のあるページと運営者情報・プライバシーポリシーが求められる。
"""
import html, json, os, subprocess
import pages_text
LANGS = ['ja', 'en', 'ko', 'zh']
LANG_LABEL = {'ja': '日本語', 'en': 'English', 'ko': '한국어', 'zh': '中文'}


def fname(base, lang):
    """howto.html / howto.en.html …"""
    return base + '.html' if lang == 'ja' else f'{base}.{lang}.html'

CONTACT = 'mailto:wachapa.com@gmail.com'

CSS = '''
:root{--night:#15102D;--card:#221F45;--lantern:#FFB347;--berry:#FF5C8A;--mint:#5CE1B9;--soft:#E9E6FF;--muted:#A6A1D0;--paper:#F6EBD9;--ink:#1E1A3A}
*{box-sizing:border-box}body{margin:0;background:var(--night);color:var(--soft);font-family:"BIZ UDPGothic","Yu Gothic UI","Meiryo","Hiragino Sans","Noto Sans JP",sans-serif;line-height:1.8}
header{position:sticky;top:0;background:rgba(21,16,45,.94);padding:10px 18px;display:flex;gap:16px;align-items:center;flex-wrap:wrap;z-index:2}
header a{color:var(--lantern);font-weight:700;text-decoration:none}header .play{margin-left:auto;background:var(--lantern);color:#2B1700;padding:6px 16px;border-radius:12px}
main{max-width:920px;margin:0 auto;padding:10px 18px 60px}h1{color:var(--lantern);font-size:30px;margin:18px 0 8px}h2{color:var(--lantern);font-size:22px;margin:34px 0 8px;border-bottom:2px solid var(--card);padding-bottom:4px}
h3{font-size:18px;margin:20px 0 4px}a{color:var(--mint)}.box{background:var(--card);border-radius:16px;padding:14px 18px;margin:12px 0}
.game{display:flex;gap:14px;align-items:flex-start}.game img{width:64px;height:64px;flex:0 0 auto}
.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:12px}
.c{background:var(--paper);color:var(--ink);border-radius:14px;padding:10px;text-align:center;border:3px solid #8E86B8}.c.star{border-color:var(--berry)}
.c img{width:84px;height:84px;object-fit:contain}.c b{display:block}.c small{display:block;font-size:12px;line-height:1.5;color:#3B3560;text-align:left;margin-top:4px}
.c .n{color:var(--berry);font-size:12px}header .langs{font-size:13px}header .langs a{margin-left:6px;font-weight:400}ul{padding-left:1.2em}.muted{color:var(--muted);font-size:14px}footer{text-align:center;color:var(--muted);font-size:13px;padding:20px}
'''


STREAM_NAV = {'ja': '配信・動画OK', 'en': 'Streaming OK', 'ko': '방송 · 영상 OK', 'zh': '直播 · 视频 OK'}


def page(title, body, head_extra='', ad='', lang='ja', base='howto'):
    if lang == 'ja':
        brand, nav, contact, desc, hl = 'わちゃパ', ['遊び方', 'このサイトについて', 'プライバシー', '遊ぶ'], 'お問い合わせ', \
            'なんでもアリーナ・みんなのものさし・ワードウルフなど9つのパーティーゲーム。iPhone・Android・PCのブラウザで、部屋コードを使ってみんなで遊べます。', 'ja'
    else:
        T = pages_text.L[lang]
        brand, nav, contact, desc, hl = T['brand'], T['nav'], T['contact'], T['desc'], T['html_lang']
    alt = ''.join(f'<link rel="alternate" hreflang="{"zh-CN" if l == "zh" else l}" href="{fname(base, l)}">' for l in LANGS)
    langs = ' '.join(f'<a href="{fname(base, l)}"{" style=\"color:var(--soft)\"" if l == lang else ""}>{LANG_LABEL[l]}</a>' for l in LANGS)
    return f'''<!doctype html>
<html lang="{hl}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{html.escape(title)}｜{brand}</title><meta name="theme-color" content="#15102D"><link rel="icon" href="icons/icon-192.png">
<meta name="description" content="{html.escape(desc)}">{alt}
{head_extra}<style>{CSS}</style></head><body>
<header><a href="./">{brand}</a><a href="{fname('howto', lang)}">{nav[0]}</a><a href="{fname('about', lang)}">{nav[1]}</a><a href="{fname('privacy', lang)}">{nav[2]}</a><a href="{fname('streaming', lang)}">{STREAM_NAV[lang]}</a><span class="langs">🌐 {langs}</span><a class="play" href="./">{nav[3]}</a></header>
<main>{body}{('<div style="text-align:center;margin:30px 0">' + ad + '</div>') if ad else ''}</main><footer>© 2026 Zen ・ <a href="{CONTACT}">{contact}</a></footer></body></html>
'''


GAMES = [
    ('icon_arena', 'なんでもアリーナ（一押し）', '2〜8人',
     '手番が来たら山札から1枚引き、手札から1枚出して、書かれていることに従います。落とし穴を誰かの前に置いたり、全員でいっせいにじゃんけんしたり、「わたし」と言ったら負けになったり──何が起きるかわかりません。負けた人は抜けて、最後の1人になるか、カードの勝利条件（金貨を15ポイント分ためる・誕生月の人が出す、など）を満たした人の勝ち。全員まとめて負けることもあります。'
     '★のついたスターカードは特に強力。ゲームの最初に「今夜のルール」が1つ追加されます。言葉やしぐさのルールを破った人を見つけたら、その人の「違反！」を押しましょう。'),
    ('icon_scale', 'みんなのものさし（NEW）', '2〜8人',
     '全員にひみつの数字（1〜100）が配られます。お題（「強そうな動物」なら 1＝弱い・100＝強い）に合わせて、自分の数字を「ことば」で表します。たとえば90なら「ライオン」、5なら「ありんこ」。数字そのものは言ってはいけません。'
     'みんなのヒントを見て話し合いながら、自分がいちばん小さいと思ったら「出す」。まだ誰かの手元にもっと小さい数字があったらミスで、ライフが1つ減ります（その数字は捨てられます）。ライフを残して全員出し切れたらクリア！ 全員で協力するゲームです。お題は119種類。'),
    ('icon_wordwolf', 'ワードウルフ', '3人〜',
     '全員にお題が配られますが、1人（7人以上なら2人）だけ少し違うお題です。それが「ウルフ」。自分がどちらかは分かりません。お題について話し合い、ウルフだと思う人に投票します。ウルフが最多票なら、ウルフは市民のお題を当てれば逆転勝ち。お題は「カレー」と「シチュー」のように似たもの同士で、574組あります。'),
    ('icon_ngword', 'NGワード', '3人〜',
     '全員に別々のNGワードが付きます。自分のワードだけ見えません。おしゃべりしながら、ほかの人にその人のNGワードを言わせましょう。言った人を見つけたら「言った！」を押します。最後まで残った人の勝ち。「ハード」にすると「なるほど」「たしかに」のような口ぐせがNGワードになります。'),
    ('icon_spy', 'スパイはだれだ', '3人〜',
     '全員に同じ「場所」が配られますが、スパイだけは場所を知りません。順番に質問し合ってスパイを探します。スパイが最多票なら、スパイは12個の候補から場所を当てれば逆転勝ち。'),
    ('icon_sync', 'みんなでシンクロ', '2人〜',
     '「赤い食べ物といえば？」などのお題に、全員が自分の画面で答えます。同じ答えの人が多いほど得点。ひらがな・カタカナの違いや全角・半角の違いは同じ答えとして数えます。'),
    ('icon_guess', '質問で当てろ', '2人〜',
     '出題者だけが答えを知っています。ほかの人は「はい・いいえ」で答えられる質問をして、時間内に答えを当てます。当てた人と出題者に1点。'),
    ('icon_question', 'ぶっちゃけ質問', '3人〜',
     '「この中で一番〇〇なのは？」に全員が匿名で投票して、結果を発表します。だれが誰に入れたかは表示されません。'),
    ('icon_draw', 'お絵かき伝言', '3人〜',
     '最初に全員がお題（「ねこが空を飛んでいる」など）を書きます。それが隣の人に回り、絵にします。次の人はその絵だけを見て何の絵か文で答え、さらに次の人がその文を絵に…と全員分くり返します。最後に、お題がどう変わっていったかを1コマずつ発表して笑いましょう。'),
]


def howto(cards, chaos):
    b = '<h1>遊び方・カード一覧</h1><p>このサイトでは、みんなで集まって（または通話しながら）遊ぶ9つのパーティーゲームを無料で遊べます。アプリのインストールは不要で、iPhone・Android・PC のブラウザからそのまま遊べます。</p>'
    b += '<h2>はじめ方</h2><div class="box"><ol><li>1人が名前を入れて「部屋を作る」を押すと、6けたの部屋コードが出ます。</li><li>ほかの人は「部屋に参加する」で同じ部屋コードを入れます（Android アプリの人は「オンライン」に切り替えてから）。</li><li>部屋を作った人（ホスト）がゲームを選んで「はじめる」を押します。</li></ol>'
    b += '<p class="muted">iPhone は Safari の共有ボタン →「ホーム画面に追加」で、アプリのように全画面で遊べます。話して遊ぶゲームは、離れているときは通話アプリ（LINE・Discord など）をつなぎながら遊びましょう。</p></div>'
    b += '<h2>9つのゲーム</h2>'
    for icon, name, n, text in GAMES:
        b += f'<div class="box game"><img src="img/{icon}.webp" alt=""><div><h3>{html.escape(name)}<span class="muted">（{n}）</span></h3><p>{html.escape(text)}</p></div></div>'
    b += '<h2>なんでもアリーナのカード</h2><p>全部で50枚（うち★のスターカード14枚）。同じ名前でも、出してから効果がわかるカードがあります（「全員じゃんけん」は負ける手が4種類、「せーのでゆび」は4種類）。</p><div class="cards">'
    seen = set()
    for c in cards:
        key = (c['name'], c['text'])
        if key in seen: continue
        seen.add(key)
        b += f'<div class="c{" star" if c["star"] else ""}"><img src="img/card_{c["art"]}.webp" alt=""><b>{"★ " if c["star"] else ""}{html.escape(c["name"])}</b><small>{html.escape(c["text"])}</small></div>'
    b += '</div><h2>今夜のルール</h2><p>アリーナの始まりに、次のうち1つがランダムで追加されます（ロビーで「なし」にもできます）。</p><ul>'
    for c in chaos.values():
        b += f'<li><b>{html.escape(c["title"])}</b>：{html.escape(c["text"])}</li>'
    b += '</ul>'
    return b


PLAYERS = {'icon_arena': (2, 8), 'icon_scale': (2, 8), 'icon_wordwolf': (3,), 'icon_ngword': (3,), 'icon_spy': (3,), 'icon_sync': (2,), 'icon_guess': (2,),
           'icon_question': (3,), 'icon_draw': (3, 8)}


def howto_l(lang, cards, chaos, tr):
    T = pages_text.L[lang]
    b = T['howto_intro'] + T['start'] + f'<h2>{T["games_h"]}</h2>'
    for icon, _, _, _ in GAMES:
        name, text = T['games'][icon]
        pr = PLAYERS[icon]
        n = T['players_range'].format(*pr) if len(pr) == 2 else T['players'].format(pr[0])
        b += f'<div class="box game"><img src="img/{icon}.webp" alt=""><div><h3>{html.escape(name)}<span class="muted">（{n}）</span></h3><p>{html.escape(text)}</p></div></div>'
    b += f'<h2>{T["cards_h"]}</h2><p>{T["cards_p"]}</p><div class="cards">'
    seen = set()
    for c in cards:
        key = (c['name'], c['text'])
        if key in seen: continue
        seen.add(key)
        b += f'<div class="c{" star" if c["star"] else ""}"><img src="img/card_{c["art"]}.webp" alt=""><b>{"★ " if c["star"] else ""}{html.escape(tr(c["name"]))}</b><small>{html.escape(tr(c["text"]))}</small></div>'
    b += f'</div><h2>{T["chaos_h"]}</h2><p>{T["chaos_p"]}</p><ul>'
    for c in chaos.values():
        b += f'<li><b>{html.escape(tr(c["title"]))}</b>{T["colon"]}{html.escape(tr(c["text"]))}</li>'
    return b + '</ul>'


def about():
    return f'''<h1>このサイトについて</h1>
<p>「わちゃパ」は、友だちや家族と集まったとき、または通話しながら遊べるパーティーゲーム集です。なんでもアリーナ・みんなのものさし・ワードウルフ・NGワード・スパイはだれだ・みんなでシンクロ・質問で当てろ・ぶっちゃけ質問・お絵かき伝言の9つを、無料で遊べます。</p>
<h2>遊べる端末</h2><ul><li>iPhone・iPad（Safari。ホーム画面に追加するとアプリのように使えます）</li><li>Android（ブラウザ、または Android アプリ）</li><li>パソコン（Edge・Chrome などのブラウザ、または PC 版の「遊ぶ.bat」）</li></ul>
<p>どの端末どうしでも、部屋コードを使って同じ部屋で遊べます。</p>
<h2>しくみ</h2><p>このサイトは運営者のサーバーを使っていません。ゲームの進行は部屋を作った人の端末の中で行われ、参加者の端末どうしが直接通信します（相手を見つけるときだけ PeerJS の仲介サーバーを使います）。そのため、入力した名前や答えが運営者に届くことはありません。</p>
<h2>無料で遊べる理由</h2><p>ロビーや結果の画面、ゲームの合間に広告を表示しています。広告の収益で運営しています。</p>
<h2>運営者</h2><p>Zen（個人で制作しています）</p>
<h2>お問い合わせ</h2><p>ご意見・不具合の報告は <a href="{CONTACT}">wachapa.com@gmail.com</a> へお寄せください。</p>
'''


def privacy():
    return f'''<h1>プライバシーポリシー</h1>
<p>「わちゃパ」（このサイトのウェブ版、PC 版、Android アプリを含みます。以下「本サービス」）における利用者の情報の取り扱いについて説明します。</p>
<h2>1. 運営者が集める情報</h2><p>運営者は、利用者の個人情報を収集・保存しません（4. の荒らし対策を除く）。本サービスは運営者のサーバーを使用しません。</p>
<h2>2. 端末どうしの通信</h2>
<p>入力した名前やゲームの内容は、同じ部屋に参加している端末の間でのみやり取りされ、運営者に送られることはありません。</p>
<ul><li><b>オンライン</b>：端末どうしを WebRTC（PeerJS）で直接つなぎます。相手を見つけるために PeerJS の仲介サーバー（0.peerjs.com）と通信し、直接つながらない回線では PeerJS の中継サーバーを経由します。このとき、通信に必要な IP アドレスなどが仲介・中継サーバーと相手の端末に伝わります。</li>
<li><b>近くで（Android アプリ）</b>：Google の Nearby Connections（Bluetooth・Wi-Fi）で近くの端末と直接つながります。近くの端末を見つけるために「付近のデバイス」「位置情報」の許可を求めますが、位置情報そのものを取得・記録・送信することはありません。</li></ul>
<h2>3. 端末に保存する情報</h2><p>次回の入力を省くため、入力した名前、最後に入れた部屋コード、表示する言語を端末内（アプリの保存領域、またはブラウザの localStorage）に保存します。ウェブ版はすぐ起動できるよう、ゲームのファイルを端末に保存します。いずれもアプリの削除やブラウザのデータ削除で消えます。</p>
<h2>4. 荒らし対策</h2><p>迷惑行為を防ぐため、本サービスは端末ごとにランダムな番号（端末番号。広告IDやお使いの端末の識別番号とは無関係です）を作って端末内に保存し、オンラインの部屋に参加するときに部屋を作った人の端末へ送ります。</p>
<ul><li>運営者が迷惑行為を確認した場合、その端末番号を元に戻せない形（SHA-256 のハッシュ値）にしたものを、参加禁止リストとして本サイト（bans.json）に掲載することがあります。リストにある端末は、オンラインの部屋に参加したり、部屋を作ったりできなくなります。名前やゲームの内容はリストに載せません。</li>
<li>運営者は、迷惑行為への対応のため、管理者としてオンラインの部屋に入り、部屋の様子（参加者の名前・端末番号のハッシュ値・ゲームの画面）を確認したり、参加者を退出させたり、部屋を閉じたりすることがあります。管理者が入ると、部屋の全員に表示されます。</li>
<li>端末番号は、アプリの削除やブラウザのデータ削除で消えます。参加禁止の解除を希望する場合は、お問い合わせ先へご連絡ください。</li></ul>
<h2>5. 広告</h2>
{pages_text.AD_JA}
<h2>6. 対象年齢</h2><p>本サービスは13歳以上の方を対象としています。</p>
<h2>7. 改定</h2><p>本ポリシーは必要に応じて改定します。改定後の内容は本ページに掲載した時点で効力を持ちます。</p>
<h2>8. お問い合わせ</h2><p><a href="{CONTACT}">wachapa.com@gmail.com</a> へお寄せください。</p>
<p class="muted">制定日：2026年11月3日／改定日：2026年11月4日</p>
'''


def build(game_dir, kjs_path, head_extra='', ad=''):
    out = subprocess.run(['node', '-e', f'const g=require({json.dumps(kjs_path)}).com.zen.partygame.game;console.log(JSON.stringify({{cards:JSON.parse(g.arenaCardsJson()),meta:JSON.parse(g.metaJson())}}))'],
                         capture_output=True, text=True, check=True).stdout
    data = json.loads(out)
    for base, title, body in (('howto', '遊び方・カード一覧', howto(data['cards'], data['meta']['chaos'])),
                              ('about', 'このサイトについて', about()),
                              ('privacy', 'プライバシーポリシー', privacy())):
        open(f'{game_dir}/{fname(base, "ja")}', 'w', encoding='utf-8').write(page(title, body, head_extra, ad, 'ja', base))
    # 配信・動画OKのページ（4言語）
    import streaming_text
    for lang in LANGS:
        title, body = streaming_text.L[lang]
        open(f'{game_dir}/{fname("streaming", lang)}', 'w', encoding='utf-8').write(page(title, body.replace('{contact}', CONTACT), head_extra, ad, lang, 'streaming'))
    i18n_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'app', 'src', 'main', 'assets', 'i18n')
    for lang in LANGS[1:]:
        T = pages_text.L[lang]
        d = json.load(open(os.path.join(i18n_dir, lang + '.json'), encoding='utf-8'))
        tr = lambda k: d.get(k, k)
        bodies = [howto_l(lang, data['cards'], data['meta']['chaos'], tr), T['about'].replace('{contact}', CONTACT), T['privacy'].replace('{contact}', CONTACT)]
        for base, title, body in zip(('howto', 'about', 'privacy'), T['titles'], bodies):
            open(f'{game_dir}/{fname(base, lang)}', 'w', encoding='utf-8').write(page(title, body, head_extra, ad, lang, base))
