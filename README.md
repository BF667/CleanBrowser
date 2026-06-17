# CleanBrowser

A fast, lightweight, Chromium-based Android browser with a clean dark UI. No AI. No bloat. Just browsing.

<p align="center">
  <img src="https://img.shields.io/badge/Min%20SDK-24-green" alt="Min SDK">
  <img src="https://img.shields.io/badge/Target%20SDK-34-blue" alt="Target SDK">
  <img src="https://img.shields.io/badge/Kotlin-1.9.22-purple" alt="Kotlin">
  <img src="https://img.shields.io/badge/AGP-8.2.2-orange" alt="AGP">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

## Features

- **Multi-Tab Browsing** — Open, switch, and close tabs with a smooth tab switcher overlay
- **Incognito Mode** — Private browsing with no cache, cookies, or history saved
- **Smart URL Bar** — Auto-detects URLs vs search queries, Google search fallback
- **SSL Indicator** — Visual lock icon shows secure (green) or insecure (red) connections
- **Desktop Mode** — Request desktop sites on demand
- **Find in Page** — Search for text within any webpage
- **Bookmarks** — Save and manage your favorite pages with SharedPreferences
- **History** — Full browsing history with clear option
- **Login / App Lock** — PIN-based app lock to keep your browser private
- **Settings** — Customizable search engine, homepage, and data clearing
- **Dark UI** — Original dark theme inspired by Chrome but uniquely styled
- **Pull to Refresh** — Swipe down to reload the current page
- **Share Pages** — Share current URL to any app
- **Standalone Build** — Build a signed APK without Android Studio

## Screenshots

| Tab Switcher | Settings | Login |
|:---:|:---:|:---:|
| Dark card-based tab list | Search engine, homepage, clear data | PIN lock screen |

## Download

Get the latest release APK from [GitHub Releases](https://github.com/BF667/CleanBrowser/releases).

## Build from Source

No Android Studio needed. The build script downloads everything automatically.

### Prerequisites

- **Java 17** (or newer) — `java -version`
- **curl** and **unzip`

### Build

```bash
chmod +x build-release.sh
./build-release.sh
```

The signed APK will be at `app/build/outputs/apk/release/app-release.apk`.

### What the script does

1. Downloads Android SDK command-line tools
2. Accepts SDK licenses
3. Installs required SDK packages (platform 34, build-tools 34.0.0)
4. Generates a signing keystore if one doesn't exist
5. Builds the release APK with R8/ProGuard minification

### Manual Build (with Android Studio)

```bash
# Generate your own keystore
keytool -genkeypair -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000

# Build
./gradlew assembleRelease
```

## Project Structure

```
CleanBrowser/
├── app/
│   └── src/main/
│       ├── java/com/cleanbrowser/
│       │   ├── MainActivity.kt          # Core browser, tabs, URL bar
│       │   ├── ui/
│       │   │   └── TabAdapter.kt        # Tab switcher RecyclerView adapter
│       │   ├── LoginActivity.kt         # PIN login/app lock
│       │   ├── SettingsActivity.kt      # Search engine, homepage, clear data
│       │   ├── BookmarksActivity.kt     # Save & manage bookmarks
│       │   └── HistoryActivity.kt       # Browsing history
│       ├── res/
│       │   ├── layout/                  # Activity & item layouts
│       │   ├── values/                  # Colors, strings, themes
│       │   ├── drawable/                # Vector icons & shape drawables
│       │   ├── menu/                    # Toolbar menus
│       │   └── xml/                     # Network security config
│       └── AndroidManifest.xml
├── build-release.sh                      # Standalone build script
├── build.gradle.kts (root)
├── app/build.gradle.kts
├── settings.gradle.kts
├── proguard-rules.pro
└── gradle/
    └── wrapper/
        └── gradle-wrapper.properties    # Gradle 8.5
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 1.9.22 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |
| Build System | Gradle 8.5 + AGP 8.2.2 |
| Rendering | Android WebView (Chromium) |
| UI | Material Components, ConstraintLayout |
| Storage | SharedPreferences, SQLite |
| Minification | R8 / ProGuard |

## Permissions

- `INTERNET` — Network access for browsing
- `ACCESS_NETWORK_STATE` — Check connectivity
- `ACCESS_WIFI_STATE` — Wi-Fi status detection

## License

MIT License — use it, modify it, fork it.

---

Made with Kotlin. Zero AI. Zero bloat.