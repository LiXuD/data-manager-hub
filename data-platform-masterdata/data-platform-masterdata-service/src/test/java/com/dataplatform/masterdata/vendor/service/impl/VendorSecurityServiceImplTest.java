package com.dataplatform.masterdata.vendor.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityOrderReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityPreviewReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorSecurityStepDTO;
import com.dataplatform.masterdata.vendor.entity.VendorConfig;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.entity.VendorSecurityStep;
import com.dataplatform.masterdata.vendor.entity.VendorSecurityVersion;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorSecurityStepMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorSecurityVersionMapper;
import com.dataplatform.masterdata.vendor.service.SecurityConfigConflictException;
import com.dataplatform.masterdata.vendor.service.VendorExtendedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

class VendorSecurityServiceImplTest {

    private VendorSecurityStepMapper stepMapper;
    private VendorSecurityVersionMapper versionMapper;
    private VendorConfigMapper configMapper;
    private VendorInfoMapper vendorInfoMapper;
    private VendorExtendedConfigService extendedConfigService;
    private VendorSecurityServiceImpl service;
    private VendorConfig config;
    private List<VendorSecurityStep> stored;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, VendorConfig.class);
        TableInfoHelper.initTableInfo(assistant, VendorSecurityStep.class);
        TableInfoHelper.initTableInfo(assistant, VendorSecurityVersion.class);
        stepMapper = mock(VendorSecurityStepMapper.class);
        versionMapper = mock(VendorSecurityVersionMapper.class);
        configMapper = mock(VendorConfigMapper.class);
        vendorInfoMapper = mock(VendorInfoMapper.class);
        extendedConfigService = mock(VendorExtendedConfigService.class);
        service = new VendorSecurityServiceImpl(stepMapper, versionMapper, configMapper, vendorInfoMapper,
                extendedConfigService, new ObjectMapper());
        config = new VendorConfig();
        config.setId(1L);
        config.setVendorId(9L);
        config.setSecurityVersion(0);
        stored = new ArrayList<>();
        AtomicLong ids = new AtomicLong(10);
        when(configMapper.selectById(1L)).thenReturn(config);
        when(configMapper.update(any(), any())).thenReturn(1);
        when(stepMapper.delete(any())).thenAnswer(invocation -> { stored.clear(); return 1; });
        when(stepMapper.insert(any(VendorSecurityStep.class))).thenAnswer(invocation -> {
            VendorSecurityStep entity = invocation.getArgument(0);
            entity.setId(ids.incrementAndGet());
            stored.add(entity);
            return 1;
        });
        when(stepMapper.selectList(any())).thenAnswer(invocation -> stored.stream()
                .sorted(Comparator.comparing(VendorSecurityStep::getDirection)
                        .thenComparing(VendorSecurityStep::getSortNo))
                .toList());
        when(stepMapper.updateById(any(VendorSecurityStep.class))).thenAnswer(invocation -> {
            VendorSecurityStep update = invocation.getArgument(0);
            stored.stream().filter(item -> item.getId().equals(update.getId())).findFirst()
                    .ifPresent(item -> item.setSortNo(update.getSortNo()));
            return 1;
        });
        when(versionMapper.insert(any(VendorSecurityVersion.class))).thenReturn(1);
    }

    @Test
    void shouldReplaceVersionAndReorderAllDirectionSteps() {
        List<VendorSecurityStepDTO> steps = List.of(
                step("canonical", "CANONICALIZE", Map.of("inputFrom", "PARAMS", "fieldOrder", "KEY_ASC")),
                step("digest", "DIGEST", Map.of("inputFrom", "canonical", "algorithm", "SHA256")),
                step("inject", "INJECT", Map.of("inputFrom", "digest", "location", "HEADER", "fieldName", "X-Sign")),
                step("nonce", "GENERATE", Map.of("generator", "NONCE", "fieldName", "nonce", "location", "PARAM")));

        var saved = service.replaceSteps(1L, 0, steps);

        assertEquals(1, saved.getVersion());
        assertEquals(List.of("canonical", "digest", "inject", "nonce"),
                saved.getSteps().stream().map(VendorSecurityStepDTO::getStepKey).toList());
        verify(versionMapper).insert(any(VendorSecurityVersion.class));

        config.setSecurityVersion(1);
        VendorSecurityOrderReqDTO order = new VendorSecurityOrderReqDTO();
        order.setVersion(1);
        order.setDirection("REQUEST");
        Map<String, Long> idsByKey = saved.getSteps().stream()
                .collect(java.util.stream.Collectors.toMap(VendorSecurityStepDTO::getStepKey, VendorSecurityStepDTO::getId));
        order.setOrderedStepIds(List.of(idsByKey.get("nonce"), idsByKey.get("canonical"),
                idsByKey.get("digest"), idsByKey.get("inject")));
        var reordered = service.reorder(1L, order);

        assertEquals(2, reordered.getVersion());
        assertEquals(order.getOrderedStepIds(), reordered.getSteps().stream().map(VendorSecurityStepDTO::getId).toList());
    }

    @Test
    void shouldRejectStaleVersionAndResolveOnlyReferencedSecrets() throws Exception {
        assertThrows(SecurityConfigConflictException.class,
                () -> service.replaceSteps(1L, 2, List.of()));

        VendorSecurityStep vendorSecret = entity("hmac", "HMAC",
                Map.of("algorithm", "HMAC_SHA256", "secretRef", "vendor.secretKey"), 100);
        VendorSecurityStep configSecret = entity("encrypt", "ENCRYPT",
                Map.of("algorithm", "AES_GCM", "secretRef", "vendor.aes.key"), 200);
        stored.addAll(List.of(vendorSecret, configSecret));
        VendorInfo vendor = new VendorInfo();
        vendor.setId(9L);
        vendor.setSecretKey("vendor-secret");
        when(vendorInfoMapper.selectById(9L)).thenReturn(vendor);
        when(extendedConfigService.getConfig(9L, "vendor.aes.key")).thenReturn("aes-secret");

        Map<String, String> secrets = service.resolveSecrets(1L);

        assertEquals("vendor-secret", secrets.get("vendor.secretKey"));
        assertEquals("aes-secret", secrets.get("vendor.aes.key"));
        assertEquals(2, secrets.size());
        assertTrue(service.capabilities().stream().anyMatch(item -> item.getAlgorithms().contains("SM4_CBC")));
    }

    @Test
    void shouldResolveSecretReferencedOnlyByUnsavedPreviewSteps() {
        when(extendedConfigService.getConfig(9L, "vendor.preview.key")).thenReturn("preview-secret");
        VendorSecurityStepDTO hmac = step("preview-hmac", "HMAC", Map.of(
                "inputFrom", "PARAMS.payload",
                "algorithm", "HMAC_SHA256",
                "secretRef", "vendor.preview.key",
                "outputEncoding", "HEX_LOWER"));
        VendorSecurityPreviewReqDTO request = new VendorSecurityPreviewReqDTO();
        request.setDirection("REQUEST");
        request.setParams(Map.of("payload", "hello"));
        request.setSteps(List.of(hmac));

        var preview = service.preview(1L, request);

        assertTrue(String.valueOf(preview.getStepResults().get("preview-hmac")).matches("[0-9a-f]{64}"));
        verify(extendedConfigService).getConfig(9L, "vendor.preview.key");
    }

    private VendorSecurityStepDTO step(String key, String type, Map<String, Object> stepConfig) {
        VendorSecurityStepDTO dto = new VendorSecurityStepDTO();
        dto.setStepKey(key);
        dto.setDirection("REQUEST");
        dto.setStepType(type);
        dto.setEnabled(true);
        dto.setConfig(new LinkedHashMap<>(stepConfig));
        return dto;
    }

    private VendorSecurityStep entity(String key, String type, Map<String, Object> stepConfig, int sortNo)
            throws Exception {
        VendorSecurityStep entity = new VendorSecurityStep();
        entity.setId((long) sortNo);
        entity.setVendorConfigId(1L);
        entity.setStepKey(key);
        entity.setDirection("REQUEST");
        entity.setStepType(type);
        entity.setSortNo(sortNo);
        entity.setEnabled(true);
        entity.setConfigJson(new ObjectMapper().writeValueAsString(stepConfig));
        return entity;
    }
}
