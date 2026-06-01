import { z } from 'zod';

/**
 * Zod schemas per callable. Server-derived fields (createdBy, createdAt,
 * status, joinedCount, promoted, trusted, score, leaderboard, etc.) are
 * NEVER accepted from the client — see plan §5 "Server-derived fields".
 *
 * Fixture-driven parity with the Kotlin client serializer is enforced by
 * `functions/test/fixtures/api/{callable}/{valid,invalid}/`.
 */

export const PredictionPayloadSchema = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('SINGLE_PICK'), optionId: z.string().min(1) }),
  z.object({ kind: z.literal('RANKING'), orderedOptionIds: z.array(z.string().min(1)) }),
  z.object({ kind: z.literal('BOOLEAN_PROP'), value: z.boolean() }),
]);

// Phase 1.5: option shape extended with optional countryCode (ISO-3166 alpha-2).
// null / absent = free-text; present = country with flag rendering on the client.
// nullish() accepts both undefined (absent) and null (explicitly cleared).
const BetOptionSchema = z.object({
  id: z.string().min(1),
  label: z.string().min(1),
  countryCode: z.string().length(2).nullish(),
});

export const BetSchema = z.discriminatedUnion('kind', [
  z.object({
    kind: z.literal('SINGLE_PICK'),
    id: z.string().min(1),
    title: z.string().min(1),
    // Phase 1.5: NONE = free-text, COUNTRY = country autocomplete + flag.
    optionType: z.enum(['NONE', 'COUNTRY']).optional().default('NONE'),
    options: z.array(BetOptionSchema).min(1),
  }),
  z.object({
    kind: z.literal('RANKING'),
    id: z.string().min(1),
    title: z.string().min(1),
    optionType: z.enum(['NONE', 'COUNTRY']).optional().default('NONE'),
    topN: z.number().int().positive(),
    options: z.array(BetOptionSchema).min(1),
  }),
  z.object({
    kind: z.literal('BOOLEAN_PROP'),
    id: z.string().min(1),
    title: z.string().min(1),
  }),
]);

export const VisibilitySchema = z.enum(['FRIENDS', 'INVITE_ONLY']); // PROMOTED never accepted from client

export const CreateChallengeSchema = z.object({
  title: z.string().min(1).max(120),
  description: z.string().max(2000).default(''),
  category: z.string().min(1),
  visibility: VisibilitySchema,
  locksAt: z.string().datetime(), // ISO-8601 UTC
  bets: z.array(BetSchema).min(1),
  // Hard-rejected if present:
  promoted: z.never().optional(),
  trusted: z.never().optional(),
});

export const UpdateDraftSchema = CreateChallengeSchema.extend({
  challengeId: z.string().min(1),
});

export const PublishChallengeSchema = z.object({
  challengeId: z.string().min(1),
});

export const SubmitPredictionsSchema = z.object({
  challengeId: z.string().min(1),
  predictions: z
    .array(
      z.object({
        betId: z.string().min(1),
        payload: PredictionPayloadSchema,
      }),
    )
    .min(1),
});

export const PostResultsSchema = z.object({
  challengeId: z.string().min(1),
  results: z.record(z.string().min(1), PredictionPayloadSchema),
});

export const InviteFriendsSchema = z.object({
  challengeId: z.string().min(1),
  uids: z.array(z.string().min(1)).min(1),
});

export const SendFriendRequestSchema = z.object({
  handle: z.string().min(1),
});

export const FriendRequestActionSchema = z.object({
  requesterUid: z.string().min(1),
});

export const UnfriendSchema = z.object({
  otherUid: z.string().min(1),
});

export const UpdateProfileSchema = z.object({
  displayName: z.string().min(1).max(40).optional(),
  avatarSeed: z.string().optional(),
  handle: z.string().regex(/^[a-z0-9_]{3,20}$/).optional(),
});

export const ClaimHandleSchema = z.object({
  handle: z.string().regex(/^[a-z0-9_]{3,20}$/),
});

export const RegisterPushTokenSchema = z.object({
  token: z.string().min(1).max(4096),
  platform: z.enum(['fcm', 'apns']),
});

export const SetNotificationPrefSchema = z.object({
  enabled: z.boolean(),
});

export const DeleteChallengeSchema = z.object({
  challengeId: z.string().min(1),
});

export const MigrateGuestDataSchema = z.object({
  predictions: z
    .array(
      z.object({
        challengeId: z.string().min(1),
        betId: z.string().min(1),
        payload: PredictionPayloadSchema,
      }),
    )
    .max(2000),
});
