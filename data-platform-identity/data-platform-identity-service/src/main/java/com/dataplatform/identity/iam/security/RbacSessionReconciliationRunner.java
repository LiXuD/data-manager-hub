package com.dataplatform.identity.iam.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RbacSessionReconciliationRunner implements ApplicationRunner {

    static final String REVISION_KEY = "security:rbac:session-schema-revision";
    static final String REVISION = "v027-api-permission-security";

    private final StringRedisTemplate redisTemplate;
    private final IamAuthorizationService authorizationService;

    public RbacSessionReconciliationRunner(
            StringRedisTemplate redisTemplate,
            IamAuthorizationService authorizationService) {
        this.redisTemplate = redisTemplate;
        this.authorizationService = authorizationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String reconciledRevision = redisTemplate.opsForValue().get(REVISION_KEY);
        if (REVISION.equals(reconciledRevision)) {
            return;
        }
        authorizationService.invalidateAllUsers();
        redisTemplate.opsForValue().set(REVISION_KEY, REVISION);
    }
}
