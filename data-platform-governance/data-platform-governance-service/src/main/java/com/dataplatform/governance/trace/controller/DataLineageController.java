package com.dataplatform.governance.trace.controller;

import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.Result;
import com.dataplatform.governance.trace.entity.DataLineage;
import com.dataplatform.governance.trace.service.DataLineageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 观测治理域数据血缘的 Data Lineage Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/trace/lineage")
public class DataLineageController {

    @Autowired
    private DataLineageService dataLineageService;

    @OperationLog(module = "数据血缘管理", operation = "记录数据血缘")
    @PostMapping
    public ResponseEntity<Result<Boolean>> recordLineage(
            @RequestBody(required = false) DataLineage body,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String targetName,
            @RequestParam(required = false) String relationType,
            @RequestParam(required = false) String transformRule) {
        if (body != null) {
            sourceType = body.getSourceType();
            sourceId = body.getSourceId();
            sourceName = body.getSourceName();
            targetType = body.getTargetType();
            targetId = body.getTargetId();
            targetName = body.getTargetName();
            relationType = body.getRelationType();
            transformRule = body.getTransformRule();
        }
        // 参数验证
        if (sourceType == null || sourceType.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "sourceType不能为空"));
        }
        if (sourceId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "sourceId不能为空"));
        }
        if (sourceName == null || sourceName.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "sourceName不能为空"));
        }
        if (targetType == null || targetType.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "targetType不能为空"));
        }
        if (targetId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "targetId不能为空"));
        }
        if (targetName == null || targetName.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "targetName不能为空"));
        }

        boolean result = dataLineageService.recordLineage(
            sourceType, sourceId, sourceName,
            targetType, targetId, targetName,
            relationType, transformRule
        );
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/upstream")
    public ResponseEntity<Result<List<DataLineage>>> getUpstream(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long id) {
        if (type == null || type.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "type不能为空"));
        }
        if (id == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "id不能为空"));
        }

        List<DataLineage> result = dataLineageService.getUpstream(type, id);
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/downstream")
    public ResponseEntity<Result<List<DataLineage>>> getDownstream(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long id) {
        if (type == null || type.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "type不能为空"));
        }
        if (id == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "id不能为空"));
        }

        List<DataLineage> result = dataLineageService.getDownstream(type, id);
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/full")
    public ResponseEntity<Result<List<DataLineage>>> getFullLineage(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long id) {
        if (type == null || type.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "type不能为空"));
        }
        if (id == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "id不能为空"));
        }

        List<DataLineage> result = dataLineageService.getFullLineage(type, id);
        return ResponseEntity.ok(Result.success(result));
    }
}
