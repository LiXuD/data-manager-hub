package com.dataplatform.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.common.entity.DataType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DataTypeMapper extends BaseMapper<DataType> {
    
    /**
     * 根据数据类型编码查询单价
     */
    @Select("SELECT unit_price FROM data_type " +
            "WHERE data_type_code = #{dataTypeCode} " +
            "AND status = 'active' " +
            "AND deleted = false")
    java.math.BigDecimal selectUnitPriceByCode(@Param("dataTypeCode") String dataTypeCode);
    
    /**
     * 批量查询数据类型单价
     */
    @Select("<script>" +
            "SELECT data_type_code, unit_price FROM data_type " +
            "WHERE data_type_code IN " +
            "<foreach collection='dataTypeCodes' item='code' open='(' separator=',' close=')'>" +
            "#{code}" +
            "</foreach>" +
            "AND status = 'active' " +
            "AND deleted = false" +
            "</script>")
    List<Map<String, Object>> selectUnitPriceBatch(@Param("dataTypeCodes") List<String> dataTypeCodes);
}