package com.dataplatform.access.call.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.mapper.CallSceneMapper;
import com.dataplatform.common.constant.StatusConstants;
import org.springframework.stereotype.Service;

@Service
public class CallSceneService extends ServiceImpl<CallSceneMapper, CallScene> {

    public CallScene getActiveScene(String sceneCode) {
        if (sceneCode == null || sceneCode.trim().isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<CallScene> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CallScene::getSceneCode, sceneCode.trim())
                .eq(CallScene::getStatus, StatusConstants.ACTIVE)
                .last("LIMIT 1");
        return getOne(wrapper, false);
    }
}
