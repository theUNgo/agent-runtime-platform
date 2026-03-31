package com.example.agentruntime.mcp;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 将 MCP 初始化或探测失败原因转换为更适合用户理解的诊断信息。
 */
public final class McpValidationDiagnostics {

    private McpValidationDiagnostics() {
    }

    public static List<McpValidationDiagnostic> diagnose(AgentRuntimeProperties.McpServerProperties server,
                                                         McpServerStatus status,
                                                         MessageService messageService) {
        List<McpValidationDiagnostic> diagnostics = new ArrayList<>();
        String error = status == null ? null : safeLower(status.lastError());

        if (server == null) {
            diagnostics.add(new McpValidationDiagnostic(
                    "server.unconfigured",
                    "ERROR",
                    messageService.get("mcp.validation.diagnostic.serverUnconfigured"),
                    messageService.get("mcp.validation.diagnostic.serverUnconfigured.suggestion")
            ));
            return List.copyOf(diagnostics);
        }

        if (status != null && status.initialized()) {
            diagnostics.add(new McpValidationDiagnostic(
                    "server.ready",
                    "INFO",
                    messageService.get("mcp.validation.diagnostic.serverReady"),
                    messageService.get("mcp.validation.diagnostic.serverReady.suggestion")
            ));
            return List.copyOf(diagnostics);
        }

        if (server.transport() != null && server.transport().equalsIgnoreCase("stdio")) {
            addStdioDiagnostics(server, error, diagnostics, messageService);
        } else {
            addHttpDiagnostics(server, error, diagnostics, messageService);
        }

        if (diagnostics.isEmpty()) {
            diagnostics.add(new McpValidationDiagnostic(
                    "server.genericFailure",
                    "WARN",
                    messageService.get("mcp.validation.diagnostic.genericFailure"),
                    messageService.get("mcp.validation.diagnostic.genericFailure.suggestion")
            ));
        }

        return List.copyOf(diagnostics);
    }

    public static String describeConfiguration(AgentRuntimeProperties.McpServerProperties server) {
        if (server == null) {
            return null;
        }
        String transport = server.transport() == null ? "stdio" : server.transport().trim().toLowerCase(Locale.ROOT);
        if ("streamable-http".equals(transport) || "streamable_http".equals(transport)) {
            return server.url() == null || server.url().isBlank()
                    ? "streamable-http"
                    : "streamable-http: " + server.url();
        }
        return server.command() == null || server.command().isBlank()
                ? "stdio"
                : "stdio: " + server.command() + joinArgsPreview(server.args());
    }

    private static void addStdioDiagnostics(AgentRuntimeProperties.McpServerProperties server,
                                            String error,
                                            List<McpValidationDiagnostic> diagnostics,
                                            MessageService messageService) {
        if (server.command() == null || server.command().isBlank()) {
            diagnostics.add(new McpValidationDiagnostic(
                    "stdio.commandMissing",
                    "ERROR",
                    messageService.get("mcp.validation.diagnostic.stdio.commandMissing"),
                    messageService.get("mcp.validation.diagnostic.stdio.commandMissing.suggestion")
            ));
        }
        if (containsAny(error, "createprocess error=2", "cannot run program")) {
            diagnostics.add(new McpValidationDiagnostic(
                    "stdio.commandNotFound",
                    "ERROR",
                    messageService.get("mcp.validation.diagnostic.stdio.commandNotFound"),
                    messageService.get("mcp.validation.diagnostic.stdio.commandNotFound.suggestion")
            ));
        }
        if (containsAny(error, ".ps1", "does not exist", "cannot find path", "cannot find file", "not recognized as the name")) {
            diagnostics.add(new McpValidationDiagnostic(
                    "stdio.scriptNotFound",
                    "ERROR",
                    messageService.get("mcp.validation.diagnostic.stdio.scriptNotFound"),
                    messageService.get("mcp.validation.diagnostic.stdio.scriptNotFound.suggestion")
            ));
        }
        if (containsAny(error, "unsupportedclassversionerror", "class file version")) {
            diagnostics.add(new McpValidationDiagnostic(
                    "stdio.javaVersionMismatch",
                    "ERROR",
                    messageService.get("mcp.validation.diagnostic.stdio.javaVersionMismatch"),
                    messageService.get("mcp.validation.diagnostic.stdio.javaVersionMismatch.suggestion")
            ));
        }
        if (containsAny(error, "timeout", "timed out")) {
            diagnostics.add(new McpValidationDiagnostic(
                    "transport.timeout",
                    "WARN",
                    messageService.get("mcp.validation.diagnostic.timeout"),
                    messageService.get("mcp.validation.diagnostic.timeout.suggestion")
            ));
        }
    }

    private static void addHttpDiagnostics(AgentRuntimeProperties.McpServerProperties server,
                                           String error,
                                           List<McpValidationDiagnostic> diagnostics,
                                           MessageService messageService) {
        if (server.url() == null || server.url().isBlank()) {
            diagnostics.add(new McpValidationDiagnostic(
                    "http.urlMissing",
                    "ERROR",
                    messageService.get("mcp.validation.diagnostic.http.urlMissing"),
                    messageService.get("mcp.validation.diagnostic.http.urlMissing.suggestion")
            ));
        }
        if (containsAny(error, "connection refused", "failed to connect", "connection reset")) {
            diagnostics.add(new McpValidationDiagnostic(
                    "http.unreachable",
                    "ERROR",
                    messageService.get("mcp.validation.diagnostic.http.unreachable"),
                    messageService.get("mcp.validation.diagnostic.http.unreachable.suggestion")
            ));
        }
        if (containsAny(error, "status code: 401", "status code: 403", "unauthorized", "forbidden")) {
            diagnostics.add(new McpValidationDiagnostic(
                    "http.authFailed",
                    "ERROR",
                    messageService.get("mcp.validation.diagnostic.http.authFailed"),
                    messageService.get("mcp.validation.diagnostic.http.authFailed.suggestion")
            ));
        }
        if (containsAny(error, "status code: 404", "not found")) {
            diagnostics.add(new McpValidationDiagnostic(
                    "http.notFound",
                    "ERROR",
                    messageService.get("mcp.validation.diagnostic.http.notFound"),
                    messageService.get("mcp.validation.diagnostic.http.notFound.suggestion")
            ));
        }
        if (containsAny(error, "timeout", "timed out")) {
            diagnostics.add(new McpValidationDiagnostic(
                    "transport.timeout",
                    "WARN",
                    messageService.get("mcp.validation.diagnostic.timeout"),
                    messageService.get("mcp.validation.diagnostic.timeout.suggestion")
            ));
        }
    }

    private static boolean containsAny(String text, String... candidates) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String candidate : candidates) {
            if (text.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String safeLower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String joinArgsPreview(List<String> args) {
        if (args == null || args.isEmpty()) {
            return "";
        }
        return " " + String.join(" ", args);
    }
}
