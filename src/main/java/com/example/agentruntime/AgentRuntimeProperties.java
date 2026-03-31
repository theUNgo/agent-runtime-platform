package com.example.agentruntime;

import com.example.agentruntime.auth.UserRole;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 应用级配置。
 * 除了技能目录和工作区信息外，也承载 MCP 服务器清单。
 */
@ConfigurationProperties(prefix = "agent")
public record AgentRuntimeProperties(
        String skillsDir,
        String workspaceRoot,
        AuthProperties auth,
        McpProperties mcp,
        SecurityProperties security
) {

    public AgentRuntimeProperties {
        skillsDir = skillsDir == null || skillsDir.isBlank() ? "./skills" : skillsDir;
        workspaceRoot = workspaceRoot == null || workspaceRoot.isBlank() ? "." : workspaceRoot;
        auth = auth == null ? new AuthProperties(true, Duration.ofDays(30), "admin", "", "Platform Admin", UserRole.ADMIN) : auth;
        mcp = mcp == null ? new McpProperties(List.of()) : mcp;
        security = security == null ? new SecurityProperties(new CryptoProperties("ChangeThisDevelopmentCryptoSecret-32CharsMin")) : security;
    }

    public record AuthProperties(
            boolean enabled,
            Duration tokenValidity,
            String bootstrapUsername,
            String bootstrapPassword,
            String bootstrapDisplayName,
            UserRole bootstrapRole
    ) {

        public AuthProperties(boolean enabled,
                              Duration tokenValidity,
                              String bootstrapUsername,
                              String bootstrapPassword,
                              String bootstrapDisplayName) {
            this(enabled, tokenValidity, bootstrapUsername, bootstrapPassword, bootstrapDisplayName, UserRole.ADMIN);
        }

        public AuthProperties {
            tokenValidity = tokenValidity == null ? Duration.ofDays(30) : tokenValidity;
            bootstrapUsername = bootstrapUsername == null || bootstrapUsername.isBlank() ? "admin" : bootstrapUsername;
            // 出于安全考虑，公开仓库默认不内置管理员密码，要求部署时显式提供。
            bootstrapPassword = bootstrapPassword == null ? "" : bootstrapPassword;
            bootstrapDisplayName = bootstrapDisplayName == null || bootstrapDisplayName.isBlank() ? bootstrapUsername : bootstrapDisplayName;
            bootstrapRole = bootstrapRole == null ? UserRole.ADMIN : bootstrapRole;
        }
    }

    public record McpProperties(List<McpServerProperties> servers) {

        public McpProperties {
            servers = servers == null ? List.of() : List.copyOf(servers);
        }
    }

    public record McpServerProperties(
            String name,
            String description,
            boolean enabled,
            String transport,
            String command,
            List<String> args,
            String url,
            Map<String, String> env,
            Map<String, String> headers,
            Duration timeout
    ) {

        /**
         * 这里对配置进行兜底，保证运行期读取时字段已经是可直接使用的状态。
         */
        public McpServerProperties {
            enabled = !isBlank(name) && enabled;
            transport = isBlank(transport) ? "stdio" : transport;
            args = args == null ? List.of() : List.copyOf(args);
            env = env == null ? Map.of() : Map.copyOf(env);
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    public record SecurityProperties(CryptoProperties crypto) {

        public SecurityProperties {
            crypto = crypto == null ? new CryptoProperties("ChangeThisDevelopmentCryptoSecret-32CharsMin") : crypto;
        }
    }

    public record CryptoProperties(String encryptionSecret) {

        public CryptoProperties {
            encryptionSecret = encryptionSecret == null || encryptionSecret.isBlank()
                    ? "ChangeThisDevelopmentCryptoSecret-32CharsMin"
                    : encryptionSecret;
        }
    }
}
