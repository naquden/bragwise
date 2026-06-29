import * as functions from 'firebase-functions/v2';
import { HttpsError, CallableRequest } from 'firebase-functions/v2/https';
import { ZodSchema, ZodError } from 'zod';
import { db, FieldValue, Timestamp } from './admin';

export function requireAuth(req: CallableRequest<unknown>): string {
  if (!req.auth) {
    throw new HttpsError('unauthenticated', 'auth-required');
  }
  return req.auth.uid;
}

/**
 * Email-verified gate. Email-link sign-in (Phase 1) always produces
 * `email_verified == true`, so this is effectively a no-op in the normal
 * flow — kept as defence-in-depth for operator-created accounts.
 */
export function requireVerifiedEmail(req: CallableRequest<unknown>): void {
  const auth = req.auth;
  if (!auth) throw new HttpsError('unauthenticated', 'auth-required');
  const provider = (auth.token as Record<string, unknown>)?.firebase as
    | { sign_in_provider?: string }
    | undefined;
  const isOAuth =
    provider?.sign_in_provider === 'google.com' ||
    provider?.sign_in_provider === 'apple.com';
  if (!isOAuth && !auth.token.email_verified) {
    throw new HttpsError('failed-precondition', 'email_unverified');
  }
}

/**
 * Sliding-window rate limit. Counter doc at `/rateLimits/{uid}__{action}`.
 * Fields: `count` (int), `windowStart` (timestamp).
 * A transaction reads the doc, resets if window has expired, increments,
 * and rejects if the new count exceeds `max`.
 */
export async function rateLimit(
  uid: string,
  action: string,
  windowSeconds: number,
  max: number,
): Promise<void> {
  const ref = db.doc(`rateLimits/${uid}__${action}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const now = Timestamp.now();
    if (!snap.exists) {
      tx.set(ref, { count: 1, windowStart: now });
      return;
    }
    const data = snap.data()!;
    const windowStart: FirebaseFirestore.Timestamp = data.windowStart;
    const elapsed = now.seconds - windowStart.seconds;
    if (elapsed >= windowSeconds) {
      tx.set(ref, { count: 1, windowStart: now });
      return;
    }
    const count: number = data.count ?? 0;
    if (count >= max) {
      throw new HttpsError('resource-exhausted', 'rate-limited');
    }
    tx.update(ref, { count: FieldValue.increment(1) });
  });
}

export function validate<T>(schema: ZodSchema<T>, payload: unknown): T {
  try {
    return schema.parse(payload);
  } catch (e) {
    if (e instanceof ZodError) {
      throw new HttpsError('invalid-argument', 'invalid-argument');
    }
    throw e;
  }
}

const AUDIT_TTL_DAYS = 90;

export async function audit(
  uid: string,
  action: string,
  args: Record<string, unknown>,
): Promise<void> {
  const expireAt = Timestamp.fromMillis(
    Date.now() + AUDIT_TTL_DAYS * 24 * 60 * 60 * 1000,
  );
  await db.collection('auditLog').add({
    uid,
    action,
    args,
    at: FieldValue.serverTimestamp(),
    expireAt,
  });
}

export const log = functions.logger;
