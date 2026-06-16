package com.dataplatform.sdk.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * SDK 生成命令行入口 — 无需 Spring，纯 Java main。
 *
 * Usage:
 *   java -jar data-platform-sdk.jar --lang java --base-url http://localhost:8888 --output ./sdk-out
 *   java -jar data-platform-sdk.jar --lang all  --base-url http://localhost:8888 --output ./sdk-out
 */
public class SDKCli {

    public static void main(String[] args) throws IOException {
        String lang = null;
        String baseUrl = null;
        String output = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--lang"    -> lang    = nextArg(args, i++);
                case "--base-url"-> baseUrl = nextArg(args, i++);
                case "--output"  -> output  = nextArg(args, i++);
                default -> { /* skip unknown */ }
            }
        }

        if (lang == null || baseUrl == null || output == null) {
            System.err.println("Usage: --lang java|python|go|all --base-url URL --output DIR");
            System.exit(1);
        }

        SDKGenerator generator = new SDKGenerator();
        ApiSpec spec = ApiSpec.fromDefaults(baseUrl);
        Path outDir = Path.of(output);

        switch (lang) {
            case "java"   -> writeFiles(outDir, "java",    generator.generateJavaClient(spec));
            case "python" -> writeFiles(outDir, "python",  generator.generatePythonClient(spec));
            case "go"     -> writeFiles(outDir, "go",      generator.generateGoClient(spec));
            case "all"    -> {
                Map<String, Map<String, String>> all = generator.generateAllClients(spec);
                for (var entry : all.entrySet()) {
                    writeFiles(outDir, entry.getKey(), entry.getValue());
                }
            }
            default -> {
                System.err.println("Unknown language: " + lang);
                System.exit(1);
            }
        }

        System.out.println("SDK files generated in: " + outDir.toAbsolutePath());
    }

    private static void writeFiles(Path baseDir, String lang, Map<String, String> files) throws IOException {
        Path dir = baseDir.resolve(lang);
        Files.createDirectories(dir);
        for (var entry : files.entrySet()) {
            Path target = dir.resolve(entry.getKey());
            Files.writeString(target, entry.getValue());
            System.out.println("  " + lang + "/" + entry.getKey());
        }
    }

    private static String nextArg(String[] args, int index) {
        if (index + 1 >= args.length) {
            System.err.println("Missing value for " + args[index]);
            System.exit(1);
        }
        return args[index + 1];
    }
}
