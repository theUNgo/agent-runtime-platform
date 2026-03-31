package com.example.agentruntime.auth;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import com.example.agentruntime.persistence.entity.UserAccountEntity;
import com.example.agentruntime.persistence.entity.UserTokenEntity;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import com.example.agentruntime.persistence.repository.UserTokenRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * 处理登录、登出和 token 校验。
 */
@Service
public class AuthenticationService {

    private final UserAccountRepository userAccountRepository;
    private final UserTokenRepository userTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AgentRuntimeProperties properties;
    private final MessageService messageService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthenticationService(UserAccountRepository userAccountRepository,
                                 UserTokenRepository userTokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 AgentRuntimeProperties properties,
                                 MessageService messageService) {
        this.userAccountRepository = userAccountRepository;
        this.userTokenRepository = userTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.messageService = messageService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccountEntity user = userAccountRepository.findByUsername(request.username())
                .filter(UserAccountEntity::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.invalidCredentials")));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException(messageService.get("auth.error.invalidCredentials"));
        }

        userTokenRepository.deleteByExpiresAtBefore(Instant.now());

        UserTokenEntity token = new UserTokenEntity();
        token.setUser(user);
        token.setToken(generateToken());
        token.setExpiresAt(Instant.now().plus(properties.auth().tokenValidity()));
        userTokenRepository.save(token);

        return new LoginResponse(
                token.getToken(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getRole() == UserRole.ADMIN
        );
    }

    @Transactional
    public void logout(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return;
        }
        userTokenRepository.deleteByToken(tokenValue);
    }

    @Transactional
    public AuthenticatedUser authenticate(String tokenValue) {
        UserTokenEntity token = userTokenRepository.findByToken(tokenValue)
                .filter(value -> value.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.invalidToken")));
        token.setLastUsedAt(Instant.now());
        UserAccountEntity user = token.getUser();
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
