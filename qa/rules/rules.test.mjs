// Tier 2 (section 10.3): database rules in the emulator. Run from the repo root with
//   firebase emulators:exec --only database --project campus-find-86d58 "npm --prefix qa/rules test"
// If a rule fails a test, fix the rule, not the test, unless the test is wrong.
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { ref, get, set, update, remove, serverTimestamp } from 'firebase/database';

const rulesPath = fileURLToPath(new URL('../../firebase/database.rules.json', import.meta.url));
const [host, port] = (process.env.FIREBASE_DATABASE_EMULATOR_HOST || '127.0.0.1:9000').split(':');

let env;

// Accounts. Staff roles are set in the seed, as the owner does in the console.
const ana = { uid: 'ana', email: 'ana@ms.bulsu.edu.ph', name: 'Ana Cruz', role: 'student' }; // finder / poster
const ben = { uid: 'ben', email: 'ben@gmail.com', name: 'Ben Reyes', role: 'student' }; // claimant
const cara = { uid: 'cara', email: 'cara@gmail.com', name: 'Cara Lim', role: 'student' }; // second claimant
const clerk = { uid: 'clerk', email: 'clerk@ms.bulsu.edu.ph', name: 'Clara Office', role: 'clerk' };
const faculty = { uid: 'fac', email: 'fac@ms.bulsu.edu.ph', name: 'Felix Dean', role: 'faculty' };
const unverified = { uid: 'unv', email: 'unv@gmail.com', name: 'Una Verde', role: 'student' };

function db(user, { verified = true, email = user.email } = {}) {
  return env.authenticatedContext(user.uid, { email, email_verified: verified }).database();
}

const T = 1_700_000_000_000;
const userRecord = (u) => ({ name: u.name, email: u.email, role: u.role, createdAt: T });

function item(overrides = {}) {
  return {
    type: 'FOUND', title: 'Calculus textbook', category: 'Book', location: 'Laboratory',
    status: 'OPEN', postedBy: ana.uid, postedByName: ana.name, createdAt: T, updatedAt: T,
    ...overrides,
  };
}

function claim(user, overrides = {}) {
  return {
    claimant: user.uid, claimantName: user.name, secretDetails: 'My name is on the back cover',
    status: 'PENDING', createdAt: T, ...overrides,
  };
}

/** A student's claim as the app writes it: the claim, the pointer and CLAIM_PENDING in one update. */
function claimUpdate(user, itemId = 'found1', overrides = {}) {
  return {
    [`claims/${itemId}/${user.uid}`]: claim(user, overrides),
    [`users/${user.uid}/myClaims/${itemId}`]: true,
    [`items/${itemId}/status`]: 'CLAIM_PENDING',
  };
}

async function seed(extra = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await set(ref(ctx.database()), {
      users: Object.fromEntries([ana, ben, cara, clerk, faculty, unverified].map((u) => [u.uid, userRecord(u)])),
      items: {
        found1: item(),
        lost1: item({ type: 'LOST', title: 'Blue umbrella', category: 'Other' }),
      },
      itemSecrets: { found1: { hiddenDetail: 'Name written inside back cover' } },
      ...extra,
    });
  });
}

/** found1 with Ben's pending claim (and Cara's, when [withCara]). */
async function seedPending({ withCara = false } = {}) {
  const claims = { found1: { ben: claim(ben) } };
  if (withCara) claims.found1.cara = claim(cara, { secretDetails: 'There is a sticker on page one' });
  await seed({ claims });
  await env.withSecurityRulesDisabled((ctx) => set(ref(ctx.database(), 'items/found1/status'), 'CLAIM_PENDING'));
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'campus-find-86d58',
    database: { host, port: Number(port), rules: readFileSync(rulesPath, 'utf8') },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearDatabase();
  await seed();
});

describe('Users (R1-R3, R23)', () => {
  test('R1 verified Gmail user writes own profile as student: allow', async () => {
    await env.clearDatabase();
    await assertSucceeds(set(ref(db(ben), 'users/ben'), userRecord(ben)));
  });

  test('R2 user writes own profile as clerk: deny', async () => {
    await env.clearDatabase();
    await assertFails(set(ref(db(ben), 'users/ben'), { ...userRecord(ben), role: 'clerk' }));
  });

  test('R2b student promotes self later: deny', async () => {
    await assertFails(set(ref(db(ben), 'users/ben/role'), 'clerk'));
  });

  test('R3 non-allowed email writes own profile: deny', async () => {
    await env.clearDatabase();
    const yahoo = { uid: 'yah', email: 'yah@yahoo.com', name: 'Ya Hoo', role: 'student' };
    await assertFails(set(ref(db(yahoo), 'users/yah'), userRecord(yahoo)));
  });

  test('R23 student changes own name after creation: deny', async () => {
    await assertFails(set(ref(db(ben), 'users/ben/name'), 'Benjamin Reyes'));
  });

  test('student reads another user profile: deny; staff: allow', async () => {
    await assertFails(get(ref(db(ben), 'users/ana')));
    await assertSucceeds(get(ref(db(clerk), 'users/ana')));
  });
});

describe('Profile picture', () => {
  const photo = 'A'.repeat(20000);

  test('student and clerk set and remove their own picture: allow', async () => {
    await assertSucceeds(set(ref(db(ben), 'users/ben/photo'), photo));
    await assertSucceeds(remove(ref(db(ben), 'users/ben/photo')));
    await assertSucceeds(set(ref(db(clerk), 'users/clerk/photo'), photo));
  });

  test('picture over 60000 characters or not text: deny', async () => {
    await assertFails(set(ref(db(ben), 'users/ben/photo'), 'A'.repeat(60001)));
    await assertFails(set(ref(db(ben), 'users/ben/photo'), 12345));
  });

  test('someone else sets my picture: deny (even staff)', async () => {
    await assertFails(set(ref(db(cara), 'users/ben/photo'), photo));
    await assertFails(set(ref(db(clerk), 'users/ben/photo'), photo));
  });
});

describe('Items (R4-R8, R17, R21, R22, R25-R27)', () => {
  test('R4 verified student creates own OPEN item with its secret in one update: allow', async () => {
    await assertSucceeds(update(ref(db(ben)), {
      'items/new1': item({ postedBy: ben.uid, postedByName: ben.name }),
      'itemSecrets/new1': { hiddenDetail: 'Sticker on the back' },
    }));
  });

  test('R5 student creates an item with status RETURNED: deny', async () => {
    await assertFails(set(ref(db(ben), 'items/new1'), item({ postedBy: ben.uid, postedByName: ben.name, status: 'RETURNED' })));
  });

  test('R6 unverified student creates an item: deny', async () => {
    await assertFails(set(ref(db(unverified, { verified: false }), 'items/new1'),
      item({ postedBy: unverified.uid, postedByName: unverified.name })));
  });

  test('R7 poster of a FOUND item sets it RETURNED: deny', async () => {
    await assertFails(set(ref(db(ana), 'items/found1/status'), 'RETURNED'));
  });

  test('R8 poster of a LOST item closes it, stamping updatedAt: allow', async () => {
    await assertSucceeds(update(ref(db(ana)), {
      'items/lost1/status': 'CLOSED',
      'items/lost1/updatedAt': serverTimestamp(),
    }));
  });

  test('R8b another student closes the LOST item: deny', async () => {
    await assertFails(set(ref(db(ben), 'items/lost1/status'), 'CLOSED'));
  });

  test('R17 postedByName that is not the poster name: deny', async () => {
    await assertFails(set(ref(db(ben), 'items/new1'), item({ postedBy: ben.uid, postedByName: 'Someone Else' })));
  });

  test('R21 unknown field: deny', async () => {
    await assertFails(set(ref(db(ben), 'items/new1'), item({ postedBy: ben.uid, postedByName: ben.name, reward: 100 })));
  });

  test('R22 coverUrl outside covers.openlibrary.org: deny; inside: allow', async () => {
    const base = { postedBy: ben.uid, postedByName: ben.name, category: 'Book' };
    await assertFails(set(ref(db(ben), 'items/new1'), item({ ...base, coverUrl: 'https://evil.example/x.jpg' })));
    await assertSucceeds(set(ref(db(ben), 'items/new2'),
      item({ ...base, coverUrl: 'https://covers.openlibrary.org/b/isbn/9780134685991-M.jpg?default=false' })));
  });

  test('R25 faculty overrides any item to any valid status: allow', async () => {
    for (const status of ['TURNED_IN', 'CLAIM_PENDING', 'RETURNED', 'CLOSED', 'OPEN']) {
      await assertSucceeds(update(ref(db(faculty)), { 'items/found1/status': status, 'items/found1/updatedAt': serverTimestamp() }));
    }
  });

  test('R26 status outside the five allowed: deny (even for faculty)', async () => {
    await assertFails(set(ref(db(faculty), 'items/found1/status'), 'LOST_FOREVER'));
    await assertFails(set(ref(db(ana), 'items/lost1/status'), 'DONE'));
  });

  test('R27 any signed-in user reads items: allow; signed out: deny', async () => {
    await assertSucceeds(get(ref(db(unverified, { verified: false }), 'items')));
    await assertFails(get(ref(env.unauthenticatedContext().database(), 'items')));
  });

  test('clerk marks a FOUND item TURNED_IN with turnedInAt: allow', async () => {
    await assertSucceeds(update(ref(db(clerk)), {
      'items/found1/status': 'TURNED_IN',
      'items/found1/turnedInAt': serverTimestamp(),
      'items/found1/updatedAt': serverTimestamp(),
    }));
  });

  test('poster edits own item title after posting: deny (editing is staff only)', async () => {
    await assertFails(set(ref(db(ana), 'items/found1/title'), 'Different title'));
  });
});

describe('Item color, brand and heldAt', () => {
  const base = { postedBy: 'ben', postedByName: 'Ben Reyes' };

  test('color from the list: allow; unknown color: deny', async () => {
    await assertSucceeds(set(ref(db(ben), 'items/c1'), item({ ...base, color: 'blue' })));
    await assertFails(set(ref(db(ben), 'items/c2'), item({ ...base, color: 'purple' })));
    await assertFails(set(ref(db(ben), 'items/c3'), item({ ...base, color: 'Blue' })));
  });

  test('brand up to 60 characters: allow; 61: deny', async () => {
    await assertSucceeds(set(ref(db(ben), 'items/b1'), item({ ...base, brand: 'a'.repeat(60) })));
    await assertFails(set(ref(db(ben), 'items/b2'), item({ ...base, brand: 'a'.repeat(61) })));
  });

  test('heldAt up to 100 characters: allow; 101: deny', async () => {
    await assertSucceeds(set(ref(db(ben), 'items/h1'), item({ ...base, heldAt: 'Library front desk' })));
    await assertFails(set(ref(db(ben), 'items/h2'), item({ ...base, heldAt: 'a'.repeat(101) })));
  });
});

describe('Hidden details (R9, R10)', () => {
  test('R9 other student reads itemSecrets: deny', async () => {
    await assertFails(get(ref(db(ben), 'itemSecrets/found1')));
  });

  test('R10 poster reads own itemSecrets: allow; staff: allow', async () => {
    await assertSucceeds(get(ref(db(ana), 'itemSecrets/found1')));
    await assertSucceeds(get(ref(db(clerk), 'itemSecrets/found1')));
  });

  test('poster rewrites an existing hidden detail: deny', async () => {
    await assertFails(set(ref(db(ana), 'itemSecrets/found1'), { hiddenDetail: 'Changed' }));
  });
});

describe('Claims (R11-R16, R18-R20, R24)', () => {
  test('student claims an OPEN found item in one update: allow', async () => {
    await assertSucceeds(update(ref(db(ben)), claimUpdate(ben)));
  });

  test('R11 other student reads someone else claim: deny', async () => {
    await seedPending();
    await assertFails(get(ref(db(cara), 'claims/found1/ben')));
    await assertSucceeds(get(ref(db(ben), 'claims/found1/ben')));
  });

  test('R12 clerk reads claims and approves with item RETURNED: allow', async () => {
    await seedPending();
    await assertSucceeds(get(ref(db(clerk), 'claims/found1')));
    await assertSucceeds(update(ref(db(clerk)), {
      'claims/found1/ben/status': 'APPROVED',
      'claims/found1/ben/reviewedBy': clerk.uid,
      'claims/found1/ben/pickupCode': '123456',
      'items/found1/status': 'RETURNED',
      'items/found1/updatedAt': serverTimestamp(),
    }));
  });

  test('R13 student sets CLAIM_PENDING with no claim in the same update: deny', async () => {
    await assertFails(set(ref(db(ben), 'items/found1/status'), 'CLAIM_PENDING'));
  });

  test('R14 (changed by the owner) second student claims an item already CLAIM_PENDING: allow', async () => {
    await seedPending();
    await assertSucceeds(update(ref(db(cara)), claimUpdate(cara)));
  });

  test('R14b claim on a RETURNED item: deny', async () => {
    await env.withSecurityRulesDisabled((ctx) => set(ref(ctx.database(), 'items/found1/status'), 'RETURNED'));
    await assertFails(update(ref(db(ben)), claimUpdate(ben)));
  });

  test('R15 poster claims own FOUND item: deny', async () => {
    await assertFails(update(ref(db(ana)), claimUpdate(ana)));
  });

  test('R16 claim without setting CLAIM_PENDING: deny', async () => {
    await assertFails(set(ref(db(ben), 'claims/found1/ben'), claim(ben)));
  });

  test('R18 resubmit after REJECTED: allow', async () => {
    await seed({ claims: { found1: { ben: claim(ben, { status: 'REJECTED', rejectReason: 'Wrong color', reviewedBy: 'clerk' }) } } });
    await assertSucceeds(update(ref(db(ben)), claimUpdate(ben, 'found1', { secretDetails: 'More detail: name on page 3' })));
  });

  test('R18 resubmit while PENDING or after APPROVED: deny', async () => {
    await seedPending();
    await assertFails(update(ref(db(ben)), claimUpdate(ben)));
    await seed({ claims: { found1: { ben: claim(ben, { status: 'APPROVED', reviewedBy: 'clerk', pickupCode: '123456' }) } } });
    await assertFails(update(ref(db(ben)), claimUpdate(ben)));
  });

  test('R19 claim a LOST item: deny', async () => {
    await assertFails(update(ref(db(ben)), claimUpdate(ben, 'lost1')));
  });

  test('R20 clerk rejects with no rejectReason: deny; with a reason: allow', async () => {
    await seedPending();
    await assertFails(update(ref(db(clerk)), {
      'claims/found1/ben/status': 'REJECTED',
      'claims/found1/ben/reviewedBy': clerk.uid,
      'items/found1/status': 'OPEN',
    }));
    await assertSucceeds(update(ref(db(clerk)), {
      'claims/found1/ben/status': 'REJECTED',
      'claims/found1/ben/rejectReason': 'The description does not match.',
      'claims/found1/ben/reviewedBy': clerk.uid,
      'items/found1/status': 'OPEN',
      'items/found1/updatedAt': serverTimestamp(),
    }));
  });

  test('R24 unverified student claims: deny', async () => {
    await assertFails(update(ref(db(unverified, { verified: false })), claimUpdate(unverified)));
  });

  test('claim text under 10 characters: deny', async () => {
    await assertFails(update(ref(db(ben)), claimUpdate(ben, 'found1', { secretDetails: 'too short' })));
  });

  test('student writes own claim as APPROVED: deny', async () => {
    await assertFails(update(ref(db(ben)), claimUpdate(ben, 'found1', { status: 'APPROVED' })));
  });
});

describe('Multiple claims and pickup code', () => {
  test('approve one claim, reject the other, item RETURNED, in one update: allow', async () => {
    await seedPending({ withCara: true });
    await assertSucceeds(update(ref(db(clerk)), {
      'claims/found1/ben/status': 'APPROVED',
      'claims/found1/ben/reviewedBy': clerk.uid,
      'claims/found1/ben/pickupCode': '004217',
      'claims/found1/cara/status': 'REJECTED',
      'claims/found1/cara/rejectReason': 'Another claim for this item was approved.',
      'claims/found1/cara/reviewedBy': clerk.uid,
      'items/found1/status': 'RETURNED',
      'items/found1/updatedAt': serverTimestamp(),
    }));
  });

  test('reject one of two claims keeps the item CLAIM_PENDING: allow', async () => {
    await seedPending({ withCara: true });
    await assertSucceeds(update(ref(db(clerk)), {
      'claims/found1/cara/status': 'REJECTED',
      'claims/found1/cara/rejectReason': 'Wrong cover.',
      'claims/found1/cara/reviewedBy': clerk.uid,
      'items/found1/status': 'CLAIM_PENDING',
      'items/found1/updatedAt': serverTimestamp(),
    }));
  });

  test('pickup code that is not 6 digits: deny', async () => {
    await seedPending();
    await assertFails(update(ref(db(clerk)), {
      'claims/found1/ben/status': 'APPROVED',
      'claims/found1/ben/reviewedBy': clerk.uid,
      'claims/found1/ben/pickupCode': '12ab',
      'items/found1/status': 'RETURNED',
    }));
  });

  test('student writes a pickupCode into own claim: deny', async () => {
    await assertFails(update(ref(db(ben)), claimUpdate(ben, 'found1', { pickupCode: '123456' })));
  });

  test('pickup code is readable by the claimant and staff only', async () => {
    await seed({ claims: { found1: { ben: claim(ben, { status: 'APPROVED', reviewedBy: 'clerk', pickupCode: '123456' }) } } });
    await assertSucceeds(get(ref(db(ben), 'claims/found1/ben/pickupCode')));
    await assertSucceeds(get(ref(db(clerk), 'claims/found1/ben/pickupCode')));
    await assertFails(get(ref(db(cara), 'claims/found1/ben/pickupCode')));
    await assertFails(get(ref(db(ana), 'claims/found1/ben/pickupCode')));
  });
});

describe('Staff never claim; no self-review', () => {
  test('clerk claims a found item: deny', async () => {
    await assertFails(update(ref(db(clerk)), claimUpdate(clerk)));
  });

  test('faculty claims a found item: deny', async () => {
    await assertFails(update(ref(db(faculty)), claimUpdate(faculty)));
  });

  test('staff approves a claim filed under their own uid: deny', async () => {
    // Only possible with seeded data, since staff cannot claim.
    await seed({ claims: { found1: { clerk: claim(clerk) } } });
    await assertFails(update(ref(db(clerk)), {
      'claims/found1/clerk/status': 'APPROVED',
      'claims/found1/clerk/reviewedBy': clerk.uid,
      'items/found1/status': 'RETURNED',
    }));
  });
});

describe('Item photos', () => {
  const photo = 'B'.repeat(30000);
  const newFound = () => item({ postedBy: ben.uid, postedByName: ben.name });
  const newLost = () => item({ type: 'LOST', title: 'Black umbrella', postedBy: ben.uid, postedByName: ben.name });

  test('poster adds up to two photos with a new item in one update: allow', async () => {
    await assertSucceeds(update(ref(db(ben)), {
      'items/p1': newFound(),
      'itemSecrets/p1': { hiddenDetail: 'Sticker inside' },
      'itemPhotos/p1': { 0: photo, 1: photo },
    }));
  });

  test('third photo, oversized photo or photos on someone else item: deny', async () => {
    await assertFails(update(ref(db(ben)), { 'items/p2': newFound(), 'itemPhotos/p2': { 0: photo, 1: photo, 2: photo } }));
    await assertFails(update(ref(db(ben)), { 'items/p3': newFound(), 'itemPhotos/p3': { 0: 'B'.repeat(80001) } }));
    await assertFails(set(ref(db(cara), 'itemPhotos/found1'), { 0: photo }));
  });

  test('poster cannot replace photos once saved: deny', async () => {
    await env.withSecurityRulesDisabled((ctx) => set(ref(ctx.database(), 'itemPhotos/found1'), { 0: photo }));
    await assertFails(set(ref(db(ana), 'itemPhotos/found1'), { 0: 'C'.repeat(30000) }));
  });

  test('found item photos: poster and staff read, other students deny', async () => {
    await env.withSecurityRulesDisabled((ctx) => set(ref(ctx.database(), 'itemPhotos/found1'), { 0: photo }));
    await assertSucceeds(get(ref(db(ana), 'itemPhotos/found1')));
    await assertSucceeds(get(ref(db(clerk), 'itemPhotos/found1')));
    await assertFails(get(ref(db(ben), 'itemPhotos/found1')));
  });

  test('lost item photos: any signed-in user reads', async () => {
    await assertSucceeds(update(ref(db(ben)), { 'items/l2': newLost(), 'itemPhotos/l2': { 0: photo } }));
    await assertSucceeds(get(ref(db(cara), 'itemPhotos/l2')));
    await assertFails(get(ref(env.unauthenticatedContext().database(), 'itemPhotos/l2')));
  });

  test('staff delete photos with the item: allow; student delete: deny', async () => {
    await env.withSecurityRulesDisabled((ctx) => set(ref(ctx.database(), 'itemPhotos/found1'), { 0: photo }));
    await assertFails(remove(ref(db(ana), 'itemPhotos/found1')));
    await assertSucceeds(update(ref(db(clerk)), { 'items/found1': null, 'itemSecrets/found1': null, 'itemPhotos/found1': null }));
  });
});

describe('Deletes (staff Settled, History, student Profile)', () => {
  test('staff delete item, hidden detail and claims in one update: allow', async () => {
    await seedPending();
    await assertSucceeds(update(ref(db(clerk)), {
      'items/found1': null, 'itemSecrets/found1': null, 'claims/found1': null,
    }));
  });

  test('student deletes an item, even their own: deny', async () => {
    await assertFails(remove(ref(db(ana), 'items/found1')));
  });

  test('staff delete one History record: allow; student deletes own claim record: deny', async () => {
    await seed({ claims: { found1: { ben: claim(ben, { status: 'REJECTED', rejectReason: 'No', reviewedBy: 'clerk' }) } } });
    await assertFails(remove(ref(db(ben), 'claims/found1/ben')));
    await assertSucceeds(remove(ref(db(clerk), 'claims/found1/ben')));
  });

  test('student removes a claim from own Profile (myClaims pointer): allow; from another student: deny', async () => {
    await seed({
      users: {
        ...Object.fromEntries([ana, cara, clerk, faculty, unverified].map((u) => [u.uid, userRecord(u)])),
        ben: { ...userRecord(ben), myClaims: { found1: true } },
      },
      claims: { found1: { ben: claim(ben, { status: 'APPROVED', reviewedBy: 'clerk', pickupCode: '123456' }) } },
    });
    await assertFails(update(ref(db(cara)), { 'users/ben/myClaims/found1': null }));
    await assertSucceeds(update(ref(db(ben)), { 'users/ben/myClaims/found1': null }));
    // The staff record stays.
    await assertSucceeds(get(ref(db(clerk), 'claims/found1/ben')));
  });
});
