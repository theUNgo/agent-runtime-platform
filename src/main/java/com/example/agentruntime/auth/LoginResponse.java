package com.example.agentruntime.auth;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        String displayName,
        String role,
        boolean admin
) {
}
