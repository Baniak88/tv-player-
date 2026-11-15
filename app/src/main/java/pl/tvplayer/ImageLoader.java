package pl.tvplayer;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;

/** Coalesced, bounded artwork loading with cancellation when recycled views no longer need a request. */
final class ImageLoader {
    private static ImageLoader instance;
    private final OkHttpClient client;
    private final LruCache<String,Bitmap> memory;
    private final ThreadPoolExecutor pool=new ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(96));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,Work> pending=new HashMap<>();
    private final Map<String,Long> failures=new LinkedHashMap<>();
    private final Object lock=new Object();
    private static final int R_TAG=0x7f0a0fff,R_BINDING=0x7f0a0ffe,R_LISTENER=0x7f0a0ffd;
    private record Binding(String url,int width,int height) {}
    static synchronized ImageLoader get(Context context) { if(instance==null) instance=new ImageLoader(context.getApplicationContext()); return instance; }
    private ImageLoader(Context context) {
        client=new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).cache(new Cache(new File(context.getCacheDir(),"images"),80L*1024*1024)).build();
        int kb=(int)Math.max(8192,Math.min(32768,Runtime.getRuntime().maxMemory()/1024/8)); memory=new LruCache<String,Bitmap>(kb) { @Override protected int sizeOf(String key,Bitmap bitmap) { return Math.max(1,bitmap.getByteCount()/1024); } };
    }
    private static int bucket(int pixels) { return Math.max(32,((Math.max(1,pixels)+31)/32)*32); }
    void load(String url,ImageView view,int width,int height) {
        boolean valid=url!=null && (url.startsWith("http://")||url.startsWith("https://")); boolean fit=view.getScaleType()==ImageView.ScaleType.FIT_CENTER || view.getScaleType()==ImageView.ScaleType.CENTER_INSIDE;
        int w=bucket(width),h=bucket(height); String key=valid?url+"@"+w+"x"+h+(fit?"/fit":"/crop"):""; Object previous=view.getTag(R_TAG);
        if(previous!=null && !previous.equals(key)) release(view,previous);
        view.setTag(R_BINDING,new Binding(url,width,height));
        if(view.getTag(R_LISTENER)==null) {
            view.setTag(R_LISTENER,Boolean.TRUE);
            view.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(android.view.View attached) { Binding binding=(Binding)attached.getTag(R_BINDING); if(binding!=null && ((ImageView)attached).getDrawable()==null) load(binding.url(),(ImageView)attached,binding.width(),binding.height()); }
                @Override public void onViewDetachedFromWindow(android.view.View detached) { release((ImageView)detached,detached.getTag(R_TAG)); }
            });
        }
        view.setTag(R_TAG,key); view.animate().cancel(); view.setAlpha(1f);
        if(!valid) { view.setImageDrawable(null); return; }
        Bitmap cached=memory.get(key); if(cached!=null) { view.setImageBitmap(cached); return; }
        if(!key.equals(previous)) view.setImageDrawable(null);
        synchronized(lock) {
            Long retry=failures.get(key); if(retry!=null && retry>SystemClock.elapsedRealtime()) return;
            Work work=pending.get(key); if(work!=null) { work.add(view); return; }
            work=new Work(url,key,w,h,fit); work.add(view); pending.put(key,work);
            try { pool.execute(work); } catch(RejectedExecutionException busy) {
                // Drop stale queued artwork first; visible rows never wait behind an unbounded backlog.
                Runnable dropped=pool.getQueue().poll(); if(dropped instanceof Work old) { old.cancelled=true; pending.remove(old.key,old); }
                try { pool.execute(work); } catch(RejectedExecutionException ignored) { pending.remove(key,work); }
            }
        }
    }
    private void release(ImageView view,Object key) {
        if(key==null) return; synchronized(lock) { Work old=pending.get(key); if(old!=null) { old.remove(view); if(old.targets.isEmpty()) { pending.remove(key); old.cancelled=true; if(old.call!=null) old.call.cancel(); pool.remove(old); } } }
    }
    private final class Work implements Runnable {
        final String url,key; final int width,height; final boolean fit;
        final List<WeakReference<ImageView>> targets=new ArrayList<>(); volatile boolean cancelled; volatile Call call;
        Work(String url,String key,int width,int height,boolean fit) { this.url=url; this.key=key; this.width=width; this.height=height; this.fit=fit; }
        void add(ImageView view) { for(WeakReference<ImageView> ref:targets) if(ref.get()==view) return; targets.add(new WeakReference<>(view)); }
        void remove(ImageView view) { targets.removeIf(ref -> ref.get()==null || ref.get()==view); }
        @Override public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            synchronized(lock) { targets.removeIf(ref -> ref.get()==null); if(cancelled || targets.isEmpty()) { pending.remove(key,this); return; } }
            Bitmap bitmap=fetch(); if(bitmap!=null && !cancelled) memory.put(key,bitmap);
            main.post(() -> {
                List<WeakReference<ImageView>> waiting;
                synchronized(lock) { if(pending.get(key)!=this) return; pending.remove(key); waiting=new ArrayList<>(targets); if(bitmap==null && !cancelled) { failures.put(key,SystemClock.elapsedRealtime()+15000); while(failures.size()>256) failures.remove(failures.keySet().iterator().next()); } else failures.remove(key); }
                if(cancelled) return; for(WeakReference<ImageView> reference:waiting) { ImageView target=reference.get(); if(target!=null && key.equals(target.getTag(R_TAG)) && bitmap!=null) { target.animate().cancel(); target.setAlpha(1f); target.setImageBitmap(bitmap); } }
            });
        }
        private Bitmap fetch() {
            try {
                call=client.newCall(new Request.Builder().url(url).header("User-Agent","TVPlayer/0.8").build()); if(cancelled) { call.cancel(); return null; }
                try(Response response=call.execute()) {
                    if(!response.isSuccessful()||response.body()==null) return null;
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream(); try(InputStream input=response.body().byteStream()) { byte[] buffer=new byte[8192]; int n; while((n=input.read(buffer))!=-1) { if(cancelled || bytes.size()+n>12*1024*1024) return null; bytes.write(buffer,0,n); } }
                    byte[] data=bytes.toByteArray(); BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true; BitmapFactory.decodeByteArray(data,0,data.length,bounds); if(bounds.outWidth<=0 || bounds.outHeight<=0) return null;
                    int sample=1; while(fit?(bounds.outWidth/(sample*2)>=width || bounds.outHeight/(sample*2)>=height):(bounds.outWidth/(sample*2)>=width && bounds.outHeight/(sample*2)>=height)) sample*=2;
                    BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=sample; options.inPreferredConfig=Bitmap.Config.RGB_565; return BitmapFactory.decodeByteArray(data,0,data.length,options);
                }
            } catch(Exception|OutOfMemoryError e) { return null; }
        }
    }
}
