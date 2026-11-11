# Android application

This directory contains the native Android client. It is a Kotlin/Jetpack Compose application and does not load the Streamlit website.

## Current status

The first navigation/home-screen scaffold is in place. Search, local persistence, prompt studio, document handling, and report exports are being migrated in phases. The current screen is a UI scaffold, not a feature-complete release.

## Build

The CI workflow installs JDK 17, Android SDK platform 37/build tools 36.0.0, Gradle 9.6.0, then runs:

```bash
gradle assembleDebug --no-daemon
```

The debug APK is published as a short-lived GitHub Actions artifact after a successful build.

## Package

- Application ID: `ai.researchmate.mobile`
- Minimum Android: API 26
- Compile SDK: API 37
- UI: Jetpack Compose + Material 3
