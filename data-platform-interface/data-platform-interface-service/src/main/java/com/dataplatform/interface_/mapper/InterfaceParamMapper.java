package com.dataplatform.interface_.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.interface_.entity.InterfaceParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 接口参数定义 Mapper
 */
@Mapper
public interface InterfaceParamMapper extends BaseMapper<InterfaceParam> {

    /**
     * 根据接口ID查询所有参数定义，按sort排序
     */
    @Select("SELECT * FROM interface_param WHERE interface_id = #{interfaceId} ORDER BY sort ASC, id ASC")
    List<InterfaceParam> selectByInterfaceId(@Param("interfaceId") Long interfaceId);

    /**
     * 根据接口ID和参数名查询
     */
    @Select("SELECT * FROM interface_param WHERE interface_id = #{interfaceId} AND param_name = #{paramName}")
    InterfaceParam selectByInterfaceIdAndParamName(@Param("interfaceId") Long interfaceId,
                                                    @Param("paramName") String paramName);
}
