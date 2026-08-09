package com.dataplatform.common.plugin.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Host-owned last line of defense before plugin messages leave the runtime boundary. */
public final class ConnectorSafeMessageSanitizer {

    public static final int MAX_SAFE_MESSAGE_LENGTH = 512;
    private static final int MAX_INPUT_LENGTH = 16 * 1024;
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN(?: [A-Z0-9]+)* PRIVATE KEY-----.*?-----END(?: [A-Z0-9]+)* PRIVATE KEY-----",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{4,}");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(\\bauthorization\\b\\s*[:=]\\s*)"
                    + "(?:\"[^\"]*\"|'[^']*'|[^\\r\\n,;&}]+)");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:access[_-]?token|refresh[_-]?token|token|password|passwd|secret|"
                    + "credential|private\\s*[_-]?\\s*key|api\\s*[_-]?\\s*key)\\b\\s*[:=]\\s*)"
                    + "(?:\"[^\"]*\"|'[^']*'|[^\\s,;&}]+)");

    private ConnectorSafeMessageSanitizer() { }

    public static String sanitize(String message, Iterable<String> secretValues) {
        if (message == null || message.isBlank()) {
            return "Connector plugin failed";
        }
        String safe = message.length() > MAX_INPUT_LENGTH
                ? message.substring(0, MAX_INPUT_LENGTH) : message;
        List<String> secrets = new ArrayList<>();
        if (secretValues != null) {
            for (String value : secretValues) {
                if (value != null && !value.isBlank()) secrets.add(value);
            }
        }
        secrets = secrets.stream().distinct()
                .sorted(Comparator.comparingInt(String::length).reversed()).toList();
        for (String secret : secrets) {
            safe = replaceLiteral(safe, secret);
        }
        safe = PRIVATE_KEY.matcher(safe).replaceAll(REDACTED);
        safe = BEARER.matcher(safe).replaceAll("Bearer " + REDACTED);
        safe = AUTHORIZATION.matcher(safe).replaceAll("$1" + REDACTED);
        safe = SENSITIVE_ASSIGNMENT.matcher(safe).replaceAll("$1" + REDACTED);
        safe = safe.replaceAll("[\\p{Cntrl}&&[^\\t\\n\\r]]", " ")
                .replaceAll("\\s+", " ").trim();
        if (safe.length() > MAX_SAFE_MESSAGE_LENGTH) {
            safe = safe.substring(0, MAX_SAFE_MESSAGE_LENGTH - 3) + "...";
        }
        return safe.isBlank() ? "Connector plugin failed" : safe;
    }

    private static String replaceLiteral(String input, String secret) {
        return input.replace(secret, REDACTED);
    }
}
