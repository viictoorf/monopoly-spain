'use strict';
/* Monopoly España: front end. The server owns the game; this file only draws the state it sends and posts actions. */

const $ = (s, r = document) => r.querySelector(s);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const eur = n => n + ' €';

/** Tiny element builder: h('div', {class: 'x', onclick: fn}, child, child...) */
function h(tag, attrs = {}, ...kids) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v == null || v === false) continue;
    if (k === 'class') n.className = v;
    else if (k === 'style') n.style.cssText = v;
    else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
    else n.setAttribute(k, v === true ? '' : v);
  }
  for (const c of kids.flat(Infinity)) if (c != null && c !== false) n.append(c.nodeType ? c : String(c));
  return n;
}

const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch { /* private mode: ignore */ } },
};

const AVATARS = {
  COCHE: ['🚗', 'Coche'], PELOTA: ['⚽', 'Balón'], SOMBRERO: ['🎩', 'Sombrero'], ESFINGE: ['🦁', 'Esfinge'],
  TORO: ['🐂', 'Toro'], PAELLA: ['🥘', 'Paella'], FLAMENCA: ['💃', 'Flamenca'], PULPO: ['🐙', 'Pulpo'],
};
const HUMAN_NAMES = ['Paco', 'Lola', 'Manolo', 'Carmen', 'Pepe', 'Marisol', 'Antonio', 'Pilar'];
const BOT_NAMES = ['El cuñado', 'La vecina del 5º', 'Don Anselmo', 'Tía Mari', 'Paco el del bar', 'Doña Rosa'];
const ICONS = { go: '🏁', jail: '🔒', parking: '😴', gotojail: '🚓', chance: '🍀', chest: '🏢', station: '🚆', tax: '💶' };

let S = null;               // last state received from the server
let shown = [];             // where each token is drawn (it walks towards the real position)
let locking = false;        // true while a state is being animated and drawn
let pending = null;         // newest state that arrived meanwhile
let botTimer = 0;
let overShown = false;
let sound = store.get('sound') !== 'off';
let setupRows = null;
let setupOpts = { rounds: '0', house: false };
let savedGame = false;
let canReturn = false;      // the player left a running game on purpose to prepare another one
let leaveSetup = false;     // set when this player starts or loads a game: only then may the game screen come back

// ------------------------------------------------------------------ server calls

let seqIssued = 0;          // every request gets a number: an older response must never overwrite a newer state
let seqApplied = 0;

async function call(path, body) {
  const seq = ++seqIssued;
  const opts = body ? {
    method: 'POST',
    headers: { 'X-Requested-With': 'monopoly', 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(body),
  } : {};
  const r = await fetch(path, opts);
  const d = await r.json();
  if (!r.ok) throw new Error(d.error || 'Error');
  d.seq = seq;
  return d;
}

async function post(path, body = {}) {
  try { await update(await call(path, body)); } catch (e) { toast(e.message, true); }
}

const act = a => post('/api/action', { a });

function toast(msg, bad = false) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = 'show' + (bad ? ' bad' : '');
  clearTimeout(toast.t);
  toast.t = setTimeout(() => { t.className = ''; }, bad ? 3600 : 2200);
}

// ------------------------------------------------------------------ sound (tiny WebAudio beeps, no files)

let audio;
function tone(freq, dur = .12, type = 'sine', gain = .06, delay = 0) {
  if (!sound) return;
  try {
    audio = audio || new AudioContext();
    const t = audio.currentTime + delay;
    const o = audio.createOscillator();
    const g = audio.createGain();
    o.type = type; o.frequency.value = freq;
    g.gain.setValueAtTime(gain, t);
    g.gain.exponentialRampToValueAtTime(.0001, t + dur);
    o.connect(g); g.connect(audio.destination);
    o.start(t); o.stop(t + dur);
  } catch { /* no audio available */ }
}
const sfx = {
  dice() { for (let i = 0; i < 5; i++) tone(180 + Math.random() * 160, .05, 'square', .035, i * .06); },
  coin() { tone(880, .08, 'triangle'); tone(1320, .16, 'triangle', .06, .08); },
  buy() { tone(523, .1, 'triangle'); tone(659, .1, 'triangle', .06, .1); tone(784, .2, 'triangle', .06, .2); },
  jail() { tone(120, .5, 'sawtooth', .07); },
  win() { [523, 659, 784, 1046].forEach((f, i) => tone(f, .25, 'triangle', .07, i * .14)); },
};

function effects(prev) {
  const line = S.log[S.log.length - 1] || '';
  if (!prev || !prev.started || prev.rev === S.rev) return;
  if (S.rolls !== prev.rolls) sfx.dice();
  if (S.phase === 'OVER' && prev.phase !== 'OVER') sfx.win();
  else if (/cárcel/.test(line)) sfx.jail();
  else if (/compra|se lleva|trato hecho/i.test(line)) sfx.buy();
  else if (/cobra|nómina|devolución|herencia|pedrea|quiniela/i.test(line)) sfx.coin();
}

// ------------------------------------------------------------------ state flow

async function update(next) {
  if (next.seq < seqApplied) return;
  seqApplied = next.seq;
  if (next.same) return;
  if (locking) { pending = next; return; }
  locking = true;
  try {
    const prev = S;
    S = next;
    if (!S.started) { openSetup(false); return; }
    if (canReturn && !leaveSetup) return;      // do not drag them back to the old game
    leaveSetup = false;
    canReturn = false;
    showGame();
    renderCenter();
    await animate(prev);
    render();
    effects(prev);
  } finally {
    locking = false;
    if (pending) { const p = pending; pending = null; update(p); } else maybeBot();
  }
}

/** Walks the tokens square by square towards their real positions. */
async function animate(prev) {
  const fresh = !prev || !prev.started || prev.players.length !== S.players.length || shown.length !== S.players.length;
  if (fresh) { shown = S.players.map(p => p.pos); return; }
  for (let i = 0; i < S.players.length; i++) {
    const to = S.players[i].pos;
    if (!Number.isInteger(shown[i])) shown[i] = to;     // the list may have been reset meanwhile (new game)
    const jailed = S.players[i].jail && !prev.players[i].jail;
    const fwd = (to - shown[i] + 40) % 40;
    const back = (shown[i] - to + 40) % 40;
    let dir = 0;
    if (!jailed && fwd > 0 && fwd <= 12) dir = 1;
    else if (!jailed && back > 0 && back <= 4) dir = -1;
    for (let guard = 0; dir && shown[i] !== to && guard < 14; guard++) {
      shown[i] = (shown[i] + dir + 40) % 40;
      renderBoard();
      await sleep(110);
    }
    shown[i] = to;
  }
}

function maybeBot() {
  clearTimeout(botTimer);
  if (!S || !S.started || S.phase === 'OVER' || !S.host || document.body.dataset.view !== 'game') return;
  const actor = S.players[S.actor];
  if (actor && actor.bot) botTimer = setTimeout(() => post('/api/action', { a: 'bot' }), 850);
}

async function poll() {
  if (locking) return;
  try { await update(await call('/api/state?rev=' + (S ? S.rev : -1))); } catch { /* server busy or gone: try again */ }
}

// ------------------------------------------------------------------ setup screen

function defaultName(bot, i) {
  const pool = bot ? BOT_NAMES : HUMAN_NAMES;
  const taken = new Set(setupRows.filter((_, k) => k !== i).map(r => r.name));
  return pool.find(n => !taken.has(n)) || pool[0] + ' ' + (i + 1);
}

function nextAvatar(i) {
  const keys = Object.keys(AVATARS);
  const used = new Set(setupRows.filter((_, k) => k !== i).map(r => r.avatar));
  const start = keys.indexOf(setupRows[i].avatar);
  for (let n = 1; n <= keys.length; n++) {
    const k = keys[(start + n) % keys.length];
    if (!used.has(k)) return k;
  }
  return setupRows[i].avatar;
}

function openSetup(fromGame) {
  canReturn = fromGame;
  savedGame = S && !S.started ? S.saved : savedGame;
  document.body.dataset.view = 'setup';
  $('#setup').hidden = false;
  $('#game').hidden = true;
  for (const id of ['bUndo', 'bFinish', 'bNew']) $('#' + id).hidden = true;
  if (!setupRows) {
    setupRows = [
      { name: HUMAN_NAMES[0], avatar: 'TORO', bot: false },
      { name: HUMAN_NAMES[1], avatar: 'PAELLA', bot: false },
    ];
  }
  drawSetup();
}

function drawSetup() {
  const rows = $('#rows');
  rows.replaceChildren(...setupRows.map((r, i) => h('div', { class: 'prow' },
    h('button', { class: 'pick', title: 'Cambiar ficha: ' + AVATARS[r.avatar][1], 'aria-label': 'Cambiar ficha, ahora ' + AVATARS[r.avatar][1],
      onclick: () => { r.avatar = nextAvatar(i); drawSetup(); } }, AVATARS[r.avatar][0]),
    h('input', { type: 'text', value: r.name, maxlength: 16, 'aria-label': 'Nombre del jugador ' + (i + 1),
      oninput: e => { r.name = e.target.value.replace(/[^\p{L}\p{N} ._-]/gu, ''); if (r.name !== e.target.value) e.target.value = r.name; } }),
    h('button', { class: 'tgl', 'aria-pressed': String(r.bot), title: r.bot ? 'Juega el ordenador' : 'Juega una persona', 'aria-label': 'Jugador automático',
      onclick: () => {
        const wasDefault = HUMAN_NAMES.includes(r.name) || BOT_NAMES.includes(r.name);
        r.bot = !r.bot;
        if (wasDefault) r.name = defaultName(r.bot, i);
        drawSetup();
      } }, '🤖'),
    setupRows.length > 2 ? h('button', { class: 'del', 'aria-label': 'Quitar jugador', onclick: () => { setupRows.splice(i, 1); drawSetup(); } }, '✕') : null)));
  $('#bAdd').hidden = setupRows.length >= 6;
  $('#rounds').value = setupOpts.rounds;
  $('#house').checked = setupOpts.house;
  const load = $('#bLoad');
  load.hidden = !savedGame;
  if (savedGame) load.textContent = `Continuar la partida guardada (${savedGame.players.join(', ')} · ronda ${savedGame.round})`;
  $('#bBack').hidden = !canReturn;
}

function startNewGame() {
  setupOpts.rounds = $('#rounds').value;
  setupOpts.house = $('#house').checked;
  const players = setupRows.map(r => `${r.name.trim()}|${r.avatar}|${r.bot ? 1 : 0}`).join(';');
  overShown = false;
  shown = [];
  leaveSetup = true;
  post('/api/new', { players, rounds: setupOpts.rounds, house: setupOpts.house ? '1' : '0' });
}

// ------------------------------------------------------------------ rendering

function showGame() {
  document.body.dataset.view = 'game';
  $('#setup').hidden = true;
  $('#game').hidden = false;
}

function render() {
  $('#bUndo').hidden = !(S.host && S.canUndo);
  $('#bFinish').hidden = !(S.phase === 'ROLL' || S.phase === 'MANAGE');
  $('#bNew').hidden = false;
  renderCenter();
  renderBoard();
  renderTurn();
  renderPlayers();
  renderMine();
  $('#log').replaceChildren(...S.log.slice().reverse().slice(0, 16).map(l => h('li', {}, l)));
  if (S.phase === 'OVER' && !overShown) { overShown = true; showOver(); }
  if (S.phase !== 'OVER') overShown = false;
}

const av = p => AVATARS[p.avatar][0];
const token = (p, big = false, on = false) => h('span', { class: 'token' + (big ? ' big' : '') + (on ? ' on' : ''), style: '--c:' + p.color, title: p.name }, av(p));

function cellOf(i) {
  if (i === 0) return [11, 11];
  if (i < 10) return [11, 11 - i];
  if (i === 10) return [11, 1];
  if (i < 20) return [11 - (i - 10), 1];
  if (i === 20) return [1, 1];
  if (i < 30) return [1, 1 + (i - 20)];
  if (i === 30) return [1, 11];
  return [1 + (i - 30), 11];
}
const sideOf = i => (i % 10 === 0 ? 'c' : i < 10 ? 'b' : i < 20 ? 'l' : i < 30 ? 't' : 'r');

function squareEl(sq) {
  const [r, c] = cellOf(sq.i);
  const owner = sq.owner >= 0 ? S.players[sq.owner] : null;
  const n = h('div', {
    class: `sq ${sq.kind} s-${sideOf(sq.i)}${sq.mortgaged ? ' mort' : ''}`, role: 'button', tabindex: '0', 'aria-label': sq.name,
    style: `grid-area:${r}/${c}`, onclick: () => showInfo(sq.i),
    onkeydown: e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); showInfo(sq.i); } },
  });
  if (owner) n.style.boxShadow = `inset 0 0 0 3px ${owner.color}`;
  const name = h('div', { class: 'nm' }, h('span', { class: 'full' }, sq.name), h('span', { class: 'short' }, sq.short));
  const priceLine = owner
    ? h('div', { class: 'pr' }, av(owner) + (sq.mortgaged ? '🔒' : ''))
    : sq.price ? h('div', { class: 'pr' }, h('span', { class: 'amount' }, eur(sq.price))) : sq.amount ? h('div', { class: 'pr' }, h('span', { class: 'amount' }, eur(sq.amount))) : null;
  if (sq.kind === 'street') {
    const band = h('div', { class: 'band', style: 'background:' + sq.color });
    if (sq.houses === 5) band.append(h('i', { class: 'hotel' }));
    else for (let k = 0; k < sq.houses; k++) band.append(h('i', { class: 'house' }));
    if (sq.pool) band.append(h('span', { class: 'extra' }, '🏊'));
    if (sq.court) band.append(h('span', { class: 'extra' }, '🎾'));
    n.append(band, h('div', { class: 'inner' }, name, priceLine));
  } else {
    const icon = sq.kind === 'utility' ? (sq.short === 'La Luz' ? '💡' : '🚰') : ICONS[sq.kind];
    n.append(h('div', { class: 'inner' }, h('div', { class: 'ico' }, icon), name, priceLine));
  }
  const tokens = S.players.map((p, i) => (!p.bankrupt && shown[i] === sq.i ? token(p, false, i === S.current) : null)).filter(Boolean);
  if (tokens.length) n.append(h('div', { class: 'tk' }, tokens));
  return n;
}

function renderBoard() {
  $('#board').replaceChildren($('#center'), ...S.squares.map(squareEl));
}

function renderCenter() {
  const dice = document.querySelectorAll('#center .die');
  if (S.dice && $('#center').dataset.rolls !== String(S.rolls)) {
    const first = $('#center').dataset.rolls === undefined;
    dice.forEach((d, i) => {
      d.textContent = String.fromCodePoint(0x267F + S.dice[i]);
      d.setAttribute('aria-label', 'Dado: ' + S.dice[i]);
      d.classList.remove('roll');
      if (!first) { void d.offsetWidth; d.classList.add('roll'); }
    });
    $('#center').dataset.rolls = String(S.rolls);
  }
  const cur = S.players[S.current];
  $('#cturn').textContent = S.phase === 'OVER' ? 'Fin de la partida' : `${av(cur)} ${cur.name}`;
  const limit = S.maxRounds ? ` de ${S.maxRounds}` : '';
  $('#cinfo').textContent = `Ronda ${S.round}${limit} · 🏠 ${S.housesLeft} · 🏨 ${S.hotelsLeft}${S.house ? ' · reglas de casa' : ''}`;
}

const btn = (label, cls, fn, disabled = false) => h('button', { class: 'btn ' + cls, onclick: fn, disabled }, label);
const sqName = i => S.squares[i].name;

function listText(cash, props, cards) {
  const parts = [];
  if (cash) parts.push(eur(cash));
  props.forEach(i => parts.push(sqName(i)));
  if (cards) parts.push(cards + (cards === 1 ? ' carta de la cárcel' : ' cartas de la cárcel'));
  return parts.length ? parts.join(', ') : 'nada';
}

function renderTurn() {
  const t = $('#turn');
  const cur = S.players[S.current];
  const actor = S.players[S.actor];
  const kids = [];
  const last = () => h('p', { class: 'last' }, S.log[S.log.length - 1]);

  if (S.phase === 'OVER') {
    const w = S.players[S.winner];
    kids.push(h('h2', { class: 'who' }, token(w, true), `Gana ${w.name}`),
      h('div', { class: 'btns' }, btn('Ver estadísticas', 'pri', showOver), btn('Nueva partida', 'sec', () => openSetup(false))));
    t.replaceChildren(...kids);
    return;
  }

  kids.push(h('h2', { class: 'who' }, token(actor, true), S.actor === S.current ? `Turno de ${cur.name}` : `Le toca a ${actor.name}`));

  if (actor.bot) {
    kids.push(h('p', { class: 'wait' }, `${actor.name} está pensando…`), last());
  } else if (S.trade) {
    const tr = S.trade;
    const from = S.players[tr.from];
    kids.push(h('div', { class: 'trade-box' },
      h('p', {}, `${from.name} te propone un trato:`),
      h('div', { class: 'side' }, h('strong', {}, 'Te da: '), listText(tr.cashGive, tr.give, tr.cardsGive)),
      h('div', { class: 'side' }, h('strong', {}, 'Te pide: '), listText(tr.cashAsk, tr.ask, tr.cardsAsk))),
      h('div', { class: 'btns' }, btn('Aceptar', 'pri', () => act('accept')), btn('Rechazar', 'sec', () => act('reject'))),
      h('p', { class: 'last' }, h('button', { class: 'link', onclick: () => act('cancel') }, `Retirar la oferta (${from.name})`)));
  } else if (S.phase === 'DEBT') {
    const d = S.debt;
    const debtor = S.players[d.debtor];
    const enough = debtor.cash >= d.amount;
    kids.push(h('p', {}, `${debtor.name} debe ${eur(d.amount)} (${d.reason})${d.creditor >= 0 ? ' a ' + S.players[d.creditor].name : ' al banco'}.`),
      h('div', { class: 'bar' }, h('i', { style: `width:${Math.min(100, Math.round(100 * debtor.cash / d.amount))}%` })),
      h('p', { class: 'wait' }, enough ? 'Ya tienes dinero para pagar.' : `Tienes ${eur(debtor.cash)}. Vende edificios o hipoteca propiedades abajo.`),
      h('div', { class: 'btns' }, btn(`Pagar ${eur(d.amount)}`, 'pri', () => act('paydebt'), !enough),
        btn('Declararme en bancarrota', 'dng', () => { if (confirm('¿Seguro? Pierdes todo y quedas fuera de la partida.')) act('bankrupt'); })));
  } else if (S.phase === 'AUCTION') {
    kids.push(...auctionBox(actor));
  } else if (S.phase === 'BUY') {
    const sq = S.squares[S.pending];
    kids.push(h('div', { class: 'lot' }, h('span', { class: 'chip', style: 'background:' + (sq.color || 'var(--tinta-2)') }),
      h('div', {}, h('strong', {}, sq.name), h('span', { class: 'stat' }, sq.blurb))),
      h('div', { class: 'btns' }, btn(`Comprar por ${eur(sq.price)}`, 'pri', () => act('buy'), cur.cash < sq.price),
        btn('No comprar (a subasta)', 'sec', () => act('decline'))), last());
  } else if (S.phase === 'ROLL') {
    if (cur.jail) {
      kids.push(h('p', {}, 'Estás en la cárcel: saca dobles, paga la fianza o usa el enchufe.'),
        h('div', { class: 'btns' }, btn('Intentar sacar dobles', 'pri', () => act('roll')),
          btn('Pagar la fianza (50 €)', 'sec', () => act('jailfine')),
          cur.jailCards ? btn('Usar el enchufe', 'sec', () => act('jailcard')) : null));
    } else {
      kids.push(btn('🎲 Tirar los dados', 'pri big', () => act('roll')), last());
    }
    kids.push(tradeButton());
  } else {
    kids.push(btn('Terminar turno', 'pri big', () => act('end')), last(), tradeButton());
  }
  t.replaceChildren(...kids.filter(Boolean));
}

function tradeButton() {
  return h('div', { class: 'btns' }, btn('🤝 Proponer un trato', 'sec', openTrade));
}

function auctionBox(actor) {
  const a = S.auction;
  const sq = S.squares[a.square];
  const high = a.highBidder >= 0 ? S.players[a.highBidder] : null;
  const input = h('input', { type: 'number', min: Math.max(10, a.highest + 1), max: actor.cash, step: 10, value: Math.min(actor.cash, Math.max(10, a.highest + 10)), 'aria-label': 'Tu puja en euros' });
  const quick = [10, 50, 100].map(inc => btn('+' + inc, 'sec', () => act('bid:' + (a.highest + inc)), a.highest + inc > actor.cash));
  return [
    h('div', { class: 'lot' }, h('span', { class: 'chip', style: 'background:' + (sq.color || 'var(--tinta-2)') }),
      h('div', {}, h('strong', {}, `Subasta: ${sq.name}`), h('span', { class: 'stat' }, `Precio de lista ${eur(sq.price)}`))),
    h('p', {}, high ? `Puja más alta: ${eur(a.highest)} (${high.name}).` : 'Aún nadie ha pujado (mínimo 10 €).'),
    h('p', { class: 'wait' }, `Aún en la subasta: ${a.bidders.map(i => S.players[i].name).join(', ')}.`),
    h('div', { class: 'btns' }, quick),
    h('div', { class: 'bid-row' }, input, btn('Pujar', 'pri', () => act('bid:' + (parseInt(input.value, 10) || 0)))),
    h('div', { class: 'btns' }, btn('Me retiro', 'dng', () => act('pass'))),
  ];
}

function renderPlayers() {
  const box = $('#players');
  box.replaceChildren(h('h2', {}, 'Jugadores'), ...S.players.map((p, i) => {
    const chips = [];
    if (p.bot) chips.push('🤖');
    if (p.jail) chips.push('🔒 en la cárcel');
    if (p.skip) chips.push(`🔧 taller ×${p.skip}`);
    if (p.jailCards) chips.push(`🎫 ×${p.jailCards}`);
    if (p.bankrupt) chips.push('en la ruina');
    return h('div', { class: 'pl' + (i === S.current ? ' cur' : '') + (i === S.actor ? ' act' : '') + (p.bankrupt ? ' out' : '') },
      token(p, true), h('strong', {}, p.name), h('span', { class: 'cash' }, eur(p.cash)),
      h('div', { class: 'meta' }, h('span', {}, 'Patrimonio ' + eur(p.worth)), chips.map(c => h('span', { class: 'chip2' }, c)),
        h('span', { class: 'pips' }, p.props.map(k => h('i', { class: 'pip' + (S.squares[k].mortgaged ? ' m' : ''), title: S.squares[k].name, style: 'background:' + (S.squares[k].color || 'var(--tinta-2)') })))));
  }));
}

function renderMine() {
  const actor = S.players[S.actor];
  const box = $('#mine');
  const can = !actor.bot && S.phase !== 'OVER';
  const props = actor.props.map(i => S.squares[i]);
  const rows = props.length ? props.map(sq => propRow(sq, can)) : [h('p', { class: 'muted' }, 'Todavía no tiene ni un piso en el pueblo.')];
  box.replaceChildren(h('h2', {}, `Propiedades de ${actor.name}`), ...rows);
}

function propRow(sq, can) {
  const ib = (icon, title, type) => h('button', { class: 'ib', title, 'aria-label': title, onclick: () => act(type + ':' + sq.i) }, icon);
  let status = '';
  if (sq.kind === 'street') status = sq.houses === 5 ? '🏨' : '🏠'.repeat(sq.houses);
  status += (sq.pool ? '🏊' : '') + (sq.court ? '🎾' : '');
  const row = h('div', { class: 'prow2' + (sq.mortgaged ? ' mort' : '') },
    h('i', { class: 'pip lg', style: 'background:' + (sq.color || 'var(--tinta-2)') }),
    h('button', { class: 'link nm2', onclick: () => showInfo(sq.i) }, sq.name),
    h('span', { class: 'stat' }, sq.mortgaged ? 'hipotecada' : status));
  if (can) {
    if (sq.kind === 'street') {
      row.append(ib('➕', `Construir (${eur(sq.houseCost)})`, 'build'), ib('➖', 'Vender un edificio', 'sell'));
      if (S.house && sq.houses === 5) row.append(ib('🏊', `Piscina (${eur(sq.poolCost)})`, 'pool'), ib('🎾', `Pista de deporte (${eur(sq.courtCost)})`, 'court'));
    }
    row.append(sq.mortgaged ? ib('🔓', `Cancelar la hipoteca (${eur(sq.unmortgage)})`, 'unmortgage') : ib('🏦', `Hipotecar (cobras ${eur(sq.mortgage)})`, 'mortgage'));
  }
  return row;
}

// ------------------------------------------------------------------ dialogs

function openDlg(dlg, ...content) {
  dlg.replaceChildren(h('div', { class: 'dlg' }, content));
  if (!dlg.open) dlg.showModal();
}
for (const d of document.querySelectorAll('dialog')) d.addEventListener('click', e => { if (e.target === d) d.close(); });

function showInfo(i) {
  const sq = S.squares[i];
  const body = [h('h3', {}, sq.name)];
  if (sq.color) body.push(h('div', { class: 'colorbar', style: 'background:' + sq.color }));
  body.push(h('p', { class: 'blurb' }, sq.blurb));
  if (sq.price) {
    const owner = sq.owner >= 0 ? S.players[sq.owner] : null;
    body.push(h('p', {}, `Precio: ${eur(sq.price)} · Hipoteca: ${eur(sq.mortgage)} · `, owner ? `Dueño: ${av(owner)} ${owner.name}${sq.mortgaged ? ' (hipotecada)' : ''}` : 'Sin dueño'));
  }
  if (sq.kind === 'street') {
    const names = ['Alquiler', 'Con el grupo completo', '1 casa', '2 casas', '3 casas', '4 casas', 'Hotel'];
    const rows = [[names[0], sq.rents[0]], [names[1], sq.rents[0] * 2], ...sq.rents.slice(1).map((r, k) => [names[k + 2], r])];
    body.push(h('div', { class: 'scroll' }, h('table', {}, rows.map(([n, r]) => h('tr', {}, h('td', {}, n), h('td', {}, eur(r)))))));
    body.push(h('p', { class: 'muted' }, `Cada casa cuesta ${eur(sq.houseCost)}.` + (S.house ? ` Piscina ${eur(sq.poolCost)} y pista ${eur(sq.courtCost)} (sobre un hotel): cada una suma ${eur(sq.rents[0] * 25)} al alquiler.` : '')));
  } else if (sq.kind === 'station') {
    body.push(h('p', { class: 'muted' }, 'Alquiler: 25 € con una estación, 50 € con dos, 100 € con tres y 200 € con las cuatro.'));
  } else if (sq.kind === 'utility') {
    body.push(h('p', { class: 'muted' }, 'Alquiler: 4 veces lo que saquen los dados, o 10 veces si el dueño tiene las dos compañías.'));
  } else if (sq.amount) {
    body.push(h('p', {}, `Pagas ${eur(sq.amount)} al banco.`));
  }
  body.push(h('div', { class: 'btns' }, btn('Cerrar', 'sec', () => $('#info').close())));
  openDlg($('#info'), body);
}

function tradable(sq) {
  return sq.kind !== 'street' || !S.squares.some(x => x.group === sq.group && x.houses > 0);
}

function openTrade() {
  const me = S.players[S.current];
  const others = S.players.map((p, i) => [p, i]).filter(([p, i]) => i !== S.current && !p.bankrupt);
  if (!others.length) return;
  let to = others[0][1];
  const dlg = $('#trade');

  const draw = () => {
    const mine = S.squares.filter(s => s.owner === S.current && tradable(s));
    const theirs = S.squares.filter(s => s.owner === to && tradable(s));
    const them = S.players[to];
    const checks = (list, name) => list.length
      ? list.map(s => h('label', { class: 'item' }, h('input', { type: 'checkbox', name, value: s.i }),
        h('i', { class: 'pip', style: 'background:' + (s.color || 'var(--tinta-2)') }), s.name + (s.mortgaged ? ' (hipotecada)' : '')))
      : [h('p', { class: 'muted' }, 'Nada que ofrecer.')];
    const cards = (n, id) => n ? h('label', {}, 'Cartas de la cárcel: ', h('select', { id }, [...Array(n + 1).keys()].map(k => h('option', { value: k }, k)))) : null;
    const select = h('select', { id: 'trTo', 'aria-label': 'Con quién', onchange: e => { to = parseInt(e.target.value, 10); draw(); } },
      others.map(([p, i]) => h('option', { value: i, selected: i === to }, `${av(p)} ${p.name}`)));
    const form = h('div', {},
      h('h3', {}, 'Proponer un trato'), h('label', {}, 'Con: ', select),
      h('div', { class: 'cols' },
        h('div', { class: 'col' }, h('h4', {}, `${me.name} da`), h('label', {}, 'Dinero (€)', h('input', { type: 'number', id: 'trCashGive', min: 0, max: me.cash, step: 10, value: 0 })), checks(mine, 'give'), cards(me.jailCards, 'trCardsGive')),
        h('div', { class: 'col' }, h('h4', {}, `${them.name} da`), h('label', {}, 'Dinero (€)', h('input', { type: 'number', id: 'trCashAsk', min: 0, max: them.cash, step: 10, value: 0 })), checks(theirs, 'ask'), cards(them.jailCards, 'trCardsAsk'))),
      h('p', { class: 'muted' }, 'Las propiedades hipotecadas obligan a quien las recibe a pagar el 10 % al banco. No se pueden tratar calles de un grupo con edificios.'),
      h('div', { class: 'btns' }, btn('Proponer', 'pri', () => {
        const num = id => parseInt(($('#' + id, dlg) || { value: '0' }).value, 10) || 0;
        const picked = name => [...dlg.querySelectorAll(`input[name=${name}]:checked`)].map(c => c.value).join(',');
        dlg.close();
        act(`trade:${to}:${num('trCashGive')}:${num('trCashAsk')}:${picked('give')}:${picked('ask')}:${num('trCardsGive')}:${num('trCardsAsk')}`);
      }), btn('Cancelar', 'sec', () => dlg.close())));
    dlg.replaceChildren(h('div', { class: 'dlg' }, form));
  };
  draw();
  dlg.showModal();
}

function showOver() {
  const ranked = S.players.map((p, i) => ({ p, i })).sort((a, b) => b.p.worth - a.p.worth);
  const w = S.players[S.winner];
  const heads = ['Jugador', 'Patrimonio', 'Efectivo', 'Propiedades', 'Alquiler cobrado', 'Alquiler pagado', 'Impuestos', 'Construido', 'Vueltas', 'Cárcel', 'Subastas'];
  const rows = ranked.map(({ p }) => [
    `${av(p)} ${p.name}${p.bankrupt ? ' (en la ruina)' : ''}`, eur(p.worth), eur(p.cash), p.props.length,
    eur(p.stats.rentReceived), eur(p.stats.rentPaid), eur(p.stats.taxPaid), eur(p.stats.built), p.stats.laps, p.stats.jails, p.stats.auctions]);
  openDlg($('#over'),
    h('h3', {}, 'Fin de la partida'),
    h('p', { class: 'winner' }, token(w, true), ' Gana ', h('span', {}, w.name)),
    h('div', { class: 'scroll' }, h('table', {}, h('tr', {}, heads.map(x => h('th', {}, x))), rows.map(r => h('tr', {}, r.map(c => h('td', {}, c)))))),
    h('div', { class: 'btns' }, btn('Nueva partida', 'pri', () => { $('#over').close(); openSetup(false); }), btn('Cerrar', 'sec', () => $('#over').close())));
}

// ------------------------------------------------------------------ wiring

$('#bAdd').addEventListener('click', () => {
  if (setupRows.length >= 6) return;
  const used = new Set(setupRows.map(r => r.avatar));
  setupRows.push({ name: '', avatar: Object.keys(AVATARS).find(k => !used.has(k)), bot: false });
  setupRows[setupRows.length - 1].name = defaultName(false, setupRows.length - 1);
  drawSetup();
});
$('#bStart').addEventListener('click', startNewGame);
$('#bLoad').addEventListener('click', () => { overShown = false; shown = []; leaveSetup = true; post('/api/load'); });
$('#bBack').addEventListener('click', () => { canReturn = false; shown = S.players.map(p => p.pos); showGame(); render(); maybeBot(); });
$('#bUndo').addEventListener('click', () => post('/api/undo'));
$('#bFinish').addEventListener('click', () => { if (confirm('¿Terminar ya? Gana quien más patrimonio tenga ahora mismo.')) act('finish'); });
$('#bNew').addEventListener('click', () => { if (confirm('¿Dejar esta partida y preparar otra?')) openSetup(true); });
$('#bSound').addEventListener('click', () => {
  sound = !sound;
  store.set('sound', sound ? 'on' : 'off');
  $('#bSound').textContent = sound ? '🔊' : '🔇';
  if (sound) sfx.coin();
});
$('#bSound').textContent = sound ? '🔊' : '🔇';

poll();
setInterval(poll, 1500);
