package com.dataplatform.identity.iam.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.dataplatform.common.security.RoleCodeNormalizer;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleCodeNormalizerTest {

    @Test
    void normalizesRoleCodesAtIdentityAndWorkflowBoundary() {
        assertThat(RoleCodeNormalizer.normalize("  API_Interface_Approver "))
                .isEqualTo("api_interface_approver");
        assertThat(RoleCodeNormalizer.normalizeAll(List.of("ADMIN", "admin", "  ")))
                .containsExactly("admin");
    }
}
