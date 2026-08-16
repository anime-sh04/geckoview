# GeckoBrowser

A minimal Android app (Kotlin) that embeds Mozilla's **GeckoView** — the same
rendering engine that powers Firefox for Android — instead of the built-in
Android `WebView`. It shows a native home screen with a **START** button;
pressing it opens a full-screen GeckoView browser loaded at
`https://10.11.111.11` (your dev/test server), with a bundled WebExtension
that can run content scripts on the page and exchange messages with the app.

## 1. What this project does

- Native Kotlin home screen with a title, description, and a **START** button.
- On START, swaps in a full-screen `GeckoView` and loads `https://10.11.111.11`.
- Shows a loading spinner while the page loads, and a native error screen
  (with Retry / Back to home) on load failure — nothing is delegated to the
  external Chrome/Firefox app.
- Bundles a Firefox WebExtension (`app/src/main/assets/extension/`) that is
  installed into the GeckoView runtime and runs a content script on the dev
  site.
- Demonstrates two-way messaging: Android → extension (`START`) and
  extension → Android (`PAGE_READY`), via GeckoView's native
  `WebExtension.Port` / `MessageDelegate` APIs.
- Android back button: goes back in browser history if possible, otherwise
  returns to the home screen.
- Only requests the `INTERNET` permission.
- Builds a debug APK automatically via GitHub Actions on every push.

Contains **no** CAPTCHA-solving or anti-bot-bypass logic — only the
extension/messaging plumbing you asked for.

## 2. Why GeckoView instead of WebView

Android `WebView` is backed by Chromium (via the "Android System WebView"
component). GeckoView embeds Mozilla's **Gecko** engine — the same engine as
desktop/mobile Firefox — as a library inside your own app process. That gets
you:

- A different, independently-updated rendering/JS engine, decoupled from
  whatever WebView build happens to be installed on the device.
- First-class support for **Firefox WebExtensions** (Manifest V2/V3-style
  content scripts, background scripts, `browser.*` APIs) running inside your
  app, which is what this project's extension architecture is built on.
- An engine you can update independently of the OS, by bumping the GeckoView
  dependency version.

## 3. Project structure

```
GeckoBrowser/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/{gradle-wrapper.jar, gradle-wrapper.properties}
├── .github/workflows/
│   ├── build.yml            # builds + uploads the debug APK on every push
│   └── release.yml          # optional, manual, requires signing (see file)
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/geckobrowser/
│       │   ├── MainActivity.kt        # home screen + GeckoView + messaging
│       │   └── AppGeckoRuntime.kt     # shared GeckoRuntime singleton
│       ├── res/
│       │   ├── layout/activity_main.xml
│       │   ├── values/{strings,colors,themes}.xml
│       │   ├── drawable/ic_launcher.xml
│       │   └── xml/network_security_config.xml
│       └── assets/extension/
│           ├── manifest.json
│           ├── background.js
│           └── content.js
└── README.md
```

## 4. Opening the project in Android Studio

1. Install a recent stable **Android Studio** (Ladybug/Koala or newer).
2. `File → Open`, select the project's root folder (the one containing
   `settings.gradle.kts`).
3. Let Gradle sync. On first sync it will download:
   - The Android Gradle Plugin / Kotlin plugin from Google's / Maven
     Central's repositories.
   - The GeckoView AAR from `https://maven.mozilla.org/maven2/` (configured
     in `settings.gradle.kts`) — this file is large (100+ MB), so the first
     sync can take a while.
4. Run the `app` configuration on a device or emulator (**API 23+**).

## 5. Building locally from the command line

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat assembleDebug` instead.

## 6. Installing the generated APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or drag the APK onto a running emulator, or transfer it to a device and open
it (you'll need "install unknown apps" allowed for whichever app you use to
open it).

## 7. Changing the website URL

The URL is defined once, in `app/src/main/res/values/strings.xml`:

```xml
<string name="target_url" translatable="false">https://10.11.111.11</string>
```

If you point it at a different host, also update:

- The `<domain>` entry in `app/src/main/res/xml/network_security_config.xml`.
- The `matches` pattern in `app/src/main/assets/extension/manifest.json`
  (currently `https://10.11.111.11/*`), if you want the content script to
  keep running on the new host.

## 8. How the WebExtension works

`app/src/main/assets/extension/` is a standard (Manifest V2) Firefox
WebExtension:

- `manifest.json` declares a background script and a content script scoped
  to `https://10.11.111.11/*`.
- `content.js` runs in the page context of the loaded site and logs a
  message, then reports `PAGE_READY` to the background script.
- `background.js` runs in the extension's own background context, opens a
  persistent native port to the Android app (`browser.runtime.connectNative("browser")`),
  and forwards content-script messages over that port.

At runtime, `MainActivity.installExtension()` calls:

```kotlin
runtime.webExtensionController.ensureBuiltIn(
    "resource://android/assets/extension/",
    "geckobrowser-example@example.com" // must match manifest.json's gecko.id
)
```

`resource://android/assets/extension/` resolves to the bundled
`app/src/main/assets/extension/` folder inside the APK — no separate
packaging or unzip step is needed. `ensureBuiltIn` (rather than the older
`installBuiltIn`) is idempotent: it skips reinstalling if the same
extension ID/version is already registered.

## 9. How Android ↔ extension communication works

GeckoView provides its own app↔extension messaging, independent of desktop
Firefox's external native-messaging-host mechanism:

- The extension calls `browser.runtime.connectNative("browser")`. The
  string `"browser"` is an arbitrary "native app id" — it just has to match
  what the Android side registers.
- On the Kotlin side, `extension.setMessageDelegate(delegate, "browser")`
  registers a `WebExtension.MessageDelegate`. Its `onConnect(port)` callback
  fires when the extension opens the port, handing you a `WebExtension.Port`
  you can call `port.postMessage(...)` on (Android → extension) and attach a
  `PortDelegate` to (`onPortMessage` for extension → Android).

Demonstration flow included:

- **Android → extension:** as soon as the port connects, `MainActivity`
  sends `{"type": "START"}`.
- **Extension → Android:** `content.js` reports `PAGE_READY` to
  `background.js`, which forwards it to Android over the port; separately,
  `background.js` also replies with `{"type": "PAGE_READY"}` when it
  receives the `START` message.

Both example messages are isolated in one place (`installExtension()` in
`MainActivity.kt`, and the two extension `.js` files) so you can swap in
your own message types and payloads later without touching the rest of the
app.

## 10. Development-only HTTPS/certificate considerations for `https://10.11.111.11`

An IP-address HTTPS endpoint almost always means a self-signed or
internal-CA certificate, which the OS trust store will reject by default —
correctly, since a browser/engine shouldn't blindly trust arbitrary
certificates.

This project does **not** disable certificate validation. Instead,
`app/src/main/res/xml/network_security_config.xml` uses Android's own
`<debug-overrides>` mechanism:

```xml
<debug-overrides>
    <trust-anchors>
        <certificates src="system" />
        <certificates src="user" />
    </trust-anchors>
</debug-overrides>
```

- `<debug-overrides>` is **automatically stripped out of release builds** by
  the Android Gradle Plugin — it only ever applies to a `debug` build
  (`assembleDebug`/`installDebug`). A release build only trusts the system
  CA store, exactly like a normal app.
- Within a debug build, it additionally trusts "user" CAs — certificates
  you've manually installed as trusted on that specific device/emulator.

To make a debug build trust your dev server's self-signed cert:

1. Get the certificate in DER or PEM form, e.g.:
   ```bash
   openssl s_client -connect 10.11.111.11:443 -showcerts </dev/null 2>/dev/null \
     | openssl x509 -outform der -out dev-cert.der
   ```
2. Push it to the device/emulator and install it as a user CA:
   - Emulator/device: `adb push dev-cert.der /sdcard/Download/`
   - On the device: **Settings → Security → Encryption & credentials →
     Install a certificate → CA certificate**, then pick the file. Android
     will warn that user CAs let apps see your traffic — that's expected and
     limited to this specific certificate.
3. Reinstall/run the **debug** build. It will now trust `https://10.11.111.11`.

If you later need this to work in a signed **release** build for wider
testing (not recommended for anything beyond controlled internal testing),
add a dedicated `<domain-config>` for `10.11.111.11` pinned to that
specific certificate/CA (bundled as a raw resource via `<pin-set>` or
`<certificates src="@raw/dev_ca" />`), scoped only to that one domain — never
add a blanket `<base-config cleartextTrafficPermitted="true">` or a global
`src="user"` trust anchor to a release build.

## 11. Pushing to GitHub

```bash
git init
git add .
git commit -m "Initial Android GeckoView app"
git branch -M main
git remote add origin <MY_GITHUB_REPO>
git push -u origin main
```

## 12. How GitHub Actions builds the APK

`.github/workflows/build.yml` runs on every push/PR to `main` (and can also
be triggered manually via "Run workflow"). It:

1. Checks out the repo.
2. Installs JDK 17 (Temurin).
3. Installs the Android SDK (`android-actions/setup-android@v3`).
4. Caches the Gradle dependency cache.
5. Makes `./gradlew` executable.
6. Runs `./gradlew assembleDebug`.
7. Uploads `app/build/outputs/apk/debug/app-debug.apk` as a build artifact.

No signing configuration is required for this — debug builds use Android's
automatically-generated debug keystore.

`.github/workflows/release.yml` is a separate, **manual-only** workflow
skeleton for a signed release build; it will not run on push, and
`assembleRelease` will fail until you add a real `signingConfigs` block plus
GitHub secrets for your keystore, as described in that file's comments.

## 13. Where to download the built APK

After a workflow run finishes:

**GitHub → your repo → Actions tab → click the workflow run → "Artifacts"
section at the bottom → download `app-debug-apk`.**

It downloads as a zip containing `app-debug.apk`.

## 14. Notes on keeping GeckoView current

`app/build.gradle.kts` pins:

```kotlin
implementation("org.mozilla.geckoview:geckoview:153.0.20260810162159")
```

This is the "universal" release-channel artifact (bundles all supported
ABIs), which is the simplest way to get started. Before you build, it's
worth checking
<https://maven.mozilla.org/?prefix=maven2/org/mozilla/geckoview/geckoview/>
for a newer build — GeckoView ships a new version roughly every 4 weeks,
following the Firefox release train, and Mozilla recommends staying current
for security fixes. For a smaller production APK, switch to the per-ABI
artifacts (`geckoview-release-arm64-v8a`, etc.) documented at
<https://geckoview.dev>.

Because GeckoView's Kotlin API surface can shift slightly between releases
(delegate method signatures, deprecations), if you bump the version and hit
a compile error in `MainActivity.kt`, check that release's API docs at
<https://geckoview.dev> / the Javadoc bundled with the AAR — the delegate
interfaces used here (`ProgressDelegate`, `NavigationDelegate`,
`WebExtension.MessageDelegate`/`PortDelegate`) have been stable for a long
time, but exact parameter types occasionally change.
"# geckoview" 
