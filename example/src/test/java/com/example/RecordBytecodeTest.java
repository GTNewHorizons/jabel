package com.example;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.RecordComponentVisitor;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecordBytecodeTest {

    @Test
    public void testNoRecordAttributeInClassFile() throws Exception {
        byte[] bytes = compileRecordWithJabelAndReadClassBytes();

        boolean[] hasRecord = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
                hasRecord[0] = true;
                return null;
            }
        }, 0);

        assertFalse("Classfile must not contain Record attribute", hasRecord[0]);
    }

    private static byte[] compileRecordWithJabelAndReadClassBytes() throws Exception {
        String javac = requireNonEmptySystemProperty("jabel.test.javac");
        String pluginClasspath = requireNonEmptySystemProperty("jabel.test.pluginClasspath");

        Path dir = Files.createTempDirectory("jabel-record-bytecode");
        try {
            Path srcDir = dir.resolve("src");
            Path outDir = dir.resolve("out");
            Files.createDirectories(srcDir.resolve("com/example"));
            Files.createDirectories(outDir);

            Path javaFile = srcDir.resolve("com/example/TmpRecord.java");
            Files.write(javaFile, recordSource().getBytes(StandardCharsets.UTF_8));

            Process process = new ProcessBuilder(
                    javac, "--release", "8", "-d", outDir.toString(), "-cp", pluginClasspath, "-Xplugin:jabel", javaFile.toString()
                ).redirectErrorStream(true).start();
            byte[] out = readAllBytes(process.getInputStream());
            int exit = process.waitFor();
            if (exit != 0) {
                throw new AssertionError("javac failed (" + exit + "):\n" + new String(out, StandardCharsets.UTF_8));
            }

            Path classFile = outDir.resolve("com/example/TmpRecord.class");
            assertTrue("Expected classfile to be generated", Files.exists(classFile));
            return Files.readAllBytes(classFile);
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String recordSource() {
        return  "package com.example;\n"
                + "\n"
                + "import com.github.bsideup.jabel.Desugar;\n"
                + "\n"
                + "@Desugar\n"
                + "public record TmpRecord(int i, String s) {\n"
                + "}\n";
    }

    private static String requireNonEmptySystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing system property: " + name);
        }
        return value;
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8 * 1024];
        for (int read = is.read(buf); read != -1; read = is.read(buf)) {
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }
}
