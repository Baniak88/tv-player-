package pl.tvplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Small poster/logo loader: memory LRU, OkHttp disk cache, down-sampling and view recycling safety. */
final class ImageLoader {
    private static ImageLoader instance;
    private final OkHttpClient client;
    private final LruCache<String, Bitmap> memory;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());

    static synchronized ImageLoader get(Context context) {
        if (instance == null) instance = new ImageLoader(context.getApplicationContext());
        return instance;
    }

    private ImageLoader(Context context) {
        client = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .cache(new Cache(new File(context.getCacheDir(), "images"), 80L * 1024 * 1024)).build();
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        memory = new LruCache<String, Bitmap>(kb) { @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount() / 1024; } };
    }

    /** Loads url into view, sized for width×height pixels. A null/blank url clears the view. */
    void load(String url, ImageView view, int width, int height) {
        view.setTag(R_TAG, url);
        if (url == null || url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) { view.setImageDrawable(null); return; }
        String key = url + "@" + width;
        Bitmap cached = memory.get(key);
        if (cached != null) { view.setImageBitmap(cached); return; }
        view.setImageDrawable(null);
        pool.execute(() -> {
            Bitmap bitmap = fetch(url, width, height);
            if (bitmap != null) memory.put(key, bitmap);
            main.post(() -> {
                if (url.equals(view.getTag(R_TAG)) && bitmap != null) {
                    view.setAlpha(0f); view.setImageBitmap(bitmap); view.animate().alpha(1f).setDuration(150).start();
                }
            });
        });
    }

    private Bitmap fetch(String url, int width, int height) {
        try (Response response = client.newCall(new Request.Builder().url(url).header("User-Agent", "TVPlayer/0.8").build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            byte[] bytes = response.body().bytes();
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample; options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception | OutOfMemoryError e) { return null; }
    }

    private static final int R_TAG = 0x7f0a0fff;
}
