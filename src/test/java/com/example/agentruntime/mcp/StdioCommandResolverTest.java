package com.example.agentruntime.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StdioCommandResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldResolvePowerShellFileArgumentAgainstParentDirectory() throws IOException {
        Path projectRoot = Files.createDirectories(tempDir.resolve("project"));
        Path targetDirectory = Files.createDirectories(projectRoot.resolve("target"));
        Path script = Files.createDirectories(projectRoot.resolve("scripts"))
                .resolve("run-demo-mcp-stdio-server.ps1");
        Files.writeString(script, "Write-Output 'ok'");

        List<String> resolved = StdioCommandResolver.resolveArgs(
                List.of("-ExecutionPolicy", "Bypass", "-File", "./scripts/run-demo-mcp-stdio-server.ps1"),
                targetDirectory
        );

        assertEquals(script.toAbsolutePath().normalize().toString(), resolved.get(3));
    }

    @Test
    void shouldKeepNonPathArgumentsUnchanged() {
        List<String> resolved = StdioCommandResolver.resolveArgs(
                List.of("-Command", "Write-Output 'hello'"),
                tempDir
        );

        assertEquals(List.of("-Command", "Write-Output 'hello'"), resolved);
    }
}
