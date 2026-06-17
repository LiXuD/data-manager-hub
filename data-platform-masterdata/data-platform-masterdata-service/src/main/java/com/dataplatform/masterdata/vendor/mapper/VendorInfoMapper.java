package com.dataplatform.masterdata.vendor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 主数据域厂商的 Vendor Info Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface VendorInfoMapper extends BaseMapper<VendorInfo> {
}