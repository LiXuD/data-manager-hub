package com.dataplatform.governance.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.governance.trace.entity.DataLineage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 观测治理域数据血缘的 Data Lineage Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface DataLineageMapper extends BaseMapper<DataLineage> {
}