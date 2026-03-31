package com.example.agentruntime.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 解析 stdio 传输下的命令与参数。
 * 当前重点解决 PowerShell `-File ./scripts/...` 这类相对路径问题，
 * 让应用即使不是从项目根目录启动，也能尽量找到目标脚本。
 */
public final class StdioCommandResolver {

    /**
     * 这些参数后面通常跟的是一个文件路径，适合做相对路径补全。
     */
    private static final Set<String> PATH_ARGUMENT_FLAGS = Set.of(
            "-file",
            "--file",
            "-filepath",
            "--filepath"
    );

    private StdioCommandResolver() {
    }

    /**
     * 解析命令本身。
     * 如果命令已经是绝对路径或无法命中候选路径，则保持原样，
     * 交给操作系统按 PATH 规则继续解析。
     */
    public static String resolveCommand(String command, Path baseDirectory) {
        if (isBlank(command) || looksLikeCliName(command) || looksLikeUri(command)) {
            return command;
        }
        return resolvePathLikeValue(command, baseDirectory);
    }

    /**
     * 解析参数列表。
     * 目前仅对明确声明为“文件路径参数”的项做补全，避免误改普通字符串参数。
     */
    public static List<String> resolveArgs(List<String> args, Path baseDirectory) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }

        List<String> resolved = new ArrayList<>(args.size());
        for (int index = 0; index < args.size(); index++) {
            String current = args.get(index);
            String previous = index == 0 ? null : args.get(index - 1);
            if (isPathArgument(previous, current)) {
                resolved.add(resolvePathLikeValue(current, baseDirectory));
            } else {
                resolved.add(current);
            }
        }
        return List.copyOf(resolved);
    }

    static String resolvePathLikeValue(String rawValue, Path baseDirectory) {
        if (isBlank(rawValue) || looksLikeUri(rawValue)) {
            return rawValue;
        }

        Path rawPath;
        try {
            rawPath = Path.of(rawValue);
        } catch (RuntimeException ignored) {
            return rawValue;
        }

        if (rawPath.isAbsolute()) {
            return rawPath.normalize().toString();
        }

        for (Path candidateBase : candidateBaseDirectories(baseDirectory)) {
            if (candidateBase == null) {
                continue;
            }
            Path resolved = candidateBase.resolve(rawPath).normalize();
            if (Files.exists(resolved)) {
                return resolved.toString();
            }
        }

        return rawValue;
    }

    private static boolean isPathArgument(String previous, String current) {
        if (isBlank(current) || looksLikeUri(current)) {
            return false;
        }
        return previous != null && PATH_ARGUMENT_FLAGS.contains(previous.toLowerCase(Locale.ROOT));
    }

    private static List<Path> candidateBaseDirectories(Path baseDirectory) {
        Path normalizedBase = baseDirectory == null ? Path.of("").toAbsolutePath().normalize() : baseDirectory.toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(normalizedBase);
        if (normalizedBase.getParent() != null) {
            candidates.add(normalizedBase.getParent());
        }
        if (normalizedBase.getParent() != null && normalizedBase.getParent().getParent() != null) {
            candidates.add(normalizedBase.getParent().getParent());
        }
        return candidates;
    }

    private static boolean looksLikeCliName(String command) {
        return !command.contains("\\") && !command.contains("/") && !command.contains(".");
    }

    private static boolean looksLikeUri(String value) {
        return value.contains("://");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
