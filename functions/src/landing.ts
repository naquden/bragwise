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
 *   GET /u/{handle}           — Generic "Open in Bragwise" profile page.
 *   GET /.well-known/assetlinks.json    — served by Firebase Hosting (static file),
 *                               NOT by this function.
 *   GET /.well-known/apple-app-site-association — same, static via Hosting.
 *
 * Routes NOT handled here fall through to Firebase Hosting (index.html stub).
 */
import { onRequest } from 'firebase-functions/v2/https';
import { db } from './lib/admin';
import { renderChallengeCard } from './shareCard';
import type { ShareCardData, PodiumEntry } from './shareCard';

const APP_NAME = 'Bragwise';
const APP_PACKAGE = 'se.atte.bragwise';
const PLAY_STORE_URL = `https://play.google.com/store/apps/details?id=${APP_PACKAGE}`;

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

// ─── HTML renderers ───────────────────────────────────────────────────────────

function renderAuthFinishPage(fullAuthUrl: string): string {
  // Encode the full auth URL for the intent: scheme. The Android intent URL
  // will open the app directly if installed; the S.browser_fallback_url param
  // goes to the Play Store if not.
  const encoded = encodeURIComponent(fullAuthUrl);
  const intentUrl =
    `intent://${fullAuthUrl.replace('https://', '')}#Intent;` +
    `scheme=https;package=${APP_PACKAGE};` +
    `S.browser_fallback_url=${encodeURIComponent(PLAY_STORE_URL)};end`;

  return html`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Sign in to ${APP_NAME}</title>
  ${commonHead()}
  <script>
    // Attempt to open the app immediately via the intent URL.
    // If the app is not installed, the browser stays on this page.
    window.onload = function() {
      const intentUrl = ${JSON.stringify(intentUrl)};
      window.location.href = intentUrl;
    };
  </script>
</head>
<body>
  ${header()}
  <main>
    <h1>Complete your sign-in</h1>
    <p>Tap the button to finish signing in to Bragwise on your device.</p>
    <a class="btn" href="${intentUrl}">Open Bragwise to sign in</a>
    <p class="sub">
      Don't have the app yet?
      <a href="${PLAY_STORE_URL}">Get it on Google Play</a>
    </p>
  </main>
</body>
</html>`;
}

interface DeepLinkPageOptions {
  title: string;
  description: string;
  ogImage: string;
  deepLink: string;
  ctaLabel: string;
}

function renderDeepLinkPage({ title, description, ogImage, deepLink, ctaLabel }: DeepLinkPageOptions): string {
  const intentUrl =
    `intent://${deepLink.replace('https://', '')}#Intent;` +
    `scheme=https;package=${APP_PACKAGE};` +
    `S.browser_fallback_url=${encodeURIComponent(PLAY_STORE_URL)};end`;

  const ogImageTag = ogImage
    ? `<meta property="og:image" content="${escHtml(ogImage)}">`
    : '';
  const twitterCard = ogImage ? 'summary_large_image' : 'summary';

  return html`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>${escHtml(title)}</title>
  <meta property="og:title" content="${escHtml(title)}">
  <meta property="og:description" content="${escHtml(description)}">
  <meta property="og:type" content="website">
  <meta property="og:url" content="${escHtml(deepLink)}">
  ${ogImageTag}
  <meta name="twitter:card" content="${twitterCard}">
  <meta name="twitter:title" content="${escHtml(title)}">
  <meta name="twitter:description" content="${escHtml(description)}">
  ${commonHead()}
</head>
<body>
  ${header()}
  <main>
    <h1>${escHtml(title)}</h1>
    <p>${escHtml(description)}</p>
    <a class="btn" href="${intentUrl}">${escHtml(ctaLabel)}</a>
    <p class="sub">
      Don't have the app yet?
      <a href="${PLAY_STORE_URL}">Get it on Google Play</a>
    </p>
  </main>
</body>
</html>`;
}

function commonHead(): string {
  return `<style>
    *{box-sizing:border-box;margin:0;padding:0}
    html,body{height:100%;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,system-ui,sans-serif;background:#0e0e10;color:#fafafa}
    header{padding:20px 24px;border-bottom:1px solid #1e1e22}
    header span{font-size:1.1rem;font-weight:600;color:#fafafa}
    main{display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:calc(100vh - 61px);padding:24px;text-align:center;gap:16px}
    h1{font-size:1.5rem;max-width:28rem}
    p{color:#b8b8c0;max-width:26rem;font-size:0.95rem;line-height:1.5}
    .btn{display:inline-block;background:#7b61ff;color:#fff;text-decoration:none;padding:14px 28px;border-radius:12px;font-weight:600;font-size:1rem;margin-top:8px}
    .btn:hover{background:#6b51ef}
    .sub{font-size:0.82rem;margin-top:4px}
    .sub a{color:#9d8fff;text-decoration:none}
  </style>`;
}

function header(): string {
  return `<header><span>${APP_NAME}</span></header>`;
}

/** Tagged template literal — passes through the string unchanged. */
function html(strings: TemplateStringsArray, ...values: string[]): string {
  return strings.raw.reduce((acc, str, i) => acc + (values[i - 1] ?? '') + str);
}

function escHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
