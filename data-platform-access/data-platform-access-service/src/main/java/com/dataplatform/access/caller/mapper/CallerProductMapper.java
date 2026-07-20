package com.dataplatform.access.caller.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.access.caller.entity.CallerProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 访问域调用方的 Caller Product Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface CallerProductMapper extends BaseMapper<CallerProduct> {
}
