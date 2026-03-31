package com.example.agentruntime.web;

import com.example.agentruntime.auth.AuthenticationService;
import com.example.agentruntime.auth.CurrentUserResponse;
import com.example.agentruntime.auth.CurrentUserService;
import com.example.agentruntime.auth.LoginRequest;
import com.example.agentruntime.auth.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录与身份接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthenticationService authenticationService, CurrentUserService currentUserService) {
        this.authenticationService = authenticationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        var user = currentUserService.requireUser();
        return new CurrentUserResponse(user.id(), user.username(), user.displayName(), user.role().name(), user.isAdmin());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        String token = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            token = authorizationHeader.substring("Bearer ".length()).trim();
        }
        authenticationService.logout(token);
        return Map.of("status", "ok");
    }
}
