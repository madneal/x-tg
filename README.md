# Unofficial Telegram Client for Android

This repository contains a native Kotlin/Compose Telegram client built on
[TDLib](https://core.telegram.org/tdlib). It is an unofficial client and does
not use Telegram's name or logo as its product branding.

## Local setup

1. Install Android Studio, Android SDK 35, NDK/CMake support, and JDK 17.
2. Create an application at <https://my.telegram.org>.
3. Copy `local.properties.example` to `local.properties` and fill in your
   `telegram.apiId` and `telegram.apiHash` values. `local.properties` is ignored
   by Git.
4. Build with `./gradlew assembleDebug` or open the project in Android Studio.

The app uses the version-pinned `io.github.tdlibx` Android artifact, which
packages the official TDLib native library and generated Java API. The
dependency can be replaced with a locally built TDLib AAR without changing the
application layer.

## Feature coverage

The login flow follows Telegram's phone-first experience: enter a phone number,
enter the code delivered by Telegram or SMS, and enter a two-step verification
password when enabled. Accounts that require an additional email verification
step are handled as a secondary TDLib authorization state.

The first milestone covers authentication, chat lists, text and media
messaging, search, contacts, groups, notifications, and settings. The feature
registry in `app/src/main/java/com/example/tgclient/features/FeatureRegistry.kt`
tracks additional Telegram capabilities as they are implemented.

Multiple Telegram accounts are supported. Use **Settings → Accounts → Add
account** to start a second phone-number login, then switch accounts from the
same list. Each account uses an isolated TDLib database, files directory, and
Android Keystore-wrapped database key, so switching accounts does not mix chat
history or credentials.

TDLib and its generated API are licensed independently of this application.
Review all third-party notices before distributing an APK.
