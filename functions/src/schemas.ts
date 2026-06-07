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

// Phase 1.5: option shape extended with optional countryCode.
// Supports ISO-3166 alpha-2 ("GB") and subdivision codes ("GB-ENG", "GB-SCT", etc.).
// null / absent = free-text; present = country with flag rendering on the client.
// nullish() accepts both undefined (absent) and null (explicitly cleared).
const BetOptionSchema = z.object({
  id: z.string().min(1),
  label: z.string().min(1),
  countryCode: z.string().min(2).max(6).nullish(),
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

// Validates that each RANKING bet has topN <= options.length.
// Applied via superRefine so the base object shape remains extendable.
function validateRankingTopN(data: { bets: z.infer<typeof BetSchema>[] }, ctx: z.RefinementCtx) {
  data.bets.forEach((bet, index) => {
    if (bet.kind === 'RANKING' && bet.topN > bet.options.length) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'topN-exceeds-option-count',
        path: ['bets', index, 'topN'],
      });
    }
  });
}

// Validates that no bet contains duplicate options.
// Dedup key: countryCode when present, otherwise normalized label (trim + lowercase).
// Prevents ambiguous predictions and unresolvable results.
function validateNoDuplicateOptions(data: { bets: z.infer<typeof BetSchema>[] }, ctx: z.RefinementCtx) {
  data.bets.forEach((bet, betIndex) => {
    if (bet.kind === 'BOOLEAN_PROP') return;
    const seen = new Set<string>();
    bet.options.forEach((option, optionIndex) => {
      const key = option.countryCode ?? option.label.trim().toLowerCase();
      if (seen.has(key)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'duplicate-option',
          path: ['bets', betIndex, 'options', optionIndex],
        });
      }
      seen.add(key);
    });
  });
}

const CreateChallengeBaseSchema = z.object({
  title: z.string().min(1).max(120),
  description: z.string().max(2000).default(''),
  category: z.string().min(1),
  visibility: VisibilitySchema,
  locksAt: z.string().datetime().refine(
    (s) => { const ms = Date.parse(s); return !Number.isNaN(ms) && ms > Date.now(); },
    { message: 'locksAt-must-be-future' },
  ),
  bets: z.array(BetSchema).min(1),
  betsVisible: z.boolean().default(false),
  // Hard-rejected if present:
  promoted: z.never().optional(),
  trusted: z.never().optional(),
});

export const CreateChallengeSchema = CreateChallengeBaseSchema
  .superRefine(validateRankingTopN)
  .superRefine(validateNoDuplicateOptions);

export const UpdateDraftSchema = CreateChallengeBaseSchema.extend({
  challengeId: z.string().min(1),
})
  .superRefine(validateRankingTopN)
  .superRefine(validateNoDuplicateOptions);

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
