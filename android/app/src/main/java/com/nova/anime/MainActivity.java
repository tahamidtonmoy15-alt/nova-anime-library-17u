package com.nova.anime;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 4102;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildPlayerUi();
        try {
            ArrayList<String> options = new ArrayList<>();
            options.add("--no-drop-late-frames");
            options.add("--no-skip-frames");
            libVLC = new LibVLC(this, options);
            mediaPlayer = new MediaPlayer(libVLC);
            mediaPlayer.attachViews(videoLayout, null, false, false);
            status.setText("NOVA VLC Player ready · Choose a video");
        } catch (Exception error) {
            status.setText("Could not initialize the video engine: " + error.getMessage());
        }
    }

    private void buildPlayerUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF080311);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(18, 26, 18, 18);
        TextView title = new TextView(this);
        title.setText("NOVA  ·  VIDEO PLAYER");
        title.setTextColor(0xFFF7EFFF);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(title, new LinearLayout.LayoutParams(-1, 54));
        videoLayout = new VLCVideoLayout(this);
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        videoParams.topMargin = 12;
        videoParams.bottomMargin = 12;
        column.addView(videoLayout, videoParams);
        status = new TextView(this);
        status.setText("Starting LibVLC…");
        status.setTextColor(0xFFBFAED8);
        status.setTextSize(13);
        column.addView(status, new LinearLayout.LayoutParams(-1, 44));
        Button choose = new Button(this);
        choose.setText("Choose video from phone");
        choose.setOnClickListener(v -> chooseVideo());
        column.addView(choose, new LinearLayout.LayoutParams(-1, 54));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private void chooseVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_VIDEO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        try {
            Media media = new Media(libVLC, uri);
            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();
            status.setText("Playing with the built-in LibVLC engine");
        } catch (Exception error) {
            status.setText("Could not play this file: " + error.getMessage());
        }
    }

    @Override protected void onStop() {
        if (mediaPlayer != null) mediaPlayer.pause();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.detachViews();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
        super.onDestroy();
    }
}
