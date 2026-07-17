package com.dataplatform.masterdata.vendor.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import com.dataplatform.api.Result;
import com.dataplatform.identity.api.dto.EncryptionReqDTO;
import com.dataplatform.identity.api.feign.EncryptionInternalFeignClient;
import com.dataplatform.masterdata.vendor.entity.VendorExtendedConfig;
import com.dataplatform.masterdata.vendor.mapper.ConfigVersionMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorExtendedConfigMapper;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

class VendorExtendedConfigServiceImplSecurityTest {

    private VendorExtendedConfigServiceImpl service;
    private VendorExtendedConfigMapper mapper;
    private EncryptionInternalFeignClient encryptionClient;
    private ConfigVersionMapper configVersionMapper;
    private ValueOperations<String, String> values;
    private StringRedisTemplate redis;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = spy(new VendorExtendedConfigServiceImpl());
        mapper = mock(VendorExtendedConfigMapper.class);
        encryptionClient = mock(EncryptionInternalFeignClient.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        configVersionMapper = mock(ConfigVersionMapper.class);
        when(configVersionMapper.insert(any(com.dataplatform.masterdata.vendor.entity.ConfigVersion.class))).thenReturn(1);
        ReflectionTestUtils.setField(service, "configVersionMapper", configVersionMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        ReflectionTestUtils.setField(service, "encryptionClient", encryptionClient);
    }

    @Test
    void shouldEncryptBeforeStorageAndMaskResponse() {
        VendorExtendedConfig input = config(true, "plain-secret");
        when(encryptionClient.encrypt(any(EncryptionReqDTO.class))).thenReturn(Result.success("v1:1:ciphertext"));
        doReturn(true).when(service).save(any(VendorExtendedConfig.class));

        VendorExtendedConfig result = service.saveSecure(input);

        assertEquals("••••••••", result.getConfigValue());
        verify(service).save(org.mockito.ArgumentMatchers.argThat(
                stored -> "v1:1:ciphertext".equals(stored.getConfigValue())));
    }

    @Test
    void shouldDecryptOnlyForInternalResolutionAndCacheBriefly() {
        VendorExtendedConfig stored = config(true, "v1:1:ciphertext");
        when(values.get("config:9:vendor.aes.key")).thenReturn(null);
        doReturn(stored).when(service).getOne(any());
        when(encryptionClient.decrypt(any(EncryptionReqDTO.class))).thenReturn(Result.success("plain-secret"));

        String value = service.getConfig(9L, "vendor.aes.key");

        assertEquals("plain-secret", value);
        verify(values).set("config:9:vendor.aes.key", "plain-secret", 300, TimeUnit.SECONDS);
    }

    @Test
    void shouldPreserveCiphertextWhenMaskedValueIsSubmitted() {
        VendorExtendedConfig stored = config(true, "v1:1:ciphertext");
        stored.setId(5L);
        when(mapper.selectById(5L)).thenReturn(stored);
        VendorExtendedConfig update = config(true, "••••••••");
        doReturn(true).when(service).updateById(any(VendorExtendedConfig.class));

        VendorExtendedConfig result = service.updateSecure(5L, update);

        assertEquals("••••••••", result.getConfigValue());
        verify(encryptionClient, never()).encrypt(any(EncryptionReqDTO.class));
        verify(service).updateById(org.mockito.ArgumentMatchers.argThat(
                entity -> "v1:1:ciphertext".equals(entity.getConfigValue())));
    }

    @Test
    void shouldRejectPublishingEncryptedConfigToSharedChannel() {
        VendorExtendedConfig stored = config(true, "v1:1:ciphertext");
        doReturn(stored).when(service).getOne(any());

        assertFalse(service.publishConfig("vendor.aes.key"));

        verify(encryptionClient, never()).decrypt(any(EncryptionReqDTO.class));
        verify(values, never()).set(eq("config:vendor.aes.key:publish"), any(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void shouldEvictCachedSecretWhenDisabled() {
        VendorExtendedConfig stored = config(true, "v1:1:ciphertext");
        stored.setId(5L);
        when(mapper.selectById(5L)).thenReturn(stored);
        doReturn(true).when(service).updateById(org.mockito.ArgumentMatchers.argThat(
                update -> Long.valueOf(5L).equals(update.getId()) && Boolean.FALSE.equals(update.getIsActive())));

        assertEquals(true, service.updateStatusSecure(5L, "inactive"));

        verify(redis).delete("config:9:vendor.aes.key");
    }

    @Test
    void shouldReadLegacyPlaintextMarkedEncryptedAndEncryptItOnNextUpdate() {
        VendorExtendedConfig stored = config(true, "legacy-plain-secret");
        stored.setId(5L);
        when(values.get("config:9:vendor.aes.key")).thenReturn(null);
        doReturn(stored).when(service).getOne(any());
        when(mapper.selectById(5L)).thenReturn(stored);
        when(encryptionClient.encrypt(any(EncryptionReqDTO.class))).thenReturn(Result.success("v1:1:ciphertext"));
        doReturn(true).when(service).updateById(any(VendorExtendedConfig.class));

        assertEquals("legacy-plain-secret", service.getConfig(9L, "vendor.aes.key"));
        service.updateSecure(5L, config(true, "••••••••"));

        verify(encryptionClient, never()).decrypt(any(EncryptionReqDTO.class));
        verify(service).updateById(org.mockito.ArgumentMatchers.argThat(
                entity -> "v1:1:ciphertext".equals(entity.getConfigValue())));
    }

    @Test
    void shouldFailWhenSecurePersistenceReturnsFalse() {
        VendorExtendedConfig input = config(false, "value");
        doReturn(false).when(service).save(any(VendorExtendedConfig.class));

        assertThrows(IllegalStateException.class, () -> service.saveSecure(input));
    }

    private VendorExtendedConfig config(boolean encrypted, String value) {
        VendorExtendedConfig config = new VendorExtendedConfig();
        config.setVendorId(9L);
        config.setConfigKey("vendor.aes.key");
        config.setConfigValue(value);
        config.setConfigType("password");
        config.setIsEncrypted(encrypted);
        config.setIsActive(true);
        config.setStatus("active");
        return config;
    }
}
