import * as admin from 'firebase-admin';
import { db, messaging } from './lib/admin';

export const CHANNEL_SOCIAL = 'bragwise_social';
export const CHANNEL_CHALLENGES = 'bragwise_challenges';
export const CHANNEL_RESULTS = 'bragwise_results';

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
  const notifEnabled = notifPrefsSnap.exists
    ? (notifPrefsSnap.data()?.notifications ?? true)
    : true;
  if (!notifEnabled) return;

  const tokens = tokensSnap.docs.map((d) => d.data().token as string);

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

  const staleTokens: string[] = [];
  result.responses.forEach((resp, idx) => {
    if (!resp.success) {
      const code = resp.error?.code;
      if (
        code === 'messaging/registration-token-not-registered' ||
        code === 'messaging/invalid-argument' ||
        code === 'messaging/invalid-registration-token'
      ) {
        staleTokens.push(tokens[idx]);
      }
    }
  });

  if (staleTokens.length > 0) {
    await Promise.all(
      staleTokens.map((token) =>
        db.doc(`players/${uid}/pushTokens/${token}`).delete(),
      ),
    );
  }
}

