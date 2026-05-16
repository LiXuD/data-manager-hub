package com.dataplatform.masterdata.interface_.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.entity.ApiInterfaceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApiInterfaceMapper extends BaseMapper<ApiInterface> {

    IPage<ApiInterfaceVO> selectListWithNames(
            Page<ApiInterfaceVO> page,
            @Param("vendorId") Long vendorId,
            @Param("dataTypeId") Long dataTypeId,
            @Param("status") String status);
}
