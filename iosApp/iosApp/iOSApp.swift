import SwiftUI
import FirebaseCore
import FirebaseAppCheck
import Shared

@main
struct iOSApp: App {

    init() {
        // App Check provider factory MUST be installed BEFORE
        // FirebaseApp.configure(), otherwise the first network calls Firebase
        // makes will go out without an App Check token.
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(BragwiseAppAttestProviderFactory())
        #endif

        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Universal Link path: tapping the email sign-in link from
                // Mail / Safari arrives here once the OS has verified our
                // `apple-app-site-association` for bragwise.firebaseapp.com.
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    guard let url = activity.webpageURL else { return }
                    IosAuthBridgeKt.handleSignInLinkFromIos(url: url.absoluteString)
                }
                // Belt-and-braces: handles the rarer `openURL` path too (eg
                // long-press → Open in Bragwise, custom scheme fallback).
                .onOpenURL { url in
                    IosAuthBridgeKt.handleSignInLinkFromIos(url: url.absoluteString)
                }
        }
    }
}

#if !DEBUG
/// Release-build App Check provider. Wraps Apple's App Attest API — the iOS
/// equivalent of Play Integrity on Android. iOS 14+ only; falls back to nil
/// on older OS versions, in which case Firebase rejects the call (acceptable
/// since we target iOS 14+ in Phase 1).
final class BragwiseAppAttestProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        return AppAttestProvider(app: app)
    }
}
#endif
