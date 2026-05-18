/**
 * Marketing / install page Cloud Function. Handles `https://bragwise.app/*`:
 *   - `/.well-known/assetlinks.json` (Android App Links verification)
 *   - `/.well-known/apple-app-site-association` (iOS Universal Links)
 *   - `/c/{id}` — OpenGraph preview for PROMOTED challenges; generic
 *     "Open in Bragwise" page otherwise (we deliberately don't leak
 *     INVITE_ONLY / FRIENDS metadata to the public web).
 *   - `/u/{handle}` — generic "Open in Bragwise"; never reads publicProfiles.
 *
 * See plan §5 "Marketing / install page Cloud Function".
 */
import { onRequest } from 'firebase-functions/v2/https';

export const landing = onRequest({ region: 'us-central1' }, async (_req, res) => {
  // TODO: route table per plan §5
  res.status(503).send('landing function not yet implemented — see decision.md');
});
