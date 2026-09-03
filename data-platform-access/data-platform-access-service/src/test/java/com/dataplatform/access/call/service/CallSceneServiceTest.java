package com.dataplatform.access.call.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.mapper.CallSceneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallSceneServiceTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), CallScene.class);
    }

    @Mock
    private CallSceneMapper mapper;

    private CallSceneService service;

    @BeforeEach
    void setUp() {
        service = new CallSceneService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void createNormalizesInputAndDefaultsToActive() {
        when(mapper.insert(any(CallScene.class))).thenReturn(1);
        CallScene scene = new CallScene();
        scene.setSceneCode("  browser  ");
        scene.setSceneName("  浏览器测试  ");
        scene.setDescription("  description  ");

        scene.setTenantId(999L);
        CallScene created = service.createScene(20L, scene);

        assertEquals("browser", created.getSceneCode());
        assertEquals("浏览器测试", created.getSceneName());
        assertEquals("active", created.getStatus());
        assertEquals("description", created.getDescription());
        assertEquals(20L, created.getTenantId());
        assertFalse(created.getDeleted());
        verify(mapper).insert(scene);
    }

    @Test
    void rejectsUnknownStatusBeforeWriting() {
        CallScene scene = new CallScene();
        scene.setSceneCode("browser");
        scene.setSceneName("浏览器测试");
        scene.setStatus("paused");

        CallSceneException exception = assertThrows(CallSceneException.class,
                () -> service.createScene(20L, scene));

        assertEquals(400, exception.getStatus());
        assertEquals("CALL_SCENE_STATUS_INVALID", exception.getErrorCode());
        verify(mapper, org.mockito.Mockito.never()).insert(any(CallScene.class));
    }

    @Test
    void updatesOnlyMutableMetadataAndKeepsSceneCode() {
        CallScene existing = scene(7L, "browser", "旧名称", "active");
        when(mapper.selectOne(any(), eq(false))).thenReturn(existing);
        when(mapper.updateById(any(CallScene.class))).thenAnswer(invocation -> {
            CallScene update = invocation.getArgument(0);
            existing.setSceneName(update.getSceneName());
            existing.setDescription(update.getDescription());
            return 1;
        });

        CallScene updated = service.updateMetadata(20L, 7L, " 新名称 ", " 新描述 ");

        assertEquals("browser", updated.getSceneCode());
        assertEquals("新名称", updated.getSceneName());
        assertEquals("新描述", updated.getDescription());
        org.mockito.ArgumentCaptor<CallScene> captor =
                org.mockito.ArgumentCaptor.forClass(CallScene.class);
        verify(mapper).updateById(captor.capture());
        CallScene patch = captor.getValue();
        assertEquals(7L, patch.getId());
        assertEquals("新名称", patch.getSceneName());
        assertEquals("新描述", patch.getDescription());
        assertEquals(null, patch.getSceneCode());
        assertEquals(null, patch.getStatus());
    }

    @Test
    void changesStatusWithoutDeletingHistoricalScene() {
        CallScene existing = scene(7L, "browser", "名称", "active");
        when(mapper.selectOne(any(), eq(false))).thenReturn(existing);
        when(mapper.updateById(any(CallScene.class))).thenAnswer(invocation -> {
            CallScene update = invocation.getArgument(0);
            existing.setStatus(update.getStatus());
            return 1;
        });

        CallScene updated = service.changeStatus(20L, 7L, "inactive");

        assertEquals("inactive", updated.getStatus());
        assertFalse(Boolean.TRUE.equals(updated.getDeleted()));
        verify(mapper).updateById(any(CallScene.class));
    }

    @Test
    void rejectsMissingSceneAndInvalidMetadata() {
        when(mapper.selectOne(any(), eq(false))).thenReturn(null);
        CallSceneException missing = assertThrows(CallSceneException.class,
                () -> service.changeStatus(20L, 404L, "inactive"));
        assertEquals(404, missing.getStatus());

        CallScene scene = new CallScene();
        scene.setSceneCode("browser");
        scene.setSceneName(" ");
        CallSceneException invalid = assertThrows(CallSceneException.class,
                () -> service.createScene(20L, scene));
        assertEquals(400, invalid.getStatus());
        assertNull(scene.getDescription());
    }

    @Test
    void scopesSceneListAndRuntimeLookupToTenant() {
        CallScene scene = scene(7L, "browser", "名称", "active");
        when(mapper.selectList(any())).thenReturn(java.util.List.of(scene));
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(scene);

        assertEquals(1, service.listManagedScenes(20L).size());
        service.getActiveScene(20L, " browser ");

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<CallScene>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        assertEquals(true, captor.getValue().getSqlSegment().contains("tenant_id"),
                () -> String.valueOf(captor.getValue().getSqlSegment()));

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<CallScene>> runtimeCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectOne(runtimeCaptor.capture(), eq(false));
        assertEquals(true, runtimeCaptor.getValue().getSqlSegment().contains("tenant_id"),
                () -> String.valueOf(runtimeCaptor.getValue().getSqlSegment()));
    }

    @Test
    void rejectsMissingTenantForWritesAndNeverUsesUnscopedLookup() {
        CallScene scene = new CallScene();
        scene.setSceneCode("browser");
        scene.setSceneName("浏览器测试");

        CallSceneException exception = assertThrows(CallSceneException.class,
                () -> service.createScene(null, scene));

        assertEquals("CALL_SCENE_TENANT_REQUIRED", exception.getErrorCode());
        verify(mapper, org.mockito.Mockito.never()).insert(any(CallScene.class));
        verify(mapper, org.mockito.Mockito.never()).selectById(any());
    }

    private CallScene scene(Long id, String code, String name, String status) {
        CallScene scene = new CallScene();
        scene.setId(id);
        scene.setTenantId(20L);
        scene.setSceneCode(code);
        scene.setSceneName(name);
        scene.setStatus(status);
        scene.setDeleted(false);
        return scene;
    }
}
