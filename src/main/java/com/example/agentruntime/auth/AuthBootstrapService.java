package com.example.agentruntime.auth;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 初始化默认管理员账号。
 */
@Component
public class AuthBootstrapService implements ApplicationRunner {

    private final AgentRuntimeProperties properties;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthBootstrapService(AgentRuntimeProperties properties,
                                UserAccountRepository userAccountRepository,
                                PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (!properties.auth().enabled()) {
            return;
        }
        // 公开仓库默认不携带管理员初始密码，因此未显式配置密码时跳过引导账号创建。
        if (properties.auth().bootstrapPassword() == null || properties.auth().bootstrapPassword().isBlank()) {
            return;
        }
        userAccountRepository.findByUsername(properties.auth().bootstrapUsername())
                .map(existing -> {
                    existing.setRole(properties.auth().bootstrapRole());
                    existing.setDisplayName(properties.auth().bootstrapDisplayName());
                    existing.setEnabled(true);
                    return userAccountRepository.save(existing);
                })
                .orElseGet(() -> {
                    UserAccountEntity user = new UserAccountEntity();
                    user.setUsername(properties.auth().bootstrapUsername());
                    user.setDisplayName(properties.auth().bootstrapDisplayName());
                    user.setPasswordHash(passwordEncoder.encode(properties.auth().bootstrapPassword()));
                    user.setRole(properties.auth().bootstrapRole());
                    user.setEnabled(true);
                    return userAccountRepository.save(user);
                });
    }
}
