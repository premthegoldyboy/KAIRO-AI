# KAIRO — Sovereign Intelligence for Android

KAIRO is a modular Android AI-assistant project. This repository contains the first **v0.1 foundation**.

## Current milestone

- Futuristic dark AMOLED-style Compose interface
- Chat screen with a clearly labelled local demo response engine
- User-initiated speech recognition (microphone permission requested when needed)
- Android Text-to-Speech responses using the device's installed voices
- Provider interface that can later host cloud and offline AI engines
- GitHub Actions workflow to build and publish a debug APK artifact

## Important status notes

This is an early development build, not a finished autonomous assistant. The current response engine is a local demo; it does **not** connect to a real cloud model yet. Voice input depends on an installed Android speech-recognition service, and available TTS voices depend on the device. The app does not run an always-listening wake-word service.

## Build

The GitHub Actions workflow runs on pushes to `main` and can also be started manually:

1. Open the repository's **Actions** tab.
2. Select **Build KAIRO Android APK**.
3. Open the latest successful run.
4. Download the `KAIRO-debug-apk` artifact.

The project uses JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.20 and Android API 36.

## Project structure

- `app/` — Android application
- `AIEngine.kt` — provider abstraction and local demo engine
- `.github/workflows/android.yml` — automated debug APK build

## Privacy and permissions

Microphone access is requested only when the user taps the voice-input button. No API key is included in this repository. Add provider credentials only through an appropriate secure configuration in a future integration; never commit secrets.

## Roadmap

1. v0.1 — app shell, demo chat, user-initiated voice input and TTS
2. v0.2 — secure cloud AI provider integration and Telugu/English response handling
3. v0.3 — persistent conversation memory and settings
4. Later — optional offline model support, permission-controlled skills and carefully scoped Android integrations

KAIRO is intended to be free/open-source-first. Third-party AI services and device capabilities may have their own limits or costs.
