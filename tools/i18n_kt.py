"""Kotlin の画面コードの日本語の文字列を tr("…{0}…", 値) に書きかえる（多言語化の下ごしらえ）。

"参加者 ${n}人" → tr("参加者 {0}人", n)。${} の中の文字列も同じように書きかえる。
"""
import re
import sys

JP = re.compile('[ぁ-んァ-ヶ一-龠ー々〜！？。、「」（）…]')


def parse_string(s, i):
    """s[i] == '"' の普通の文字列を読む。@return (終わりの位置, [('t', 文字) | ('e', 式の元テキスト)])"""
    assert s[i] == '"' and not s.startswith('"""', i)
    j = i + 1; parts = []; buf = ''
    while True:
        c = s[j]
        if c == '\\':
            buf += s[j:j + 2]; j += 2; continue
        if c == '"':
            break
        if c == '$' and s[j + 1] == '{':
            if buf: parts.append(('t', buf)); buf = ''
            k = j + 2; depth = 1
            while depth:
                ch = s[k]
                if ch == '"':
                    if s.startswith('"""', k):
                        k = s.index('"""', k + 3) + 3; continue
                    k, _ = parse_string(s, k); continue
                if ch == "'":
                    k = s.index("'", k + 2 if s[k + 1] == '\\' else k + 1) + 1; continue
                if ch == '{': depth += 1
                elif ch == '}': depth -= 1
                k += 1
            parts.append(('e', s[j + 2:k - 1])); j = k; continue
        if c == '$' and (s[j + 1].isalpha() or s[j + 1] == '_'):
            if buf: parts.append(('t', buf)); buf = ''
            k = j + 1
            while s[k].isalnum() or s[k] == '_': k += 1
            parts.append(('e', s[j + 1:k])); j = k; continue
        buf += c; j += 1
    if buf: parts.append(('t', buf))
    return j + 1, parts


def rewrite(s):
    out = []; i = 0; n = len(s)
    while i < n:
        if s.startswith('//', i):
            e = s.find('\n', i); e = n if e < 0 else e
            out.append(s[i:e]); i = e; continue
        if s.startswith('/*', i):
            e = s.index('*/', i) + 2; out.append(s[i:e]); i = e; continue
        if s.startswith('"""', i):
            e = s.index('"""', i + 3) + 3; out.append(s[i:e]); i = e; continue
        c = s[i]
        if c == "'":
            e = s.index("'", i + 2 if s[i + 1] == '\\' else i + 1) + 1; out.append(s[i:e]); i = e; continue
        if c == '"':
            e, parts = parse_string(s, i)
            text = ''.join(p[1] for p in parts if p[0] == 't')
            if JP.search(text):
                tpl = ''; args = []
                for kind, val in parts:
                    if kind == 't': tpl += val.replace('{', '{{').replace('}', '}}') if False else val
                    else: tpl += '{%d}' % len(args); args.append(rewrite(val))
                out.append('tr("' + tpl + '"' + ''.join(', ' + a for a in args) + ')')
            else:
                # 式の中だけ書きかえる
                o = '"'
                for kind, val in parts:
                    o += val if kind == 't' else '${' + rewrite(val) + '}'
                out.append(o + '"')
            i = e; continue
        out.append(c); i += 1
    return ''.join(out)


if __name__ == '__main__':
    total = 0
    for p in sys.argv[1:]:
        src = open(p, encoding='utf-8').read()
        dst = rewrite(src)
        total += dst.count('tr("')
        open(p, 'w', encoding='utf-8').write(dst)
    print('tr() calls:', total)
