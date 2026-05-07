package com.dataplatform.call.service;

import com.dataplatform.call.service.ParamMappingProcessor.MappingEntry;
import com.dataplatform.call.service.ParamMappingProcessor.ParamDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ParamMappingProcessorTest {

    private ParamMappingProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ParamMappingProcessor();
    }

    @Test
    void shouldUseTargetFieldWhenMappingExists() {
        List<ParamDefinition> defs = new ArrayList<>();
        ParamDefinition nameDef = new ParamDefinition();
        nameDef.setParamName("name");
        nameDef.setParamType("string");
        nameDef.setRequired(true);
        defs.add(nameDef);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "阿里巴巴");

        String mappingJson = "[{\"paramName\":\"name\",\"targetField\":\"ent_name\",\"transformExpr\":null}]";

        Map<String, Object> result = processor.buildVendorRequest(defs, mappingJson, input);

        assertEquals("阿里巴巴", result.get("ent_name"));
        assertNull(result.get("name"));
    }

    @Test
    void shouldUseOriginalParamNameWhenNoMapping() {
        List<ParamDefinition> defs = new ArrayList<>();
        ParamDefinition pageDef = new ParamDefinition();
        pageDef.setParamName("page");
        pageDef.setParamType("number");
        pageDef.setRequired(false);
        defs.add(pageDef);

        Map<String, Object> input = new HashMap<>();
        input.put("page", 1);

        String mappingJson = null;

        Map<String, Object> result = processor.buildVendorRequest(defs, mappingJson, input);

        assertEquals(1, result.get("page"));
    }

    @Test
    void shouldUseDefaultValueWhenInputMissing() {
        List<ParamDefinition> defs = new ArrayList<>();
        ParamDefinition modeDef = new ParamDefinition();
        modeDef.setParamName("mode");
        modeDef.setParamType("string");
        modeDef.setRequired(false);
        modeDef.setDefaultValue("exact");
        defs.add(modeDef);

        Map<String, Object> input = new HashMap<>();

        Map<String, Object> result = processor.buildVendorRequest(defs, null, input);

        assertEquals("exact", result.get("mode"));
    }

    @Test
    void shouldThrowWhenRequiredParamMissing() {
        List<ParamDefinition> defs = new ArrayList<>();
        ParamDefinition nameDef = new ParamDefinition();
        nameDef.setParamName("name");
        nameDef.setParamType("string");
        nameDef.setRequired(true);
        defs.add(nameDef);

        Map<String, Object> input = new HashMap<>();

        assertThrows(IllegalArgumentException.class, () ->
                processor.buildVendorRequest(defs, null, input));
    }

    @Test
    void shouldMixMappedAndUnmappedFields() {
        List<ParamDefinition> defs = new ArrayList<>();
        ParamDefinition nameDef = new ParamDefinition();
        nameDef.setParamName("name");
        nameDef.setParamType("string");
        defs.add(nameDef);

        ParamDefinition pageDef = new ParamDefinition();
        pageDef.setParamName("page");
        pageDef.setParamType("number");
        defs.add(pageDef);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "阿里巴巴");
        input.put("page", 1);

        String mappingJson = "[{\"paramName\":\"name\",\"targetField\":\"ent_name\"}]";

        Map<String, Object> result = processor.buildVendorRequest(defs, mappingJson, input);

        assertEquals("阿里巴巴", result.get("ent_name"));
        assertEquals(1, result.get("page"));
        assertEquals(2, result.size());
    }

    @Test
    void shouldHandleEmptyMappingJson() {
        List<ParamDefinition> defs = new ArrayList<>();
        ParamDefinition nameDef = new ParamDefinition();
        nameDef.setParamName("name");
        nameDef.setParamType("string");
        defs.add(nameDef);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "test");

        Map<String, Object> result = processor.buildVendorRequest(defs, "", input);
        assertEquals("test", result.get("name"));

        result = processor.buildVendorRequest(defs, "[]", input);
        assertEquals("test", result.get("name"));
    }
}
