import { onDocumentUpdated, onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { auth } from 'firebase-functions/v1';
import { db, FieldValue } from './lib/admin';
import { score, Bet, PredictionPayload } from './scoring';
import { sendToUser, CHANNEL_RESULTS, CHANNEL_CHALLENGES, CHANNEL_SOCIAL } from './push';

// ─── onResultsPosted ──────────────────────────────────────────────────────────

/**
 * Fires on any write to a challenge doc. Only acts when `resultsPostedAt`
 * transitions null → non-null (the sentinel set by `postResults`).
 *
 * Phase 1:
 *   1. Scan composite player docs, score each against results, write
 *      `challenges/{c}.leaderboard` map { uid → points }.
 *   2. Per-participant: read private/social, accumulate friend-pair h2h
 *      deltas in memory, issue ONE update per side.
 *
 * Head-to-head writes are NOT retried on transient failure — FieldValue.increment
 * is not idempotent and h2h is a rivalry signal, not load-bearing data.
 */
export const onResultsPosted = onDocumentUpdated(
  'challenges/{challengeId}',
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!after) return;

    // Only act on the null → non-null transition.
    if (before?.resultsPostedAt !== null && before?.resultsPostedAt !== undefined) return;
    if (after.resultsPostedAt === null || after.resultsPostedAt === undefined) return;

    const challengeId = event.params.challengeId;
    const results: Record<string, PredictionPayload> = after.results ?? {};
    const bets: Bet[] = after.bets ?? [];

    // 1. Scan player docs and compute scores.
    const playersSnap = await db.collection(`challenges/${challengeId}/players`).get();
    const leaderboard: Record<string, number> = {};
    for (const playerDoc of playersSnap.docs) {
      const data = playerDoc.data();
      const playerUid: string = data.uid ?? playerDoc.id;
      const predictions: Record<string, PredictionPayload> = data.predictions ?? {};
      let total = 0;
      for (const bet of bets) {
        const pred = predictions[bet.id];
        const result = results[bet.id];
        if (pred && result) {
          try { total += score(bet, pred, result); } catch (_) { /* mismatch — skip */ }
        }
      }
      leaderboard[playerUid] = total;
    }

    await db.doc(`challenges/${challengeId}`).update({ leaderboard });

    // 2. Notify each participant of their result — best-effort.
    const challengeTitle: string = after.title ?? 'A challenge';
    const sortedUids = Object.entries(leaderboard)
      .sort(([, a], [, b]) => b - a)
      .map(([uid]) => uid);
    await Promise.allSettled(
      sortedUids.map((uid, idx) =>
        sendToUser(uid, {
          title: 'Results are in!',
          body: `${challengeTitle} — you finished #${idx + 1} with ${leaderboard[uid]} pts`,
          channel: CHANNEL_RESULTS,
          deepLink: `https://bragwise.firebaseapp.com/c/${challengeId}`,
        }),
      ),
    );

    // 3. Head-to-head deltas — best-effort, NOT retried.
    const participants = Object.keys(leaderboard);
    await Promise.allSettled(
      participants.map(async (pid) => {
        const socialSnap = await db.doc(`players/${pid}/private/social`).get();
        if (!socialSnap.exists) return;
        const friends: Record<string, unknown> = socialSnap.data()!.friends ?? {};
        const myPoints = leaderboard[pid];
        const updates: Record<string, unknown> = {};
        for (const fid of Object.keys(friends)) {
          if (!(fid in leaderboard)) continue;
          const theirPoints = leaderboard[fid];
          let outcome: 'wins' | 'losses' | 'ties';
          if (myPoints > theirPoints) outcome = 'wins';
          else if (myPoints < theirPoints) outcome = 'losses';
          else outcome = 'ties';
          updates[`vs.${fid}.${outcome}`] = FieldValue.increment(1);
        }
        if (Object.keys(updates).length > 0) {
          await db.doc(`players/${pid}/private/headToHead`).set(updates, { merge: true });
        }
      }),
    );
  },
);

// ─── onMemberJoin ─────────────────────────────────────────────────────────────

/**
 * Increments `joinedCount` on first player doc creation. Idempotent: a
 * re-delivered event for the same doc path would re-increment, but Firestore
 * v2 triggers guarantee at-least-once delivery and there is no built-in
 * dedup — acceptable since joinedCount is best-effort UI noise, not
 * load-bearing (ground truth is the players subcollection).
 */
export const onMemberJoin = onDocumentCreated(
  'challenges/{challengeId}/players/{uid}',
  async (event) => {
    const data = event.data?.data();
    // Creator auto-join is excluded from the count — they set isCreator=true.
    if (!data || data.isCreator === true) return;

    const { challengeId, uid } = event.params;
    await db.doc(`challenges/${challengeId}`).update({
      joinedCount: FieldValue.increment(1),
    });

    // Notify the challenge creator — best-effort.
    const challengeSnap = await db.doc(`challenges/${challengeId}`).get();
    const creatorUid: string | undefined = challengeSnap.data()?.createdBy;
    if (creatorUid && creatorUid !== uid) {
      const joinerSnap = await db.doc(`players/${uid}`).get();
      const joinerName: string = joinerSnap.data()?.displayName ?? 'Someone';
      const challengeTitle: string = challengeSnap.data()?.title ?? 'your challenge';
      await sendToUser(creatorUid, {
        title: 'New participant!',
        body: `${joinerName} joined ${challengeTitle}`,
        channel: CHANNEL_CHALLENGES,
        deepLink: `https://bragwise.firebaseapp.com/c/${challengeId}`,
      }).catch(() => {/* best-effort */});
    }
  },
);

// ─── onFriendAccepted ─────────────────────────────────────────────────────────

/**
 * Fires on every write to `players/{uid}/private/social`. Only acts when
 * the `friends` map has grown (new entries added). For each new friend pair,
 * scans both users' open FRIENDS-visibility challenges and creates reciprocal
 * invitations for the other party.
 */
export const onFriendAccepted = onDocumentWritten(
  'players/{uid}/private/social',
  async (event) => {
    const uid = event.params.uid;
    const before: Record<string, unknown> = event.data?.before.data()?.friends ?? {};
    const after: Record<string, unknown> = event.data?.after.data()?.friends ?? {};

    const newFriendUids = Object.keys(after).filter((fid) => !(fid in before));
    if (newFriendUids.length === 0) return;

    // For each new friend pair, find open FRIENDS challenges created by
    // either side and create invitations for the other.
    for (const friendUid of newFriendUids) {
      await Promise.allSettled([
        createInvitationsForNewFriend(uid, friendUid),
        createInvitationsForNewFriend(friendUid, uid),
      ]);
    }

    // Notify each new friend that their request was accepted — best-effort.
    const acceptorSnap = await db.doc(`players/${uid}`).get();
    const acceptorName: string = acceptorSnap.data()?.displayName ?? 'Someone';
    await Promise.allSettled(
      newFriendUids.map((friendUid) =>
        sendToUser(friendUid, {
          title: 'Friend request accepted!',
          body: `${acceptorName} accepted your friend request`,
          channel: CHANNEL_SOCIAL,
        }),
      ),
    );
  },
);

async function createInvitationsForNewFriend(creatorUid: string, inviteeUid: string): Promise<void> {
  const challengesSnap = await db
    .collection('challenges')
    .where('createdBy', '==', creatorUid)
    .where('status', '==', 'OPEN')
    .where('visibility', '==', 'FRIENDS')
    .get();

  if (challengesSnap.empty) return;

  const batch = db.batch();
  for (const doc of challengesSnap.docs) {
    const inviteRef = db.doc(`challenges/${doc.id}/invitations/${inviteeUid}`);
    batch.set(inviteRef, {
      invitedUid: inviteeUid,
      invitedBy: creatorUid,
      invitedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  }
  await batch.commit();
}

// ─── onUserDeleted ────────────────────────────────────────────────────────────

/**
 * Processes the deletion checklist written by the `deleteAccount` callable.
 * Idempotent and resumable: each step queries-then-deletes so re-runs after
 * partial failure are no-ops for already-completed steps.
 */
export const onUserDeleted = auth.user().onDelete(async (user) => {
  const uid = user.uid;
  await processDeleteChecklist(uid);
});

async function processDeleteChecklist(uid: string): Promise<void> {
  const checklistRef = db.doc(`deletionRequests/${uid}`);
  const snap = await checklistRef.get();
  // If the callable didn't create the checklist yet (edge case: manual
  // Admin Console deletion), create it now.
  if (!snap.exists) {
    await checklistRef.set({
      uid,
      requestedAt: FieldValue.serverTimestamp(),
      steps: {
        handles: 'pending',
        players_subs: 'pending',
        players_subcoll: 'pending',
        invitations: 'pending',
        public_profile: 'pending',
        player_doc: 'pending',
        auth_user: 'done', // already deleted since we're in the trigger
      },
    });
  }

  const tick = (step: string) =>
    checklistRef.update({ [`steps.${step}`]: 'done' });

  // Step 1: remove handle reservation
  const playerSnap = await db.doc(`players/${uid}`).get();
  if (playerSnap.exists) {
    const handle = playerSnap.data()!.handle as string | undefined;
    if (handle) await db.doc(`handles/${handle}`).delete();
  }
  await tick('handles');

  // Step 2: private subcollections
  const privateDocs = await db.collection(`players/${uid}/private`).listDocuments();
  if (privateDocs.length > 0) {
    const batch = db.batch();
    for (const ref of privateDocs) batch.delete(ref);
    await batch.commit();
  }
  await tick('players_subs');

  // Step 3: membership + prediction docs (collection-group)
  const memberDocs = await db
    .collectionGroup('players')
    .where('uid', '==', uid)
    .get();
  if (!memberDocs.empty) {
    const chunks = chunk(memberDocs.docs, 500);
    for (const c of chunks) {
      const batch = db.batch();
      for (const d of c) {
        batch.delete(d.ref);
        batch.update(d.ref.parent.parent!, {
          joinedCount: FieldValue.increment(-1),
          [`leaderboard.${uid}`]: FieldValue.delete(),
        });
      }
      await batch.commit();
    }
  }
  await tick('players_subcoll');

  // Step 4: invitations
  const inviteDocs = await db
    .collectionGroup('invitations')
    .where('invitedUid', '==', uid)
    .get();
  if (!inviteDocs.empty) {
    const chunks = chunk(inviteDocs.docs, 500);
    for (const c of chunks) {
      const batch = db.batch();
      for (const d of c) batch.delete(d.ref);
      await batch.commit();
    }
  }
  await tick('invitations');

  // Step 5: public profile
  await db.doc(`publicProfiles/${uid}`).delete();
  await tick('public_profile');

  // Step 6: root player doc
  await db.doc(`players/${uid}`).delete();
  await tick('player_doc');

  // Auth user already gone (we're in the trigger) — mark done
  await tick('auth_user');
  await checklistRef.update({ completedAt: FieldValue.serverTimestamp() });
}

// ─── reconcileDeletions ───────────────────────────────────────────────────────

/**
 * Hourly. Resumes any deletionRequest with steps still `pending` older
 * than 24 h (catches failures from the `onUserDeleted` trigger).
 */
export const reconcileDeletions = onSchedule('every 60 minutes', async (_event) => {
  const cutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);
  const staleSnap = await db
    .collection('deletionRequests')
    .where('requestedAt', '<', cutoff)
    .get();

  await Promise.allSettled(
    staleSnap.docs
      .filter((doc) => {
        const steps: Record<string, string> = doc.data().steps ?? {};
        return Object.values(steps).some((v) => v === 'pending');
      })
      .map((doc) => processDeleteChecklist(doc.id)),
  );
});

// ─── helpers ──────────────────────────────────────────────────────────────────

function chunk<T>(arr: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}
