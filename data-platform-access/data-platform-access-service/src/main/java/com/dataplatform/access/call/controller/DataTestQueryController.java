package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.vo.DataTestQueryReqVO;
import com.dataplatform.access.call.vo.OpenApiQueryRespVO;
import com.dataplatform.access.caller.entity.ApiKey;
import com.dataplatform.access.caller.service.CurrentUserApiKeyOptionService;
import com.dataplatform.api.Result;
import com.dataplatform.common.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录态数据查询测试入口。服务端解析用户可选的 API Key，并复用 OpenAPI 查询链路。
 */
@RestController
@RequestMapping("/data-test")
public class DataTestQueryController {

    private final CurrentUserApiKeyOptionService currentUserApiKeyOptionService;
    private final OpenApiQueryController openApiQueryController;

    public DataTestQueryController(
            CurrentUserApiKeyOptionService currentUserApiKeyOptionService,
            OpenApiQueryController openApiQueryController) {
        this.currentUserApiKeyOptionService = currentUserApiKeyOptionService;
        this.openApiQueryController = openApiQueryController;
    }

    @PostMapping("/query")
    public ResponseEntity<Result<OpenApiQueryRespVO>> query(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody DataTestQueryReqVO request,
            HttpServletRequest httpRequest) {
        return queryForUser(
                UserContext.getCurrentUserId(),
                UserContext.getCurrentTenantId(),
                traceId,
                request,
                httpRequest);
    }

    ResponseEntity<Result<OpenApiQueryRespVO>> queryForUser(
            Long userId,
            Long tenantId,
            String traceId,
            DataTestQueryReqVO request,
            HttpServletRequest httpRequest) {
        if (request == null || request.getApiKeyId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(HttpStatus.BAD_REQUEST.value(), "apiKeyId不能为空"));
        }

        ApiKey apiKey = currentUserApiKeyOptionService.findUsableKey(
                userId,
                tenantId,
                request.getApiKeyId());
        if (apiKey == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(HttpStatus.FORBIDDEN.value(), "无权使用该API Key"));
        }

        return openApiQueryController.query(
                apiKey.getApiKey(), null, traceId, request, httpRequest);
    }
}
