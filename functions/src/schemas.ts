import { z } from 'zod';
import _avatarFixture from './fixtures/avatarSeeds.json';

/**
 * Zod schemas per callable. Server-derived fields (createdBy, createdAt,
 * status, joinedCount, promoted, score, leaderboard, etc.) are
 * NEVER accepted from the client — see plan §5 "Server-derived fields".
 *
 * Fixture-driven parity with the Kotlin client serializer is enforced by
 * `functions/test/fixtures/api/{callable}/{valid,invalid}/`.
 */

// Avatar seed allowlist — imported from src/fixtures/avatarSeeds.json so the
// data is bundled into the deployed function. The test fixture at
// test/fixtures/avatar/seeds.json is a copy; keep them in sync.
const _legacyRe = new RegExp(_avatarFixture.legacyRegex);
const _flagSeeds = new Set(_avatarFixture.flagCodes.map((c) => `flag:${c}`));
const _emojiSeeds = new Set(_avatarFixture.emoji);

export const AvatarSeedSchema = z
  .string()
  .max(32)
  .refine(
    (v) => v === '' || _emojiSeeds.has(v) || _flagSeeds.has(v) || _legacyRe.test(v),
    { message: 'invalid-avatarSeed' },
  );

export const PredictionPayloadSchema = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('SINGLE_PICK'), optionId: z.string().min(1) }),
  z.object({ kind: z.literal('RANKING'), orderedOptionIds: z.array(z.string().min(1)) }),
  z.object({ kind: z.literal('BOOLEAN_PROP'), value: z.boolean() }),
  z.object({ kind: z.literal('GUESS'), guessValue: z.number().int() }),
  z.object({ kind: z.literal('MULTI_SELECT'), selectedOptionIds: z.array(z.string()) }),
  z.object({
    kind: z.literal('OVER_UNDER'),
    over: z.boolean().optional(),
    actualValue: z.number().int().optional(),
  }),
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
  z.object({
    kind: z.literal('GUESS'),
    id: z.string().min(1),
    title: z.string().min(1),
    granularity: z.enum(['TIME', 'DAY', 'NUMBER']),
    closest: z.boolean().default(true),
    placement: z.boolean().default(false),
  }),
  z.object({
    kind: z.literal('MULTI_SELECT'),
    id: z.string().min(1),
    title: z.string().min(1),
    optionType: z.enum(['NONE', 'COUNTRY']).optional().default('NONE'),
    options: z.array(BetOptionSchema).min(1),
  }),
  z.object({
    kind: z.literal('OVER_UNDER'),
    id: z.string().min(1),
    title: z.string().min(1),
    line: z.number().int(),
  }),
]);

export const VisibilitySchema = z.enum(['FRIENDS', 'INVITE_ONLY']); // PROMOTED never accepted from client

function validateInviteOnlyHasInvitees(
  data: { visibility: z.infer<typeof VisibilitySchema>; invitedUids: string[] },
  ctx: z.RefinementCtx,
) {
  if (data.visibility === 'INVITE_ONLY' && data.invitedUids.length === 0) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'invite-only-needs-invitees',
      path: ['invitedUids'],
    });
  }
}

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

// Validates that placement=true bets also have closest=true.
// placement is a tiebreaker scoring mode that only makes sense for closest-wins bets.
function validatePlacementRequiresClosest(data: { bets: z.infer<typeof BetSchema>[] }, ctx: z.RefinementCtx) {
  data.bets.forEach((bet, index) => {
    if (bet.kind === 'GUESS' && bet.placement === true && bet.closest !== true) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'placement-requires-closest',
        path: ['bets', index, 'placement'],
      });
    }
  });
}

// Validates that no bet contains duplicate options.
// Dedup key: countryCode when present, otherwise normalized label (trim + lowercase).
// Prevents ambiguous predictions and unresolvable results.
function validateNoDuplicateOptions(data: { bets: z.infer<typeof BetSchema>[] }, ctx: z.RefinementCtx) {
  data.bets.forEach((bet, betIndex) => {
    if (bet.kind === 'BOOLEAN_PROP' || bet.kind === 'GUESS' || bet.kind === 'OVER_UNDER') return;
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
  invitedUids: z.array(z.string().min(1)).max(100).default([]),
  // Hard-rejected if present:
  promoted: z.never().optional(),
});

export const CreateChallengeSchema = CreateChallengeBaseSchema
  .superRefine(validateRankingTopN)
  .superRefine(validatePlacementRequiresClosest)
  .superRefine(validateNoDuplicateOptions)
  .superRefine(validateInviteOnlyHasInvitees);

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

export const WithdrawFriendRequestSchema = z.object({
  otherUid: z.string().min(1),
});

export const UpdateProfileSchema = z.object({
  displayName: z.string().min(1, 'invalid-displayName').max(40, 'invalid-displayName').optional(),
  avatarSeed: AvatarSeedSchema.optional(),
  handle: z.string().regex(/^[a-z0-9_]{3,20}$/, 'invalid-handle').optional(),
});

export const ClaimHandleSchema = z.object({
  handle: z.string().regex(/^[a-z0-9_]{3,20}$/, 'invalid-handle'),
});

export const RegisterPushTokenSchema = z.object({
  token: z.string().min(1).max(4096),
  platform: z.enum(['fcm', 'apns']),
});

export const NOTIFICATION_CATEGORY_KEYS = ['social', 'results', 'participations', 'invites'] as const;
export type NotificationCategoryKey = typeof NOTIFICATION_CATEGORY_KEYS[number];

export const SetNotificationPrefSchema = z
  .object({
    enabled: z.boolean().optional(),
    categories: z.record(z.enum(NOTIFICATION_CATEGORY_KEYS), z.boolean()).optional(),
  })
  .refine((d) => d.enabled !== undefined || (d.categories && Object.keys(d.categories).length > 0), {
    message: 'at-least-one-field-required',
  });

export const DeleteChallengeSchema = z.object({
  challengeId: z.string().min(1),
});

export const RecomputeLeaderboardSchema = z.object({
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

export const REACTION_EMOJIS = ['😂', '🔥', '😭', '🎉', '💀', '👏', '🤡', '🧠', '😱', '🤯', '👀'] as const;
export type ReactionEmoji = typeof REACTION_EMOJIS[number];

export const SetReactionSchema = z.object({
  challengeId: z.string().min(1),
  emoji: z.enum(REACTION_EMOJIS).nullable(),
});
