package com.dataplatform.masterdata.vendor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.masterdata.vendor.entity.VendorExtendedConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 主数据域厂商的 Vendor Extended Config Mapper。
 * <p>MyBatis-Plus 数据访问接口，封装对应表的持久化操作。</p>
 */
@Mapper
public interface VendorExtendedConfigMapper extends BaseMapper<VendorExtendedConfig> {
}
