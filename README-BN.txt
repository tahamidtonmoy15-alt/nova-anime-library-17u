NOVA v17 — Premium UI + Local Anime Reels
==========================================

NOVA v8 — GitHub Pages দিয়ে ফ্রিতে মোবাইলে ইনস্টল
====================================================

গুরুত্বপূর্ণ: ZIP ফাইলটা GitHub-এ আপলোড করবেন না! আগে Extract করুন,
তারপর ভেতরের সব ফাইল (ফোল্ডার নয়) আপলোড করুন। index.html অবশ্যই
রিপোজিটরির সবার উপরের লেভেলে থাকবে।

আপলোড করার ফাইল (সবগুলো):
  index.html, sw.js, manifest.webmanifest, hero.webp, favicon.png,
  apple-touch-icon.png, icon-192.png, icon-512.png, icon-maskable-512.png, .nojekyll

ধাপ ১: github.com > New repository > নাম (যেমন nova) > Public > Create
ধাপ ২: "uploading an existing file" > সব ফাইল ড্র্যাগ > Commit changes
ধাপ ৩: Settings > Pages > Source: Deploy from a branch > main / (root) > Save
ধাপ ৪: ১-২ মিনিট পর লিংক: https://আপনার-নাম.github.io/nova/
ধাপ ৫: ফোনের Chrome-এ লিংক খুলুন > উপরের নীল/সবুজ Install (ডাউনলোড তীর) বাটন চাপুন (বা Storage > Install App)

নতুন আইকন দেখতে (গুরুত্বপূর্ণ):
  ফোনে আগের NOVA অ্যাপ থাকলে সেটা Uninstall করুন, Chrome-এর Site settings থেকে
  Clear & reset করুন, তারপর নতুন করে Install করুন। না হলে পুরনো আইকন থেকে যেতে পারে।

নতুন যা আছে:
  - Watching: Release Day ট্যাব (Daily/Sat/Sun/...)। Sat চাপলে শুধু Saturday-র anime আসবে।
    আজকের দিনের ট্যাবে নীল ডট থাকে।
  - Library: যেকোনো anime-র ছবি বা নামে ট্যাপ করুন > Season/Episode সংখ্যা সেট করুন >
    এপিসোডে ভিডিও যোগ করুন, দেখুন, Watched মার্ক করুন। পরে ভেতর থেকেই Seasons/EP/Day বদলানো যায়।
  - Storage: উপরে ডান কোণার ছোট ডাটাবেস আইকন চাপলে শুধু Storage দেখাবে
    (মোট / ব্যবহৃত / বাকি)।
  - ভিডিও প্লেয়ার: ছবি না এলে কারণ দেখায় (যেমন 10-bit বা HEVC) এবং
    "Open in VLC / MX Player" ও "Save to phone" বাটন আছে।

ভিডিওতে "শুধু শব্দ, ছবি নেই" হলে:
  ফোনের Chrome নিচের ফরম্যাট চালাতে পারে না:
    - 10-bit H.264 (Hi10P) — অনেক anime রিলিজে থাকে
    - H.265 / HEVC (বিশেষ করে 10-bit)
    - AC-3 / DTS অডিও
  সমাধান ১: প্লেয়ারে "Open in VLC / MX Player" চাপুন।
  সমাধান ২: ভিডিওটা MP4 (H.264 8-bit + AAC) করে নিন (HandBrake বা ফোনের Video Converter অ্যাপ)।
  একাধিক অডিও ট্র্যাক (Hindi/Japanese) Chrome-এ বদলানো যায় না; সেক্ষেত্রে VLC / MX Player ব্যবহার করুন।

ভিডিও ফোনের ব্রাউজার স্টোরেজে থাকে; ব্রাউজার ডেটা মুছলে চলে যাবে।
Export (JSON) ব্যাকআপে ভিডিও থাকে না।

Convert & Play (v12):
  প্লেয়ারে "Convert & Play" বাটন চাপলে ভিডিও ফোনেই Chrome-এর চলার মতো MP4 (H.264 8-bit + AAC) হয়ে যায়।
  এটা ffmpeg দিয়ে ফোনেই হয়, কোনো সার্ভার লাগে না। এই ৪টা ফাইল GitHub-এ আপলোড করতেই হবে: ffmpeg.js, 814.ffmpeg.js, ffmpeg-core.js, ffmpeg-core.wasm.gz
  10-bit/HEVC ভিডিওতে সময় বেশি লাগে (লম্বা এপিসোডে ১-৩ ঘণ্টা হতে পারে)। তখন Fast · 480p বেছে নিন।
  একাধিক অডিও থাকলে ভাষা (অডিও ট্র্যাক) বেছে নেওয়া যায়। কনভার্ট চলার সময় স্ক্রিন খোলা রাখুন।


NOVA v17 Local Reels:
- Reels ট্যাব থেকে ভিডিও Upload করে NOVA-এর IndexedDB-তে কপি করা যায়।
- Vertical video feed, category filter, custom categories, caption, hashtags, Like, Save এবং Delete আছে।
- Reels কেবল এই ডিভাইস/অ্যাপেই থাকে; কোনো server বা cloud-এ upload হয় না।
- Reels Delete করলে IndexedDB record ও video Blob মুছে যায়।
- Android app uninstall করলে app-private WebView data-সহ Reels মুছে যায়।
- Reels player WebView/HTML5 codec support ব্যবহার করে; embedded LibVLC এখনো যুক্ত নয়।
