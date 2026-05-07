package com.dataplatform.interface_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.interface_.entity.InterfaceParam;
import com.dataplatform.interface_.mapper.InterfaceParamMapper;
import com.dataplatform.interface_.service.InterfaceParamService;
import org.springframework.stereotype.Service;

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
        wrapper.orderByAsc(InterfaceParam::getSort);
        return this.list(wrapper);
    }

    @Override
    public void batchSave(Long interfaceId, List<InterfaceParam> params) {
        // 删除旧参数
        LambdaQueryWrapper<InterfaceParam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceParam::getInterfaceId, interfaceId);
        this.remove(wrapper);

        // 保存新参数
        if (params != null && !params.isEmpty()) {
            for (InterfaceParam param : params) {
                param.setInterfaceId(interfaceId);
            }
            this.saveBatch(params);
        }
    }

    @Override
    public InterfaceParam getByInterfaceIdAndParamName(Long interfaceId, String paramName) {
        LambdaQueryWrapper<InterfaceParam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceParam::getInterfaceId, interfaceId)
               .eq(InterfaceParam::getParamName, paramName);
        return this.getOne(wrapper);
    }
}
