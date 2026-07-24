package com.dataplatform.identity.iam.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class IdentityAccessInternalControllerTest {

    @Test
    void exposesIdentityAccessContractUnderInternalPath() {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
                IdentityAccessInternalController.class, RequestMapping.class);

        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/internal/v1/identity/users"}, mapping.value());
    }
}
