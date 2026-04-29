package com.dataplatform.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.role.entity.Role;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}