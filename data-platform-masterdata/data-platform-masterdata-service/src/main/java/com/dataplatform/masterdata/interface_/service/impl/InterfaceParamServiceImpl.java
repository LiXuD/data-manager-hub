package com.dataplatform.masterdata.interface_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.masterdata.interface_.entity.InterfaceParam;
import com.dataplatform.masterdata.interface_.mapper.InterfaceParamMapper;
import com.dataplatform.masterdata.interface_.service.InterfaceParamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 接口参数定义服务实现
 */
@Service
public class InterfaceParamServiceImpl extends ServiceImpl<InterfaceParamMapper, InterfaceParam> implements InterfaceParamService {

    @Override
    public List<InterfaceParam> listByInterfaceId(Long interfaceId) {
        LambdaQueryWrapper<InterfaceParam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceParam::getInterfaceId, interfaceId);
        wrapper.orderByAsc(InterfaceParam::getDirection)
                .orderByAsc(InterfaceParam::getParentId)
                .orderByAsc(InterfaceParam::getSort)
                .orderByAsc(InterfaceParam::getId);
        return this.list(wrapper);
    }

    @Override
    @Transactional
    public void batchSave(Long interfaceId, List<InterfaceParam> params) {
        if (interfaceId == null) {
            throw new IllegalArgumentException("接口ID不能为空");
        }
        // 删除旧参数
        LambdaQueryWrapper<InterfaceParam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceParam::getInterfaceId, interfaceId)
                .and(condition -> condition.eq(InterfaceParam::getDirection, "REQUEST")
                        .or().isNull(InterfaceParam::getDirection));
        this.remove(wrapper);

        // 保存新参数
        if (params != null && !params.isEmpty()) {
            for (InterfaceParam param : params) {
                if (param == null) {
                    throw new IllegalArgumentException("接口参数不能为空");
                }
                param.setInterfaceId(interfaceId);
                param.setDirection("REQUEST");
                param.setParentId(null);
            }
            if (!this.saveBatch(params)) {
                throw new IllegalStateException("接口参数保存失败，请重试");
            }
        }
    }

    @Override
    public InterfaceParam getByInterfaceIdAndParamName(Long interfaceId, String paramName) {
        LambdaQueryWrapper<InterfaceParam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceParam::getInterfaceId, interfaceId)
               .eq(InterfaceParam::getParamName, paramName)
               .isNull(InterfaceParam::getParentId)
               .and(condition -> condition.eq(InterfaceParam::getDirection, "REQUEST")
                       .or().isNull(InterfaceParam::getDirection));
        return this.getOne(wrapper);
    }
}
