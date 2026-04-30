package com.dataplatform.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.iam.entity.Role;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}