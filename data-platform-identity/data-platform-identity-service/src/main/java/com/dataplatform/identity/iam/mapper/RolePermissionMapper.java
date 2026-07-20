package com.dataplatform.identity.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.identity.iam.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 身份租户域用户权限的 Role Permission Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
