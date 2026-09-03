package com.dataplatform.masterdata.vendor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.api.PageResult;
import com.dataplatform.api.Result;
import com.dataplatform.common.enums.CommonStatus;
import com.dataplatform.common.log.OperationLog;
import com.dataplatform.masterdata.datatype.api.dto.DataTypeCreateReqDTO;
import com.dataplatform.masterdata.datatype.api.dto.DataTypeDTO;
import com.dataplatform.masterdata.datatype.api.dto.DataTypeUpdateReqDTO;
import com.dataplatform.masterdata.vendor.entity.DataType;
import com.dataplatform.masterdata.vendor.mapper.DataTypeMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 主数据域厂商的 Data Type Controller。
 * <p>HTTP 接口控制器，负责接收请求、组织参数并委托本域业务服务处理。</p>
 */
@RestController
@RequestMapping("/datatype")
public class DataTypeController {

    private final DataTypeMapper dataTypeMapper;

    public DataTypeController(DataTypeMapper dataTypeMapper) {
        this.dataTypeMapper = dataTypeMapper;
    }

    @GetMapping("/list")
    public PageResult<DataTypeDTO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        LambdaQueryWrapper<DataType> wrapper = buildQuery(keyword, status);
        wrapper.orderByDesc(DataType::getCreatedAt);

        Page<DataType> result = dataTypeMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return PageResult.of(
                result.getRecords().stream().map(this::toDTO).toList(),
                result.getTotal(),
                page,
                pageSize);
    }

    @GetMapping("/{id}")
    public Result<DataTypeDTO> get(@PathVariable("id") Long id) {
        DataType dataType = dataTypeMapper.selectById(id);
        if (dataType == null || Boolean.TRUE.equals(dataType.getDeleted())) {
            return Result.error(404, "数据类型不存在");
        }
        return Result.success(toDTO(dataType));
    }

    @GetMapping("/code/{code}")
    public Result<DataTypeDTO> getByCode(@PathVariable("code") String code) {
        DataType dataType = getActiveByCode(code);
        if (dataType == null) {
            return Result.error(404, "数据类型不存在");
        }
        return Result.success(toDTO(dataType));
    }

    @OperationLog(module = "数据类型管理", operation = "新增数据类型")
    @PostMapping
    public Result<DataTypeDTO> create(@RequestBody DataTypeCreateReqDTO dto) {
        if (dto == null) {
            return Result.error(400, "数据类型请求不能为空");
        }
        if (hasInvalidStatus(dto.getStatus())) {
            return Result.error(400, "无效的状态值，有效值: active, inactive");
        }
        DataType dataType = toEntity(dto);
        if (!StringUtils.hasText(dataType.getDataTypeCode())) {
            return Result.error(400, "dataTypeCode不能为空");
        }
        if (!StringUtils.hasText(dataType.getDataTypeName())) {
            return Result.error(400, "dataTypeName不能为空");
        }

        if (getActiveByCode(dataType.getDataTypeCode()) != null) {
            return Result.error(400, "数据类型编码已存在");
        }

        dataType.setId(null);
        if (dataType.getStatus() == null) {
            dataType.setStatus(CommonStatus.ACTIVE);
        }
        dataType.setDeleted(false);
        dataType.setCreatedAt(LocalDateTime.now());
        dataType.setUpdatedAt(LocalDateTime.now());
        if (dataTypeMapper.insert(dataType) <= 0) {
            return Result.error(409, "数据类型保存失败");
        }
        return Result.success(toDTO(dataType));
    }

    @OperationLog(module = "数据类型管理", operation = "更新数据类型")
    @PutMapping("/{id}")
    public Result<DataTypeDTO> update(@PathVariable("id") Long id, @RequestBody DataTypeUpdateReqDTO dto) {
        if (dto == null) {
            return Result.error(400, "数据类型请求不能为空");
        }
        if (hasInvalidStatus(dto.getStatus())) {
            return Result.error(400, "无效的状态值，有效值: active, inactive");
        }
        if (dto.getDataTypeName() != null && !StringUtils.hasText(dto.getDataTypeName())) {
            return Result.error(400, "dataTypeName不能为空");
        }
        DataType existing = dataTypeMapper.selectById(id);
        if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
            return Result.error(404, "数据类型不存在");
        }
        DataType dataType = toEntity(dto);
        dataType.setId(id);
        dataType.setUpdatedAt(LocalDateTime.now());
        if (dataTypeMapper.updateById(dataType) <= 0) {
            return Result.error(409, "数据类型更新失败");
        }
        DataType updated = dataTypeMapper.selectById(id);
        if (updated == null || Boolean.TRUE.equals(updated.getDeleted())) {
            return Result.error(409, "数据类型更新后无法读取");
        }
        return Result.success(toDTO(updated));
    }

    @OperationLog(module = "数据类型管理", operation = "删除数据类型")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        DataType existing = dataTypeMapper.selectById(id);
        if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
            return Result.error(404, "数据类型不存在");
        }
        DataType dataType = new DataType();
        dataType.setId(id);
        dataType.setDeleted(true);
        if (dataTypeMapper.updateById(dataType) <= 0) {
            return Result.error(409, "数据类型删除失败");
        }
        return Result.success(null);
    }

    @OperationLog(module = "数据类型管理", operation = "更新数据类型状态")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        if (body == null) {
            return Result.error(400, "状态请求不能为空");
        }
        CommonStatus status = CommonStatus.fromCode(body.get("status"));
        if (status == null) {
            return Result.error(400, "无效的状态值，有效值: active, inactive");
        }

        DataType existing = dataTypeMapper.selectById(id);
        if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
            return Result.error(404, "数据类型不存在");
        }

        DataType dataType = new DataType();
        dataType.setId(id);
        dataType.setStatus(status);
        dataType.setUpdatedAt(LocalDateTime.now());
        if (dataTypeMapper.updateById(dataType) <= 0) {
            return Result.error(409, "数据类型状态更新失败");
        }
        return Result.success(null);
    }

    @GetMapping("/all")
    public Result<List<DataTypeDTO>> listAll() {
        LambdaQueryWrapper<DataType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataType::getStatus, CommonStatus.ACTIVE.getCode());
        wrapper.eq(DataType::getDeleted, false);
        wrapper.orderByAsc(DataType::getDataTypeCode);
        return Result.success(dataTypeMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .toList());
    }

    private LambdaQueryWrapper<DataType> buildQuery(String keyword, String status) {
        LambdaQueryWrapper<DataType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(DataType::getDataTypeName, keyword)
                    .or()
                    .like(DataType::getDataTypeCode, keyword));
        }
        CommonStatus statusEnum = CommonStatus.fromCode(status);
        if (statusEnum != null) {
            wrapper.eq(DataType::getStatus, statusEnum.getCode());
        }
        wrapper.eq(DataType::getDeleted, false);
        return wrapper;
    }

    private DataType getActiveByCode(String code) {
        LambdaQueryWrapper<DataType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataType::getDataTypeCode, code);
        wrapper.eq(DataType::getStatus, CommonStatus.ACTIVE);
        wrapper.eq(DataType::getDeleted, false);
        return dataTypeMapper.selectOne(wrapper);
    }

    private DataTypeDTO toDTO(DataType entity) {
        if (entity == null) {
            return null;
        }
        DataTypeDTO dto = new DataTypeDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getStatus() != null) {
            dto.setStatus(entity.getStatus().getCode());
        }
        return dto;
    }

    private DataType toEntity(DataTypeCreateReqDTO dto) {
        DataType entity = new DataType();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getDataTypeCode() != null) {
            entity.setDataTypeCode(dto.getDataTypeCode().trim());
        }
        if (dto.getDataTypeName() != null) {
            entity.setDataTypeName(dto.getDataTypeName().trim());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(CommonStatus.fromCode(dto.getStatus().trim()));
        }
        return entity;
    }

    private DataType toEntity(DataTypeUpdateReqDTO dto) {
        DataType entity = new DataType();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getDataTypeName() != null) {
            entity.setDataTypeName(dto.getDataTypeName().trim());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(CommonStatus.fromCode(dto.getStatus().trim()));
        }
        return entity;
    }

    private boolean hasInvalidStatus(String status) {
        return status != null && CommonStatus.fromCode(status.trim()) == null;
    }
}
