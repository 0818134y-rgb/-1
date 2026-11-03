"""i18n_src/*.tsv（番号<TAB>英<TAB>韓<TAB>中）を、日本語キーの辞書 app/src/main/assets/i18n/{en,ko,zh}.json にまとめる。
番号は i18n_keys.json の順番。1行目に「#base N」と書くと、その番号から数える（あとでキーが増えても前の訳がずれないように）。
すでにある辞書の訳は残し、新しい分を上書きする。"""
import glob, json, os, re
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
keys = list(json.load(open(os.path.join(ROOT, 'i18n_keys.json'), encoding='utf-8')).keys())
langs = ['en', 'ko', 'zh']
D = {}
for lg in langs:
    fn = os.path.join(ROOT, 'app/src/main/assets/i18n', lg + '.json')
    D[lg] = json.load(open(fn, encoding='utf-8')) if os.path.exists(fn) else {}
def un(s): return s.replace('\\n', '\n')
for f in sorted(glob.glob(os.path.join(ROOT, 'i18n_src', '*.tsv'))):
    kmap = None
    for line in open(f, encoding='utf-8'):
        line = line.rstrip('\n')
        if not line.strip(): continue
        if line.startswith('#keys '):
            kmap = os.path.join(ROOT, 'i18n_src', line[6:].strip()); kmap = list(json.load(open(kmap, encoding='utf-8')).keys()); continue
        if line.startswith('#'): continue
        parts = line.split('\t')
        if len(parts) != 4: raise SystemExit(f'{os.path.basename(f)}: bad line: {line[:80]}')
        i = int(parts[0]); key = (kmap or keys)[i]
        for lg, t in zip(langs, parts[1:]):
            t = un(t)
            if sorted(re.findall(r'\{\d+\}', key)) != sorted(re.findall(r'\{\d+\}', t)):
                raise SystemExit(f'{os.path.basename(f)} #{i} {lg}: placeholders differ: {key!r} → {t!r}')
            if key.count('<') != t.count('<'):
                raise SystemExit(f'{os.path.basename(f)} #{i} {lg}: tags differ: {key!r} → {t!r}')
            D[lg][key] = t
for lg in langs:
    json.dump(D[lg], open(os.path.join(ROOT, 'app/src/main/assets/i18n', lg + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
    print(lg, len(D[lg]))
