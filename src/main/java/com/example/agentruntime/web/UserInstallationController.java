package com.example.agentruntime.web;

import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.catalog.CatalogItemType;
import com.example.agentruntime.installation.UserInstallationService;
import com.example.agentruntime.installation.UserInstallationView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户的安装视图。
 */
@RestController
@RequestMapping("/api/me/installations")
public class UserInstallationController {

    private final CurrentUserService currentUserService;
    private final UserInstallationService userInstallationService;

    public UserInstallationController(CurrentUserService currentUserService,
                                      UserInstallationService userInstallationService) {
        this.currentUserService = currentUserService;
        this.userInstallationService = userInstallationService;
    }

    @GetMapping
    public List<UserInstallationView> list() {
        var user = currentUserService.requireUser();
        return userInstallationService.listForUser(user.id());
    }

    @DeleteMapping("/{itemType}/{itemId}")
    public void remove(@PathVariable("itemType") CatalogItemType itemType,
                       @PathVariable("itemId") String itemId) {
        var user = currentUserService.requireUser();
        userInstallationService.remove(user.id(), itemType, itemId);
    }
}
