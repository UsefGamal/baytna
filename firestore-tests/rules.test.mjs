// Security rule tests, run against the Firestore emulator in CI (see .github/workflows/android.yml).
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, collection, query, where, setDoc, updateDoc, deleteDoc, writeBatch, runTransaction,
  serverTimestamp, Timestamp,
} from 'firebase/firestore';

const HID = 'house-1';
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-baytna',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const db = (uid) => env.authenticatedContext(uid).firestore();

function createHousehold(fs, uid, hid = HID, memberId = 'm-owner') {
  const b = writeBatch(fs);
  b.set(doc(fs, 'households', hid), { ownerUid: uid, createdAt: serverTimestamp() });
  b.set(doc(fs, 'households', hid, 'memberships', uid), {
    uid, memberId, role: 'OWNER', status: 'ACTIVE', inviteId: null, joinedAt: serverTimestamp(),
  });
  b.set(doc(fs, 'users', uid), { householdId: hid, memberId, updatedAt: serverTimestamp() });
  return b.commit();
}

function entity(uid, opId, { type = 'transaction', id = 't1', version = 1, payload = '{}', hid = HID } = {}) {
  return {
    type, id, householdId: hid, version, deleted: false, payload, updatedBy: uid,
    serverUpdatedAt: serverTimestamp(), lastOpId: opId,
  };
}

function pushOp(fs, uid, opId, opts = {}) {
  const type = opts.type ?? 'transaction';
  const id = opts.id ?? 't1';
  const b = writeBatch(fs);
  b.set(doc(fs, 'households', HID, 'entities', `${type}_${id}`), entity(uid, opId, opts));
  b.set(doc(fs, 'households', HID, 'ops', opId), { entityKey: `${type}_${id}`, by: uid, at: serverTimestamp() });
  return b.commit();
}

function invite(createdBy, { memberId = 'm-partner', days = 7, hid = HID } = {}) {
  return {
    householdId: hid, memberId, createdBy, createdAt: serverTimestamp(),
    expiresAt: Timestamp.fromMillis(Date.now() + days * 86400000), revoked: false, usedBy: null, usedAt: null,
  };
}

function join(fs, uid, hash, memberId = 'm-partner') {
  return runTransaction(fs, async (tx) => {
    const inv = await tx.get(doc(fs, 'invites', hash));
    tx.set(doc(fs, 'households', inv.data().householdId, 'memberships', uid), {
      uid, memberId, role: 'MEMBER', status: 'ACTIVE', inviteId: hash, joinedAt: serverTimestamp(),
    });
    tx.update(doc(fs, 'invites', hash), { usedBy: uid, usedAt: serverTimestamp() });
    tx.set(doc(fs, 'users', uid), { householdId: inv.data().householdId, memberId, updatedAt: serverTimestamp() });
  });
}

describe('households', () => {
  test('owner can create a household with their membership', async () => {
    await assertSucceeds(createHousehold(db('alice'), 'alice'));
    await assertSucceeds(getDoc(doc(db('alice'), 'households', HID)));
  });

  test('a household cannot be created without the creator membership', async () => {
    await assertFails(setDoc(doc(db('alice'), 'households', HID), { ownerUid: 'alice', createdAt: serverTimestamp() }));
  });

  test('nobody can take over an existing household', async () => {
    await createHousehold(db('alice'), 'alice');
    await assertFails(createHousehold(db('mallory'), 'mallory'));
    await assertFails(setDoc(doc(db('mallory'), 'households', HID, 'memberships', 'mallory'), {
      uid: 'mallory', memberId: 'x', role: 'OWNER', status: 'ACTIVE', inviteId: null, joinedAt: serverTimestamp(),
    }));
  });

  test('strangers cannot read the household or its records', async () => {
    await createHousehold(db('alice'), 'alice');
    await pushOp(db('alice'), 'alice', 'op1');
    await assertFails(getDoc(doc(db('mallory'), 'households', HID)));
    await assertFails(getDocs(collection(db('mallory'), 'households', HID, 'entities')));
    await assertFails(getDocs(collection(db('mallory'), 'households', HID, 'memberships')));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'households', HID)));
  });

  test('users doc must point at a real membership', async () => {
    await createHousehold(db('alice'), 'alice');
    await assertFails(setDoc(doc(db('mallory'), 'users', 'mallory'), { householdId: HID, memberId: 'm-owner', updatedAt: serverTimestamp() }));
    await assertFails(getDoc(doc(db('mallory'), 'users', 'alice')));
  });
});

describe('entities', () => {
  beforeEach(async () => { await createHousehold(db('alice'), 'alice'); });

  test('member creates version 1 and updates by exactly one', async () => {
    await assertSucceeds(pushOp(db('alice'), 'alice', 'op1'));
    await assertSucceeds(pushOp(db('alice'), 'alice', 'op2', { version: 2 }));
    await assertFails(pushOp(db('alice'), 'alice', 'op3', { version: 4 }));
    await assertFails(pushOp(db('alice'), 'alice', 'op4', { version: 2 }));
  });

  test('create must start at version 1', async () => {
    await assertFails(pushOp(db('alice'), 'alice', 'op1', { version: 3 }));
  });

  test('replaying a processed operation is refused', async () => {
    await assertSucceeds(pushOp(db('alice'), 'alice', 'op1'));
    await assertFails(pushOp(db('alice'), 'alice', 'op1', { version: 2 }));
  });

  test('an entity write must record its operation', async () => {
    await assertFails(setDoc(doc(db('alice'), 'households', HID, 'entities', 'transaction_t1'), entity('alice', 'op1')));
  });

  test('key, type, household and author are validated', async () => {
    await assertFails(pushOp(db('alice'), 'alice', 'op1', { type: 'secret' }));
    const fs = db('alice');
    const b = writeBatch(fs);
    b.set(doc(fs, 'households', HID, 'entities', 'transaction_other'), entity('alice', 'op2'));
    b.set(doc(fs, 'households', HID, 'ops', 'op2'), { entityKey: 'x', by: 'alice', at: serverTimestamp() });
    await assertFails(b.commit());
    const b2 = writeBatch(fs);
    b2.set(doc(fs, 'households', HID, 'entities', 'transaction_t1'), { ...entity('alice', 'op3'), updatedBy: 'bob' });
    b2.set(doc(fs, 'households', HID, 'ops', 'op3'), { entityKey: 'x', by: 'alice', at: serverTimestamp() });
    await assertFails(b2.commit());
    const b3 = writeBatch(fs);
    b3.set(doc(fs, 'households', HID, 'entities', 'transaction_t1'), { ...entity('alice', 'op4'), extra: true });
    b3.set(doc(fs, 'households', HID, 'ops', 'op4'), { entityKey: 'x', by: 'alice', at: serverTimestamp() });
    await assertFails(b3.commit());
  });

  test('planning records sync like other records', async () => {
    for (const [i, type] of ['savings_goal', 'goal_contribution', 'recurring_rule'].entries()) {
      await assertSucceeds(pushOp(db('alice'), 'alice', `p${i}a`, { type, id: 'x1' }));
      await assertSucceeds(pushOp(db('alice'), 'alice', `p${i}b`, { type, id: 'x1', version: 2 }));
    }
  });

  test('activity entries can be created but never edited', async () => {
    await assertSucceeds(pushOp(db('alice'), 'alice', 'a1', { type: 'activity', id: 'e1' }));
    await assertFails(pushOp(db('alice'), 'alice', 'a2', { type: 'activity', id: 'e1', version: 2 }));
    await assertFails(pushOp(db('mallory'), 'mallory', 'a3', { type: 'activity', id: 'e2' }));
  });

  test('strangers cannot write records', async () => {
    await assertFails(pushOp(db('mallory'), 'mallory', 'op1'));
  });

  test('records and operations cannot be deleted', async () => {
    await pushOp(db('alice'), 'alice', 'op1');
    await assertFails(deleteDoc(doc(db('alice'), 'households', HID, 'entities', 'transaction_t1')));
    await assertFails(deleteDoc(doc(db('alice'), 'households', HID, 'ops', 'op1')));
    await assertFails(updateDoc(doc(db('alice'), 'households', HID, 'ops', 'op1'), { by: 'alice' }));
  });
});

describe('invites', () => {
  beforeEach(async () => { await createHousehold(db('alice'), 'alice'); });

  test('member creates an invite; partner joins once', async () => {
    await assertSucceeds(setDoc(doc(db('alice'), 'invites', 'h1'), invite('alice')));
    await assertSucceeds(join(db('bob'), 'bob', 'h1'));
    await assertSucceeds(getDoc(doc(db('bob'), 'households', HID)));
    await assertSucceeds(pushOp(db('bob'), 'bob', 'op-bob'));
    await assertFails(join(db('carol'), 'carol', 'h1'));
  });

  test('expired, revoked or long-lived invites are refused', async () => {
    await assertFails(setDoc(doc(db('alice'), 'invites', 'long'), invite('alice', { days: 30 })));
    await assertFails(setDoc(doc(db('alice'), 'invites', 'past'), invite('alice', { days: -1 })));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'invites', 'old'), {
        householdId: HID, memberId: 'm-partner', createdBy: 'alice', createdAt: Timestamp.now(),
        expiresAt: Timestamp.fromMillis(Date.now() - 1000), revoked: false, usedBy: null, usedAt: null,
      });
    });
    await assertFails(join(db('bob'), 'bob', 'old'));
    await setDoc(doc(db('alice'), 'invites', 'r1'), invite('alice'));
    await assertSucceeds(updateDoc(doc(db('alice'), 'invites', 'r1'), { revoked: true }));
    await assertFails(join(db('bob'), 'bob', 'r1'));
  });

  test('joining must use the invite member profile and consume the invite', async () => {
    await setDoc(doc(db('alice'), 'invites', 'h2'), invite('alice'));
    await assertFails(join(db('bob'), 'bob', 'h2', 'm-owner'));
    const fs = db('bob');
    await assertFails(setDoc(doc(fs, 'households', HID, 'memberships', 'bob'), {
      uid: 'bob', memberId: 'm-partner', role: 'MEMBER', status: 'ACTIVE', inviteId: 'h2', joinedAt: serverTimestamp(),
    }));
  });

  test('strangers cannot create, list or revoke invites', async () => {
    await assertFails(setDoc(doc(db('mallory'), 'invites', 'm1'), invite('mallory')));
    await setDoc(doc(db('alice'), 'invites', 'h3'), invite('alice'));
    await assertFails(getDocs(query(collection(db('mallory'), 'invites'), where('householdId', '==', HID))));
    await assertSucceeds(getDocs(query(collection(db('alice'), 'invites'), where('householdId', '==', HID))));
    await assertFails(updateDoc(doc(db('mallory'), 'invites', 'h3'), { revoked: true }));
  });
});
