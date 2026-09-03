package com.dataplatform.access.caller.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.access.caller.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 访问域调用方的 Api Key Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {

    @Update("""
            UPDATE api_key
               SET quota_used = COALESCE(quota_used, 0) + #{count},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND deleted = FALSE
               AND status = 'active'
               AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP)
               AND quota_limit > 0
               AND COALESCE(quota_used, 0) >= 0
               AND #{count} <= quota_limit - COALESCE(quota_used, 0)
            """)
    int consumeQuota(@Param("id") Long id, @Param("count") long count);
}
