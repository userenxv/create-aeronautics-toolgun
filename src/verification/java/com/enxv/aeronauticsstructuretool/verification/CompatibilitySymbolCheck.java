package com.enxv.aeronauticsstructuretool.verification;

import com.google.gson.Gson;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CompatibilitySymbolCheck {
    private static final String MANIFEST_RESOURCE = "/compatibility-symbols.json";
    private static final int MAX_NESTED_JAR_DEPTH = 4;
    private static final int MAX_ENTRY_BYTES = 128 * 1024 * 1024;

    private CompatibilitySymbolCheck() {
    }

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Manifest manifest = readManifest();
        List<String> failures = new ArrayList<>();
        int verifiedArtifacts = 0;

        for (Artifact artifact : manifest.artifacts()) {
            Path jar = projectRoot.resolve(artifact.path()).normalize();
            if (!jar.startsWith(projectRoot)) {
                failures.add(artifact.label() + ": artifact path escapes the project root");
                continue;
            }
            if (!Files.isRegularFile(jar)) {
                if (artifact.required()) {
                    failures.add(artifact.label() + ": missing artifact " + artifact.path());
                } else {
                    System.out.println("[compat] SKIP " + artifact.label() + ": missing " + artifact.path());
                }
                continue;
            }

            ClassIndex index = ClassIndex.read(jar);
            int requirementCount = verifyArtifact(artifact, index, failures);
            verifiedArtifacts++;
            System.out.println(
                    "[compat] OK " + artifact.label() + ": " + index.size()
                            + " classes indexed, " + requirementCount + " symbols checked"
            );
        }

        if (verifiedArtifacts == 0) {
            failures.add("no compatibility artifacts were available");
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Compatibility symbol verification failed:\n - "
                    + String.join("\n - ", failures));
        }
    }

    private static int verifyArtifact(Artifact artifact, ClassIndex index, List<String> failures) {
        int checked = 0;
        for (String owner : artifact.classes()) {
            checked++;
            if (!index.contains(owner)) {
                failures.add(artifact.label() + ": missing class " + owner.replace('/', '.'));
            }
        }
        for (Member member : artifact.methods()) {
            checked++;
            ClassInfo owner = index.get(member.owner());
            if (owner == null || !owner.hasMethod(member.name(), member.descriptor())) {
                failures.add(artifact.label() + ": missing method " + member.describe());
            }
        }
        for (Member member : artifact.fields()) {
            checked++;
            ClassInfo owner = index.get(member.owner());
            if (owner == null || !owner.hasField(member.name(), member.descriptor())) {
                failures.add(artifact.label() + ": missing field " + member.describe());
            }
        }
        return checked;
    }

    private static Manifest readManifest() throws IOException {
        try (InputStream input = CompatibilitySymbolCheck.class.getResourceAsStream(MANIFEST_RESOURCE)) {
            if (input == null) {
                throw new IOException("missing verification resource " + MANIFEST_RESOURCE);
            }
            Manifest manifest = new Gson().fromJson(new InputStreamReader(input), Manifest.class);
            if (manifest == null || manifest.artifacts() == null || manifest.artifacts().isEmpty()) {
                throw new IOException("compatibility symbol manifest has no artifacts");
            }
            return manifest.normalized();
        }
    }

    private record Manifest(List<Artifact> artifacts) {
        Manifest normalized() {
            return new Manifest(artifacts.stream().map(Artifact::normalized).toList());
        }
    }

    private record Artifact(
            String label,
            String path,
            boolean required,
            List<String> classes,
            List<Member> methods,
            List<Member> fields
    ) {
        Artifact normalized() {
            return new Artifact(
                    label,
                    path,
                    required,
                    classes == null ? List.of() : List.copyOf(classes),
                    methods == null ? List.of() : List.copyOf(methods),
                    fields == null ? List.of() : List.copyOf(fields)
            );
        }
    }

    private record Member(String owner, String name, String descriptor) {
        String describe() {
            String suffix = descriptor == null || descriptor.isBlank() ? "" : descriptor;
            return owner.replace('/', '.') + '#' + name + suffix;
        }
    }

    private record Symbol(String name, String descriptor) {
    }

    private record ClassInfo(Set<Symbol> methods, Set<Symbol> fields) {
        boolean hasMethod(String name, String descriptor) {
            return has(methods, name, descriptor);
        }

        boolean hasField(String name, String descriptor) {
            return has(fields, name, descriptor);
        }

        private static boolean has(Set<Symbol> symbols, String name, String descriptor) {
            if (descriptor == null || descriptor.isBlank()) {
                return symbols.stream().anyMatch(symbol -> symbol.name().equals(name));
            }
            return symbols.contains(new Symbol(name, descriptor));
        }
    }

    private static final class ClassIndex {
        private final Map<String, ClassInfo> classes = new LinkedHashMap<>();

        static ClassIndex read(Path jar) throws IOException {
            ClassIndex index = new ClassIndex();
            try (InputStream input = Files.newInputStream(jar)) {
                index.readArchive(input, 0, jar.toString());
            }
            return index;
        }

        int size() {
            return classes.size();
        }

        boolean contains(String owner) {
            return classes.containsKey(owner);
        }

        ClassInfo get(String owner) {
            return classes.get(owner);
        }

        private void readArchive(InputStream input, int depth, String source) throws IOException {
            if (depth > MAX_NESTED_JAR_DEPTH) {
                throw new IOException("nested jar depth exceeded while reading " + source);
            }
            try (ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (entry.getName().endsWith(".class")) {
                        indexClass(readBounded(zip, entry.getName()));
                    } else if (entry.getName().endsWith(".jar")) {
                        byte[] nested = readBounded(zip, entry.getName());
                        readArchive(new ByteArrayInputStream(nested), depth + 1, source + "!/" + entry.getName());
                    }
                }
            }
        }

        private void indexClass(byte[] bytes) {
            ClassReader reader = new ClassReader(bytes);
            Set<Symbol> methods = new LinkedHashSet<>();
            Set<Symbol> fields = new LinkedHashSet<>();
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    fields.add(new Symbol(name, descriptor));
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    methods.add(new Symbol(name, descriptor));
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            classes.putIfAbsent(reader.getClassName(), new ClassInfo(Set.copyOf(methods), Set.copyOf(fields)));
        }

        private static byte[] readBounded(InputStream input, String entryName) throws IOException {
            byte[] bytes = input.readNBytes(MAX_ENTRY_BYTES + 1);
            if (bytes.length > MAX_ENTRY_BYTES) {
                throw new IOException("archive entry exceeds verification limit: " + entryName);
            }
            return bytes;
        }
    }
}
