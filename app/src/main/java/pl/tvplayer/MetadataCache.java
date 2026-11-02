package pl.tvplayer;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Bounded, encrypted metadata cache. Account URLs never appear in file names or plaintext. */
final class MetadataCache {
    private static final Object LOCK=new Object();
    private static final Gson JSON=new Gson();
    private static final long FRESH_MS=15*60*1000,MAX_AGE_MS=7*24*60*60*1000L;
    private static final int MAX_BYTES=4*1024*1024;
    record Hit<T>(T value,boolean fresh) {}
    private final File directory;
    MetadataCache(Context context) { directory=new File(context.getCacheDir(),"details-v1"); }
    private File file(String source) throws Exception { byte[] hash=MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)); StringBuilder name=new StringBuilder(64); for(byte b:hash) name.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16)); return new File(directory,name+".enc"); }
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(!store.containsAlias("tv-details")) { KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore"); generator.init(new KeyGenParameterSpec.Builder("tv-details",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generator.generateKey(); }
        return (javax.crypto.SecretKey)store.getKey("tv-details",null);
    }
    <T> Hit<T> read(String source,Class<T> type) {
        synchronized(LOCK) { try {
            File path=file(source); long age=System.currentTimeMillis()-path.lastModified(); if(!path.isFile() || age<0 || age>MAX_AGE_MS || path.length()>MAX_BYTES) return null;
            byte[] encrypted=new AtomicFile(path).readFully(); if(encrypted.length<28) return null;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,encrypted,0,12)); byte[] compressed=cipher.doFinal(encrypted,12,encrypted.length-12);
            ByteArrayOutputStream plain=new ByteArrayOutputStream(); try(InputStream input=new GZIPInputStream(new ByteArrayInputStream(compressed))) { byte[] buffer=new byte[8192]; int n; while((n=input.read(buffer))!=-1) { if(plain.size()+n>MAX_BYTES) return null; plain.write(buffer,0,n); } }
            T value=JSON.fromJson(plain.toString(StandardCharsets.UTF_8.name()),type); return value==null?null:new Hit<>(value,age<FRESH_MS);
        } catch(Exception e) { return null; } }
    }
    void write(String source,Object value) {
        synchronized(LOCK) { try {
            byte[] plain=JSON.toJson(value).getBytes(StandardCharsets.UTF_8); if(plain.length>MAX_BYTES) return;
            ByteArrayOutputStream zipped=new ByteArrayOutputStream(); try(OutputStream gzip=new GZIPOutputStream(zipped)) { gzip.write(plain); }
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key()); ByteArrayOutputStream encrypted=new ByteArrayOutputStream(); encrypted.write(cipher.getIV()); encrypted.write(cipher.doFinal(zipped.toByteArray()));
            if(!directory.isDirectory() && !directory.mkdirs()) return; AtomicFile target=new AtomicFile(file(source)); FileOutputStream output=target.startWrite(); try { output.write(encrypted.toByteArray()); target.finishWrite(output); } catch(Exception e) { target.failWrite(output); return; }
            File[] files=directory.listFiles((dir,name) -> name.endsWith(".enc")); if(files==null) return; Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed()); long bytes=0; for(int i=0;i<files.length;i++) { bytes+=files[i].length(); if(i>=40 || bytes>16L*1024*1024) new AtomicFile(files[i]).delete(); }
        } catch(Exception ignored) { /* Metadata is optional; cache failures never block playback. */ } }
    }
}
