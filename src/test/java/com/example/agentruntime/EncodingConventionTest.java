package com.example.agentruntime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编码约束测试。
 * 用来防止源码、配置和前端文本文件再次被写入 UTF-8 BOM。
 */
class EncodingConventionTest {

    private static final List<String> EXTENSIONS = List.of(
            ".java", ".kt", ".xml", ".yml", ".yaml", ".properties", ".md",
            ".vue", ".ts", ".js", ".json", ".css", ".html"
    );

    @Test
    void textFilesShouldNotContainUtf8Bom() throws IOException {
        try (Stream<Path> stream = Files.walk(Path.of("."))) {
            List<String> filesWithBom = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isTrackedTextFile)
                    .filter(this::hasUtf8Bom)
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .sorted()
                    .toList();

            assertTrue(filesWithBom.isEmpty(), () -> "These files contain UTF-8 BOM: " + filesWithBom);
        }
    }

    private boolean isTrackedTextFile(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (normalized.contains("/target/") || normalized.contains("/node_modules/") || normalized.contains("/dist/")) {
            return false;
        }
        return EXTENSIONS.stream().anyMatch(normalized::endsWith);
    }

    private boolean hasUtf8Bom(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return bytes.length >= 3
                    && bytes[0] == (byte) 0xEF
                    && bytes[1] == (byte) 0xBB
                    && bytes[2] == (byte) 0xBF;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file: " + path, exception);
        }
    }
}
