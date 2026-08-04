import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** JDK-only Ed25519 generator/signer/verifier for macOS LibreSSL compatibility. */
final class Ed25519FixtureTool {
    private Ed25519FixtureTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("expected generate or verify command");
        }
        switch (args[0]) {
            case "generate" -> generate(args);
            case "verify" -> verify(args);
            default -> throw new IllegalArgumentException("unknown command: " + args[0]);
        }
    }

    private static void generate(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "generate <payload> <key-directory> <signature-bin> <signature-base64>");
        }
        Path payload = Path.of(args[1]);
        Path keyDirectory = Path.of(args[2]);
        Path signatureFile = Path.of(args[3]);
        Path signatureBase64File = Path.of(args[4]);
        Files.createDirectories(keyDirectory);

        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(Files.readAllBytes(payload));
        byte[] signature = signer.sign();

        writePem(keyDirectory.resolve("ed25519-private.pem"), "PRIVATE KEY", pair.getPrivate().getEncoded());
        writePem(keyDirectory.resolve("ed25519-public.pem"), "PUBLIC KEY", pair.getPublic().getEncoded());
        Files.writeString(keyDirectory.resolve("ed25519-public.der.b64"),
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n",
                StandardCharsets.US_ASCII);
        Files.write(signatureFile, signature);
        Files.writeString(signatureBase64File, Base64.getEncoder().encodeToString(signature) + "\n",
                StandardCharsets.US_ASCII);
    }

    private static void verify(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("verify <payload> <public-key-pem> <signature-bin>");
        }
        String pem = Files.readString(Path.of(args[2]), StandardCharsets.US_ASCII)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        var publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(Files.readAllBytes(Path.of(args[1])));
        if (!verifier.verify(Files.readAllBytes(Path.of(args[3])))) {
            throw new IllegalStateException("Ed25519 signature verification failed");
        }
    }

    private static void writePem(Path path, String type, byte[] encoded) throws Exception {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(encoded);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body
                + "\n-----END " + type + "-----\n", StandardCharsets.US_ASCII);
    }
}
