"""訳すべき日本語の文（キー）を全部集めて i18n/keys.json に書く。訳のファイルに足りないキーも表示する。"""
import json, os, re, subprocess, sys, glob
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JP = re.compile('[ぁ-んァ-ヶ一-龠ー々〜！？。、「」（）…]')
keys = {}
def add(k, where):
    if JP.search(k) and k not in keys: keys[k] = where

def kt_unescape(s):
    return re.sub(r'\\(.)', lambda m: {'n': '\n', 't': '\t'}.get(m.group(1), m.group(1)), s)
def js_unescape(s):
    return re.sub(r'\\(.)', lambda m: {'n': '\n', 't': '\t'}.get(m.group(1), m.group(1)), s)

for f in glob.glob(os.path.join(ROOT, 'app/src/main/java/com/zen/partygame/**/*.kt'), recursive=True):
    src = open(f, encoding='utf-8').read()
    for m in re.finditer(r'\btr\("((?:\\.|[^"\\])*)"', src):
        add(kt_unescape(m.group(1)), os.path.basename(f))
ag = open(os.path.join(ROOT, 'shared/src/commonMain/kotlin/com/zen/partygame/game/ArenaGame.kt'), encoding='utf-8').read()
for m in re.finditer(r'\b(?:log|tx)\("((?:\\.|[^"\\])*)"', ag):
    add(kt_unescape(m.group(1)), 'ArenaGame.kt')
tg = open(os.path.join(ROOT, "shared/src/commonMain/kotlin/com/zen/partygame/game/TableGame.kt"), encoding="utf-8").read()
for m in re.finditer(r'"((?:\\.|[^"\\])*)"', tg):
    add(kt_unescape(m.group(1)), "TableGame.kt")
yg = open(os.path.join(ROOT, "shared/src/commonMain/kotlin/com/zen/partygame/game/YaoGame.kt"), encoding="utf-8").read()
for m in re.finditer(r'"((?:\\.|[^"\\])*)"', yg):
    add(kt_unescape(m.group(1)), "YaoGame.kt")
js = open(os.path.join(ROOT, 'pc/src/app.js'), encoding='utf-8').read()
for m in re.finditer(r"\bT\('((?:\\.|[^'\\])*)'", js):
    add(js_unescape(m.group(1)), 'app.js')
# カード・今夜のルール・ゲーム名（Kotlin → JS にしたものから読む）
kjs = os.path.join(ROOT, 'shared/build/compileSync/js/main/productionExecutable/kotlin/partygame.js')
out = subprocess.run(['node', '-e', '''
const m = require(process.argv[1]);
const G = (m.com ? m : (global['PartyGame:shared'] || m)).com.zen.partygame.game;
console.log(JSON.stringify({cards: JSON.parse(G.arenaCardsJson()), meta: JSON.parse(G.metaJson())}));
''', kjs], capture_output=True, text=True, check=True).stdout
d = json.loads(out)
for c in d['cards']:
    for k in ('name', 'text', 'cover', 'coverName'):
        if c.get(k): add(c[k], 'card:' + c['id'])
for k, c in d['meta']['chaos'].items():
    add(c['title'], 'chaos'); add(c['text'], 'chaos')
for g in d['meta']['games']:
    add(g['label'], 'game'); add(g['short'], 'game')
for k in ['（わからない…）', '、', 'わちゃパ｜ワードウルフ・NGワードほかパーティーゲーム']:
    add(k, 'misc')
os.makedirs(os.path.join(ROOT, 'app/src/main/assets/i18n'), exist_ok=True)
json.dump(keys, open(os.path.join(ROOT, 'i18n_keys.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
print('keys:', len(keys), 'chars:', sum(len(k) for k in keys))
for lg in sys.argv[1:]:
    fn = os.path.join(ROOT, 'app/src/main/assets/i18n', lg + '.json')
    have = json.load(open(fn, encoding='utf-8')) if os.path.exists(fn) else {}
    miss = [k for k in keys if k not in have]
    bad = [k for k in keys if k in have and sorted(re.findall(r'\{\d+\}', k)) != sorted(re.findall(r'\{\d+\}', have[k]))]
    print(lg, 'missing', len(miss), 'placeholder mismatch', len(bad), bad[:5])
