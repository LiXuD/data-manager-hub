package com.dataplatform.masterdata.vendor.controller;

import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.vendor.entity.DataType;
import com.dataplatform.masterdata.vendor.entity.VendorInfo;
import com.dataplatform.masterdata.vendor.mapper.DataTypeMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorConfigMapper;
import com.dataplatform.masterdata.vendor.mapper.VendorInfoMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataQueryControllerTest {

    @Mock
    private VendorInfoMapper vendorInfoMapper;
    @Mock
    private DataTypeMapper dataTypeMapper;
    @Mock
    private VendorConfigMapper vendorConfigMapper;
    @Mock
    private ApiInterfaceService apiInterfaceService;

    @Test
    void shouldRejectUnknownInterfaceInsteadOfDroppingInterfaceFilter() {
        VendorInfo vendor = new VendorInfo();
        vendor.setId(1L);
        DataType dataType = new DataType();
        dataType.setId(2L);
        when(vendorInfoMapper.selectOne(any())).thenReturn(vendor);
        when(dataTypeMapper.selectOne(any())).thenReturn(dataType);
        when(apiInterfaceService.getByInterfaceCode("missing-interface")).thenReturn(null);

        DataQueryController controller = new DataQueryController();
        ReflectionTestUtils.setField(controller, "vendorInfoMapper", vendorInfoMapper);
        ReflectionTestUtils.setField(controller, "dataTypeMapper", dataTypeMapper);
        ReflectionTestUtils.setField(controller, "vendorConfigMapper", vendorConfigMapper);
        ReflectionTestUtils.setField(controller, "apiInterfaceService", apiInterfaceService);

        Map<String, Object> result = controller.query(Map.of(
                "vendorCode", "VENDOR_A",
                "dataTypeCode", "PERSONAL",
                "interfaceCode", "missing-interface"));

        assertEquals("INTERFACE_NOT_FOUND", result.get("errorCode"));
        verify(vendorConfigMapper, never()).selectOne(any());
    }
}
