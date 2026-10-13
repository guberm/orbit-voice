package dev.orbitvoice.common;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Private, atomic encrypted files. Decryption errors never silently reset account data. */
public final class Vault {
    private final Context context;
    private static final String ALIAS="orbit-voice-v1";
    public Vault(Context context) { this.context=context.getApplicationContext(); }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());gen.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    private AtomicFile file(String name) { if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid storage name");return new AtomicFile(new File(context.getFilesDir(),name+".enc")); }
    public synchronized String read(String name,String defaultValue) throws IOException {
        AtomicFile f=file(name);if (!f.getBaseFile().exists()) return defaultValue;
        try {
            byte[] bytes=f.readFully();if (bytes.length<29 || bytes.length>16000000) throw new IOException("Invalid encrypted file");
            byte[] iv=java.util.Arrays.copyOfRange(bytes,0,12);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IOException("Cannot unlock local data. Restore access to this installation or clear app data only if you accept losing its history.",e); }
    }
    public synchronized void write(String name,String value) throws IOException {
        AtomicFile f=file(name);FileOutputStream out=null;
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));out=f.startWrite();out.write(cipher.getIV());out.write(encrypted);f.finishWrite(out);
        } catch (Exception e) { if (out!=null) f.failWrite(out);throw new IOException("Cannot save local data",e); }
    }
    public synchronized void delete(String name) { file(name).delete(); }
}
