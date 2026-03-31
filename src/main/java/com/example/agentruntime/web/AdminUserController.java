package com.example.agentruntime.web;

import com.example.agentruntime.auth.AdminUserView;
import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.persistence.repository.UserAccountRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员用户查询接口。
 * 当前先提供最小用户列表，供模型授权面板使用。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final CurrentUserService currentUserService;
    private final UserAccountRepository userAccountRepository;

    public AdminUserController(CurrentUserService currentUserService,
                               UserAccountRepository userAccountRepository) {
        this.currentUserService = currentUserService;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public List<AdminUserView> list() {
        currentUserService.requireAdmin();
        return userAccountRepository.findAll().stream()
                .map(user -> new AdminUserView(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        user.getRole().name(),
                        user.isEnabled()
                ))
                .toList();
    }
}
