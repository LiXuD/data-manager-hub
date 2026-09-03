package com.dataplatform.access.call.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.service.CallSceneService;
import com.dataplatform.access.call.vo.CallSceneStatusUpdateReqVO;
import com.dataplatform.access.call.vo.CallSceneUpdateReqVO;
import com.dataplatform.common.util.UserContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class CallSceneControllerTenantTest {

    private final CallSceneService service = mock(CallSceneService.class);
    private final CallSceneController controller = new CallSceneController(service);

    @Test
    void passesCurrentTenantToAllSceneManagementOperations() {
        CallScene input = new CallScene();
        input.setSceneCode("browser");
        input.setSceneName("浏览器测试");
        CallSceneUpdateReqVO update = new CallSceneUpdateReqVO();
        update.setSceneName("新名称");
        CallSceneStatusUpdateReqVO status = new CallSceneStatusUpdateReqVO();
        status.setStatus("inactive");

        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(20L);
            when(service.listManagedScenes(20L)).thenReturn(List.of());
            when(service.createScene(eq(20L), any(CallScene.class))).thenReturn(input);
            when(service.updateMetadata(20L, 7L, "新名称", null)).thenReturn(input);
            when(service.changeStatus(20L, 7L, "inactive")).thenReturn(input);

            controller.list();
            controller.create(input);
            controller.update(7L, update);
            controller.updateStatus(7L, status);

            verify(service).listManagedScenes(20L);
            verify(service).createScene(20L, input);
            verify(service).updateMetadata(20L, 7L, "新名称", null);
            verify(service).changeStatus(20L, 7L, "inactive");
        }
    }

    @Test
    void returnsEmptyListWhenNoTenantSessionExists() {
        try (var userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentTenantId).thenReturn(null);
            when(service.listManagedScenes(null)).thenReturn(List.of());

            assertThat(controller.list().getData()).isEmpty();
            verify(service).listManagedScenes(null);
        }
    }
}
