/*
 * わちゃパ（パーティーゲーム）PC・ウェブ版。
 * スマホのアプリと同じ部屋に「オンライン」で入れる。ゲームの進行（Engine）はアプリと同じ Kotlin のコードを
 * JavaScript にしたもの（lib/partygame.js）で、PC が部屋を作ったときはこの中で動く。
 */
(function () {
  'use strict';
  // ゲームの進行（Kotlin → JavaScript）。読み込むと window['PartyGame:shared'] に入る
  const G = (window['PartyGame:shared'] || window.partygame).com.zen.partygame.game;
  const META = JSON.parse(G.metaJson());
  const PROTO = META.proto;
  // ---------- ことば（日本語の文をキーに、選んだ言語の訳を引く） ----------
  const LANGS = [['ja', '日本語'], ['en', 'English'], ['ko', '한국어'], ['zh', '简体中文']];
  const DICTS = window.PG_I18N || {};
  function pickLang() {
    const saved = load('pg.lang', '');
    if (saved && LANGS.some(function (l) { return l[0] === saved; })) return saved;
    const nav = (navigator.language || 'ja').toLowerCase();
    return nav.startsWith('ja') ? 'ja' : nav.startsWith('ko') ? 'ko' : nav.startsWith('zh') ? 'zh' : 'en';
  }
  let LANG = pickLang();
  function T(key) {
    const s = (LANG !== 'ja' && DICTS[LANG] && DICTS[LANG][key]) || key;
    const args = arguments;
    return args.length < 2 ? s : s.replace(/\{(\d+)\}/g, function (m, n) { const a = args[Number(n) + 1]; return a === undefined ? m : (a == null ? '' : String(a)); });
  }
  function fmtLog(line) { return G.formatLog(line, function (k) { return T(k); }, T('、')); }
  function afterFirst(text) { const i = text.indexOf('。'); if (i >= 0) return text.slice(i + 1); const j = text.indexOf('. '); return j >= 0 ? text.slice(j + 2) : text; }
  const CARDS_JA = JSON.parse(G.arenaCardsJson());
  const CHAOS_JA = JSON.parse(JSON.stringify(META.chaos));
  const GAMES_JA = META.games.map(function (g) { return Object.assign({}, g); });
  const CARDS = {}, GAMES = {};
  /** カード・今夜のルール・ゲーム名を今の言語にする */
  function localize() {
    CARDS_JA.forEach(function (c) {
      CARDS[c.id] = Object.assign({}, c, { name: T(c.name), text: T(c.text), cover: c.cover && T(c.cover), coverName: c.coverName && T(c.coverName) });
    });
    Object.keys(CHAOS_JA).forEach(function (k) { const c = CHAOS_JA[k]; META.chaos[k] = Object.assign({}, c, { title: T(c.title), text: T(c.text) }); });
    GAMES_JA.forEach(function (g, i) { META.games[i] = Object.assign({}, g, { label: T(g.label), short: T(g.short) }); GAMES[g.id] = META.games[i]; });
    document.documentElement.lang = LANG === 'zh' ? 'zh-CN' : LANG;
    document.title = T('わちゃパ｜ワードウルフ・NGワードほかパーティーゲーム');
  }
  localize();
  // ---------- 効果音（sfx/*.mp3。最初にどこかを触ったときに音を出せるようにする） ----------
  const SND = { on: load('pg.sound', '1') === '1', ctx: null, buf: {}, last: {} };
  const SND_FILE = location.protocol === 'file:';   // ファイルを直接開いたときは fetch が使えないので <audio> で鳴らす
  function sndInit() {
    if (SND_FILE || SND.ctx || !(window.AudioContext || window.webkitAudioContext)) return;
    try { SND.ctx = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { return; }
    ['tap', 'card_play', 'card_draw', 'your_turn', 'tick', 'tick_last', 'buzzer', 'win', 'lose', 'reveal', 'page', 'sent', 'join', 'bomb', 'alert', 'sync', 'start', 'vote', 'out'].forEach(function (n) {
      fetch('sfx/' + n + '.mp3').then(function (r) { return r.arrayBuffer(); })
        .then(function (a) { return new Promise(function (ok, ng) { SND.ctx.decodeAudioData(a, ok, ng); }); })
        .then(function (b) { SND.buf[n] = b; }).catch(function () {});
    });
  }
  function sfx(name) {
    if (window.__sfxLog && SND.on) window.__sfxLog.push(name);   // テスト用
    if (!SND.on) return;
    const now = performance.now(); if (now - (SND.last[name] || 0) < 60) return; SND.last[name] = now;
    if (SND_FILE) { try { new Audio('sfx/' + name + '.mp3').play().catch(function () {}); } catch (e) {} return; }
    if (!SND.ctx || !SND.buf[name]) return;
    try {
      if (SND.ctx.state === 'suspended') SND.ctx.resume();
      const src = SND.ctx.createBufferSource(); src.buffer = SND.buf[name]; src.connect(SND.ctx.destination); src.start();
    } catch (e) {}
  }
  function setSound(on) { SND.on = on; save('pg.sound', on ? '1' : '0'); if (on) { sndInit(); setTimeout(function () { sfx('tap'); }, 50); } render(); }
  // ---------- BGM（bgm/*.mp3。場面ごとの曲は G.bgmTrack が決める。切れ目なくくり返し、曲が変わるときはクロスフェード） ----------
  // mp3 は頭に無音が入るので、曲の頭1秒を後ろにもう一度つけてある → loopStart（0.1〜0.9秒）から loopStart+曲の長さ までをくり返す
  const MUS = { on: load('pg.music', '1') === '1', want: '', cur: null, buf: {}, ls: {}, loading: {}, loops: window.PG_BGM_LOOPS || null, out: null, vol: 0.42, ducked: false };
  const MUS_FADE = 0.7;
  function musOut() {
    if (!SND.ctx) return null;
    if (!MUS.out) { MUS.out = SND.ctx.createGain(); MUS.out.gain.value = MUS.ducked ? 0 : 1; MUS.out.connect(SND.ctx.destination); }
    return MUS.out;
  }
  function musLoad(id) {
    if (MUS.buf[id] || MUS.loading[id] || !SND.ctx) return;
    MUS.loading[id] = true;
    fetch('bgm/' + id + '.mp3').then(function (r) { return r.arrayBuffer(); })
      .then(function (a) { return new Promise(function (ok, ng) { SND.ctx.decodeAudioData(a, ok, ng); }); })
      .then(function (b) { MUS.buf[id] = b; MUS.ls[id] = musLoopStart(b, id); MUS.loading[id] = false; musSync(); })
      .catch(function () { MUS.loading[id] = false; });
  }
  // つなぎ目の位置：0.1〜0.9秒の中で、1周あとの音といちばん同じになる所（mp3 の圧縮のずれが一番小さい所）を選ぶ
  function musLoopStart(b, id) {
    const L = Math.round(((MUS.loops && MUS.loops[id]) || (b.duration - 1)) * b.sampleRate);
    const chs = []; for (let c = 0; c < b.numberOfChannels; c++) chs.push(b.getChannelData(c));
    let best = 0.1 * b.sampleRate, bestE = Infinity;
    for (let s0 = Math.round(0.1 * b.sampleRate); s0 < 0.9 * b.sampleRate && s0 + L + 16 < b.length; s0 += 64) {
      let e = 0;
      for (let c = 0; c < chs.length; c++) { const x = chs[c]; for (let k = -16; k < 16; k++) { const d = x[s0 + k] - x[s0 + L + k]; e += d * d; } }
      if (e < bestE) { bestE = e; best = s0; }
    }
    return best / b.sampleRate;
  }
  function musStop(p, fade) {
    if (!p) return;
    try {
      if (p.el) { musFadeEl(p, 0, fade, true); return; }
      const t = SND.ctx.currentTime; p.gain.gain.cancelScheduledValues(t); p.gain.gain.setValueAtTime(p.gain.gain.value, t);
      p.gain.gain.linearRampToValueAtTime(0, t + fade); p.src.stop(t + fade + 0.05);
    } catch (e) {}
  }
  // ファイルを直接開いたとき（PC版）は <audio> で流し、ループの位置は少しずつ見張って戻す
  function musFadeEl(p, to, sec, end) {
    clearInterval(p.fade); const from = p.el.volume, t0 = performance.now();
    p.fade = setInterval(function () {
      const k = Math.min(1, (performance.now() - t0) / (sec * 1000)); p.el.volume = Math.max(0, Math.min(1, from + (to - from) * k));
      if (k >= 1) { clearInterval(p.fade); if (end) { p.el.pause(); clearInterval(p.watch); p.el.src = ''; } }
    }, 30);
  }
  function musSync() {
    if (window.__bgmLog) window.__bgmLog.push(MUS.on ? MUS.want : '');   // テスト用
    const want = MUS.on ? MUS.want : '';
    if (MUS.cur && MUS.cur.id === want) return;
    if (!want) { musStop(MUS.cur, 0.25); MUS.cur = null; return; }
    if (SND_FILE) {
      const len = (MUS.loops && MUS.loops[want]) || 0;
      const el = new Audio('bgm/' + want + '.mp3'); el.volume = 0;
      const p = { id: want, el: el };
      el.addEventListener('loadedmetadata', function () { try { el.currentTime = 0.1; } catch (e) {} });
      p.watch = setInterval(function () { if (len && el.currentTime >= 0.1 + len) el.currentTime -= len; }, 20);
      el.play().then(function () { musFadeEl(p, MUS.vol, MUS_FADE); }).catch(function () { clearInterval(p.watch); if (MUS.cur === p) MUS.cur = null; });
      musStop(MUS.cur, MUS_FADE); MUS.cur = p; return;
    }
    const out = musOut(); if (!out) return;
    const b = MUS.buf[want]; if (!b) { musLoad(want); return; }
    try {
      if (SND.ctx.state === 'suspended') SND.ctx.resume();
      const len = (MUS.loops && MUS.loops[want]) || (b.duration - 1.0);
      const ls = MUS.ls[want] || 0.1;
      const src = SND.ctx.createBufferSource(); src.buffer = b; src.loop = true; src.loopStart = ls; src.loopEnd = Math.min(b.duration, ls + len);
      const gain = SND.ctx.createGain(); const t = SND.ctx.currentTime;
      gain.gain.setValueAtTime(0, t); gain.gain.linearRampToValueAtTime(MUS.vol, t + MUS_FADE);
      src.connect(gain); gain.connect(out); src.start(t, 0.1);
      musStop(MUS.cur, MUS_FADE); MUS.cur = { id: want, src: src, gain: gain };
    } catch (e) {}
  }
  function bgm(id) { if (MUS.want === id) return; MUS.want = id; musSync(); }
  // 全画面広告の間などは、BGM を一時的に消す
  function musDuck(on) {
    MUS.ducked = on;
    if (MUS.cur && MUS.cur.el) { musFadeEl(MUS.cur, on ? 0 : MUS.vol, 0.3); return; }
    if (!MUS.out) return;
    try { const t = SND.ctx.currentTime; MUS.out.gain.cancelScheduledValues(t); MUS.out.gain.setValueAtTime(MUS.out.gain.value, t); MUS.out.gain.linearRampToValueAtTime(on ? 0 : 1, t + 0.3); } catch (e) {}
  }
  function setMusic(on) { MUS.on = on; save('pg.music', on ? '1' : '0'); if (on) sndInit(); musSync(); render(); }
  document.addEventListener('visibilitychange', function () {
    if (SND_FILE) { if (MUS.cur && MUS.cur.el) { if (document.hidden) MUS.cur.el.pause(); else MUS.cur.el.play().catch(function () {}); } return; }
    if (!SND.ctx) return;
    if (document.hidden) SND.ctx.suspend().catch(function () {}); else SND.ctx.resume().catch(function () {});
  });
  ['pointerdown', 'keydown'].forEach(function (ev) {
    document.addEventListener(ev, function () { sndInit(); if (SND.ctx && SND.ctx.state === 'suspended') SND.ctx.resume(); if (!MUS.cur) musSync(); }, { capture: true, passive: true });
  });

  function setLang(code) { LANG = code; save('pg.lang', code); localize(); render(); }
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
    // 管理者・荒らし対策（ホスト側）
    admins: new Map(), pendingAdmins: new Set(), devices: new Map(), pendingDevices: new Map(), sessionBans: new Set(),
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

  // ================= 荒らし対策（端末番号・参加禁止リスト・管理者の署名） =================

  const ADM = JSON.parse(G.adminConfigJson());
  const DID = (function () {
    let d = load('pg.did', '');
    if (!d) { d = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : String(Math.random()).slice(2) + String(Date.now()); save('pg.did', d); }
    return d;
  })();
  let BANS = { bans: [] }, bansAt = 0, MY_DEVICE = '';
  function sha256hex(text) {
    if (!(window.crypto && crypto.subtle) || !text) return Promise.resolve('');
    return crypto.subtle.digest('SHA-256', new TextEncoder().encode(text)).then(function (b) {
      return Array.from(new Uint8Array(b)).map(function (x) { return x.toString(16).padStart(2, '0'); }).join('');
    }).catch(function () { return ''; });
  }
  sha256hex(DID).then(function (h) { MY_DEVICE = h; });
  function refreshBans(force) {
    if (!force && Date.now() - bansAt < 5 * 60000) return Promise.resolve();
    bansAt = Date.now();
    return fetch(ADM.bansUrl + '?t=' + Date.now(), { cache: 'no-store' }).then(function (r) { return r.ok ? r.json() : null; })
      .then(function (j) { if (j && Array.isArray(j.bans)) BANS = j; }).catch(function () {});
  }
  refreshBans(true);
  function isBanned(h) { return !!h && (S.sessionBans.has(h) || BANS.bans.some(function (b) { return b.h === h; })); }
  function b64bytes(b64) { const s = atob(b64); const u = new Uint8Array(s.length); for (let i = 0; i < s.length; i++) u[i] = s.charCodeAt(i); return u; }
  /** 管理者の署名を確かめる（ECDSA P-256 / SHA-256） */
  function verifyAdmin(code, peerId, a) {
    if (!a || !(window.crypto && crypto.subtle) || Math.abs(Date.now() - a.ts) > ADM.windowMs) return Promise.resolve(false);
    return crypto.subtle.importKey('spki', b64bytes(ADM.publicKey), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify'])
      .then(function (key) { return crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, key, b64bytes(a.sig), new TextEncoder().encode(G.adminMessage(code, peerId, a.ts, a.nonce))); })
      .catch(function () { return false; });
  }
  function sendAdminView(id) {
    const h = S.host; if (!h) return;
    const dev = {}; S.devices.forEach(function (v, k) { dev[k] = v; });
    PgNet.send([id], h.adminViewMsg(S.admins.get(id) || '', JSON.stringify(dev), JSON.stringify(Array.from(S.sessionBans))));
  }
  function notice(text, args) {
    const json = JSON.stringify({ t: 'notice', text: text, args: args || [] });
    PgNet.send(Array.from(S.connected), json);
    toast(T.apply(null, [text].concat(args || [])));
  }
  function handleAdmin(from, op) {
    const h = S.host; if (!h || !op || op.t !== 'adminOp') return;
    switch (op.op) {
      case 'kick': case 'ban': {
        const t = op.target;
        if (op.op === 'ban' && S.devices.get(t)) S.sessionBans.add(S.devices.get(t));
        if (t === HOST_ID) { closeByAdmin(); return; }
        const who = nameOf(t); if (h.playerIds().indexOf(t) < 0) break;
        if (S.connected.delete(t)) {
          PgNet.send([t], JSON.stringify({ t: 'bye', reason: T('管理者により退出させられました') }));
          setTimeout(function () { PgNet.disconnect(t); }, 500);
        }
        h.leave(t);
        notice('🛡 {0}さんは管理者により退出させられました', [who]);
        publish(true);
        break;
      }
      case 'close': closeByAdmin(); return;
      case 'lobby': if (h.adminLobby()) publish(); break;
      case 'next': if (h.adminSkip()) publish(); break;
      case 'notice': { const t = String(op.text || '').trim().replace(/\n/g, ' ').slice(0, 100); if (t) notice('🛡 管理者：{0}', [t]); break; }
      case 'watch': S.admins.set(from, String(op.target || '')); break;
      case 'unban': S.sessionBans.delete(String(op.target || '')); break;
    }
    if (S.admins.has(from)) sendAdminView(from);
  }
  function closeByAdmin() {
    const all = Array.from(S.connected).concat(Array.from(S.admins.keys()));
    PgNet.send(all, JSON.stringify({ t: 'bye', reason: T('管理者が部屋を閉じました') }));
    setTimeout(function () { resetNet(); }, 300);
    S.host = null; S.view = null; S.page = 'title'; S.busy = false;
    closeModal(); render(); toast(T('管理者が部屋を閉じました'));
  }

  // ================= 通信 =================

  function send(intent) {
    const json = JSON.stringify(intent);
    if (S.host) { if (S.host.apply(HOST_ID, json)) publish(); }
    else PgNet.send(['host'], json);
  }

  function hostRoom() {
    if (MY_DEVICE && BANS.bans.some(function (b) { return b.h === MY_DEVICE; })) { toast(T('この端末はオンラインの部屋を作れません')); return; }
    resetNet();
    const code = String(100000 + Math.floor(Math.random() * 900000));
    S.host = new G.JsHost(code, (window.TOPICS_BY_LANG || {})[LANG] || window.TOPICS_JSON);
    S.host.join(HOST_ID, S.name, true);
    S.host.preferArena();
    PgNet.host(PROTO, code, S.name);
    S.devices.set(HOST_ID, MY_DEVICE);
    S.tickTimer = setInterval(function () { if (S.host && S.host.tick()) publish(); refreshBans(false); }, 250);
    S.page = 'room'; publish(true);
  }

  function joinRoom(code) {
    code = String(code).replace(/\D/g, '');
    if (code.length !== 6) { toast(T('部屋コードは6けたの数字です')); return; }
    S.code = code; save('pg.code', code);
    resetNet(); S.busy = true; S.page = 'join'; render();
    PgNet.join(PROTO, code, S.name || T('ゲスト'), { did: DID });
  }

  function leave() {
    if (S.host && S.connected.size) PgNet.send(Array.from(S.connected), JSON.stringify({ t: 'bye', reason: T('ホストが部屋を閉じました') }));
    const wasHost = !!S.host;
    setTimeout(function () { resetNet(); }, wasHost ? 300 : 0);
    if (!wasHost) resetNet();
    S.host = null; S.view = null; S.page = 'title'; S.busy = false;
    closeModal(); render();
  }

  function resetNet() {
    S.lastViewJson = ''; S.countsDown = false;
    if (S.tickTimer) clearInterval(S.tickTimer); S.tickTimer = null;
    PgNet.stop();
    S.connected.clear(); S.pendingNames.clear(); S.lastVersion = -1;
    S.admins.clear(); S.pendingAdmins.clear(); S.devices.clear(); S.pendingDevices.clear(); S.sessionBans.clear();
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
        PgNet.send([id], JSON.stringify({ t: 'bye', reason: T('ホストに退出させられました') }));
        setTimeout(function () { PgNet.disconnect(id); }, 500);
      }
    });
    S.admins.forEach(function (_, id) { sendAdminView(id); });
  }

  function setView(v) {
    try {
      const json = JSON.stringify(v);
      if (SND.on) G.sfxCues(S.lastViewJson || '', json).forEach(sfx);
      S.lastViewJson = json; S.countsDown = G.sfxCountsDown(json); S.bgm = G.bgmTrack(json);
    } catch (e) {}
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
      case 'join': {
        if (e.proto !== PROTO) { PgNet.reject(e.id); break; }
        const code = S.view && S.view.roomCode;
        Promise.all([e.admin ? verifyAdmin(code, e.id, e.admin) : Promise.resolve(false), sha256hex(e.did)]).then(function (r) {
          if (S.host !== h) return;
          if (r[0]) { S.pendingAdmins.add(e.id); PgNet.accept(e.id); return; }        // 管理者：ゲーム中・満員でも入れる
          if (e.admin) { PgNet.reject(e.id); return; }                                   // 署名がおかしい「管理者」は入れない
          if (isBanned(r[1]) || !h.canJoin()) { PgNet.reject(e.id); return; }         // 参加禁止の端末・ゲーム中・満員
          S.pendingNames.set(e.id, e.name); S.pendingDevices.set(e.id, r[1]); PgNet.accept(e.id);
        });
        break;
      }
      case 'connected': {
        if (S.pendingAdmins.delete(e.id)) {
          S.admins.set(e.id, '');
          notice('🛡 管理者が部屋の様子を確認しています');
          sendAdminView(e.id);
          break;
        }
        if (S.pendingDevices.has(e.id)) { S.devices.set(e.id, S.pendingDevices.get(e.id)); S.pendingDevices.delete(e.id); }
        const n = S.pendingNames.get(e.id) || T('ゲスト'); S.pendingNames.delete(e.id);
        if (h.join(e.id, n, false) === 'ok') { S.connected.add(e.id); publish(true); }
        else { PgNet.send([e.id], JSON.stringify({ t: 'bye', reason: T('ゲーム中のため参加できませんでした') })); setTimeout(function () { PgNet.disconnect(e.id); }, 500); }
        break;
      }
      case 'data': {
        if (S.admins.has(e.id)) { let op = null; try { op = JSON.parse(e.text); } catch (err) {} handleAdmin(e.id, op); break; }
        if (e.text.indexOf('"adminOp"') >= 0) break;   // 管理者でない人の管理者操作は無視
        if (S.connected.has(e.id) && h.apply(e.id, e.text)) publish();
        break;
      }
      case 'closed':
        S.pendingNames.delete(e.id); S.pendingAdmins.delete(e.id); S.pendingDevices.delete(e.id);
        if (S.admins.delete(e.id)) break;
        if (S.connected.delete(e.id)) {
          const who = nameOf(e.id);
          h.leave(e.id); publish();
          toast(T('{0}さんが抜けました', who));
        }
        break;
      case 'taken': if (!S.connected.size) hostRoom(); break;
      case 'error': toast(e.message === 'network' ? T('インターネットにつながりません') : T('通信エラー（{0}）', e.message)); break;
    }
  }

  function onClientNet(e) {
    switch (e.ev) {
      case 'connected': break;   // 最初の画面が届くのを待つ
      case 'failed':
        S.busy = false; S.page = 'join'; render();
        toast(e.reason === 'notfound' ? T('部屋が見つかりません。部屋コードを確かめてください（アプリの版が違うときも入れません）')
          : e.reason === 'rejected' ? T('参加できませんでした（ゲーム中・満員・版が違う、のどれかです）') : T('接続できませんでした。もう一度試してください'));
        break;
      case 'data': {
        let m = null; try { m = JSON.parse(e.text); } catch (err) { return; }
        if (m.t === 'view') { S.busy = false; setView(m.view); }
        else if (m.t === 'bye') { resetNet(); S.view = null; S.page = 'title'; closeModal(); render(); toast(m.reason); }
        else if (m.t === 'notice') toast(T.apply(null, [String(m.text || '')].concat(m.args || [])));
        break;
      }
      case 'closed':
        if (S.page === 'room') { resetNet(); S.view = null; S.page = 'title'; closeModal(); render(); toast(T('ホストとの接続が切れました')); }
        break;
      case 'error': toast(e.message === 'network' ? T('インターネットにつながりません') : T('通信エラー')); break;
    }
  }

  // ================= 広告 =================

  const adsOn = !!(CFG.adsenseClient && /^https?:/.test(location.protocol));
  let adsLoaded = false;
  function loadAds() {
    if (!adsOn || adsLoaded) return; adsLoaded = true;
    // 公開用の index.html には AdSense のコードが最初から入っている（審査で必要）。無いときだけここで読み込む
    if (!document.querySelector('script[src*="adsbygoogle.js"]')) {
      const s = document.createElement('script'); s.async = true; s.crossOrigin = 'anonymous';
      s.src = 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=' + encodeURIComponent(CFG.adsenseClient);
      if (CFG.adTest) s.setAttribute('data-adbreak-test', 'on');
      document.head.appendChild(s);
    }
    // ゲーム用の全画面広告（H5 Games Ads の Ad Placement API）
    window.adsbygoogle = window.adsbygoogle || [];
    window.adBreak = window.adConfig = function (o) { window.adsbygoogle.push(o); };
    if (CFG.h5games) window.adConfig({ preloadAdBreaks: 'on', sound: 'on' });
  }
  loadAds();
  function adUnit(slot, style) {
    if (!adsOn || !slot) return T('<div class="adph">広告枠</div>');
    return '<ins class="adsbygoogle" style="' + (style || 'display:block') + '" data-ad-client="' + esc(CFG.adsenseClient) + '" data-ad-slot="' + esc(slot) + '" data-ad-format="auto" data-full-width-responsive="true"></ins>';
  }
  function pushAd() { try { (window.adsbygoogle = window.adsbygoogle || []).push({}); } catch (e) {} }
  const $banner = document.getElementById('banner');
  let bannerReady = false;
  /** 広告サービスの管理画面でコピーした広告タグ（HTML）を差し込む。innerHTML では script が動かないので作り直す */
  const TAGS = CFG.adTags || {};
  function injectTag(el, html) {
    // 忍者AdMax の t.js は1ページで1回しか広告を取りに行かないので、2つ目以降の広告（休憩画面など）のために印を消してから読み直す
    if (html.indexOf('admax') >= 0) { try { window.__admax_tag__ = undefined; window.admaxads = []; } catch (e) {} }
    el.innerHTML = html;
    el.querySelectorAll('script').forEach(function (old) {
      const s = document.createElement('script');
      for (const a of old.attributes) s.setAttribute(a.name, a.value);
      s.text = old.textContent;
      old.replaceWith(s);
    });
  }
  // 広告の枠は「スマホ向け」「PC向け」で配信が分かれているので、画面の幅ではなく端末の種類で選ぶ
  // （PC で窓を狭くしてもスマホ向けの枠を出すと、広告が来ない）
  const IS_MOBILE = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent) || (navigator.maxTouchPoints > 1 && /Macintosh/.test(navigator.userAgent));
  function bannerTag() { return IS_MOBILE ? (TAGS.bannerSp || TAGS.bannerPc) : (TAGS.bannerPc || TAGS.bannerSp); }
  function breakTag() { return IS_MOBILE ? (TAGS.break || TAGS.bannerSp || bannerTag()) : (TAGS.breakPc || TAGS.bannerPc || bannerTag()); }
  /** PC で窓が狭いときは、横長の広告を縮めて収める */
  function fitBanner() {
    const a = $banner.querySelector('.admax-ads'); if (!a || IS_MOBILE) return;
    const k = Math.min(1, (innerWidth - 8) / 728);
    a.style.transform = k < 1 ? 'scale(' + k + ')' : ''; a.style.transformOrigin = 'center bottom';
  }
  addEventListener('resize', fitBanner);
  function showBanner(on) {
    $banner.hidden = !on;
    if (on && !bannerReady) {
      bannerReady = true;
      if (adsOn && CFG.bannerSlot) { loadAds(); $banner.innerHTML = adUnit(CFG.bannerSlot, innerWidth <= 520 ? 'display:inline-block;width:320px;height:50px' : 'display:inline-block;width:728px;height:90px'); pushAd(); }
      else if (bannerTag()) { injectTag($banner, bannerTag()); setTimeout(fitBanner, 50); setTimeout(fitBanner, 3000); }
      else $banner.innerHTML = adUnit('');
    }
  }

  /** ゲームの合間の広告休憩（全員の端末で同時に出る） */
  let adBreakShown = -1;
  function adBreak(id) {
    if (adBreakShown === id) return; adBreakShown = id;
    loadAds();
    // AdSense のゲーム向け全画面広告が使えるときはそれを出す（出なかったときもすぐ次へ）
    if (adsOn && CFG.h5games && typeof window.adBreak === 'function') {
      openModal(T('<div class="box">{0}<h2>ちょっと休憩…</h2><div class="cap">みんなの準備ができたら次へ進みます</div></div>', img('icon_tea', 90, 'art')));
      let finished = false;
      const done = function () { if (finished) return; finished = true; musDuck(false); closeModal(); send({ t: 'adDone', breakId: id }); };
      musDuck(true);
      window.adBreak({ type: 'next', name: 'round_break', adBreakDone: done });
      setTimeout(done, 20000);   // 万一戻ってこなくても止まらないように
      return;
    }
    const useTag = !(adsOn && CFG.breakSlot) && !!breakTag();
    let left = (adsOn && CFG.breakSlot) || useTag ? 5 : 3;
    openModal(T('<div class="box">{0}<h2>ちょっと休憩…</h2><div class="adbox" id="breakAd">{1}</div><button class="big" id="adClose" disabled>あと {2} 秒</button></div>', img('icon_tea', 90, 'art'), (useTag ? '' : adUnit(CFG.breakSlot)), left));
    if (adsOn && CFG.breakSlot) pushAd();
    else if (useTag) injectTag(document.getElementById('breakAd'), breakTag());
    const btn = document.getElementById('adClose');
    const t = setInterval(function () {
      left--; if (left > 0) { btn.textContent = T('あと {0} 秒', left); return; }
      clearInterval(t); btn.disabled = false; btn.textContent = T('閉じて次へ');
    }, 1000);
    btn.onclick = function () { closeModal(); send({ t: 'adDone', breakId: id }); };
  }

  function openModal(html) { const m = document.getElementById('modal'); m.innerHTML = html; m.hidden = false; }
  function closeModal() { const m = document.getElementById('modal'); m.hidden = true; m.innerHTML = ''; }
  function confirmBox(title, text, okLabel, onOk) {
    openModal(T('<div class="box"><h2>{0}</h2><div class="cap">{1}</div><div class="row"><button class="big sec grow" id="mNo">やめる</button><button class="big berry grow" id="mOk">{2}</button></div></div>', esc(title), esc(text), esc(okLabel)));
    document.getElementById('mNo').onclick = closeModal;
    document.getElementById('mOk').onclick = function () { closeModal(); onOk(); };
  }

  // ================= 描画 =================

  function render() {
    // 入力中の文字とフォーカスを保つ
    const ae = document.activeElement, focusId = ae && ae.id, sel = ae && ae.selectionStart;
    const bar = document.getElementById('roomBar');
    let html = '', banner = false;
    bgm(S.page === 'title' || S.page === 'join' || !S.view ? 'title' : (S.bgm || 'lobby'));
    if (S.page === 'title') { bar.hidden = true; html = titlePage(); banner = true; }
    else if (S.page === 'join') { bar.hidden = true; html = joinPage(); banner = true; }
    else if (S.view) {
      const v = S.view;
      bar.hidden = false;
      document.getElementById('leaveBtn').textContent = T('退出');
      document.getElementById('langBtn').textContent = '🌐 ' + LANG.toUpperCase();
      document.getElementById('soundBtn').textContent = SND.on ? '🔊' : '🔇';
      document.getElementById('musicBtn').classList.toggle('off', !MUS.on);
      document.getElementById('roomLabel').textContent = T('🌐 部屋 {0}{1}', v.roomCode, (v.isHost ? T('（あなたがホスト）') : ''));
      // お絵かき中は、ほかの人が送っただけなら画面を作り直さない（描きかけの絵や入力を消さないため）
      const root = document.getElementById('drawRoot');
      if (v.screen.t === 'drawTask' && root && root.dataset.key === drawKey(v.screen) && S.ui.drawStep === v.screen.step) {
        document.getElementById('drawStatus').innerHTML = drawStatus(v, v.screen);
        showBanner(false); tickTimers(); return;
      }
      html = screenHtml(v);
      banner = ['lobby', 'arenaResult', 'wwResult', 'ngResult', 'syncReveal', 'guessResult', 'qReveal', 'scaleResult'].indexOf(v.screen.t) >= 0;
      if (v.screen.t === 'adBreak') adBreak(v.screen.breakId);
    }
    $main.innerHTML = html;
    hydrateDraw();
    showBanner(banner);
    if (focusId) { const el = document.getElementById(focusId); if (el) { el.focus(); try { el.setSelectionRange(sel, sel); } catch (e) {} } }
    tickTimers();
  }

  function tickTimers() {
    const r = remaining();
    if (S.countsDown && r != null && S.page === 'room') {
      const sec = Math.ceil(Math.max(0, r) / 1000);
      if (sec !== S.lastSec) { S.lastSec = sec; if (sec >= 4 && sec <= 10) sfx('tick'); else if (sec >= 1 && sec <= 3) sfx('tick_last'); else if (sec === 0) sfx('buzzer'); }
    } else S.lastSec = null;
    if (r != null && r <= 0 && S.view && S.view.screen.t === 'drawTask' && !S.view.screen.submitted) drawSend();
    document.querySelectorAll('[data-timer]').forEach(function (el) {
      if (r == null) { el.textContent = ''; return; }
      el.textContent = fmt(r); el.classList.toggle('low', r < Number(el.dataset.low || 10000));
    });
    document.querySelectorAll('[data-nose]').forEach(function (el) {
      const left = Math.max(0, META.noseWindowMs - (performance.now() - (S.ui.goSeenAt || 0)));
      el.textContent = T('あと {0} 秒', (left / 1000).toFixed(1));
    });
  }
  setInterval(tickTimers, 100);

  /** 読みもののページへのリンクを、その言語のページにする */
  function localLinks(h) { return LANG === 'ja' ? h : h.replace(/href="(howto|about|privacy)\.html"/g, 'href="$1.' + LANG + '.html"'); }
  function langLabel() { return (LANGS.find(function (l) { return l[0] === LANG; }) || LANGS[0])[1]; }
  function titlePage() {
    return '<div class="langbar"><button class="btn' + (MUS.on ? '' : ' off') + '" data-act="music" aria-label="music">🎵</button><button class="btn" data-act="sound" aria-label="sound">' + (SND.on ? '🔊' : '🔇') + '</button><button class="btn" data-act="lang">🌐 Language · ' + langLabel() + '</button></div>' + localLinks(T('<div class="title"><div class="hero"><img src="img/arena_hero.webp" alt=""><span class="badge">一押し！ なんでもアリーナ</span></div><div class="stack"><div class="row">{0}<div><h1>わちゃパ</h1><div class="cap">なんでもアリーナ・みんなのものさし・ワードウルフ・お絵かき伝言 ほか全9種<br>iPhone・PC・Androidアプリの人と、部屋コードでいっしょに遊べます</div></div></div><input id="nameIn" class="text" maxlength="10" placeholder="あなたの名前（10文字まで）" value="{1}"><button class="big" data-act="host"{2}>{3}部屋を作る</button><button class="big sec" data-act="toJoin"{4}>{5}部屋に参加する</button><div class="cap">話すゲームは、通話アプリ（LINE・Discord など）をつなぎながら遊びましょう。</div><div class="row cap"><a class="link" href="howto.html">遊び方・カード一覧</a><a class="link" href="about.html">このサイトについて</a><a class="link" href="privacy.html">プライバシーポリシー</a></div></div></div>', img('char_wolf_hello', 96), esc(S.name), (S.name.trim() ? '' : ' disabled'), img('icon_create', 36), (S.name.trim() ? '' : ' disabled'), img('icon_join', 36)));
  }

  function joinPage() {
    return T('<div class="stack" style="max-width:520px;margin:30px auto;width:100%"><h2 class="center">部屋コードで参加</h2><div class="cap center">部屋を作った人の画面に出ている6けたの数字を入れてください（スマホのアプリの部屋にも入れます）</div>{0}<input id="codeIn" class="text code" maxlength="6" inputmode="numeric" value="{1}"{2}>{3}<button class="big sec" data-act="back">もどる</button></div>', img('char_wolf_hello', 140, 'art'), esc(S.code), (S.busy ? ' disabled' : ''), (S.busy ? T('<div class="center"><span class="spin"></span>　つないでいます…</div>') : T('<button class="big" data-act="join">入る</button>')));
  }

  function hostOnly(v, waiting, html) { return v.isHost ? html : (waiting ? '<div class="cap center">' + esc(waiting) + '</div>' : ''); }
  function nextButtons(v, label) {
    return hostOnly(v, T('ホストが次に進むのを待っています…'),
      '<div class="row"><button class="big grow" data-act="next">' + esc(label || T('もう一回')) + T('</button><button class="big sec grow" data-act="lobby">ロビーへ（ゲームを変える）</button></div>'));
  }
  function timerHeader(icon, title, low) {
    return '<div class="row">' + img(icon, 48) + '<h2 class="grow">' + esc(title) + '</h2><span class="timer" data-timer data-low="' + (low || 10000) + '"></span></div>';
  }
  function scoreboard(scores, gained) {
    return T('<div class="panel"><h3>得点</h3><ul class="score">{0}</ul></div>', scores.map(function (s, i) {
      const g = (gained || []).find(function (x) { return x.id === s.id; });
      return '<li><span class="grow">' + (i + 1) + '. ' + esc(nameOf(s.id)) + (s.id === S.view.me ? T('（あなた）') : '') + '</span>' +
        (g && g.votes > 0 ? '<span style="color:var(--mint)">+' + g.votes + '　</span>' : '') + '<b style="color:var(--lantern)">' + s.votes + T('点</b></li>');
    }).join(''));
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
      case 'drawTask': return drawTask(v, s);
      case 'drawReveal': return drawReveal(v, s);
      case 'scale': return scalePlay(v, s);
      case 'scaleResult': return scaleResult(v, s);
      case 'adBreak': return T('<div class="center" style="margin-top:80px">{0}<h2>ちょっと休憩…</h2><div class="cap">全員の準備ができたら次へ進みます</div></div>', img('icon_tea', 120, 'art'));
      default: return T('<div class="cap">（この画面は PC 版では表示できません）</div>');
    }
  }

  // ---------- ロビー ----------

  const ICON = { ARENA: 'icon_arena', WORD_WOLF: 'icon_wordwolf', NG_WORD: 'icon_ngword', SPY: 'icon_spy', SYNC: 'icon_sync', GUESS: 'icon_guess', QUESTION: 'icon_question', DRAW: 'icon_draw', SCALE: 'icon_scale' };
  function summary(st) {
    switch (st.game) {
      case 'ARENA': return (st.arenaTurnSec === 0 ? T('手番の制限なし') : T('手番 {0}秒', st.arenaTurnSec)) + (st.arenaChaos ? T('・今夜のルールあり') : '');
      case 'WORD_WOLF': return T('話し合い {0}分', st.talkSec / 60);
      case 'SPY': return T('質問タイム {0}分', st.talkSec / 60);
      case 'NG_WORD': return T('制限時間 {0}分', st.ngSec / 60);
      case 'SYNC': return st.syncSec === 0 ? T('回答時間 制限なし') : T('回答時間 {0}秒', st.syncSec);
      case 'GUESS': return T('制限時間 {0}分', st.guessSec / 60);
      case 'DRAW': return T('描く {0}秒・書く {1}秒', st.drawSec, st.writeSec);
      case 'SCALE': return st.scaleCards === 0 ? T('全員で協力・ライフ{0}', 3) : T('1人{0}枚・ライフ{1}', st.scaleCards, 3);
      default: return T('全員で匿名投票');
    }
  }
  function chips(title, items) {
    return '<h3 style="margin-top:12px">' + esc(title) + '</h3><div class="row">' + items.map(function (it) {
      return '<button class="chip' + (it.on ? ' on' : '') + '" data-act="set" data-arg=\'' + esc(JSON.stringify(it.patch)) + '\'>' + esc(it.label) + '</button>';
    }).join('') + '</div>';
  }

  function lobby(v) {
    const st = v.settings;
    let h = T('<div class="panel"><div class="cap center">オンラインの部屋コード。参加する人は「部屋に参加する」でこの数字を入れます（スマホのアプリは「オンライン」に切り替えてから）</div><div class="code">{0}</div><div class="center row" style="justify-content:center"><button class="btn" data-act="copy">コードをコピー</button>{1}</div></div>', esc(v.roomCode), (navigator.share ? T('<button class="btn lan" data-act="share">招待を送る</button>') : ''));
    h += T('<div class="panel"><h3>参加者 {0}人</h3><ul class="players">{1}</ul></div>', v.players.length, v.players.map(function (p) {
      return '<li><span class="grow">' + (p.isHost ? '👑 ' : '・ ') + esc(p.name) + (p.id === v.me ? T('（あなた）') : '') + '</span>' +
        (v.isHost && !p.isHost ? T('<button class="link" data-act="kick" data-arg="{0}">退出させる</button>', esc(p.id)) : '') + '</li>';
    }).join(''));
    if (v.isHost) {
      h += T('<div class="panel"><h3>ゲーム</h3><div class="featured{0}" data-act="game" data-arg="ARENA"><img class="bg" src="img/arena_hero.webp" alt=""><span class="badge tl">一押し！ NEW</span>{1}<div class="band"><div class="row">{2}<b>なんでもアリーナ</b></div><div class="cap" style="color:var(--soft)">何が起きるかわからない理不尽カードバトル（2〜8人）</div></div></div><div class="games" style="margin-top:12px">{3}</div>', (st.game === 'ARENA' ? ' on' : ''), (st.game === 'ARENA' ? T('<span class="badge lan tr">✓ 選択中</span>') : ''), img('icon_arena', 38), META.games.filter(function (g) { return g.id !== 'ARENA'; }).map(function (g) {
          return '<button class="game' + (st.game === g.id ? ' on' : '') + '" data-act="game" data-arg="' + g.id + '">' + img(ICON[g.id], 54) + esc(g.short) + '</button>';
        }).join(''));
      switch (st.game) {
        case 'ARENA':
          h += chips(T('1手番の制限時間（過ぎると勝手にカードが出る）'), [[30, T('30秒')], [60, T('60秒')], [90, T('90秒')], [0, T('なし')]].map(function (x) { return { label: x[1], on: st.arenaTurnSec === x[0], patch: { arenaTurnSec: x[0] } }; }));
          h += chips(T('今夜のルール（開始時にランダムで1つ追加）'), [{ label: T('あり'), on: st.arenaChaos, patch: { arenaChaos: true } }, { label: T('なし（初めての人向け）'), on: !st.arenaChaos, patch: { arenaChaos: false } }]);
          break;
        case 'WORD_WOLF': case 'SPY': {
          const role = st.game === 'SPY' ? T('スパイ') : T('ウルフ');
          h += chips(st.game === 'SPY' ? T('質問タイム') : T('話し合いの時間'), [120, 180, 240, 300, 420].map(function (x) { return { label: T('{0}分', x / 60), on: st.talkSec === x, patch: { talkSec: x } }; }));
          h += chips(T('{0}の人数', role), [[0, T('おまかせ')], [1, T('1人')], [2, T('2人')]].map(function (x) { return { label: x[1], on: st.wolves === x[0], patch: { wolves: x[0] } }; }));
          if (st.game === 'WORD_WOLF') h += chips(T('お題のジャンル（選ばなければ全部）'), v.categories.map(function (c) {
            const on = st.categories.indexOf(c) >= 0;
            return { label: c, on: on, patch: { categories: on ? st.categories.filter(function (x) { return x !== c; }) : st.categories.concat([c]) } };
          }));
          break;
        }
        case 'NG_WORD':
          h += chips(T('制限時間'), [180, 300, 600].map(function (x) { return { label: T('{0}分', x / 60), on: st.ngSec === x, patch: { ngSec: x } }; }));
          h += chips(T('ワードの難しさ'), [['NORMAL', T('ふつう')], ['HARD', T('ハード（口ぐせ）')], ['MIX', T('ミックス')]].map(function (x) { return { label: x[1], on: st.ngLevel === x[0], patch: { ngLevel: x[0] } }; }));
          break;
        case 'SYNC':
          h += chips(T('回答時間'), [[30, T('30秒')], [60, T('60秒')], [90, T('90秒')], [0, T('制限なし')]].map(function (x) { return { label: x[1], on: st.syncSec === x[0], patch: { syncSec: x[0] } }; }));
          break;
        case 'GUESS':
          h += chips(T('制限時間'), [120, 180, 300].map(function (x) { return { label: T('{0}分', x / 60), on: st.guessSec === x, patch: { guessSec: x } }; }));
          break;
        case 'SCALE':
          h += '<div class="cap" style="margin-top:10px">' + esc(T('全員にひみつの数字（1〜100）を配ります。お題に合わせて数字を「ことば」で表し、話し合いながら小さい順に出していく協力ゲーム。もっと小さい数字が残っていたらミスでライフ -1（2〜8人）。')) + '</div>';
          h += chips(T('1人に配る数字の枚数'), [[0, T('おまかせ')], [1, T('1枚')], [2, T('2枚')], [3, T('3枚')]].map(function (x) { return { label: x[1], on: st.scaleCards === x[0], patch: { scaleCards: x[0] } }; }));
          break;
        case 'DRAW':
          h += T('<div class="cap" style="margin-top:10px">お題を書く → 次の人が絵にする → 次の人がその絵を見て答える…を全員分くり返し、最後にどう伝わったかを発表します（3〜8人）。</div>');
          h += chips(T('絵を描く時間'), [60, 90, 120, 180].map(function (x) { return { label: T('{0}秒', x), on: st.drawSec === x, patch: { drawSec: x } }; }));
          h += chips(T('お題を書く・答える時間'), [30, 45, 60, 90].map(function (x) { return { label: T('{0}秒', x), on: st.writeSec === x, patch: { writeSec: x } }; }));
          break;
        default: h += T('<div class="cap" style="margin-top:10px">設定はありません。だれが誰に入れたかは表示されません。</div>');
      }
      h += '</div>';
      const need = GAMES[st.game].min;
      h += '<button class="big" data-act="start"' + (v.players.length >= need ? '' : ' disabled') + '>' + img(ICON[st.game], 36) + (v.players.length >= need ? T('はじめる') : T('{0}人からはじめられます', need)) + '</button>';
    } else {
      h += T('<div class="panel"><div class="row">{0}<div><h2 style="color:var(--lantern)">{1}</h2><div class="cap">{2}</div></div></div></div><div class="cap center">ホストがはじめるのを待っています…</div>', img(ICON[st.game], 60), esc(GAMES[st.game].label), esc(summary(st)));
    }
    return h;
  }

  // ---------- ワードウルフ／スパイ ----------

  function spyGame() { return S.view.settings.game === 'SPY'; }
  function role() { return spyGame() ? T('スパイ') : T('ウルフ'); }
  function spyCard() { return T('<div class="panel"><div class="row">{0}<div><h2 style="color:var(--berry)">あなたはスパイ</h2><div class="cap">みんなの質問や答えから場所を推理しよう。バレないように！</div></div></div></div>', img('icon_spy', 64)); }

  function wwReveal(v, s) {
    const done = s.confirmed.indexOf(v.me) >= 0;
    let h = '<div class="row">' + img(ICON[v.settings.game], 48) + '<h2>' + (spyGame() ? T('場所') : T('お題')) + '</h2></div>';
    if (s.spy) h += spyCard();
    else h += '<div class="cap">' + (spyGame() ? T('あなたがいる場所（ほかの人に見せないで）') : T('あなたのお題（ほかの人に見せないで）')) + '</div><div class="panel"><div class="word">' + esc(s.word) + '</div></div>' +
      '<div class="cap">' + (spyGame() ? T('全員同じ場所にいますが、1人だけ場所を知らないスパイがまぎれています。') : T('多数派（市民）か少数派（ウルフ）かは、まだ誰にも分かりません。話しながら探りましょう。')) + '</div>';
    h += '<button class="big" data-act="send" data-arg=\'{"t":"confirm"}\'' + (done ? ' disabled' : '') + '>' + (done ? T('確認しました') : T('確認した')) + '</button>';
    h += T('<div class="cap center">確認済み {0} / {1}人</div>', s.confirmed.length, v.players.length);
    return h;
  }

  function wwTalk(v, s) {
    let h = '<div class="row">' + img('icon_timer', 60) + '<h2 class="grow">' + (spyGame() ? T('質問タイム') : T('話し合い')) + '</h2><span class="timer" data-timer data-low="30000"></span></div>';
    if (s.spy) h += spyCard();
    else if (s.word != null) h += '<div class="panel" data-act="peek" style="cursor:pointer"><div class="cap">' + (spyGame() ? T('あなたがいる場所（タップで表示／非表示）') : T('あなたのお題（タップで表示／非表示）')) + '</div><div class="word">' + (S.ui.peek ? esc(s.word) : '●●●') + '</div></div>';
    if (spyGame()) h += T('<div class="cap">例：「ここには何をしに来ますか？」「今どんな服を着ていますか？」</div>');
    else h += T('<div class="cap">お題について話し合って、ウルフ（違うお題の人）を探しましょう</div>');
    h += hostOnly(v, T('時間になると投票に進みます'), T('<button class="big sec" data-act="send" data-arg=\'{"t":"endTalk"}\'>切り上げて投票へ</button>'));
    return h;
  }

  function wwVote(v, s) {
    let h = '<div class="row">' + img('icon_vote', 48) + '<h2>' + (s.runoff ? T('決選投票') : T('投票')) + '</h2></div>';
    h += '<div class="cap">' + (s.runoff ? T('同票だった人の中から、{0}だと思う人を選んでください', role()) : T('{0}だと思う人を選んでください', role())) + T('</div><div class="choices">{0}</div>', s.candidates.filter(function (id) { return id !== v.me; }).map(function (id) {
        return '<button class="choice' + (s.myVote === id ? ' on' : '') + '" data-act="send" data-arg=\'' + esc(JSON.stringify({ t: 'vote', target: id })) + '\'>' + (s.myVote === id ? '✓ ' : '') + esc(nameOf(id)) + '</button>';
      }).join(''));
    h += T('<div class="cap center">投票済み {0} / {1}人{2}</div>', s.voted.length, v.players.length, (s.myVote ? T('（全員そろうまで変更できます）') : ''));
    h += hostOnly(v, '', T('<button class="big sec" data-act="send" data-arg=\'{"t":"closeVote"}\'>締め切って集計</button>'));
    return h;
  }

  function wwGuess(v, s) {
    let h = T('{0}<h2 class="center" style="color:var(--berry)">{1}発見！</h2><div class="panel"><div class="cap">{2}は</div><div class="word s" style="color:var(--berry)">{3}</div></div>', img('char_wolf_caught', 170, 'art'), role(), role(), esc(s.wolves.map(nameOf).join(T('、'))));
    if (spyGame()) {
      h += '<div>' + (s.iAmWolf ? T('最後のチャンス！場所を当てれば逆転勝ち。下から選んでください。') : T('スパイが場所を当てたら逆転されます。スパイが選ぶのを待っています…')) + '</div>';
      if (s.iAmWolf) h += '<div class="choices">' + s.options.map(function (o) { return '<button class="choice" data-act="send" data-arg=\'' + esc(JSON.stringify({ t: 'spyGuess', answer: o })) + '\'>' + esc(o) + '</button>'; }).join('') + '</div>';
      return h;
    }
    h += '<div>' + (s.iAmWolf ? T('最後のチャンス！市民のお題を当てれば逆転勝ち。口で答えてください。') : T('でも、ウルフが市民のお題を当てたら逆転されます。ウルフに答えてもらいましょう。')) + '</div>';
    h += hostOnly(v, T('ホストが判定します…'), T('<div class="cap">ウルフの答えは市民のお題と…</div><div class="row"><button class="big berry grow" data-act="send" data-arg=\'{"t":"judge","correct":true}\'>合っていた</button><button class="big grow" style="background:var(--mint)" data-act="send" data-arg=\'{"t":"judge","correct":false}\'>違った</button></div>'));
    return h;
  }

  function wwResult(v, s) {
    const cw = s.winner === 'CITIZEN', spy = spyGame(), r = role();
    const reason = { WOLF_CAUGHT: spy ? T('スパイを見つけて、場所も守りきりました') : T('ウルフを見つけて、逆転も防ぎました'),
      WOLF_GUESSED: spy ? T('スパイが場所を当てて逆転！') : T('ウルフが市民のお題を当てて逆転！'),
      CITIZEN_ACCUSED: T('{0}さん（{1}ではない）が最多票に…{2}の逃げ切り', nameOf(s.accused), r, r), TIE: T('票が割れて、{0}の逃げ切り', r) }[s.reason];
    let h = img(cw ? 'char_villagers' : 'char_wolf_grin', 170, 'art') +
      '<div class="word" style="color:' + (cw ? 'var(--mint)' : 'var(--berry)') + '">' + (cw ? (spy ? T('みんなの勝ち！') : T('市民の勝ち！')) : T('{0}の勝ち！', r)) + '</div><div class="cap center">' + esc(reason) + '</div>';
    h += '<div class="panel">';
    if (spy) h += T('<div class="cap">場所</div><h2 style="color:var(--mint)">{0}</h2>{1}', esc(s.citizenWord), (s.spyGuess ? T('<div class="cap">スパイの答え</div><h2 style="color:var(--berry)">{0}</h2>', esc(s.spyGuess)) : ''));
    else h += T('<div class="cap">市民のお題</div><h2 style="color:var(--mint)">{0}</h2><div class="cap">ウルフのお題</div><h2 style="color:var(--berry)">{1}</h2>', esc(s.citizenWord), esc(s.wolfWord));
    h += '<div class="cap">' + r + '</div><h3>' + esc(s.wolves.map(nameOf).join(T('、'))) + '</h3>' + (s.myWord && !spy ? T('<div class="cap">あなたのお題：{0}</div>', esc(s.myWord)) : '') + '</div>';
    if (s.tally.length) h += T('<div class="panel"><h3>得票</h3>{0}</div>', s.tally.map(function (t) { return T('<div>{0}　{1}票</div>', esc(nameOf(t.id)), t.votes); }).join(''));
    return h + nextButtons(v);
  }

  // ---------- NGワード ----------

  function ngPlay(v, s) {
    let h = timerHeader('icon_ngword', T('NGワード'), 30000);
    const mine = s.entries.find(function (e) { return e.id === v.me; });
    h += s.myOut ? T('<div class="panel"><h2 style="color:var(--berry)">アウト！</h2><div>あなたのNGワードは「{0}」でした。ほかの人を見張りましょう</div></div>', esc(mine && mine.word))
      : T('<div class="cap">みんなのNGワードが見えます（あなたのだけ見えません）。言わせたら「言った！」</div>');
    h += s.entries.map(function (e) {
      const me = e.id === v.me;
      let btn = '';
      if (e.out && v.isHost) btn = T('<button class="link" data-act="send" data-arg=\'{0}\'>取り消し</button>', esc(JSON.stringify({ t: 'undoOut', target: e.id })));
      else if (e.out) btn = T('<span class="warn">アウト</span>');
      else if (!me && e.word != null) btn = T('<button class="btn on" data-act="ngOut" data-arg="{0}">言った！</button>', esc(e.id));
      return '<div class="panel ng"><div class="grow"><div class="cap">' + esc(nameOf(e.id)) + (me ? T('（あなた）') : '') + '</div><div class="w' + (e.out ? ' out' : '') + '">' + esc(e.word == null ? T('？？？') : e.word) + '</div></div>' + btn + '</div>';
    }).join('');
    h += hostOnly(v, '', T('<button class="big sec" data-act="send" data-arg=\'{"t":"endNg"}\'>ゲームを終える</button>'));
    return h;
  }

  function ngResult(v, s) {
    let h = img(s.winners.length ? 'char_villagers' : 'char_wolf_caught', 160, 'art') +
      '<div class="word">' + (s.winners.length ? esc(s.winners.map(nameOf).join(T('、'))) + T('の勝ち！') : T('全員アウト！')) + '</div>';
    h += T('<div class="panel"><h3>みんなのNGワード</h3>{0}</div>', s.entries.slice().sort(function (a, b) { return (a.out - b.out) || (b.outOrder - a.outOrder); }).map(function (e) {
      return T('<div class="row"><span class="grow">{0}</span><span style="color:{1}">「{2}」{3}</span></div>', esc(nameOf(e.id)), (e.out ? 'var(--muted)' : 'var(--mint)'), esc(e.word), (e.out ? T('　{0}番目にアウト', e.outOrder) : T('　生き残り')));
    }).join(''));
    return h + nextButtons(v);
  }

  // ---------- シンクロ ----------

  function syncAnswer(v, s) {
    if (S.ui.prompt !== s.prompt) { S.ui.prompt = s.prompt; S.ui.draft = s.myAnswer || ''; }
    let h = timerHeader('icon_sync', T('みんなでシンクロ'));
    h += T('<div class="cap">みんなと同じ答えになるほど得点！</div><div class="panel"><div class="word s">{0}</div></div>', esc(s.prompt));
    h += T('<input id="syncIn" class="text" maxlength="20" placeholder="あなたの答え" value="{0}">', esc(S.ui.draft));
    h += '<button class="big" data-act="syncSend">' + (s.myAnswer == null ? T('これで答える') : T('答えを変える')) + '</button>';
    if (s.myAnswer != null) h += T('<div class="cap">あなたの答え：「{0}」（全員そろうまで変更できます）</div>', esc(s.myAnswer));
    h += T('<div class="cap center">回答済み {0} / {1}人</div>', s.answered.length, v.players.length);
    h += hostOnly(v, '', T('<button class="big sec" data-act="send" data-arg=\'{"t":"closeVote"}\'>締め切って答え合わせ</button>'));
    return h;
  }

  function syncReveal(v, s) {
    let h = T('<div class="row">{0}<h2>答え合わせ</h2></div><div class="panel"><h2 class="center">{1}</h2></div>', img('icon_sync', 48), esc(s.prompt));
    h += s.groups.map(function (g) {
      const m = g.ids.length >= 2;
      return '<div class="panel row"><div class="grow"><h2 style="color:' + (m ? 'var(--lantern)' : 'var(--muted)') + '">' + esc(g.answer) + '</h2><div class="cap">' + esc(g.ids.map(nameOf).join(T('、'))) + '</div></div><b style="color:' + (m ? 'var(--mint)' : 'var(--muted)') + '">' + (m ? T('{0}人シンクロ！', g.ids.length) : T('ひとり')) + '</b></div>';
    }).join('');
    const no = v.players.filter(function (p) { return !s.groups.some(function (g) { return g.ids.indexOf(p.id) >= 0; }); });
    if (no.length) h += T('<div class="cap">未回答：{0}</div>', esc(no.map(function (p) { return p.name; }).join(T('、'))));
    return h + scoreboard(s.scores, s.gained) + nextButtons(v, T('次のお題へ'));
  }

  // ---------- みんなのものさし ----------

  function lives(n, max) { return '<span class="lives">' + '❤️'.repeat(Math.max(0, n)) + '🖤'.repeat(Math.max(0, max - n)) + '</span>'; }
  function ntag(c, big) {
    return '<span class="ntag ' + c.state.toLowerCase() + (big ? ' big' : '') + '">' + (c.value == null ? '?' : c.value) + '</span>';
  }
  function scaleRow(v, c) {
    return '<div class="srow">' + ntag(c) + '<div class="grow"><b' + (c.hint ? '' : ' class="mutedtxt"') + '>' + (c.hint ? esc(T('「{0}」', c.hint)) : esc(T('（考え中…）'))) + '</b><div class="cap">' +
      esc(c.ownerName) + (c.owner === v.me ? T('（あなた）') : '') + (c.state === 'MISSED' ? T('・ミスで捨てられた') : '') + '</div></div></div>';
  }
  function ruler(t) {
    return '<div class="panel"><div class="cap">' + T('今回のお題') + '</div><div class="word s">' + esc(t.t) + '</div><div class="ruler"></div>' +
      '<div class="row" style="justify-content:space-between;align-items:flex-start"><div><b style="color:var(--mint)">1</b><div class="cap">' + esc(t.lo) + '</div></div>' +
      '<div style="text-align:right"><b style="color:var(--berry)">100</b><div class="cap">' + esc(t.hi) + '</div></div></div></div>';
  }
  function scalePlay(v, s) {
    const key = s.topic.t + '|' + s.cards.map(function (c) { return c.uid; }).join(',');
    if (S.ui.scaleKey !== key) { S.ui.scaleKey = key; S.ui.hints = {}; }
    let h = '<div class="row">' + img('icon_scale', 48) + '<h2 class="grow">' + T('みんなのものさし') + '</h2>' + lives(s.lives, s.maxLives) + '</div>';
    if (s.streak > 0) h += '<div class="cap">' + T('連続クリア {0}回中', s.streak) + '</div>';
    h += ruler(s.topic);
    if (s.lastMiss.length) {
      const missed = s.cards.filter(function (c) { return s.lastMiss.indexOf(c.uid) >= 0; }).sort(function (a, b) { return a.value - b.value; });
      h += '<div class="panel"><b style="color:var(--berry)">' + T('ミス！ もっと小さい数字がありました') + '</b><div class="cap">' +
        esc(missed.map(function (c) { return T('{0}（{1}さん）', c.value, c.ownerName); }).join(T('、'))) + '</div></div>';
    }
    const mine = s.cards.filter(function (c) { return c.owner === v.me; }).sort(function (a, b) { return a.value - b.value; });
    const next = mine.filter(function (c) { return c.state === 'HELD'; })[0];
    if (!s.playing) h += '<div class="cap">' + T('自分の数字を、お題に合わせた「ことば」で表しましょう。数字そのものは言わないで！') + '</div>';
    else if (next) h += '<button class="big" data-act="send" data-arg=\'{"t":"scalePlay"}\'>' + img('icon_scale', 36) + T('{0} を出す', next.value) + '</button><div class="cap">' + T('自分の数字がいちばん小さいと思ったら出しましょう。話し合ってOK（数字は言わない）') + '</div>';
    else h += '<div class="cap">' + T('あなたのカードは全部出ました。みんなを応援しよう') + '</div>';
    h += mine.map(function (c) {
      let x = '<div class="panel"><div class="row">' + ntag(c, true) + '<div class="grow">';
      if (c.state === 'PLAYED') x += '<b style="color:var(--mint)">' + T('出しました') + '</b><div class="cap">' + esc(T('「{0}」', c.hint)) + '</div></div></div>';
      else if (c.state === 'MISSED') x += '<b style="color:var(--berry)">' + T('ミスで捨てられました') + '</b><div class="cap">' + esc(T('「{0}」', c.hint)) + '</div></div></div>';
      else {
        const d = S.ui.hints[c.uid] != null ? S.ui.hints[c.uid] : c.hint;
        x += '<input id="scaleIn' + c.uid + '" class="text" maxlength="20" placeholder="' + esc(T('この数字をお題で表すと？')) + '" value="' + esc(d) + '"></div></div>' +
          '<button class="big' + (c.hint ? ' sec' : '') + '" data-act="scaleHint" data-arg="' + c.uid + '">' + (c.hint ? T('ヒントを変える') : T('このヒントで決める')) + '</button>';
      }
      return x + '</div>';
    }).join('');
    const played = s.cards.filter(function (c) { return c.state !== 'HELD'; }).sort(function (a, b) { return (a.order || 1e9) - (b.order || 1e9) || a.value - b.value; });
    if (played.length) h += '<div class="panel"><h3>' + T('場に出たカード') + '</h3>' + played.map(function (c) { return scaleRow(v, c); }).join('') + '</div>';
    const others = s.cards.filter(function (c) { return c.owner !== v.me && c.state === 'HELD'; });
    if (others.length) h += '<div class="panel"><h3>' + T('みんなのヒント（まだ出ていない）') + '</h3>' + others.map(function (c) { return scaleRow(v, c); }).join('') + '</div>';
    if (!s.playing) {
      h += '<div class="cap center">' + T('ヒント {0} / {1}枚。全員そろうと並べ始めます', s.cards.filter(function (c) { return c.hint; }).length, s.cards.length) + '</div>';
      h += hostOnly(v, '', '<button class="big sec" data-act="send" data-arg=\'{"t":"scaleBegin"}\'>' + T('そろわなくても並べ始める') + '</button>');
    }
    return h;
  }
  function scaleResult(v, s) {
    let h = '<div class="row">' + img('icon_scale', 48) + '<h2 style="color:' + (s.cleared ? 'var(--mint)' : 'var(--berry)') + '">' + (s.cleared ? T('クリア！') : T('しっぱい…')) + '</h2></div>';
    h += '<div class="row">' + lives(s.lives, s.maxLives) + '<span class="cap">' + (s.cleared ? T('連続クリア {0}回', s.streak) : T('ライフがなくなりました')) + '</span></div>';
    h += '<div class="panel"><div class="cap">' + T('今回のお題') + '</div><h2>' + esc(s.topic.t) + '</h2><div class="cap">' + T('1＝{0} ／ 100＝{1}', s.topic.lo, s.topic.hi) + '</div></div>';
    h += '<div class="panel"><h3>' + T('答え（小さい順）') + '</h3>' + s.cards.map(function (c) { return scaleRow(v, c); }).join('') + '</div>';
    return h + nextButtons(v, T('次のお題へ'));
  }

  // ---------- 質問で当てろ ----------

  function guessPlay(v, s) {
    const iAsk = s.asker === v.me;
    let h = timerHeader('icon_guess', T('質問で当てろ'), 30000);
    if (iAsk) h += T('<div class="panel"><div class="cap">あなたが出題者。答えは</div><div class="word">{0}</div></div><div class="cap">みんなの質問に「はい」「いいえ」「わからない」だけで答えてください。当てられたら、当てた人を押します。</div>', esc(s.answer));
    else h += T('<div class="panel"><div class="cap">出題者</div><div class="word s">{0}さん</div></div><div class="cap">「はい」「いいえ」で答えられる質問をして、出題者の答えを当てよう。例：「それは食べられますか？」</div>', esc(nameOf(s.asker)));
    if (iAsk || v.isHost) {
      h += T('<h3>当てた人</h3><div class="choices">{0}</div><button class="big sec" data-act="send" data-arg=\'{"t":"guessGiveUp"}\'>降参（答えを発表）</button>', v.players.filter(function (p) { return p.id !== s.asker; }).map(function (p) {
        return T('<button class="choice" data-act="send" data-arg=\'{0}\'>{1}さんが当てた！</button>', esc(JSON.stringify({ t: 'guessSolved', guesser: p.id })), esc(p.name));
      }).join(''));
    } else h += T('<div class="cap center">当てたら、出題者が「当てた」を押します</div>');
    return h;
  }

  function guessResult(v, s) {
    let h = img(s.guesser ? 'char_villagers' : 'char_wolf_grin', 160, 'art') + '<div class="word" style="color:' + (s.guesser ? 'var(--mint)' : 'var(--berry)') + '">' + (s.guesser ? T('{0}さんが正解！', esc(nameOf(s.guesser))) : T('時間切れ！')) + '</div>';
    h += T('<div class="panel"><div class="cap">答え</div><h2 style="color:var(--lantern)">{0}</h2><div class="cap">出題者：{1}さん{2}</div></div>', esc(s.answer), esc(nameOf(s.asker)), (s.guesser ? T('（当てた人と出題者に1点ずつ）') : ''));
    return h + scoreboard(s.scores) + nextButtons(v, T('次の出題者へ'));
  }

  // ---------- ぶっちゃけ質問 ----------

  function qVote(v, s) {
    let h = T('<div class="row">{0}<h2>ぶっちゃけ質問</h2></div><div class="panel"><div class="word s">{1}</div></div>', img('icon_question', 48), esc(s.question));
    h += T('<div class="cap">当てはまると思う人に投票（自分もOK）。だれが誰に入れたかは表示されません。</div><div class="choices">{0}</div><div class="cap center">投票済み {1} / {2}人</div>', v.players.map(function (p) {
      return '<button class="choice' + (s.myVote === p.id ? ' on' : '') + '" data-act="send" data-arg=\'' + esc(JSON.stringify({ t: 'qVote', target: p.id })) + '\'>' + (s.myVote === p.id ? '✓ ' : '') + esc(p.name) + (p.id === v.me ? T('（あなた）') : '') + '</button>';
    }).join(''), s.voted.length, v.players.length);
    return h + hostOnly(v, '', T('<button class="big sec" data-act="send" data-arg=\'{"t":"closeVote"}\'>締め切って発表</button>'));
  }

  function qReveal(v, s) {
    const top = s.tally.length ? s.tally[0].votes : 0;
    const win = s.tally.filter(function (t) { return t.votes === top && top > 0; });
    let h = T('<div class="row">{0}<h2>発表</h2></div><div class="panel"><h2 class="center">{1}</h2></div>{2}', img('icon_question', 48), esc(s.question), img('char_wolf_hello', 140, 'art'));
    h += '<div class="word">' + (win.length ? esc(win.map(function (t) { return nameOf(t.id); }).join(T('、'))) + T('さん！') : T('投票なし')) + '</div>';
    if (s.tally.length) h += '<div class="panel">' + s.tally.map(function (t) { return T('<div class="row"><span class="grow">{0}</span><b style="color:var(--lantern)">{1}票</b></div>', esc(nameOf(t.id)), t.votes); }).join('') + '</div>';
    return h + nextButtons(v, T('次の質問へ'));
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
    let h = T('<div class="row">{0}<h2 class="grow">なんでもアリーナ</h2>{1}</div>', img('icon_arena', 48), (!s.mini && v.remainingMs != null ? '<span class="timer" data-timer></span>' : ''));
    if (s.chaos) { const c = META.chaos[s.chaos]; h += T('<div class="chaos">🌙 <b>今夜のルール：{0}</b>　<span class="cap">{1}</span></div>', esc(c.title), esc(c.text)); }
    h += '<div class="seats">' + s.seats.map(function (st) {
      const turn = st.id === s.turn && st.alive;
      return '<div class="seat' + (turn ? ' turn' : '') + (st.alive ? '' : ' out') + '"><div class="nm">' + esc(nameOf(st.id)) + (st.id === v.me ? T('（あなた）') : '') + '</div>' +
        '<div class="st">' + (st.alive ? T('手札{0}枚{1}', st.hand, (st.points > 0 ? '・' + st.points + 'pt' : '')) : T('負け')) + '</div>' +
        (st.front.length ? '<div class="fr">' + st.front.map(function (c) { return '<img src="img/card_' + CARDS[c.id].art + '.webp" title="' + esc(CARDS[c.id].name) + '" alt="">'; }).join('') + '</div>' : '') +
        (canReport && st.alive && st.id !== v.me ? T('<button class="rep" data-act="report" data-arg="{0}">違反！</button>', esc(st.id)) : '') + '</div>';
    }).join('') + '</div>';
    h += '<div class="field"><div class="deck center"><img src="img/card_back.webp" alt=""><div class="cap">' + (s.noDraw ? T('引けない！') : T('山札 {0}枚', s.deck)) + '</div></div>';
    if (s.discardTop) h += T('<div>{0}<div class="cap center">さっき出た</div></div>', cardHtml(s.discardTop, { small: true, hidden: s.topHidden }));
    s.rules.forEach(function (r) { h += T('<div>{0}<div class="cap center">ルール</div></div>', cardHtml(r, { small: true })); });
    h += '</div>';
    if (s.rules.length) h += T('<div class="cap">ルール：{0}</div>', s.rules.map(function (r) { const d = CARDS[r.id]; return T('「{0}」{1}', esc(d.name), esc(afterFirst(d.text))); }).join('　'));
    if (s.endOwner) h += T('<div class="warn">🔔 {0}さんの次の手番が終わると、全員の負け！</div>', esc(nameOf(s.endOwner)));
    if (s.pointGoal !== 15) h += T('<div class="cap">大金庫が出たので、勝つには{0}ポイント必要</div>', s.pointGoal);
    if (s.mini) h += miniHtml(v, s, s.mini);

    if (me && !me.alive) h += T('<div class="panel"><b class="warn">あなたの負けです</b><div class="cap">最後まで見守りましょう。違反の報告はできます</div></div>');
    else if (myTurn) h += T('<h3 style="color:var(--lantern)">あなたの番！ カードを1枚選んで出そう</h3>');
    else if (!s.mini) h += T('<div class="cap center">{0}さんの番です</div>', esc(nameOf(s.turn)));
    if (s.hand.length) {
      h += T('<h3>あなたの手札（{0}枚）</h3><div class="hand">{1}</div>', s.hand.length, s.hand.map(function (c) { return cardHtml(c, { click: true, sel: S.ui.pick === c.uid }); }).join(''));
    }
    const sel = s.hand.find(function (c) { return c.uid === S.ui.pick; });
    if (myTurn && sel) {
      const d = CARDS[sel.id];
      let targets = [];
      if (d.target === 'ALIVE_OTHER') targets = s.seats.filter(function (st) {
        const dino = st.front.some(function (c) { return CARDS[c.id].fx === 'DINO'; });
        return st.alive && st.id !== v.me && !(placesFront(d) && dino);
      }).map(function (st) { return st.id; });
      if (targets.length) h += T('<h3>だれに？</h3><div class="row">{0}</div>', targets.map(function (t) { return '<button class="chip' + (S.ui.target === t ? ' on' : '') + '" data-act="target" data-arg="' + esc(t) + '">' + esc(nameOf(t)) + '</button>'; }).join(''));
      else if (d.target !== 'NONE') h += T('<div class="cap">指名できる人がいないので、出しても何も起きません</div>');
      h += T('<button class="big" data-act="play"{0}>「{1}」を出す</button>', (targets.length && !S.ui.target ? ' disabled' : ''), esc(d.name));
    }
    h += T('<div class="panel log"><h3>できごと</h3>{0}</div>', s.log.slice(-6).reverse().map(function (l) { return '<div>' + esc(fmtLog(l)) + '</div>'; }).join(''));
    return h;
  }
  function placesFront(d) { return ['TRAP', 'POINTS', 'LAUGH', 'BOMB', 'DINO'].indexOf(d.fx) >= 0; }

  function miniHtml(v, s, m) {
    const inIt = m.involved.indexOf(v.me) >= 0, done = m.myInput != null, owner = v.me === m.owner;
    const d = m.card ? CARDS[m.card] : null;
    let h = '<div class="mini">' + (d ? '<img src="img/card_' + d.art + '.webp" width="70" height="70" alt="">' : '');
    let q;
    if (m.kind === 'cancel') q = T('「{0}」が出された！', esc(d ? (s.topHidden ? (d.coverName || d.name) : d.name) : ''));
    else q = esc(owner || !d || !d.cover ? (d ? d.text : '') : d.cover);
    h += '<div class="q">' + q + '</div>';
    function act(val) { return 'data-act="input" data-arg="' + esc(val) + '"'; }
    switch (m.kind) {
      case 'cancel':
        h += inIt && !done ? T('<div class="cap">あなたはネコを持っています。横やりで打ち消しますか？</div><div class="row"><button class="big berry" style="width:200px" {0}>横やり！</button><button class="big sec" style="width:200px" {1}>見送る</button></div>', act('use'), act('pass'))
          : T('<div class="cap">…ちょっと待って。横やりが入るかも？</div>'); break;
      case 'yesno':
        h += owner && !done ? T('<b>今月はあなたの誕生月？</b><div class="row"><button class="big" style="width:160px" {0}>はい</button><button class="big sec" style="width:160px" {1}>いいえ</button></div>', act('yes'), act('no'))
          : T('<div class="cap">{0}さんが答えています…</div>', esc(nameOf(m.owner))); break;
      case 'rps':
        h += inIt && !done ? '<div class="rps">' + [['g', '✊', T('グー')], ['c', '✌', T('チョキ')], ['p', '✋', T('パー')]].map(function (x) { return '<button ' + act(x[0]) + '><span>' + x[1] + '</span>' + x[2] + '</button>'; }).join('') + '</div>'
          : '<div class="cap">' + (inIt ? T('出しました。みんなを待っています…') : T('じゃんけん中…')) + '</div>'; break;
      case 'fingers':
        h += inIt && !done ? '<div class="nums">' + [1, 2, 3, 4, 5].map(function (n) { return '<button ' + act(String(n)) + '>' + n + '</button>'; }).join('') + T('</div><div class="cap">キーボードの 1〜5 でもOK</div>')
          : '<div class="cap">' + (inIt ? T('{0}本を出しました。みんなを待っています…', m.myInput) : T('みんなが選んでいます…')) + '</div>'; break;
      case 'vote':
        h += inIt && !done ? '<div class="row">' + s.seats.filter(function (st) { return st.alive; }).map(function (st) { return '<button class="chip" ' + act(st.id) + '>' + esc(nameOf(st.id)) + (st.id === v.me ? T('（自分）') : '') + '</button>'; }).join('') + '</div>'
          : '<div class="cap">' + (inIt ? T('{0}さんに投票しました', esc(nameOf(m.myInput))) : T('投票中…')) + '</div>'; break;
      case 'present':
        h += inIt && !done ? T('<div class="row"><button class="big" style="width:180px" {0}>ほしい！</button><button class="big sec" style="width:180px" {1}>いらない</button></div>', act('want'), act('no'))
          : '<div class="cap">' + (inIt ? T('「{0}」と答えました', (m.myInput === 'want' ? T('ほしい') : T('いらない'))) : T('みんなの答えを待っています…')) + '</div>'; break;
      case 'nose': {
        const go = m.goInMs != null;
        if (go && !S.ui.goSeenAt) S.ui.goSeenAt = S.receivedAt;
        if (owner && !go) h += T('<div class="cap">好きなタイミングで合図しよう。合図は全員の画面に出ます</div><button class="push" {0}>合図！</button>', act('go'));
        else if (owner) h += T('<div class="cap">合図しました！ みんなの反応を待っています…</div>');
        else if (!go) h += T('<h2 style="color:var(--muted)">{0}さんの合図を待て…</h2>', esc(nameOf(m.owner)));
        else if (done) h += '<button class="push done" disabled>✓</button>';
        else h += T('<h2 style="color:var(--berry)">押せ！！</h2><button class="push" data-act="nose">PUSH</button><div class="cap" data-nose></div><div class="cap">スペースキーでもOK</div>');
        break;
      }
    }
    if (m.kind !== 'nose' && S.view.remainingMs != null) h += T('<div class="cap">残り <span data-timer></span>　答えた人 {0} / {1}人</div>', m.done.length, m.involved.length);
    return h + '</div>';
  }

  function arenaResult(v, s) {
    let h = '<img src="img/arena_hero.webp" style="width:100%;height:220px;object-fit:cover;border-radius:20px" alt="">';
    h += '<div class="word">' + (s.winners.length ? esc(s.winners.map(nameOf).join(T('、'))) + T('の勝ち！') : T('勝者なし！')) + '</div>';
    h += T('<div class="panel"><h3>最終結果</h3>{0}</div>', s.seats.slice().sort(function (a, b) { return (s.winners.indexOf(b.id) >= 0) - (s.winners.indexOf(a.id) >= 0); }).map(function (st) {
      const w = s.winners.indexOf(st.id) >= 0;
      return '<div class="row"><span class="grow">' + esc(nameOf(st.id)) + '</span><b style="color:' + (w ? 'var(--lantern)' : st.alive ? 'var(--mint)' : 'var(--muted)') + '">' + (w ? T('👑 優勝') : st.alive ? T('生き残り') : T('負け')) + '</b></div>';
    }).join(''));
    h += T('<div class="panel log"><h3>最後のできごと</h3>{0}</div>', s.log.slice(-6).map(function (l) { return '<div>' + esc(fmtLog(l)) + '</div>'; }).join(''));
    return h + nextButtons(v, T('もう一戦'));
  }

  // ---------- お絵かき伝言 ----------

  const DR = META.draw;
  const DRAW_MAX_BYTES = Math.floor(DR.maxChars / 4) * 3;
  function decodeDrawing(str) { try { return str ? JSON.parse(G.drawingDecode(str)) : []; } catch (e) { return []; } }
  function inkUsed(strokes) { return strokes.reduce(function (a, st) { return a + 4 + st[2].length; }, 0); }
  /** 線を画用紙（canvas）に描く */
  function paint(cv, strokes) {
    const ctx = cv.getContext('2d'), k = cv.width / DR.w;
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, cv.width, cv.height);
    ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    strokes.forEach(function (st) { paintStroke(ctx, k, st); });
  }
  function paintStroke(ctx, k, st) {
    const p = st[2], w = DR.widths[st[1]] * k;
    ctx.strokeStyle = ctx.fillStyle = DR.colors[st[0]];
    if (p.length === 2) { ctx.beginPath(); ctx.arc((p[0] + .5) * k, (p[1] + .5) * k, w / 2, 0, Math.PI * 2); ctx.fill(); return; }
    ctx.lineWidth = w; ctx.beginPath(); ctx.moveTo((p[0] + .5) * k, (p[1] + .5) * k);
    for (let i = 2; i < p.length; i += 2) ctx.lineTo((p[i] + .5) * k, (p[i + 1] + .5) * k);
    ctx.stroke();
  }
  function drawCanvas(id, strokesAttr, cls) {
    return '<canvas class="pad' + (cls ? ' ' + cls : '') + '" id="' + id + '" width="' + DR.w * 3 + '" height="' + DR.h * 3 + '"' + (strokesAttr != null ? ' data-drawing="' + esc(strokesAttr) + '"' : '') + '></canvas>';
  }
  function drawKey(s) { return s.step + ':' + s.kind + ':' + s.submitted; }
  function drawStatus(v, s) {
    const left = v.players.filter(function (p) { return s.done.indexOf(p.id) < 0; });
    return T('<div class="cap center">できた人 {0} / {1}人{2}</div>{3}', s.done.length, v.players.length, (left.length && left.length <= 3 ? T('（まだ：{0}）', esc(left.map(function (p) { return p.name; }).join(T('、')))) : ''), hostOnly(v, '', s.done.length < v.players.length ? T('<button class="btn" style="align-self:center" data-act="send" data-arg=\'{"t":"closeVote"}\'>待たずに次へ（まだの人は白紙）</button>') : ''));
  }
  function drawTask(v, s) {
    if (S.ui.drawStep !== s.step) {
      S.ui.drawStep = s.step; S.ui.strokes = []; S.ui.text = ''; S.ui.sentStep = -1;
      if (S.ui.color == null) { S.ui.color = 1; S.ui.width = 1; }
    }
    const title = { WRITE: T('お題を書こう'), DRAW: T('絵を描こう'), GUESS: T('この絵は何？') }[s.kind];
    let h = '<div id="drawRoot" class="stack" data-key="' + esc(drawKey(s)) + '">';
    h += '<div class="row">' + img('icon_draw', 48) + '<h2 class="grow">' + title + '<span class="cap">　' + (s.step + 1) + ' / ' + s.steps + '</span></h2><span class="timer" data-timer data-low="10000"></span></div>';
    if (s.kind === 'WRITE') {
      h += T('<div class="cap">次の人がこれを絵にします。おもしろい文を書こう！（例：{0}）</div>', esc(s.suggestion || ''));
    } else if (s.kind === 'DRAW') {
      h += T('<div class="panel prompt"><div class="cap">お題</div><div class="word s">{0}</div></div>', esc(s.text || ''));
    } else {
      h += T('<div class="cap">前の人が描いた絵です。何を描いたのか、文で答えよう</div>{0}', drawCanvas('seeCv', s.drawing || '', 'see'));
    }
    if (s.submitted) {
      h += T('<div class="panel center"><h3 style="color:var(--mint)">送りました！</h3><div class="cap">みんながそろうと次へ進みます</div></div>');
      if (s.kind === 'DRAW') h += drawCanvas('padCv', null, 'mine');
    } else if (s.kind === 'DRAW') {
      h += drawCanvas('padCv', null, 'live');
      h += '<div class="tools"><div class="palette">' + DR.colors.map(function (c, i) {
        return i === 0 ? '' : T('<button class="sw{0}" style="background:{1}" data-act="dcolor" data-arg="{2}" aria-label="色"></button>', (S.ui.color === i ? ' on' : ''), c, i);
      }).join('') + '</div><div class="row">' + DR.widths.map(function (w, i) {
        return '<button class="wd' + (S.ui.width === i ? ' on' : '') + '" data-act="dwidth" data-arg="' + i + '"><i style="width:' + (4 + w * 1.4) + 'px;height:' + (4 + w * 1.4) + 'px"></i></button>';
      }).join('') + T('<button class="btn{0}" data-act="dcolor" data-arg="0">消しゴム</button><button class="btn" data-act="dundo">1つ戻す</button><button class="btn" data-act="dclear">全部消す</button></div><div class="ink"><i id="inkBar"></i></div></div>', (S.ui.color === 0 ? ' on' : ''));
      h += T('<button class="big" data-act="dsend">できた！</button>');
    } else {
      h += '<input id="drawIn" class="text" maxlength="' + DR.maxText + '" placeholder="' + (s.kind === 'WRITE' ? T('お題を書く（{0}文字まで）', DR.maxText) : T('この絵は…')) + '" value="' + esc(S.ui.text) + '">';
      h += '<div class="row">' + (s.kind === 'WRITE' ? T('<button class="big sec grow" data-act="dsuggest">おまかせ</button>') : '') + T('<button class="big grow" data-act="dsend">これで決定</button></div>');
    }
    h += '<div id="drawStatus">' + drawStatus(v, s) + '</div></div>';
    return h;
  }
  function drawReveal(v, s) {
    let h = T('<div class="row">{0}<div class="grow"><h2>{1}さんのお題から</h2><div class="cap">{2} / {3}冊目　{4} / {5}コマ</div></div></div>', img('icon_draw', 48), esc(s.ownerName), (s.book + 1), s.books, s.entries.length, s.total);
    h += '<div class="album">' + s.entries.map(function (e, i) {
      const last = i === s.entries.length - 1;
      if (e.kind === 'DRAW') return T('<div class="page{0}"><div class="cap">{1}さんが描いた絵</div>{2}</div>', (last ? ' new' : ''), esc(e.byName), drawCanvas('rv' + i, e.drawing, 'see'));
      return '<div class="page' + (last ? ' new' : '') + '"><div class="cap">' + esc(e.byName) + (e.kind === 'WRITE' ? T('さんのお題') : T('さんの答え')) + '</div><div class="bubble">' + esc(e.text) + '</div></div>';
    }).join('') + '</div>';
    if (s.finished) return T('{0}<div class="cap center">全員の発表が終わりました！</div>{1}', h, nextButtons(v, T('もう一回')));
    const endOfBook = s.entries.length >= s.total;
    return h + hostOnly(v, T('ホストがめくるのを待っています…'),
      '<button class="big" data-act="next">' + (endOfBook ? (s.book + 1 < s.books ? T('次の人の流れへ') : T('発表おわり')) : T('次をめくる')) + '</button>');
  }
  /** 画面を作り直したあと、絵の canvas に中身を描き、描く用の canvas に操作をつける */
  function hydrateDraw() {
    document.querySelectorAll('canvas[data-drawing]').forEach(function (cv) { paint(cv, decodeDrawing(cv.dataset.drawing)); });
    const mine = document.getElementById('padCv');
    if (mine) { paint(mine, S.ui.strokes || []); if (mine.classList.contains('live')) attachPad(mine); updateInk(); }
    const nw = document.querySelector('.album .page.new');
    if (nw && S.ui.lastReveal !== nw.parentNode.children.length + ':' + S.view.screen.book) {
      S.ui.lastReveal = nw.parentNode.children.length + ':' + S.view.screen.book;
      try { nw.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch (e) {}
    }
  }
  function updateInk() {
    const bar = document.getElementById('inkBar'); if (!bar) return;
    const used = inkUsed(S.ui.strokes || []) / DRAW_MAX_BYTES;
    bar.style.width = Math.max(0, 100 - used * 100) + '%'; bar.classList.toggle('low', used > .85);
  }
  function attachPad(cv) {
    const ctx = cv.getContext('2d'), k = cv.width / DR.w;
    let cur = null;
    function pt(e) {
      const r = cv.getBoundingClientRect();
      return [Math.max(0, Math.min(DR.w - 1, Math.floor((e.clientX - r.left) / r.width * DR.w))), Math.max(0, Math.min(DR.h - 1, Math.floor((e.clientY - r.top) / r.height * DR.h)))];
    }
    function room() { return DRAW_MAX_BYTES - inkUsed(S.ui.strokes) - 6; }
    cv.addEventListener('pointerdown', function (e) {
      e.preventDefault(); if (room() <= 2) { toast(T('インクがなくなりました（「1つ戻す」で減らせます）')); return; }
      try { cv.setPointerCapture(e.pointerId); } catch (er) {}
      const p = pt(e); cur = [S.ui.color, S.ui.width, [p[0], p[1]]]; S.ui.strokes.push(cur);
      ctx.lineCap = 'round'; ctx.lineJoin = 'round'; paintStroke(ctx, k, cur);
    });
    cv.addEventListener('pointermove', function (e) {
      if (!cur) return;
      const evs = e.getCoalescedEvents ? e.getCoalescedEvents() : [e];
      for (const ev of (evs.length ? evs : [e])) {
        const p = pt(ev), a = cur[2], lx = a[a.length - 2], ly = a[a.length - 1];
        if (Math.abs(p[0] - lx) + Math.abs(p[1] - ly) < 2) continue;
        if (room() <= 2) { cur = null; updateInk(); return; }
        a.push(p[0], p[1]);
        ctx.strokeStyle = DR.colors[cur[0]]; ctx.lineWidth = DR.widths[cur[1]] * k;
        ctx.beginPath(); ctx.moveTo((lx + .5) * k, (ly + .5) * k); ctx.lineTo((p[0] + .5) * k, (p[1] + .5) * k); ctx.stroke();
      }
      updateInk();
    });
    function end() { cur = null; updateInk(); }
    cv.addEventListener('pointerup', end); cv.addEventListener('pointercancel', end); cv.addEventListener('pointerleave', function (e) { if (e.buttons === 0) end(); });
  }
  function drawSend() {
    const v = S.view, s = v && v.screen; if (!s || s.t !== 'drawTask' || s.submitted || S.ui.sentStep === s.step) return;
    S.ui.sentStep = s.step;
    if (s.kind === 'DRAW') send({ t: 'drawSubmit', step: s.step, drawing: G.drawingEncode(JSON.stringify(S.ui.strokes || [])) });
    else send({ t: 'drawSubmit', step: s.step, text: (S.ui.text || '').trim() });
  }

  // ================= 操作 =================

  document.addEventListener('input', function (e) {
    if (e.target.id === 'nameIn') {
      S.name = e.target.value.slice(0, 10); save('pg.name', S.name);
      document.querySelectorAll('[data-act=host],[data-act=toJoin]').forEach(function (b) { b.disabled = !S.name.trim(); });
    }
    if (e.target.id === 'codeIn') { e.target.value = e.target.value.replace(/\D/g, '').slice(0, 6); S.code = e.target.value; }
    if (e.target.id === 'syncIn') S.ui.draft = e.target.value.slice(0, 20);
    if (e.target.id && e.target.id.indexOf('scaleIn') === 0) { S.ui.hints = S.ui.hints || {}; S.ui.hints[e.target.id.slice(7)] = e.target.value.slice(0, 20); }
    if (e.target.id === 'drawIn') S.ui.text = e.target.value.slice(0, DR.maxText);
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && e.target.id === 'codeIn') joinRoom(e.target.value);
    if (e.key === 'Enter' && e.target.id === 'syncIn') doAct('syncSend');
    if (e.key === 'Enter' && e.target.id && e.target.id.indexOf('scaleIn') === 0 && !e.isComposing) doAct('scaleHint', e.target.id.slice(7));
    if (e.key === 'Enter' && e.target.id === 'drawIn' && !e.isComposing) doAct('dsend');
    const v = S.view; if (!v || v.screen.t !== 'arena' || !v.screen.mini || e.target.tagName === 'INPUT') return;
    const m = v.screen.mini;
    if (m.kind === 'fingers' && /^[1-5]$/.test(e.key) && m.involved.indexOf(v.me) >= 0 && m.myInput == null) send({ t: 'arenaInput', value: e.key });
    if (m.kind === 'nose' && e.code === 'Space') { e.preventDefault(); if (m.owner === v.me && m.goInMs == null) send({ t: 'arenaInput', value: 'go' }); else doAct('nose'); }
  });
  document.addEventListener('click', function (e) {
    const el = e.target.closest('[data-act]'); if (!el || el.disabled) return;
    if (el.dataset.act === 'nose') return;   // 指が触れた時点（pointerdown）で処理ずみ
    if (el.dataset.act !== 'sound' && el.dataset.act !== 'music') sfx('tap');
    doAct(el.dataset.act, el.dataset.arg);
  });

  function doAct(act, arg) {
    const v = S.view;
    switch (act) {
      case 'host': if (S.name.trim()) hostRoom(); break;
      case 'toJoin': if (S.name.trim()) { S.page = 'join'; S.busy = false; render(); setTimeout(function () { const c = document.getElementById('codeIn'); if (c) c.focus(); }, 0); } break;
      case 'join': joinRoom(document.getElementById('codeIn').value); break;
      case 'back': resetNet(); S.page = 'title'; S.busy = false; render(); break;
      case 'leave': confirmBox(v && v.isHost ? T('部屋を閉じますか？') : T('部屋を出ますか？'), v && v.isHost ? T('全員の接続が切れます') : '', T('出る'), leave); break;
      case 'share': {
        const url = /^https?:/.test(location.protocol) ? location.href.split('#')[0] : '';
        navigator.share({ title: T('わちゃパ'), text: T('わちゃパで遊ぼう！ 「部屋に参加する」で部屋コード {0} を入れてね', v.roomCode), url: url || undefined }).catch(function () {});
        break;
      }
      case 'copy': try { navigator.clipboard.writeText(v.roomCode); toast(T('コピーしました')); } catch (err) { toast(v.roomCode); } break;
      case 'kick': send({ t: 'kick', target: arg }); break;
      case 'game': send({ t: 'settings', settings: Object.assign({}, v.settings, { game: arg }) }); break;
      case 'set': send({ t: 'settings', settings: Object.assign({}, v.settings, JSON.parse(arg)) }); break;
      case 'start': send({ t: 'start' }); break;
      case 'next': send({ t: 'next' }); break;
      case 'lobby': send({ t: 'lobby' }); break;
      case 'send': send(JSON.parse(arg)); break;
      case 'lang':
        openModal('<div class="box"><h2>Language / 言語</h2><div class="stack">' + LANGS.map(function (l) {
          return '<button class="big' + (l[0] === LANG ? '' : ' sec') + '" data-act="setLang" data-arg="' + l[0] + '">' + (l[0] === LANG ? '✓ ' : '') + l[1] + '</button>';
        }).join('') + '</div><div class="cap">' + T('お題やカードは、部屋を作った人の言語になります') + '</div><button class="big sec" data-act="closeModal">' + T('閉じる') + '</button></div>');
        break;
      case 'setLang': closeModal(); setLang(arg); break;
      case 'sound': setSound(!SND.on); break;
      case 'music': setMusic(!MUS.on); break;
      case 'closeModal': closeModal(); break;
      case 'peek': S.ui.peek = !S.ui.peek; render(); break;
      case 'ngOut': {
        const e2 = v.screen.entries.find(function (x) { return x.id === arg; });
        confirmBox(T('{0}さんがアウト？', nameOf(arg)), T('「{0}」と言いましたか？', (e2 && e2.word)), T('言った！'), function () { send({ t: 'out', target: arg }); });
        break;
      }
      case 'scaleHint': {
        const uid = +arg; const t = ((S.ui.hints || {})[uid] || '').trim();
        if (t) send({ t: 'scaleHint', uid: uid, text: t }); else toast(T('ヒントを書いてね'));
        break;
      }
      case 'syncSend': { const t = (S.ui.draft || '').trim(); if (t) send({ t: 'syncAnswer', text: t }); break; }
      case 'pick': { const uid = Number(arg); S.ui.pick = S.ui.pick === uid ? null : uid; S.ui.target = null; render(); break; }
      case 'target': S.ui.target = arg; render(); break;
      case 'play': if (S.ui.pick != null) { send({ t: 'arenaPlay', uid: S.ui.pick, target: S.ui.target || null }); S.ui.pick = null; S.ui.target = null; } break;
      case 'input': send({ t: 'arenaInput', value: arg }); break;
      case 'dcolor': S.ui.color = Number(arg); document.querySelectorAll('.sw,.tools .btn').forEach(function (b) { b.classList.toggle('on', b.dataset.act === 'dcolor' && Number(b.dataset.arg) === S.ui.color); }); break;
      case 'dwidth': S.ui.width = Number(arg); document.querySelectorAll('.wd').forEach(function (b) { b.classList.toggle('on', Number(b.dataset.arg) === S.ui.width); }); break;
      case 'dundo': S.ui.strokes.pop(); paint(document.getElementById('padCv'), S.ui.strokes); updateInk(); break;
      case 'dclear': if (S.ui.strokes.length) confirmBox(T('全部消しますか？'), T('描いた絵がすべて消えます'), T('消す'), function () { S.ui.strokes = []; paint(document.getElementById('padCv'), S.ui.strokes); updateInk(); }); break;
      case 'dsuggest': { const s2 = v.screen; S.ui.text = s2.suggestion || ''; const el = document.getElementById('drawIn'); if (el) el.value = S.ui.text; break; }
      case 'dsend': {
        const s2 = v.screen;
        if (s2.kind !== 'DRAW' && !(S.ui.text || '').trim()) { toast(s2.kind === 'WRITE' ? T('お題を書くか「おまかせ」を押してね') : T('答えを書いてね')); break; }
        if (s2.kind === 'DRAW' && !(S.ui.strokes || []).length) { confirmBox(T('何も描いていません'), T('このまま白紙で送りますか？'), T('白紙で送る'), drawSend); break; }
        drawSend(); break;
      }
      case 'nose': {
        const m = v && v.screen.mini;
        if (m && m.kind === 'nose' && m.goInMs != null && m.myInput == null && m.owner !== v.me) send({ t: 'arenaInput', value: 'press:' + Math.round(performance.now() - S.ui.goSeenAt) });
        break;
      }
      case 'report': {
        const s = v.screen;
        const names = s.rules.filter(function (r) { return CARDS[r.id].fx === 'RULE_SOCIAL'; }).map(function (r) { return T('「{0}」', CARDS[r.id].name); });
        if (s.chaos && META.chaos[s.chaos].social) names.push(T('今夜のルール「{0}」', META.chaos[s.chaos].title));
        confirmBox(T('{0}さんがルール違反？', nameOf(arg)), T('{0}を破りましたか？ 報告するとその人は負けになります（お守りがあれば守られます）', names.join('・')), T('違反！'), function () { send({ t: 'arenaReport', target: arg }); });
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

  window.addEventListener('beforeunload', function () { if (S.host && S.connected.size) PgNet.send(Array.from(S.connected), JSON.stringify({ t: 'bye', reason: T('ホストが部屋を閉じました') })); });
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
  if ('serviceWorker' in navigator && /^https?:/.test(location.protocol)) {
    // 新しい版が届いたら、遊んでいないとき（タイトル画面）だけ自動で読み直す
    const hadSw = !!navigator.serviceWorker.controller;
    navigator.serviceWorker.addEventListener('controllerchange', function () { if (hadSw && S.page === 'title' && !S.view) location.reload(); });
    navigator.serviceWorker.register('sw.js', { updateViaCache: 'none' }).catch(function () {});
  }

  // テスト用の入口
  window.PG = { S: S, send: send, doAct: doAct, render: render, MUS: MUS };
  render();
})();
