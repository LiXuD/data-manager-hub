package com.dataplatform.common.plugin.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class PluginArtifactVerifier {

    public static final long MAX_ARTIFACT_BYTES = 50L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 100L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 16L * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    private static final Set<String> ALLOWED_SIGNATURE_ALGORITHMS = Set.of(
            "ED25519", "SHA256WITHRSA", "SHA256WITHECDSA");
    private static final Set<String> FORBIDDEN_ARCHIVE_PREFIXES = Set.of(
            "java/", "javax/", "jakarta/", "com/dataplatform/plugin/spi/",
            "com/dataplatform/common/", "com/dataplatform/access/",
            "com/dataplatform/masterdata/", "com/dataplatform/billing/",
            "com/dataplatform/identity/", "com/dataplatform/governance/");

    private final PluginManifestReader manifestReader;
    private final TrustedSigningKeyProvider keyProvider;

    public PluginArtifactVerifier(ObjectMapper mapper, TrustedSigningKeyProvider keyProvider) {
        this.manifestReader = new PluginManifestReader(mapper);
        this.keyProvider = keyProvider;
    }

    public VerifiedPluginArtifact verify(PluginArtifactCoordinates coordinates) {
        try {
            if (!Files.isRegularFile(coordinates.jarPath())) {
                throw new PluginArtifactException("Plugin artifact does not exist");
            }
            long size = Files.size(coordinates.jarPath());
            if (size <= 0 || size > MAX_ARTIFACT_BYTES) {
                throw new PluginArtifactException("Plugin artifact size exceeds 50 MiB");
            }
            String actualSha = sha256(coordinates.jarPath());
            if (!MessageDigest.isEqual(actualSha.getBytes(StandardCharsets.US_ASCII),
                    coordinates.expectedSha256().getBytes(StandardCharsets.US_ASCII))) {
                throw new PluginArtifactException("Plugin artifact SHA-256 mismatch");
            }
            byte[] manifestBytes = inspectJarAndReadManifest(coordinates.jarPath());
            PluginManifest manifest = manifestReader.read(manifestBytes);
            if (!coordinates.pluginId().equals(manifest.pluginId())
                    || !coordinates.version().equals(manifest.version())) {
                throw new PluginArtifactException("Artifact coordinates do not match Manifest");
            }
            verifySignature(coordinates, manifestBytes, actualSha);
            return new VerifiedPluginArtifact(coordinates.jarPath(), manifest, actualSha);
        } catch (PluginArtifactException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PluginArtifactException("Plugin artifact verification failed", exception);
        }
    }

    private byte[] inspectJarAndReadManifest(java.nio.file.Path path) throws IOException {
        byte[] manifestBytes = null;
        Set<String> names = new HashSet<>();
        int count = 0;
        long expanded = 0;
        int supportedClassMajor = Runtime.version().feature() + 44;
        try (JarFile jar = new JarFile(path.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (++count > MAX_ENTRIES) {
                    throw new PluginArtifactException("Plugin artifact contains too many entries");
                }
                String name = entry.getName();
                if (!names.add(name)) {
                    throw new PluginArtifactException("Plugin artifact contains duplicate entry: " + name);
                }
                validateEntryName(name);
                byte[] content = readBounded(jar.getInputStream(entry), MAX_ENTRY_BYTES);
                expanded += content.length;
                if (expanded > MAX_EXPANDED_BYTES) {
                    throw new PluginArtifactException("Plugin artifact expanded size exceeds limit");
                }
                if (name.endsWith(".class")) {
                    validateClassVersion(name, content, supportedClassMajor);
                    PluginBytecodePolicy.validate(name, content);
                }
                if (PluginManifestReader.MANIFEST_PATH.equals(name)) {
                    manifestBytes = content;
                }
            }
        }
        if (manifestBytes == null) {
            throw new PluginArtifactException("Plugin Manifest is missing");
        }
        return manifestBytes;
    }

    private void validateEntryName(String name) {
        if (name.startsWith("/") || name.contains("../") || name.contains("\\")) {
            throw new PluginArtifactException("Unsafe plugin archive entry: " + name);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_ARCHIVE_PREFIXES.stream().anyMatch(lower::startsWith)) {
            throw new PluginArtifactException("Plugin artifact contains forbidden package: " + name);
        }
    }

    private void validateClassVersion(String name, byte[] bytes, int supportedClassMajor) {
        if (bytes.length < 8 || bytes[0] != (byte) 0xCA || bytes[1] != (byte) 0xFE
                || bytes[2] != (byte) 0xBA || bytes[3] != (byte) 0xBE) {
            throw new PluginArtifactException("Invalid class file: " + name);
        }
        int major = ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
        if (major > supportedClassMajor) {
            throw new PluginArtifactException("Plugin class bytecode is newer than the host runtime: " + name);
        }
    }

    private void verifySignature(PluginArtifactCoordinates coordinates, byte[] manifestBytes, String sha256)
            throws Exception {
        TrustedSigningKey trustedKey = keyProvider.find(coordinates.signingKeyId())
                .orElseThrow(() -> new PluginArtifactException("Unknown signing key"));
        String algorithm = trustedKey.signatureAlgorithm().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SIGNATURE_ALGORITHMS.contains(algorithm)) {
            throw new PluginArtifactException("Signature algorithm is not allowed");
        }
        Signature verifier = Signature.getInstance(trustedKey.signatureAlgorithm());
        verifier.initVerify(trustedKey.publicKey());
        verifier.update(signaturePayload(manifestReader.canonicalize(manifestBytes), sha256));
        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(coordinates.detachedSignature());
        } catch (IllegalArgumentException exception) {
            throw new PluginArtifactException("Detached signature is not valid Base64", exception);
        }
        if (!verifier.verify(signature)) {
            throw new PluginArtifactException("Plugin artifact signature is invalid");
        }
    }

    static byte[] signaturePayload(byte[] canonicalManifest, String sha256) {
        byte[] hash = sha256.getBytes(StandardCharsets.US_ASCII);
        byte[] payload = new byte[canonicalManifest.length + 1 + hash.length];
        System.arraycopy(canonicalManifest, 0, payload, 0, canonicalManifest.length);
        payload[canonicalManifest.length] = '\n';
        System.arraycopy(hash, 0, payload, canonicalManifest.length + 1, hash.length);
        return payload;
    }

    private String sha256(java.nio.file.Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private byte[] readBounded(InputStream input, long limit) throws IOException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) {
                    throw new PluginArtifactException("Plugin archive entry exceeds size limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
