"""app.js の日本語の文字列を T('…{0}…', 値) の形に書きかえる（多言語化の下ごしらえ・一度だけ使う）。

'文字' + 値 + '文字' のようにつながった式は、ひとつのテンプレートにまとめる（語順の違う言語に訳せるように）。
"""
import re
import sys

JP = re.compile('[ぁ-んァ-ヶ一-龠ー々〜！？。、「」（）…]')


def scan_string(s, i):
    q = s[i]; j = i + 1
    while s[j] != q:
        j += 2 if s[j] == '\\' else 1
    return j + 1


STOP_WORDS = ('return', 'case', 'typeof', 'else', 'new', 'in', 'of')


def operand_end(s, i, n):
    """i から始まる「+ でつながる1つの項」の終わり"""
    depth = 0; j = i
    while j < n:
        c = s[j]
        if c in '\'"':
            j = scan_string(s, j); continue
        if c in '([{':
            depth += 1
        elif c in ')]}':
            if depth == 0: return j
            depth -= 1
        elif depth == 0:
            if c in ',;?:' or (c == '+' and s[j + 1:j + 2] != '+' and s[j - 1:j] != '+'):
                return j
            if s.startswith('||', j) or s.startswith('&&', j) or s.startswith('===', j) or s.startswith('!==', j) \
                    or s.startswith('==', j) or s.startswith('!=', j):
                return j
            if c == '=' and s[j + 1:j + 2] != '>': return j
            if c in '<>' and not in_tag_context(s, j): return j
            if c == '\n': return j
        j += 1
    return j


def in_tag_context(s, j):
    return False


def find_chain(s, a, b):
    """文字列リテラル s[a:b] を含む + の連なり全体の [start, end) と、項のリスト"""
    # 左へ：直前が '+' なら、その前の項を取る
    ops = [(a, b)]
    start = a
    while True:
        k = start - 1
        while k >= 0 and s[k] in ' \t\n': k -= 1
        if k >= 0 and s[k] == '+' and s[k - 1] not in '+=' and s[k + 1] != '=':
            # 前の項の始まりを探す：後ろ向きに括弧を数える
            e = k - 1
            while s[e] in ' \t\n': e -= 1
            st = backward_operand_start(s, e)
            if st is None: break
            ops.insert(0, (st, e + 1)); start = st
        else:
            break
    end = b
    n = len(s)
    while True:
        k = end
        while k < n and s[k] in ' \t\n': k += 1
        if k < n and s[k] == '+' and s[k + 1] not in '+=':
            k += 1
            while s[k] in ' \t\n': k += 1
            e = operand_end(s, k, n)
            e2 = e
            while s[e2 - 1] in ' \t\n': e2 -= 1
            ops.append((k, e2)); end = e2
        else:
            break
    return start, end, ops


def backward_operand_start(s, e):
    """s[e] で終わる項の始まり（後ろ向き）。うまく取れなければ None"""
    j = e; depth = 0
    while j >= 0:
        c = s[j]
        if c in '\'"':
            # 文字列の始まりまで戻る
            q = c; j -= 1
            while not (s[j] == q and s[j - 1] != '\\'): j -= 1
            if depth == 0:
                return j
            j -= 1; continue
        if c in ')]}':
            depth += 1
        elif c in '([{':
            if depth == 0: return j + 1
            depth -= 1
            if depth == 0:
                # 関数呼び出しの名前やメンバーアクセスまで含める
                k = j - 1
                while k >= 0 and (s[k].isalnum() or s[k] in '_.$'): k -= 1
                if k + 1 < j and (s[k + 1].isalpha() or s[k + 1] in '_$'):
                    j = k + 1
                # 「.join('')」のように前がさらに続くなら続ける
                kk = j - 1
                while kk >= 0 and s[kk] in ' \t\n': kk -= 1
                if kk >= 0 and s[kk] == ')':
                    pass
                prev = j - 1
                if prev >= 0 and s[prev] == '.':
                    j = prev - 1; continue
                return j
        elif depth == 0:
            if c.isalnum() or c in '_.$':
                k = j
                while k >= 0 and (s[k].isalnum() or s[k] in '_.$'): k -= 1
                word = s[k + 1:j + 1]
                if word in STOP_WORDS: return None
                return k + 1
            return None
        j -= 1
    return None


def is_lit(s, a, b):
    t = s[a:b]
    return len(t) >= 2 and t[0] == "'" and t[-1] == "'" and scan_string(s, a) == b


def unquote(t):
    return t[1:-1]


def rewrite(s):
    pieces = []   # (元の始まり, 元の終わり, 出力)
    pos = 0; n = len(s)
    def add(a, b, text): pieces.append((a, b, text))
    while pos < n:
        c = s[pos]
        if s.startswith('//', pos):
            e = s.find('\n', pos); e = n if e < 0 else e
            add(pos, e, s[pos:e]); pos = e; continue
        if s.startswith('/*', pos):
            e = s.find('*/', pos) + 2
            add(pos, e, s[pos:e]); pos = e; continue
        if c == '/' and regex_ok(s, pos):
            e = scan_regex(s, pos); add(pos, e, s[pos:e]); pos = e; continue
        if c in '\'"':
            e = scan_string(s, pos)
            lit = s[pos:e]
            if c == "'" and JP.search(lit):
                start, end, ops = find_chain(s, pos, e)
                # 左の項に、すでに書きかえたものが含まれていたら、左へは広げない
                if any(a >= start and t != s[a:b] for a, b, t in pieces):
                    start, end, ops = pos, end, [o for o in ops if o[0] >= pos]
                tpl = ''; args = []
                for (a, b) in ops:
                    if is_lit(s, a, b): tpl += unquote(s[a:b])
                    else: tpl += '{%d}' % len(args); args.append(rewrite(s[a:b]))
                if not JP.search(tpl):
                    add(pos, e, lit); pos = e; continue
                while pieces and pieces[-1][0] >= start: pieces.pop()
                if pieces and pieces[-1][1] > start:
                    a0, b0, t0 = pieces.pop(); add(a0, start, s[a0:start])
                add(start, end, "T('" + tpl + "'" + ''.join(', ' + x for x in args) + ')'); pos = end; continue
            add(pos, e, lit); pos = e; continue
        add(pos, pos + 1, c); pos += 1
    return ''.join(t for _, _, t in pieces)


def regex_ok(s, pos):
    if s.startswith('//', pos) or s.startswith('/*', pos): return False
    k = pos - 1
    while k >= 0 and s[k] in ' \t': k -= 1
    return k < 0 or s[k] in '(,=:[!&|?{};+\n'


def scan_regex(s, pos):
    j = pos + 1; cls = False
    while True:
        c = s[j]
        if c == '\\': j += 2; continue
        if c == '[': cls = True
        elif c == ']': cls = False
        elif c == '/' and not cls: break
        j += 1
    j += 1
    while s[j].isalpha(): j += 1
    return j


if __name__ == '__main__':
    src = open(sys.argv[1], encoding='utf-8').read()
    out = rewrite(src)
    open(sys.argv[2], 'w', encoding='utf-8').write(out)
    keys = re.findall(r"T\('((?:\\.|[^'\\])*)'", out)
    print('T() calls:', len(keys), 'unique:', len(set(keys)))
