import { describe, it, expect } from 'vitest';
import {
  APP_STORE_URL,
  PLAY_STORE_URL,
  renderAuthFinishPage,
  renderDeepLinkPage,
} from '../src/landingHtml';

const HOST = 'bragwise.firebaseapp.com';
const CHALLENGE_ID = 'WjxX7easzR0rsy73hxul';
const DEEP_LINK = `https://${HOST}/c/${CHALLENGE_ID}`;

function deepLinkPage(): string {
  return renderDeepLinkPage({
    title: 'Bragwise',
    description: 'Make your predictions on Bragwise.',
    ogImage: '',
    deepLink: DEEP_LINK,
    ctaLabel: 'Open challenge in Bragwise',
  });
}

function authPage(): string {
  return renderAuthFinishPage(`https://${HOST}/auth/finish?apiKey=k&oobCode=c`);
}

describe('renderDeepLinkPage — Android (must stay byte-identical)', () => {
  it('emits the intent:// URL with package=se.atte.bragwise and the Play fallback', () => {
    // Hardcoded on purpose: this is the regression guard for the Android path.
    expect(deepLinkPage()).toContain(
      `intent://${HOST}/c/${CHALLENGE_ID}#Intent;` +
        'scheme=https;package=se.atte.bragwise;' +
        'S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Dse.atte.bragwise;end',
    );
  });

  it('links to Google Play', () => {
    expect(deepLinkPage()).toContain(PLAY_STORE_URL);
    expect(PLAY_STORE_URL).toContain('play.google.com');
  });
});

describe('renderDeepLinkPage — iOS', () => {
  it('emits the bragwise:// scheme URL with host and path preserved', () => {
    expect(deepLinkPage()).toContain(`bragwise://${HOST}/c/${CHALLENGE_ID}`);
  });

  it('links to the App Store', () => {
    expect(deepLinkPage()).toContain('https://apps.apple.com/app/id6778372770');
    expect(APP_STORE_URL).toBe('https://apps.apple.com/app/id6778372770');
  });

  it('emits the apple-itunes-app smart banner with app-id and app-argument', () => {
    expect(deepLinkPage()).toContain(
      `<meta name="apple-itunes-app" content="app-id=6778372770, app-argument=${DEEP_LINK}">`,
    );
  });
});

describe('renderDeepLinkPage — client-side platform pruning', () => {
  it('renders all four CTA ids so the body is UA-invariant and CDN-cacheable', () => {
    const page = deepLinkPage();
    for (const id of ['cta-android', 'cta-ios', 'store-android', 'store-ios']) {
      expect(page, id).toContain(`id="${id}"`);
    }
  });

  it('prunes with .remove(), not .hidden (loses to .btn{display:inline-block})', () => {
    const page = deepLinkPage();
    expect(page).toContain('el.remove()');
    expect(page).not.toContain('el.hidden');
  });

  it('detects iPadOS, which reports a desktop Macintosh UA', () => {
    const page = deepLinkPage();
    expect(page).toContain('/iPad|iPhone|iPod/.test(navigator.userAgent)');
    expect(page).toContain('navigator.maxTouchPoints > 1');
  });

  it('drops both app buttons on desktop and keeps both store links', () => {
    // Neither app URL can work in a desktop browser, and ctaBlock() gives both
    // buttons the same label — so keeping them showed two identical buttons,
    // one dead. Store links stay: the visitor's phone platform is unknown.
    expect(deepLinkPage()).toContain(": ['cta-android', 'cta-ios']");
  });
});

describe('renderAuthFinishPage', () => {
  it('offers all four CTAs', () => {
    const page = authPage();
    for (const id of ['cta-android', 'cta-ios', 'store-android', 'store-ios']) {
      expect(page, id).toContain(`id="${id}"`);
    }
    // `&` is `&amp;` because these land in an href attribute; the browser decodes
    // it back to `&`, so the app still receives the query string byte-for-byte.
    expect(page).toContain(`intent://${HOST}/auth/finish?apiKey=k&amp;oobCode=c#Intent;`);
    expect(page).toContain(`bragwise://${HOST}/auth/finish?apiKey=k&amp;oobCode=c`);
  });

  it('preserves the query string unescaped inside the script, so sign-in still works', () => {
    // AuthRepository.isSignInLink needs the original oobCode/apiKey untouched.
    const script = authPage().slice(0, authPage().indexOf('</script>'));
    expect(script).toContain(`/auth/finish?apiKey=k&oobCode=c#Intent;`);
  });

  it('keeps the auto-redirect behind the NOT-iOS guard', () => {
    const page = authPage();
    const guard = page.indexOf('if (isIOS) return;');
    const redirect = page.indexOf('window.location.href');
    expect(guard).toBeGreaterThan(-1);
    expect(redirect).toBeGreaterThan(guard);
  });

  it('has no dead encoded-URL local', () => {
    expect(authPage()).not.toContain('const encoded');
  });

  it('escapes < as \\u003c inside the inline script', () => {
    const page = renderAuthFinishPage(`https://${HOST}/auth/finish?x=</script>`);
    const script = page.slice(page.indexOf('<script>'), page.indexOf('</script>'));
    expect(script).toContain('\\u003c');
    expect(script).not.toContain('?x=</script>');
  });

  // The CTA hrefs are built from req.originalUrl (landing.ts), so a caller-supplied
  // quote must not be able to close the attribute and inject markup on this origin.
  it('escapes the CTA href attributes so the request URL cannot break out', () => {
    const page = renderAuthFinishPage(
      `https://${HOST}/auth/finish?oobCode=abc"><img src=x onerror=alert(1)>`,
    );
    expect(page).not.toContain('<img src=x onerror=alert(1)>');
    expect(page).toContain('&quot;&gt;&lt;img src=x onerror=alert(1)&gt;');
  });
});
