"""検索で見つけてもらうためのページ：ゲームごとのページ・お題一覧・季節の特集。
pages.build() から呼ばれる。作ったページの名前の一覧を返す（サイトマップに載せる）。"""
import html, json, os
import pages, seo_text

LANGS = pages.LANGS
fname = pages.fname
SITE = 'https://wachapa.com/'
E = html.escape

EXTRA_CSS = '''<style>
.btn-play{display:inline-block;background:var(--lantern);color:#2B1700;font-weight:800;padding:12px 22px;border-radius:14px;text-decoration:none;margin:10px 0;font-size:18px}
.lead{font-size:17px}.meta{color:var(--muted);font-size:14px}.hero{display:flex;gap:16px;align-items:center}.hero img{width:96px;height:96px}
.chips{display:flex;flex-wrap:wrap;gap:8px}.chips span{background:var(--card);border-radius:10px;padding:4px 10px;font-size:15px}
table.odai{width:100%;border-collapse:collapse}table.odai td,table.odai th{border-bottom:1px solid #2C2858;padding:6px 8px;text-align:left}table.odai th{color:var(--lantern)}
.glist{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px}.glist a{display:flex;flex-direction:column;align-items:center;background:var(--card);border-radius:14px;padding:10px;text-decoration:none;color:var(--soft);font-weight:700;text-align:center}
.glist img{width:64px;height:64px}.faq dt{font-weight:800;margin-top:10px}.faq dd{margin:4px 0 0 0;color:var(--soft)}
</style>'''


def ld(obj):
    return '<script type="application/ld+json">' + json.dumps(obj, ensure_ascii=False).replace('</', '<\\/') + '</script>'


def crumbs(items):
    return {'@context': 'https://schema.org', '@type': 'BreadcrumbList', 'itemListElement': [
        {'@type': 'ListItem', 'position': i + 1, 'name': n, 'item': SITE + u} for i, (n, u) in enumerate(items)]}


def load_topics(lang):
    root = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'app', 'src', 'main', 'assets')
    return json.load(open(os.path.join(root, 'topics.json' if lang == 'ja' else f'topics_{lang}.json'), encoding='utf-8'))


# ================= お題一覧 =================

# slug, お題のデータの名前, 遊ぶゲーム（?game=）, ゲームのページ
ODAI = [
    ('odai-wordwolf', 'wordwolf', 'WORD_WOLF', 'wordwolf'),
    ('odai-ngword', 'ng', 'NG_WORD', 'ngword'),
    ('odai-scale', 'scales', 'SCALE', 'scale'),
    ('odai-spy', 'spy', 'SPY', 'spy'),
    ('odai-sync', 'sync', 'SYNC', 'sync'),
    ('odai-guess', 'guess', 'GUESS', 'guess'),
    ('odai-question', 'questions', 'QUESTION', 'question'),
    ('odai-draw', 'drawWho', 'DRAW', 'draw'),
]

ODAI_JA = {
    'odai-wordwolf': ('ワードウルフのお題一覧【{n}組】ジャンル別｜そのまま遊べる', 'ワードウルフのお題（市民とウルフの組み合わせ）{n}組をジャンル別に全部のせました。「カレーとシチュー」のような、似ているけど違うお題ばかり。下のボタンから、このお題でそのまま遊べます。'),
    'odai-ngword': ('NGワードゲームのお題一覧【{n}個】ふつう・口ぐせ', 'NGワードゲームで使うワード{n}個の一覧です。ふつうのワードに加えて、「なるほど」「たしかに」のような口ぐせのハードなワードも。'),
    'odai-scale': ('数字を「ことば」で伝えるゲームのお題一覧【{n}種】｜みんなのものさし', '1〜100の数字を、お題に合わせた「ことば」で表して小さい順に並べる協力ゲーム「みんなのものさし」のお題{n}種の一覧です。'),
    'odai-spy': ('スパイゲームの場所一覧【{n}か所】｜スパイはだれだ', '「スパイはだれだ」で使う場所{n}か所の一覧です。みんなは場所を知っていて、スパイだけが知りません。'),
    'odai-sync': ('連想ゲーム・以心伝心のお題一覧【{n}問】｜みんなでシンクロ', '「赤い食べ物といえば？」のような、みんなと答えがそろうと得点の連想ゲームのお題{n}問です。'),
    'odai-guess': ('はい・いいえで当てるゲームのお題一覧【{n}個】｜質問で当てろ', '「はい・いいえ」で答えられる質問だけで答えを当てるゲームのお題{n}個です。20の扉のような遊びにも使えます。'),
    'odai-question': ('「この中で一番〇〇なのは？」質問{n}選｜ぶっちゃけ質問', '飲み会や修学旅行で盛り上がる「この中で一番〇〇なのは？」の質問{n}個。匿名で投票できるので本音が出ます。'),
    'odai-draw': ('お絵かき伝言ゲームのお題一覧｜だれが×なにをしている', 'お絵かき伝言ゲームで使うお題の材料です。「だれが」{n}個と「なにをしている」{m}個を組み合わせて、お題が作られます。'),
}

ODAI_INDEX = {
    'ja': ('パーティーゲームのお題一覧｜ワードウルフ・NGワードほか', 'ワードウルフ・NGワード・スパイゲーム・連想ゲームなど、パーティーゲームのお題をまとめました。どれもスマホ・PCのブラウザで、そのまま遊べます。'),
    'en': ('Party game prompt lists | Word Wolf, Forbidden Words and more', 'All the prompts for Word Wolf, Forbidden Words, Who\'s the Spy? and more. Play any of them right away in your browser.'),
    'ko': ('파티 게임 주제 목록 | 워드울프 · 금지어 게임 외', '워드울프 · 금지어 게임 · 스파이 게임 · 연상 게임 등 파티 게임 주제를 모았어요. 모두 브라우저에서 바로 플레이할 수 있어요.'),
    'zh': ('派对游戏题目列表｜谁是卧底 · 禁语游戏等', '汇总了谁是卧底、禁语游戏、间谍游戏、联想游戏等派对游戏的题目。都可以直接在浏览器里玩。'),
}


def odai_items(slug, data, lang, tr):
    """(件数, 本文の HTML)"""
    if slug == 'odai-wordwolf':
        groups = {}
        for p in data['wordwolf']: groups.setdefault(p['c'], []).append(p)
        b = ''
        for c, ps in groups.items():
            b += f'<h2>{E(c)}（{len(ps)}）</h2><table class="odai"><tbody>' + ''.join(f'<tr><td>{E(p["a"])}</td><td>⇔</td><td>{E(p["b"])}</td></tr>' for p in ps) + '</tbody></table>'
        return len(data['wordwolf']), b
    if slug == 'odai-ngword':
        groups = {}
        for p in data['ng']: groups.setdefault(p['c'], []).append(p['w'])
        label = {'ふつう': tr('ふつう'), 'ハード': tr('ハード（口ぐせ）')}
        b = ''.join(f'<h2>{E(label.get(c, c))}（{len(ws)}）</h2><div class="chips">' + ''.join(f'<span>{E(w)}</span>' for w in ws) + '</div>' for c, ws in groups.items())
        return len(data['ng']), b
    if slug == 'odai-scale':
        rows = ''.join(f'<tr><td><b>{E(t["t"])}</b></td><td>1 = {E(t["lo"])}</td><td>100 = {E(t["hi"])}</td></tr>' for t in data['scales'])
        return len(data['scales']), f'<table class="odai"><tbody>{rows}</tbody></table>'
    if slug == 'odai-draw':
        who, what = data['drawWho'], data['drawWhat']
        lw, lt = DRAW_LABELS[lang]
        b = f'<h2>{E(lw)}（{len(who)}）</h2><div class="chips">' + ''.join(f'<span>{E(w)}</span>' for w in who) + '</div>'
        b += f'<h2>{E(lt)}（{len(what)}）</h2><div class="chips">' + ''.join(f'<span>{E(w)}</span>' for w in what) + '</div>'
        return len(who), b
    key = {'odai-spy': 'spy', 'odai-sync': 'sync', 'odai-guess': 'guess', 'odai-question': 'questions'}[slug]
    items = data[key]
    if slug in ('odai-sync', 'odai-question'):
        return len(items), '<ol>' + ''.join(f'<li>{E(x)}</li>' for x in items) + '</ol>'
    return len(items), '<div class="chips">' + ''.join(f'<span>{E(x)}</span>' for x in items) + '</div>'


DRAW_LABELS = {'ja': ('だれが', 'なにをしている'), 'en': ('Who', 'Doing what'), 'ko': ('누가', '무엇을 하고 있나'), 'zh': ('谁', '在做什么')}


# ================= ゲームのページ =================

def game_label(gid, games, tr):
    g = next(x for x in games if x['id'] == gid)
    return tr(g['label'])


def play_url(gid):
    return f'./?game={gid}'


def game_list(lang, games, tr, skip=None):
    b = '<div class="glist">'
    for slug, gid, icon, _ in seo_text.GAMES:
        if slug == skip: continue
        b += f'<a href="{fname(slug, lang)}"><img src="img/{icon}.webp" alt="">{E(game_label(gid, games, tr))}</a>'
    return b + '</div>'


def build(game_dir, data, dicts, head_extra='', ad=''):
    """data = カード・ゲームの一覧（pages.build と同じ）。dicts = 言語 → 訳の辞書"""
    games = data['meta']['games']
    out = []

    def write(base, lang, title, body, desc, langs=None, more=''):
        open(os.path.join(game_dir, fname(base, lang)), 'w', encoding='utf-8').write(
            pages.page(title, body, head_extra, ad, lang, base, desc_override=desc, langs=langs, head_more=EXTRA_CSS + more, brand_title=False))
        out.append(fname(base, lang))

    for lang in LANGS:
        tr = (lambda k: k) if lang == 'ja' else (lambda k, d=dicts[lang]: d.get(k, k))
        brand = 'わちゃパ' if lang == 'ja' else pages.pages_text.L[lang]['brand']
        L = seo_text.L.get(lang)
        home_name = 'ホーム' if lang == 'ja' else L['home']
        odai_name = pages.ODAI_NAV[lang]
        topics = load_topics(lang)

        # ---------- お題一覧 ----------
        index_cards = ''
        for slug, _, gid, gslug in ODAI:
            name = game_label(gid, games, tr)
            n, body_list = odai_items(slug, topics, lang, tr)
            if lang == 'ja':
                m = len(topics['drawWhat'])
                title, lead = (x.format(n=n, m=m) for x in ODAI_JA[slug])
            else:
                title = L['odai_title'].format(name, L['count'].format(n))
                lead = L['odai_lead'].format(name)
            play = ('▶ このお題で今すぐ遊ぶ（登録不要）' if lang == 'ja' else L['play'].format(name))
            body = f'<h1>{E(title)}</h1><p class="lead">{E(lead)}</p><a class="btn-play" href="{play_url(gid)}">{E(play)}</a>'
            body += f'<p class="meta"><a href="{fname(gslug, lang)}">{E(("「" + name + "」の遊び方") if lang == "ja" else (L["rules"] + ": " + name))}</a> ・ <a href="{fname("odai", lang)}">{E(odai_name)}</a></p>'
            body += body_list + f'<a class="btn-play" href="{play_url(gid)}">{E(play)}</a>'
            more = ld(crumbs([(home_name, ''), (odai_name, fname('odai', lang)), (title, fname(slug, lang))]))
            write(slug, lang, title, body, lead, more=more)
            icon = next(x[2] for x in seo_text.GAMES if x[1] == gid)
            index_cards += f'<a href="{fname(slug, lang)}"><img src="img/{icon}.webp" alt="">{E(name)}<span class="meta">{E(("{0}件".format(n)) if lang == "ja" else L["count"].format(n))}</span></a>'
        ititle, ilead = ODAI_INDEX[lang]
        write('odai', lang, ititle, f'<h1>{E(ititle)}</h1><p class="lead">{E(ilead)}</p><div class="glist">{index_cards}</div>', ilead,
              more=ld(crumbs([(home_name, ''), (odai_name, fname('odai', lang))])))

        # ---------- ゲームのページ ----------
        for slug, gid, icon, odai in seo_text.GAMES:
            name = game_label(gid, games, tr)
            lo, hi = seo_text.PLAYERS[slug]
            if lang == 'ja':
                J = seo_text.JA[slug]
                title, desc = J['title'], J['desc']
                body = (f'<div class="hero"><img src="img/{icon}.webp" alt=""><div><h1>{E(J["name"])}</h1><p class="meta">{lo}〜{hi}人 ・ {E(J["time"])} ・ 無料 ・ 登録不要 ・ スマホ／PC</p></div></div>'
                        f'<p class="lead">{E(J["lead"])}</p><a class="btn-play" href="{play_url(gid)}">▶ {E(J["name"])}で今すぐ遊ぶ（無料）</a>'
                        f'<h2>遊び方</h2><ol>' + ''.join(f'<li>{E(x)}</li>' for x in J['steps']) + '</ol>'
                        f'<h2>勝つためのコツ</h2><ul>' + ''.join(f'<li>{E(x)}</li>' for x in J['tips']) + '</ul>'
                        f'<h2>こんなときにおすすめ</h2><p>{E(J["scenes"])}</p>')
                if odai: body += f'<h2>お題の一覧</h2><p><a href="{fname(odai, lang)}">{E(J["name"])}のお題をすべて見る</a></p>'
                body += '<h2>よくある質問</h2><dl class="faq">' + ''.join(f'<dt>Q. {E(q)}</dt><dd>A. {E(a)}</dd>' for q, a in J['faq']) + '</dl>'
                body += (f'<h2>はじめ方</h2><ol><li>「{E(J["name"])}で今すぐ遊ぶ」を押して、名前を入れて「部屋を作る」。</li><li>表示された6けたの部屋コードか「招待リンク」を友だちに送ります。</li>'
                         f'<li>全員そろったら「はじめる」。離れているときは通話アプリ（LINE・Discord など）をつなぎながら遊びましょう。</li></ol>'
                         f'<a class="btn-play" href="{play_url(gid)}">▶ {E(J["name"])}で今すぐ遊ぶ</a>')
                others = 'ほかのゲーム'
            else:
                T = pages.pages_text.L[lang]
                text = T['games'][icon][1]
                title = L['title'].format(name)
                desc = L['desc'].format(name, f'{lo}–{hi}')
                body = (f'<div class="hero"><img src="img/{icon}.webp" alt=""><div><h1>{E(name)}</h1><p class="meta">{E(L["players"])}: {lo}–{hi}</p></div></div>'
                        f'<p class="lead">{E(L["lead"].format(name))}</p><a class="btn-play" href="{play_url(gid)}">{E(L["play"].format(name))}</a>'
                        f'<h2>{E(L["rules"])}</h2><p>{E(text)}</p>')
                if odai: body += f'<p><a href="{fname(odai, lang)}">{E(L["odai"])}</a></p>'
                body += f'<a class="btn-play" href="{play_url(gid)}">{E(L["play"].format(name))}</a>'
                others = L['others']
            body += f'<h2>{E(others)}</h2>' + game_list(lang, games, tr, skip=slug)
            app = {'@context': 'https://schema.org', '@type': 'VideoGame', 'name': name, 'url': SITE + fname(slug, lang), 'description': desc,
                   'inLanguage': lang, 'genre': ['Party game', 'Card game' if slug in ('arena', 'yao', 'blackjack', 'highlow', 'poker', 'indian') else 'Word game'],
                   'gamePlatform': ['Web browser', 'Android'], 'applicationCategory': 'GameApplication', 'operatingSystem': 'Any',
                   'numberOfPlayers': {'@type': 'QuantitativeValue', 'minValue': lo, 'maxValue': hi},
                   'offers': {'@type': 'Offer', 'price': '0', 'priceCurrency': 'JPY'}, 'image': SITE + f'img/{icon}.webp',
                   'publisher': {'@type': 'Person', 'name': 'Zen'}}
            write(slug, lang, title, body, desc, more=ld(app) + ld(crumbs([(home_name, ''), (name, fname(slug, lang))])))

    # ---------- 季節の特集（日本語だけ） ----------
    out.extend(seasonal(game_dir, games, head_extra, ad, write))
    return out


def seasonal(game_dir, games, head_extra, ad, write):
    title = '忘年会・新年会で盛り上がるゲーム14選｜スマホだけで今すぐ・オンライン飲み会もOK【2026】'
    desc = '忘年会・新年会・オンライン飲み会で盛り上がるパーティーゲームを、場面別に紹介。道具も準備もいらず、全員のスマホで今すぐ遊べます。幹事さん向けの進め方つき。'
    def g(slug):
        gid = next(x[1] for x in seo_text.GAMES if x[0] == slug); J = seo_text.JA[slug]
        icon = next(x[2] for x in seo_text.GAMES if x[0] == slug)
        return (f'<div class="box game"><img src="img/{icon}.webp" alt=""><div><h3><a href="{fname(slug, "ja")}">{E(J["name"])}</a>'
                f'<span class="muted">（{seo_text.PLAYERS[slug][0]}〜{seo_text.PLAYERS[slug][1]}人・{E(J["time"])}）</span></h3><p>{E(J["lead"])}</p>'
                f'<a class="btn-play" href="./?game={gid}" style="font-size:15px;padding:8px 16px">▶ 遊ぶ</a></div></div>')
    body = f'''<h1>{E(title)}</h1>
<p class="lead">「幹事になったけど、何をすればいいかわからない」「話題が途切れて気まずい」──そんなときに、全員のスマホだけで始められるゲームを集めました。アプリのインストールも会員登録もいりません。部屋を作って、部屋コードかリンクを送るだけです。</p>
<a class="btn-play" href="./">▶ わちゃパを開く（無料）</a>
<h2>まずはこれ：とにかく笑いたいとき</h2>{g('arena')}{g('draw')}{g('ngword')}
<h2>話して盛り上がる：お酒の席の定番</h2>{g('wordwolf')}{g('question')}{g('spy')}
<h2>初対面・会社の集まりでも安心：協力して遊ぶ</h2>{g('scale')}{g('sync')}
<h2>年末らしく：トランプで一勝負</h2><p>チップは遊びの中だけの点数で、お金は一切かかりません。</p>{g('indian')}{g('poker')}{g('blackjack')}{g('highlow')}
<h2>じっくり対戦したいとき</h2>{g('yao')}{g('guess')}
<h2>幹事さん向け：30秒で始める進め方</h2>
<ol><li>幹事がスマホで <a href="./">わちゃパ</a> を開き、名前を入れて「部屋を作る」。</li>
<li>画面の「招待リンクをコピー」を押して、LINE のグループに送ります。みんなはリンクを開くだけで同じ部屋に入れます。</li>
<li>ゲームを選んで「はじめる」。ゲームが終わったら「もう一回」か、ロビーで別のゲームへ。</li></ol>
<h2>オンライン飲み会のときは</h2><p>Zoom・LINE・Discord などの通話をつなぎながら、ゲームの画面は各自のスマホやPCで開きます。お絵かき伝言の発表は、画面共有すると一段と盛り上がります。</p>
<h2>大人数のときは</h2><p>1つの部屋は8人までです。9人以上なら、2つの部屋に分かれて同じゲームを遊び、最後に結果を見せ合うのがおすすめです。</p>
<p class="meta"><a href="odai.html">お題一覧</a> ・ <a href="howto.html">遊び方・カード一覧</a></p>'''
    more = ld({'@context': 'https://schema.org', '@type': 'Article', 'headline': title, 'description': desc, 'inLanguage': 'ja',
               'author': {'@type': 'Person', 'name': 'Zen'}, 'datePublished': '2026-11-05', 'dateModified': '2026-11-05', 'image': SITE + 'og.jpg'})
    write('bonenkai', 'ja', title, body, desc, langs=['ja'], more=more)
    return []
