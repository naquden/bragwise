import { onCall, CallableRequest } from 'firebase-functions/v2/https';
import { HttpsError } from 'firebase-functions/v2/https';
import { setGlobalOptions } from 'firebase-functions/v2';
import { sendToUser, CHANNEL_SOCIAL, CHANNEL_CHALLENGES, CHANNEL_INVITES } from './push';

// Co-locate all 2nd-gen functions with Firestore (europe-west1). Without this,
// onCall + onSchedule default to us-central1 — every callable would do a
// cross-Atlantic round-trip on every Firestore read/write. The auth-blocking
// `onUserDeleted` is a 1st-gen function with a constrained region (us-east1)
// and is unaffected.
setGlobalOptions({ region: 'europe-west1' });

function locksAtMillis(v: unknown): number | null {
  if (v == null) return null;
  if (typeof v === 'string') {
    const ms = Date.parse(v);
    return Number.isNaN(ms) ? null : ms;
  }
  if (typeof (v as { toMillis?: () => number }).toMillis === 'function') {
    return (v as { toMillis: () => number }).toMillis();
  }
  return null;
}

import {
  audit,
  rateLimit,
  requireAuth,
  requireVerifiedEmail,
  validate,
  verifyAppCheck,
} from './lib/middleware';
import { db, FieldValue, Timestamp, auth as adminAuth } from './lib/admin';
import { validateResults, validatePredictionMap } from './predictionValidation';
import { computeLeaderboard, competitionRanks } from './leaderboard';
import { Bet, PredictionPayload } from './scoring';
import {
  ClaimHandleSchema,
  CreateChallengeSchema,
  DeleteChallengeSchema,
  FriendRequestActionSchema,
  InviteFriendsSchema,
  MigrateGuestDataSchema,
  PostResultsSchema,
  RegisterPushTokenSchema,
  SetNotificationPrefSchema,
  SendFriendRequestSchema,
  SetReactionSchema,
  SubmitPredictionsSchema,
  UnfriendSchema,
  WithdrawFriendRequestSchema,
  UpdateProfileSchema,
  RecomputeLeaderboardSchema,
} from './schemas';
import { applyTransition } from './lib/friendships';

// ─── applyHandleChange ───────────────────────────────────────────────────────
// Shared transaction helper: claim newHandle for uid, releasing previous handle.
// Throws HttpsError('already-exists', 'handle-taken') if taken by another user.

async function applyHandleChange(
  tx: FirebaseFirestore.Transaction,
  uid: string,
  newHandle: string,
  profileRef: FirebaseFirestore.DocumentReference,
): Promise<void> {
  const handleRef = db.doc(`handles/${newHandle}`);
  const [handleSnap, profileSnap] = await Promise.all([
    tx.get(handleRef),
    tx.get(profileRef),
  ]);
  if (handleSnap.exists && (handleSnap.data()!.uid as string) !== uid) {
    throw new HttpsError('already-exists', 'handle-taken');
  }
  const prev = profileSnap.exists ? (profileSnap.data()!.handle as string | undefined) : undefined;
  if (prev && prev !== newHandle) {
    tx.delete(db.doc(`handles/${prev}`));
  }
  tx.set(handleRef, { uid, claimedAt: FieldValue.serverTimestamp() });
}

// ─── claimHandle ────────────────────────────────────────────────────────────
// Standalone first-claim / onboarding flow. Edit-profile uses updateProfile.

export const claimHandle = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'claimHandle', 86400, 10);
  const { handle } = validate(ClaimHandleSchema, req.data);

  const profileRef = db.doc(`publicProfiles/${uid}`);
  const playerRef = db.doc(`players/${uid}`);

  await db.runTransaction(async (tx) => {
    await applyHandleChange(tx, uid, handle, profileRef);
    tx.set(profileRef, { handle }, { merge: true });
    tx.set(playerRef, { handle }, { merge: true });
  });
});

// ─── updateProfile ───────────────────────────────────────────────────────────
// Single transactional write covering handle claim + publicProfiles + players.
// Throws field-tagged HttpsError messages so the client can route to the right field.

export const updateProfile = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'updateProfile', 3600, 10);
  const payload = validate(UpdateProfileSchema, req.data);

  const profileRef = db.doc(`publicProfiles/${uid}`);
  const playerRef = db.doc(`players/${uid}`);

  const updates: Record<string, unknown> = {};
  if (payload.displayName !== undefined) updates.displayName = payload.displayName;
  if (payload.handle !== undefined) updates.handle = payload.handle;
  if (payload.avatarSeed !== undefined) updates.avatarSeed = payload.avatarSeed;
  updates.updatedAt = FieldValue.serverTimestamp();

  if (Object.keys(updates).length <= 1) return;

  const playerUpdates = { ...updates };
  if (payload.displayName !== undefined || payload.avatarSeed !== undefined) {
    playerUpdates.participantSyncPending = true;
  }

  await db.runTransaction(async (tx) => {
    if (payload.handle !== undefined) {
      await applyHandleChange(tx, uid, payload.handle, profileRef);
    }
    tx.set(profileRef, updates, { merge: true });
    tx.set(playerRef, playerUpdates, { merge: true });
  });
});

const ACTIVE_CHALLENGE_CAP = 30;

// ─── createChallenge ─────────────────────────────────────────────────────────
// Creates the challenge as OPEN in a single transaction (cap check + player doc).
// Drafts are now local-only on the client; publishing is a single server call.

export const createChallenge = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'createChallenge', 3600, 10);
  const payload = validate(CreateChallengeSchema, req.data);
  await audit(uid, 'createChallenge', { title: payload.title, visibility: payload.visibility });

  const ref = db.collection('challenges').doc();
  const counterRef = db.doc(`players/${uid}/private/counters`);
  const socialRef = db.doc(`players/${uid}/private/social`);

  let eligibleInvitees: string[] = [];

  await db.runTransaction(async (tx) => {
    const [counterSnap, socialSnap] = await Promise.all([
      tx.get(counterRef),
      tx.get(socialRef),
    ]);
    const active: number = counterSnap.exists
      ? (counterSnap.data()!.activeChallenges ?? 0)
      : 0;

    if (active >= ACTIVE_CHALLENGE_CAP) {
      // Drift repair: re-read the live aggregation count before hard-rejecting.
      const liveSnap = await db
        .collection('challenges')
        .where('createdBy', '==', uid)
        .where('resultsPostedAt', '==', null)
        .count()
        .get();
      const liveCount = liveSnap.data().count;
      if (liveCount >= ACTIVE_CHALLENGE_CAP) {
        throw new HttpsError('resource-exhausted', 'challenge-cap-reached');
      }
      // Counter drifted low — repair it inline and proceed.
      tx.set(counterRef, { activeChallenges: liveCount }, { merge: true });
    }

    // Resolve invitation eligibility inside the transaction for consistency.
    const requestedUids: string[] = payload.invitedUids ?? [];
    if (requestedUids.length > 0) {
      const friends: Record<string, unknown> = socialSnap.exists
        ? (socialSnap.data()!.friends ?? {})
        : {};
      eligibleInvitees = requestedUids.filter((u) => u in friends);
      if (payload.visibility === 'INVITE_ONLY' && eligibleInvitees.length === 0) {
        throw new HttpsError('failed-precondition', 'invite-only-no-reachable-invitees');
      }
    }

    // Strip client-supplied invite list from the persisted challenge doc.
    const { invitedUids: _invitedUids, ...challengeFields } = payload;
    tx.set(ref, {
      ...challengeFields,
      locksAt: Timestamp.fromMillis(Date.parse(payload.locksAt)),
      id: ref.id,
      createdBy: uid,
      createdAt: FieldValue.serverTimestamp(),
      publishedAt: FieldValue.serverTimestamp(),
      status: 'OPEN',
      joinedCount: 0,
      promoted: false,
      leaderboard: null,
      resultsPostedAt: null,
    });
    tx.set(counterRef, { activeChallenges: FieldValue.increment(1) }, { merge: true });

    // Write invitation docs atomically with the challenge.
    for (const inviteeUid of eligibleInvitees) {
      tx.set(db.doc(`challenges/${ref.id}/invitations/${inviteeUid}`), {
        invitedUid: inviteeUid,
        invitedBy: uid,
        invitedAt: FieldValue.serverTimestamp(),
      });
    }
  });

  // Push notifications are best-effort and sent after the transaction commits.
  if (eligibleInvitees.length > 0) {
    const [inviterSnap, challengeSnap] = await Promise.all([
      db.doc(`players/${uid}`).get(),
      db.doc(`challenges/${ref.id}`).get(),
    ]);
    const inviterName: string = inviterSnap.data()?.displayName ?? 'Someone';
    const challengeTitle: string = challengeSnap.data()?.title ?? 'a challenge';
    await Promise.all(
      eligibleInvitees.map((targetUid) =>
        sendToUser(targetUid, {
          title: "You're invited!",
          body: `${inviterName} invited you to ${challengeTitle}`,
          channel: CHANNEL_INVITES,
          deepLink: `https://bragwise.firebaseapp.com/c/${ref.id}`,
        }).catch(() => {}),
      ),
    );
  }

  return { challengeId: ref.id, invited: String(eligibleInvitees.length), requested: String((payload.invitedUids ?? []).length) };
});

// ─── applyPredictions (shared core) ──────────────────────────────────────────

async function applyPredictions(
  tx: FirebaseFirestore.Transaction,
  uid: string,
  challengeId: string,
  predMap: Record<string, PredictionPayload>,
  opts?: { skipIfExists?: boolean },
): Promise<void> {
  const challengeRef = db.doc(`challenges/${challengeId}`);
  const playerRef = db.doc(`challenges/${challengeId}/players/${uid}`);

  const [challengeSnap, playerSnap] = await Promise.all([
    tx.get(challengeRef),
    tx.get(playerRef),
  ]);

  if (!challengeSnap.exists) throw new HttpsError('not-found', 'challenge-not-found');
  const challenge = challengeSnap.data()!;

  if (challenge.status !== 'OPEN') {
    throw new HttpsError('failed-precondition', 'challenge-not-open');
  }
  const locksAtMs = locksAtMillis(challenge.locksAt);
  if (locksAtMs == null) throw new HttpsError('failed-precondition', 'no-locks-at');
  if (locksAtMs <= Date.now()) {
    throw new HttpsError('failed-precondition', 'challenge-locked');
  }

  // FRIENDS and PROMOTED both use bearer-capability model: knowing the unguessable
  // 20-char challenge ID is the share token. No friendship check needed.
  const isEligible =
    challenge.createdBy === uid ||
    challenge.visibility === 'PROMOTED' ||
    challenge.visibility === 'FRIENDS' ||
    playerSnap.exists ||
    (await tx.get(db.doc(`challenges/${challengeId}/invitations/${uid}`))).exists;

  if (!isEligible) throw new HttpsError('permission-denied', 'not-eligible');

  const bets: Bet[] = challenge.bets ?? [];
  validatePredictionMap(bets, predMap);

  if (!playerSnap.exists) {
    tx.set(playerRef, {
      uid,
      joinedAt: FieldValue.serverTimestamp(),
      predictions: predMap,
      isCreator: challenge.createdBy === uid,
    });
  } else if (opts?.skipIfExists) {
    throw new HttpsError('already-exists', 'already-predicted');
  } else {
    tx.update(playerRef, { predictions: predMap, updatedAt: FieldValue.serverTimestamp() });
  }
}

// ─── submitPredictions ───────────────────────────────────────────────────────

export const submitPredictions = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  // Anonymous guests have a real uid and can submit — email verification is NOT required.
  const uid = requireAuth(req);
  await rateLimit(uid, 'submitPredictions', 3600, 600);
  const { challengeId, predictions } = validate(SubmitPredictionsSchema, req.data);

  const predMap: Record<string, PredictionPayload> = {};
  for (const p of predictions) predMap[p.betId] = p.payload as PredictionPayload;

  await db.runTransaction((tx) => applyPredictions(tx, uid, challengeId, predMap));
});

// ─── recomputeLeaderboardTx ──────────────────────────────────────────────────

/**
 * Reads all player docs (excluding `excludeUid` if supplied) and recomputes
 * `leaderboard` + `rankedLeaderboard` from the challenge's stored results.
 * Returns the two maps; callers apply them via tx.update / tx.set.
 */
async function recomputeLeaderboardTx(
  tx: FirebaseFirestore.Transaction,
  challengeRef: FirebaseFirestore.DocumentReference,
  data: FirebaseFirestore.DocumentData,
  excludeUid?: string,
): Promise<{ leaderboard: Record<string, number>; rankedLeaderboard: Record<string, number> }> {
  const bets: Bet[] = data.bets ?? [];
  const results = (data.results ?? {}) as Record<string, PredictionPayload>;
  const playersSnap = await tx.get(challengeRef.collection('players'));
  const players = playersSnap.docs
    .filter((d) => d.id !== excludeUid)
    .map((d) => ({
      uid: (d.data().uid ?? d.id) as string,
      predictions: (d.data().predictions ?? {}) as Record<string, PredictionPayload>,
    }));
  const leaderboard = computeLeaderboard(bets, players, results);
  const ranked = competitionRanks(leaderboard);
  const rankedLeaderboard: Record<string, number> = {};
  for (const e of ranked) rankedLeaderboard[e.uid] = e.rank;
  return { leaderboard, rankedLeaderboard };
}

// ─── postResults ─────────────────────────────────────────────────────────────

export const postResults = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'postResults', 3600, 20);
  const { challengeId, results } = validate(PostResultsSchema, req.data);
  await audit(uid, 'postResults', { challengeId });

  const ref = db.doc(`challenges/${challengeId}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError('not-found', 'challenge-not-found');
    const data = snap.data()!;
    if (data.createdBy !== uid) throw new HttpsError('permission-denied', 'not-creator');

    // Creator may post results at any time, ending the challenge early.
    // No deadline gate — posting transitions status straight to RESULTS_POSTED.

    const bets: Bet[] = data.bets ?? [];
    validateResults(bets, results as Record<string, PredictionPayload>);

    // Read all players in-transaction to compute leaderboard.
    const playersSnap = await tx.get(db.collection(`challenges/${challengeId}/players`));
    const players = playersSnap.docs.map((d) => ({
      uid: (d.data().uid ?? d.id) as string,
      predictions: (d.data().predictions ?? {}) as Record<string, PredictionPayload>,
    }));

    const leaderboard = computeLeaderboard(bets, players, results as Record<string, PredictionPayload>);
    const ranked = competitionRanks(leaderboard);
    const rankedLeaderboard: Record<string, number> = {};
    for (const e of ranked) rankedLeaderboard[e.uid] = e.rank;

    const isFirstPost = data.resultsPostedAt == null;

    tx.update(ref, {
      results,
      resultsPostedAt: FieldValue.serverTimestamp(),
      status: 'RESULTS_POSTED',
      leaderboard,
      rankedLeaderboard,
    });

    if (isFirstPost) {
      const counterRef = db.doc(`players/${uid}/private/counters`);
      tx.set(counterRef, { activeChallenges: FieldValue.increment(-1) }, { merge: true });
    }
  });
});

// ─── deleteChallenge ─────────────────────────────────────────────────────────

export const deleteChallenge = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'deleteChallenge', 3600, 10);
  const { challengeId } = validate(DeleteChallengeSchema, req.data);
  await audit(uid, 'deleteChallenge', { challengeId });

  const challengeRef = db.doc(`challenges/${challengeId}`);
  const counterRef = db.doc(`players/${uid}/private/counters`);
  let creatorUid = uid;

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(challengeRef);
    if (!snap.exists) throw new HttpsError('not-found', 'challenge-not-found');
    const data = snap.data()!;
    creatorUid = data.createdBy as string;
    if (creatorUid !== uid) throw new HttpsError('permission-denied', 'not-creator');
    if (data.resultsPostedAt !== null && data.resultsPostedAt !== undefined) {
      throw new HttpsError('failed-precondition', 'results-posted');
    }
    tx.set(counterRef, { activeChallenges: FieldValue.increment(-1) }, { merge: true });
  });

  // Purge subcollections then the challenge doc itself.
  const [playersSnap, invitationsSnap, reactionsSnap] = await Promise.all([
    challengeRef.collection('players').listDocuments(),
    challengeRef.collection('invitations').listDocuments(),
    challengeRef.collection('reactions').listDocuments(),
  ]);
  const allRefs = [...playersSnap, ...invitationsSnap, ...reactionsSnap, challengeRef];
  const BATCH_SIZE = 500;
  for (let i = 0; i < allRefs.length; i += BATCH_SIZE) {
    const batch = db.batch();
    for (const ref of allRefs.slice(i, i + BATCH_SIZE)) batch.delete(ref);
    await batch.commit();
  }
});

// ─── recomputeLeaderboard ─────────────────────────────────────────────────────

/**
 * Lets the challenge creator recompute scores and ranks after results have
 * already been posted — for example after a member deletion leaves stale ranks,
 * or to correct a calculation. Does NOT bump `resultsPostedAt`, so
 * `onResultsPosted` (push notifications / H2H) will not re-fire.
 */
export const recomputeLeaderboard = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'recomputeLeaderboard', 3600, 30);
  const { challengeId } = validate(RecomputeLeaderboardSchema, req.data);
  await audit(uid, 'recomputeLeaderboard', { challengeId });

  const ref = db.doc(`challenges/${challengeId}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError('not-found', 'challenge-not-found');
    const data = snap.data()!;
    if (data.createdBy !== uid) throw new HttpsError('permission-denied', 'not-creator');
    if (data.resultsPostedAt == null) throw new HttpsError('failed-precondition', 'results-not-posted');

    const { leaderboard, rankedLeaderboard } = await recomputeLeaderboardTx(tx, ref, data);
    tx.update(ref, { leaderboard, rankedLeaderboard });
  });
});

// ─── inviteFriends ────────────────────────────────────────────────────────────

export const inviteFriends = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'inviteFriends', 3600, 30);
  const { challengeId, uids } = validate(InviteFriendsSchema, req.data);

  const challengeRef = db.doc(`challenges/${challengeId}`);
  const socialRef = db.doc(`players/${uid}/private/social`);

  const [challengeSnap, socialSnap] = await Promise.all([
    challengeRef.get(),
    socialRef.get(),
  ]);

  if (!challengeSnap.exists) throw new HttpsError('not-found', 'challenge-not-found');
  const challenge = challengeSnap.data()!;
  if (challenge.createdBy !== uid) throw new HttpsError('permission-denied', 'not-creator');
  if (challenge.status !== 'OPEN') throw new HttpsError('failed-precondition', 'challenge-not-open');
  const inviteLocksAt = locksAtMillis(challenge.locksAt);
  if (inviteLocksAt !== null && inviteLocksAt <= Date.now()) throw new HttpsError('failed-precondition', 'challenge-locked');

  const friends: Record<string, unknown> = socialSnap.exists
    ? (socialSnap.data()!.friends ?? {})
    : {};

  const eligibleUids = uids.filter((targetUid) => targetUid in friends);
  if (eligibleUids.length === 0) return;

  const batch = db.batch();
  for (const targetUid of eligibleUids) {
    batch.set(db.doc(`challenges/${challengeId}/invitations/${targetUid}`), {
      invitedUid: targetUid,
      invitedBy: uid,
      invitedAt: FieldValue.serverTimestamp(),
    });
  }
  await batch.commit();

  const inviterSnap = await db.doc(`players/${uid}`).get();
  const inviterName: string = inviterSnap.data()?.displayName ?? 'Someone';
  const challengeTitle: string = challenge.title ?? 'a challenge';
  await Promise.all(
    eligibleUids.map((targetUid) =>
      sendToUser(targetUid, {
        title: "You're invited!",
        body: `${inviterName} invited you to ${challengeTitle}`,
        channel: CHANNEL_INVITES,
        deepLink: `https://bragwise.firebaseapp.com/c/${challengeId}`,
      }).catch(() => {}),
    ),
  );
});

// ─── sendFriendRequest ────────────────────────────────────────────────────────

export const sendFriendRequest = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'sendFriendRequest', 86400, 50);
  const { handle } = validate(SendFriendRequestSchema, req.data);

  const handleSnap = await db.doc(`handles/${handle}`).get();
  if (!handleSnap.exists) throw new HttpsError('not-found', 'handle-not-found');
  const targetUid = handleSnap.data()!.uid as string;

  if (targetUid === uid) throw new HttpsError('invalid-argument', 'cannot-friend-self');

  await db.runTransaction(async (tx) => {
    await applyTransition(tx, uid, targetUid, 'send');
  });

  // Notify target — best-effort, outside the transaction.
  const senderSnap = await db.doc(`players/${uid}`).get();
  const senderName: string = senderSnap.data()?.displayName ?? 'Someone';
  await sendToUser(targetUid, {
    title: 'New friend request',
    body: `${senderName} wants to be your friend`,
    channel: CHANNEL_SOCIAL,
    deepLink: 'https://bragwise.firebaseapp.com/requests',
  }).catch(() => {/* best-effort */});
});

// ─── acceptFriendRequest ──────────────────────────────────────────────────────

export const acceptFriendRequest = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'acceptFriendRequest', 3600, 100);
  const { requesterUid } = validate(FriendRequestActionSchema, req.data);

  await db.runTransaction(async (tx) => {
    await applyTransition(tx, uid, requesterUid, 'accept');
  });
});

// ─── declineFriendRequest ─────────────────────────────────────────────────────

export const declineFriendRequest = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'declineFriendRequest', 3600, 100);
  const { requesterUid } = validate(FriendRequestActionSchema, req.data);

  await db.runTransaction(async (tx) => {
    await applyTransition(tx, uid, requesterUid, 'decline');
  });
});

// ─── withdrawFriendRequest ────────────────────────────────────────────────────

export const withdrawFriendRequest = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'withdrawFriendRequest', 3600, 100);
  const { otherUid } = validate(WithdrawFriendRequestSchema, req.data);

  await db.runTransaction(async (tx) => {
    await applyTransition(tx, uid, otherUid, 'withdraw');
  });
});

// ─── unfriend ─────────────────────────────────────────────────────────────────

export const unfriend = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'unfriend', 3600, 50);
  const { otherUid } = validate(UnfriendSchema, req.data);

  await db.runTransaction(async (tx) => {
    await applyTransition(tx, uid, otherUid, 'unfriend');
  });
});

// ─── deleteAccount ────────────────────────────────────────────────────────────

export const deleteAccount = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'deleteAccount', 86400, 1);
  await audit(uid, 'deleteAccount', {});

  // Create the checklist first so the trigger / reconciler can resume
  // if anything fails mid-way.
  const checklistRef = db.doc(`deletionRequests/${uid}`);
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
      auth_user: 'pending',
    },
  });

  // Deleting the Auth user fires onUserDeleted which processes the checklist.
  await adminAuth.deleteUser(uid);
});

// ─── migrateGuestData ─────────────────────────────────────────────────────────

export const migrateGuestData = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'migrateGuestData', 86400, 1);
  const { predictions } = validate(MigrateGuestDataSchema, req.data);

  // Group by challengeId — each challenge migrates as one complete submission.
  // A partial set (missing bets) is rejected by validatePredictionMap → counted as failed.
  const byChallenge = new Map<string, Record<string, PredictionPayload>>();
  for (const { challengeId, betId, payload } of predictions) {
    if (!byChallenge.has(challengeId)) byChallenge.set(challengeId, {});
    byChallenge.get(challengeId)![betId] = payload as PredictionPayload;
  }

  const challengeIds = [...byChallenge.keys()];
  const results = await Promise.allSettled(
    challengeIds.map((challengeId) =>
      db.runTransaction((tx) =>
        applyPredictions(tx, uid, challengeId, byChallenge.get(challengeId)!, { skipIfExists: true }),
      ),
    ),
  );

  const migrated: string[] = [];
  const skipped: string[] = [];
  const failed: string[] = [];
  results.forEach((result, i) => {
    const challengeId = challengeIds[i];
    if (result.status === 'fulfilled') {
      migrated.push(challengeId);
    } else if (
      result.reason instanceof HttpsError &&
      result.reason.code === 'already-exists'
    ) {
      skipped.push(challengeId);
    } else {
      failed.push(challengeId);
    }
  });
  return { migrated, skipped, failed };
});

// ─── registerPushToken ────────────────────────────────────────────────────────

export const registerPushToken = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'registerPushToken', 86400, 100);
  const { token, platform } = validate(RegisterPushTokenSchema, req.data);

  await db.doc(`players/${uid}/pushTokens/${token}`).set({
    token,
    platform,
    updatedAt: FieldValue.serverTimestamp(),
  });
  return { ok: true };
});

// ─── setNotificationPref ──────────────────────────────────────────────────────

export const setNotificationPref = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'setNotificationPref', 3600, 50);
  const { enabled, categories } = validate(SetNotificationPrefSchema, req.data);

  const patch: Record<string, unknown> = { updatedAt: FieldValue.serverTimestamp() };
  if (enabled !== undefined) patch['notifications'] = enabled;
  // Use a nested object (not dotted keys): set(..., {merge:true}) treats "categories.x"
  // as a literal field name, leaving the real nested map untouched. merge deep-merges
  // nested maps, so sibling category keys are preserved.
  if (categories) patch['categories'] = categories;

  await db.doc(`players/${uid}/private/preferences`).set(patch, { merge: true });
  return { ok: true };
});

// ─── Handle auto-assignment helpers ──────────────────────────────────────────

const HANDLE_ADJECTIVES = [
  'brave', 'swift', 'lucky', 'sunny', 'clever', 'sharp', 'quick', 'bold',
  'cool', 'calm', 'kind', 'wild', 'bright', 'happy', 'proud', 'witty',
  'keen', 'free', 'great', 'fair',
];

const HANDLE_NOUNS = [
  'fox', 'otter', 'panda', 'tiger', 'eagle', 'wolf', 'bear', 'shark',
  'hawk', 'lynx', 'raven', 'bison', 'moose', 'koala', 'finch', 'drake',
  'crane', 'viper', 'newt', 'toad',
];

function randomHandleCandidate(): string {
  const adj = HANDLE_ADJECTIVES[Math.floor(Math.random() * HANDLE_ADJECTIVES.length)];
  const noun = HANDLE_NOUNS[Math.floor(Math.random() * HANDLE_NOUNS.length)];
  const digits = Math.floor(1000 + Math.random() * 9000);
  return `${adj}${noun}${digits}`;
}

/**
 * Transactionally claims a unique random handle for `uid`. Retries up to
 * `maxAttempts` times on collision. No-ops if the account already has a handle
 * (guard against concurrent assignment). Best-effort: callers should catch.
 */
async function assignRandomHandle(uid: string, maxAttempts = 10): Promise<void> {
  const playerRef = db.doc(`players/${uid}`);
  const profileRef = db.doc(`publicProfiles/${uid}`);

  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const candidate = randomHandleCandidate();
    const handleRef = db.doc(`handles/${candidate}`);

    try {
      await db.runTransaction(async (tx) => {
        const [playerSnap, handleSnap] = await Promise.all([tx.get(playerRef), tx.get(handleRef)]);

        // Another process already assigned a handle — nothing to do.
        if (playerSnap.exists && playerSnap.data()!.handle) return;

        // Collision: this candidate is already taken — signal a retry.
        if (handleSnap.exists) throw new Error('handle-collision');

        tx.set(handleRef, { uid, claimedAt: FieldValue.serverTimestamp() });
        tx.set(profileRef, { handle: candidate }, { merge: true });
        tx.set(playerRef, { handle: candidate }, { merge: true });
      });
      return;
    } catch (err) {
      if (err instanceof Error && err.message === 'handle-collision') continue;
      throw err;
    }
  }
}

// ─── setReaction ──────────────────────────────────────────────────────────────

/**
 * Set or clear the calling user's smiley reaction on a challenge.
 * emoji == null clears the reaction. Toggling to the same emoji also clears.
 * No email verification — guests can react (same as submitPredictions).
 */
export const setReaction = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'setReaction', 3600, 600);
  const { challengeId, emoji } = validate(SetReactionSchema, req.data);

  const challengeRef = db.doc(`challenges/${challengeId}`);
  const reactionRef = db.doc(`challenges/${challengeId}/reactions/${uid}`);

  await db.runTransaction(async (tx) => {
    const [challengeSnap, playerSnap, inviteSnap] = await Promise.all([
      tx.get(challengeRef),
      tx.get(db.doc(`challenges/${challengeId}/players/${uid}`)),
      tx.get(db.doc(`challenges/${challengeId}/invitations/${uid}`)),
    ]);

    if (!challengeSnap.exists) throw new HttpsError('not-found', 'challenge-not-found');
    const challenge = challengeSnap.data()!;

    const isEligible =
      challenge.createdBy === uid ||
      challenge.visibility === 'PROMOTED' ||
      challenge.visibility === 'FRIENDS' ||
      playerSnap.exists ||
      inviteSnap.exists;

    if (!isEligible) throw new HttpsError('permission-denied', 'not-eligible');

    if (emoji == null) {
      tx.delete(reactionRef);
    } else {
      tx.set(reactionRef, { uid, emoji, updatedAt: FieldValue.serverTimestamp() });
    }
  });
});

// ─── recordActivity ───────────────────────────────────────────────────────────

/**
 * Heartbeat called on app launch (and right after guest sign-in) for any
 * authenticated user, anonymous guests included. Stamps `lastSeen` on the
 * player doc and records whether the session is anonymous — the
 * `purgeStaleGuests` job uses both fields to delete guest accounts that have
 * been inactive for 90 days. No request payload.
 */
export const recordActivity = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'recordActivity', 3600, 30);

  const provider = (req.auth!.token as Record<string, unknown>)?.firebase as
    | { sign_in_provider?: string }
    | undefined;
  const isAnonymous = provider?.sign_in_provider === 'anonymous';

  const playerRef = db.doc(`players/${uid}`);
  const playerSnap = await playerRef.get();
  const hasHandle = !!(playerSnap.exists && playerSnap.data()!.handle);

  await playerRef.set(
    { lastSeen: FieldValue.serverTimestamp(), isAnonymous },
    { merge: true },
  );

  // Auto-assign a unique random handle for real (non-guest) accounts that
  // don't have one yet. Best-effort — never fails the heartbeat response.
  if (!isAnonymous && !hasHandle) {
    assignRandomHandle(uid).catch((err) =>
      console.error(`assignRandomHandle failed for uid=${uid}:`, err),
    );
  }

  return { ok: true };
});

export * from './triggers';
export * from './landing';
