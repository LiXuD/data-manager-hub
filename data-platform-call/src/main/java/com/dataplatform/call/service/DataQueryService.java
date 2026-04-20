package com.dataplatform.call.service;

import java.util.Map;

public interface DataQueryService {
    
    Map<String, Object> queryData(String vendorCode, String dataType, Map<String, Object> params);
}
