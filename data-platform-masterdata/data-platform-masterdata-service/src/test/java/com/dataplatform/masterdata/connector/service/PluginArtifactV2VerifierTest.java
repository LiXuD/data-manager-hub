package com.dataplatform.masterdata.connector.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dataplatform.common.plugin.artifact.PluginManifestReader;
import com.dataplatform.masterdata.connector.api.dto.PluginImportRequestDTO;
import com.dataplatform.masterdata.connector.config.ConnectorPluginProperties;
import com.dataplatform.plugin.spi.ConnectorAuthoringModel;
import com.dataplatform.plugin.spi.ConnectorKind;
import com.dataplatform.plugin.spi.ConnectorOutputMode;
import com.dataplatform.plugin.spi.ConnectorTransportMode;
import com.dataplatform.plugin.spi.StageCapability;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;

class PluginArtifactV2VerifierTest {

    private static final URI ARTIFACT_URI = URI.create("https://repo.example/plugins/v2.jar");
    private final ObjectMapper mapper = new ObjectMapper();
    private final PluginManifestReader manifestReader = new PluginManifestReader(mapper);

    @Test
    void exposesCompleteV2ProjectionAndCanonicalCompatibilityFromSharedReader() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ObjectNode manifest = validManifest();
        byte[] manifestBytes = bytes(manifest);
        byte[] artifact = jar(manifestBytes, "example/V2Plugin.class");
        String sha = sha256(artifact);
        PluginArtifactVerifier verifier = verifier(keyPair);

        VerifiedPluginArtifact verified = verifier.verifyDownloaded(
                request(sha, sign(keyPair, manifestBytes, sha)), ARTIFACT_URI, artifact);

        assertEquals("2", verified.manifestVersion());
        assertEquals(ConnectorAuthoringModel.SIMPLE_CONNECTOR, verified.authoringModel());
        assertEquals(ConnectorKind.DEDICATED_VENDOR, verified.connectorKind());
        assertEquals(ConnectorTransportMode.HOST_SINGLE_HTTP, verified.transportMode());
        assertEquals(ConnectorOutputMode.HOST_MAPPING, verified.outputMode());
        assertEquals(java.util.Set.of("ACME"), verified.compatibility().vendorCodes());
        assertEquals(java.util.Set.of("COMPANY_PROFILE"), verified.compatibility().dataTypeCodes());
        assertEquals("{\"dataTypeCodes\":[\"COMPANY_PROFILE\"],\"vendorCodes\":[\"ACME\"]}",
                verified.compatibilityJson());
        assertArrayEquals(manifestReader.canonicalize(manifestBytes),
                verified.manifestJson().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsSignatureWhenAValidV2ProductFieldIsTampered() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ObjectNode original = validManifest();
        byte[] originalManifest = bytes(original);
        byte[] originalArtifact = jar(originalManifest, "example/V2Plugin.class");
        String originalSha = sha256(originalArtifact);
        String signature = sign(keyPair, originalManifest, originalSha);

        ObjectNode tampered = original.deepCopy().put("connectorKind", "GENERIC_HTTP");
        byte[] tamperedArtifact = jar(bytes(tampered), "example/V2Plugin.class");
        String tamperedSha = sha256(tamperedArtifact);

        assertThrows(PluginArtifactValidationException.class,
                () -> verifier(keyPair).verifyDownloaded(
                        request(tamperedSha, signature), ARTIFACT_URI, tamperedArtifact));
    }

    @Test
    void sharedManifestRulesAndEntryClassPresenceBothFailClosed() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        ObjectNode invalidCompatibility = validManifest();
        invalidCompatibility.putObject("compatibility");
        byte[] invalidManifest = bytes(invalidCompatibility);
        byte[] invalidArtifact = jar(invalidManifest, "example/V2Plugin.class");
        String invalidSha = sha256(invalidArtifact);
        assertThrows(PluginArtifactValidationException.class,
                () -> verifier(keyPair).verifyDownloaded(
                        request(invalidSha, sign(keyPair, invalidManifest, invalidSha)),
                        ARTIFACT_URI, invalidArtifact));

        byte[] validManifest = bytes(validManifest());
        byte[] missingEntryClass = jar(validManifest, "example/OtherPlugin.class");
        String missingSha = sha256(missingEntryClass);
        assertThrows(PluginArtifactValidationException.class,
                () -> verifier(keyPair).verifyDownloaded(
                        request(missingSha, sign(keyPair, validManifest, missingSha)),
                        ARTIFACT_URI, missingEntryClass));
    }

    private PluginArtifactVerifier verifier(KeyPair keyPair) {
        ConnectorPluginProperties properties = new ConnectorPluginProperties();
        properties.setArtifactAllowedHosts(List.of("repo.example"));
        properties.setArtifactAllowedPathPrefixes(List.of("/plugins/"));
        properties.getTrustedSigningKeys().put("test-key", Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded()));
        return new PluginArtifactVerifier(properties, mapper);
    }

    private PluginImportRequestDTO request(String sha, String signature) {
        return new PluginImportRequestDTO(ARTIFACT_URI.toString(), sha, signature, "test-key");
    }

    private ObjectNode validManifest() {
        ObjectNode root = mapper.createObjectNode();
        root.put("manifestVersion", "2");
        root.put("pluginId", "fixture-plugin");
        root.put("version", "2.0.0");
        root.put("spiVersion", "1.1");
        root.put("displayName", "Fixture v2");
        root.put("provider", "test");
        root.put("entryClass", "example.V2Plugin");
        root.put("authoringModel", "SIMPLE_CONNECTOR");
        root.put("connectorKind", "DEDICATED_VENDOR");
        root.put("transportMode", "HOST_SINGLE_HTTP");
        root.put("outputMode", "HOST_MAPPING");
        root.putArray("capabilities")
                .add(StageCapability.REQUEST_BUILDER.name())
                .add(StageCapability.RESPONSE_PARSER.name());
        ObjectNode compatibility = root.putObject("compatibility");
        compatibility.putArray("vendorCodes").add("ACME");
        compatibility.putArray("dataTypeCodes").add("COMPANY_PROFILE");
        root.put("minHostVersion", "2.0.0");
        ObjectNode properties = root.putObject("configSchema")
                .put("type", "object")
                .putObject("properties");
        properties.putObject("secretRef")
                .put("type", "string")
                .put("x-secret-ref", true)
                .putArray("x-stage-scope").add(StageCapability.REQUEST_BUILDER.name());
        ObjectNode permissions = root.putObject("permissions");
        permissions.putArray("networkProtocols").add("https");
        permissions.putArray("networkHosts").add("api.example.com");
        return root;
    }

    private byte[] bytes(ObjectNode manifest) {
        return manifest.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] jar(byte[] manifest, String classEntry) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(output)) {
            jar.putNextEntry(new JarEntry(PluginArtifactVerifier.MANIFEST_PATH));
            jar.write(manifest);
            jar.closeEntry();
            jar.putNextEntry(new JarEntry(classEntry));
            try (var classBytes = PluginArtifactV2VerifierTest.class.getResourceAsStream(
                    "PluginArtifactV2VerifierTest$LegalFixture.class")) {
                if (classBytes == null) {
                    throw new IllegalStateException("Legal fixture class is unavailable");
                }
                jar.write(classBytes.readAllBytes());
            }
            jar.closeEntry();
        }
        return output.toByteArray();
    }

    private String sign(KeyPair pair, byte[] manifest, String sha) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(manifestReader.canonicalize(manifest));
        signer.update((byte) '\n');
        signer.update(sha.getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class LegalFixture {
    }
}
