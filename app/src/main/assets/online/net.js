/*
 * オンライン対戦の通信（PeerJS = ブラウザどうしの P2P）。Android アプリと PC 版で同じこのファイルを使う。
 * サーバーは持たない。相手を見つけるときだけ PeerJS の無料の仲介サーバーを使い、
 * つながったあとのデータは端末どうしで直接（直接つながらない回線では PeerJS の中継）やりとりする。
 *
 * 使い方：PgNet.onEvent = (ev) => {...} を決めてから、PgNet.host(proto, code, name) か PgNet.join(proto, code, name)
 * 出てくるできごと：
 *   {ev:'hosting'}                       部屋を開いた
 *   {ev:'taken'}                         その部屋コードは使われていた（別のコードで開き直す）
 *   {ev:'join', id, name, proto}         ホスト：参加の申し込み（accept / reject で答える）
 *   {ev:'connected', id}                 つながった（参加者側の id は 'host'）
 *   {ev:'failed', reason}                参加できなかった（notfound / rejected / network）
 *   {ev:'data', id, text}                データが届いた
 *   {ev:'closed', id}                    相手との接続が切れた
 *   {ev:'error', message}
 */
(function () {
  'use strict';
  // 生きている合図を4秒ごとに送り、30秒何も届かなければ切れたとみなす（タブが裏にあっても大丈夫な長さ）
  const PING_MS = 4000, DEAD_MS = 30000;
  const conns = new Map();      // id -> DataConnection
  const lastSeen = new Map();   // id -> 最後に何か届いた時刻
  let peer = null, isHost = false, pingTimer = null, joinTimer = null;

  const net = {
    onEvent: function () {},
    /** テスト用に仲介サーバーを差しかえる（{host, port, path, secure}） */
    server: null,
  };
  function emit(e) { try { net.onEvent(e); } catch (err) { console.error(err); } }
  function peerId(proto, code) { return 'zpg' + proto + '-' + code; }
  function opts() { return Object.assign({ debug: 0 }, net.server || {}); }

  function track(id, c) {
    conns.set(id, c); lastSeen.set(id, Date.now());
    c.on('data', function (d) {
      lastSeen.set(id, Date.now());
      if (d === '__ping') return;
      if (d === '__reject') { emit({ ev: 'failed', reason: 'rejected' }); drop(id, false); return; }
      emit({ ev: 'data', id: id, text: String(d) });
    });
    c.on('close', function () { if (conns.get(id) === c) drop(id, true); });
    c.on('error', function () { if (conns.get(id) === c) drop(id, true); });
  }
  function drop(id, notify) {
    const c = conns.get(id);
    conns.delete(id); lastSeen.delete(id);
    if (c) { try { c.close(); } catch (e) {} }
    if (notify) emit({ ev: 'closed', id: id });
  }
  function startPing() {
    stopPing();
    pingTimer = setInterval(function () {
      const now = Date.now();
      conns.forEach(function (c, id) {
        if (c.open) { try { c.send('__ping'); } catch (e) {} }
        if (now - (lastSeen.get(id) || now) > DEAD_MS) drop(id, true);
      });
    }, PING_MS);
  }
  function stopPing() { if (pingTimer) clearInterval(pingTimer); pingTimer = null; }

  /** 部屋を開く（ホスト） */
  net.host = function (proto, code, name) {
    net.stop(); isHost = true;
    peer = new Peer(peerId(proto, code), opts());
    peer.on('open', function () { emit({ ev: 'hosting' }); });
    peer.on('connection', function (c) {
      const id = c.peer;
      c.on('open', function () {
        track(id, c);
        const m = c.metadata || {};
        emit({ ev: 'join', id: id, name: String(m.name || 'ゲスト').slice(0, 10), proto: Number(m.proto || -1) });
      });
    });
    peer.on('disconnected', function () { if (peer && !peer.destroyed) setTimeout(function () { try { peer.reconnect(); } catch (e) {} }, 1500); });
    peer.on('error', function (e) {
      if (e.type === 'unavailable-id') emit({ ev: 'taken' });
      else if (e.type === 'network' || e.type === 'server-error' || e.type === 'socket-error' || e.type === 'socket-closed') emit({ ev: 'error', message: 'network' });
      else if (e.type !== 'peer-unavailable') emit({ ev: 'error', message: String(e.type || e) });
    });
    startPing();
  };
  /** ホスト：申し込みを受ける */
  net.accept = function (id) { if (conns.has(id)) emit({ ev: 'connected', id: id }); };
  /** ホスト：申し込みを断る */
  net.reject = function (id) {
    const c = conns.get(id);
    if (c) { try { c.send('__reject'); } catch (e) {} setTimeout(function () { drop(id, false); }, 400); }
  };

  /** 部屋に入る（参加者） */
  net.join = function (proto, code, name) {
    net.stop(); isHost = false;
    peer = new Peer(opts());
    let done = false;
    function fail(reason) { if (done) return; done = true; emit({ ev: 'failed', reason: reason }); net.stop(); }
    joinTimer = setTimeout(function () { fail('network'); }, 20000);
    peer.on('open', function () {
      const c = peer.connect(peerId(proto, code), { reliable: true, metadata: { name: name, proto: proto } });
      c.on('open', function () {
        if (done) return; done = true; clearTimeout(joinTimer);
        track('host', c); startPing(); emit({ ev: 'connected', id: 'host' });
      });
    });
    peer.on('error', function (e) {
      if (e.type === 'peer-unavailable') fail('notfound');
      else if (!done) fail('network');
    });
  };

  net.send = function (ids, text) {
    ids.forEach(function (id) { const c = conns.get(id); if (c && c.open) { try { c.send(text); } catch (e) {} } });
  };
  net.disconnect = function (id) { drop(id, false); };
  net.stop = function () {
    stopPing(); if (joinTimer) clearTimeout(joinTimer); joinTimer = null;
    conns.forEach(function (c) { try { c.close(); } catch (e) {} });
    conns.clear(); lastSeen.clear();
    if (peer) { try { peer.destroy(); } catch (e) {} }
    peer = null;
  };
  window.PgNet = net;
})();
