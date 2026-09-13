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

### Bumping the app version

`gradle.properties` is the single source of truth (`app.versionName`, `app.versionCode`).
Use the script — it updates the Android properties *and* syncs the iOS xcconfig, and
increments `versionCode` by 1 automatically:

```bash
./scripts/bump-version.sh 0.7.11
```

Consumers, all fed from those two properties:
- Android — `androidApp/build.gradle.kts` (`versionCode` / `versionName`)
- iOS — `iosApp/Configuration/Config.xcconfig` (`CURRENT_PROJECT_VERSION`, `MARKETING_VERSION`)
- shared — `shared/build.gradle.kts`

Patch numbers run past 9 (`0.4.9 → 0.4.10 → 0.4.11`), so the successor to `0.7.9` is
`0.7.10`, not `0.8.0`.

If you edit `gradle.properties` by hand instead, sync iOS with
`./gradlew generateXcconfig` — don't hand-edit `Config.xcconfig`.

### Building iOS IPA for App Store (Transporter)

**1. Archive** (~10 min — builds the shared KMP framework)
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
    <string>2AN99UVZWL</string>
    <key>uploadSymbols</key>
    <true/>
    <key>signingStyle</key>
    <string>automatic</string>
</dict>
</plist>
```

`signingStyle` must be `automatic` to match the project's own signing config (see
"Signing" below). Do **not** add a `provisioningProfiles` dict — the named profile
it would reference does not exist.

**3. Export IPA**
```bash
xcodebuild -exportArchive \
  -archivePath /tmp/bragwise.xcarchive \
  -exportOptionsPlist /tmp/ExportOptions.plist \
  -exportPath /tmp/bragwise-export \
  -allowProvisioningUpdates
```

IPA is at `/tmp/bragwise-export/Bragwise.ipa` — drag into Transporter to upload.

**4. Verify before uploading**
```bash
plutil -p /tmp/bragwise-export/DistributionSummary.plist | head -15
```
Check two things: `buildNumber` matches the version you bumped to, and
`certificate.type` is `Apple Distribution` (not `Apple Development`). An
`Apple Development` certificate here means the export did not re-sign and the
upload will be rejected.

Prereqs: Xcode signed in with Apple ID, bundle ID `se.atte.bragwise.Bragwise` registered in App Store Connect.

### Signing

The Release config uses **automatic** signing (`CODE_SIGN_STYLE = Automatic`,
`CODE_SIGN_IDENTITY = "Apple Development"`, `PROVISIONING_PROFILE_SPECIFIER = ""`
in `iosApp.xcodeproj`). `-allowProvisioningUpdates` on both commands is therefore
required — it resolves the distribution profile from the developer portal at
archive/export time.

Consequence worth knowing: the **archive is `Apple Development`-signed**, and only
the *export* step re-signs with `Apple Distribution`. This is expected for this
flow, but it means `/tmp/bragwise.xcarchive` is not independently distributable —
only the exported IPA is. Verify via step 4 above rather than by inspecting the
archive's `SigningIdentity`.

Provisioning profiles are not carried by a fresh `git clone` — they live in
`~/Library/MobileDevice/Provisioning Profiles/`, not the repo. With automatic
signing plus `-allowProvisioningUpdates` this is usually handled for you; that
directory may legitimately contain **no** Bragwise profile and the build will
still succeed. If archiving does fail with `No profiles for
'se.atte.bragwise.Bragwise' were found`, create an App Store profile manually:

1. developer.apple.com → Certificates, Identifiers & Profiles → Profiles → **+**
2. **App Store Connect** (Distribution) → App ID `se.atte.bragwise.Bragwise` → cert **Apple Distribution** → name it `Bragwise Appstore` → Generate → Download.
3. Install by UUID (double-click no longer auto-installs on recent Xcode):
   ```bash
   UUID=$(security cms -D -i ~/Downloads/Bragwise_Appstore.mobileprovision | plutil -extract UUID raw -)
   cp ~/Downloads/Bragwise_Appstore.mobileprovision "$HOME/Library/MobileDevice/Provisioning Profiles/$UUID.mobileprovision"
   ```

Note: the App ID has **Sign In with Apple** enabled. Enabling (or changing) a
capability invalidates existing profiles — if you hit `No profiles for
'se.atte.bragwise.Bragwise' were found` or an entitlement-mismatch signing
error after pulling a capability change, regenerate the profile with the
steps above and delete the stale copy from
`~/Library/MobileDevice/Provisioning Profiles/` first.

Before building, ensure `USE_MOCK_DATA = false` in
`shared/src/commonMain/kotlin/se/atte/bragwise/BuildFlags.kt`. That single `const`
is the only place to change it — all three platform entry points read it, and
`iosApp/iosApp/iOSApp.swift` derives its local `useMock` from
`BuildFlags.shared.USE_MOCK_DATA` rather than declaring its own value. The flag is
**not** gated by build type, so a `true` value ships in a release build.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…