package pl.tvplayer;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class LibraryStore {
    private static final Object LOCK=new Object();
    private final Context context;
    private javax.crypto.SecretKey cachedKey;
    private final Map<String,byte[]> loadedFingerprints=new HashMap<>();
    LibraryStore(Context context) { this.context=context.getApplicationContext(); }
    private javax.crypto.SecretKey key() throws Exception {
        if(cachedKey!=null) return cachedKey;
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(!store.containsAlias("tv-library")) {
            KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("tv-library",KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        cachedKey=(javax.crypto.SecretKey)store.getKey("tv-library",null); return cachedKey;
    }
    private AtomicFile file(String name) { return new AtomicFile(new File(context.getFilesDir(),name)); }
    private void write(AtomicFile file,byte[] bytes) throws IOException {
        FileOutputStream out=file.startWrite();
        try { out.write(bytes); file.finishWrite(out); } catch(IOException e) { file.failWrite(out); throw e; }
    }
    private void encryptedWrite(String name,byte[] plain) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(plain);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(encrypted.length+12);
        bytes.write(cipher.getIV()); bytes.write(encrypted); write(file(name),bytes.toByteArray());
    }
    private byte[] decryptedRead(String name) throws Exception {
        byte[] bytes=file(name).readFully();
        if(bytes.length<28) throw new IOException("Nieprawidłowy zapis biblioteki.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,bytes,0,12));
        // One bulk operation avoids a hardware-keystore IPC for every 512-byte CipherInputStream read.
        return cipher.doFinal(bytes,12,bytes.length-12);
    }
    private byte[] fingerprint(String name) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-256").digest(file(name).readFully());
    }
    private String generation() throws IOException {
        AtomicFile current=file("catalog.current");
        try {
            String name=new String(current.readFully(),StandardCharsets.UTF_8);
            if(!name.matches("[a-f0-9-]{36}")) throw new IOException("Nieprawidłowy indeks biblioteki.");
            return name;
        } catch(FileNotFoundException missing) { return null; }
    }
    private void writeChannel(JsonWriter writer,Channel c) throws IOException {
        writer.beginObject().name("name").value(c.name()).name("url").value(c.url()).name("group").value(c.group())
            .name("epg").value(c.epgId()).name("kind").value(c.kind()).name("guide").value(c.guideUrl())
            .name("archiveDays").value(c.archiveDays()).name("timezone").value(c.timezone()).name("bouquetOrder").value(c.bouquetOrder()).endObject();
    }
    private Channel readChannel(JsonReader reader) throws IOException {
        String name="",url="",group="Bez kategorii",epg="",kind="",guide="",timezone="UTC"; int days=0,order=Integer.MAX_VALUE;
        reader.beginObject();
        while(reader.hasNext()) {
            switch(reader.nextName()) {
                case "name": name=SourceClient.value(reader); break;
                case "url": url=SourceClient.value(reader); break;
                case "group": group=SourceClient.value(reader); break;
                case "epg": epg=SourceClient.value(reader); break;
                case "kind": kind=SourceClient.value(reader); break;
                case "guide": guide=SourceClient.value(reader); break;
                case "timezone": timezone=SourceClient.value(reader); break;
                case "bouquetOrder": try { order=Integer.parseInt(SourceClient.value(reader)); } catch(NumberFormatException ignored) {} break;
                case "archiveDays": try { days=Integer.parseInt(SourceClient.value(reader)); } catch(NumberFormatException ignored) {} break;
                default: reader.skipValue();
            }
        }
        reader.endObject(); return new Channel(name,url,group,epg,kind,guide,days,timezone,order);
    }
    void save(List<Channel> channels,Set<String> favorites) throws Exception {
        synchronized(LOCK) {
            String next=UUID.randomUUID().toString();
            try {
                for(String section:new String[]{"tv","movie","series"}) {
                    ByteArrayOutputStream compressed=new ByteArrayOutputStream();
                    try(JsonWriter writer=new JsonWriter(new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(compressed),StandardCharsets.UTF_8)))) {
                        writer.beginArray(); for(Channel c:channels) if(c.section().equals(section)) writeChannel(writer,c); writer.endArray();
                    }
                    encryptedWrite("catalog-"+next+"-"+section+".enc",compressed.toByteArray());
                }
                saveFavorites(favorites);
                write(file("catalog.current"),next.getBytes(StandardCharsets.UTF_8));
            } catch(Exception e) {
                for(String section:new String[]{"tv","movie","series"}) file("catalog-"+next+"-"+section+".enc").delete();
                throw e;
            }
            // The manifest changes only after all sections are complete.
            File[] old=context.getFilesDir().listFiles();
            if(old!=null) for(File entry:old) {
                String name=entry.getName();
                if(name.startsWith("catalog-") && !name.startsWith("catalog-"+next+"-") && name.endsWith(".enc")) file(name).delete();
            }
            file("library.enc").delete(); loadedFingerprints.clear();
        }
    }
    void updateSection(String section,List<Channel> items) throws Exception {
        synchronized(LOCK) {
            if(!Arrays.asList("tv","movie","series").contains(section)) throw new IllegalArgumentException("Nieprawidłowa sekcja.");
            String previous=generation(); if(previous==null) return;
            byte[] expected=loadedFingerprints.get(section);
            if(expected!=null && !java.security.MessageDigest.isEqual(expected,fingerprint("catalog-"+previous+"-"+section+".enc"))) return;
            String next=UUID.randomUUID().toString();
            try {
                for(String part:new String[]{"tv","movie","series"}) {
                    String destination="catalog-"+next+"-"+part+".enc";
                    if(part.equals(section)) {
                        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
                        try(JsonWriter writer=new JsonWriter(new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(compressed),StandardCharsets.UTF_8)))) {
                            writer.beginArray(); for(Channel c:items) writeChannel(writer,c); writer.endArray();
                        }
                        encryptedWrite(destination,compressed.toByteArray());
                    } else {
                        // Copy unchanged encrypted sections without decrypting or scanning VOD.
                        write(file(destination),file("catalog-"+previous+"-"+part+".enc").readFully());
                    }
                }
                write(file("catalog.current"),next.getBytes(StandardCharsets.UTF_8));
            } catch(Exception e) {
                for(String part:new String[]{"tv","movie","series"}) file("catalog-"+next+"-"+part+".enc").delete();
                throw e;
            }
            loadedFingerprints.put(section,fingerprint("catalog-"+next+"-"+section+".enc"));
            for(String part:new String[]{"tv","movie","series"}) file("catalog-"+previous+"-"+part+".enc").delete();
        }
    }
    void saveFavorites(Set<String> favorites) throws Exception {
        synchronized(LOCK) {
            ByteArrayOutputStream plain=new ByteArrayOutputStream();
            try(JsonWriter writer=new JsonWriter(new OutputStreamWriter(plain,StandardCharsets.UTF_8))) {
                writer.beginArray(); for(String url:favorites) writer.value(url); writer.endArray();
            }
            encryptedWrite("favorites.enc",plain.toByteArray());
        }
    }
    private void readFavorites(Set<String> favorites) throws Exception {
        byte[] bytes;
        try { bytes=decryptedRead("favorites.enc"); } catch(FileNotFoundException missing) { return; }
        try(JsonReader reader=new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8))) {
            reader.beginArray(); while(reader.hasNext()) favorites.add(reader.nextString()); reader.endArray();
            if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IOException("Nieprawidłowy zapis ulubionych.");
        }
    }
    List<Channel> loadSection(String section,Set<String> favorites) throws Exception {
        synchronized(LOCK) {
            if(!Arrays.asList("tv","movie","series").contains(section)) throw new IllegalArgumentException("Nieprawidłowa sekcja.");
            String current=generation();
            if(current==null) {
                List<Channel> old=loadLegacy(favorites);
                if(!old.isEmpty() || !favorites.isEmpty()) { save(old,favorites); loadedFingerprints.put(section,fingerprint("catalog-"+generation()+"-"+section+".enc")); }
                List<Channel> result=new ArrayList<>(); for(Channel c:old) if(c.section().equals(section)) result.add(c);
                return result;
            }
            byte[] compressed=decryptedRead("catalog-"+current+"-"+section+".enc");
            List<Channel> result=new ArrayList<>();
            try(JsonReader reader=new JsonReader(new BufferedReader(new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(compressed)),StandardCharsets.UTF_8)))) {
                reader.beginArray(); while(reader.hasNext()) result.add(readChannel(reader)); reader.endArray();
                if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IOException("Nieprawidłowy zapis katalogu.");
            }
            loadedFingerprints.put(section,fingerprint("catalog-"+current+"-"+section+".enc"));
            readFavorites(favorites); return result;
        }
    }
    private List<Channel> loadLegacy(Set<String> favorites) throws Exception {
        byte[] plain;
        try { plain=decryptedRead("library.enc"); } catch(FileNotFoundException missing) { return new ArrayList<>(); }
        List<Channel> result=new ArrayList<>();
        try(JsonReader reader=new JsonReader(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(plain),StandardCharsets.UTF_8)))) {
            reader.beginObject();
            while(reader.hasNext()) {
                switch(reader.nextName()) {
                    case "channels": reader.beginArray(); while(reader.hasNext()) result.add(readChannel(reader)); reader.endArray(); break;
                    case "favorites": reader.beginArray(); while(reader.hasNext()) favorites.add(reader.nextString()); reader.endArray(); break;
                    default: reader.skipValue();
                }
            }
            reader.endObject(); if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IOException("Nieprawidłowy zapis starej biblioteki.");
        }
        return result;
    }
}
