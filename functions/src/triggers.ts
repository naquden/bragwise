/**
 * Server-driven reactions to already-validated state. Triggers never
 * validate user input — that's the callable layer's job. See plan §5
 * "Triggers" table.
 */
import { onDocumentUpdated, onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { auth } from 'firebase-functions/v1';

const NOT_IMPL = 'trigger not yet implemented — see decision.md';

/**
 * Fires when `challenges/{c}.resultsPostedAt` is written. Two-phase:
 *   1. Scan composite player docs, run TS scoring engine, write
 *      `challenges/{c}.leaderboard` map ({ uid → points }).
 *   2. Per-participant: read `private/social`, intersect with leaderboard
 *      keys, accumulate friend-pair deltas in memory, issue ONE
 *      `update()` per side covering all friend deltas.
 *
 * Head-to-head writes are NOT retried on transient failure — `FieldValue.increment`
 * is not idempotent under retry, and head-to-head is a rivalry signal,
 * not load-bearing data. See plan §5 "Head-to-head failure policy".
 */
export const onResultsPosted = onDocumentUpdated(
  'challenges/{challengeId}',
  async (_event) => {
    // TODO: detect resultsPostedAt sentinel transition null → non-null
    throw new Error(NOT_IMPL);
  },
);

/**
 * Best-effort joinedCount maintenance. Idempotent via Firestore event ID
 * dedup. Fires on first complete `submitPredictions` (creates the player
 * doc) and on creator auto-join during `publishChallenge`. Subsequent
 * prediction edits are `update()`s — they don't re-fire.
 */
export const onMemberJoin = onDocumentCreated(
  'challenges/{challengeId}/players/{uid}',
  async (_event) => {
    throw new Error(NOT_IMPL);
  },
);

/**
 * Diff `change.before.data().friends` vs `change.after.data().friends` to
 * find newly-added uids. For each new pair, scan both users' open
 * FRIENDS-visibility challenges and create reciprocal invitations. Safe
 * because friend entries can only be added by `acceptFriendRequest`,
 * which verifies the inbound request first.
 */
export const onFriendAccepted = onDocumentWritten(
  'players/{uid}/private/social',
  async (_event) => {
    throw new Error(NOT_IMPL);
  },
);

/**
 * Step through the deletion checklist. Idempotent and resumable — each step
 * queries-then-deletes, so re-runs are no-ops once a step has succeeded.
 * See plan §5 "Account deletion".
 */
export const onUserDeleted = auth.user().onDelete(async (_user) => {
  throw new Error(NOT_IMPL);
});

/** Hourly resume of any deletionRequest with pending steps older than 24h. */
export const reconcileDeletions = onSchedule('every 60 minutes', async (_event) => {
  throw new Error(NOT_IMPL);
});
