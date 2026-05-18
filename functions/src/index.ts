/**
 * Bragwise Cloud Functions entry. Every callable here goes through the
 * shared middleware (App Check → auth → email-verified → rate-limit → Zod
 * validate → audit). Bodies are stubbed — wire up against admin SDK +
 * Firestore transactions per plan §5 callable table.
 *
 * See decision.md for execution-scope notes.
 */
import { onCall, CallableRequest } from 'firebase-functions/v2/https';
import {
  audit,
  rateLimit,
  requireAuth,
  requireVerifiedEmail,
  validate,
  verifyAppCheck,
} from './lib/middleware';
import {
  ClaimHandleSchema,
  CreateChallengeSchema,
  FriendRequestActionSchema,
  InviteFriendsSchema,
  MigrateGuestDataSchema,
  PostResultsSchema,
  PublishChallengeSchema,
  SendFriendRequestSchema,
  SubmitPredictionsSchema,
  UnfriendSchema,
  UpdateDraftSchema,
  UpdateProfileSchema,
} from './schemas';

const NOT_IMPL = 'callable not yet implemented — see decision.md';

export const submitPredictions = onCall(async (req: CallableRequest<unknown>) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'submitPredictions', 3600, 600);
  validate(SubmitPredictionsSchema, req.data);
  // TODO: tx — read challenge, enforce status==OPEN, serverTime<locksAt,
  //   eligibility (creator/member/invitee/PROMOTED), bet-payload shape,
  //   write `challenges/{c}/players/{uid}` with predictions map.
  throw new Error(NOT_IMPL);
});

export const createChallenge = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'createChallenge', 3600, 10);
  validate(CreateChallengeSchema, req.data);
  await audit(uid, 'createChallenge', { ...(req.data as object) });
  throw new Error(NOT_IMPL);
});

export const updateDraft = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'updateDraft', 3600, 60);
  validate(UpdateDraftSchema, req.data);
  // TODO tx: assert createdBy==uid && status==DRAFT
  throw new Error(NOT_IMPL);
});

export const publishChallenge = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'publishChallenge', 3600, 10);
  validate(PublishChallengeSchema, req.data);
  await audit(uid, 'publishChallenge', { ...(req.data as object) });
  throw new Error(NOT_IMPL);
});

export const postResults = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'postResults', 3600, 10);
  validate(PostResultsSchema, req.data);
  await audit(uid, 'postResults', { ...(req.data as object) });
  // TODO tx: assert createdBy==uid, serverTime>=locksAt, resultsPostedAt==null
  throw new Error(NOT_IMPL);
});

export const inviteFriends = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'inviteFriends', 3600, 30);
  validate(InviteFriendsSchema, req.data);
  // TODO: assert caller is creator; assert every target is in caller's friends
  throw new Error(NOT_IMPL);
});

export const sendFriendRequest = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'sendFriendRequest', 86400, 50);
  validate(SendFriendRequestSchema, req.data);
  throw new Error(NOT_IMPL);
});

export const acceptFriendRequest = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'acceptFriendRequest', 3600, 100);
  validate(FriendRequestActionSchema, req.data);
  // TODO: verify inbound request exists, mirror writes
  throw new Error(NOT_IMPL);
});

export const declineFriendRequest = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'declineFriendRequest', 3600, 100);
  validate(FriendRequestActionSchema, req.data);
  throw new Error(NOT_IMPL);
});

export const unfriend = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'unfriend', 3600, 50);
  validate(UnfriendSchema, req.data);
  throw new Error(NOT_IMPL);
});

export const updateProfile = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'updateProfile', 3600, 10);
  validate(UpdateProfileSchema, req.data);
  throw new Error(NOT_IMPL);
});

export const claimHandle = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'claimHandle', 86400, 10);
  validate(ClaimHandleSchema, req.data);
  throw new Error(NOT_IMPL);
});

export const deleteAccount = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  await rateLimit(uid, 'deleteAccount', 86400, 1);
  await audit(uid, 'deleteAccount', {});
  // TODO: create deletionRequests/{uid} checklist, remove Auth user.
  throw new Error(NOT_IMPL);
});

export const migrateGuestData = onCall(async (req) => {
  verifyAppCheck(req);
  const uid = requireAuth(req);
  requireVerifiedEmail(req);
  await rateLimit(uid, 'migrateGuestData', 86400, 1);
  validate(MigrateGuestDataSchema, req.data);
  throw new Error(NOT_IMPL);
});

export * from './triggers';
export * from './landing';
