"""PC 版を組み立てる
  out/PartyGamePC/            … 配る フォルダ（遊ぶ.bat と game/）
  out/PartyGamePC.zip
前もって `gradle :shared:compileProductionExecutableKotlinJs` でゲームの進行（Kotlin → JavaScript）を作っておくこと。
"""
import hashlib, json, os, re, shutil, sys, zipfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
OUT = os.path.join(HERE, 'out'); D = os.path.join(OUT, 'PartyGamePC'); G = os.path.join(D, 'game')
VERSION = re.search(r'versionName = "([^"]+)"', open(os.path.join(ROOT, 'app', 'build.gradle.kts'), encoding='utf-8').read()).group(1)
KJS = os.path.join(ROOT, 'shared', 'build', 'compileSync', 'js', 'main', 'productionExecutable', 'kotlin', 'partygame.js')
ASSETS = os.path.join(ROOT, 'app', 'src', 'main', 'assets')
DRAW = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi')

shutil.rmtree(D, ignore_errors=True); os.makedirs(os.path.join(G, 'lib')); os.makedirs(os.path.join(G, 'img'))
for f in ('index.html', 'app.js', 'style.css', 'config.js'):
    shutil.copy(os.path.join(HERE, 'src', f), G)
shutil.copy(KJS, os.path.join(G, 'lib', 'partygame.js'))
shutil.copytree(os.path.join(HERE, 'src', 'sfx'), os.path.join(G, 'sfx'))   # 効果音（tools/make_sfx.py で作る）
shutil.copytree(os.path.join(HERE, 'src', 'bgm'), os.path.join(G, 'bgm'))   # BGM（tools/make_bgm.py で作る）

# ---- 収益化の設定（プロジェクトの一番上の monetization.properties。公開リポジトリには入れない） ----
M = {}
mp = os.path.join(ROOT, 'monetization.properties')
if os.path.exists(mp):
    for line in open(mp, encoding='utf-8'):
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, v = line.split('=', 1); M[k.strip()] = v.strip()
ADSENSE = M.get('adsense.client', '')               # ca-pub-...
DOMAIN = M.get('site.domain', '')                    # 独自ドメイン（例: partygame.jp）
SITE = M.get('site.url') or (f'https://{DOMAIN}/' if DOMAIN else 'https://0818134y-rgb.github.io/-/')
# AdSense 以外の広告サービス（忍者AdMax など）の広告タグ：pc/adtags/ に管理画面でコピーした HTML をそのまま置く
TAGDIR = os.path.join(HERE, 'adtags')
def tag(name):
    p = os.path.join(TAGDIR, name + '.html')
    return open(p, encoding='utf-8').read().strip() if os.path.exists(p) else ''
AD_TAGS = {'bannerSp': tag('banner_sp'), 'bannerPc': tag('banner_pc'), 'break': tag('break'), 'breakPc': tag('break_pc'), 'page': tag('page')}
cfg = {'adTags': {k: v for k, v in AD_TAGS.items() if k != 'page'}, 'adsenseClient': ADSENSE, 'bannerSlot': M.get('adsense.banner', ''), 'breakSlot': M.get('adsense.break', ''),
       'h5games': M.get('adsense.h5games', 'false') == 'true', 'adTest': M.get('adsense.test', 'false') == 'true'}
open(os.path.join(G, 'config.js'), 'w', encoding='utf-8').write('/* 自動で作られる設定（pc/build.py）。手で直すときは monetization.properties を直して作り直す */\nwindow.PG_CONFIG = ' + json.dumps(cfg, ensure_ascii=False, indent=1) + ';\n')
# AdSense の審査・自動広告のため、各ページの <head> に AdSense のコードを最初から入れておく
HEAD_ADS = ''
if ADSENSE:
    HEAD_ADS = ('<meta name="google-adsense-account" content="%s">\n<script async src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=%s" crossorigin="anonymous"%s></script>\n'
                % (ADSENSE, ADSENSE, ' data-adbreak-test="on"' if cfg['adTest'] else ''))
    pub = ADSENSE.replace('ca-', '')
    open(os.path.join(G, 'ads.txt'), 'w').write(f'google.com, {pub}, DIRECT, f08c47fec0942fa0\n')
# Android アプリの広告（AdMob）用：app-ads.txt（サイトの一番上の階層に置く必要がある）
APP_PUB = M.get('admob.appId', '').split('~')[0].replace('ca-app-', '')
if APP_PUB:
    open(os.path.join(G, 'app-ads.txt'), 'w').write(f'google.com, {APP_PUB}, DIRECT, f08c47fec0942fa0\n')
if DOMAIN:
    open(os.path.join(G, 'CNAME'), 'w').write(DOMAIN + '\n')
# 検索エンジンと AdSense の審査クローラーが全部のページを読めるように
open(os.path.join(G, 'robots.txt'), 'w').write(f'User-agent: *\nAllow: /\nSitemap: {SITE}sitemap.xml\n')
open(os.path.join(G, 'sitemap.xml'), 'w').write('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
    + ''.join(f'  <url><loc>{SITE}{p}</loc></url>\n' for p in [''] + [b + ('.html' if l == 'ja' else '.' + l + '.html') for b in ('howto', 'about', 'privacy') for l in ('ja', 'en', 'ko', 'zh')]) + '</urlset>\n')
sys.path.insert(0, HERE); import pages
# 読みもののページの広告：スマホならスマホ向けの枠、PC なら PC 向けの枠を、開いた端末で選んで差し込む
_sp = AD_TAGS['page'] or AD_TAGS['bannerSp'] or AD_TAGS['bannerPc']; _pc = AD_TAGS['bannerPc'] or _sp
PAGE_AD = ('<div id="pgad"></div><script>(function(){var m=/Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)||(navigator.maxTouchPoints>1&&/Macintosh/.test(navigator.userAgent));'
           'var el=document.getElementById("pgad");el.innerHTML=m?%s:%s;el.querySelectorAll("script").forEach(function(o){var s=document.createElement("script");'
           'for(var i=0;i<o.attributes.length;i++)s.setAttribute(o.attributes[i].name,o.attributes[i].value);s.text=o.textContent;o.replaceWith(s);});'
           'if(!m&&innerWidth<760){el.style.transform="scale("+((innerWidth-24)/728)+")";el.style.transformOrigin="center top";}})();</script>'
           % (json.dumps(_sp).replace('</', '<\\/'), json.dumps(_pc).replace('</', '<\\/'))) if (_sp or _pc) else ''
pages.build(G, KJS, HEAD_ADS, PAGE_AD)
shutil.copy(os.path.join(ASSETS, 'online', 'peerjs.min.js'), os.path.join(G, 'lib'))
shutil.copy(os.path.join(ASSETS, 'online', 'net.js'), os.path.join(G, 'lib'))
# お題（言語ごと）と、画面の訳
LANGS = ['ja', 'en', 'ko', 'zh']
tj = {}
for lg in LANGS:
    fn = os.path.join(ASSETS, 'topics.json' if lg == 'ja' else 'topics_%s.json' % lg)
    if os.path.exists(fn): tj[lg] = open(fn, encoding='utf-8').read()
open(os.path.join(G, 'lib', 'topics.js'), 'w', encoding='utf-8').write(
    'window.TOPICS_JSON = ' + json.dumps(tj['ja'], ensure_ascii=False) + ';\nwindow.TOPICS_BY_LANG = ' + json.dumps(tj, ensure_ascii=False) + ';\n')
dicts = {}
for lg in LANGS[1:]:
    fn = os.path.join(ASSETS, 'i18n', lg + '.json')
    if os.path.exists(fn): dicts[lg] = json.load(open(fn, encoding='utf-8'))
open(os.path.join(G, 'lib', 'i18n.js'), 'w', encoding='utf-8').write('window.PG_I18N = ' + json.dumps(dicts, ensure_ascii=False, separators=(',', ':')) + ';\n')
for f in os.listdir(DRAW):
    if f.endswith('.webp') and f != 'launcher_art.webp':
        shutil.copy(os.path.join(DRAW, f), os.path.join(G, 'img', f))
# ホーム画面用のアイコンと設定（iPhone の「ホーム画面に追加」・Android の Chrome）
os.makedirs(os.path.join(G, 'icons'))
src_icon = Image.open(os.path.join(ROOT, 'pc', 'icon-512.png')).convert('RGB')
for n in (180, 192, 512):
    src_icon.resize((n, n), Image.LANCZOS).save(os.path.join(G, 'icons', 'icon-%d.png' % n))
json.dump({'name': 'わちゃパ（パーティーゲーム）', 'short_name': 'わちゃパ', 'lang': 'ja', 'start_url': './', 'scope': './',
           'display': 'standalone', 'orientation': 'portrait', 'background_color': '#15102D', 'theme_color': '#15102D',
           'icons': [{'src': 'icons/icon-%d.png' % n, 'sizes': '%dx%d' % (n, n), 'type': 'image/png', 'purpose': 'any'} for n in (192, 512)]
                    + [{'src': 'icons/icon-512.png', 'sizes': '512x512', 'type': 'image/png', 'purpose': 'maskable'}]},
          open(os.path.join(G, 'manifest.webmanifest'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
html = open(os.path.join(G, 'index.html'), encoding='utf-8').read()
html = html.replace('<title>', '<meta name="version" content="%s">\n<meta name="description" content="なんでもアリーナ・ワードウルフ・NGワードなど8つのパーティーゲーム。iPhone・Android・PCのブラウザで、部屋コードを使ってみんなで遊べます。">\n%s<title>' % (VERSION, HEAD_ADS))
open(os.path.join(G, 'index.html'), 'w', encoding='utf-8').write(html)

# サービスワーカー：ゲームのファイルを端末に保存（中身のハッシュを版名に入れるので、更新すると古い保存は消える）
files = []
for root, _, fs in os.walk(G):
    for f in fs:
        files.append(os.path.relpath(os.path.join(root, f), G).replace(os.sep, '/'))
files.sort(); h = hashlib.sha256()
for f in files: h.update(f.encode()); h.update(open(os.path.join(G, f), 'rb').read())
cache = 'pg-%s-%s' % (VERSION, h.hexdigest()[:10])
files = [f for f in files if not f.startswith('bgm/') or f.endswith('.js')]   # BGM は重いので、流したときに保存する
open(os.path.join(G, 'sw.js'), 'w', encoding='utf-8').write('''/* パーティーゲーム：ホーム画面から開いたときもすぐ起動できるよう、ゲームのファイルを端末に保存する */
const CACHE = '%s';
const FILES = %s;
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(['./', ...FILES])).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k.startsWith('pg-') && k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url); if (e.request.method !== 'GET' || u.origin !== location.origin) return;   // 通信（PeerJS）や広告はそのまま通す
  // 画面・設定・プログラムは「通信できれば最新を取りに行き、だめなら保存分」。画像などは保存分を先に使う
  const fresh = e.request.mode === 'navigate' || /\\.(html|js|css|webmanifest)$/.test(u.pathname) || u.pathname.endsWith('/');
  if (fresh) { e.respondWith(caches.open(CACHE).then(c => fetch(e.request).then(res => { if (res.ok) c.put(e.request, res.clone()); return res; }).catch(() => c.match(e.request, { ignoreSearch: true })))); return; }
  e.respondWith(caches.open(CACHE).then(c => c.match(e.request, { ignoreSearch: true }).then(r => r || fetch(e.request).then(res => { if (res.ok) c.put(e.request, res.clone()); return res; }))));
});
''' % (cache, json.dumps(['./' + f for f in files], ensure_ascii=False)))

# 遊ぶ.bat（日本語の Windows のコマンドプロンプトで読めるよう Shift_JIS・CRLF で書く）
bat = r'''@echo off
rem わちゃパ PC 版を、アプリのようなウィンドウで開きます
setlocal
cd /d "%~dp0"
set "URL="
if exist "site.txt" set /p URL=<"site.txt"
if not defined URL set "URL=file:///%~dp0game/index.html"
set "URL=%URL:\=/%"
set "EDGE=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if not exist "%EDGE%" set "EDGE=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"
set "CHROME=%ProgramFiles%\Google\Chrome\Application\chrome.exe"
if not exist "%CHROME%" set "CHROME=%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"
if not exist "%CHROME%" set "CHROME=%LocalAppData%\Google\Chrome\Application\chrome.exe"
if exist "%EDGE%" (
  start "" "%EDGE%" --app="%URL%" --window-size=1280,880
  exit /b 0
)
if exist "%CHROME%" (
  start "" "%CHROME%" --app="%URL%" --window-size=1280,880
  exit /b 0
)
start "" "%URL%"
exit /b 0
'''
open(os.path.join(D, '遊ぶ.bat'), 'wb').write(bat.replace('\n', '\r\n').encode('cp932'))
open(os.path.join(D, 'site.txt'), 'w', encoding='ascii').write(SITE)

readme = f'''わちゃパ PC 版 v{VERSION}
================================
スマホのアプリ（「オンライン」に切り替えたとき）と同じ部屋で遊べます。
なんでもアリーナ・ワードウルフ・NGワード・スパイはだれだ・みんなでシンクロ・質問で当てろ・ぶっちゃけ質問・お絵かき伝言

■ 遊び方
  1. この zip を好きな場所に「すべて展開」する
  2. 「遊ぶ.bat」をダブルクリック（Edge か Chrome がアプリのようなウィンドウで開きます）
  3. 名前を入れて「部屋を作る」→ 出てきた6けたの部屋コードを友だちに送る
     参加する人は「部屋に参加する」→ 部屋コードを入れる（スマホのアプリは「オンライン」に切り替えてから）
  ※ インストールは不要です。インターネットにつながっている必要があります
  ※ 話すゲームは、通話アプリ（LINE・Discord など）をつなぎながら遊んでください
  ※ 初回に「WindowsによってPCが保護されました」と出たら「詳細情報」→「実行」

■ 通信について
  自前のサーバーは使っていません。相手を見つけるときだけ PeerJS の無料の仲介サーバーを使い、
  つながったあとは端末どうしが直接（直接つながらない回線では PeerJS の中継で）やりとりします。

■ 広告（公開する人向け）
  Google AdSense はウェブサイトに置いたページでしか表示されません。
  1. game フォルダの中身を GitHub Pages などに置く（index.html が一番上に来るように）
  2. AdSense に申し込み、審査に通ったら game/config.js に ca-pub- から始まる ID と広告ユニットの ID を書く
  3. site.txt に公開したページの URL（https://～/）を1行で書く
     → 遊ぶ.bat がそのページを開くようになり、ロビー・結果画面の下と、ゲームの合間の休憩画面に広告が出ます
  設定するまでは、広告の場所に小さく「広告枠」と出るだけです。
'''
open(os.path.join(D, 'はじめにお読みください.txt'), 'w', encoding='utf-8-sig').write(readme.replace('\n', '\r\n'))

# iPhone・スマホのブラウザ用：game フォルダの中身をそのままサイトに置ける形で
W = os.path.join(OUT, 'PartyGameWeb'); shutil.rmtree(W, ignore_errors=True); shutil.copytree(G, W)
web_readme = f'''わちゃパ ウェブ版（iPhone・スマホ・PC のブラウザ用） v{VERSION}
==================================================
iPhone では、このフォルダの中身を HTTPS のサイトに置いて Safari で開きます。
「ホーム画面に追加」すると、アプリのように全画面で遊べます（App Store は通しません）。
Android のアプリ（「オンライン」）や PC 版と同じ部屋に、部屋コードで入れます。

■ 1回目だけ：サイトに置く（GitHub Pages・無料）
  1. GitHub で新しいリポジトリを作る（例：partygame。Public）
  2. 「Add file」→「Upload files」で、このフォルダの“中身”をすべてドラッグ
     （index.html・app.js・style.css・config.js・manifest.webmanifest・sw.js・lib・img・icons）→「Commit changes」
  3. リポジトリの「Settings」→「Pages」→ Branch を「main」「/(root)」にして「Save」
  4. 1〜2分後に出る https://（ユーザー名）.github.io/partygame/ が遊ぶ場所です
  ※ Netlify Drop（app.netlify.com/drop）にフォルダごとドラッグしても置けます

■ iPhone に入れる
  1. Safari で上のアドレスを開く
  2. 共有ボタン（□に↑）→「ホーム画面に追加」→「追加」
  3. ホーム画面の「わちゃパ」から起動
  ※ iOS 16.4 以降がおすすめ（遊んでいる間、画面が消えないようにできます）

■ 更新するとき
  新しい版のファイルで上書きアップロード → iPhone ではアプリを2回開き直すと新しい版になります

■ 広告
  この URL で AdSense の審査に通ったら config.js に ID を書いてアップロードし直してください。
  PC 版の site.txt にこの URL を書くと、PC の遊ぶ.bat もこのページを開くようになります。
'''
open(os.path.join(W, 'はじめにお読みください.txt'), 'w', encoding='utf-8-sig').write(web_readme.replace('\n', '\r\n'))
wz = os.path.join(OUT, 'PartyGameWeb.zip')
if os.path.exists(wz): os.remove(wz)
with zipfile.ZipFile(wz, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, _, files2 in os.walk(W):
        for f in files2:
            p = os.path.join(root, f)
            z.write(p, os.path.relpath(p, OUT))

zp = os.path.join(OUT, 'PartyGamePC.zip')
if os.path.exists(zp): os.remove(zp)
with zipfile.ZipFile(zp, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(D):
        for f in files:
            p = os.path.join(root, f)
            z.write(p, os.path.relpath(p, OUT))
print('ok', VERSION, zp, os.path.getsize(zp))
