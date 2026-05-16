package com.dataplatform.identity.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.identity.iam.entity.UserCaller;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserCallerMapper extends BaseMapper<UserCaller> {
}
