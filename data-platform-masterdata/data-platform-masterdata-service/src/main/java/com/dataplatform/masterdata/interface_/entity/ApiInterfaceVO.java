package com.dataplatform.masterdata.interface_.entity;

/**
 * 接口视图对象 - 包含关联的厂商名称和数据类型名称
 */
public class ApiInterfaceVO extends ApiInterface {

    private String vendorName;
    private String dataTypeName;

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getDataTypeName() {
        return dataTypeName;
    }

    public void setDataTypeName(String dataTypeName) {
        this.dataTypeName = dataTypeName;
    }
}
