This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Building iOS IPA for App Store (Transporter)

**1. Archive**
```bash
xcodebuild archive \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -archivePath /tmp/bragwise.xcarchive \
  -allowProvisioningUpdates
```

**2. Create `/tmp/ExportOptions.plist`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store-connect</string>
    <key>teamID</key>
    <string><teamId></string>
    <key>uploadSymbols</key>
    <true/>
    <key>signingStyle</key>
    <string>manual</string>
    <key>signingCertificate</key>
    <string>Apple Distribution</string>
    <key>provisioningProfiles</key>
    <dict>
        <key>se.atte.bragwise.Bragwise</key>
        <string>Bragwise Appstore</string>
    </dict>
</dict>
</plist>
```

**3. Export IPA**
```bash
xcodebuild -exportArchive \
  -archivePath /tmp/bragwise.xcarchive \
  -exportOptionsPlist /tmp/ExportOptions.plist \
  -exportPath /tmp/bragwise-export \
  -allowProvisioningUpdates
```

IPA is at `/tmp/bragwise-export/Bragwise.ipa` — drag into Transporter to upload.

Prereqs: Xcode signed in with Apple ID, bundle ID `se.atte.bragwise.Bragwise` registered in App Store Connect.

The Release config uses **manual** signing (`CODE_SIGN_STYLE = Manual`, `CODE_SIGN_IDENTITY = "Apple Distribution"`, `PROVISIONING_PROFILE_SPECIFIER = "Bragwise Appstore"` in `iosApp.xcodeproj`). This avoids automatic signing's requirement for a registered iOS device.

A fresh `git clone` does **not** carry provisioning profiles — they live in `~/Library/MobileDevice/Provisioning Profiles/`, not the repo. If archiving fails with `No profiles for 'se.atte.bragwise.Bragwise' were found`, recreate the App Store profile:

1. developer.apple.com → Certificates, Identifiers & Profiles → Profiles → **+**
2. **App Store Connect** (Distribution) → App ID `se.atte.bragwise.Bragwise` → cert **Apple Distribution** → name it `Bragwise Appstore` → Generate → Download.
3. Install by UUID (double-click no longer auto-installs on recent Xcode):
   ```bash
   UUID=$(security cms -D -i ~/Downloads/Bragwise_Appstore.mobileprovision | plutil -extract UUID raw -)
   cp ~/Downloads/Bragwise_Appstore.mobileprovision "$HOME/Library/MobileDevice/Provisioning Profiles/$UUID.mobileprovision"
   ```

Before building, ensure `useMock = false` in `iosApp/iosApp/iOSApp.swift`.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…