# NOVA v1.4.0 — সম্পূর্ণ Source Code

এই প্যাকেজে NOVA Anime Universe-এর ওয়েব অ্যাপ, Android Native Kotlin অ্যাপ, ভিডিও প্লেয়ার এবং GitHub Actions workflow-এর সোর্স রাখা হয়েছে।

## কোথায় কী আছে

- `index.html` — NOVA-এর মূল ওয়েব ইন্টারফেস, Anime Library, Episode Tracker, Upload এবং Reels।
- `sw.js`, `manifest.webmanifest` — PWA/offline app configuration।
- `hero.webp`, `icon-*.png`, `favicon.png` — UI ও অ্যাপের visual assets।
- `ffmpeg.js`, `814.ffmpeg.js`, `ffmpeg-core.js`, `ffmpeg-core.wasm.gz` — ওয়েব অ্যাপের video conversion support files।
- `android/app/src/main/java/com/nova/anime/MainActivity.kt` — Android WebView host ও JavaScript-to-native bridge।
- `android/app/src/main/java/com/nova/anime/NativeVideoStore.kt` — নির্বাচিত Episode/Reel ভিডিও NOVA-র private storage-এ কপি, খোঁজা, পুরোনো Episode link migration এবং delete।
- `android/app/src/main/java/com/nova/anime/PlayerActivity.kt` — Native playback UI; LibVLC, software decoder retry এবং Media3 fallback।
- `android/app/src/main/AndroidManifest.xml` — Android application ও PlayerActivity registration।
- `android/*.gradle.kts` — Kotlin/Android build configuration ও dependencies।
- `.github/workflows/` — APK build এবং source package workflow।

## Android Studio-তে খোলা

1. ZIP Extract করো।
2. Android Studio থেকে Extract করা folder-এর ভিতরের `android` folder Open করো।
3. Gradle Sync শেষ হতে দাও।
4. Run চাপলে ফোনে NOVA install হবে; অথবা Gradle task `app:assembleDebug` চালিয়ে APK তৈরি করা যায়।

## Build configuration

- Package: `com.nova.anime`
- Version: `1.4.0` (versionCode 5)
- Minimum Android: API 23
- Compile SDK: 35
- Java/Kotlin target: 17
- LibVLC Android: 3.7.1
- AndroidX Media3: 1.5.1
- Gradle: 8.11.1 (GitHub Actions workflow)

## ভিডিও playback সম্পর্কে গুরুত্বপূর্ণ কথা

NOVA নির্বাচিত ভিডিও নিজের app-private storage-এ কপি করে Native player-এ পাঠায়। LibVLC বিস্তৃত container/codec সমর্থন দেয়; decoding সমস্যা হলে software-decoder retry এবং Media3 fallback চেষ্টা করা হয়। তবে ক্ষতিগ্রস্ত ফাইল বা প্রতিটি proprietary/অস্বাভাবিক codec শতভাগ চালানোর নিশ্চয়তা কোনো সাধারণ player দিতে পারে না। ফোনে আলাদা করে পরীক্ষা করতে হবে।

## গুরুত্বপূর্ণ

ভিডিওগুলো Android app-এর private storage-এ কপি হয়। অ্যাপ uninstall করলে Android app data-সহ ওই কপিগুলো মুছে যেতে পারে। প্রয়োজনীয় ভিডিও আগে ফোনের অন্য জায়গায় Export/Save করে রাখবে।
