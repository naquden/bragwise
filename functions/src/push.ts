import * as admin from 'firebase-admin';
import { db, messaging } from './lib/admin';

export const CHANNEL_SOCIAL = 'bragwise_social';
export const CHANNEL_CHALLENGES = 'bragwise_challenges';
export const CHANNEL_RESULTS = 'bragwise_results';
export const CHANNEL_INVITES = 'bragwise_invites';

const CHANNEL_TO_CATEGORY: Record<string, string> = {
  [CHANNEL_SOCIAL]: 'social',
  [CHANNEL_CHALLENGES]: 'participations',
  [CHANNEL_RESULTS]: 'results',
  [CHANNEL_INVITES]: 'invites',
};

interface PushPayload {
  title: string;
  body: string;
  channel: string;
  deepLink?: string;
}

/**
 * Send a push notification to all registered tokens for a user.
 * Stale tokens (unregistered / invalid) are reaped from Firestore.
 */
export async function sendToUser(uid: string, payload: PushPayload): Promise<void> {
  const tokensSnap = await db.collection(`players/${uid}/pushTokens`).get();
  if (tokensSnap.empty) return;

  const notifPrefsSnap = await db.doc(`players/${uid}/private/preferences`).get();
  const prefsData = notifPrefsSnap.exists ? notifPrefsSnap.data() : null;
  const masterEnabled = prefsData?.notifications ?? true;
  if (!masterEnabled) return;

  const categoryKey = CHANNEL_TO_CATEGORY[payload.channel];
  if (categoryKey !== undefined) {
    const categories = prefsData?.categories ?? {};
    const categoryEnabled = categories[categoryKey] ?? true;
    if (!categoryEnabled) return;
  }

  // Keep doc refs aligned with the token array so stale ones can be reaped
  // by ref (the doc ID is the token, but deleting by ref avoids any
  // path-reconstruction mismatch for tokens with special characters).
  const docs = tokensSnap.docs;
  const tokens = docs.map((d) => d.data().token as string);

  const message: admin.messaging.MulticastMessage = {
    tokens,
    data: {
      title: payload.title,
      body: payload.body,
      channel: payload.channel,
      ...(payload.deepLink ? { deepLink: payload.deepLink } : {}),
    },
    android: { priority: 'high' },
  };

  const result = await messaging.sendEachForMulticast(message);

  const staleRefs = result.responses
    .map((resp, idx) => ({ resp, ref: docs[idx].ref }))
    .filter(({ resp }) => {
      if (resp.success) return false;
      const code = resp.error?.code;
      return (
        code === 'messaging/registration-token-not-registered' ||
        code === 'messaging/invalid-argument' ||
        code === 'messaging/invalid-registration-token'
      );
    })
    .map(({ ref }) => ref);

  if (staleRefs.length > 0) {
    await Promise.all(staleRefs.map((ref) => ref.delete()));
  }
}

