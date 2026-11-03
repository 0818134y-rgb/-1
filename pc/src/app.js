/*
 * パーティーゲーム PC 版。
 * スマホのアプリと同じ部屋に「オンライン」で入れる。ゲームの進行（Engine）はアプリと同じ Kotlin のコードを
 * JavaScript にしたもの（lib/partygame.js）で、PC が部屋を作ったときはこの中で動く。
 */
(function () {
  'use strict';
  // ゲームの進行（Kotlin → JavaScript）。読み込むと window['PartyGame:shared'] に入る
  const G = (window['PartyGame:shared'] || window.partygame).com.zen.partygame.game;
  const META = JSON.parse(G.metaJson());
  const PROTO = META.proto;
  const CARDS = {}; JSON.parse(G.arenaCardsJson()).forEach(function (c) { CARDS[c.id] = c; });
  const GAMES = {}; META.games.forEach(function (g) { GAMES[g.id] = g; });
  const CFG = window.PG_CONFIG || {};
  const HOST_ID = 'h';
  const $main = document.getElementById('main');

  const S = {
    page: 'title',              // title / join / room
    name: load('pg.name', ''),
    code: load('pg.code', ''),
    busy: false,
    view: null, receivedAt: 0,
    host: null,                 // JsHost（自分がホストのとき）
    connected: new Set(), pendingNames: new Map(), lastVersion: -1,
    tickTimer: null,
    ui: {},                     // 画面だけの状態（選んだカード・のぞき見など）
  };
  function load(k, d) { try { return localStorage.getItem(k) || d; } catch (e) { return d; } }
  function save(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function img(name, size, cls) { return '<img src="img/' + name + '.webp" width="' + size + '" height="' + size + '" class="' + (cls || '') + '" alt="">'; }
  function nameOf(id) { const v = S.view; const p = v && v.players.find(function (x) { return x.id === id; }); return p ? p.name : '?'; }
  function toast(t) {
    const el = document.getElementById('toast'); el.textContent = t; el.hidden = false;
    clearTimeout(toast.t); toast.t = setTimeout(function () { el.hidden = true; }, 3500);
  }
  function fmt(ms) { const s = Math.ceil(Math.max(0, ms) / 1000); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); }
  function remaining() { const v = S.view; if (!v || v.remainingMs == null) return null; return v.remainingMs - (performance.now() - S.receivedAt); }

  // ================= 通信 =================

  function send(intent) {
    const json = JSON.stringify(intent);
    if (S.host) { if (S.host.apply(HOST_ID, json)) publish(); }
    else PgNet.send(['host'], json);
  }

  function hostRoom() {
    resetNet();
    const code = String(100000 + Math.floor(Math.random() * 900000));
    S.host = new G.JsHost(code, window.TOPICS_JSON);
    S.host.join(HOST_ID, S.name, true);
    S.host.preferArena();
    PgNet.host(PROTO, code, S.name);
    S.tickTimer = setInterval(function () { if (S.host && S.host.tick()) publish(); }, 250);
    S.page = 'room'; publish(true);
  }

  function joinRoom(code) {
    code = String(code).replace(/\D/g, '');
    if (code.length !== 6) { toast('部屋コードは6けたの数字です'); return; }
    S.code = code; save('pg.code', code);
    resetNet(); S.busy = true; S.page = 'join'; render();
    PgNet.join(PROTO, code, S.name || 'ゲスト');
  }

  function leave() {
    if (S.host && S.connected.size) PgNet.send(Array.from(S.connected), JSON.stringify({ t: 'bye', reason: 'ホストが部屋を閉じました' }));
    const wasHost = !!S.host;
    setTimeout(function () { resetNet(); }, wasHost ? 300 : 0);
    if (!wasHost) resetNet();
    S.host = null; S.view = null; S.page = 'title'; S.busy = false;
    closeModal(); render();
  }

  function resetNet() {
    if (S.tickTimer) clearInterval(S.tickTimer); S.tickTimer = null;
    PgNet.stop();
    S.connected.clear(); S.pendingNames.clear(); S.lastVersion = -1;
  }

  /** ホスト：状態が変わっていれば自分の画面と全員に送る */
  function publish(force) {
    const h = S.host; if (!h) return;
    if (!force && h.version() === S.lastVersion) return;
    S.lastVersion = h.version();
    setView(JSON.parse(h.viewJson(HOST_ID)));
    const ids = new Set(h.playerIds());
    S.connected.forEach(function (id) {
      if (ids.has(id)) PgNet.send([id], '{"t":"view","view":' + h.viewJson(id) + '}');
      else {
        S.connected.delete(id);
        PgNet.send([id], JSON.stringify({ t: 'bye', reason: 'ホストに退出させられました' }));
        setTimeout(function () { PgNet.disconnect(id); }, 500);
      }
    });
  }

  function setView(v) {
    const prev = S.view;
    S.view = v; S.receivedAt = performance.now(); S.page = 'room';
    if (!prev || prev.screen.t !== v.screen.t) S.ui = {};
    render();
  }

  PgNet.onEvent = function (e) {
    if (S.host) onHostNet(e); else onClientNet(e);
  };

  function onHostNet(e) {
    const h = S.host;
    switch (e.ev) {
      case 'join':
        if (e.proto !== PROTO || !h.canJoin()) PgNet.reject(e.id);
        else { S.pendingNames.set(e.id, e.name); PgNet.accept(e.id); }
        break;
      case 'connected': {
        const n = S.pendingNames.get(e.id) || 'ゲスト'; S.pendingNames.delete(e.id);
        if (h.join(e.id, n, false) === 'ok') { S.connected.add(e.id); publish(true); }
        else { PgNet.send([e.id], JSON.stringify({ t: 'bye', reason: 'ゲーム中のため参加できませんでした' })); setTimeout(function () { PgNet.disconnect(e.id); }, 500); }
        break;
      }
      case 'data':
        if (S.connected.has(e.id) && h.apply(e.id, e.text)) publish();
        break;
      case 'closed':
        S.pendingNames.delete(e.id);
        if (S.connected.delete(e.id)) {
          const who = nameOf(e.id);
          h.leave(e.id); publish();
          toast(who + 'さんが抜けました');
        }
        break;
      case 'taken': if (!S.connected.size) hostRoom(); break;
      case 'error': toast(e.message === 'network' ? 'インターネットにつながりません' : '通信エラー（' + e.message + '）'); break;
    }
  }

  function onClientNet(e) {
    switch (e.ev) {
      case 'connected': break;   // 最初の画面が届くのを待つ
      case 'failed':
        S.busy = false; S.page = 'join'; render();
        toast(e.reason === 'notfound' ? '部屋が見つかりません。部屋コードを確かめてください（アプリの版が違うときも入れません）'
          : e.reason === 'rejected' ? '参加できませんでした（ゲーム中・満員・版が違う、のどれかです）' : '接続できませんでした。もう一度試してください');
        break;
      case 'data': {
        let m = null; try { m = JSON.parse(e.text); } catch (err) { return; }
        if (m.t === 'view') { S.busy = false; setView(m.view); }
        else if (m.t === 'bye') { resetNet(); S.view = null; S.page = 'title'; closeModal(); render(); toast(m.reason); }
        break;
      }
      case 'closed':
        if (S.page === 'room') { resetNet(); S.view = null; S.page = 'title'; closeModal(); render(); toast('ホストとの接続が切れました'); }
        break;
      case 'error': toast(e.message === 'network' ? 'インターネットにつながりません' : '通信エラー'); break;
    }
  }

  // ================= 広告 =================

  const adsOn = !!(CFG.adsenseClient && /^https?:/.test(location.protocol));
  let adsLoaded = false;
  function loadAds() {
    if (!adsOn || adsLoaded) return; adsLoaded = true;
    const s = document.createElement('script'); s.async = true; s.crossOrigin = 'anonymous';
    s.src = 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=' + encodeURIComponent(CFG.adsenseClient);
    document.head.appendChild(s);
  }
  function adUnit(slot, style) {
    if (!adsOn || !slot) return '<div class="adph">広告枠</div>';
    return '<ins class="adsbygoogle" style="' + (style || 'display:block') + '" data-ad-client="' + esc(CFG.adsenseClient) + '" data-ad-slot="' + esc(slot) + '" data-ad-format="auto" data-full-width-responsive="true"></ins>';
  }
  function pushAd() { try { (window.adsbygoogle = window.adsbygoogle || []).push({}); } catch (e) {} }
  const $banner = document.getElementById('banner');
  let bannerReady = false;
  function showBanner(on) {
    $banner.hidden = !on;
    if (on && !bannerReady) { bannerReady = true; loadAds(); $banner.innerHTML = adUnit(CFG.bannerSlot, innerWidth <= 520 ? 'display:inline-block;width:320px;height:50px' : 'display:inline-block;width:728px;height:90px'); if (adsOn && CFG.bannerSlot) pushAd(); }
  }

  /** ゲームの合間の広告休憩（全員の端末で同時に出る） */
  let adBreakShown = -1;
  function adBreak(id) {
    if (adBreakShown === id) return; adBreakShown = id;
    loadAds();
    let left = adsOn && CFG.breakSlot ? 5 : 3;
    openModal('<div class="box">' + img('icon_tea', 90, 'art') + '<h2>ちょっと休憩…</h2><div class="adbox">' + adUnit(CFG.breakSlot) +
      '</div><button class="big" id="adClose" disabled>あと ' + left + ' 秒</button></div>');
    if (adsOn && CFG.breakSlot) pushAd();
    const btn = document.getElementById('adClose');
    const t = setInterval(function () {
      left--; if (left > 0) { btn.textContent = 'あと ' + left + ' 秒'; return; }
      clearInterval(t); btn.disabled = false; btn.textContent = '閉じて次へ';
    }, 1000);
    btn.onclick = function () { closeModal(); send({ t: 'adDone', breakId: id }); };
  }

  function openModal(html) { const m = document.getElementById('modal'); m.innerHTML = html; m.hidden = false; }
  function closeModal() { const m = document.getElementById('modal'); m.hidden = true; m.innerHTML = ''; }
  function confirmBox(title, text, okLabel, onOk) {
    openModal('<div class="box"><h2>' + esc(title) + '</h2><div class="cap">' + esc(text) + '</div><div class="row"><button class="big sec grow" id="mNo">やめる</button><button class="big berry grow" id="mOk">' + esc(okLabel) + '</button></div></div>');
    document.getElementById('mNo').onclick = closeModal;
    document.getElementById('mOk').onclick = function () { closeModal(); onOk(); };
  }

  // ================= 描画 =================

  function render() {
    // 入力中の文字とフォーカスを保つ
    const ae = document.activeElement, focusId = ae && ae.id, sel = ae && ae.selectionStart;
    const bar = document.getElementById('roomBar');
    let html = '', banner = false;
    if (S.page === 'title') { bar.hidden = true; html = titlePage(); banner = true; }
    else if (S.page === 'join') { bar.hidden = true; html = joinPage(); banner = true; }
    else if (S.view) {
      const v = S.view;
      bar.hidden = false;
      document.getElementById('roomLabel').textContent = '🌐 部屋 ' + v.roomCode + (v.isHost ? '（あなたがホスト）' : '');
      html = screenHtml(v);
      banner = ['lobby', 'arenaResult', 'wwResult', 'ngResult', 'syncReveal', 'guessResult', 'qReveal'].indexOf(v.screen.t) >= 0;
      if (v.screen.t === 'adBreak') adBreak(v.screen.breakId);
    }
    $main.innerHTML = html;
    showBanner(banner);
    if (focusId) { const el = document.getElementById(focusId); if (el) { el.focus(); try { el.setSelectionRange(sel, sel); } catch (e) {} } }
    tickTimers();
  }

  function tickTimers() {
    const r = remaining();
    document.querySelectorAll('[data-timer]').forEach(function (el) {
      if (r == null) { el.textContent = ''; return; }
      el.textContent = fmt(r); el.classList.toggle('low', r < Number(el.dataset.low || 10000));
    });
    document.querySelectorAll('[data-nose]').forEach(function (el) {
      const left = Math.max(0, META.noseWindowMs - (performance.now() - (S.ui.goSeenAt || 0)));
      el.textContent = 'あと ' + (left / 1000).toFixed(1) + ' 秒';
    });
  }
  setInterval(tickTimers, 100);

  function titlePage() {
    return '<div class="title">' +
      '<div class="hero"><img src="img/arena_hero.webp" alt=""><span class="badge">一押し！ なんでもアリーナ</span></div>' +
      '<div class="stack">' +
      '<div class="row">' + img('char_wolf_hello', 96) + '<div><h1>パーティーゲーム</h1><div class="cap">なんでもアリーナ・ワードウルフ・NGワード ほか全7種<br>iPhone・PC・Androidアプリの人と、部屋コードでいっしょに遊べます</div></div></div>' +
      '<input id="nameIn" class="text" maxlength="10" placeholder="あなたの名前（10文字まで）" value="' + esc(S.name) + '">' +
      '<button class="big" data-act="host"' + (S.name.trim() ? '' : ' disabled') + '>' + img('icon_create', 36) + '部屋を作る</button>' +
      '<button class="big sec" data-act="toJoin"' + (S.name.trim() ? '' : ' disabled') + '>' + img('icon_join', 36) + '部屋に参加する</button>' +
      '<div class="cap">話すゲームは、通話アプリ（LINE・Discord など）をつなぎながら遊びましょう。' +
      (CFG.privacyUrl ? ' <a class="link" href="' + esc(CFG.privacyUrl) + '" target="_blank">プライバシーポリシー</a>' : '') + '</div>' +
      '</div></div>';
  }

  function joinPage() {
    return '<div class="stack" style="max-width:520px;margin:30px auto;width:100%">' +
      '<h2 class="center">部屋コードで参加</h2>' +
      '<div class="cap center">部屋を作った人の画面に出ている6けたの数字を入れてください（スマホのアプリの部屋にも入れます）</div>' +
      img('char_wolf_hello', 140, 'art') +
      '<input id="codeIn" class="text code" maxlength="6" inputmode="numeric" value="' + esc(S.code) + '"' + (S.busy ? ' disabled' : '') + '>' +
      (S.busy ? '<div class="center"><span class="spin"></span>　つないでいます…</div>' : '<button class="big" data-act="join">入る</button>') +
      '<button class="big sec" data-act="back">もどる</button></div>';
  }

  function hostOnly(v, waiting, html) { return v.isHost ? html : (waiting ? '<div class="cap center">' + esc(waiting) + '</div>' : ''); }
  function nextButtons(v, label) {
    return hostOnly(v, 'ホストが次に進むのを待っています…',
      '<div class="row"><button class="big grow" data-act="next">' + esc(label || 'もう一回') + '</button><button class="big sec grow" data-act="lobby">ロビーへ（ゲームを変える）</button></div>');
  }
  function timerHeader(icon, title, low) {
    return '<div class="row">' + img(icon, 48) + '<h2 class="grow">' + esc(title) + '</h2><span class="timer" data-timer data-low="' + (low || 10000) + '"></span></div>';
  }
  function scoreboard(scores, gained) {
    return '<div class="panel"><h3>得点</h3><ul class="score">' + scores.map(function (s, i) {
      const g = (gained || []).find(function (x) { return x.id === s.id; });
      return '<li><span class="grow">' + (i + 1) + '. ' + esc(nameOf(s.id)) + (s.id === S.view.me ? '（あなた）' : '') + '</span>' +
        (g && g.votes > 0 ? '<span style="color:var(--mint)">+' + g.votes + '　</span>' : '') + '<b style="color:var(--lantern)">' + s.votes + '点</b></li>';
    }).join('') + '</ul></div>';
  }

  function screenHtml(v) {
    const s = v.screen;
    switch (s.t) {
      case 'lobby': return lobby(v);
      case 'wwReveal': return wwReveal(v, s);
      case 'wwTalk': return wwTalk(v, s);
      case 'wwVote': return wwVote(v, s);
      case 'wwGuess': return wwGuess(v, s);
      case 'wwResult': return wwResult(v, s);
      case 'ngPlay': return ngPlay(v, s);
      case 'ngResult': return ngResult(v, s);
      case 'syncAnswer': return syncAnswer(v, s);
      case 'syncReveal': return syncReveal(v, s);
      case 'guessPlay': return guessPlay(v, s);
      case 'guessResult': return guessResult(v, s);
      case 'qVote': return qVote(v, s);
      case 'qReveal': return qReveal(v, s);
      case 'arena': return arena(v, s);
      case 'arenaResult': return arenaResult(v, s);
      case 'adBreak': return '<div class="center" style="margin-top:80px">' + img('icon_tea', 120, 'art') + '<h2>ちょっと休憩…</h2><div class="cap">全員の準備ができたら次へ進みます</div></div>';
      default: return '<div class="cap">（この画面は PC 版では表示できません）</div>';
    }
  }

  // ---------- ロビー ----------

  const ICON = { ARENA: 'icon_arena', WORD_WOLF: 'icon_wordwolf', NG_WORD: 'icon_ngword', SPY: 'icon_spy', SYNC: 'icon_sync', GUESS: 'icon_guess', QUESTION: 'icon_question' };
  function summary(st) {
    switch (st.game) {
      case 'ARENA': return (st.arenaTurnSec === 0 ? '手番の制限なし' : '手番 ' + st.arenaTurnSec + '秒') + (st.arenaChaos ? '・今夜のルールあり' : '');
      case 'WORD_WOLF': return '話し合い ' + st.talkSec / 60 + '分';
      case 'SPY': return '質問タイム ' + st.talkSec / 60 + '分';
      case 'NG_WORD': return '制限時間 ' + st.ngSec / 60 + '分';
      case 'SYNC': return st.syncSec === 0 ? '回答時間 制限なし' : '回答時間 ' + st.syncSec + '秒';
      case 'GUESS': return '制限時間 ' + st.guessSec / 60 + '分';
      default: return '全員で匿名投票';
    }
  }
  function chips(title, items) {
    return '<h3 style="margin-top:12px">' + esc(title) + '</h3><div class="row">' + items.map(function (it) {
      return '<button class="chip' + (it.on ? ' on' : '') + '" data-act="set" data-arg=\'' + esc(JSON.stringify(it.patch)) + '\'>' + esc(it.label) + '</button>';
    }).join('') + '</div>';
  }

  function lobby(v) {
    const st = v.settings;
    let h = '<div class="panel"><div class="cap center">オンラインの部屋コード。参加する人は「部屋に参加する」でこの数字を入れます（スマホのアプリは「オンライン」に切り替えてから）</div>' +
      '<div class="code">' + esc(v.roomCode) + '</div><div class="center row" style="justify-content:center"><button class="btn" data-act="copy">コードをコピー</button>' + (navigator.share ? '<button class="btn lan" data-act="share">招待を送る</button>' : '') + '</div></div>';
    h += '<div class="panel"><h3>参加者 ' + v.players.length + '人</h3><ul class="players">' + v.players.map(function (p) {
      return '<li><span class="grow">' + (p.isHost ? '👑 ' : '・ ') + esc(p.name) + (p.id === v.me ? '（あなた）' : '') + '</span>' +
        (v.isHost && !p.isHost ? '<button class="link" data-act="kick" data-arg="' + esc(p.id) + '">退出させる</button>' : '') + '</li>';
    }).join('') + '</ul></div>';
    if (v.isHost) {
      h += '<div class="panel"><h3>ゲーム</h3>' +
        '<div class="featured' + (st.game === 'ARENA' ? ' on' : '') + '" data-act="game" data-arg="ARENA"><img class="bg" src="img/arena_hero.webp" alt="">' +
        '<span class="badge tl">一押し！ NEW</span>' + (st.game === 'ARENA' ? '<span class="badge lan tr">✓ 選択中</span>' : '') +
        '<div class="band"><div class="row">' + img('icon_arena', 38) + '<b>なんでもアリーナ</b></div><div class="cap" style="color:var(--soft)">何が起きるかわからない理不尽カードバトル（2〜8人）</div></div></div>' +
        '<div class="games" style="margin-top:12px">' + META.games.filter(function (g) { return g.id !== 'ARENA'; }).map(function (g) {
          return '<button class="game' + (st.game === g.id ? ' on' : '') + '" data-act="game" data-arg="' + g.id + '">' + img(ICON[g.id], 54) + esc(g.short) + '</button>';
        }).join('') + '</div>';
      switch (st.game) {
        case 'ARENA':
          h += chips('1手番の制限時間（過ぎると勝手にカードが出る）', [[30, '30秒'], [60, '60秒'], [90, '90秒'], [0, 'なし']].map(function (x) { return { label: x[1], on: st.arenaTurnSec === x[0], patch: { arenaTurnSec: x[0] } }; }));
          h += chips('今夜のルール（開始時にランダムで1つ追加）', [{ label: 'あり', on: st.arenaChaos, patch: { arenaChaos: true } }, { label: 'なし（初めての人向け）', on: !st.arenaChaos, patch: { arenaChaos: false } }]);
          break;
        case 'WORD_WOLF': case 'SPY': {
          const role = st.game === 'SPY' ? 'スパイ' : 'ウルフ';
          h += chips(st.game === 'SPY' ? '質問タイム' : '話し合いの時間', [120, 180, 240, 300, 420].map(function (x) { return { label: x / 60 + '分', on: st.talkSec === x, patch: { talkSec: x } }; }));
          h += chips(role + 'の人数', [[0, 'おまかせ'], [1, '1人'], [2, '2人']].map(function (x) { return { label: x[1], on: st.wolves === x[0], patch: { wolves: x[0] } }; }));
          if (st.game === 'WORD_WOLF') h += chips('お題のジャンル（選ばなければ全部）', v.categories.map(function (c) {
            const on = st.categories.indexOf(c) >= 0;
            return { label: c, on: on, patch: { categories: on ? st.categories.filter(function (x) { return x !== c; }) : st.categories.concat([c]) } };
          }));
          break;
        }
        case 'NG_WORD':
          h += chips('制限時間', [180, 300, 600].map(function (x) { return { label: x / 60 + '分', on: st.ngSec === x, patch: { ngSec: x } }; }));
          h += chips('ワードの難しさ', [['NORMAL', 'ふつう'], ['HARD', 'ハード（口ぐせ）'], ['MIX', 'ミックス']].map(function (x) { return { label: x[1], on: st.ngLevel === x[0], patch: { ngLevel: x[0] } }; }));
          break;
        case 'SYNC':
          h += chips('回答時間', [[30, '30秒'], [60, '60秒'], [90, '90秒'], [0, '制限なし']].map(function (x) { return { label: x[1], on: st.syncSec === x[0], patch: { syncSec: x[0] } }; }));
          break;
        case 'GUESS':
          h += chips('制限時間', [120, 180, 300].map(function (x) { return { label: x / 60 + '分', on: st.guessSec === x, patch: { guessSec: x } }; }));
          break;
        default: h += '<div class="cap" style="margin-top:10px">設定はありません。だれが誰に入れたかは表示されません。</div>';
      }
      h += '</div>';
      const need = GAMES[st.game].min;
      h += '<button class="big" data-act="start"' + (v.players.length >= need ? '' : ' disabled') + '>' + img(ICON[st.game], 36) + (v.players.length >= need ? 'はじめる' : need + '人からはじめられます') + '</button>';
    } else {
      h += '<div class="panel"><div class="row">' + img(ICON[st.game], 60) + '<div><h2 style="color:var(--lantern)">' + esc(GAMES[st.game].label) + '</h2><div class="cap">' + esc(summary(st)) + '</div></div></div></div>' +
        '<div class="cap center">ホストがはじめるのを待っています…</div>';
    }
    return h;
  }

  // ---------- ワードウルフ／スパイ ----------

  function spyGame() { return S.view.settings.game === 'SPY'; }
  function role() { return spyGame() ? 'スパイ' : 'ウルフ'; }
  function spyCard() { return '<div class="panel"><div class="row">' + img('icon_spy', 64) + '<div><h2 style="color:var(--berry)">あなたはスパイ</h2><div class="cap">みんなの質問や答えから場所を推理しよう。バレないように！</div></div></div></div>'; }

  function wwReveal(v, s) {
    const done = s.confirmed.indexOf(v.me) >= 0;
    let h = '<div class="row">' + img(ICON[v.settings.game], 48) + '<h2>' + (spyGame() ? '場所' : 'お題') + '</h2></div>';
    if (s.spy) h += spyCard();
    else h += '<div class="cap">' + (spyGame() ? 'あなたがいる場所（ほかの人に見せないで）' : 'あなたのお題（ほかの人に見せないで）') + '</div><div class="panel"><div class="word">' + esc(s.word) + '</div></div>' +
      '<div class="cap">' + (spyGame() ? '全員同じ場所にいますが、1人だけ場所を知らないスパイがまぎれています。' : '多数派（市民）か少数派（ウルフ）かは、まだ誰にも分かりません。話しながら探りましょう。') + '</div>';
    h += '<button class="big" data-act="send" data-arg=\'{"t":"confirm"}\'' + (done ? ' disabled' : '') + '>' + (done ? '確認しました' : '確認した') + '</button>';
    h += '<div class="cap center">確認済み ' + s.confirmed.length + ' / ' + v.players.length + '人</div>';
    return h;
  }

  function wwTalk(v, s) {
    let h = '<div class="row">' + img('icon_timer', 60) + '<h2 class="grow">' + (spyGame() ? '質問タイム' : '話し合い') + '</h2><span class="timer" data-timer data-low="30000"></span></div>';
    if (s.spy) h += spyCard();
    else if (s.word != null) h += '<div class="panel" data-act="peek" style="cursor:pointer"><div class="cap">' + (spyGame() ? 'あなたがいる場所' : 'あなたのお題') + '（クリックで表示／非表示）</div><div class="word">' + (S.ui.peek ? esc(s.word) : '●●●') + '</div></div>';
    if (spyGame()) h += '<div class="cap">例：「ここには何をしに来ますか？」「今どんな服を着ていますか？」</div>';
    else h += '<div class="cap">お題について話し合って、ウルフ（違うお題の人）を探しましょう</div>';
    h += hostOnly(v, '時間になると投票に進みます', '<button class="big sec" data-act="send" data-arg=\'{"t":"endTalk"}\'>切り上げて投票へ</button>');
    return h;
  }

  function wwVote(v, s) {
    let h = '<div class="row">' + img('icon_vote', 48) + '<h2>' + (s.runoff ? '決選投票' : '投票') + '</h2></div>';
    h += '<div class="cap">' + (s.runoff ? '同票だった人の中から、' : '') + role() + 'だと思う人を選んでください</div><div class="choices">' +
      s.candidates.filter(function (id) { return id !== v.me; }).map(function (id) {
        return '<button class="choice' + (s.myVote === id ? ' on' : '') + '" data-act="send" data-arg=\'' + esc(JSON.stringify({ t: 'vote', target: id })) + '\'>' + (s.myVote === id ? '✓ ' : '') + esc(nameOf(id)) + '</button>';
      }).join('') + '</div>';
    h += '<div class="cap center">投票済み ' + s.voted.length + ' / ' + v.players.length + '人' + (s.myVote ? '（全員そろうまで変更できます）' : '') + '</div>';
    h += hostOnly(v, '', '<button class="big sec" data-act="send" data-arg=\'{"t":"closeVote"}\'>締め切って集計</button>');
    return h;
  }

  function wwGuess(v, s) {
    let h = img('char_wolf_caught', 170, 'art') + '<h2 class="center" style="color:var(--berry)">' + role() + '発見！</h2>' +
      '<div class="panel"><div class="cap">' + role() + 'は</div><div class="word s" style="color:var(--berry)">' + esc(s.wolves.map(nameOf).join('、')) + '</div></div>';
    if (spyGame()) {
      h += '<div>' + (s.iAmWolf ? '最後のチャンス！場所を当てれば逆転勝ち。下から選んでください。' : 'スパイが場所を当てたら逆転されます。スパイが選ぶのを待っています…') + '</div>';
      if (s.iAmWolf) h += '<div class="choices">' + s.options.map(function (o) { return '<button class="choice" data-act="send" data-arg=\'' + esc(JSON.stringify({ t: 'spyGuess', answer: o })) + '\'>' + esc(o) + '</button>'; }).join('') + '</div>';
      return h;
    }
    h += '<div>' + (s.iAmWolf ? '最後のチャンス！市民のお題を当てれば逆転勝ち。口で答えてください。' : 'でも、ウルフが市民のお題を当てたら逆転されます。ウルフに答えてもらいましょう。') + '</div>';
    h += hostOnly(v, 'ホストが判定します…', '<div class="cap">ウルフの答えは市民のお題と…</div><div class="row"><button class="big berry grow" data-act="send" data-arg=\'{"t":"judge","correct":true}\'>合っていた</button><button class="big grow" style="background:var(--mint)" data-act="send" data-arg=\'{"t":"judge","correct":false}\'>違った</button></div>');
    return h;
  }

  function wwResult(v, s) {
    const cw = s.winner === 'CITIZEN', spy = spyGame(), r = role();
    const reason = { WOLF_CAUGHT: spy ? 'スパイを見つけて、場所も守りきりました' : 'ウルフを見つけて、逆転も防ぎました',
      WOLF_GUESSED: spy ? 'スパイが場所を当てて逆転！' : 'ウルフが市民のお題を当てて逆転！',
      CITIZEN_ACCUSED: nameOf(s.accused) + 'さん（' + r + 'ではない）が最多票に…' + r + 'の逃げ切り', TIE: '票が割れて、' + r + 'の逃げ切り' }[s.reason];
    let h = img(cw ? 'char_villagers' : 'char_wolf_grin', 170, 'art') +
      '<div class="word" style="color:' + (cw ? 'var(--mint)' : 'var(--berry)') + '">' + (cw ? (spy ? 'みんなの勝ち！' : '市民の勝ち！') : r + 'の勝ち！') + '</div><div class="cap center">' + esc(reason) + '</div>';
    h += '<div class="panel">';
    if (spy) h += '<div class="cap">場所</div><h2 style="color:var(--mint)">' + esc(s.citizenWord) + '</h2>' + (s.spyGuess ? '<div class="cap">スパイの答え</div><h2 style="color:var(--berry)">' + esc(s.spyGuess) + '</h2>' : '');
    else h += '<div class="cap">市民のお題</div><h2 style="color:var(--mint)">' + esc(s.citizenWord) + '</h2><div class="cap">ウルフのお題</div><h2 style="color:var(--berry)">' + esc(s.wolfWord) + '</h2>';
    h += '<div class="cap">' + r + '</div><h3>' + esc(s.wolves.map(nameOf).join('、')) + '</h3>' + (s.myWord && !spy ? '<div class="cap">あなたのお題：' + esc(s.myWord) + '</div>' : '') + '</div>';
    if (s.tally.length) h += '<div class="panel"><h3>得票</h3>' + s.tally.map(function (t) { return '<div>' + esc(nameOf(t.id)) + '　' + t.votes + '票</div>'; }).join('') + '</div>';
    return h + nextButtons(v);
  }

  // ---------- NGワード ----------

  function ngPlay(v, s) {
    let h = timerHeader('icon_ngword', 'NGワード', 30000);
    const mine = s.entries.find(function (e) { return e.id === v.me; });
    h += s.myOut ? '<div class="panel"><h2 style="color:var(--berry)">アウト！</h2><div>あなたのNGワードは「' + esc(mine && mine.word) + '」でした。ほかの人を見張りましょう</div></div>'
      : '<div class="cap">みんなのNGワードが見えます（あなたのだけ見えません）。言わせたら「言った！」</div>';
    h += s.entries.map(function (e) {
      const me = e.id === v.me;
      let btn = '';
      if (e.out && v.isHost) btn = '<button class="link" data-act="send" data-arg=\'' + esc(JSON.stringify({ t: 'undoOut', target: e.id })) + '\'>取り消し</button>';
      else if (e.out) btn = '<span class="warn">アウト</span>';
      else if (!me && e.word != null) btn = '<button class="btn on" data-act="ngOut" data-arg="' + esc(e.id) + '">言った！</button>';
      return '<div class="panel ng"><div class="grow"><div class="cap">' + esc(nameOf(e.id)) + (me ? '（あなた）' : '') + '</div><div class="w' + (e.out ? ' out' : '') + '">' + esc(e.word == null ? '？？？' : e.word) + '</div></div>' + btn + '</div>';
    }).join('');
    h += hostOnly(v, '', '<button class="big sec" data-act="send" data-arg=\'{"t":"endNg"}\'>ゲームを終える</button>');
    return h;
  }

  function ngResult(v, s) {
    let h = img(s.winners.length ? 'char_villagers' : 'char_wolf_caught', 160, 'art') +
      '<div class="word">' + (s.winners.length ? esc(s.winners.map(nameOf).join('、')) + 'の勝ち！' : '全員アウト！') + '</div>';
    h += '<div class="panel"><h3>みんなのNGワード</h3>' + s.entries.slice().sort(function (a, b) { return (a.out - b.out) || (b.outOrder - a.outOrder); }).map(function (e) {
      return '<div class="row"><span class="grow">' + esc(nameOf(e.id)) + '</span><span style="color:' + (e.out ? 'var(--muted)' : 'var(--mint)') + '">「' + esc(e.word) + '」' + (e.out ? '　' + e.outOrder + '番目にアウト' : '　生き残り') + '</span></div>';
    }).join('') + '</div>';
    return h + nextButtons(v);
  }

  // ---------- シンクロ ----------

  function syncAnswer(v, s) {
    if (S.ui.prompt !== s.prompt) { S.ui.prompt = s.prompt; S.ui.draft = s.myAnswer || ''; }
    let h = timerHeader('icon_sync', 'みんなでシンクロ');
    h += '<div class="cap">みんなと同じ答えになるほど得点！</div><div class="panel"><div class="word s">' + esc(s.prompt) + '</div></div>';
    h += '<input id="syncIn" class="text" maxlength="20" placeholder="あなたの答え" value="' + esc(S.ui.draft) + '">';
    h += '<button class="big" data-act="syncSend">' + (s.myAnswer == null ? 'これで答える' : '答えを変える') + '</button>';
    if (s.myAnswer != null) h += '<div class="cap">あなたの答え：「' + esc(s.myAnswer) + '」（全員そろうまで変更できます）</div>';
    h += '<div class="cap center">回答済み ' + s.answered.length + ' / ' + v.players.length + '人</div>';
    h += hostOnly(v, '', '<button class="big sec" data-act="send" data-arg=\'{"t":"closeVote"}\'>締め切って答え合わせ</button>');
    return h;
  }

  function syncReveal(v, s) {
    let h = '<div class="row">' + img('icon_sync', 48) + '<h2>答え合わせ</h2></div><div class="panel"><h2 class="center">' + esc(s.prompt) + '</h2></div>';
    h += s.groups.map(function (g) {
      const m = g.ids.length >= 2;
      return '<div class="panel row"><div class="grow"><h2 style="color:' + (m ? 'var(--lantern)' : 'var(--muted)') + '">' + esc(g.answer) + '</h2><div class="cap">' + esc(g.ids.map(nameOf).join('、')) + '</div></div><b style="color:' + (m ? 'var(--mint)' : 'var(--muted)') + '">' + (m ? g.ids.length + '人シンクロ！' : 'ひとり') + '</b></div>';
    }).join('');
    const no = v.players.filter(function (p) { return !s.groups.some(function (g) { return g.ids.indexOf(p.id) >= 0; }); });
    if (no.length) h += '<div class="cap">未回答：' + esc(no.map(function (p) { return p.name; }).join('、')) + '</div>';
    return h + scoreboard(s.scores, s.gained) + nextButtons(v, '次のお題へ');
  }

  // ---------- 質問で当てろ ----------

  function guessPlay(v, s) {
    const iAsk = s.asker === v.me;
    let h = timerHeader('icon_guess', '質問で当てろ', 30000);
    if (iAsk) h += '<div class="panel"><div class="cap">あなたが出題者。答えは</div><div class="word">' + esc(s.answer) + '</div></div><div class="cap">みんなの質問に「はい」「いいえ」「わからない」だけで答えてください。当てられたら、当てた人を押します。</div>';
    else h += '<div class="panel"><div class="cap">出題者</div><div class="word s">' + esc(nameOf(s.asker)) + 'さん</div></div><div class="cap">「はい」「いいえ」で答えられる質問をして、出題者の答えを当てよう。例：「それは食べられますか？」</div>';
    if (iAsk || v.isHost) {
      h += '<h3>当てた人</h3><div class="choices">' + v.players.filter(function (p) { return p.id !== s.asker; }).map(function (p) {
        return '<button class="choice" data-act="send" data-arg=\'' + esc(JSON.stringify({ t: 'guessSolved', guesser: p.id })) + '\'>' + esc(p.name) + 'さんが当てた！</button>';
      }).join('') + '</div><button class="big sec" data-act="send" data-arg=\'{"t":"guessGiveUp"}\'>降参（答えを発表）</button>';
    } else h += '<div class="cap center">当てたら、出題者が「当てた」を押します</div>';
    return h;
  }

  function guessResult(v, s) {
    let h = img(s.guesser ? 'char_villagers' : 'char_wolf_grin', 160, 'art') + '<div class="word" style="color:' + (s.guesser ? 'var(--mint)' : 'var(--berry)') + '">' + (s.guesser ? esc(nameOf(s.guesser)) + 'さんが正解！' : '時間切れ！') + '</div>';
    h += '<div class="panel"><div class="cap">答え</div><h2 style="color:var(--lantern)">' + esc(s.answer) + '</h2><div class="cap">出題者：' + esc(nameOf(s.asker)) + 'さん' + (s.guesser ? '（当てた人と出題者に1点ずつ）' : '') + '</div></div>';
    return h + scoreboard(s.scores) + nextButtons(v, '次の出題者へ');
  }

  // ---------- ぶっちゃけ質問 ----------

  function qVote(v, s) {
    let h = '<div class="row">' + img('icon_question', 48) + '<h2>ぶっちゃけ質問</h2></div><div class="panel"><div class="word s">' + esc(s.question) + '</div></div>';
    h += '<div class="cap">当てはまると思う人に投票（自分もOK）。だれが誰に入れたかは表示されません。</div><div class="choices">' + v.players.map(function (p) {
      return '<button class="choice' + (s.myVote === p.id ? ' on' : '') + '" data-act="send" data-arg=\'' + esc(JSON.stringify({ t: 'qVote', target: p.id })) + '\'>' + (s.myVote === p.id ? '✓ ' : '') + esc(p.name) + (p.id === v.me ? '（あなた）' : '') + '</button>';
    }).join('') + '</div><div class="cap center">投票済み ' + s.voted.length + ' / ' + v.players.length + '人</div>';
    return h + hostOnly(v, '', '<button class="big sec" data-act="send" data-arg=\'{"t":"closeVote"}\'>締め切って発表</button>');
  }

  function qReveal(v, s) {
    const top = s.tally.length ? s.tally[0].votes : 0;
    const win = s.tally.filter(function (t) { return t.votes === top && top > 0; });
    let h = '<div class="row">' + img('icon_question', 48) + '<h2>発表</h2></div><div class="panel"><h2 class="center">' + esc(s.question) + '</h2></div>' + img('char_wolf_hello', 140, 'art');
    h += '<div class="word">' + (win.length ? esc(win.map(function (t) { return nameOf(t.id); }).join('、')) + 'さん！' : '投票なし') + '</div>';
    if (s.tally.length) h += '<div class="panel">' + s.tally.map(function (t) { return '<div class="row"><span class="grow">' + esc(nameOf(t.id)) + '</span><b style="color:var(--lantern)">' + t.votes + '票</b></div>'; }).join('') + '</div>';
    return h + nextButtons(v, '次の質問へ');
  }

  // ---------- なんでもアリーナ ----------

  const SUIT_IMG = { SUN: 'suit_sun', MOON: 'suit_moon', FLOWER: 'suit_flower', LEAF: 'suit_leaf' };
  function cardHtml(c, opt) {
    opt = opt || {};
    const d = CARDS[c.id];
    const name = opt.hidden ? (d.coverName || d.name) : d.name;
    const text = opt.hidden ? (d.cover || d.text) : d.text;
    return '<div class="acard ' + c.suit + (opt.small ? ' small' : '') + (opt.sel ? ' sel' : '') + (opt.click ? ' click' : '') + '"' +
      (opt.click ? ' data-act="pick" data-arg="' + c.uid + '"' : '') + ' title="' + esc(name + '：' + text) + '">' +
      '<div class="top">' + img(SUIT_IMG[c.suit], 20) + (d.star ? '<span class="star">★</span>' : '') + '</div>' +
      '<img class="pic" src="img/card_' + d.art + '.webp" alt=""><div class="nm">' + esc(name) + '</div><div class="tx">' + esc(text) + '</div></div>';
  }

  function arena(v, s) {
    const me = s.seats.find(function (x) { return x.id === v.me; });
    const myTurn = s.turn === v.me && !s.mini;
    const social = s.rules.filter(function (r) { return CARDS[r.id].fx === 'RULE_SOCIAL'; });
    const canReport = social.length > 0 || (s.chaos && META.chaos[s.chaos].social);
    let h = '<div class="row">' + img('icon_arena', 48) + '<h2 class="grow">なんでもアリーナ</h2>' + (!s.mini && v.remainingMs != null ? '<span class="timer" data-timer></span>' : '') + '</div>';
    if (s.chaos) { const c = META.chaos[s.chaos]; h += '<div class="chaos">🌙 <b>今夜のルール：' + esc(c.title) + '</b>　<span class="cap">' + esc(c.text) + '</span></div>'; }
    h += '<div class="seats">' + s.seats.map(function (st) {
      const turn = st.id === s.turn && st.alive;
      return '<div class="seat' + (turn ? ' turn' : '') + (st.alive ? '' : ' out') + '"><div class="nm">' + esc(nameOf(st.id)) + (st.id === v.me ? '（あなた）' : '') + '</div>' +
        '<div class="st">' + (st.alive ? '手札' + st.hand + '枚' + (st.points > 0 ? '・' + st.points + 'pt' : '') : '負け') + '</div>' +
        (st.front.length ? '<div class="fr">' + st.front.map(function (c) { return '<img src="img/card_' + CARDS[c.id].art + '.webp" title="' + esc(CARDS[c.id].name) + '" alt="">'; }).join('') + '</div>' : '') +
        (canReport && st.alive && st.id !== v.me ? '<button class="rep" data-act="report" data-arg="' + esc(st.id) + '">違反！</button>' : '') + '</div>';
    }).join('') + '</div>';
    h += '<div class="field"><div class="deck center"><img src="img/card_back.webp" alt=""><div class="cap">' + (s.noDraw ? '引けない！' : '山札 ' + s.deck + '枚') + '</div></div>';
    if (s.discardTop) h += '<div>' + cardHtml(s.discardTop, { small: true, hidden: s.topHidden }) + '<div class="cap center">さっき出た</div></div>';
    s.rules.forEach(function (r) { h += '<div>' + cardHtml(r, { small: true }) + '<div class="cap center">ルール</div></div>'; });
    h += '</div>';
    if (s.rules.length) h += '<div class="cap">ルール：' + s.rules.map(function (r) { const d = CARDS[r.id]; return '「' + esc(d.name) + '」' + esc(d.text.split('。').slice(1).join('。')); }).join('　') + '</div>';
    if (s.endOwner) h += '<div class="warn">🔔 ' + esc(nameOf(s.endOwner)) + 'さんの次の手番が終わると、全員の負け！</div>';
    if (s.pointGoal !== 15) h += '<div class="cap">大金庫が出たので、勝つには' + s.pointGoal + 'ポイント必要</div>';
    if (s.mini) h += miniHtml(v, s, s.mini);

    if (me && !me.alive) h += '<div class="panel"><b class="warn">あなたの負けです</b><div class="cap">最後まで見守りましょう。違反の報告はできます</div></div>';
    else if (myTurn) h += '<h3 style="color:var(--lantern)">あなたの番！ カードを1枚選んで出そう</h3>';
    else if (!s.mini) h += '<div class="cap center">' + esc(nameOf(s.turn)) + 'さんの番です</div>';
    if (s.hand.length) {
      h += '<h3>あなたの手札（' + s.hand.length + '枚）</h3><div class="hand">' + s.hand.map(function (c) { return cardHtml(c, { click: true, sel: S.ui.pick === c.uid }); }).join('') + '</div>';
    }
    const sel = s.hand.find(function (c) { return c.uid === S.ui.pick; });
    if (myTurn && sel) {
      const d = CARDS[sel.id];
      let targets = [];
      if (d.target === 'ALIVE_OTHER') targets = s.seats.filter(function (st) {
        const dino = st.front.some(function (c) { return CARDS[c.id].fx === 'DINO'; });
        return st.alive && st.id !== v.me && !(placesFront(d) && dino);
      }).map(function (st) { return st.id; });
      if (targets.length) h += '<h3>だれに？</h3><div class="row">' + targets.map(function (t) { return '<button class="chip' + (S.ui.target === t ? ' on' : '') + '" data-act="target" data-arg="' + esc(t) + '">' + esc(nameOf(t)) + '</button>'; }).join('') + '</div>';
      else if (d.target !== 'NONE') h += '<div class="cap">指名できる人がいないので、出しても何も起きません</div>';
      h += '<button class="big" data-act="play"' + (targets.length && !S.ui.target ? ' disabled' : '') + '>「' + esc(d.name) + '」を出す</button>';
    }
    h += '<div class="panel log"><h3>できごと</h3>' + s.log.slice(-6).reverse().map(function (l) { return '<div>' + esc(l) + '</div>'; }).join('') + '</div>';
    return h;
  }
  function placesFront(d) { return ['TRAP', 'POINTS', 'LAUGH', 'BOMB', 'DINO'].indexOf(d.fx) >= 0; }

  function miniHtml(v, s, m) {
    const inIt = m.involved.indexOf(v.me) >= 0, done = m.myInput != null, owner = v.me === m.owner;
    const d = m.card ? CARDS[m.card] : null;
    let h = '<div class="mini">' + (d ? '<img src="img/card_' + d.art + '.webp" width="70" height="70" alt="">' : '');
    let q;
    if (m.kind === 'cancel') q = '「' + esc(d ? (s.topHidden ? (d.coverName || d.name) : d.name) : '') + '」が出された！';
    else q = esc(owner || !d || !d.cover ? (d ? d.text : '') : d.cover);
    h += '<div class="q">' + q + '</div>';
    function act(val) { return 'data-act="input" data-arg="' + esc(val) + '"'; }
    switch (m.kind) {
      case 'cancel':
        h += inIt && !done ? '<div class="cap">あなたはネコを持っています。横やりで打ち消しますか？</div><div class="row"><button class="big berry" style="width:200px" ' + act('use') + '>横やり！</button><button class="big sec" style="width:200px" ' + act('pass') + '>見送る</button></div>'
          : '<div class="cap">…ちょっと待って。横やりが入るかも？</div>'; break;
      case 'yesno':
        h += owner && !done ? '<b>今月はあなたの誕生月？</b><div class="row"><button class="big" style="width:160px" ' + act('yes') + '>はい</button><button class="big sec" style="width:160px" ' + act('no') + '>いいえ</button></div>'
          : '<div class="cap">' + esc(nameOf(m.owner)) + 'さんが答えています…</div>'; break;
      case 'rps':
        h += inIt && !done ? '<div class="rps">' + [['g', '✊', 'グー'], ['c', '✌', 'チョキ'], ['p', '✋', 'パー']].map(function (x) { return '<button ' + act(x[0]) + '><span>' + x[1] + '</span>' + x[2] + '</button>'; }).join('') + '</div>'
          : '<div class="cap">' + (inIt ? '出しました。みんなを待っています…' : 'じゃんけん中…') + '</div>'; break;
      case 'fingers':
        h += inIt && !done ? '<div class="nums">' + [1, 2, 3, 4, 5].map(function (n) { return '<button ' + act(String(n)) + '>' + n + '</button>'; }).join('') + '</div><div class="cap">キーボードの 1〜5 でもOK</div>'
          : '<div class="cap">' + (inIt ? m.myInput + '本を出しました。みんなを待っています…' : 'みんなが選んでいます…') + '</div>'; break;
      case 'vote':
        h += inIt && !done ? '<div class="row">' + s.seats.filter(function (st) { return st.alive; }).map(function (st) { return '<button class="chip" ' + act(st.id) + '>' + esc(nameOf(st.id)) + (st.id === v.me ? '（自分）' : '') + '</button>'; }).join('') + '</div>'
          : '<div class="cap">' + (inIt ? esc(nameOf(m.myInput)) + 'さんに投票しました' : '投票中…') + '</div>'; break;
      case 'present':
        h += inIt && !done ? '<div class="row"><button class="big" style="width:180px" ' + act('want') + '>ほしい！</button><button class="big sec" style="width:180px" ' + act('no') + '>いらない</button></div>'
          : '<div class="cap">' + (inIt ? '「' + (m.myInput === 'want' ? 'ほしい' : 'いらない') + '」と答えました' : 'みんなの答えを待っています…') + '</div>'; break;
      case 'nose': {
        const go = m.goInMs != null;
        if (go && !S.ui.goSeenAt) S.ui.goSeenAt = S.receivedAt;
        if (owner && !go) h += '<div class="cap">好きなタイミングで合図しよう。合図は全員の画面に出ます</div><button class="push" ' + act('go') + '>合図！</button>';
        else if (owner) h += '<div class="cap">合図しました！ みんなの反応を待っています…</div>';
        else if (!go) h += '<h2 style="color:var(--muted)">' + esc(nameOf(m.owner)) + 'さんの合図を待て…</h2>';
        else if (done) h += '<button class="push done" disabled>✓</button>';
        else h += '<h2 style="color:var(--berry)">押せ！！</h2><button class="push" data-act="nose">PUSH</button><div class="cap" data-nose></div><div class="cap">スペースキーでもOK</div>';
        break;
      }
    }
    if (m.kind !== 'nose' && S.view.remainingMs != null) h += '<div class="cap">残り <span data-timer></span>　答えた人 ' + m.done.length + ' / ' + m.involved.length + '人</div>';
    return h + '</div>';
  }

  function arenaResult(v, s) {
    let h = '<img src="img/arena_hero.webp" style="width:100%;height:220px;object-fit:cover;border-radius:20px" alt="">';
    h += '<div class="word">' + (s.winners.length ? esc(s.winners.map(nameOf).join('、')) + 'の勝ち！' : '勝者なし！') + '</div>';
    h += '<div class="panel"><h3>最終結果</h3>' + s.seats.slice().sort(function (a, b) { return (s.winners.indexOf(b.id) >= 0) - (s.winners.indexOf(a.id) >= 0); }).map(function (st) {
      const w = s.winners.indexOf(st.id) >= 0;
      return '<div class="row"><span class="grow">' + esc(nameOf(st.id)) + '</span><b style="color:' + (w ? 'var(--lantern)' : st.alive ? 'var(--mint)' : 'var(--muted)') + '">' + (w ? '👑 優勝' : st.alive ? '生き残り' : '負け') + '</b></div>';
    }).join('') + '</div>';
    h += '<div class="panel log"><h3>最後のできごと</h3>' + s.log.slice(-6).map(function (l) { return '<div>' + esc(l) + '</div>'; }).join('') + '</div>';
    return h + nextButtons(v, 'もう一戦');
  }

  // ================= 操作 =================

  document.addEventListener('input', function (e) {
    if (e.target.id === 'nameIn') {
      S.name = e.target.value.slice(0, 10); save('pg.name', S.name);
      document.querySelectorAll('[data-act=host],[data-act=toJoin]').forEach(function (b) { b.disabled = !S.name.trim(); });
    }
    if (e.target.id === 'codeIn') { e.target.value = e.target.value.replace(/\D/g, '').slice(0, 6); S.code = e.target.value; }
    if (e.target.id === 'syncIn') S.ui.draft = e.target.value.slice(0, 20);
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && e.target.id === 'codeIn') joinRoom(e.target.value);
    if (e.key === 'Enter' && e.target.id === 'syncIn') doAct('syncSend');
    const v = S.view; if (!v || v.screen.t !== 'arena' || !v.screen.mini || e.target.tagName === 'INPUT') return;
    const m = v.screen.mini;
    if (m.kind === 'fingers' && /^[1-5]$/.test(e.key) && m.involved.indexOf(v.me) >= 0 && m.myInput == null) send({ t: 'arenaInput', value: e.key });
    if (m.kind === 'nose' && e.code === 'Space') { e.preventDefault(); if (m.owner === v.me && m.goInMs == null) send({ t: 'arenaInput', value: 'go' }); else doAct('nose'); }
  });
  document.addEventListener('click', function (e) {
    const el = e.target.closest('[data-act]'); if (!el || el.disabled) return;
    if (el.dataset.act === 'nose') return;   // 指が触れた時点（pointerdown）で処理ずみ
    doAct(el.dataset.act, el.dataset.arg);
  });

  function doAct(act, arg) {
    const v = S.view;
    switch (act) {
      case 'host': if (S.name.trim()) hostRoom(); break;
      case 'toJoin': if (S.name.trim()) { S.page = 'join'; S.busy = false; render(); setTimeout(function () { const c = document.getElementById('codeIn'); if (c) c.focus(); }, 0); } break;
      case 'join': joinRoom(document.getElementById('codeIn').value); break;
      case 'back': resetNet(); S.page = 'title'; S.busy = false; render(); break;
      case 'leave': confirmBox(v && v.isHost ? '部屋を閉じますか？' : '部屋を出ますか？', v && v.isHost ? '全員の接続が切れます' : '', '出る', leave); break;
      case 'share': {
        const url = /^https?:/.test(location.protocol) ? location.href.split('#')[0] : '';
        navigator.share({ title: 'パーティーゲーム', text: 'パーティーゲームで遊ぼう！ 「部屋に参加する」で部屋コード ' + v.roomCode + ' を入れてね', url: url || undefined }).catch(function () {});
        break;
      }
      case 'copy': try { navigator.clipboard.writeText(v.roomCode); toast('コピーしました'); } catch (err) { toast(v.roomCode); } break;
      case 'kick': send({ t: 'kick', target: arg }); break;
      case 'game': send({ t: 'settings', settings: Object.assign({}, v.settings, { game: arg }) }); break;
      case 'set': send({ t: 'settings', settings: Object.assign({}, v.settings, JSON.parse(arg)) }); break;
      case 'start': send({ t: 'start' }); break;
      case 'next': send({ t: 'next' }); break;
      case 'lobby': send({ t: 'lobby' }); break;
      case 'send': send(JSON.parse(arg)); break;
      case 'peek': S.ui.peek = !S.ui.peek; render(); break;
      case 'ngOut': {
        const e2 = v.screen.entries.find(function (x) { return x.id === arg; });
        confirmBox(nameOf(arg) + 'さんがアウト？', '「' + (e2 && e2.word) + '」と言いましたか？', '言った！', function () { send({ t: 'out', target: arg }); });
        break;
      }
      case 'syncSend': { const t = (S.ui.draft || '').trim(); if (t) send({ t: 'syncAnswer', text: t }); break; }
      case 'pick': { const uid = Number(arg); S.ui.pick = S.ui.pick === uid ? null : uid; S.ui.target = null; render(); break; }
      case 'target': S.ui.target = arg; render(); break;
      case 'play': if (S.ui.pick != null) { send({ t: 'arenaPlay', uid: S.ui.pick, target: S.ui.target || null }); S.ui.pick = null; S.ui.target = null; } break;
      case 'input': send({ t: 'arenaInput', value: arg }); break;
      case 'nose': {
        const m = v && v.screen.mini;
        if (m && m.kind === 'nose' && m.goInMs != null && m.myInput == null && m.owner !== v.me) send({ t: 'arenaInput', value: 'press:' + Math.round(performance.now() - S.ui.goSeenAt) });
        break;
      }
      case 'report': {
        const s = v.screen;
        const names = s.rules.filter(function (r) { return CARDS[r.id].fx === 'RULE_SOCIAL'; }).map(function (r) { return '「' + CARDS[r.id].name + '」'; });
        if (s.chaos && META.chaos[s.chaos].social) names.push('今夜のルール「' + META.chaos[s.chaos].title + '」');
        confirmBox(nameOf(arg) + 'さんがルール違反？', names.join('・') + 'を破りましたか？ 報告するとその人は負けになります（お守りがあれば守られます）', '違反！', function () { send({ t: 'arenaReport', target: arg }); });
        break;
      }
    }
  }

  // 新しい手番になったら選んでいたカードを忘れる
  const origSetView = setView;
  setView = function (v) {
    const prev = S.view;
    if (prev && prev.screen.t === 'arena' && v.screen.t === 'arena') {
      if (prev.screen.turn !== v.screen.turn || !v.screen.hand.some(function (c) { return c.uid === S.ui.pick; })) { S.ui.pick = null; S.ui.target = null; }
      const pm = prev.screen.mini, nm = v.screen.mini;
      if (!nm || !pm || pm.card !== nm.card || pm.owner !== nm.owner || nm.goInMs == null) S.ui.goSeenAt = 0;
    }
    origSetView(v);
  };

  window.addEventListener('beforeunload', function () { if (S.host && S.connected.size) PgNet.send(Array.from(S.connected), JSON.stringify({ t: 'bye', reason: 'ホストが部屋を閉じました' })); });
  // 遊んでいる間は画面を消さない（iPhone は iOS 16.4 以降）
  let wake = null;
  function keepAwake() {
    if (!('wakeLock' in navigator) || document.visibilityState !== 'visible') return;
    if (S.page === 'room' && !wake) navigator.wakeLock.request('screen').then(function (w) { wake = w; w.addEventListener('release', function () { wake = null; }); }).catch(function () {});
    if (S.page !== 'room' && wake) { wake.release().catch(function () {}); wake = null; }
  }
  setInterval(keepAwake, 3000);
  document.addEventListener('visibilitychange', keepAwake);
  // 鼻タッチは指が触れた瞬間に反応させる（タップの遅れをなくす）
  document.addEventListener('pointerdown', function (e) {
    const el = e.target.closest('[data-act=nose]'); if (!el) return;
    e.preventDefault(); doAct('nose');
  });
  // ホーム画面に追加したときにオフラインでも開けるよう、ファイルを端末に保存する（サイトに置いたときだけ）
  if ('serviceWorker' in navigator && /^https?:/.test(location.protocol)) navigator.serviceWorker.register('sw.js').catch(function () {});

  // テスト用の入口
  window.PG = { S: S, send: send, doAct: doAct, render: render };
  render();
})();
