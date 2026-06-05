import { onCall, CallableRequest } from 'firebase-functions/v2/https';
import { HttpsError } from 'firebase-functions/v2/https';
import { setGlobalOptions } from 'firebase-functions/v2';
import { sendToUser, CHANNEL_SOCIAL, CHANNEL_CHALLENGES } from './push';

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
import { db, FieldValue, auth as adminAuth } from './lib/admin';
import {
  ClaimHandleSchema,
  CreateChallengeSchema,
  DeleteChallengeSchema,
  FriendRequestActionSchema,
  InviteFriendsSchema,
  MigrateGuestDataSchema,
  PostResultsSchema,
  PublishChallengeSchema,
  RegisterPushTokenSchema,
  SetNotificationPrefSchema,
  SendFriendRequestSchema,
  SubmitPredictionsSchema,
  UnfriendSchema,
  UpdateDraftSchema,
  UpdateProfileSchema,
} from './schemas';

// ─── claimHandle ────────────────────────────────────────────────────────────

export const claimHandle = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'claimHandle', 86400, 10);
  const { handle } = validate(ClaimHandleSchema, req.data);

  const handleRef = db.doc(`handles/${handle}`);
  const profileRef = db.doc(`publicProfiles/${uid}`);
  const playerRef = db.doc(`players/${uid}`);

  await db.runTransaction(async (tx) => {
    const [handleSnap, profileSnap] = await Promise.all([
      tx.get(handleRef),
      tx.get(profileRef),
    ]);

    if (handleSnap.exists) {
      const owner = handleSnap.data()!.uid as string | undefined;
      // Allow reclaiming own handle (idempotent).
      if (owner !== uid) {
        throw new HttpsError('already-exists', 'handle-taken');
      }
    }

    // Release previous handle if the player already had one.
    if (profileSnap.exists) {
      const prev = profileSnap.data()!.handle as string | undefined;
      if (prev && prev !== handle) {
        tx.delete(db.doc(`handles/${prev}`));
      }
    }

    tx.set(handleRef, { uid, claimedAt: FieldValue.serverTimestamp() });
    tx.set(profileRef, { handle }, { merge: true });
    tx.set(playerRef, { handle }, { merge: true });
  });
});

// ─── updateProfile ───────────────────────────────────────────────────────────

export const updateProfile = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'updateProfile', 3600, 10);
  const payload = validate(UpdateProfileSchema, req.data);

  const profileRef = db.doc(`publicProfiles/${uid}`);
  const playerRef = db.doc(`players/${uid}`);

  // Handle change is a sub-transaction — delegate to claimHandle logic.
  if (payload.handle !== undefined) {
    const handleRef = db.doc(`handles/${payload.handle}`);
    await db.runTransaction(async (tx) => {
      const [handleSnap, profileSnap] = await Promise.all([
        tx.get(handleRef),
        tx.get(profileRef),
      ]);
      if (handleSnap.exists && (handleSnap.data()!.uid as string) !== uid) {
        throw new HttpsError('already-exists', 'handle-taken');
      }
      const prev = profileSnap.exists ? (profileSnap.data()!.handle as string | undefined) : undefined;
      if (prev && prev !== payload.handle) {
        tx.delete(db.doc(`handles/${prev}`));
      }
      tx.set(handleRef, { uid, claimedAt: FieldValue.serverTimestamp() });
    });
  }

  const updates: Record<string, unknown> = {};
  if (payload.displayName !== undefined) updates.displayName = payload.displayName;
  if (payload.handle !== undefined) updates.handle = payload.handle;
  if (payload.avatarSeed !== undefined) updates.avatarSeed = payload.avatarSeed;
  updates.updatedAt = FieldValue.serverTimestamp();

  if (Object.keys(updates).length > 1) {
    await Promise.all([
      profileRef.set(updates, { merge: true }),
      playerRef.set(updates, { merge: true }),
    ]);
  }
});

const ACTIVE_CHALLENGE_CAP = 30;

// ─── createChallenge ─────────────────────────────────────────────────────────

export const createChallenge = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'createChallenge', 3600, 10);
  const payload = validate(CreateChallengeSchema, req.data);
  await audit(uid, 'createChallenge', { title: payload.title, visibility: payload.visibility });

  const ref = db.collection('challenges').doc();
  const counterRef = db.doc(`players/${uid}/private/counters`);

  await db.runTransaction(async (tx) => {
    const counterSnap = await tx.get(counterRef);
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

    tx.set(ref, {
      ...payload,
      id: ref.id,
      createdBy: uid,
      createdAt: FieldValue.serverTimestamp(),
      status: 'DRAFT',
      joinedCount: 0,
      promoted: false,
      trusted: false,
      leaderboard: null,
      resultsPostedAt: null,
    });
    tx.set(counterRef, { activeChallenges: FieldValue.increment(1) }, { merge: true });
  });

  return { challengeId: ref.id };
});

// ─── updateDraft ─────────────────────────────────────────────────────────────

export const updateDraft = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'updateDraft', 3600, 60);
  const payload = validate(UpdateDraftSchema, req.data);

  const ref = db.doc(`challenges/${payload.challengeId}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError('not-found', 'challenge-not-found');
    const data = snap.data()!;
    if (data.createdBy !== uid) throw new HttpsError('permission-denied', 'not-creator');
    if (data.status !== 'DRAFT') throw new HttpsError('failed-precondition', 'not-draft');

    const { challengeId: _id, ...fields } = payload;
    tx.update(ref, { ...fields, updatedAt: FieldValue.serverTimestamp() });
  });
});

// ─── publishChallenge ─────────────────────────────────────────────────────────

export const publishChallenge = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'publishChallenge', 3600, 10);
  const { challengeId } = validate(PublishChallengeSchema, req.data);
  await audit(uid, 'publishChallenge', { challengeId });

  const challengeRef = db.doc(`challenges/${challengeId}`);
  const playerRef = db.doc(`challenges/${challengeId}/players/${uid}`);

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(challengeRef);
    if (!snap.exists) throw new HttpsError('not-found', 'challenge-not-found');
    const data = snap.data()!;
    if (data.createdBy !== uid) throw new HttpsError('permission-denied', 'not-creator');
    if (data.status !== 'DRAFT') throw new HttpsError('failed-precondition', 'not-draft');

    tx.update(challengeRef, {
      status: 'OPEN',
      publishedAt: FieldValue.serverTimestamp(),
    });
    // Creator auto-joins without predictions — they're the one who posts results.
    tx.set(playerRef, {
      uid,
      joinedAt: FieldValue.serverTimestamp(),
      predictions: {},
      isCreator: true,
    });
  });
});

// ─── submitPredictions ───────────────────────────────────────────────────────

export const submitPredictions = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  // Anonymous guests have a real uid and can submit — email verification is NOT required.
  // App Check + rate limiting guard against abuse.
  const uid = requireAuth(req);
  await rateLimit(uid, 'submitPredictions', 3600, 600);
  const { challengeId, predictions } = validate(SubmitPredictionsSchema, req.data);

  const challengeRef = db.doc(`challenges/${challengeId}`);
  const playerRef = db.doc(`challenges/${challengeId}/players/${uid}`);

  await db.runTransaction(async (tx) => {
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

    // Eligibility: creator, existing member, existing invitee, PROMOTED, or
    // FRIENDS (join-by-link: anyone with the challengeId share link can participate).
    // INVITE_ONLY still requires an explicit invitation.
    const isEligible =
      challenge.createdBy === uid ||
      challenge.visibility === 'PROMOTED' ||
      challenge.visibility === 'FRIENDS' ||
      playerSnap.exists ||
      (await tx.get(db.doc(`challenges/${challengeId}/invitations/${uid}`))).exists;

    if (!isEligible) throw new HttpsError('permission-denied', 'not-eligible');

    const bets: Array<{ id: string }> = challenge.bets ?? [];
    const predMap: Record<string, unknown> = {};
    for (const p of predictions) predMap[p.betId] = p.payload;

    if (!playerSnap.exists) {
      // First submission must cover every bet.
      if (predictions.length !== bets.length) {
        throw new HttpsError('invalid-argument', 'incomplete-predictions');
      }
      tx.set(playerRef, {
        uid,
        joinedAt: FieldValue.serverTimestamp(),
        predictions: predMap,
        isCreator: false,
      });
    } else {
      // Edit — dot-path update only the submitted bets.
      const updates: Record<string, unknown> = { updatedAt: FieldValue.serverTimestamp() };
      for (const [betId, payload] of Object.entries(predMap)) {
        updates[`predictions.${betId}`] = payload;
      }
      tx.update(playerRef, updates);
    }
  });
});

// ─── postResults ─────────────────────────────────────────────────────────────

export const postResults = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'postResults', 3600, 10);
  const { challengeId, results } = validate(PostResultsSchema, req.data);
  await audit(uid, 'postResults', { challengeId });

  const ref = db.doc(`challenges/${challengeId}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError('not-found', 'challenge-not-found');
    const data = snap.data()!;
    if (data.createdBy !== uid) throw new HttpsError('permission-denied', 'not-creator');
    if (data.resultsPostedAt !== null && data.resultsPostedAt !== undefined) {
      throw new HttpsError('already-exists', 'results-already-posted');
    }
    tx.update(ref, {
      results,
      resultsPostedAt: FieldValue.serverTimestamp(),
      status: 'RESULTS_POSTED',
    });
    const counterRef = db.doc(`players/${uid}/private/counters`);
    tx.set(counterRef, { activeChallenges: FieldValue.increment(-1) }, { merge: true });
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
  const [playersSnap, invitationsSnap] = await Promise.all([
    challengeRef.collection('players').listDocuments(),
    challengeRef.collection('invitations').listDocuments(),
  ]);
  const allRefs = [...playersSnap, ...invitationsSnap, challengeRef];
  const BATCH_SIZE = 500;
  for (let i = 0; i < allRefs.length; i += BATCH_SIZE) {
    const batch = db.batch();
    for (const ref of allRefs.slice(i, i + BATCH_SIZE)) batch.delete(ref);
    await batch.commit();
  }
});

// ─── inviteFriends ────────────────────────────────────────────────────────────

export const inviteFriends = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
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

  const friends: Record<string, unknown> = socialSnap.exists
    ? (socialSnap.data()!.friends ?? {})
    : {};

  for (const targetUid of uids) {
    if (!(targetUid in friends)) {
      throw new HttpsError('invalid-argument', 'not-a-friend');
    }
  }

  const batch = db.batch();
  for (const targetUid of uids) {
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
    uids.map((targetUid) =>
      sendToUser(targetUid, {
        title: "You're invited!",
        body: `${inviterName} invited you to ${challengeTitle}`,
        channel: CHANNEL_CHALLENGES,
        deepLink: `https://bragwise.firebaseapp.com/c/${challengeId}`,
      }).catch(() => {}),
    ),
  );
});

// ─── sendFriendRequest ────────────────────────────────────────────────────────

export const sendFriendRequest = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'sendFriendRequest', 86400, 50);
  const { handle } = validate(SendFriendRequestSchema, req.data);

  // Resolve handle → uid.
  const handleSnap = await db.doc(`handles/${handle}`).get();
  if (!handleSnap.exists) throw new HttpsError('not-found', 'handle-not-found');
  const targetUid = handleSnap.data()!.uid as string;

  if (targetUid === uid) throw new HttpsError('invalid-argument', 'cannot-friend-self');

  const mySocialRef = db.doc(`players/${uid}/private/social`);
  const theirSocialRef = db.doc(`players/${targetUid}/private/social`);

  await db.runTransaction(async (tx) => {
    const [mySnap, theirSnap] = await Promise.all([
      tx.get(mySocialRef),
      tx.get(theirSocialRef),
    ]);
    const myData = mySnap.data() ?? {};
    const theirData = theirSnap.data() ?? {};
    const myFriends: Record<string, unknown> = myData.friends ?? {};

    if (targetUid in myFriends) {
      throw new HttpsError('already-exists', 'already-friends');
    }
    const theirRequestsIn: Record<string, unknown> = theirData.requestsIn ?? {};
    if (uid in theirRequestsIn) {
      throw new HttpsError('already-exists', 'request-already-sent');
    }

    const now = FieldValue.serverTimestamp();
    tx.set(mySocialRef, { requestsOut: { [targetUid]: now } }, { merge: true });
    tx.set(theirSocialRef, { requestsIn: { [uid]: now } }, { merge: true });
  });

  // Notify target — best-effort, outside the transaction.
  const senderSnap = await db.doc(`players/${uid}`).get();
  const senderName: string = senderSnap.data()?.displayName ?? 'Someone';
  await sendToUser(targetUid, {
    title: 'New friend request',
    body: `${senderName} wants to be your friend`,
    channel: CHANNEL_SOCIAL,
  }).catch(() => {/* best-effort */});
});

// ─── acceptFriendRequest ──────────────────────────────────────────────────────

export const acceptFriendRequest = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'acceptFriendRequest', 3600, 100);
  const { requesterUid } = validate(FriendRequestActionSchema, req.data);

  const mySocialRef = db.doc(`players/${uid}/private/social`);
  const theirSocialRef = db.doc(`players/${requesterUid}/private/social`);

  await db.runTransaction(async (tx) => {
    const mySnap = await tx.get(mySocialRef);
    const myData = mySnap.data() ?? {};
    const requestsIn: Record<string, unknown> = myData.requestsIn ?? {};

    if (!(requesterUid in requestsIn)) {
      throw new HttpsError('not-found', 'request-not-found');
    }

    const now = FieldValue.serverTimestamp();
    tx.set(mySocialRef, {
      friends: { [requesterUid]: now },
      requestsIn: { [requesterUid]: FieldValue.delete() },
    }, { merge: true });
    tx.set(theirSocialRef, {
      friends: { [uid]: now },
      requestsOut: { [uid]: FieldValue.delete() },
    }, { merge: true });
  });
});

// ─── declineFriendRequest ─────────────────────────────────────────────────────

export const declineFriendRequest = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'declineFriendRequest', 3600, 100);
  const { requesterUid } = validate(FriendRequestActionSchema, req.data);

  const mySocialRef = db.doc(`players/${uid}/private/social`);
  const theirSocialRef = db.doc(`players/${requesterUid}/private/social`);

  const batch = db.batch();
  batch.set(mySocialRef, { requestsIn: { [requesterUid]: FieldValue.delete() } }, { merge: true });
  batch.set(theirSocialRef, { requestsOut: { [uid]: FieldValue.delete() } }, { merge: true });
  await batch.commit();
});

// ─── unfriend ─────────────────────────────────────────────────────────────────

export const unfriend = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'unfriend', 3600, 50);
  const { otherUid } = validate(UnfriendSchema, req.data);

  const mySocialRef = db.doc(`players/${uid}/private/social`);
  const theirSocialRef = db.doc(`players/${otherUid}/private/social`);

  const batch = db.batch();
  batch.set(mySocialRef, { friends: { [otherUid]: FieldValue.delete() } }, { merge: true });
  batch.set(theirSocialRef, { friends: { [uid]: FieldValue.delete() } }, { merge: true });
  await batch.commit();
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

  // For each local prediction, attempt to submit it if the challenge is still
  // OPEN, not yet locked, and the user is eligible (PROMOTED) or invited.
  // Silent per-item failures are intentional — some challenges may be locked
  // or the user may not be eligible for FRIENDS/INVITE_ONLY ones.
  const results = await Promise.allSettled(
    predictions.map(async ({ challengeId, betId, payload }) => {
      const challengeRef = db.doc(`challenges/${challengeId}`);
      const playerRef = db.doc(`challenges/${challengeId}/players/${uid}`);

      await db.runTransaction(async (tx) => {
        const [challengeSnap, playerSnap, inviteSnap] = await Promise.all([
          tx.get(challengeRef),
          tx.get(playerRef),
          tx.get(db.doc(`challenges/${challengeId}/invitations/${uid}`)),
        ]);
        if (!challengeSnap.exists) throw new Error('not-found');
        const c = challengeSnap.data()!;
        if (c.status !== 'OPEN') throw new Error('not-open');
        const locksAtMs = locksAtMillis(c.locksAt);
        if (locksAtMs == null || locksAtMs <= Date.now()) throw new Error('locked');

        const eligible =
          c.createdBy === uid ||
          c.visibility === 'PROMOTED' ||
          playerSnap.exists ||
          inviteSnap.exists;
        if (!eligible) throw new Error('not-eligible');

        if (!playerSnap.exists) {
          tx.set(playerRef, {
            uid,
            joinedAt: FieldValue.serverTimestamp(),
            predictions: { [betId]: payload },
            isCreator: false,
          });
        } else {
          tx.update(playerRef, {
            [`predictions.${betId}`]: payload,
            updatedAt: FieldValue.serverTimestamp(),
          });
        }
      });
    }),
  );

  const migrated = results.filter((r) => r.status === 'fulfilled').length;
  const failed = results.filter((r) => r.status === 'rejected').length;
  return { migrated, failed };
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
  const { enabled } = validate(SetNotificationPrefSchema, req.data);

  await db.doc(`players/${uid}/private/preferences`).set(
    { notifications: enabled, updatedAt: FieldValue.serverTimestamp() },
    { merge: true },
  );
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
