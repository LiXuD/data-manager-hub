package com.dataplatform.common.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class RoleCodeNormalizer {

    private RoleCodeNormalizer() {
    }

    public static String normalize(String roleCode) {
        if (roleCode == null) {
            return null;
        }
        String normalized = roleCode.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public static Set<String> normalizeAll(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        roleCodes.stream()
                .map(RoleCodeNormalizer::normalize)
                .filter(Objects::nonNull)
                .forEach(normalized::add);
        return Set.copyOf(normalized);
    }
}
