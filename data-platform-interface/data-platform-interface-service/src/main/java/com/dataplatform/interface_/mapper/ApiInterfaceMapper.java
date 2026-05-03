package com.dataplatform.interface_.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.interface_.entity.ApiInterface;
import com.dataplatform.interface_.entity.ApiInterfaceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApiInterfaceMapper extends BaseMapper<ApiInterface> {

    @Select("""
        <script>
        SELECT i.*, v.vendor_name, dt.data_type_name
        FROM api_interface i
        LEFT JOIN vendor_info v ON i.vendor_id = v.id
        LEFT JOIN data_type dt ON i.data_type_id = dt.id
        WHERE i.deleted = false
        <if test="vendorId != null">
            AND i.vendor_id = #{vendorId}
        </if>
        <if test="dataTypeId != null">
            AND i.data_type_id = #{dataTypeId}
        </if>
        <if test="status != null">
            AND i.status = #{status}
        </if>
        ORDER BY i.sort ASC, i.created_at DESC
        </script>
    """)
    List<ApiInterfaceVO> selectListWithNames(
            @Param("vendorId") Long vendorId,
            @Param("dataTypeId") Long dataTypeId,
            @Param("status") String status,
            Page<ApiInterfaceVO> page);
}
