package pl.tvplayer;
import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import com.google.gson.Gson;

final class TvAccountStore {
    record Account(String type,String server,String user,String password) {}
    private final Context context;
    TvAccountStore(Context context) { this.context=context.getApplicationContext(); }
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(!store.containsAlias("tv-account")) {
            KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("tv-account",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (javax.crypto.SecretKey)store.getKey("tv-account",null);
    }
    private AtomicFile file() { return new AtomicFile(new File(context.getFilesDir(),"tv-account.enc")); }
    void save(Account account) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(new Gson().toJson(account).getBytes(StandardCharsets.UTF_8));
        AtomicFile destination=file(); FileOutputStream output=destination.startWrite();
        try { output.write(cipher.getIV()); output.write(encrypted); destination.finishWrite(output); }
        catch(IOException e) { destination.failWrite(output); throw e; }
    }
    Account load() throws Exception {
        byte[] bytes; try { bytes=file().readFully(); } catch(FileNotFoundException missing) { return null; }
        if(bytes.length<28) throw new IOException("Nieprawidłowy zapis konta.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,bytes,0,12));
        return new Gson().fromJson(new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8),Account.class);
    }
}
