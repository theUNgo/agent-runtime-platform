package com.example.agentruntime.web;

import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.model.UserModelProfileService;
import com.example.agentruntime.model.UserModelProfileUpsertRequest;
import com.example.agentruntime.model.UserModelProfileView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前登录用户的模型视图接口。
 *
 * 注意：
 * 1. 模型资源本身由管理员全局维护
 * 2. 普通用户在这里看到的是“平台开放的模型 + 自己的启用/默认关系”
 * 3. create / update / delete 仍然走当前路径，但服务层会强制校验管理员身份
 */
@RestController
@RequestMapping("/api/me/models")
public class UserModelProfileController {

    private final CurrentUserService currentUserService;
    private final UserModelProfileService userModelProfileService;

    public UserModelProfileController(CurrentUserService currentUserService,
                                      UserModelProfileService userModelProfileService) {
        this.currentUserService = currentUserService;
        this.userModelProfileService = userModelProfileService;
    }

    @GetMapping
    public List<UserModelProfileView> list() {
        var user = currentUserService.requireUser();
        return userModelProfileService.list(user.id());
    }

    @GetMapping("/active")
    public UserModelProfileView active() {
        var user = currentUserService.requireUser();
        return userModelProfileService.active(user.id()).orElse(null);
    }

    /**
     * 创建全局模型档案，仅管理员可执行。
     */
    @PostMapping
    public UserModelProfileView create(@RequestBody UserModelProfileUpsertRequest request) {
        var user = currentUserService.requireUser();
        return userModelProfileService.create(user.id(), request);
    }

    /**
     * 更新全局模型档案，仅管理员可执行。
     */
    @PutMapping("/{profileId}")
    public UserModelProfileView update(@PathVariable("profileId") Long profileId,
                                       @RequestBody UserModelProfileUpsertRequest request) {
        var user = currentUserService.requireUser();
        return userModelProfileService.update(user.id(), profileId, request);
    }

    /**
     * 把某个模型设为当前用户默认模型。
     */
    @PostMapping("/{profileId}/activate")
    public UserModelProfileView activate(@PathVariable("profileId") Long profileId) {
        var user = currentUserService.requireUser();
        return userModelProfileService.activate(user.id(), profileId);
    }

    /**
     * 为当前用户启用某个全局模型。
     */
    @PostMapping("/{profileId}/enable")
    public UserModelProfileView enable(@PathVariable("profileId") Long profileId) {
        var user = currentUserService.requireUser();
        return userModelProfileService.enable(user.id(), profileId);
    }

    /**
     * 取消当前用户对某个全局模型的启用关系。
     */
    @PostMapping("/{profileId}/disable")
    public void disable(@PathVariable("profileId") Long profileId) {
        var user = currentUserService.requireUser();
        userModelProfileService.disable(user.id(), profileId);
    }

    /**
     * 删除全局模型档案，仅管理员可执行。
     */
    @DeleteMapping("/{profileId}")
    public void delete(@PathVariable("profileId") Long profileId) {
        var user = currentUserService.requireUser();
        userModelProfileService.delete(user.id(), profileId);
    }
}
