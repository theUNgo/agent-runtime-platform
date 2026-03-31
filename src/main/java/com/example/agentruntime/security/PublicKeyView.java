package com.example.agentruntime.security;

/**
 * 前端用于加密 API Key 的公钥响应。
 */
public record PublicKeyView(
        String algorithm,
        String keyFormat,
        String publicKey
) {
}
