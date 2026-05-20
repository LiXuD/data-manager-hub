package com.dataplatform.governance.trace.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dataplatform.governance.trace.service.DataLineageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DataLineageControllerTest {

    @Test
    void recordsLineageFromJsonBody() throws Exception {
        CapturingDataLineageService service = new CapturingDataLineageService();
        DataLineageController controller = new DataLineageController();
        ReflectionTestUtils.setField(controller, "dataLineageService", service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/trace/lineage")
                .contentType("application/json")
                .content("""
                    {
                      "sourceType": "vendor",
                      "sourceId": 1,
                      "sourceName": "Vendor A",
                      "targetType": "interface",
                      "targetId": 2,
                      "targetName": "Interface B",
                      "relationType": "PROVIDES",
                      "transformRule": "SkyWalking traceId=trace-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));

        assertEquals("vendor", service.sourceType);
        assertEquals(1L, service.sourceId);
        assertEquals("Vendor A", service.sourceName);
        assertEquals("interface", service.targetType);
        assertEquals(2L, service.targetId);
        assertEquals("Interface B", service.targetName);
        assertEquals("PROVIDES", service.relationType);
        assertEquals("SkyWalking traceId=trace-1", service.transformRule);
    }

    private static final class CapturingDataLineageService extends DataLineageService {
        private String sourceType;
        private Long sourceId;
        private String sourceName;
        private String targetType;
        private Long targetId;
        private String targetName;
        private String relationType;
        private String transformRule;

        @Override
        public boolean recordLineage(String sourceType, Long sourceId, String sourceName,
                                     String targetType, Long targetId, String targetName,
                                     String relationType, String transformRule) {
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.sourceName = sourceName;
            this.targetType = targetType;
            this.targetId = targetId;
            this.targetName = targetName;
            this.relationType = relationType;
            this.transformRule = transformRule;
            return true;
        }
    }
}
