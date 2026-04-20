package com.dataplatform.vendor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.vendor.entity.VendorInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VendorMapper extends BaseMapper<VendorInfo> {
}
