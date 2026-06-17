package com.dataplatform.access.call.controller;

import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.service.CallSceneService;
import com.dataplatform.common.constant.StatusConstants;
import com.dataplatform.common.result.Result;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
        return Result.success(callSceneService.list());
    }

    @PostMapping
    public ResponseEntity<Result<CallScene>> create(@RequestBody CallScene scene) {
        if (scene.getSceneCode() == null || scene.getSceneCode().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "sceneCode不能为空"));
        }
        if (scene.getSceneName() == null || scene.getSceneName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "sceneName不能为空"));
        }
        scene.setId(null);
        if (scene.getStatus() == null || scene.getStatus().trim().isEmpty()) {
            scene.setStatus(StatusConstants.ACTIVE);
        }
        callSceneService.save(scene);
        return ResponseEntity.ok(Result.success(scene));
    }
}
