/**
 * HTML renderers for the landing / install pages served by ./landing.
 *
 * Split out of landing.ts so it can be unit-tested: landing.ts imports
 * ./lib/admin, which calls admin.initializeApp() at module scope. This file has
 * no imports and no side effects.
 *
 * Every page is rendered ONCE, platform-neutrally, and cached by the Firebase
 * Hosting CDN (/c/{id} is `Cache-Control: public, max-age=300`). The CDN does
 * NOT vary on User-Agent, so platform branching MUST happen client-side — see
 * platformPruneScript(). Never add server-side UA sniffing here: it would
 * poison the shared cache entry and also break OpenGraph scrapers.
 */

export const APP_NAME = 'Bragwise';
export const APP_PACKAGE = 'se.atte.bragwise';
export const PLAY_STORE_URL = `https://play.google.com/store/apps/details?id=${APP_PACKAGE}`;

/** App Store numeric ID for se.atte.bragwise.Bragwise. */
export const APP_STORE_ID = '6778372770';
export const APP_STORE_URL = `https://apps.apple.com/app/id${APP_STORE_ID}`;

/**
 * Custom URL scheme. Tapping a same-domain https link from inside Safari does
 * NOT re-trigger a Universal Link, so iOS needs a scheme URL to reach the app.
 * KEEP IN SYNC with iosApp/iosApp/Info.plist (CFBundleURLTypes) and
 * shared/src/commonMain/kotlin/se/atte/bragwise/data/FirebaseConfig.kt.
 */
export const APP_URL_SCHEME = 'bragwise';

/**
 * JS *expression* (no statements) that is true on iPhone / iPad / iPod.
 * iPadOS 13+ reports a desktop 'Macintosh' UA, hence the touch-points probe.
 */
const IS_IOS_EXPR =
  "(/iPad|iPhone|iPod/.test(navigator.userAgent) || " +
  "(navigator.userAgent.indexOf('Macintosh') !== -1 && navigator.maxTouchPoints > 1))";

export interface DeepLinkPageOptions {
  title: string;
  description: string;
  ogImage: string;
  deepLink: string;
  ctaLabel: string;
}

export function renderAuthFinishPage(fullAuthUrl: string): string {
  // The Android intent URL opens the app directly if installed; the
  // S.browser_fallback_url param goes to the Play Store if not.
  const intentUrl =
    `intent://${fullAuthUrl.replace('https://', '')}#Intent;` +
    `scheme=https;package=${APP_PACKAGE};` +
    `S.browser_fallback_url=${encodeURIComponent(PLAY_STORE_URL)};end`;
  const schemeUrl = toSchemeUrl(fullAuthUrl);

  return html`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Sign in to ${APP_NAME}</title>
  ${commonHead()}
  <script>
    // Android: open the app immediately via the intent URL. If the app is not
    // installed the browser just stays on this page.
    // iOS: do NOT auto-redirect — Safari answers "cannot open the page because
    // the address is invalid" for intent:// URLs. iOS users tap #cta-ios.
    window.onload = function () {
      var isIOS = ${IS_IOS_EXPR};
      if (isIOS) return;
      window.location.href = ${jsonForScript(intentUrl)};
    };
  </script>
</head>
<body>
  ${header()}
  <main>
    <h1>Complete your sign-in</h1>
    <p>Tap the button to finish signing in to Bragwise on your device.</p>
    ${ctaBlock(intentUrl, schemeUrl, 'Open Bragwise to sign in')}
  </main>
  ${platformPruneScript()}
</body>
</html>`;
}

export function renderDeepLinkPage({ title, description, ogImage, deepLink, ctaLabel }: DeepLinkPageOptions): string {
  const intentUrl =
    `intent://${deepLink.replace('https://', '')}#Intent;` +
    `scheme=https;package=${APP_PACKAGE};` +
    `S.browser_fallback_url=${encodeURIComponent(PLAY_STORE_URL)};end`;
  const schemeUrl = toSchemeUrl(deepLink);

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
  <meta name="apple-itunes-app" content="app-id=${APP_STORE_ID}, app-argument=${escHtml(deepLink)}">
  ${commonHead()}
</head>
<body>
  ${header()}
  <main>
    <h1>${escHtml(title)}</h1>
    <p>${escHtml(description)}</p>
    ${ctaBlock(intentUrl, schemeUrl, ctaLabel)}
  </main>
  ${platformPruneScript()}
</body>
</html>`;
}

/** `https://host/path` -> `bragwise://host/path` (host + path preserved verbatim). */
function toSchemeUrl(httpsUrl: string): string {
  return `${APP_URL_SCHEME}://${httpsUrl.replace('https://', '')}`;
}

/**
 * The four platform CTAs. All four are ALWAYS in the HTML; platformPruneScript()
 * removes the ones that don't apply by id. Only JS-disabled clients keep all
 * four; that is the unavoidable floor, and it degrades to "every option shown"
 * rather than "no option shown", so no <noscript> is needed.
 *
 * Both URLs MUST be escaped: on /auth/finish they derive from the request URL
 * (landing.ts builds them from req.originalUrl), so a caller-supplied `"` would
 * otherwise break out of the href attribute and inject markup on this origin.
 * The store URLs are module constants with no escapable characters — escaped
 * anyway so no future edit can quietly introduce a third sink.
 */
function ctaBlock(intentUrl: string, schemeUrl: string, ctaLabel: string): string {
  return `<a class="btn" id="cta-android" href="${escHtml(intentUrl)}">${escHtml(ctaLabel)}</a>
    <a class="btn" id="cta-ios" href="${escHtml(schemeUrl)}">${escHtml(ctaLabel)}</a>
    <p class="sub">
      Don't have the app yet?
      <a id="store-android" href="${escHtml(PLAY_STORE_URL)}">Get it on Google Play</a>
      <a id="store-ios" href="${escHtml(APP_STORE_URL)}">Download on the App Store</a>
    </p>`;
}

/**
 * Removes the CTAs that don't apply to this client. Interpolates NO data — the
 * URLs stay in HTML attributes — so there is nothing here to escape.
 * Must use .remove(): .hidden would lose to .btn{display:inline-block}.
 * Runs at the end of <body> so the elements already exist.
 *
 * Desktop / unknown UA drops BOTH app buttons and keeps BOTH store links.
 * Neither app URL can work there — `intent://` is Chrome-on-Android only and
 * `bragwise://` resolves to nothing without the iOS app installed — and since
 * ctaBlock() gives both buttons the same ctaLabel, keeping them rendered two
 * identical buttons, one of them dead. Both store links stay because the
 * visitor's phone platform is unknown at that point.
 */
function platformPruneScript(): string {
  return `<script>
    (function () {
      var isIOS = ${IS_IOS_EXPR};
      var isAndroid = /Android/.test(navigator.userAgent);
      var drop = isIOS ? ['cta-android', 'store-android']
        : isAndroid ? ['cta-ios', 'store-ios']
        : ['cta-android', 'cta-ios'];
      for (var i = 0; i < drop.length; i++) {
        var el = document.getElementById(drop[i]);
        if (el) el.remove();
      }
    })();
  </script>`;
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

/**
 * JSON literal safe to embed inside an inline <script>: `<` becomes `<` so
 * a value containing `</script>` cannot terminate the element early.
 */
function jsonForScript(value: unknown): string {
  return JSON.stringify(value).replace(/</g, '\\u003c');
}

function escHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
