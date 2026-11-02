# NOVA Android player (LibVLC)

This folder is a native Android player module, separate from the GitHub Pages PWA. It embeds the official VideoLAN LibVLC Android library (3.7.1) and plays a video selected through Android's Storage Access Framework.

## Build

Open this `android/` folder in Android Studio (JDK 17, Android SDK 35, Gradle 8.11+), or run:

```bash
gradle assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Important current scope

- This is the first native LibVLC player foundation, not yet a full native port of the complete NOVA website/PWA.
- The current web Reels collection remains local to the browser/PWA. The native player currently opens videos selected from the phone.
- Codec support depends on the media stream and device hardware; no player can promise every file will play.
- Before distributing an app containing LibVLC, review VideoLAN's licensing requirements and the app's distribution obligations.
