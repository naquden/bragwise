import SwiftUI
import FirebaseCore
import FirebaseAppCheck
import FirebaseCrashlytics
import FirebaseMessaging
import FirebaseAnalytics
import UserNotifications
import Shared

@main
struct iOSApp: App {

    // SwiftUI app lifecycle has no AppDelegate by default, but APNs/FCM callbacks
    // (token registration, notification taps) are delivered to a UIApplicationDelegate.
    // Adapt one in. FirebaseAppDelegateProxyEnabled is NO (Info.plist), so this
    // delegate forwards the APNs token to Messaging and the FCM token to Kotlin itself.
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    // Prompt for notification permission on the first SignedIn
                    // transition (Kotlin gates this so guests are never prompted),
                    // mirroring Android's MainActivity.requestNotificationsOnFirstSignIn.
                    // On grant we register with APNs here (UIKit main-thread API).
                    IosPushBridgeKt.requestPushPermissionOnFirstSignInFromIos {
                        DispatchQueue.main.async {
                            UIApplication.shared.registerForRemoteNotifications()
                        }
                    }
                }
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

/// UIKit delegate hosting Firebase init + the push/notification plumbing.
/// Bridges into the shared Kotlin layer (`PushNotifications`) via `IosPushBridge`.
final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let useMock = false

        // App Check provider factory MUST be installed BEFORE
        // FirebaseApp.configure(), otherwise the first network calls Firebase
        // makes will go out without an App Check token.
        // FirebaseApp.configure() is called even in mock mode because the shared
        // Kotlin platformModule eagerly constructs AuthRemoteDataSource (Firebase.auth /
        // Firebase.functions) regardless of the mock flag — skipping configure() causes
        // a fatal crash. Auth/Analytics/FCM are still skipped in mock mode.
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(BragwiseAppAttestProviderFactory())
        #endif

        FirebaseApp.configure()

        if !useMock {
            // Touch Analytics so the SDK initialises (parity with Android's
            // BragwiseApplication touching Firebase.analytics).
            Analytics.setAnalyticsCollectionEnabled(true)

            // Wire Kotlin's CrashReporter to native Crashlytics.
            // KotlinThrowable arrives as KotlinBase; build an NSError from its
            // message and type name so Crashlytics can group and symbolicate it.
            IosCrashBridgeKt.registerIosCrashReporter(
                onRecord: { throwable in
                    let domain = String(describing: type(of: throwable))
                    let message = throwable.message ?? "no message"
                    let error = NSError(
                        domain: domain,
                        code: 0,
                        userInfo: [NSLocalizedDescriptionKey: message]
                    )
                    Crashlytics.crashlytics().record(error: error)
                },
                onLog: { msg in Crashlytics.crashlytics().log(msg) },
                onKey: { key, value in Crashlytics.crashlytics().setCustomValue(value, forKey: key) }
            )

            // Receive FCM registration tokens. Permission + APNs registration is
            // requested lazily on first sign-in (see requestPushPermissionOnFirstSignInFromIos).
            Messaging.messaging().delegate = self
            UNUserNotificationCenter.current().delegate = self
        }

        // Start Koin before any Composable is hosted. This makes the DI graph
        // available to IosAuthBridge / IosPushBridge and koinViewModel() calls.
        KoinInitializerKt.doInitKoin(useMock: useMock)

        return true
    }

    // MARK: APNs token

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Proxy disabled, so hand the raw APNs token to FCM ourselves. FCM then
        // mints the registration token delivered via messaging(_:didReceiveRegistrationToken:).
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        NSLog("Bragwise: APNs registration failed: \(error.localizedDescription)")
    }

    // MARK: FCM token

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        IosPushBridgeKt.handlePushTokenFromIos(token: token)
    }

    // MARK: Notification presentation + tap

    /// Show banners while the app is foregrounded (Android renders these via
    /// NotificationCompat regardless of foreground state).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    /// Notification tapped: forward the `deepLink` payload into the shared push
    /// flow so AppNav can navigate (mirrors Android's tap PendingIntent → push.onIncomingDeepLink).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        if let deepLink = userInfo["deepLink"] as? String {
            IosPushBridgeKt.handlePushDeepLinkFromIos(url: deepLink)
        }
        completionHandler()
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
