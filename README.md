# JulesLink

A clean, native Android home for [Jules](https://jules.google.com/) — Google’s autonomous coding agent.

No official Jules app? This gives you a dedicated launcher icon, a polished dark UI, and a WebView tuned so **Google sign-in actually works**.

<p align="center">
  <a href="https://github.com/ElcanoTek/juleslink/releases/latest">
    <img alt="Download APK" src="https://img.shields.io/badge/Download-APK-5CFFB0?style=for-the-badge&logo=android&logoColor=0B0F14" />
  </a>
</p>

## Features

- **One tap → Jules** — opens `https://jules.google.com/` immediately
- **Google login that works** — third-party cookies, Chrome-like user agent (no `; wv)`), OAuth popups handled in-app
- **Session persistence** — stay signed in across launches
- **Pull to refresh**, back navigation, share, open in browser
- **Sign out** — clear cookies/cache from the overflow menu
- **Deep links** — `https://jules.google.com/...` opens in the app
- **Material dark UI** with a mint/cyan Jules-inspired look
- **CI builds** — every push to `main` publishes a signed APK and **keeps only the latest release**

## Install

1. Open the **[latest release](https://github.com/ElcanoTek/juleslink/releases/latest)**
2. Download `JulesLink-v*.apk`
3. On your phone: allow install from the browser if prompted → install → open **Jules**
4. Sign in with the Google account you use for Jules

Minimum Android **7.0** (API 24).

## Build locally

```bash
./gradlew assembleRelease
# APK → app/build/outputs/apk/release/
```

Requirements: JDK 17+, Android SDK (compileSdk 35).

## Project layout

```
app/src/main/java/com/ElcanoTek/juleslink/
  MainActivity.kt          # WebView shell, login, chrome, menus
.github/workflows/
  build-release.yml        # Build APK → delete old releases → publish latest
keystore/
  juleslink-release.jks    # Release signing (personal sideload keystore)
```

## Notes

- This is a **convenience wrapper**, not affiliated with Google.
- Sideload only — not on the Play Store.
- The release keystore is committed for automatic GitHub Actions signing of this personal app. Rotate it if you fork for wider distribution.

## License

MIT — see [LICENSE](LICENSE).
