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

public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 4102;
    private static final int PICK_WEB_FILE = 4103;
    private static final String PREFS_NAME = "nova_episode_media";
    private static final String PREF_PREFIX = "episode_uri:";
    private static final String NOVA_URL = "https://tahamidtonmoy15-alt.github.io/nova-anime-library-17u/";
    private static final String LEGACY_NOVA_URL = "https://cdtc1127l-cmyk.github.io/anime-f/";

    private FrameLayout root;
    private WebView webView;
    private ValueCallbackHolder fileCallback;
    private boolean pendingVideoWebPicker = false;
    private Uri lastPickedVideoUri = null;
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
            String value = type.toLowerCase(Locale.ROOT);
            if (value.contains("video") || value.contains(".mkv") || value.contains(".mp4") || value.contains(".avi") || value.contains(".mov")) return true;
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
                case org.videolan.libvlc.MediaPlayer.Event.EncounteredError: playerStatus.setText("This file could not be decoded by VLC. Try another copy or format."); break;
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
        String saved = mediaPrefs().getString(episodePrefKey(key), null);
        try {
            ensureVlc();
            buildPlayerUi();
            if (saved != null) {
                playNativeUri(Uri.parse(saved));
            } else {
                playerStatus.setText("Link this saved episode to its original phone file once.");
                pendingEpisodeKey = key;
                chooseVideo();
            }
        } catch (Exception e) {
            Toast.makeText(this, "NOVA player could not start: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (showingPlayer) returnHome();
        }
    }

    private void playNativeUri(Uri uri) {
        try {
            ensureVlc();
            if (!showingPlayer) buildPlayerUi();
            Media media = new Media(libVLC, uri);
            media.addOption(":network-caching=1200");
            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();
            String name = "Selected video";
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (col >= 0) name = cursor.getString(col);
                }
            } catch (Exception ignored) { }
            if (playerStatus != null) playerStatus.setText("Playing in NOVA: " + name);
            handler.removeCallbacks(progressUpdater);
            handler.post(progressUpdater);
        } catch (Exception error) {
            if (playerStatus != null) playerStatus.setText("Could not play this file: " + error.getMessage());
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
                    lastPickedVideoUri = selected;
                    try { getContentResolver().takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
                }
                pendingVideoWebPicker = false;
                fileCallback.complete(result);
                fileCallback = null;
            }
            return;
        }
        if (requestCode != PICK_VIDEO) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (playerStatus != null) playerStatus.setText("No video selected. Return to NOVA and try again.");
            pendingEpisodeKey = null;
            return;
        }
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        if (pendingEpisodeKey != null) {
            mediaPrefs().edit().putString(episodePrefKey(pendingEpisodeKey), uri.toString()).apply();
            pendingEpisodeKey = null;
        }
        playNativeUri(uri);
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
        @JavascriptInterface public void registerEpisodeVideo(String key) {
            if (key == null || key.trim().isEmpty()) return;
            Uri picked = lastPickedVideoUri;
            if (picked == null) return;
            mediaPrefs().edit().putString(episodePrefKey(key), picked.toString()).apply();
            lastPickedVideoUri = null;
        }
        @JavascriptInterface public boolean playEpisodeVideo(String key) {
            if (key == null || key.trim().isEmpty()) return false;
            runOnUiThread(() -> openEpisodeInVlc(key));
            return true;
        }
        @JavascriptInterface public void forgetEpisodeVideo(String key) {
            if (key == null || key.trim().isEmpty()) return;
            mediaPrefs().edit().remove(episodePrefKey(key)).apply();
        }
    }

    private static class ValueCallbackHolder {
        private final android.webkit.ValueCallback<Uri[]> callback;
        ValueCallbackHolder(android.webkit.ValueCallback<Uri[]> callback) { this.callback = callback; }
        void complete(Uri[] value) { callback.onReceiveValue(value); }
        void cancel() { callback.onReceiveValue(null); }
    }
}
