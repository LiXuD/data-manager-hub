package com.dataplatform.access.call.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.access.call.entity.CallScene;
import org.apache.ibatis.annotations.Mapper;

/**
 * 访问域数据调用的 Call Scene Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface CallSceneMapper extends BaseMapper<CallScene> {
}
