package com.dataplatform.access.call.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dataplatform.billing.api.dto.BillingMeteringPolicyDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BillingFactExtractorTest {

    private final BillingFactExtractor extractor = new BillingFactExtractor();

    @Test
    void extractsOnlySelectedResponseAndRequestFacts() {
        BillingMeteringPolicyDTO policy = policy(
                selector("count", "NORMALIZED_RESPONSE", "$.data.items", "ARRAY_SIZE"),
                selector("scene", "REQUEST", "$.scene", "VALUE"));
        Map<String, Object> response = Map.of("data", Map.of("items", List.of(1, 2, 3), "secret", "not-forwarded"));

        Map<String, Object> facts = extractor.extract(policy, response, Map.of("scene", "loan", "identity", "private"));

        assertEquals(Map.of("count", 3, "scene", "loan"), facts);
        assertFalse(facts.containsKey("secret"));
        assertFalse(facts.containsKey("identity"));
    }

    @Test
    void missingPathIsOmittedSoBillingMissingPolicyCanDecide() {
        BillingMeteringPolicyDTO policy = policy(selector("missing", "NORMALIZED_RESPONSE", "$.data.none", "VALUE"));
        assertEquals(Map.of(), extractor.extract(policy, Map.of("data", Map.of()), Map.of()));
    }

    @Test
    void arraySizeRejectsWrongContractValueAtRuntime() {
        BillingMeteringPolicyDTO policy = policy(selector("count", "NORMALIZED_RESPONSE", "$.data.value", "ARRAY_SIZE"));
        assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(policy, Map.of("data", Map.of("value", 1)), Map.of()));
    }

    private BillingMeteringPolicyDTO policy(BillingMeteringPolicyDTO.SelectorDTO... selectors) {
        BillingMeteringPolicyDTO policy = new BillingMeteringPolicyDTO();
        policy.setSelectors(List.of(selectors));
        return policy;
    }

    private BillingMeteringPolicyDTO.SelectorDTO selector(String alias, String source,
                                                          String path, String extraction) {
        BillingMeteringPolicyDTO.SelectorDTO selector = new BillingMeteringPolicyDTO.SelectorDTO();
        selector.setAlias(alias);
        selector.setSource(source);
        selector.setPath(path);
        selector.setExtraction(extraction);
        return selector;
    }
}
