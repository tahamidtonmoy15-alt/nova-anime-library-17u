package com.nova.anime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;
import java.util.Locale;
import android.content.SharedPreferences;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 4102;
    private static final int PICK_WEB_FILE = 4103;
    private static final String PREFS_NAME = "nova_episode_media";
    private static final String PREF_PREFIX = "episode_uri:";
    private static final String REEL_PREFIX = "reel_uri:";
    private static final String NOVA_URL = "https://tahamidtonmoy15-alt.github.io/nova-anime-library-17u/";
    private static final String LEGACY_NOVA_URL = "https://cdtc1127l-cmyk.github.io/anime-f/";

    private FrameLayout root;
    private WebView webView;
    private ValueCallbackHolder fileCallback;
    private boolean pendingVideoWebPicker = false;
    private volatile Uri lastPickedVideoUri = null;
    private String pendingEpisodeKey = null;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;
    private TextView playerStatus;
    private TextView timeLabel;
    private SeekBar seekBar;
    private Button playPauseButton;
    private final Handler handler = new Handler();
    private boolean userSeeking = false;
    private boolean showingPlayer = false;

    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            if (showingPlayer && mediaPlayer != null && mediaPlayer.isPlaying()) {
                long length = mediaPlayer.getLength();
                long time = mediaPlayer.getTime();
                if (length > 0 && seekBar != null && !userSeeking) {
                    seekBar.setMax((int) Math.min(Integer.MAX_VALUE, length));
                    seekBar.setProgress((int) Math.max(0, Math.min(Integer.MAX_VALUE, time)));
                }
                if (timeLabel != null) timeLabel.setText(formatTime(time) + " / " + formatTime(length));
                if (playPauseButton != null) playPauseButton.setText("Pause");
            } else if (showingPlayer && mediaPlayer != null && playPauseButton != null) {
                playPauseButton.setText("Play");
            }
            if (showingPlayer) handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(8, 3, 17));
        getWindow().setNavigationBarColor(Color.rgb(8, 3, 17));
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildHome();
    }

    private void buildHome() {
        showingPlayer = false;
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 3, 17));
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        TextView header = new TextView(this);
        header.setText("NOVA  ·  ANIME UNIVERSE");
        header.setTextColor(Color.WHITE);
        header.setTextSize(18);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(4), dp(16), dp(4));
        shell.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        // Episode playback starts after async media inspection; allow WebView playback from that flow.
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setBackgroundColor(Color.rgb(8, 3, 17));
        webView.addJavascriptInterface(new NovaBridge(), "NovaAndroid");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.cancel();
                fileCallback = new ValueCallbackHolder(callback);
                pendingVideoWebPicker = isVideoChooser(params);
                Intent intent;
                if (pendingVideoWebPicker) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("video/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                } else {
                    intent = params.createIntent();
                }
                try { startActivityForResult(intent, PICK_WEB_FILE); }
                catch (Exception e) { fileCallback.cancel(); fileCallback = null; Toast.makeText(MainActivity.this, "No file picker available", Toast.LENGTH_SHORT).show(); }
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                if ("tahamidtonmoy15-alt.github.io".equals(host) || "cdtc1127l-cmyk.github.io".equals(host)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) showLoadError();
            }
        });
        shell.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Keep the NOVA web app full-height. Native debug controls previously covered
        // the bottom of the episode UI and duplicated the in-app video player.
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        webView.loadUrl(NOVA_URL);
    }

    private boolean isVideoChooser(WebChromeClient.FileChooserParams params) {
        if (params == null || params.getAcceptTypes() == null) return false;
        for (String type : params.getAcceptTypes()) {
            if (type == null) continue;
            String value = type.toLowerCase(Locale.ROOT).replace(" ", "");
            for (String part : value.split(",")) {
                String mime = part.split(";")[0];
                if (mime.startsWith("video/") || mime.equals(".mkv") || mime.equals(".mp4")
                        || mime.equals(".avi") || mime.equals(".mov") || mime.equals(".webm")) return true;
            }
        }
        return false;
    }

    private SharedPreferences mediaPrefs() { return getSharedPreferences(PREFS_NAME, MODE_PRIVATE); }
    private String episodePrefKey(String key) { return PREF_PREFIX + key; }

    private void showLoadError() {
        if (isFinishing() || webView == null) return;
        // Keep the native reload action visible; offer the previously used NOVA Pages URL as a fallback.
        webView.post(() -> {
            if (webView == null || !webView.getUrl().equals(NOVA_URL)) return;
            new android.app.AlertDialog.Builder(this)
                .setTitle("NOVA could not connect")
                .setMessage("The NOVA website may still be publishing. Try the previous NOVA website address?")
                .setNegativeButton("Retry", (d, w) -> webView.loadUrl(NOVA_URL))
                .setPositiveButton("Try previous site", (d, w) -> webView.loadUrl(LEGACY_NOVA_URL))
                .show();
        });
    }

    private void openVlcPlayer() {
        try {
            ensureVlc();
            buildPlayerUi();
            chooseVideo();
        } catch (Exception e) {
            Toast.makeText(this, "VLC could not start: " + e.getMessage(), Toast.LENGTH_LONG).show();
            buildHome();
        }
    }

    private void ensureVlc() {
        if (libVLC != null && mediaPlayer != null) return;
        ArrayList<String> options = new ArrayList<>();
        options.add("--no-drop-late-frames");
        options.add("--no-skip-frames");
        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);
        mediaPlayer.setEventListener(event -> runOnUiThread(() -> {
            if (playerStatus == null) return;
            switch (event.type) {
                case org.videolan.libvlc.MediaPlayer.Event.Opening: playerStatus.setText("Opening video…"); break;
                case org.videolan.libvlc.MediaPlayer.Event.Buffering: playerStatus.setText("Buffering…"); break;
                case org.videolan.libvlc.MediaPlayer.Event.Playing: playerStatus.setText("Playing"); break;
                case org.videolan.libvlc.MediaPlayer.Event.Paused: playerStatus.setText("Paused"); break;
                case org.videolan.libvlc.MediaPlayer.Event.Stopped: playerStatus.setText("Playback stopped"); break;
                case org.videolan.libvlc.MediaPlayer.Event.EndReached: playerStatus.setText("Episode finished"); break;
                case org.videolan.libvlc.MediaPlayer.Event.EncounteredError:
                    playerStatus.setText("VLC could not decode this file. Try another copy or MP4 (H.264/AAC).");
                    Toast.makeText(MainActivity.this, "Video decoding failed. Try a different copy or MP4 (H.264/AAC).", Toast.LENGTH_LONG).show();
                    break;
                default: break;
            }
        }));
    }

    private void buildPlayerUi() {
        showingPlayer = true;
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(12), dp(8), dp(12), dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = makeButton("Back to NOVA");
        back.setOnClickListener(v -> returnHome());
        TextView title = new TextView(this);
        title.setText("  NOVA  ·  VLC PLAYER");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        top.addView(back, new LinearLayout.LayoutParams(dp(142), dp(48)));
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        shell.addView(top);

        videoLayout = new VLCVideoLayout(this);
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        videoParams.topMargin = dp(8);
        videoParams.bottomMargin = dp(8);
        shell.addView(videoLayout, videoParams);
        mediaPlayer.attachViews(videoLayout, null, false, false);

        playerStatus = new TextView(this);
        playerStatus.setText("Choose a video stored on your phone");
        playerStatus.setTextColor(0xFFD8C9EB);
        playerStatus.setTextSize(13);
        shell.addView(playerStatus, new LinearLayout.LayoutParams(-1, dp(30)));

        timeLabel = new TextView(this);
        timeLabel.setText("00:00 / 00:00");
        timeLabel.setTextColor(Color.WHITE);
        shell.addView(timeLabel, new LinearLayout.LayoutParams(-1, dp(24)));
        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (mediaPlayer != null && mediaPlayer.getLength() > 0) {
                    mediaPlayer.setTime((long) ((bar.getProgress() / (double) Math.max(1, bar.getMax())) * mediaPlayer.getLength()));
                }
                userSeeking = false;
            }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { }
        });
        shell.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        playPauseButton = makeButton("Play");
        playPauseButton.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            if (mediaPlayer.isPlaying()) mediaPlayer.pause(); else mediaPlayer.play();
            handler.removeCallbacks(progressUpdater);
            handler.post(progressUpdater);
        });
        Button stop = makeButton("Stop");
        stop.setOnClickListener(v -> { if (mediaPlayer != null) mediaPlayer.stop(); playerStatus.setText("Playback stopped"); });
        Button choose = makeButton("Choose video");
        choose.setOnClickListener(v -> chooseVideo());
        controls.addView(playPauseButton, new LinearLayout.LayoutParams(0, dp(50), 1f));
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        stopParams.leftMargin = dp(6);
        controls.addView(stop, stopParams);
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(0, dp(50), 1.3f);
        chooseParams.leftMargin = dp(6);
        controls.addView(choose, chooseParams);
        shell.addView(controls);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        handler.removeCallbacks(progressUpdater);
        handler.post(progressUpdater);
    }

    private void chooseVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEO);
    }

    private void openEpisodeInVlc(String key) {
        String prefKey = episodePrefKey(key);
        String saved = mediaPrefs().getString(prefKey, null);
        try {
            ensureVlc();
            buildPlayerUi();
            if (saved != null) {
                File storedFile = new File(saved);
                if (storedFile.isFile()) {
                    playNativeUri(Uri.fromFile(storedFile));
                    return;
                }
                // Migrate old installs which saved content:// document links instead of a
                // private NOVA copy. The copy is made off the UI thread before VLC opens it.
                if (saved.startsWith("content://")) {
                    playNativeUri(Uri.parse(saved), prefKey);
                    return;
                }
                mediaPrefs().edit().remove(prefKey).apply();
            }
            playerStatus.setText("Select the episode video once. NOVA will save a private copy for reliable playback.");
            pendingEpisodeKey = key;
            chooseVideo();
        } catch (Exception e) {
            Toast.makeText(this, "NOVA player could not start: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (showingPlayer) returnHome();
        }
    }

    private void openReelInVlc(String id) {
        String saved = mediaPrefs().getString(REEL_PREFIX + id, null);
        if (saved == null || !new File(saved).isFile()) {
            Toast.makeText(this, "This Reel has no NOVA video copy. Delete and upload it again.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ensureVlc();
            buildPlayerUi();
            playNativeUri(Uri.fromFile(new File(saved)));
        } catch (Exception e) {
            Toast.makeText(this, "NOVA player could not start: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (showingPlayer) returnHome();
        }
    }

    private String displayName(Uri uri) {
        String name = "Selected video";
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0 && cursor.getString(col) != null) name = cursor.getString(col);
            }
        } catch (Exception ignored) { }
        if ("Selected video".equals(name) && uri.getLastPathSegment() != null) name = uri.getLastPathSegment();
        return name;
    }

    private File copyUriToPrivateStorage(Uri uri) throws Exception {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            File sourceFile = new File(uri.getPath());
            String privateRoot = getFilesDir().getCanonicalPath() + File.separator;
            if (sourceFile.isFile() && sourceFile.getCanonicalPath().startsWith(privateRoot)) return sourceFile;
        }

        String originalName = displayName(uri);
        String safeName = originalName.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (safeName.isEmpty()) safeName = "video";
        if (safeName.length() > 100) safeName = safeName.substring(safeName.length() - 100);
        File folder = new File(getFilesDir(), "nova_media");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create NOVA video storage");
        File destination = new File(folder, UUID.randomUUID() + "_" + safeName);
        InputStream raw = "file".equalsIgnoreCase(uri.getScheme())
                ? new FileInputStream(new File(uri.getPath()))
                : getContentResolver().openInputStream(uri);
        if (raw == null) throw new IllegalStateException("Android could not read the selected video");
        boolean success = false;
        try (InputStream input = new BufferedInputStream(raw);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            success = destination.isFile() && destination.length() > 0;
        } finally {
            if (!success) destination.delete();
        }
        if (!success) throw new IllegalStateException("The copied video is empty or incomplete");
        return destination;
    }

    private boolean isPrivateVideoFile(Uri uri) {
        if (!"file".equalsIgnoreCase(uri.getScheme())) return false;
        try {
            File file = new File(uri.getPath());
            String root = getFilesDir().getCanonicalPath() + File.separator;
            return file.isFile() && file.getCanonicalPath().startsWith(root);
        } catch (Exception ignored) { return false; }
    }

    private void playNativeUri(Uri uri) {
        playNativeUri(uri, null);
    }

    private void playNativeUri(Uri uri, String preferenceKey) {
        try {
            ensureVlc();
            if (!showingPlayer) buildPlayerUi();
            String name = displayName(uri);
            if (isPrivateVideoFile(uri)) {
                beginNativePlayback(uri, name);
                return;
            }

            if (playerStatus != null) playerStatus.setText("Saving a private copy in NOVA…");
            new Thread(() -> {
                try {
                    File localCopy = copyUriToPrivateStorage(uri);
                    if (preferenceKey != null) {
                        mediaPrefs().edit().putString(preferenceKey, localCopy.getAbsolutePath()).apply();
                    }
                    runOnUiThread(() -> beginNativePlayback(Uri.fromFile(localCopy), name));
                } catch (Exception error) {
                    android.util.Log.e("NOVA", "Could not import selected video " + uri, error);
                    runOnUiThread(() -> {
                        if (playerStatus != null) playerStatus.setText("Could not copy this video: " + error.getMessage());
                        Toast.makeText(MainActivity.this, "NOVA could not copy this file. Check free storage and file access.", Toast.LENGTH_LONG).show();
                    });
                }
            }, "NOVA-video-import").start();
        } catch (Exception error) {
            if (playerStatus != null) playerStatus.setText("Could not open video: " + error.getMessage());
            android.util.Log.e("NOVA", "LibVLC failed to open " + uri, error);
            Toast.makeText(this, "NOVA could not open this video. Try selecting it again.", Toast.LENGTH_LONG).show();
        }
    }

    private void beginNativePlayback(Uri localUri, String name) {
        try {
            if (mediaPlayer == null) ensureVlc();
            Media media = new Media(libVLC, localUri);
            media.addOption(":file-caching=1000");
            media.addOption(":network-caching=1500");
            media.addOption(":no-video-title-show");
            mediaPlayer.stop();
            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();
            if (playerStatus != null) playerStatus.setText("NOVA Player · " + name);
            handler.removeCallbacks(progressUpdater);
            handler.post(progressUpdater);
        } catch (Exception error) {
            if (playerStatus != null) playerStatus.setText("Could not start playback: " + error.getMessage());
            android.util.Log.e("NOVA", "LibVLC playback start failed for " + localUri, error);
            Toast.makeText(this, "NOVA could not start this video. The file may be damaged or use an unsupported codec.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean registerPickedMedia(String preferenceKey) {
        Uri picked = lastPickedVideoUri;
        if (picked == null) return false;
        lastPickedVideoUri = null;
        try {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Copying video into NOVA storage…", Toast.LENGTH_SHORT).show());
            File localCopy = copyUriToPrivateStorage(picked);
            String old = mediaPrefs().getString(preferenceKey, null);
            mediaPrefs().edit().putString(preferenceKey, localCopy.getAbsolutePath()).apply();
            if (old != null && !old.equals(localCopy.getAbsolutePath())) {
                File oldFile = new File(old);
                String root = getFilesDir().getCanonicalPath() + File.separator;
                if (oldFile.isFile() && oldFile.getCanonicalPath().startsWith(root)) oldFile.delete();
            }
            return true;
        } catch (Exception error) {
            android.util.Log.e("NOVA", "Could not save selected video for " + preferenceKey, error);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Video copy failed. Check phone storage and file permission.", Toast.LENGTH_LONG).show());
            return false;
        }
    }

    private void forgetStoredMedia(String preferenceKey) {
        String saved = mediaPrefs().getString(preferenceKey, null);
        mediaPrefs().edit().remove(preferenceKey).apply();
        if (saved == null) return;
        try {
            File file = new File(saved);
            String root = getFilesDir().getCanonicalPath() + File.separator;
            if (file.isFile() && file.getCanonicalPath().startsWith(root)) file.delete();
        } catch (Exception error) {
            android.util.Log.w("NOVA", "Could not delete stored video copy", error);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_WEB_FILE) {
            if (fileCallback != null) {
                Uri[] result = (resultCode == RESULT_OK && data != null && data.getData() != null) ? new Uri[]{data.getData()} : null;
                boolean wasVideoPicker = pendingVideoWebPicker;
                if (result != null && result.length > 0 && wasVideoPicker) {
                    Uri selected = result[0];
                    // Keep the URI selected by Android's document picker; the JS upload callback
                    // immediately registers it against the exact anime/season/episode key.
                    lastPickedVideoUri = selected;
                    try {
                        getContentResolver().takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception permissionError) {
                        android.util.Log.w("NOVA", "Persistable video permission unavailable", permissionError);
                    }
                }
                pendingVideoWebPicker = false;
                fileCallback.complete(result);
                fileCallback = null;
            }
            return;
        }
        if (requestCode != PICK_VIDEO) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingEpisodeKey = null;
            Toast.makeText(this, "No video selected. Choose the episode file to start playback.", Toast.LENGTH_LONG).show();
            returnHome();
            return;
        }
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        String episodeKey = pendingEpisodeKey;
        pendingEpisodeKey = null;
        playNativeUri(uri, episodeKey == null ? null : episodePrefKey(episodeKey));
    }

    private void returnHome() {
        handler.removeCallbacks(progressUpdater);
        if (mediaPlayer != null) mediaPlayer.pause();
        if (mediaPlayer != null) { mediaPlayer.detachViews(); }
        videoLayout = null;
        if (webView != null) { webView.removeJavascriptInterface("NovaAndroid"); webView.destroy(); webView = null; }
        buildHome();
    }

    @Override public void onBackPressed() {
        if (showingPlayer) { returnHome(); return; }
        if (webView != null && webView.canGoBack()) { webView.goBack(); return; }
        super.onBackPressed();
    }

    @Override protected void onPause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(progressUpdater);
        if (fileCallback != null) { fileCallback.cancel(); fileCallback = null; }
        if (webView != null) { webView.removeJavascriptInterface("NovaAndroid"); webView.destroy(); webView = null; }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.detachViews();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (libVLC != null) { libVLC.release(); libVLC = null; }
        super.onDestroy();
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        return button;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    private class NovaBridge {
        @JavascriptInterface public boolean registerEpisodeVideo(String key) {
            if (key == null || key.trim().isEmpty()) return false;
            return registerPickedMedia(episodePrefKey(key));
        }
        @JavascriptInterface public boolean playEpisodeVideo(String key) {
            if (key == null || key.trim().isEmpty()) return false;
            runOnUiThread(() -> openEpisodeInVlc(key));
            return true;
        }
        @JavascriptInterface public void forgetEpisodeVideo(String key) {
            if (key == null || key.trim().isEmpty()) return;
            forgetStoredMedia(episodePrefKey(key));
        }
        @JavascriptInterface public boolean registerReelVideo(String id) {
            if (id == null || id.trim().isEmpty()) return false;
            return registerPickedMedia(REEL_PREFIX + id);
        }
        @JavascriptInterface public boolean playReelVideo(String id) {
            if (id == null || id.trim().isEmpty()) return false;
            runOnUiThread(() -> openReelInVlc(id));
            return true;
        }
        @JavascriptInterface public void forgetReelVideo(String id) {
            if (id == null || id.trim().isEmpty()) return;
            forgetStoredMedia(REEL_PREFIX + id);
        }
    }

    private static class ValueCallbackHolder {
        private final android.webkit.ValueCallback<Uri[]> callback;
        ValueCallbackHolder(android.webkit.ValueCallback<Uri[]> callback) { this.callback = callback; }
        void complete(Uri[] value) { callback.onReceiveValue(value); }
        void cancel() { callback.onReceiveValue(null); }
    }
}
