package com.dataplatform.masterdata.interface_.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;
import com.dataplatform.masterdata.interface_.api.dto.InterfaceParamDTO;
import com.dataplatform.masterdata.interface_.entity.ApiInterface;
import com.dataplatform.masterdata.interface_.entity.InterfaceParam;
import com.dataplatform.masterdata.interface_.service.ApiInterfaceService;
import com.dataplatform.masterdata.interface_.service.InterfaceParamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterfaceContractServiceImplTest {

    private final ApiInterfaceService apiInterfaceService = mock(ApiInterfaceService.class);
    private final InterfaceParamService interfaceParamService = mock(InterfaceParamService.class);
    private final List<InterfaceParam> stored = new ArrayList<>();
    private ApiInterface apiInterface;
    private InterfaceContractServiceImpl service;

    @BeforeEach
    void setUp() {
        apiInterface = new ApiInterface();
        apiInterface.setId(1L);
        apiInterface.setInterfaceCode("PERSONAL_QUERY");
        apiInterface.setInterfaceName("个人信息查询");
        when(apiInterfaceService.getById(1L)).thenReturn(apiInterface);
        when(interfaceParamService.listByInterfaceId(1L)).thenAnswer(invocation -> new ArrayList<>(stored));
        when(interfaceParamService.remove(any())).thenAnswer(invocation -> {
            stored.clear();
            return true;
        });
        AtomicLong ids = new AtomicLong(1);
        when(interfaceParamService.save(any(InterfaceParam.class))).thenAnswer(invocation -> {
            InterfaceParam entity = invocation.getArgument(0);
            entity.setId(ids.getAndIncrement());
            stored.add(entity);
            return true;
        });
        when(apiInterfaceService.updateSchema(anyLong(), anyString(), anyString())).thenAnswer(invocation -> {
            apiInterface.setRequestSchema(invocation.getArgument(1));
            apiInterface.setResponseSchema(invocation.getArgument(2));
            return true;
        });
        service = new InterfaceContractServiceImpl(apiInterfaceService, interfaceParamService, new ObjectMapper());
    }

    @Test
    void savesNestedRequestAndResponseFieldsAndGeneratesSnapshots() {
        InterfaceParamDTO name = field("name", "string", true);
        InterfaceParamDTO profile = field("profile", "object", true);
        profile.setChildren(List.of(name));
        InterfaceParamDTO score = field("score", "integer", true);
        InterfaceContractDTO input = new InterfaceContractDTO();
        input.setRequestFields(List.of(profile));
        input.setResponseFields(List.of(score));

        InterfaceContractDTO saved = service.saveContract(1L, input);

        assertEquals(2, saved.getRequestFields().get(0).getChildren().size() + saved.getResponseFields().size());
        assertTrue(saved.getRequestSchema().contains("required"));
        assertTrue(saved.getResponseSchema().contains("integer"));
    }

    @Test
    void rejectsDefaultsAndExamplesThatViolateConfiguredConstraints() {
        InterfaceParamDTO city = field("city", "string", false);
        city.setDefaultValue("UTC");
        city.setConstraintConfig("{\"pattern\":\"^[A-Za-z]+/[A-Za-z_]+$\"}");
        InterfaceContractDTO invalidDefault = new InterfaceContractDTO();
        invalidDefault.setRequestFields(List.of(city));
        assertThrows(IllegalArgumentException.class, () -> service.saveContract(1L, invalidDefault));

        InterfaceParamDTO score = field("score", "integer", false);
        score.setExampleValue("101");
        score.setConstraintConfig("{\"maximum\":100}");
        InterfaceContractDTO invalidExample = new InterfaceContractDTO();
        invalidExample.setResponseFields(List.of(score));
        assertThrows(IllegalArgumentException.class, () -> service.saveContract(1L, invalidExample));
    }

    @Test
    void generatesParseableSchemaForRegexContainingBackslash() throws Exception {
        InterfaceParamDTO city = field("city", "string", true);
        city.setConstraintConfig("{\"pattern\":\"^[A-Za-z_]+(?:/[A-Za-z0-9_+\\\\-]+)+$\"}");
        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setRequestFields(List.of(city));

        InterfaceContractDTO saved = service.saveContract(1L, contract);

        assertEquals("^[A-Za-z_]+(?:/[A-Za-z0-9_+\\-]+)+$",
                new ObjectMapper().readTree(saved.getRequestSchema())
                        .path("properties").path("city").path("pattern").asText());
    }

    @Test
    void validatesConfiguredDefaultTypeAndKeepsStringLiteralsAsStrings() throws Exception {
        InterfaceParamDTO invalid = field("enabled", "boolean", false);
        invalid.setDefaultValue("not-a-boolean");
        InterfaceContractDTO invalidContract = new InterfaceContractDTO();
        invalidContract.setRequestFields(List.of(invalid));
        assertThrows(IllegalArgumentException.class, () -> service.saveContract(1L, invalidContract));

        InterfaceParamDTO text = field("flagText", "string", false);
        text.setDefaultValue("true");
        InterfaceContractDTO validContract = new InterfaceContractDTO();
        validContract.setRequestFields(List.of(text));

        InterfaceContractDTO saved = service.saveContract(1L, validContract);

        assertTrue(new ObjectMapper().readTree(saved.getRequestSchema())
                .path("properties").path("flagText").path("default").isTextual());
    }

    @Test
    void rejectsConstraintsThatDoNotMatchFieldTypeOrHaveInvalidValues() {
        InterfaceParamDTO number = field("score", "number", false);
        number.setConstraintConfig("{\"pattern\":\"[0-9]+\"}");
        InterfaceContractDTO incompatible = new InterfaceContractDTO();
        incompatible.setRequestFields(List.of(number));
        assertThrows(IllegalArgumentException.class, () -> service.saveContract(1L, incompatible));

        InterfaceParamDTO text = field("name", "string", false);
        text.setConstraintConfig("{\"minLength\":10,\"maxLength\":2}");
        InterfaceContractDTO invalidRange = new InterfaceContractDTO();
        invalidRange.setRequestFields(List.of(text));
        assertThrows(IllegalArgumentException.class, () -> service.saveContract(1L, invalidRange));
    }

    @Test
    void rejectsExcessivelyDeepContractTrees() {
        InterfaceParamDTO root = field("level0", "object", false);
        InterfaceParamDTO current = root;
        for (int index = 1; index <= 21; index++) {
            InterfaceParamDTO child = field("level" + index, "object", false);
            current.setChildren(List.of(child));
            current = child;
        }
        InterfaceContractDTO contract = new InterfaceContractDTO();
        contract.setRequestFields(List.of(root));

        assertThrows(IllegalArgumentException.class, () -> service.saveContract(1L, contract));
    }

    private InterfaceParamDTO field(String name, String type, boolean required) {
        InterfaceParamDTO field = new InterfaceParamDTO();
        field.setParamName(name);
        field.setParamType(type);
        field.setRequired(required);
        return field;
    }
}
