package com.dataplatform.identity.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.identity.iam.entity.UserCaller;
import com.dataplatform.identity.iam.mapper.UserCallerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 身份租户域用户权限的 User Caller Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class UserCallerService extends ServiceImpl<UserCallerMapper, UserCaller> {

    public List<Long> getCallerIdsByUserId(Long userId) {
        LambdaQueryWrapper<UserCaller> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCaller::getUserId, userId);
        return list(wrapper).stream()
                .map(UserCaller::getCallerId)
                .collect(Collectors.toList());
    }

    @Transactional
    public void assignCallers(Long userId, List<Long> callerIds) {
        LambdaQueryWrapper<UserCaller> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCaller::getUserId, userId);
        remove(wrapper);
        if (callerIds != null && !callerIds.isEmpty()) {
            List<UserCaller> userCallers = callerIds.stream()
                    .map(callerId -> {
                        UserCaller uc = new UserCaller();
                        uc.setUserId(userId);
                        uc.setCallerId(callerId);
                        return uc;
                    })
                    .collect(Collectors.toList());
            saveBatch(userCallers);
        }
    }
}
