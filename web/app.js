// Campus Find staff dashboard. Same Firebase project, accounts and rules as the Android app;
// every write mirrors the app's multi-path updates, so the rules accept or refuse them the same way.
import { initializeApp } from 'https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js';
import {
  getAuth, signInWithEmailAndPassword, onAuthStateChanged, signOut, connectAuthEmulator,
} from 'https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js';
import {
  getDatabase, ref, onValue, get, update, serverTimestamp, connectDatabaseEmulator,
} from 'https://www.gstatic.com/firebasejs/11.10.0/firebase-database.js';
import { firebaseConfig, useEmulators } from './config.js';

const app = initializeApp(useEmulators ? { ...firebaseConfig, apiKey: 'emulator-key' } : firebaseConfig);
const auth = getAuth(app);
const db = getDatabase(app);
if (useEmulators) {
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  connectDatabaseEmulator(db, '127.0.0.1', 9000);
}

const OTHER_APPROVED_REASON = 'Another claim for this item was approved.';
const WRITE_TIMEOUT_MS = 20000;
const CATEGORIES = ['Book', 'Bag', 'ID or card', 'Electronics', 'Clothing', 'Keys', 'Other'];
const SETTLED_HIDE_MS = 3 * 24 * 60 * 60 * 1000;
const STATUS = {
  OPEN: ['Open', 'chip-neutral'], TURNED_IN: ['Turned in', 'chip-pending'], CLAIM_PENDING: ['Claim pending', 'chip-pending'],
  RETURNED: ['Returned', 'chip-success'], CLOSED: ['Closed', 'chip-neutral'],
  PENDING: ['Pending', 'chip-pending'], APPROVED: ['Approved', 'chip-success'], REJECTED: ['Rejected', 'chip-error'],
};

// ---------- state ----------
const state = {
  user: null, profile: null, items: {}, claims: {}, tab: 'claims', selected: null,
  checked: new Set(), secrets: new Map(), photos: new Map(), pickupCode: '', unsubscribe: [],
  loaded: { items: false, claims: false }, greeted: false,
  browse: { type: 'FOUND', query: '', category: '', settled: false },
};

// ---------- small DOM helpers (user text always goes in as text, never as HTML) ----------
const $ = (id) => document.getElementById(id);
function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v == null || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) if (c != null && c !== false) el.append(c instanceof Node ? c : String(c));
  return el;
}
/** replaceChildren that skips empty parts (it would otherwise print "null"). */
const fill = (el, ...children) => el.replaceChildren(...children.flat().filter((c) => c != null && c !== false && c !== ''));
const chip = (status) => h('span', { class: `chip ${STATUS[status]?.[1] ?? 'chip-neutral'}` }, STATUS[status]?.[0] ?? status);
const field = (label, value) => h('div', { class: 'field' }, h('div', { class: 'label' }, label), h('div', {}, value || 'Not given'));
const titleCase = (s) => (s ? s[0].toUpperCase() + s.slice(1) : s);

function ago(ms) {
  if (!ms) return '';
  const min = Math.round((Date.now() - ms) / 60000);
  if (min < 1) return 'Just now';
  if (min < 60) return `${min} minute${min === 1 ? '' : 's'} ago`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr} hour${hr === 1 ? '' : 's'} ago`;
  const d = Math.round(hr / 24);
  if (d < 7) return `${d} day${d === 1 ? '' : 's'} ago`;
  return new Date(ms).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

let toastTimer;
function toast(text) {
  const t = $('toast');
  t.textContent = text;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, 4000);
}

function errorText(e) {
  const m = String(e?.code || e?.message || e);
  if (m.includes('PERMISSION_DENIED') || m.includes('permission')) return 'This action is not allowed.';
  if (m.includes('invalid-credential') || m.includes('wrong-password') || m.includes('user-not-found')) return 'Wrong email or password.';
  if (m.includes('too-many-requests')) return 'Too many attempts. Wait a few minutes and try again.';
  if (m.includes('network') || m.includes('timed out')) return 'No connection. Try again.';
  return 'Something went wrong. Try again.';
}

/** A multi-path update that gives up with "No connection" instead of waiting forever. */
function write(updates) {
  return Promise.race([
    update(ref(db), updates),
    new Promise((_, reject) => setTimeout(() => reject(new Error('timed out')), WRITE_TIMEOUT_MS)),
  ]);
}

/** Shows a dialog; resolves true when OK is pressed. [content] may hold inputs read by the caller. */
function confirmDialog(title, content, okText = 'OK', danger = false) {
  const dialog = $('dialog');
  $('dialogTitle').textContent = title;
  $('dialogContent').replaceChildren(...[content].flat().filter(Boolean));
  $('dialogOk').textContent = okText;
  $('dialogOk').className = danger ? 'btn-filled btn-danger-fill' : 'btn-filled';
  $('dialogCancel').hidden = okText === 'Close';
  dialog.showModal();
  // Resolved from the buttons, not the dialog's close event, which some browsers deliver late or not
  // at all when the tab is in the background.
  return new Promise((resolve) => {
    const done = (ok) => {
      $('dialogOk').onclick = null;
      $('dialogCancel').onclick = null;
      dialog.oncancel = null;
      if (dialog.open) dialog.close();
      resolve(ok);
    };
    $('dialogOk').onclick = () => done(true);
    $('dialogCancel').onclick = () => done(false);
    dialog.oncancel = (e) => { e.preventDefault(); done(false); };
  });
}

function pickupCode() {
  const n = crypto.getRandomValues(new Uint32Array(1))[0] % 1000000;
  return String(n).padStart(6, '0');
}

// ---------- data views ----------
const allClaims = () => Object.entries(state.claims).flatMap(([itemId, byUid]) =>
  Object.entries(byUid || {}).map(([uid, c]) => ({ ...c, itemId, claimant: c.claimant || uid, key: `${itemId}/${uid}` })));
const itemsList = () => Object.entries(state.items).map(([id, it]) => ({ ...it, id }));
const pendingClaims = () => allClaims().filter((c) => c.status === 'PENDING').sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
const turnInItems = () => itemsList().filter((i) => i.type === 'FOUND' && i.status === 'OPEN').sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
const settledAt = (i) => i.updatedAt || i.createdAt || 0;
const settledItems = () => itemsList().filter((i) => i.status === 'RETURNED' || i.status === 'CLOSED').sort((a, b) => settledAt(b) - settledAt(a));
/** Lost and found browser: like the app Feed (settled items leave after 3 days unless asked for). */
function browseItems() {
  const { type, query, category, settled } = state.browse;
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  return itemsList()
    .filter((i) => i.type === type)
    .filter((i) => settled || !((i.status === 'RETURNED' || i.status === 'CLOSED') && Date.now() - settledAt(i) > SETTLED_HIDE_MS))
    .filter((i) => !category || i.category === category)
    .filter((i) => {
      const text = [i.title, i.brand, i.color, i.category, i.location, i.room, i.publicDescription, i.bookAuthors, i.isbn, i.heldAt].filter(Boolean).join(' ').toLowerCase();
      return words.every((w) => text.includes(w));
    })
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
}
const historyClaims = () => allClaims().filter((c) => c.reviewedBy === state.user?.uid && c.status !== 'PENDING')
  .sort((a, b) => settledAt(state.items[b.itemId] || {}) - settledAt(state.items[a.itemId] || {}));

async function secretOf(itemId) {
  if (!state.secrets.has(itemId)) {
    const snap = await get(ref(db, `itemSecrets/${itemId}/hiddenDetail`)).catch(() => null);
    state.secrets.set(itemId, snap?.val() ?? null);
  }
  return state.secrets.get(itemId);
}

async function photosOf(itemId) {
  if (!state.photos.has(itemId)) {
    const snap = await get(ref(db, `itemPhotos/${itemId}`)).catch(() => null);
    const val = snap?.val();
    state.photos.set(itemId, val ? Object.values(val).filter((p) => typeof p === 'string') : []);
  }
  return state.photos.get(itemId);
}

// ---------- rendering ----------
function render() {
  if (!state.profile) return;
  document.querySelectorAll('.tab').forEach((b) => b.setAttribute('aria-current', b.dataset.tab === state.tab ? 'page' : 'false'));
  const counts = { claims: pendingClaims().length, turnin: turnInItems().length, settled: settledItems().length };
  document.querySelectorAll('[data-count]').forEach((c) => { c.textContent = counts[c.dataset.count] || ''; });
  document.title = counts.claims ? `(${counts.claims}) Campus Find Staff` : 'Campus Find Staff';
  ({ items: renderBrowse, claims: renderClaims, turnin: renderTurnIn, pickup: renderPickup, settled: renderSettled, history: renderHistory })[state.tab]();
}

function select(key) {
  state.selected = key;
  render();
}

function rowButton(key, title, meta, status, checkbox) {
  return h('div', {
    class: `row${state.selected === key ? ' selected' : ''}`, role: 'button', tabindex: 0,
    onclick: () => select(key), onkeydown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); select(key); } },
  },
  checkbox || h('span'),
  h('span', { class: 'title' }, title),
  status ? chip(status) : h('span'),
  h('span', { class: 'meta' }, meta));
}

function listHead(text, action) {
  return h('div', { class: 'list-head' }, h('h2', {}, text), action || '');
}

function itemFields(item) {
  return h('div', { class: 'fields' },
    field('Category', item.category),
    field('Location', [item.location, item.room].filter(Boolean).join(', ')),
    field('Color', titleCase(item.color)),
    field('Brand or model', item.brand),
    field('Posted by', `${item.postedByName || ''} · ${ago(item.createdAt)}`),
    item.heldAt && item.status === 'OPEN' ? field('Where it is now', item.heldAt) : null,
    item.isbn ? field('ISBN', item.isbn) : null,
    field('Public description', item.publicDescription));
}

function itemHead(item) {
  return h('div', { class: 'detail-head' },
    h('h1', {}, item.title),
    h('span', { class: 'chip chip-type' }, item.type === 'LOST' ? 'Lost' : 'Found'),
    chip(item.status));
}

function photoStrip(photos) {
  if (!photos?.length) return null;
  return h('div', { class: 'photos' }, photos.map((p, i) => {
    const src = `data:image/jpeg;base64,${p}`;
    return h('img', {
      src, alt: `Finder's photo ${i + 1}`,
      onclick: () => confirmDialog(`Photo ${i + 1}`, h('img', { src, alt: `Finder's photo ${i + 1}` }), 'Close'),
    });
  }));
}

/** Faculty override: change any item to any of the five statuses. */
function facultyOverride(item) {
  if (state.profile.role !== 'faculty') return null;
  const select = h('select', { 'aria-label': 'New status' },
    Object.keys(STATUS).slice(0, 5).map((s) => h('option', { value: s, selected: s === item.status }, STATUS[s][0])));
  return h('div', { class: 'actions' }, select, h('button', {
    class: 'btn-outlined', type: 'button',
    onclick: async () => {
      if (select.value === item.status) return;
      try {
        await write({ [`items/${item.id}/status`]: select.value, [`items/${item.id}/updatedAt`]: serverTimestamp() });
        toast('Status changed.');
      } catch (e) { toast(errorText(e)); }
    },
  }, 'Change status'));
}

function emptyDetail(text) {
  fill($('detail'), h('p', { class: 'empty' }, text));
}

// Lost and found: every item, like the app Feed, with staff details on the right.
function renderBrowse() {
  const b = state.browse;
  const typeButton = (type, label) => h('button', {
    type: 'button', 'aria-pressed': b.type === type ? 'true' : 'false',
    onclick: () => { b.type = type; state.selected = null; render(); },
  }, `${label} (${itemsList().filter((i) => i.type === type).length})`);
  const search = h('input', { type: 'search', placeholder: 'Search title, brand, color or place', 'aria-label': 'Search items', value: b.query });
  search.addEventListener('input', () => {
    b.query = search.value;
    const pos = search.selectionStart;
    render();
    const again = document.querySelector('#list input[type=search]');
    again.focus();
    again.setSelectionRange(pos, pos);
  });
  const category = h('select', { 'aria-label': 'Category', onchange: (e) => { b.category = e.target.value; render(); } },
    h('option', { value: '' }, 'All categories'),
    CATEGORIES.map((c) => h('option', { value: c, selected: b.category === c }, c)));
  const settled = h('label', { class: 'check' },
    h('input', { type: 'checkbox', checked: b.settled, onchange: (e) => { b.settled = e.target.checked; render(); } }),
    'Include items settled over 3 days ago');
  const items = browseItems();
  fill($('list'),
    listHead('Lost and found'),
    h('div', { class: 'filters' }, h('div', { class: 'segmented', role: 'group', 'aria-label': 'Type' }, typeButton('FOUND', 'Found'), typeButton('LOST', 'Lost')), search, category, settled),
    items.length ? items.map((i) => rowButton(i.id, i.title,
      [i.category, i.location, ago(i.createdAt), i.status === 'OPEN' && i.heldAt ? `Now at: ${i.heldAt}` : null].filter(Boolean).join(' · '), i.status))
      : h('p', { class: 'empty' }, b.query || b.category ? 'No items match your search.' : `No ${b.type === 'FOUND' ? 'found' : 'lost'} items yet.`));
  const item = items.find((i) => i.id === state.selected);
  if (!item) return emptyDetail(items.length ? 'Select an item to see its details.' : '');
  const action = item.type === 'FOUND' && item.status === 'OPEN' ? h('button', {
    class: 'btn-filled', type: 'button',
    onclick: async () => {
      try {
        await write({ [`items/${item.id}/status`]: 'TURNED_IN', [`items/${item.id}/turnedInAt`]: serverTimestamp(), [`items/${item.id}/updatedAt`]: serverTimestamp() });
        toast('Marked as turned in.');
      } catch (e) { toast(errorText(e)); }
    },
  }, 'Mark turned in') : null;
  renderItemDetail(item, action);
}

// Claims: oldest first, each beside the finder's hidden detail and photos.
function renderClaims() {
  const claims = pendingClaims();
  fill($('list'), listHead('Claims to review'),
    ...(claims.length ? claims.map((c) => rowButton(c.key, state.items[c.itemId]?.title || 'Item removed',
      `${c.claimantName} · ${ago(c.createdAt)}`, 'PENDING')) : [h('p', { class: 'empty' }, 'No claims to review.')]));
  const claim = claims.find((c) => c.key === state.selected);
  if (!claim) return emptyDetail(claims.length ? 'Select a claim to review it.' : '');
  renderClaimDetail(claim);
}

async function renderClaimDetail(claim) {
  const item = { ...state.items[claim.itemId], id: claim.itemId };
  const [secret, photos] = await Promise.all([secretOf(claim.itemId), photosOf(claim.itemId)]);
  if (state.selected !== claim.key) return;
  const others = pendingClaims().filter((c) => c.itemId === claim.itemId && c.claimant !== claim.claimant);
  const own = claim.claimant === state.user.uid;
  fill($('detail'), 
    itemHead(item),
    h('div', { class: 'compare' },
      h('div', { class: 'box' }, h('h3', {}, "FINDER'S HIDDEN DETAIL"), h('div', { class: 'hidden-detail' }, secret || 'Not given'), photoStrip(photos)),
      h('div', { class: 'box' },
        h('h3', {}, `CLAIM BY ${claim.claimantName.toUpperCase()} · ${ago(claim.createdAt).toUpperCase()}`),
        h('p', { style: 'font-size:16px;margin:0 0 12px' }, claim.secretDetails),
        field('When and where they lost it', claim.whenWhere),
        h('div', { style: 'height:8px' }),
        field('Contact note', claim.contactNote))),
    others.length ? h('p', { class: 'note' }, `${others.length + 1} claims on this item. Approving this one rejects the others with the reason “${OTHER_APPROVED_REASON}”`) : null,
    own ? h('p', { class: 'note' }, 'You claimed this item. Another staff member must review it.') : h('div', { class: 'actions' },
      h('button', { class: 'btn-filled', type: 'button', onclick: () => approve(claim, item, others) }, 'Approve claim'),
      h('button', { class: 'btn-outlined', type: 'button', onclick: () => reject(claim, item, others.length > 0) }, 'Reject claim')),
    itemFields(item),
    facultyOverride(item));
}

async function approve(claim, item, others) {
  const ok = await confirmDialog('Approve claim?', h('p', {},
    `${claim.claimantName} gets a 6-digit pickup code and the item becomes Returned.` + (others.length ? ` ${others.length} other claim(s) will be rejected.` : '')),
  'Approve claim');
  if (!ok) return;
  const code = pickupCode();
  const base = `claims/${claim.itemId}/${claim.claimant}`;
  const updates = {
    [`${base}/status`]: 'APPROVED', [`${base}/reviewedBy`]: state.user.uid, [`${base}/pickupCode`]: code,
    [`items/${item.id}/status`]: 'RETURNED', [`items/${item.id}/updatedAt`]: serverTimestamp(),
  };
  for (const o of others) {
    const p = `claims/${o.itemId}/${o.claimant}`;
    Object.assign(updates, { [`${p}/status`]: 'REJECTED', [`${p}/rejectReason`]: OTHER_APPROVED_REASON, [`${p}/reviewedBy`]: state.user.uid });
  }
  try {
    await write(updates);
    state.selected = null;
    await confirmDialog('Claim approved', [h('p', {}, `Pickup code for ${claim.claimantName}:`), h('p', { class: 'code' }, code),
      h('p', { class: 'muted' }, 'The student sees this code in the app. Check it when they pick up the item.')], 'Close');
  } catch (e) { toast(errorText(e)); }
}

async function reject(claim, item, othersPending) {
  const reason = h('textarea', { maxlength: 200, 'aria-label': 'Reason for rejecting' });
  const err = h('p', { class: 'error' });
  const ok = await confirmDialog('Reject claim', [h('label', {}, 'Reason for rejecting (the student sees this)', reason), err], 'Reject claim');
  if (!ok) return;
  const text = reason.value.trim();
  if (!text || text.length > 200) { toast('Enter a reason, up to 200 characters.'); return; }
  const base = `claims/${claim.itemId}/${claim.claimant}`;
  const next = othersPending ? 'CLAIM_PENDING' : (item.turnedInAt ? 'TURNED_IN' : 'OPEN');
  try {
    await write({
      [`${base}/status`]: 'REJECTED', [`${base}/rejectReason`]: text, [`${base}/reviewedBy`]: state.user.uid,
      [`items/${item.id}/status`]: next, [`items/${item.id}/updatedAt`]: serverTimestamp(),
    });
    state.selected = null;
    toast('Claim rejected.');
  } catch (e) { toast(errorText(e)); }
}

// To turn in: found items not yet at the CS Office.
function renderTurnIn() {
  const items = turnInItems();
  fill($('list'), listHead('Found items to turn in'),
    ...(items.length ? items.map((i) => rowButton(i.id, i.title, [i.category, i.location, ago(i.createdAt), i.heldAt && `Now at: ${i.heldAt}`].filter(Boolean).join(' · '), i.status))
      : [h('p', { class: 'empty' }, 'No found items waiting to be turned in.')]));
  const item = items.find((i) => i.id === state.selected);
  if (!item) return emptyDetail(items.length ? 'Select an item.' : '');
  renderItemDetail(item, h('button', {
    class: 'btn-filled', type: 'button',
    onclick: async () => {
      try {
        await write({ [`items/${item.id}/status`]: 'TURNED_IN', [`items/${item.id}/turnedInAt`]: serverTimestamp(), [`items/${item.id}/updatedAt`]: serverTimestamp() });
        state.selected = null;
        toast('Marked as turned in.');
      } catch (e) { toast(errorText(e)); }
    },
  }, 'Mark turned in'));
}

async function renderItemDetail(item, action) {
  const [secret, photos] = await Promise.all([secretOf(item.id), photosOf(item.id)]);
  if (state.selected !== item.id) return;
  const approved = allClaims().find((c) => c.itemId === item.id && c.status === 'APPROVED');
  fill($('detail'), 
    itemHead(item),
    item.type === 'FOUND' ? h('div', { class: 'compare' },
      h('div', { class: 'box' }, h('h3', {}, "FINDER'S HIDDEN DETAIL"), h('div', { class: 'hidden-detail' }, secret || 'Not given'), photoStrip(photos)),
      approved ? h('div', { class: 'box' }, h('h3', {}, 'APPROVED FOR'), h('p', { style: 'font-size:16px;margin:0' }, approved.claimantName),
        h('p', { class: 'code' }, approved.pickupCode || '')) : h('div')) : (photos.length ? h('div', { class: 'box' }, h('h3', {}, 'PHOTOS'), photoStrip(photos)) : null),
    action ? h('div', { class: 'actions' }, action) : null,
    itemFields(item),
    facultyOverride(item));
}

// Pickup check: type the student's code to see the item and the approved student.
function renderPickup() {
  const input = h('input', { inputmode: 'numeric', maxlength: 6, placeholder: '6-digit code', 'aria-label': 'Pickup code', value: state.pickupCode });
  const check = () => { state.pickupCode = input.value.replace(/\D/g, '').slice(0, 6); state.selected = null; render(); };
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') check(); });
  fill($('list'), listHead('Pickup check'),
    h('p', { class: 'muted' }, 'Ask the student for the pickup code shown in their app.'),
    h('div', { class: 'pickup-form' }, input, h('button', { class: 'btn-filled', type: 'button', onclick: check }, 'Check')));
  input.focus();
  const code = state.pickupCode;
  if (code.length !== 6) return emptyDetail('Enter a 6-digit code.');
  const match = allClaims().find((c) => c.status === 'APPROVED' && c.pickupCode === code);
  if (!match) return fill($('detail'), h('h1', {}, 'No match'), h('p', { class: 'muted' }, `No approved claim has the code ${code}. Check the number with the student.`));
  const item = { ...state.items[match.itemId], id: match.itemId };
  fill($('detail'), 
    h('p', { class: 'chip chip-success', style: 'display:inline-block' }, 'Code matches'),
    h('h1', { style: 'margin-top:12px' }, item.title || 'Item removed'),
    h('div', { class: 'fields' }, field('Give it to', match.claimantName), field('Pickup code', code), field('Item status', STATUS[item.status]?.[0])),
    item.title ? itemFields(item) : null);
}

/** Checkbox selection with a Delete button, shared by Settled and History. */
function selectionHead(title, keys, onDelete) {
  for (const k of [...state.checked]) if (!keys.includes(k)) state.checked.delete(k);
  const n = state.checked.size;
  return listHead(title, h('button', { class: 'btn-text btn-danger', type: 'button', disabled: n === 0, onclick: onDelete }, n ? `Delete ${n}` : 'Delete'));
}

function checkbox(key, label) {
  return h('input', {
    type: 'checkbox', checked: state.checked.has(key), 'aria-label': `Select ${label}`,
    onclick: (e) => e.stopPropagation(),
    onchange: (e) => { if (e.target.checked) state.checked.add(key); else state.checked.delete(key); render(); },
  });
}

// Settled: returned and closed items. Deleting removes the item, its secret, photos and claims.
function renderSettled() {
  const items = settledItems();
  fill($('list'), 
    selectionHead('Settled items', items.map((i) => i.id), deleteSettled),
    h('p', { class: 'muted small' }, 'Settled items leave the app Feed after 3 days. Tick items to delete them.'),
    ...(items.length ? items.map((i) => rowButton(i.id, i.title, [i.category, ago(settledAt(i))].filter(Boolean).join(' · '), i.status, checkbox(i.id, i.title)))
      : [h('p', { class: 'empty' }, 'No settled items.')]));
  const item = items.find((i) => i.id === state.selected);
  if (!item) return emptyDetail(items.length ? 'Select an item.' : '');
  renderItemDetail(item);
}

async function deleteSettled() {
  const ids = [...state.checked];
  const returned = ids.some((id) => state.items[id]?.status === 'RETURNED');
  const ok = await confirmDialog('Delete permanently?', [
    h('p', {}, `Delete ${ids.length} item${ids.length === 1 ? '' : 's'} for everyone, with hidden details, photos and claims? This cannot be undone.`),
    returned ? h('p', { class: 'note' }, 'A returned item is selected. The student will lose their pickup code. Only delete after they have picked up the item.') : null,
  ], 'Delete', true);
  if (!ok) return;
  const updates = {};
  for (const id of ids) Object.assign(updates, { [`items/${id}`]: null, [`itemSecrets/${id}`]: null, [`itemPhotos/${id}`]: null, [`claims/${id}`]: null });
  try {
    await write(updates);
    state.checked.clear();
    state.selected = null;
    toast(`${ids.length} item${ids.length === 1 ? '' : 's'} deleted.`);
  } catch (e) { toast(errorText(e)); }
}

// History: claims this staff member approved or rejected.
function renderHistory() {
  const claims = historyClaims();
  const approved = claims.filter((c) => c.status === 'APPROVED').length;
  fill($('list'), 
    selectionHead('History', claims.map((c) => c.key), deleteHistory),
    h('p', { class: 'muted small' }, `${approved} approved · ${claims.length - approved} rejected`),
    ...(claims.length ? claims.map((c) => rowButton(c.key, state.items[c.itemId]?.title || 'Item removed',
      c.status === 'APPROVED' ? `Approved for ${c.claimantName}${c.pickupCode ? ` · code ${c.pickupCode}` : ''}` : `Rejected ${c.claimantName} · ${c.rejectReason || ''}`,
      c.status, checkbox(c.key, state.items[c.itemId]?.title || 'record')))
      : [h('p', { class: 'empty' }, 'You have not reviewed any claims yet.')]));
  const claim = claims.find((c) => c.key === state.selected);
  if (!claim) return emptyDetail(claims.length ? 'Select a record.' : '');
  const item = state.items[claim.itemId];
  fill($('detail'), 
    h('div', { class: 'detail-head' }, h('h1', {}, item?.title || 'Item removed'), chip(claim.status)),
    h('div', { class: 'fields' },
      field('Claimant', claim.claimantName),
      claim.status === 'APPROVED' ? field('Pickup code', claim.pickupCode) : field('Reason', claim.rejectReason),
      field('Claimed', ago(claim.createdAt))),
    h('div', { class: 'box' }, h('h3', {}, "CLAIMANT'S DESCRIPTION"), h('p', { style: 'margin:0' }, claim.secretDetails)));
}

async function deleteHistory() {
  const keys = [...state.checked];
  const approved = keys.some((k) => allClaims().find((c) => c.key === k)?.status === 'APPROVED');
  const ok = await confirmDialog('Delete permanently?', [
    h('p', {}, `Delete ${keys.length} record${keys.length === 1 ? '' : 's'} for everyone? Students will no longer see them. This cannot be undone.`),
    approved ? h('p', { class: 'note' }, 'The student will lose their pickup code. Only delete after they have picked up the item.') : null,
  ], 'Delete', true);
  if (!ok) return;
  try {
    await write(Object.fromEntries(keys.map((k) => [`claims/${k}`, null])));
    state.checked.clear();
    state.selected = null;
    toast(`${keys.length} record${keys.length === 1 ? '' : 's'} deleted.`);
  } catch (e) { toast(errorText(e)); }
}

// ---------- greeting ----------
/** Once per sign-in, after items and claims have loaded: hello plus what is waiting. */
function maybeGreet() {
  if (state.greeted || !state.loaded.items || !state.loaded.claims || !state.profile) return;
  state.greeted = true;
  const hour = new Date().getHours();
  const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  const first = String(state.profile.name || '').split(' ')[0];
  const claims = pendingClaims().length;
  const turnIn = turnInItems().length;
  const waiting = [
    claims ? `${claims} claim${claims === 1 ? '' : 's'} to review` : null,
    turnIn ? `${turnIn} found item${turnIn === 1 ? '' : 's'} to turn in` : null,
  ].filter(Boolean);
  $('greetingTitle').textContent = `${part}, ${first}.`;
  $('greetingText').textContent = waiting.length ? waiting.join(' · ') + '.' : 'Nothing is waiting right now.';
  $('greeting').hidden = false;
}

$('greetingClose').addEventListener('click', () => { $('greeting').hidden = true; });

// ---------- sign-in ----------
function show(view) {
  $('loginView').hidden = view !== 'login';
  $('appView').hidden = view !== 'app';
  $('who').hidden = view !== 'app';
}

function stopListening() {
  state.unsubscribe.forEach((u) => u());
  Object.assign(state, { unsubscribe: [], items: {}, claims: {}, selected: null, profile: null, greeted: false, loaded: { items: false, claims: false } });
  $('greeting').hidden = true;
  state.checked.clear();
  state.secrets.clear();
  state.photos.clear();
}

onAuthStateChanged(auth, async (user) => {
  stopListening();
  state.user = user;
  if (!user) return show('login');
  try {
    const profile = (await get(ref(db, `users/${user.uid}`))).val();
    if (!profile || (profile.role !== 'clerk' && profile.role !== 'faculty')) {
      await signOut(auth);
      $('loginError').textContent = 'Staff access only. Students use the Campus Find app.';
      return;
    }
    state.profile = profile;
    $('whoName').textContent = profile.name;
    $('whoRole').textContent = profile.role === 'faculty' ? 'Faculty' : 'Clerk';
    show('app');
    state.unsubscribe.push(
      onValue(ref(db, 'items'), (s) => { state.items = s.val() || {}; state.loaded.items = true; render(); maybeGreet(); }, (e) => toast(errorText(e))),
      onValue(ref(db, 'claims'), (s) => { state.claims = s.val() || {}; state.loaded.claims = true; render(); maybeGreet(); }, (e) => toast(errorText(e))),
    );
  } catch (e) {
    $('loginError').textContent = errorText(e);
    await signOut(auth);
  }
});

$('loginForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const email = $('email').value.trim().toLowerCase();
  const password = $('password').value;
  $('loginError').textContent = '';
  if (!email || !password) { $('loginError').textContent = 'Enter your email and password.'; return; }
  $('loginButton').disabled = true;
  try {
    await signInWithEmailAndPassword(auth, email, password);
    $('password').value = '';
  } catch (err) {
    $('loginError').textContent = errorText(err);
  } finally {
    $('loginButton').disabled = false;
  }
});

$('logOut').addEventListener('click', async () => {
  const ok = await confirmDialog('Log out?', h('p', {}, 'You will need your email and password to log in again.'), 'Log out');
  if (ok) signOut(auth);
});

document.querySelectorAll('.tab').forEach((b) => b.addEventListener('click', () => {
  state.tab = b.dataset.tab;
  state.selected = null;
  state.checked.clear();
  render();
}));
