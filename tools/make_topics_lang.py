"""英語・韓国語・中国語のお題（app/src/main/assets/topics_<言語>.json）を作る。
使い方: python3 tools/make_topics_lang.py en ko zh"""
import importlib, json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
OUT = os.path.join(os.path.dirname(HERE), 'app', 'src', 'main', 'assets')

def items(text):
    return [x.strip() for x in text.replace('\n', '').split('|') if x.strip()]

def uniq(xs, label, lang):
    xs = [x.strip() for x in xs if x.strip()]
    d = sorted({x for x in xs if xs.count(x) > 1})
    assert not d, f'{lang}: duplicate {label}: {d}'
    return xs

for lang in sys.argv[1:]:
    M = importlib.import_module('topics_' + lang)
    ww, seen = [], set()
    for cat, text in M.WW.items():
        for tok in items(text):
            a, b = [s.strip() for s in tok.split('/')]
            assert a and b and a != b, (lang, tok)
            key = frozenset((a, b)); assert key not in seen, f'{lang}: duplicate pair {a}/{b}'
            seen.add(key); ww.append({'c': cat, 'a': a, 'b': b})
    ng = []
    for c, text in M.NG.items():
        for w in uniq(items(text), 'ng ' + c, lang): ng.append({'c': c, 'w': w})
    allng = [x['w'] for x in ng]; uniq(allng, 'ng', lang)
    guess = sorted({w for p in ww if p['c'] in M.GUESS_CATEGORIES for w in (p['a'], p['b'])})
    data = {'version': 2, 'lang': M.LANG, 'wordwolf': ww, 'ng': ng,
            'spy': uniq(M.SPY_PLACES, 'spy', lang), 'sync': uniq(M.SYNC_PROMPTS, 'sync', lang), 'guess': guess,
            'questions': uniq(M.QUESTIONS, 'question', lang),
            'drawWho': uniq(M.DRAW_WHO, 'draw who', lang), 'drawWhat': uniq(M.DRAW_WHAT, 'draw what', lang)}
    assert len(data['spy']) >= 13
    json.dump(data, open(os.path.join(OUT, f'topics_{lang}.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    print(lang, 'pairs', len(ww), 'ng', len(ng), 'spy', len(data['spy']), 'sync', len(data['sync']), 'guess', len(guess),
          'q', len(data['questions']), 'draw', len(data['drawWho']), len(data['drawWhat']))
