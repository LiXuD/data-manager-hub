package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.vo.DataTestQueryReqVO;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.service.CurrentUserApiKeyOptionService;
import com.dataplatform.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataTestQueryControllerTest {

    private final CurrentUserApiKeyOptionService optionService =
            mock(CurrentUserApiKeyOptionService.class);
    private final OpenApiQueryController openApiQueryController =
            mock(OpenApiQueryController.class);
    private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);
    private final DataTestQueryController controller =
            new DataTestQueryController(optionService, openApiQueryController);

    @Test
    void rejectsMissingOrUnauthorizedApiKey() {
        assertEquals(400, controller.queryForUser(10L, 20L, null, null, httpRequest)
                .getStatusCode().value());

        DataTestQueryReqVO request = request(99L);
        when(optionService.findUsableKey(10L, 20L, 99L)).thenReturn(null);
        assertEquals(403, controller.queryForUser(10L, 20L, null, request, httpRequest)
                .getStatusCode().value());

        verifyNoInteractions(openApiQueryController);
    }

    @Test
    void delegatesToCanonicalOpenApiFlowWithServerResolvedKey() {
        DataTestQueryReqVO request = request(11L);
        ApiKey apiKey = new ApiKey();
        apiKey.setId(11L);
        apiKey.setApiKey("dp_live_secret");
        when(optionService.findUsableKey(10L, 20L, 11L)).thenReturn(apiKey);

        ResponseEntity<Result<OpenApiQueryRespVO>> expected =
                ResponseEntity.ok(Result.success(new OpenApiQueryRespVO()));
        when(openApiQueryController.query(
                "dp_live_secret", null, "trace-1", request, httpRequest))
                .thenReturn(expected);

        ResponseEntity<Result<OpenApiQueryRespVO>> actual = controller.queryForUser(
                10L, 20L, "trace-1", request, httpRequest);

        assertEquals(expected, actual);
        verify(openApiQueryController).query(
                "dp_live_secret", null, "trace-1", request, httpRequest);
    }

    private DataTestQueryReqVO request(Long apiKeyId) {
        DataTestQueryReqVO request = new DataTestQueryReqVO();
        request.setApiKeyId(apiKeyId);
        request.setApiCode("identity-query");
        request.setProductCode("product-a");
        request.setSceneCode("scene-a");
        return request;
    }
}
