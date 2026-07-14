package com.dataplatform.common.adapter;

import java.util.HashMap;
import java.util.Map;

public class MockVendorAdapter extends AbstractVendorAdapter {

    private final String vendorCode;

    public MockVendorAdapter(String vendorCode) {
        this.vendorCode = vendorCode;
    }

    @Override
    public String getVendorCode() {
        return vendorCode;
    }

    @Override
    public boolean supports(String dataTypeCode) {
        return true;
    }

    @Override
    public Map<String, Object> execute(VendorAdapterConfig config, Map<String, Object> params) {
        Map<String, Object> request = transformRequest(params, config != null ? config.getRequestTemplate() : null);
        Map<String, Object> vendorResponse = new HashMap<>();
        vendorResponse.put("matched", true);
        vendorResponse.put("riskLevel", "LOW");
        vendorResponse.put("score", 99);
        vendorResponse.put("vendorCode", vendorCode);
        vendorResponse.put("dataTypeCode", config != null ? config.getDataTypeCode() : null);
        vendorResponse.put("echo", request);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", transformResponse(vendorResponse, config != null ? config.getResponseMapping() : null));
        result.put("rawResponse", vendorResponse);
        result.put("latency", 0);
        return result;
    }
}
