package com.example.agentruntime.auth;

import com.example.agentruntime.i18n.MessageService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 读取当前请求的登录用户。
 */
@Service
public class CurrentUserService {

    private final MessageService messageService;

    public CurrentUserService(MessageService messageService) {
        this.messageService = messageService;
    }

    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public AuthenticatedUser requireUser() {
        return currentUser().orElseThrow(() -> new IllegalArgumentException(messageService.get("auth.error.authenticationRequired")));
    }

    /**
     * 获取当前管理员用户。
     * 管理员专用接口和全局资源配置入口都统一通过这里做权限收口。
     */
    public AuthenticatedUser requireAdmin() {
        AuthenticatedUser user = requireUser();
        if (!user.isAdmin()) {
            throw new AccessDeniedException(messageService.get("auth.error.adminRequired"));
        }
        return user;
    }
}
