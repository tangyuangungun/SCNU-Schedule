package cn.edu.scnu.schedule;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class CredentialCipher {
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "scnu_schedule_credentials";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BITS = 128;

    private CredentialCipher() {}

    static String encrypt(String value) throws Exception {
        if (value == null || value.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv, Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    static String decrypt(String encoded) throws Exception {
        if (encoded == null || encoded.isEmpty()) return "";
        int separator = encoded.indexOf(':');
        if (separator <= 0 || separator >= encoded.length() - 1) {
            throw new GeneralSecurityException("Invalid encrypted credential format");
        }
        byte[] iv = Base64.decode(encoded.substring(0, separator), Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(encoded.substring(separator + 1), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(PROVIDER);
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }
}