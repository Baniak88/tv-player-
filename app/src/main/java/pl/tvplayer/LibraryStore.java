package pl.tvplayer;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import org.json.*;
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
        JSONArray items = new JSONArray();
        for (Channel c : channels) items.put(new JSONObject().put("name", c.name()).put("url", c.url()).put("group", c.group()).put("epg", c.epgId()));
        byte[] plain = new JSONObject().put("channels", items).put("favorites", new JSONArray(favorites)).toString().getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(plain);
        android.util.AtomicFile file = new android.util.AtomicFile(new java.io.File(context.getFilesDir(), "library.enc"));
        java.io.FileOutputStream out = file.startWrite();
        try { out.write(cipher.getIV()); out.write(encrypted); file.finishWrite(out); }
        catch (Exception e) { file.failWrite(out); throw e; }
    }
    List<Channel> load(Set<String> favorites) throws Exception {
        android.util.AtomicFile file = new android.util.AtomicFile(new java.io.File(context.getFilesDir(), "library.enc"));
        if (!file.getBaseFile().exists()) return new ArrayList<>();
        byte[] bytes = file.readFully();
        if (bytes.length < 28) throw new java.io.IOException("Nieprawidłowy zapis biblioteki.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOfRange(bytes, 0, 12)));
        JSONObject root = new JSONObject(new String(cipher.doFinal(Arrays.copyOfRange(bytes, 12, bytes.length)), StandardCharsets.UTF_8));
        JSONArray saved = root.getJSONArray("favorites");
        for (int i = 0; i < saved.length(); i++) favorites.add(saved.getString(i));
        List<Channel> channels = new ArrayList<>();
        JSONArray items = root.getJSONArray("channels");
        for (int i = 0; i < items.length(); i++) {
            JSONObject c = items.getJSONObject(i);
            channels.add(new Channel(c.getString("name"), c.getString("url"), c.getString("group"), c.optString("epg")));
        }
        return channels;
    }
}
