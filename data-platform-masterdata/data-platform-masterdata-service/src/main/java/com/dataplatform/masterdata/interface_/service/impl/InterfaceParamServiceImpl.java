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
        // 删除旧参数
        LambdaQueryWrapper<InterfaceParam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceParam::getInterfaceId, interfaceId)
                .and(condition -> condition.eq(InterfaceParam::getDirection, "REQUEST")
                        .or().isNull(InterfaceParam::getDirection));
        this.remove(wrapper);

        // 保存新参数
        if (params != null && !params.isEmpty()) {
            for (InterfaceParam param : params) {
                param.setInterfaceId(interfaceId);
                param.setDirection("REQUEST");
                param.setParentId(null);
            }
            this.saveBatch(params);
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
