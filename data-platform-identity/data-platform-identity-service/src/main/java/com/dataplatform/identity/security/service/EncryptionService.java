package com.dataplatform.identity.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int MAX_KEY_CACHE_SIZE = 100;

    private final SecureRandom secureRandom = new SecureRandom();

    // 使用 LinkedHashMap 实现 LRU 缓存，限制容量防止 OOM
    private final Map<String, byte[]> tableKeys = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            if (size() > MAX_KEY_CACHE_SIZE) {
                log.warn("Encryption key cache exceeds max size, evicting oldest key for table: {}",
                    eldest.getKey());
                return true;
            }
            return false;
        }
    };

    public String encrypt(String plainText, String tableName) {
        try {
            byte[] key = getOrCreateKey(tableName);
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedText, String tableName) {
        try {
            byte[] key = getOrCreateKey(tableName);
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private synchronized byte[] getOrCreateKey(String tableName) {
        byte[] key = tableKeys.get(tableName);
        if (key == null) {
            key = new byte[32];
            secureRandom.nextBytes(key);
            tableKeys.put(tableName, key);
            log.info("Generated new encryption key for table: {}", tableName);
            log.warn("WARNING: Encryption keys are stored in memory only. " +
                "Service restart will cause data loss! Consider using a key management service (KMS) for production.");
        }
        return key;
    }

    public void rotateKey(String tableName) {
        synchronized (this) {
            byte[] oldKey = tableKeys.remove(tableName);
            if (oldKey != null) {
                log.warn("Rotating encryption key for table: {}. " +
                    "Data encrypted with the old key will become unreadable!", tableName);
            }
            getOrCreateKey(tableName);
            log.info("Encryption key rotated for table: {}", tableName);
        }
    }
}