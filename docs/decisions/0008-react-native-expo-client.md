# ADR-0008: React Native + Expo app (web build for iPhone, APK for Android)

- **Status:** Accepted (implementation: Phase 4)
- **Date:** 2026-11-16
- **Supersedes:** the original "PWA (React + Vite + vite-plugin-pwa)" plan for Phase 4

## Context

- The owner uses an **iPhone**. iOS lets no app (native or otherwise) read SMS or other apps' notifications. Installing a native iOS app outside the App Store needs the Apple Developer Program (about $99/year).
- Friends mostly use **Android**, where an app *can* read SMS and notifications (GPay, PhonePe, bank apps), which makes capture much better.
- Distribution is a sideloaded **APK**, not the Play Store, for now.
- The owner already knows React + TypeScript; mobile is a detour from the platform-engineering goal, so it should cost as little time as possible.

## Options

| Option | Verdict |
|---|---|
| **React Native + Expo (TypeScript)** ✅ | One codebase → **web build** (the iPhone, free) **and** Android APK; reuses React skills; native capture via a small Kotlin module |
| PWA only (original plan) | Works on iPhone, but can't read SMS or notifications on Android |
| Flutter | New language (Dart) and UI framework; more time away from core goals |
| Kotlin native | Best Android integration, but no iPhone at all |
| Swift native | Paid developer account to install; *still* can't read SMS |

## Decision

React Native with Expo. Rollout order:
1. **Web build first**, installed on the iPhone home screen. iPhone capture stays: Shortcut + email + statements.
2. **Android APK second**, with a Kotlin capture module: an SMS receiver plus a notification listener restricted to an **allowlist of financial apps**, filtering on the device, with an offline queue and retries.

## Consequences

- ➕ One UI codebase for both platforms; minimal new tech to learn.
- ➖ The Android capture module is native code; community libraries vary in quality, so we write our own small module.
- ➖ Sideloading: we must keep the APK signing key safe (losing it breaks updates). Play Protect may warn about an app with SMS permission. Google's developer-verification requirement for sideloaded apps (announced 2025, rolling out from 2026) should be checked for India before relying on it.
- ➖ Android OEM battery optimizers (Xiaomi, Oppo, Vivo, Samsung) can kill background capture, so users must exempt the app. The Phase 8 "no transactions in 3 days" alert covers silent failures.
