import { HttpsError } from 'firebase-functions/v2/https';
import { db, FieldValue } from './admin';
import type { Transaction } from 'firebase-admin/firestore';

export type FriendAction = 'send' | 'accept' | 'decline' | 'withdraw' | 'unfriend';

export interface FriendshipDoc {
  members: [string, string];
  state: 'PENDING' | 'ACCEPTED';
  requestedBy: string;
  requestedAt: FirebaseFirestore.Timestamp;
  acceptedAt: FirebaseFirestore.Timestamp | null;
  updatedAt: FirebaseFirestore.Timestamp;
}

export function pairId(a: string, b: string): string {
  return [a, b].sort().join('__');
}

/**
 * Single state-machine entry point for all friend relationship transitions.
 * Called inside a Firestore transaction. Reads the pair doc, applies the
 * transition, and writes the result. The projection trigger `onFriendshipWritten`
 * then derives both sides' social maps from the canonical doc.
 *
 * Preserves existing HttpsError detail strings so client toCause() works unchanged.
 */
export async function applyTransition(
  tx: Transaction,
  me: string,
  other: string,
  action: FriendAction,
): Promise<void> {
  const ref = db.doc(`friendships/${pairId(me, other)}`);
  const snap = await tx.get(ref);
  const data = snap.exists ? (snap.data() as FriendshipDoc) : null;

  switch (action) {
    case 'send': {
      if (!data) {
        // No existing relationship — create PENDING.
        tx.set(ref, {
          members: [me, other].sort() as [string, string],
          state: 'PENDING',
          requestedBy: me,
          requestedAt: FieldValue.serverTimestamp(),
          acceptedAt: null,
          updatedAt: FieldValue.serverTimestamp(),
        });
        return;
      }
      if (data.state === 'ACCEPTED') {
        throw new HttpsError('already-exists', 'already-friends');
      }
      if (data.state === 'PENDING' && data.requestedBy === other) {
        // Mutual request: auto-accept — both wanted it.
        tx.update(ref, {
          state: 'ACCEPTED',
          acceptedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
        return;
      }
      // PENDING & requestedBy === me: idempotent no-op.
      if (data.state === 'PENDING' && data.requestedBy === me) {
        throw new HttpsError('already-exists', 'request-already-sent');
      }
      return;
    }

    case 'accept': {
      if (!data || data.state !== 'PENDING' || data.requestedBy !== other) {
        throw new HttpsError('not-found', 'request-not-found');
      }
      tx.update(ref, {
        state: 'ACCEPTED',
        acceptedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      return;
    }

    case 'decline': {
      if (!data) return; // Already gone — idempotent success.
      if (data.state !== 'PENDING' || data.requestedBy !== other) {
        throw new HttpsError('not-found', 'request-not-found');
      }
      tx.delete(ref);
      return;
    }

    case 'withdraw': {
      if (!data) return; // Already gone — idempotent success.
      if (data.state !== 'PENDING' || data.requestedBy !== me) {
        throw new HttpsError('not-found', 'request-not-found');
      }
      tx.delete(ref);
      return;
    }

    case 'unfriend': {
      if (!data) return; // Already gone — idempotent success.
      tx.delete(ref);
      return;
    }
  }
}
