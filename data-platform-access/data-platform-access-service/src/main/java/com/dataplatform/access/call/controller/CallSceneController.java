package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.service.CallSceneException;
import com.dataplatform.access.call.service.CallSceneService;
import com.dataplatform.access.call.vo.CallSceneErrorVO;
import com.dataplatform.access.call.vo.CallSceneStatusUpdateReqVO;
import com.dataplatform.access.call.vo.CallSceneUpdateReqVO;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.common.util.UserContext;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访问域数据调用的 Call Scene Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/call-scene")
public class CallSceneController {

    private final CallSceneService callSceneService;

    public CallSceneController(CallSceneService callSceneService) {
        this.callSceneService = callSceneService;
    }

    @GetMapping("/list")
    public Result<List<CallScene>> list() {
        return Result.success(callSceneService.listManagedScenes(UserContext.getCurrentTenantId()));
    }

    @OperationLog(module = "调用场景管理", operation = "新增调用场景")
    @PostMapping
    public ResponseEntity<Result<CallScene>> create(@RequestBody CallScene scene) {
        return ResponseEntity.ok(Result.success(callSceneService.createScene(
                UserContext.getCurrentTenantId(), scene)));
    }

    @OperationLog(module = "调用场景管理", operation = "编辑调用场景")
    @PutMapping("/{id}")
    public ResponseEntity<Result<CallScene>> update(
            @PathVariable Long id, @RequestBody CallSceneUpdateReqVO request) {
        return ResponseEntity.ok(Result.success(callSceneService.updateMetadata(
                UserContext.getCurrentTenantId(), id,
                request == null ? null : request.getSceneName(),
                request == null ? null : request.getDescription())));
    }

    @OperationLog(module = "调用场景管理", operation = "更新调用场景状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<CallScene>> updateStatus(
            @PathVariable Long id, @RequestBody CallSceneStatusUpdateReqVO request) {
        return ResponseEntity.ok(Result.success(callSceneService.changeStatus(
                UserContext.getCurrentTenantId(), id,
                request == null ? null : request.getStatus())));
    }

    @ExceptionHandler(CallSceneException.class)
    public ResponseEntity<Result<CallSceneErrorVO>> handleCallSceneException(CallSceneException exception) {
        Result<CallSceneErrorVO> result = Result.error(exception.getStatus(), exception.getMessage());
        result.setData(new CallSceneErrorVO(exception.getErrorCode(), exception.getMessage()));
        return ResponseEntity.status(exception.getStatus()).body(result);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<CallSceneErrorVO>> handleDataIntegrityViolation() {
        String message = "场景编码已存在或数据约束冲突";
        Result<CallSceneErrorVO> result = Result.error(HttpStatus.CONFLICT.value(), message);
        result.setData(new CallSceneErrorVO("CALL_SCENE_CONSTRAINT_VIOLATION", message));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(result);
    }
}
