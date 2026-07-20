package com.dataplatform.identity.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.identity.security.entity.EncryptionKey;
import com.dataplatform.identity.security.mapper.EncryptionKeyMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

class EncryptionServiceTest {

    @Test
    void rotationKeepsOldCiphertextDecryptable() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EncryptionKey.class);
        EncryptionKeyMapper mapper = mock(EncryptionKeyMapper.class);
        Map<Long, EncryptionKey> keys = new LinkedHashMap<>();
        AtomicLong sequence = new AtomicLong();
        when(mapper.selectOne(any())).thenAnswer(invocation -> keys.values().stream()
                .filter(key -> Boolean.TRUE.equals(key.getActive())).reduce((first, second) -> second)
                .orElse(null));
        when(mapper.insert(any(EncryptionKey.class))).thenAnswer(invocation -> {
            EncryptionKey key = invocation.getArgument(0);
            key.setId(sequence.incrementAndGet());
            keys.put(key.getId(), key);
            return 1;
        });
        when(mapper.selectById(anyLong())).thenAnswer(invocation -> keys.get(invocation.getArgument(0)));
        when(mapper.update(isNull(), any())).thenAnswer(invocation -> {
            keys.values().forEach(key -> key.setActive(false));
            return keys.size();
        });

        String masterKey = Base64.getEncoder().encodeToString(new byte[32]);
        EncryptionService service = new EncryptionService(mapper, masterKey);
        String beforeRotation = service.encrypt("first", "user_info");

        service.rotateKey("user_info");
        String afterRotation = service.encrypt("second", "user_info");

        assertNotEquals(beforeRotation.split(":")[1], afterRotation.split(":")[1]);
        assertEquals("first", service.decrypt(beforeRotation, "user_info"));
        assertEquals("second", service.decrypt(afterRotation, "user_info"));
    }
}
