# Plan 1 — Scaffold & Browse — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce an installable Android TV APK that launches from the TV launcher, shows a WebView with a simple URL bar, and loads any site — the baseline browser that validates the build→sideload→run loop on the real cheap box.

**Architecture:** Single Gradle `:app` module, Kotlin, classic Android Views, no AppCompat/Compose/Leanback library (lightest). A plain `Activity` hosts a `WebView` plus a top URL bar; a pure-Kotlin `UrlNormalizer` (unit-tested) turns raw input into a loadable URL. Release APK is signed from a gitignored keystore; GitHub Actions builds debug APKs on push and a signed APK on release.

**Tech Stack:** AGP 9.1.1 (built-in Kotlin — no separate Kotlin plugin) · Gradle 9.3.1 · JDK 17 · compileSdk 36 / build-tools 36.0.0 · minSdk 21 / targetSdk 34 · androidx.core-ktx 1.19.0 · JUnit 4.13.2.

**Scope vs spec:** This plan covers spec §4 (stack), §5/§6.1 (manifest/launcher), a minimal §6.3 (WebView host, no cursor yet), URL entry (subset of §6.2), §11 (build/CI), §12 (open-source: README/LICENSE). Favorites (§6.2), cursor (§6.3), sniffer/handoff/player (§6.4–6.6) are Plans 2–4.

**Conventions:** Conventional one-line commit messages, no AI attribution. Exact paths are absolute under `/Users/mohammad/Projects/mrowser`.

---

## File map

| File | Responsibility |
|------|----------------|
| `settings.gradle.kts` | Module includes + plugin/dependency repos |
| `build.gradle.kts` (root) | Declares AGP + Kotlin plugins (apply false) |
| `gradle.properties` | Gradle/AndroidX flags |
| `gradle/libs.versions.toml` | Version catalog (single source of versions) |
| `app/build.gradle.kts` | App module config + signing |
| `app/proguard-rules.pro` | Empty proguard rules (referenced by release) |
| `app/src/main/AndroidManifest.xml` | Leanback launcher, permissions, theme |
| `app/src/main/res/values/strings.xml` | App name + UI strings |
| `app/src/main/res/values/themes.xml` | Fullscreen Material dark theme |
| `app/src/main/res/drawable/banner.xml` | 320×180 TV banner (vector) |
| `app/src/main/res/drawable/ic_launcher.xml` | App icon (vector) |
| `app/src/main/res/layout/activity_main.xml` | URL bar + WebView layout |
| `app/src/main/kotlin/net/mrowser/MainActivity.kt` | WebView host + URL navigation + Back |
| `app/src/main/kotlin/net/mrowser/web/UrlNormalizer.kt` | Raw input → loadable URL (pure logic) |
| `app/src/test/kotlin/net/mrowser/web/UrlNormalizerTest.kt` | UrlNormalizer unit tests |
| `.github/workflows/build.yml` | CI: test + debug APK on push, signed APK on release |
| `README.md`, `LICENSE` | Open-source metadata |

---

## Task 0: Install headless Android SDK locally

No tests; environment setup. Nothing is committed (SDK + `local.properties` are outside the repo / gitignored).

- [ ] **Step 1: Install the SDK command-line tools**

Run (Homebrew path; if `brew` is absent, install Android Studio instead and skip to Step 2 using its bundled `sdkmanager`):

```bash
brew install --cask android-commandlinetools
```

Expected: cask installs, `sdkmanager` becomes available on PATH.

- [ ] **Step 2: Install platform + build-tools into the SDK root and accept licenses**

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
mkdir -p "$ANDROID_HOME"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

Expected: `platforms/android-36` and `build-tools/36.0.0` exist under `$ANDROID_HOME`.

- [ ] **Step 3: Point the project at the SDK**

Create `/Users/mohammad/Projects/mrowser/local.properties` (gitignored):

```properties
sdk.dir=/Users/mohammad/Library/Android/sdk
```

- [ ] **Step 4: Verify**

```bash
ls "$HOME/Library/Android/sdk/platforms" "$HOME/Library/Android/sdk/build-tools"
```

Expected: lists `android-36` and `36.0.0`.

---

## Task 1: Gradle project skeleton + wrapper

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`
- Generated: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Create a bare settings file so the wrapper can be generated against an empty build**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "mrowser"
```

- [ ] **Step 2: Bootstrap the Gradle wrapper with a one-off Gradle 9.1.0**

```bash
curl -sL https://services.gradle.org/distributions/gradle-9.3.1-bin.zip -o /tmp/gradle-9.3.1-bin.zip
unzip -q -o /tmp/gradle-9.3.1-bin.zip -d /tmp
/tmp/gradle-9.3.1/bin/gradle -p /Users/mohammad/Projects/mrowser wrapper --gradle-version 9.3.1 --distribution-type bin
```

Expected: `BUILD SUCCESSFUL`; `gradlew` and `gradle/wrapper/gradle-wrapper.jar` now exist in the project.

- [ ] **Step 3: Verify the wrapper**

Run: `/Users/mohammad/Projects/mrowser/gradlew --version`
Expected: prints `Gradle 9.3.1`. (AGP 9.1.1 requires Gradle ≥ 9.3.1.)

- [ ] **Step 4: Add the version catalog**

Create `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.1.1"
coreKtx = "1.19.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
```

- [ ] **Step 5: Add root build + properties + module include**

Create `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
}
```

(AGP 9 ships built-in Kotlin support enabled by default, so the separate
`org.jetbrains.kotlin.android` plugin is intentionally omitted — applying it
clashes with AGP's own `kotlin` extension.)

Create `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.caching=true
android.useAndroidX=true
kotlin.code.style=official
```

Append the module include to `settings.gradle.kts` (add as the last line):

```kotlin
include(":app")
```

- [ ] **Step 6: Add the app module build file**

Create `app/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "net.mrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.mrowser"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
```

Create `app/proguard-rules.pro`:

```proguard
# No app-specific rules yet. Release does not minify (isMinifyEnabled = false).
```

- [ ] **Step 7: Verify the project configures**

Run: `/Users/mohammad/Projects/mrowser/gradlew :app:tasks --group=build -q`
Expected: lists tasks including `assembleDebug` and `assembleRelease`; `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add -A
git -C /Users/mohammad/Projects/mrowser commit -m "chore: add gradle project skeleton and wrapper"
```

---

## Task 2: Leanback manifest, resources, and a minimal WebView activity

**Files:**
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/drawable/banner.xml`, `app/src/main/res/drawable/ic_launcher.xml`, `app/src/main/kotlin/net/mrowser/MainActivity.kt`

- [ ] **Step 1: Add strings**

Create `app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">mrowser</string>
    <string name="url_hint">Enter address</string>
    <string name="go">Go</string>
</resources>
```

- [ ] **Step 2: Add a fullscreen dark theme**

Create `app/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.Mrowser" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">@android:color/black</item>
    </style>
</resources>
```

- [ ] **Step 3: Add the TV banner (vector, 320×180)**

Create `app/src/main/res/drawable/banner.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="320dp"
    android:height="180dp"
    android:viewportWidth="320"
    android:viewportHeight="180">
    <path android:fillColor="#111111" android:pathData="M0,0 H320 V180 H0 Z" />
    <path android:fillColor="#E50914" android:pathData="M134,70 L172,90 L134,110 Z" />
</vector>
```

- [ ] **Step 4: Add the launcher icon (vector)**

Create `app/src/main/res/drawable/ic_launcher.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#111111" android:pathData="M0,0 H108 V108 H0 Z" />
    <path android:fillColor="#E50914" android:pathData="M44,38 L70,54 L44,70 Z" />
</vector>
```

- [ ] **Step 5: Add the manifest**

Create `app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:banner="@drawable/banner"
        android:label="@string/app_name"
        android:theme="@style/Theme.Mrowser"
        android:usesCleartextTraffic="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

(Both `LAUNCHER` and `LEANBACK_LAUNCHER` are declared so the icon appears on cheap boxes that use either a phone-style or a TV launcher.)

- [ ] **Step 6: Add the minimal activity**

Create `app/src/main/kotlin/net/mrowser/MainActivity.kt`:

```kotlin
package net.mrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
        }
        setContentView(webView)
        webView.loadData(WELCOME_HTML, "text/html", "utf-8")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val WELCOME_HTML =
            "<html><body style='background:#111;color:#eee;font-family:sans-serif;" +
            "display:flex;height:100vh;margin:0;align-items:center;justify-content:center'>" +
            "<h1>mrowser</h1></body></html>"
    }
}
```

- [ ] **Step 7: Build the debug APK**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 8: Manual smoke test on the box**

Copy `app/build/outputs/apk/debug/app-debug.apk` to the Sunyia box, install, launch.
Expected: app appears in the launcher with the banner; opens to a black screen showing "mrowser". Back exits.

- [ ] **Step 9: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add -A
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add leanback manifest, theme, and minimal webview activity"
```

---

## Task 3: UrlNormalizer (TDD)

**Files:**
- Create: `app/src/main/kotlin/net/mrowser/web/UrlNormalizer.kt`
- Test: `app/src/test/kotlin/net/mrowser/web/UrlNormalizerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/net/mrowser/web/UrlNormalizerTest.kt`:

```kotlin
package net.mrowser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlNormalizerTest {

    @Test fun `prepends https when scheme is missing`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("example.com"))
    }

    @Test fun `keeps an existing http scheme`() {
        assertEquals("http://example.com/x", UrlNormalizer.normalize("http://example.com/x"))
    }

    @Test fun `trims surrounding whitespace`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("  example.com  "))
    }

    @Test fun `keeps full path and query`() {
        assertEquals(
            "https://meghan.andrei-tarkovsky.net/stream/1/2/?h=1080",
            UrlNormalizer.normalize("meghan.andrei-tarkovsky.net/stream/1/2/?h=1080")
        )
    }

    @Test fun `allows localhost without a dot`() {
        assertEquals("https://localhost", UrlNormalizer.normalize("localhost"))
    }

    @Test fun `returns null for blank input`() {
        assertNull(UrlNormalizer.normalize("   "))
    }

    @Test fun `returns null for a bare word that is not a host`() {
        assertNull(UrlNormalizer.normalize("notaurl"))
    }

    @Test fun `returns null when input contains spaces`() {
        assertNull(UrlNormalizer.normalize("foo bar baz"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: FAIL — compilation error `unresolved reference: UrlNormalizer`.

- [ ] **Step 3: Write the minimal implementation**

Create `app/src/main/kotlin/net/mrowser/web/UrlNormalizer.kt`:

```kotlin
package net.mrowser.web

/** Turns raw URL-bar input into a loadable URL, or null if it is not one. */
object UrlNormalizer {

    private val SCHEME = Regex("(?i)^[a-z][a-z0-9+.-]*://")

    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return null

        val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val host = hostOf(withScheme) ?: return null
        val isValidHost = host == "localhost" || host.contains('.')
        return if (isValidHost) withScheme else null
    }

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme.substringBefore('/').substringBefore('?')
        val host = authority.substringAfter('@', authority).substringBefore(':')
        return host.ifEmpty { null }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `/Users/mohammad/Projects/mrowser/gradlew test`
Expected: `BUILD SUCCESSFUL`; 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add -A
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add url normalizer with tests"
```

---

## Task 4: URL entry bar + WebView navigation

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/kotlin/net/mrowser/MainActivity.kt` (replace whole file)

- [ ] **Step 1: Add the layout**

Create `app/src/main/res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/black">

    <LinearLayout
        android:orientation="horizontal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="8dp">

        <EditText
            android:id="@+id/urlInput"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="@string/url_hint"
            android:imeOptions="actionGo"
            android:inputType="textUri"
            android:textColor="@android:color/white"
            android:textColorHint="#888888" />

        <Button
            android:id="@+id/goButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/go" />
    </LinearLayout>

    <WebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
</LinearLayout>
```

- [ ] **Step 2: Replace MainActivity to use the layout + UrlNormalizer**

Replace the entire contents of `app/src/main/kotlin/net/mrowser/MainActivity.kt`:

```kotlin
package net.mrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import net.mrowser.web.UrlNormalizer

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)
        val goButton = findViewById<Button>(R.id.goButton)

        webView.webViewClient = WebViewClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        goButton.setOnClickListener { loadFromInput() }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadFromInput()
                true
            } else {
                false
            }
        }
    }

    private fun loadFromInput() {
        val url = UrlNormalizer.normalize(urlInput.text.toString()) ?: return
        webView.loadUrl(url)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
```

- [ ] **Step 3: Build**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Manual test on the box**

Install the new APK. Focus the URL field, type the Sunyia stream URL, press Go (or the remote's IME Go).
Expected: site loads in the WebView and plays (sync still unfixed — that is Plan 4). Back navigates web history, then exits. This confirms the WebView path works for the real site on the box.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add -A
git -C /Users/mohammad/Projects/mrowser commit -m "feat: add url entry bar and webview navigation"
```

---

## Task 5: Release signing config

**Files:**
- Modify: `app/build.gradle.kts` (add signing)
- Create (local, gitignored): `release.keystore`, `keystore.properties`

- [ ] **Step 1: Generate a release keystore (gitignored by `*.keystore`)**

Run (replace `CHANGE_ME` with a real password you keep safe):

```bash
keytool -genkeypair -v \
  -keystore /Users/mohammad/Projects/mrowser/release.keystore \
  -alias mrowser -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass CHANGE_ME -keypass CHANGE_ME \
  -dname "CN=mrowser, O=mrowser, C=US"
```

Expected: `release.keystore` created in the project root.

- [ ] **Step 2: Create `keystore.properties` (gitignored)**

Create `/Users/mohammad/Projects/mrowser/keystore.properties`:

```properties
storeFile=release.keystore
storePassword=CHANGE_ME
keyAlias=mrowser
keyPassword=CHANGE_ME
```

- [ ] **Step 3: Wire signing into the app build (optional-if-absent)**

Replace the entire contents of `app/build.gradle.kts`:

```kotlin
import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val hasKeystore = keystorePropsFile.exists()
val keystoreProps = Properties().apply {
    if (hasKeystore) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "net.mrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.mrowser"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
```

- [ ] **Step 4: Build the signed release APK**

Run: `/Users/mohammad/Projects/mrowser/gradlew assembleRelease`
Expected: `BUILD SUCCESSFUL`; signed APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 5: Confirm the keystore is not tracked**

Run: `git -C /Users/mohammad/Projects/mrowser status --porcelain`
Expected: neither `release.keystore` nor `keystore.properties` appears (both gitignored).

- [ ] **Step 6: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add app/build.gradle.kts
git -C /Users/mohammad/Projects/mrowser commit -m "build: add release signing config"
```

---

## Task 6: GitHub Actions CI

**Files:**
- Create: `.github/workflows/build.yml`

- [ ] **Step 1: Add the workflow**

Create `.github/workflows/build.yml`:

```yaml
name: build

on:
  push:
    branches: [ main ]
  pull_request:
  release:
    types: [ published ]

jobs:
  test-and-debug:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - name: Install SDK packages
        run: sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
      - name: Unit tests
        run: ./gradlew test
      - name: Build debug APK
        run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: mrowser-debug-apk
          path: app/build/outputs/apk/debug/*.apk

  release-apk:
    if: github.event_name == 'release'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - name: Install SDK packages
        run: sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
      - name: Restore keystore from secrets
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 --decode > release.keystore
          {
            echo "storeFile=release.keystore"
            echo "storePassword=$STORE_PASSWORD"
            echo "keyAlias=$KEY_ALIAS"
            echo "keyPassword=$KEY_PASSWORD"
          } > keystore.properties
      - name: Build signed release APK
        run: ./gradlew assembleRelease
      - name: Attach APK to the release
        uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/*.apk
```

- [ ] **Step 2: Document required repository secrets**

The `release-apk` job needs these GitHub repo secrets (Settings → Secrets → Actions): `KEYSTORE_BASE64` (output of `base64 -i release.keystore`), `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Note this in the README in Task 7.

- [ ] **Step 3: Verify the workflow parses locally (optional)**

If `act` or `yamllint` is available, run `yamllint .github/workflows/build.yml`; otherwise rely on the first push to validate.
Expected: no YAML syntax errors.

- [ ] **Step 4: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add .github/workflows/build.yml
git -C /Users/mohammad/Projects/mrowser commit -m "ci: add github actions build and release workflow"
```

---

## Task 7: README + LICENSE

**Files:**
- Create: `README.md`, `LICENSE`

- [ ] **Step 1: Add the MIT license**

Create `/Users/mohammad/Projects/mrowser/LICENSE`:

```text
MIT License

Copyright (c) 2026 Mohammad Salehivaziri

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 2: Add the README**

Create `/Users/mohammad/Projects/mrowser/README.md`:

```markdown
# mrowser

A lightweight Android TV browser optimized for streaming video. It browses any
site from a remote and (from Plan 4 onward) hands HLS video off to a native
Media3/ExoPlayer player for tight audio/video sync that the WebView's built-in
player cannot deliver.

## Status

Early development. Plan 1 (this milestone) is a baseline browser: Leanback
launcher entry, WebView, and a URL bar. No favorites, cursor, or native player
yet — see `docs/superpowers/plans/`.

## Build

Requirements: JDK 17, Android SDK with `platforms;android-36` and
`build-tools;36.0.0`.

```bash
./gradlew assembleDebug      # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease    # signed release APK (needs keystore.properties)
./gradlew test               # unit tests
```

## Install

Copy the APK to the device (USB or file manager) and install it. Enable
"install from unknown sources" if prompted.

## Release signing

Release builds read `keystore.properties` (gitignored). CI builds a signed APK
on GitHub Releases using the repo secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`.

## Disclaimer

mrowser is a general-purpose web browser. It ships with no bundled content and
no preset sites. You are responsible for the content you choose to access and
for complying with applicable law.

## License

MIT — see `LICENSE`.
```

- [ ] **Step 3: Commit**

```bash
git -C /Users/mohammad/Projects/mrowser add README.md LICENSE
git -C /Users/mohammad/Projects/mrowser commit -m "docs: add readme and mit license"
```

---

## Self-review (completed by plan author)

- **Spec coverage (Plan 1 scope):** stack/build §4/§11 → Tasks 0,1,5,6; manifest/launcher §6.1 → Task 2; minimal WebView host §6.3 → Tasks 2,4; URL entry (subset §6.2) → Tasks 3,4; open-source §12 → Task 7. Favorites/cursor/sniffer/handoff/player intentionally deferred to Plans 2–4 (stated in header).
- **Placeholder scan:** no TBD/TODO; `CHANGE_ME` is an explicit user-supplied secret, not a plan gap.
- **Type consistency:** `UrlNormalizer.normalize(String): String?` defined in Task 3 and called identically in Task 4; view IDs `webView`/`urlInput`/`goButton` match between `activity_main.xml` and `MainActivity.kt`; `net.mrowser` namespace/applicationId consistent across manifest, build file, and sources.
```
