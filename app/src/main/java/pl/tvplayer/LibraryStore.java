package pl.tvplayer;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class LibraryStore {
    private final Context context;
    LibraryStore(Context context) { this.context = context; }
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias("tv-library")) {
            KeyGenerator gen = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("tv-library", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (javax.crypto.SecretKey) store.getKey("tv-library", null);
    }
    void save(List<Channel> channels, Set<String> favorites) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        android.util.AtomicFile file = new android.util.AtomicFile(new File(context.getFilesDir(), "library.enc"));
        FileOutputStream out = file.startWrite();
        try {
            out.write(cipher.getIV());
            // Keep the file open until AtomicFile has synced and committed it.
            OutputStream keepOpen = new FilterOutputStream(out) {
                @Override public void write(byte[] b, int offset, int length) throws IOException { out.write(b, offset, length); }
                @Override public void close() throws IOException { flush(); }
            };
            try (JsonWriter writer = new JsonWriter(new BufferedWriter(new OutputStreamWriter(
                    new CipherOutputStream(keepOpen, cipher), StandardCharsets.UTF_8)))) {
                writer.beginObject().name("channels").beginArray();
                for (Channel c : channels) {
                    writer.beginObject().name("name").value(c.name()).name("url").value(c.url())
                            .name("group").value(c.group()).name("epg").value(c.epgId()).name("kind").value(c.kind()).endObject();
                }
                writer.endArray().name("favorites").beginArray();
                for (String url : favorites) writer.value(url);
                writer.endArray().endObject();
            }
            file.finishWrite(out);
        } catch (Exception e) { file.failWrite(out); throw e; }
    }
    List<Channel> load(Set<String> favorites) throws Exception {
        android.util.AtomicFile file = new android.util.AtomicFile(new File(context.getFilesDir(), "library.enc"));
        // openRead also restores an interrupted AtomicFile backup, when present.
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile().getPath() + ".bak").exists()) return new ArrayList<>();
        List<Channel> channels = new ArrayList<>();
        Set<String> savedFavorites = new HashSet<>();
        try (InputStream input = file.openRead()) {
            byte[] iv = new byte[12]; int offset = 0;
            while (offset < iv.length) {
                int count = input.read(iv, offset, iv.length - offset);
                if (count < 0) throw new IOException("Nieprawidłowy zapis biblioteki.");
                offset += count;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            try (JsonReader reader = new JsonReader(new BufferedReader(new InputStreamReader(
                    new CipherInputStream(input, cipher), StandardCharsets.UTF_8)))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "channels":
                            reader.beginArray();
                            while (reader.hasNext()) {
                                String name = "", url = "", group = "Bez kategorii", epg = "", kind = "";
                                reader.beginObject();
                                while (reader.hasNext()) {
                                    switch (reader.nextName()) {
                                        case "name": name = SourceClient.value(reader); break;
                                        case "url": url = SourceClient.value(reader); break;
                                        case "group": group = SourceClient.value(reader); break;
                                        case "epg": epg = SourceClient.value(reader); break;
                                        case "kind": kind = SourceClient.value(reader); break;
                                        default: reader.skipValue();
                                    }
                                }
                                reader.endObject(); channels.add(new Channel(name, url, group, epg, kind));
                            }
                            reader.endArray(); break;
                        case "favorites":
                            reader.beginArray();
                            while (reader.hasNext()) savedFavorites.add(reader.nextString());
                            reader.endArray(); break;
                        default: reader.skipValue();
                    }
                }
                reader.endObject();
                // Reading EOF authenticates the GCM tag before publishing the library.
                if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Nieprawidłowy zapis biblioteki.");
            }
        }
        favorites.addAll(savedFavorites);
        return channels;
    }
}
