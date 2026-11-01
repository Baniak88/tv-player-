package pl.tvplayer;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.content.pm.ActivityInfo;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView view;
    private TextView message;
    private String url;
    private long position;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        url = getIntent().getStringExtra("url");
        if (url == null) { finish(); return; }
        if (state != null) position = state.getLong("position");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.BLACK);
        view = new PlayerView(this); view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(view, new android.widget.FrameLayout.LayoutParams(-1, -1));
        LinearLayout top = new LinearLayout(this); top.setPadding(16, 28, 16, 0); top.setBackgroundColor(0x66000000);
        Button back = new Button(this); back.setText("‹ Wróć"); back.setOnClickListener(v -> finish()); top.addView(back);
        TextView title = new TextView(this); title.setText(getIntent().getStringExtra("name")); title.setTextColor(-1); title.setTextSize(16); title.setGravity(Gravity.CENTER_VERTICAL); top.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        Button rotate = new Button(this); rotate.setText("↻"); rotate.setContentDescription("Zmień orientację ekranu"); rotate.setOnClickListener(v -> setRequestedOrientation(getResources().getConfiguration().orientation == 2 ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)); top.addView(rotate);
        root.addView(top, new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        LinearLayout error = new LinearLayout(this); error.setOrientation(LinearLayout.VERTICAL); error.setGravity(Gravity.CENTER); error.setBackgroundColor(0xDD0D1421); error.setPadding(24, 20, 24, 20);
        message = new TextView(this); message.setTextColor(-1); message.setTextSize(17); error.addView(message);
        Button retry = new Button(this); retry.setText("Spróbuj ponownie"); error.addView(retry); retry.setOnClickListener(v -> { error.setVisibility(View.GONE); player.prepare(); player.play(); });
        error.setVisibility(View.GONE); root.addView(error, new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        view.setTag(error); setContentView(root);
    }
    @Override protected void onStart() {
        super.onStart(); if (url == null) return;
        player = new ExoPlayer.Builder(this).build(); view.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException e) {
                message.setText("Nie można odtworzyć kanału. Sprawdź internet, dostępność źródła lub obsługę formatu na telefonie.");
                ((android.view.View) view.getTag()).setVisibility(View.VISIBLE);
            }
        });
        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        String path = android.net.Uri.parse(url).getPath();
        if (path != null && path.endsWith(".ts")) item.setMimeType(MimeTypes.VIDEO_MP2T);
        player.setMediaItem(item.build()); player.seekTo(position); player.prepare(); player.play();
    }
    @Override protected void onStop() {
        if (player != null) { position = player.isCurrentMediaItemLive() ? 0 : player.getCurrentPosition(); view.setPlayer(null); player.release(); player = null; }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putLong("position", player == null ? position : player.getCurrentPosition()); super.onSaveInstanceState(out); }
}
