package com.dataplatform.masterdata.graylog.controller;

import com.dataplatform.api.PageResult;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.GrayRuleStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.masterdata.graylog.api.dto.GrayRuleCreateReqDTO;
import com.dataplatform.masterdata.graylog.api.dto.GrayRuleDTO;
import com.dataplatform.masterdata.graylog.api.dto.GrayRuleUpdateReqDTO;
import com.dataplatform.masterdata.graylog.api.feign.GraylogFeignClient;
import com.dataplatform.masterdata.graylog.entity.GrayRule;
import com.dataplatform.masterdata.graylog.service.GraylogService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 主数据域灰度规则的 Graylog Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/graylog")
public class GraylogController implements GraylogFeignClient {

    private final GraylogService graylogService;

    public GraylogController(GraylogService graylogService) {
        this.graylogService = graylogService;
    }

    @Override
    @GetMapping("/list")
    public PageResult<GrayRuleDTO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        com.dataplatform.common.result.PageResult<GrayRule> result =
                graylogService.list(keyword, status, page, pageSize);
        return PageResult.of(
                result.getData().stream().map(this::toDTO).toList(),
                result.getTotal(),
                result.getPage(),
                result.getPageSize());
    }

    @Override
    @GetMapping("/{id}")
    public Result<GrayRuleDTO> get(@PathVariable("id") Long id) {
        GrayRule rule = graylogService.getById(id);
        if (rule == null) {
            return Result.error(404, "灰度规则不存在");
        }
        return Result.success(toDTO(rule));
    }

    @Override
    @OperationLog(module = "灰度规则管理", operation = "新增灰度规则")
    @PostMapping
    public Result<GrayRuleDTO> create(@RequestBody GrayRuleCreateReqDTO dto) {
        GrayRule rule = toEntity(dto);
        if (rule.getRuleName() == null || rule.getRuleName().isEmpty()) {
            return Result.error(400, "ruleName不能为空");
        }
        rule.setId(null);
        if (rule.getStatus() == null) {
            rule.setStatus(GrayRuleStatus.ACTIVE);
        }
        graylogService.save(rule);
        return Result.success(toDTO(rule));
    }

    @Override
    @OperationLog(module = "灰度规则管理", operation = "更新灰度规则")
    @PutMapping("/{id}")
    public Result<GrayRuleDTO> update(@PathVariable("id") Long id, @RequestBody GrayRuleUpdateReqDTO dto) {
        GrayRule existing = graylogService.getById(id);
        if (existing == null) {
            return Result.error(404, "灰度规则不存在");
        }
        GrayRule rule = toEntity(dto);
        rule.setId(id);
        graylogService.updateById(rule);
        return Result.success(toDTO(graylogService.getById(id)));
    }

    @Override
    @OperationLog(module = "灰度规则管理", operation = "删除灰度规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        GrayRule existing = graylogService.getById(id);
        if (existing == null) {
            return Result.error(404, "灰度规则不存在");
        }
        graylogService.removeById(id);
        return Result.success(null);
    }

    @Override
    @GetMapping("/active/{serviceName}")
    public Result<GrayRuleDTO> getActiveRule(@PathVariable("serviceName") String serviceName) {
        GrayRule rule = graylogService.getActiveRule(serviceName);
        if (rule == null) {
            return Result.error(404, "服务无活跃灰度规则");
        }
        return Result.success(toDTO(rule));
    }

    @Override
    @OperationLog(module = "灰度规则管理", operation = "更新灰度规则状态")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        GrayRuleStatus status = GrayRuleStatus.fromCode(body.get("status"));
        if (status == null) {
            return Result.error(400, "无效的状态值，有效值: active, inactive, expired, pending");
        }

        GrayRule existing = graylogService.getById(id);
        if (existing == null) {
            return Result.error(404, "灰度规则不存在");
        }

        GrayRule rule = new GrayRule();
        rule.setId(id);
        rule.setStatus(status);
        graylogService.updateById(rule);
        return Result.success(null);
    }

    private GrayRuleDTO toDTO(GrayRule entity) {
        if (entity == null) {
            return null;
        }
        GrayRuleDTO dto = new GrayRuleDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        return dto;
    }

    private GrayRule toEntity(GrayRuleCreateReqDTO dto) {
        GrayRule entity = new GrayRule();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getStatus() != null) {
            entity.setStatus(GrayRuleStatus.fromCode(dto.getStatus()));
        }
        return entity;
    }

    private GrayRule toEntity(GrayRuleUpdateReqDTO dto) {
        GrayRule entity = new GrayRule();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getStatus() != null) {
            entity.setStatus(GrayRuleStatus.fromCode(dto.getStatus()));
        }
        return entity;
    }
}
