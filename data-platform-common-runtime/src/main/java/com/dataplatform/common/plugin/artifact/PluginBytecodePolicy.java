package com.dataplatform.common.plugin.artifact;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Static constant-pool gate preventing plugins from bypassing host-provided capabilities. */
public final class PluginBytecodePolicy {

    private static final Set<String> FORBIDDEN_EXACT = Set.of(
            "java/net/Socket", "java/net/ServerSocket", "java/net/URL", "java/net/URLConnection",
            "java/net/http/HttpClient", "java/io/File", "java/io/FileInputStream",
            "java/io/FileOutputStream", "java/io/RandomAccessFile",
            "java/lang/ClassLoader", "java/lang/System", "java/lang/Runtime", "java/lang/Thread",
            "java/lang/Process", "java/lang/ProcessBuilder", "java/lang/invoke/MethodHandles",
            "java/util/concurrent/Executors", "java/util/concurrent/ThreadPoolExecutor",
            "java/util/concurrent/ScheduledThreadPoolExecutor", "java/util/concurrent/ScheduledExecutorService",
            "java/util/concurrent/ForkJoinPool", "java/util/Timer");
    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "java/nio/file/", "java/lang/reflect/", "sun/reflect/", "jdk/internal/reflect/",
            "okhttp3/", "org/apache/http/", "org/apache/hc/client/", "reactor/netty/http/client/",
            "io/netty/bootstrap/", "java/net/http/", "java/nio/channels/SocketChannel");
    private static final Set<String> FORBIDDEN_MEMBER_OWNERS = Set.of(
            "java/lang/Class", "java/lang/System", "java/lang/Runtime",
            "java/lang/invoke/MethodHandles");

    private PluginBytecodePolicy() { }

    public static void validate(String className, byte[] bytes) {
        try {
            ConstantPool pool = read(bytes);
            for (String referencedClass : pool.classNames()) {
                if (FORBIDDEN_EXACT.contains(referencedClass)
                        || FORBIDDEN_PREFIXES.stream().anyMatch(referencedClass::startsWith)) {
                    throw new PluginArtifactException(
                            "Plugin bytecode uses forbidden host-bypass API: " + className + " -> " + referencedClass);
                }
            }
            for (MemberReference member : pool.memberReferences()) {
                if (FORBIDDEN_MEMBER_OWNERS.contains(member.owner())) {
                    throw new PluginArtifactException(
                            "Plugin bytecode uses forbidden reflection API: " + className
                                    + " -> " + member.owner() + "." + member.name());
                }
            }
            for (String symbol : pool.symbols()) {
                String forbidden = FORBIDDEN_EXACT.stream().filter(name -> descriptorReferences(symbol, name))
                        .findFirst().orElseGet(() -> FORBIDDEN_PREFIXES.stream()
                                .filter(prefix -> descriptorReferences(symbol, prefix)).findFirst().orElse(null));
                if (forbidden != null) {
                    throw new PluginArtifactException(
                            "Plugin bytecode uses forbidden host-bypass API: " + className + " -> " + forbidden);
                }
            }
        } catch (PluginArtifactException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PluginArtifactException("Plugin class cannot be inspected: " + className, exception);
        }
    }

    private static ConstantPool read(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 0xCAFEBABE) throw new IOException("invalid class magic");
            input.readUnsignedShort();
            input.readUnsignedShort();
            int count = input.readUnsignedShort();
            String[] utf8 = new String[count];
            int[] classNames = new int[count];
            int[] memberOwners = new int[count];
            int[] memberNameAndTypes = new int[count];
            int[] nameAndTypeNames = new int[count];
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[index] = input.readUTF();
                    case 3, 4 -> input.skipBytes(4);
                    case 5, 6 -> { input.skipBytes(8); index++; }
                    case 7 -> classNames[index] = input.readUnsignedShort();
                    case 8, 16, 19, 20 -> input.skipBytes(2);
                    case 9, 10, 11 -> {
                        memberOwners[index] = input.readUnsignedShort();
                        memberNameAndTypes[index] = input.readUnsignedShort();
                    }
                    case 12 -> {
                        nameAndTypeNames[index] = input.readUnsignedShort();
                        input.skipBytes(2);
                    }
                    case 17, 18 -> input.skipBytes(4);
                    case 15 -> input.skipBytes(3);
                    default -> throw new IOException("unknown constant-pool tag " + tag);
                }
            }
            List<String> result = new ArrayList<>();
            for (int nameIndex : classNames) {
                if (nameIndex > 0 && nameIndex < utf8.length && utf8[nameIndex] != null) {
                    String value = utf8[nameIndex];
                    if (value.startsWith("[L") && value.endsWith(";")) value = value.substring(2, value.length() - 1);
                    result.add(value);
                }
            }
            List<String> symbols = new ArrayList<>();
            for (String value : utf8) if (value != null) symbols.add(value);
            List<MemberReference> members = new ArrayList<>();
            for (int index = 1; index < count; index++) {
                int ownerClassIndex = memberOwners[index];
                int nameAndTypeIndex = memberNameAndTypes[index];
                if (ownerClassIndex <= 0 || ownerClassIndex >= classNames.length
                        || nameAndTypeIndex <= 0 || nameAndTypeIndex >= nameAndTypeNames.length) {
                    continue;
                }
                int ownerNameIndex = classNames[ownerClassIndex];
                int memberNameIndex = nameAndTypeNames[nameAndTypeIndex];
                if (ownerNameIndex > 0 && ownerNameIndex < utf8.length
                        && memberNameIndex > 0 && memberNameIndex < utf8.length
                        && utf8[ownerNameIndex] != null && utf8[memberNameIndex] != null) {
                    members.add(new MemberReference(utf8[ownerNameIndex], utf8[memberNameIndex]));
                }
            }
            return new ConstantPool(List.copyOf(result), List.copyOf(symbols), List.copyOf(members));
        }
    }

    private static boolean descriptorReferences(String symbol, String internalName) {
        return symbol.equals(internalName) || symbol.contains("L" + internalName)
                || (internalName.endsWith("/") && symbol.contains("L" + internalName));
    }

    private record MemberReference(String owner, String name) { }

    private record ConstantPool(
            List<String> classNames, List<String> symbols, List<MemberReference> memberReferences) { }
}
