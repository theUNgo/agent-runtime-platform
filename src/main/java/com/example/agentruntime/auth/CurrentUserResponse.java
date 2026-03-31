package com.example.agentruntime.auth;

public record CurrentUserResponse(
        Long userId,
        String username,
        String displayName,
        String role,
        boolean admin
) {
}
