import { onDocumentUpdated, onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { auth } from 'firebase-functions/v1';
import { db, FieldValue, auth as adminAuth } from './lib/admin';
import { sendToUser, CHANNEL_RESULTS, CHANNEL_CHALLENGES, CHANNEL_SOCIAL } from './push';
import type { FriendshipDoc } from './lib/friendships';
import { computeLeaderboard, competitionRanks } from './leaderboard';
import type { Bet, PredictionPayload } from './scoring';

// ─── onResultsPosted ──────────────────────────────────────────────────────────

/**
 * Fires on any write to a challenge doc. Acts when `resultsPostedAt`
 * transitions null → non-null (the sentinel set by `postResults`).
 *
 * Side-effects only — scoring and leaderboard are already written by
 * `postResults` transactionally. This trigger does push notifications
 * and head-to-head bookkeeping, both idempotent.
 *
 * H2H is stored as a per-challenge keyed set under
 * `players/{uid}/private/headToHead/byChallenge/{challengeId}` so
 * re-delivery overwrites the same doc (no-op).
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
    const leaderboard: Record<string, number> = after.leaderboard ?? {};
    const rankedLeaderboard: Record<string, number> = after.rankedLeaderboard ?? {};
    const participants = Object.keys(leaderboard);

    // Early-return: no players scored.
    if (participants.length === 0) return;

    // 1. Notify each participant of their result — best-effort.
    const challengeTitle: string = after.title ?? 'A challenge';
    await Promise.allSettled(
      participants.map((uid) => {
        const rank = rankedLeaderboard[uid] ?? '?';
        const pts = leaderboard[uid] ?? 0;
        return sendToUser(uid, {
          title: 'Results are in!',
          body: `${challengeTitle} — you finished #${rank} with ${pts} pts`,
          channel: CHANNEL_RESULTS,
          deepLink: `https://bragwise.firebaseapp.com/c/${challengeId}`,
        });
      }),
    );

    // 2. Head-to-head — idempotent per-challenge set, best-effort.
    await Promise.allSettled(
      participants.map(async (pid) => {
        const socialSnap = await db.doc(`players/${pid}/private/social`).get();
        if (!socialSnap.exists) return;
        const friends: Record<string, unknown> = socialSnap.data()!.friends ?? {};
        const myPoints = leaderboard[pid];
        const vs: Record<string, 'win' | 'loss' | 'tie'> = {};
        for (const fid of Object.keys(friends)) {
          if (!(fid in leaderboard)) continue;
          const theirPoints = leaderboard[fid];
          vs[fid] = myPoints > theirPoints ? 'win' : myPoints < theirPoints ? 'loss' : 'tie';
        }
        if (Object.keys(vs).length > 0) {
          await db
            .doc(`players/${pid}/private/headToHead/byChallenge/${challengeId}`)
            .set({ vs });
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
    if (!data) return;

    const { challengeId, uid } = event.params;
    const joinerSnap = await db.doc(`players/${uid}`).get();
    const joinerData = joinerSnap.data() ?? {};
    const joinerName: string = joinerData.displayName ?? 'Someone';

    await db.doc(`challenges/${challengeId}`).update({
      joinedCount: FieldValue.increment(1),
      [`participants.${uid}`]: {
        displayName: joinerName,
        avatarSeed: joinerData.avatarSeed ?? uid,
      },
    });

    // Notify the challenge creator — best-effort.
    const challengeSnap = await db.doc(`challenges/${challengeId}`).get();
    const creatorUid: string | undefined = challengeSnap.data()?.createdBy;
    if (creatorUid && creatorUid !== uid) {
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

// ─── onFriendshipWritten ──────────────────────────────────────────────────────

/**
 * Fires on every write to `friendships/{pairId}` — the canonical source of
 * truth for a relationship. Derives both members' social-doc projections
 * from the authoritative (state, requestedBy) pair. Idempotent & convergent:
 * re-delivery recomputes the same projection, cannot orphan an edge.
 *
 * States:
 *  ACCEPTED → both sides friends[other]=acceptedAt; clear requestsIn/requestsOut.
 *  PENDING  → requestedBy side requestsOut[other]; other side requestsIn[requestedBy].
 *  deleted  → clear all four entries (mirrors onUserDeleted scrub).
 *
 * Push notification: fires "request accepted" on PENDING→ACCEPTED transition
 * (notifies the original requester that their request was accepted).
 * The system never auto-creates challenge invitations on friendship: FRIENDS-
 * visibility challenges are surfaced via observeFromFriends, not invitation docs.
 *
 * On ACCEPTED→deleted (unfriend): best-effort revocation of challenge invitations
 * for challenges created by either party.
 */
export const onFriendshipWritten = onDocumentWritten(
  'friendships/{pairId}',
  async (event) => {
    const before = event.data?.before.exists ? (event.data.before.data() as FriendshipDoc) : null;
    const after = event.data?.after.exists ? (event.data.after.data() as FriendshipDoc) : null;

    const members: [string, string] = after?.members ?? before?.members ?? ['', ''];
    const [uidA, uidB] = members;
    if (!uidA || !uidB) return;

    const socialA = db.doc(`players/${uidA}/private/social`);
    const socialB = db.doc(`players/${uidB}/private/social`);

    if (after?.state === 'ACCEPTED') {
      const acceptedAt = after.acceptedAt ?? FieldValue.serverTimestamp();
      await Promise.all([
        socialA.set({
          friends: { [uidB]: acceptedAt },
          requestsIn: { [uidB]: FieldValue.delete() },
          requestsOut: { [uidB]: FieldValue.delete() },
        }, { merge: true }),
        socialB.set({
          friends: { [uidA]: acceptedAt },
          requestsIn: { [uidA]: FieldValue.delete() },
          requestsOut: { [uidA]: FieldValue.delete() },
        }, { merge: true }),
      ]);

      // Notify the original requester that their request was accepted.
      if (before?.state === 'PENDING' || !before) {
        const requestedBy = after.requestedBy;
        const acceptorUid = requestedBy === uidA ? uidB : uidA;
        const acceptorSnap = await db.doc(`players/${acceptorUid}`).get();
        const acceptorName: string = acceptorSnap.data()?.displayName ?? 'Someone';
        await sendToUser(requestedBy, {
          title: 'Friend request accepted!',
          body: `${acceptorName} accepted your friend request`,
          channel: CHANNEL_SOCIAL,
        }).catch(() => {/* best-effort */});
      }
    } else if (after?.state === 'PENDING') {
      const requestedBy = after.requestedBy;
      const requestedAt = after.requestedAt ?? FieldValue.serverTimestamp();
      const other = requestedBy === uidA ? uidB : uidA;
      await Promise.all([
        db.doc(`players/${requestedBy}/private/social`).set({
          requestsOut: { [other]: requestedAt },
          requestsIn: { [other]: FieldValue.delete() },
          friends: { [other]: FieldValue.delete() },
        }, { merge: true }),
        db.doc(`players/${other}/private/social`).set({
          requestsIn: { [requestedBy]: requestedAt },
          requestsOut: { [requestedBy]: FieldValue.delete() },
          friends: { [requestedBy]: FieldValue.delete() },
        }, { merge: true }),
      ]);
    } else {
      // Doc deleted — clear all four entries on both sides.
      await Promise.all([
        socialA.set({
          friends: { [uidB]: FieldValue.delete() },
          requestsIn: { [uidB]: FieldValue.delete() },
          requestsOut: { [uidB]: FieldValue.delete() },
        }, { merge: true }),
        socialB.set({
          friends: { [uidA]: FieldValue.delete() },
          requestsIn: { [uidA]: FieldValue.delete() },
          requestsOut: { [uidA]: FieldValue.delete() },
        }, { merge: true }),
      ]);

      // On unfriend (ACCEPTED→deleted), revoke challenge invitations best-effort.
      if (before?.state === 'ACCEPTED') {
        await revokeInvitationsOnUnfriend(uidA, uidB).catch(() => {/* best-effort */});
      }
    }
  },
);

async function revokeInvitationsOnUnfriend(uidA: string, uidB: string): Promise<void> {
  // Find invitations where one party invited the other for their own challenges.
  const [invitesForA, invitesForB] = await Promise.all([
    db.collectionGroup('invitations').where('invitedUid', '==', uidA).get(),
    db.collectionGroup('invitations').where('invitedUid', '==', uidB).get(),
  ]);

  const toDelete: FirebaseFirestore.DocumentReference[] = [];

  for (const doc of invitesForA.docs) {
    const challengeRef = doc.ref.parent.parent;
    if (!challengeRef) continue;
    const challengeSnap = await challengeRef.get();
    if (challengeSnap.data()?.createdBy === uidB) toDelete.push(doc.ref);
  }
  for (const doc of invitesForB.docs) {
    const challengeRef = doc.ref.parent.parent;
    if (!challengeRef) continue;
    const challengeSnap = await challengeRef.get();
    if (challengeSnap.data()?.createdBy === uidA) toDelete.push(doc.ref);
  }

  for (const c of chunk(toDelete, 500)) {
    const batch = db.batch();
    for (const ref of c) batch.delete(ref);
    await batch.commit();
  }
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
        friend_refs: 'pending',
        friendships: 'pending',
        players_subs: 'pending',
        players_subcoll: 'pending',
        invitations: 'pending',
        push_tokens: 'pending',
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

  // Step 2: scrub references to this uid from other users' social and head-to-head docs.
  // Must run before players_subs so the social doc is still readable on a resumed run.
  const socialSnap = await db.doc(`players/${uid}/private/social`).get();
  if (socialSnap.exists) {
    const social = socialSnap.data()!;
    const friendUids = Object.keys(social.friends ?? {});
    const requestsInUids = Object.keys(social.requestsIn ?? {}); // they sent to us → clear their requestsOut
    const requestsOutUids = Object.keys(social.requestsOut ?? {}); // we sent to them → clear their requestsIn

    const refUpdates = new Map<string, Record<string, unknown>>();
    const addPatch = (otherUid: string, patch: Record<string, unknown>) => {
      refUpdates.set(otherUid, { ...(refUpdates.get(otherUid) ?? {}), ...patch });
    };
    for (const fid of friendUids) addPatch(fid, { [`friends.${uid}`]: FieldValue.delete() });
    for (const rid of requestsInUids) addPatch(rid, { [`requestsOut.${uid}`]: FieldValue.delete() });
    for (const rid of requestsOutUids) addPatch(rid, { [`requestsIn.${uid}`]: FieldValue.delete() });

    const socialEntries = [...refUpdates.entries()];
    for (const c of chunk(socialEntries, 500)) {
      const batch = db.batch();
      for (const [otherUid, patch] of c) {
        batch.set(db.doc(`players/${otherUid}/private/social`), patch, { merge: true });
      }
      await batch.commit();
    }

    // Head-to-head: delete per-challenge docs where this uid appeared.
    // New path: players/{fid}/private/headToHead/byChallenge/{challengeId} = { vs: { [uid]: ... } }
    // We scrub the uid key from each byChallenge doc across all friends.
    for (const c of chunk(friendUids, 50)) {
      await Promise.allSettled(
        c.map(async (fid) => {
          const byChallengeDocs = await db
            .collection(`players/${fid}/private/headToHead/byChallenge`)
            .listDocuments();
          await Promise.allSettled(
            byChallengeDocs.map((ref) =>
              ref.update({ [`vs.${uid}`]: FieldValue.delete() }),
            ),
          );
        }),
      );
    }
  }
  await tick('friend_refs');

  // Step 2b: delete canonical friendships/{pairId} docs that include this uid.
  for (;;) {
    const fsSnap = await db
      .collection('friendships')
      .where('members', 'array-contains', uid)
      .limit(500)
      .get();
    if (fsSnap.empty) break;
    const batch = db.batch();
    for (const doc of fsSnap.docs) batch.delete(doc.ref);
    await batch.commit();
    if (fsSnap.size < 500) break;
  }
  await tick('friendships');

  // Step 3: private subcollections
  const privateDocs = await db.collection(`players/${uid}/private`).listDocuments();
  if (privateDocs.length > 0) {
    const batch = db.batch();
    for (const ref of privateDocs) batch.delete(ref);
    await batch.commit();
  }
  await tick('players_subs');

  // Step 4: membership + prediction docs (collection-group)
  // Run one transaction per challenge so that, for resolved challenges, we can
  // recompute rankedLeaderboard with the deleted user excluded. For unresolved
  // challenges we fall back to a field-delete (no ranks exist yet).
  const memberDocs = await db
    .collectionGroup('players')
    .where('uid', '==', uid)
    .get();
  if (!memberDocs.empty) {
    await Promise.allSettled(
      memberDocs.docs.map(async (memberDoc) => {
        const challengeRef = memberDoc.ref.parent.parent!;
        await db.runTransaction(async (tx) => {
          const challengeSnap = await tx.get(challengeRef);
          if (!challengeSnap.exists) return;
          const data = challengeSnap.data()!;
          const resultsPosted = data.resultsPostedAt != null;

          if (resultsPosted) {
            // Recompute leaderboard excluding the deleted user.
            const bets: Bet[] = data.bets ?? [];
            const results = (data.results ?? {}) as Record<string, PredictionPayload>;
            const playersSnap = await tx.get(challengeRef.collection('players'));
            const players = playersSnap.docs
              .filter((d) => d.id !== uid)
              .map((d) => ({
                uid: (d.data().uid ?? d.id) as string,
                predictions: (d.data().predictions ?? {}) as Record<string, PredictionPayload>,
              }));
            const leaderboard = computeLeaderboard(bets, players, results);
            const ranked = competitionRanks(leaderboard);
            const rankedLeaderboard: Record<string, number> = {};
            for (const e of ranked) rankedLeaderboard[e.uid] = e.rank;

            tx.delete(memberDoc.ref);
            tx.update(challengeRef, {
              joinedCount: FieldValue.increment(-1),
              [`participants.${uid}`]: FieldValue.delete(),
              leaderboard,
              rankedLeaderboard,
            });
          } else {
            tx.delete(memberDoc.ref);
            tx.update(challengeRef, {
              joinedCount: FieldValue.increment(-1),
              [`leaderboard.${uid}`]: FieldValue.delete(),
              [`participants.${uid}`]: FieldValue.delete(),
            });
          }
        });
      }),
    );
  }
  await tick('players_subcoll');

  // Step 5: invitations
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

  // Step 6: push token subcollection (Firestore does not cascade-delete subcollections).
  const tokenRefs = await db.collection(`players/${uid}/pushTokens`).listDocuments();
  for (const c of chunk(tokenRefs, 500)) {
    const batch = db.batch();
    for (const ref of c) batch.delete(ref);
    await batch.commit();
  }
  await tick('push_tokens');

  // Step 7: public profile
  await db.doc(`publicProfiles/${uid}`).delete();
  await tick('public_profile');

  // Step 8: root player doc
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

// ─── purgeOldChallenges ───────────────────────────────────────────────────────

const DELETE_AFTER_DAYS = 90;

function locksAtMillis(v: unknown): number | null {
  if (v == null) return null;
  if (typeof v === 'string') {
    const ms = Date.parse(v);
    return Number.isNaN(ms) ? null : ms;
  }
  if (typeof v === 'number') return v;
  if (typeof v === 'object' && typeof (v as { toMillis?: unknown }).toMillis === 'function') {
    return (v as { toMillis: () => number }).toMillis();
  }
  return null;
}

async function purgeChallengeDeep(ref: FirebaseFirestore.DocumentReference): Promise<void> {
  const [playerRefs, invitationRefs] = await Promise.all([
    ref.collection('players').listDocuments(),
    ref.collection('invitations').listDocuments(),
  ]);
  for (const c of chunk([...playerRefs, ...invitationRefs, ref], 500)) {
    const batch = db.batch();
    for (const r of c) batch.delete(r);
    await batch.commit();
  }
}

/**
 * Daily. Hard-deletes challenges in two passes, both using DELETE_AFTER_DAYS:
 *
 * Pass 1 — resolved: resultsPostedAt < cutoff (original behaviour).
 * Pass 2 — abandoned: resultsPostedAt == null and createdAt < cutoff.
 *   A safety guard skips challenges still legitimately OPEN with a future
 *   locksAt so long-running challenges aren't deleted mid-life. Decrement
 *   the creator's activeChallenges counter (best-effort; createChallenge has
 *   drift repair).
 *
 * Processed in pages of 200 so a large backlog doesn't hit memory limits.
 */
export const purgeOldChallenges = onSchedule('every 24 hours', async () => {
  const cutoff = new Date(Date.now() - DELETE_AFTER_DAYS * 86400_000);

  // Pass 1: resolved challenges older than DELETE_AFTER_DAYS.
  for (;;) {
    const snap = await db
      .collection('challenges')
      .where('resultsPostedAt', '<', cutoff)
      .limit(200)
      .get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      await purgeChallengeDeep(doc.ref);
    }

    if (snap.size < 200) break;
  }

  // Pass 2: abandoned (never-resolved) challenges older than DELETE_AFTER_DAYS.
  for (;;) {
    const snap = await db
      .collection('challenges')
      .where('resultsPostedAt', '==', null)
      .where('createdAt', '<', cutoff)
      .limit(200)
      .get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      const data = doc.data();
      // Safety: skip challenges still legitimately accepting predictions.
      const locksAtMs = locksAtMillis(data.locksAt);
      if (data.status === 'OPEN' && locksAtMs != null && locksAtMs > Date.now()) continue;

      await purgeChallengeDeep(doc.ref);

      // Free the creator's active-challenge slot. Use update (not set/merge) so a
      // deleted creator's counters doc isn't re-created as an orphan; a missing
      // doc just throws and is swallowed (createChallenge has drift repair).
      const createdBy = data.createdBy as string | undefined;
      if (createdBy) {
        await db
          .doc(`players/${createdBy}/private/counters`)
          .update({ activeChallenges: FieldValue.increment(-1) })
          .catch(() => {/* no counters doc (e.g. deleted creator) — skip */});
      }
    }

    if (snap.size < 200) break;
  }
});

// ─── purgeStaleGuests ─────────────────────────────────────────────────────────

const GUEST_INACTIVE_DAYS = 90;

/**
 * Daily. Deletes anonymous (guest) accounts that haven't been seen for
 * GUEST_INACTIVE_DAYS. `lastSeen` + `isAnonymous` are stamped on the player
 * doc by the `recordActivity` callable. Deleting the Auth user fires
 * `onUserDeleted`, which purges the player/profile/membership docs via the
 * shared deletion checklist — so we only need to delete the Auth user here.
 *
 * Only `isAnonymous == true` docs are touched; real (email) accounts are
 * never auto-deleted for inactivity. Processed in pages of 200.
 */
export const purgeStaleGuests = onSchedule('every 24 hours', async () => {
  const cutoff = new Date(Date.now() - GUEST_INACTIVE_DAYS * 86400_000);

  for (;;) {
    const snap = await db
      .collection('players')
      .where('isAnonymous', '==', true)
      .where('lastSeen', '<', cutoff)
      .limit(200)
      .get();
    if (snap.empty) break;

    // deleteUser throws on an already-removed uid (orphan doc) — allSettled
    // swallows it; the next run's query simply won't re-find deleted docs.
    await Promise.allSettled(snap.docs.map((doc) => adminAuth.deleteUser(doc.id)));

    if (snap.size < 200) break;
  }
});

// ─── syncParticipantSnapshots ─────────────────────────────────────────────────

/**
 * Hourly. Propagates display name / avatar changes to the denormalized
 * `participants.{uid}` snapshot on every challenge the user has joined.
 *
 * `updateProfile` marks the player doc with `participantSyncPending: true`
 * when displayName or avatarSeed changes. This sweep processes only those
 * docs, keeping costs near-zero when no one has renamed.
 *
 * Idempotent: re-delivery rewrites the same values and re-clears the flag.
 */
export const syncParticipantSnapshots = onSchedule('every 60 minutes', async () => {
  for (;;) {
    const snap = await db
      .collection('players')
      .where('participantSyncPending', '==', true)
      .limit(200)
      .get();
    if (snap.empty) break;

    await Promise.allSettled(
      snap.docs.map(async (playerDoc) => {
        const uid = playerDoc.id;
        const data = playerDoc.data();
        const displayName: string = data.displayName ?? 'Someone';
        const avatarSeed: string = data.avatarSeed ?? uid;

        const playerDocs = await db.collectionGroup('players').where('uid', '==', uid).get();
        const challengeIds = [
          ...new Set(
            playerDocs.docs
              .map((d) => d.ref.parent.parent?.id)
              .filter((id): id is string => id != null),
          ),
        ];

        for (const ids of chunk(challengeIds, 499)) {
          const batch = db.batch();
          for (const cid of ids) {
            batch.update(db.doc(`challenges/${cid}`), {
              [`participants.${uid}.displayName`]: displayName,
              [`participants.${uid}.avatarSeed`]: avatarSeed,
            });
          }
          batch.update(playerDoc.ref, { participantSyncPending: FieldValue.delete() });
          await batch.commit();
        }

        // If the user has no joined challenges, just clear the flag.
        if (challengeIds.length === 0) {
          await playerDoc.ref.update({ participantSyncPending: FieldValue.delete() });
        }
      }),
    );

    if (snap.size < 200) break;
  }
});

// ─── helpers ──────────────────────────────────────────────────────────────────

function chunk<T>(arr: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}
