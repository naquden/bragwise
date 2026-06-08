/**
 * One-shot migration: derive canonical `friendships/{pairId}` docs from existing
 * per-user `players/{uid}/private/social` maps.
 *
 * HIGH-RISK: touches live prod Firestore. Always run with --dry-run first.
 *
 * Usage:
 *   npx ts-node scripts/migrate-friendships.ts --dry-run
 *   npx ts-node scripts/migrate-friendships.ts
 *
 * What it does:
 *  - Reads every players/*/private/social doc.
 *  - Derives a canonical FriendshipDoc for each pair observed in friends/requestsIn/requestsOut.
 *  - Heals existing corruption:
 *      - One-sided friends (A.friends[B] but not B.friends[A]) → ACCEPTED.
 *      - Mutual-pending (both sent to each other) → ACCEPTED.
 *      - Consistent single-direction pending → PENDING(requestedBy).
 *  - Deduplicates by pairId (idempotent).
 *  - In dry-run mode: logs derived docs, writes nothing.
 *  - In live mode: writes all derived docs using a batched commit.
 *
 * Keep original social docs untouched — onFriendshipWritten will re-derive them
 * once the migration has been verified.
 */

import * as admin from 'firebase-admin';
import { FieldValue } from 'firebase-admin/firestore';

admin.initializeApp();
const db = admin.firestore();

const DRY_RUN = process.argv.includes('--dry-run');

function pairId(a: string, b: string): string {
  return [a, b].sort().join('__');
}

interface DerivedFriendship {
  members: [string, string];
  state: 'PENDING' | 'ACCEPTED';
  requestedBy: string;
  requestedAt: admin.firestore.FieldValue;
  acceptedAt: admin.firestore.FieldValue | null;
  updatedAt: admin.firestore.FieldValue;
}

async function main(): Promise<void> {
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN (no writes)' : 'LIVE'}`);

  // Collect all social docs.
  const playerDocs = await db.collection('players').listDocuments();
  console.log(`Found ${playerDocs.length} player docs`);

  const derived = new Map<string, DerivedFriendship>();

  for (const playerRef of playerDocs) {
    const socialSnap = await db.doc(`${playerRef.path}/private/social`).get();
    if (!socialSnap.exists) continue;
    const social = socialSnap.data()!;
    const uid = playerRef.id;

    const friends: Record<string, unknown> = social.friends ?? {};
    const requestsOut: Record<string, unknown> = social.requestsOut ?? {};

    // From friends map: this uid is friends with every key.
    for (const otherId of Object.keys(friends)) {
      const pid = pairId(uid, otherId);
      const existing = derived.get(pid);
      if (!existing) {
        derived.set(pid, {
          members: [uid, otherId].sort() as [string, string],
          state: 'ACCEPTED',
          requestedBy: uid < otherId ? uid : otherId, // stable choice for migration
          requestedAt: FieldValue.serverTimestamp(),
          acceptedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
      } else {
        // Any observation of friendship wins → upgrade to ACCEPTED.
        existing.state = 'ACCEPTED';
        existing.acceptedAt = FieldValue.serverTimestamp();
      }
    }

    // From requestsOut: uid sent a request to each key.
    for (const otherId of Object.keys(requestsOut)) {
      const pid = pairId(uid, otherId);
      const existing = derived.get(pid);
      if (!existing) {
        derived.set(pid, {
          members: [uid, otherId].sort() as [string, string],
          state: 'PENDING',
          requestedBy: uid,
          requestedAt: FieldValue.serverTimestamp(),
          acceptedAt: null,
          updatedAt: FieldValue.serverTimestamp(),
        });
      } else if (existing.state === 'PENDING' && existing.requestedBy !== uid) {
        // Mutual-pending: both sent to each other → collapse to ACCEPTED.
        console.log(`  Healing mutual-pending: ${uid} <-> ${otherId}`);
        existing.state = 'ACCEPTED';
        existing.acceptedAt = FieldValue.serverTimestamp();
      }
      // If already ACCEPTED, keep ACCEPTED.
    }
  }

  console.log(`Derived ${derived.size} friendship docs`);

  if (DRY_RUN) {
    let i = 0;
    for (const [pid, doc] of derived.entries()) {
      console.log(`  [${++i}] ${pid}  state=${doc.state}  requestedBy=${doc.requestedBy}`);
    }
    console.log('DRY RUN complete — no writes made.');
    return;
  }

  // Write in batches of 500.
  const entries = [...derived.entries()];
  let written = 0;
  for (let i = 0; i < entries.length; i += 500) {
    const batch = db.batch();
    for (const [pid, doc] of entries.slice(i, i + 500)) {
      batch.set(db.doc(`friendships/${pid}`), doc);
      written++;
    }
    await batch.commit();
    console.log(`Written ${written}/${entries.size ?? entries.length}`);
  }

  console.log('Migration complete.');
}

main().catch((e) => { console.error(e); process.exit(1); });
