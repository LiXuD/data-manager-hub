package com.dataplatform.identity.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dataplatform.identity.security.entity.EncryptionKey;
import com.dataplatform.identity.security.mapper.EncryptionKeyMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.LocalDateTime;

/**
 * 身份租户域安全加密的 Encryption Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String CIPHERTEXT_VERSION = "v1";

    private final SecureRandom secureRandom = new SecureRandom();
    private final EncryptionKeyMapper keyMapper;
    private final String configuredMasterKey;

    public EncryptionService(EncryptionKeyMapper keyMapper,
                             @Value("${platform.encryption.master-key:}") String configuredMasterKey) {
        this.keyMapper = keyMapper;
        this.configuredMasterKey = configuredMasterKey;
    }

    public String encrypt(String plainText, String tableName) {
        try {
            EncryptionKey keyRecord = getOrCreateKey(tableName);
            byte[] key = decryptDataKey(keyRecord);
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

            return CIPHERTEXT_VERSION + ":" + keyRecord.getId() + ":"
                    + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedText, String tableName) {
        try {
            String[] parts = encryptedText.split(":", 3);
            if (parts.length != 3 || !CIPHERTEXT_VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported encrypted payload format");
            }
            EncryptionKey keyRecord = keyMapper.selectById(Long.valueOf(parts[1]));
            if (keyRecord == null || !tableName.equals(keyRecord.getTableName())) {
                throw new IllegalArgumentException("Encryption key does not belong to table " + tableName);
            }
            byte[] key = decryptDataKey(keyRecord);
            byte[] combined = Base64.getDecoder().decode(parts[2]);
            if (combined.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted payload");
            }

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

    private synchronized EncryptionKey getOrCreateKey(String tableName) {
        requireMasterKey();
        EncryptionKey existing = findActiveKey(tableName);
        if (existing != null) {
            return existing;
        }
        try {
            return createKey(tableName);
        } catch (DuplicateKeyException race) {
            EncryptionKey concurrentKey = findActiveKey(tableName);
            if (concurrentKey == null) {
                throw race;
            }
            return concurrentKey;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public synchronized void rotateKey(String tableName) {
        requireMasterKey();
        keyMapper.update(null, new LambdaUpdateWrapper<EncryptionKey>()
                .eq(EncryptionKey::getTableName, tableName)
                .eq(EncryptionKey::getActive, true)
                .set(EncryptionKey::getActive, false));
        createKey(tableName);
    }

    private EncryptionKey findActiveKey(String tableName) {
        return keyMapper.selectOne(new LambdaQueryWrapper<EncryptionKey>()
                .eq(EncryptionKey::getTableName, tableName)
                .eq(EncryptionKey::getActive, true)
                .orderByDesc(EncryptionKey::getCreatedAt)
                .last("LIMIT 1"));
    }

    private EncryptionKey createKey(String tableName) {
        byte[] dataKey = new byte[32];
        secureRandom.nextBytes(dataKey);
        EncryptionKey key = new EncryptionKey();
        key.setTableName(tableName);
        key.setEncryptedKey(encryptWithKey(dataKey, requireMasterKey()));
        key.setActive(true);
        key.setCreatedAt(LocalDateTime.now());
        keyMapper.insert(key);
        return key;
    }

    private byte[] decryptDataKey(EncryptionKey key) {
        return decryptWithKey(key.getEncryptedKey(), requireMasterKey());
    }

    private byte[] requireMasterKey() {
        if (configuredMasterKey == null || configuredMasterKey.isBlank()) {
            throw new IllegalStateException("platform.encryption.master-key is required");
        }
        try {
            byte[] key = Base64.getDecoder().decode(configuredMasterKey);
            if (key.length != 32) {
                throw new IllegalStateException("platform.encryption.master-key must decode to 32 bytes");
            }
            return key;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("platform.encryption.master-key must be Base64", e);
        }
    }

    private String encryptWithKey(byte[] value, byte[] key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(value);
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to protect data key", e);
        }
    }

    private byte[] decryptWithKey(String value, byte[] key) {
        try {
            byte[] combined = Base64.getDecoder().decode(value);
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to unlock data key", e);
        }
    }
}
