package com.dataplatform.masterdata.connector.service;

import com.dataplatform.masterdata.connector.api.dto.PluginImportRequestDTO;
import com.dataplatform.masterdata.connector.config.ConnectorPluginProperties;
import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.common.plugin.artifact.PluginArtifactException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PluginArtifactVerifier {
    static final String MANIFEST_PATH = "META-INF/data-platform/plugin.json";
    private static final Set<String> CAPABILITIES = Set.of(
            "REQUEST_BUILDER", "REQUEST_PROCESSOR", "TRANSPORT",
            "RESPONSE_PROCESSOR", "RESPONSE_PARSER", "RESPONSE_NORMALIZER");
    private static final List<String> FORBIDDEN_CLASS_PREFIXES = List.of(
            "java/", "javax/", "jakarta/", "com/dataplatform/plugin/spi/",
            "com/dataplatform/common/",
            "com/dataplatform/masterdata/", "com/dataplatform/access/",
            "com/dataplatform/billing/", "com/dataplatform/identity/",
            "com/dataplatform/governance/");

    private final ConnectorPluginProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Autowired
    public PluginArtifactVerifier(ConnectorPluginProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build());
    }

    PluginArtifactVerifier(ConnectorPluginProperties properties, ObjectMapper objectMapper,
                           OkHttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.httpClient = httpClient;
    }

    public VerifiedPluginArtifact verify(PluginImportRequestDTO request) {
        try {
            URI uri = validateArtifactUri(request.artifactUri());
            byte[] artifact = download(uri);
            return verifyDownloaded(request, uri, artifact);
        } catch (PluginArtifactValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw validationFailure(exception);
        }
    }

    VerifiedPluginArtifact verifyDownloaded(PluginImportRequestDTO request, URI uri, byte[] artifact) {
        try {
            return verifyDownloadedArtifact(request, uri, artifact);
        } catch (PluginArtifactValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw validationFailure(exception);
        }
    }

    private VerifiedPluginArtifact verifyDownloadedArtifact(
            PluginImportRequestDTO request, URI uri, byte[] artifact) {
        String actualSha256 = sha256(artifact);
        if (!actualSha256.equals(request.expectedSha256().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("插件制品SHA-256与期望值不一致");
        }
        ManifestContent manifestContent = readManifest(artifact);
        JsonNode manifest = parseManifest(manifestContent.bytes());
        validateManifest(manifest, manifestContent.classEntries());
        verifySignature(manifest, actualSha256, request.detachedSignature(), request.signingKeyId());
        JsonNode configSchema = manifest.path("configSchema");
        return new VerifiedPluginArtifact(
                text(manifest, "pluginId"), text(manifest, "version"), text(manifest, "spiVersion"),
                text(manifest, "displayName"), text(manifest, "provider"), optionalText(manifest, "description"),
                text(manifest, "entryClass"), uri.toString(), actualSha256, request.detachedSignature(),
                request.signingKeyId(), canonicalJson(manifest), canonicalJson(configSchema),
                stringArray(manifest.path("capabilities")), canonicalJson(manifest.path("permissions")),
                optionalText(manifest, "minHostVersion"), configSchema);
    }

    private PluginArtifactValidationException validationFailure(IllegalArgumentException exception) {
        return new PluginArtifactValidationException(exception.getMessage(), exception);
    }

    URI validateArtifactUri(String rawUri) {
        URI uri;
        try {
            uri = URI.create(rawUri);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("插件制品地址无效", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("插件制品地址必须是不含凭据、查询或片段的HTTPS地址");
        }
        boolean hostAllowed = properties.getArtifactAllowedHosts().stream()
                .anyMatch(host -> host.equalsIgnoreCase(uri.getHost()));
        boolean pathAllowed = properties.getArtifactAllowedPathPrefixes().stream()
                .anyMatch(prefix -> pathAllowed(uri.getPath(), prefix));
        if (!hostAllowed || !pathAllowed) {
            throw new IllegalArgumentException("插件制品地址不在受信仓库白名单");
        }
        return uri;
    }

    private boolean pathAllowed(String path, String prefix) {
        if (!StringUtils.hasText(path) || !StringUtils.hasText(prefix) || !prefix.startsWith("/")) return false;
        String normalizedPrefix = URI.create("https://placeholder" + prefix).normalize().getPath();
        String normalizedPath = URI.create("https://placeholder" + path).normalize().getPath();
        return normalizedPath.equals(normalizedPrefix) || normalizedPath.startsWith(
                normalizedPrefix.endsWith("/") ? normalizedPrefix : normalizedPrefix + "/");
    }

    private byte[] download(URI uri) {
        Request request = new Request.Builder().url(uri.toString()).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalArgumentException("插件制品下载失败: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IllegalArgumentException("插件制品下载响应为空");
            }
            long declaredLength = body.contentLength();
            if (declaredLength > properties.getMaxArtifactBytes()) {
                throw new IllegalArgumentException("插件制品超过大小限制");
            }
            try (var input = body.byteStream(); var output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > properties.getMaxArtifactBytes()) {
                        throw new IllegalArgumentException("插件制品超过大小限制");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("插件制品下载失败", exception);
        }
    }

    private ManifestContent readManifest(byte[] artifact) {
        byte[] manifest = null;
        Set<String> classEntries = new HashSet<>();
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(artifact))) {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    if (FORBIDDEN_CLASS_PREFIXES.stream().anyMatch(name::startsWith)) {
                        throw new IllegalArgumentException("插件包含禁止覆盖的宿主或SPI类: " + name);
                    }
                    classEntries.add(name);
                }
                if (MANIFEST_PATH.equals(name)) {
                    if (manifest != null) {
                        throw new IllegalArgumentException("插件包含重复Manifest");
                    }
                    manifest = readEntry(jar, properties.getMaxManifestBytes());
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("插件制品不是有效JAR", exception);
        }
        if (manifest == null) {
            throw new IllegalArgumentException("插件缺少 " + MANIFEST_PATH);
        }
        return new ManifestContent(manifest, classEntries);
    }

    private byte[] readEntry(JarInputStream jar, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = jar.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("插件Manifest超过大小限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private JsonNode parseManifest(byte[] manifestBytes) {
        try {
            JsonNode manifest = objectMapper.readTree(manifestBytes);
            if (!manifest.isObject()) {
                throw new IllegalArgumentException("插件Manifest必须是JSON对象");
            }
            return manifest;
        } catch (IOException exception) {
            throw new IllegalArgumentException("插件Manifest不是有效JSON", exception);
        }
    }

    private void validateManifest(JsonNode manifest, Set<String> classEntries) {
        // Keep control-plane acceptance identical to the Access runtime's signed Manifest contract.
        try {
            new PluginManifestReader(objectMapper).read(canonicalJson(manifest).getBytes(StandardCharsets.UTF_8));
        } catch (PluginArtifactException exception) {
            throw new IllegalArgumentException("插件Manifest不符合运行时契约", exception);
        }
        if (!"1".equals(text(manifest, "manifestVersion"))) {
            throw new IllegalArgumentException("仅支持manifestVersion=1");
        }
        String pluginId = text(manifest, "pluginId");
        if (!pluginId.matches("[a-z0-9][a-z0-9.-]{1,126}[a-z0-9]")) {
            throw new IllegalArgumentException("pluginId格式无效");
        }
        String version = text(manifest, "version");
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?")) {
            throw new IllegalArgumentException("插件version必须使用语义化版本");
        }
        text(manifest, "spiVersion");
        text(manifest, "displayName");
        text(manifest, "provider");
        String entryClass = text(manifest, "entryClass");
        if (!classEntries.contains(entryClass.replace('.', '/') + ".class")) {
            throw new IllegalArgumentException("Manifest entryClass不在插件JAR中");
        }
        List<String> capabilities = stringArray(manifest.path("capabilities"));
        if (capabilities.isEmpty() || capabilities.stream().anyMatch(capability -> !CAPABILITIES.contains(capability))) {
            throw new IllegalArgumentException("插件能力为空或包含未知能力");
        }
        JsonNode schema = manifest.path("configSchema");
        if (!schema.isObject()) {
            throw new IllegalArgumentException("configSchema必须是JSON对象");
        }
        if (canonicalJson(schema).getBytes(StandardCharsets.UTF_8).length > properties.getMaxSchemaBytes()) {
            throw new IllegalArgumentException("Config Schema超过大小限制");
        }
        rejectRemoteReferences(schema);
        JsonNode permissions = manifest.path("permissions");
        if (!permissions.isObject()) {
            throw new IllegalArgumentException("permissions必须是JSON对象");
        }
        List<String> protocols = stringArray(permissions.path("networkProtocols"));
        if (protocols.stream().anyMatch(protocol -> !"https".equalsIgnoreCase(protocol))) {
            throw new IllegalArgumentException("首期插件只允许声明HTTPS网络协议");
        }
    }

    private void rejectRemoteReferences(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if ("$ref".equals(entry.getKey())) {
                    String ref = entry.getValue().asText();
                    if (!ref.startsWith("#/")) {
                        throw new IllegalArgumentException("Config Schema禁止远程$ref");
                    }
                }
                rejectRemoteReferences(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::rejectRemoteReferences);
        }
    }

    private void verifySignature(JsonNode manifest, String sha256, String signatureValue, String keyId) {
        String encodedKey = properties.getTrustedSigningKeys().get(keyId);
        if (!StringUtils.hasText(encodedKey)) {
            throw new IllegalArgumentException("签名密钥不在只读TrustStore中");
        }
        try {
            PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey)));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update((canonicalJson(manifest) + "\n" + sha256).getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(signatureValue))) {
                throw new IllegalArgumentException("插件Ed25519签名验证失败");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("插件Ed25519签名验证失败", exception);
        }
    }

    private String text(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("插件Manifest缺少字段: " + field);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private List<String> stringArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private String canonicalJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(sort(value));
        } catch (IOException exception) {
            throw new IllegalArgumentException("插件元数据无法规范化", exception);
        }
    }

    private JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = ((ObjectNode) value).objectNode();
            List<String> fields = new ArrayList<>();
            value.fieldNames().forEachRemaining(fields::add);
            fields.sort(Comparator.naturalOrder());
            fields.forEach(field -> result.set(field, sort(value.get(field))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = ((ArrayNode) value).arrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        return value;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }

    private record ManifestContent(byte[] bytes, Set<String> classEntries) {
    }
}
