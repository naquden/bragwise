/**
 * Landing / install page Cloud Function. Handles https://bragwise.firebaseapp.com/*
 * (and in prod: https://bragwise.app/*):
 *
 *   GET /auth/finish          — email link sign-in fallback for web (shown when
 *                               App Links / Universal Links did not intercept the
 *                               email link). Renders a "tap to continue" page that
 *                               deep-links back into the app via an intent URL.
 *   GET /c/{id}               — OpenGraph preview for PROMOTED challenges; generic
 *                               "Open in Bragwise" page for all others. Deliberately
 *                               does NOT leak INVITE_ONLY / FRIENDS metadata.
 *   GET /u/{handle}           — NOT implemented; falls through to the 404 below.
 *                               firebase.json rewrites /u/** here, but nothing in
 *                               the app generates such a link and parseDeepLink
 *                               (shared/.../ui/nav/DeepLink.kt) has no /u/ branch,
 *                               so a page here would open the app and navigate
 *                               nowhere. 404 until profile sharing actually exists.
 *   GET /.well-known/assetlinks.json    — served by Firebase Hosting (static file),
 *                               NOT by this function.
 *   GET /.well-known/apple-app-site-association — same, static via Hosting.
 *
 * Routes NOT handled here fall through to Firebase Hosting (index.html stub).
 *
 * HTML rendering lives in ./landingHtml — kept out of this file because this one
 * imports ./lib/admin, which calls admin.initializeApp() at module scope and so
 * cannot be imported from a unit test.
 */
import { onRequest } from 'firebase-functions/v2/https';
import { db } from './lib/admin';
import { renderChallengeCard } from './shareCard';
import type { ShareCardData, PodiumEntry } from './shareCard';
import { APP_NAME, renderAuthFinishPage, renderDeepLinkPage } from './landingHtml';

// Region is inherited from setGlobalOptions in index.ts (europe-west1).
export const landing = onRequest(async (req, res) => {
  const path = req.path;

  // ── /auth/finish ────────────────────────────────────────────────────────────
  // Firebase email sign-in callback. When the OS doesn't intercept the email
  // link via App Links / Universal Links (emulators, older devices, browser
  // share), Firebase redirects to this continueUrl after processing the OTP.
  // We render a page that attempts to re-open the app with the full auth URL
  // using an Android App Intent and falls back to a "Get the app" button.
  if (path === '/auth/finish' || path.startsWith('/auth/finish?')) {
    const fullUrl = `https://${req.hostname}${req.originalUrl}`;
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.setHeader('Cache-Control', 'no-store');
    res.status(200).send(renderAuthFinishPage(fullUrl));
    return;
  }

  // ── /c/{challengeId}/card.png ────────────────────────────────────────────────
  // Podium share-card image — only for finished PROMOTED challenges.
  // Served unauthenticated; gating is the privacy guarantee.
  const cardMatch = path.match(/^\/c\/([a-zA-Z0-9_-]+)\/card\.png$/);
  if (cardMatch) {
    const challengeId = cardMatch[1];
    try {
      const snap = await db.doc(`challenges/${challengeId}`).get();
      if (!snap.exists) { res.status(404).send('Not found'); return; }
      const data = snap.data()!;

      // Gate: only promoted + finished
      if (data.promoted !== true || data.resultsPostedAt == null) {
        res.status(404).send('Not found');
        return;
      }

      const leaderboard: Record<string, number> = data.leaderboard ?? {};
      const rankedLeaderboard: Record<string, number> = data.rankedLeaderboard ?? {};
      const participants: Record<string, { displayName: string; avatarSeed?: string }> =
        data.participants ?? {};

      // Build top-3 podium entries from persisted rankedLeaderboard
      const ranked: PodiumEntry[] = Object.entries(rankedLeaderboard)
        .filter(([, rank]) => rank <= 3)
        .map(([uid, rank]) => ({
          rank,
          displayName: participants[uid]?.displayName ?? 'Player',
          avatarSeed: participants[uid]?.avatarSeed ?? uid,
          points: leaderboard[uid] ?? 0,
        }))
        .sort((a, b) => a.rank - b.rank);

      const playerCount = Math.max(
        Object.keys(leaderboard).length,
        typeof data.joinedCount === 'number' ? data.joinedCount : 0,
      );

      const cardData: ShareCardData = {
        title: data.title ?? '',
        playerCount,
        podium: ranked,
      };

      const png = await renderChallengeCard(cardData);
      res.setHeader('Content-Type', 'image/png');
      res.setHeader('Cache-Control', 'public, max-age=86400');
      res.status(200).send(png);
    } catch (err) {
      console.error('card.png render error', err);
      res.status(500).send('Internal error');
    }
    return;
  }

  // ── /c/{challengeId} and /c/{challengeId}/results ───────────────────────────
  // Both paths show the same OG preview (results card when finished).
  // /results deep-links directly to the in-app results screen.
  const challengeMatch = path.match(/^\/c\/([a-zA-Z0-9_-]+)(\/results)?$/);
  if (challengeMatch) {
    const challengeId = challengeMatch[1];
    const isResultsPath = challengeMatch[2] === '/results';
    const deepLink = `https://${req.hostname}/c/${challengeId}${isResultsPath ? '/results' : ''}`;
    let ogTitle = APP_NAME;
    let ogDescription = 'Make your predictions on Bragwise.';
    let ogImage = '';

    try {
      const snap = await db.doc(`challenges/${challengeId}`).get();
      if (snap.exists) {
        const data = snap.data()!;
        const isPromoted: boolean = data.promoted === true;
        const title: string = data.title ?? '';
        const category: string = data.category ?? '';
        if (isPromoted && title) {
          const isFinished: boolean = data.resultsPostedAt != null;
          ogTitle = `${title} — ${APP_NAME}`;
          ogDescription = isFinished
            ? `See the final standings on Bragwise.`
            : `Predict the ${category} challenge on Bragwise.`;
          if (isFinished) {
            ogImage = `https://${req.hostname}/c/${challengeId}/card.png`;
          }
        }
      }
    } catch {
      // best-effort — continue with generic OG tags
    }

    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.setHeader('Cache-Control', 'public, max-age=300');
    res.status(200).send(renderDeepLinkPage({
      title: ogTitle,
      description: ogDescription,
      ogImage,
      deepLink,
      ctaLabel: 'Open challenge in Bragwise',
    }));
    return;
  }

  // ── everything else ─────────────────────────────────────────────────────────
  // Fall through to Firebase Hosting (index.html serves the marketing stub).
  res.status(404).send('Not found');
});
