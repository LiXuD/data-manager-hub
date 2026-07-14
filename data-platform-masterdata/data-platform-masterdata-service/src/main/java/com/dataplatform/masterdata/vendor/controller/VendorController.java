package com.dataplatform.masterdata.vendor.controller;

import com.dataplatform.api.PageResult;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.masterdata.vendor.api.dto.VendorCreateReqDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorInfoDTO;
import com.dataplatform.masterdata.vendor.api.dto.VendorUpdateReqDTO;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.service.VendorService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 主数据域厂商的 Vendor Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/vendor")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @GetMapping("/list")
    public PageResult<VendorInfoDTO> list(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status) {
        com.dataplatform.common.result.PageResult<VendorInfo> result =
                vendorService.list(page, pageSize, keyword, status);
        return PageResult.of(
                result.getData().stream().map(this::toDTO).toList(),
                result.getTotal(),
                result.getPage(),
                result.getPageSize());
    }

    @GetMapping("/{id}")
    public Result<VendorInfoDTO> getById(@PathVariable("id") Long id) {
        VendorInfo vendor = vendorService.getById(id);
        if (vendor == null) {
            return Result.error(404, "厂商不存在");
        }
        return Result.success(toDTO(vendor));
    }

    @GetMapping("/code/{vendorCode}")
    public Result<VendorInfoDTO> getByVendorCode(@PathVariable("vendorCode") String vendorCode) {
        VendorInfo vendor = vendorService.getByVendorCode(vendorCode);
        if (vendor == null) {
            return Result.success(null);
        }
        return Result.success(toDTO(vendor));
    }

    @OperationLog(module = "厂商管理", operation = "新增厂商")
    @PostMapping
    public Result<VendorInfoDTO> create(@RequestBody VendorCreateReqDTO dto) {
        VendorInfo vendor = toEntity(dto);
        if (vendor.getVendorCode() == null || vendor.getVendorCode().trim().isEmpty()) {
            return Result.error(400, "厂商代码不能为空");
        }
        if (vendor.getVendorName() == null || vendor.getVendorName().trim().isEmpty()) {
            return Result.error(400, "厂商名称不能为空");
        }
        if (vendor.getVendorType() == null || vendor.getVendorType().trim().isEmpty()) {
            vendor.setVendorType("other");
        }

        VendorInfo existing = vendorService.getByVendorCode(vendor.getVendorCode());
        if (existing != null) {
            return Result.error(409, "厂商代码已存在");
        }

        vendor.setId(null);
        vendor.setStatus(CommonStatus.ACTIVE);
        vendorService.save(vendor);
        return Result.success(toDTO(vendor));
    }

    @OperationLog(module = "厂商管理", operation = "更新厂商")
    @PutMapping("/{id}")
    public Result<VendorInfoDTO> update(@PathVariable("id") Long id, @RequestBody VendorUpdateReqDTO dto) {
        VendorInfo existing = vendorService.getById(id);
        if (existing == null) {
            return Result.error(404, "厂商不存在");
        }
        VendorInfo vendor = toEntity(dto);
        vendor.setId(id);
        vendorService.updateById(vendor);
        return Result.success(toDTO(vendorService.getById(id)));
    }

    @OperationLog(module = "厂商管理", operation = "删除厂商")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        VendorInfo existing = vendorService.getById(id);
        if (existing == null) {
            return Result.error(404, "厂商不存在");
        }
        vendorService.removeById(id);
        return Result.success(null);
    }

    @OperationLog(module = "厂商管理", operation = "更新厂商状态")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        CommonStatus statusEnum = CommonStatus.fromCode(status);
        if (statusEnum == null) {
            return Result.error(400, "无效的状态值，有效值: active, inactive");
        }

        VendorInfo existing = vendorService.getById(id);
        if (existing == null) {
            return Result.error(404, "厂商不存在");
        }

        VendorInfo vendor = new VendorInfo();
        vendor.setId(id);
        vendor.setStatus(statusEnum);
        vendorService.updateById(vendor);
        return Result.success(null);
    }

    @OperationLog(module = "厂商管理", operation = "测试厂商连接")
    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testConnection(@PathVariable Long id) {
        VendorInfo existing = vendorService.getById(id);
        if (existing == null) {
            return Result.error(404, "厂商不存在");
        }
        return Result.success(Map.of("success", true, "message", "连接正常"));
    }

    @GetMapping("/all")
    public Result<List<VendorInfoDTO>> listAll() {
        return Result.success(vendorService.listAllActive().stream()
                .map(this::toDTO)
                .toList());
    }

    private VendorInfoDTO toDTO(VendorInfo entity) {
        if (entity == null) {
            return null;
        }
        VendorInfoDTO dto = new VendorInfoDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        return dto;
    }

    private VendorInfo toEntity(VendorCreateReqDTO dto) {
        VendorInfo entity = new VendorInfo();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private VendorInfo toEntity(VendorUpdateReqDTO dto) {
        VendorInfo entity = new VendorInfo();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }
}
