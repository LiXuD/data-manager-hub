package com.dataplatform.common.security;

public final class InternalActorContext {

    public static final String ACTOR_ID_HEADER = "X-Actor-Id";
    public static final String TENANT_ID_HEADER = "X-Actor-Tenant-Id";
    public static final String ACTOR_ID_ATTRIBUTE = InternalActorContext.class.getName() + ".actorId";
    public static final String TENANT_ID_ATTRIBUTE = InternalActorContext.class.getName() + ".tenantId";

    private InternalActorContext() {
    }
}
