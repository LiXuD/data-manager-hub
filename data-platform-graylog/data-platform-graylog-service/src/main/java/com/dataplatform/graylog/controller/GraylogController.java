package com.dataplatform.graylog.controller;

import com.dataplatform.common.enums.GrayRuleStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.common.result.PageResult;
import com.dataplatform.common.result.Result;
import com.dataplatform.graylog.entity.GrayRule;
import com.dataplatform.graylog.service.GraylogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/graylog")
public class GraylogController {
    @Autowired
    private GraylogService graylogService;

    @GetMapping("/list")
    public PageResult<GrayRule> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return graylogService.list(keyword, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<GrayRule>> get(@PathVariable Long id) {
        GrayRule rule = graylogService.getById(id);
        if (rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "灰度规则不存在"));
        }
        return ResponseEntity.ok(Result.success(rule));
    }

    @OperationLog(module = "灰度规则管理", operation = "新增灰度规则")
    @PostMapping
    public ResponseEntity<Result<GrayRule>> create(@RequestBody GrayRule rule) {
        // 验证必填参数
        if (rule.getRuleName() == null || rule.getRuleName().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "ruleName不能为空"));
        }
        rule.setId(null);
        rule.setStatus(GrayRuleStatus.ACTIVE);
        graylogService.save(rule);
        return ResponseEntity.ok(Result.success(rule));
    }

    @OperationLog(module = "灰度规则管理", operation = "更新灰度规则")
    @PutMapping("/{id}")
    public ResponseEntity<Result<GrayRule>> update(@PathVariable Long id, @RequestBody GrayRule rule) {
        GrayRule existing = graylogService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "灰度规则不存在"));
        }
        rule.setId(id);
        graylogService.updateById(rule);
        return ResponseEntity.ok(Result.success(graylogService.getById(id)));
    }

    @OperationLog(module = "灰度规则管理", operation = "删除灰度规则")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        GrayRule existing = graylogService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "灰度规则不存在"));
        }
        graylogService.removeById(id);
        return ResponseEntity.ok(Result.success(null));
    }

    @GetMapping("/active/{serviceName}")
    public ResponseEntity<Result<GrayRule>> getActiveRule(@PathVariable String serviceName) {
        GrayRule rule = graylogService.getActiveRule(serviceName);
        if (rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "服务无活跃灰度规则"));
        }
        return ResponseEntity.ok(Result.success(rule));
    }

    @OperationLog(module = "灰度规则管理", operation = "更新灰度规则状态")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Result<Void>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");

        // 验证状态值
        GrayRuleStatus statusEnum = GrayRuleStatus.fromCode(status);
        if (statusEnum == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(400, "无效的状态值，有效值: active, inactive, expired, pending"));
        }

        GrayRule existing = graylogService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(404, "灰度规则不存在"));
        }

        GrayRule rule = new GrayRule();
        rule.setId(id);
        rule.setStatus(statusEnum);
        graylogService.updateById(rule);
        return ResponseEntity.ok(Result.success(null));
    }
}
