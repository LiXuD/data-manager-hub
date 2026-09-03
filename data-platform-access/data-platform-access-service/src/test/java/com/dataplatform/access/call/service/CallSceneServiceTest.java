package com.dataplatform.access.call.service;

import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.mapper.CallSceneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallSceneServiceTest {

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

        CallScene created = service.createScene(scene);

        assertEquals("browser", created.getSceneCode());
        assertEquals("浏览器测试", created.getSceneName());
        assertEquals("active", created.getStatus());
        assertEquals("description", created.getDescription());
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
                () -> service.createScene(scene));

        assertEquals(400, exception.getStatus());
        assertEquals("CALL_SCENE_STATUS_INVALID", exception.getErrorCode());
        verify(mapper, org.mockito.Mockito.never()).insert(any(CallScene.class));
    }

    @Test
    void updatesOnlyMutableMetadataAndKeepsSceneCode() {
        CallScene existing = scene(7L, "browser", "旧名称", "active");
        when(mapper.selectById(7L)).thenReturn(existing);
        when(mapper.updateById(any(CallScene.class))).thenAnswer(invocation -> {
            CallScene update = invocation.getArgument(0);
            existing.setSceneName(update.getSceneName());
            existing.setDescription(update.getDescription());
            return 1;
        });

        CallScene updated = service.updateMetadata(7L, " 新名称 ", " 新描述 ");

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
        when(mapper.selectById(7L)).thenReturn(existing);
        when(mapper.updateById(any(CallScene.class))).thenAnswer(invocation -> {
            CallScene update = invocation.getArgument(0);
            existing.setStatus(update.getStatus());
            return 1;
        });

        CallScene updated = service.changeStatus(7L, "inactive");

        assertEquals("inactive", updated.getStatus());
        assertFalse(Boolean.TRUE.equals(updated.getDeleted()));
        verify(mapper).updateById(any(CallScene.class));
    }

    @Test
    void rejectsMissingSceneAndInvalidMetadata() {
        when(mapper.selectById(404L)).thenReturn(null);
        CallSceneException missing = assertThrows(CallSceneException.class,
                () -> service.changeStatus(404L, "inactive"));
        assertEquals(404, missing.getStatus());

        CallScene scene = new CallScene();
        scene.setSceneCode("browser");
        scene.setSceneName(" ");
        CallSceneException invalid = assertThrows(CallSceneException.class,
                () -> service.createScene(scene));
        assertEquals(400, invalid.getStatus());
        assertNull(scene.getDescription());
    }

    private CallScene scene(Long id, String code, String name, String status) {
        CallScene scene = new CallScene();
        scene.setId(id);
        scene.setSceneCode(code);
        scene.setSceneName(name);
        scene.setStatus(status);
        scene.setDeleted(false);
        return scene;
    }
}
