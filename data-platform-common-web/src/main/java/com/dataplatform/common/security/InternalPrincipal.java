package com.dataplatform.common.security;

import java.util.Set;

public record InternalPrincipal(String serviceName, String audience, Set<String> scopes) {
}
