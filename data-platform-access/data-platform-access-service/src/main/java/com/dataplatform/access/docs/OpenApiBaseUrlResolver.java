package com.dataplatform.access.docs;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 提供受控的 OpenAPI 对外服务地址，避免请求头污染生成文档。 */
@Component
public class OpenApiBaseUrlResolver {

    private final String publicBaseUrl;

    public OpenApiBaseUrlResolver(@Value("${platform.openapi.public-base-url}") String configuredUrl) {
        this.publicBaseUrl = normalize(configuredUrl);
    }

    public String resolve() {
        return publicBaseUrl;
    }

    static String normalize(String configuredUrl) {
        if (!StringUtils.hasText(configuredUrl)) {
            throw new IllegalArgumentException("platform.openapi.public-base-url不能为空");
        }
        URI uri;
        try {
            uri = URI.create(configuredUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("platform.openapi.public-base-url不是有效URL", exception);
        }
        String scheme = uri.getScheme();
        boolean validScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        String path = uri.getRawPath();
        if (!validScheme || uri.isOpaque() || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (StringUtils.hasText(path) && !"/".equals(path))) {
            throw new IllegalArgumentException("platform.openapi.public-base-url必须是无路径的HTTP(S)地址");
        }
        return scheme.toLowerCase() + "://" + uri.getRawAuthority();
    }
}
