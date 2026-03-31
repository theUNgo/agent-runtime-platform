package com.example.agentruntime.web;

import com.example.agentruntime.security.ApiKeyCryptoService;
import com.example.agentruntime.security.PublicKeyView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 暴露前端所需的加密公钥，避免敏感字段明文上送。
 */
@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final ApiKeyCryptoService apiKeyCryptoService;

    public SecurityController(ApiKeyCryptoService apiKeyCryptoService) {
        this.apiKeyCryptoService = apiKeyCryptoService;
    }

    /**
     * 返回当前会话可用的加密公钥。
     * 前端拿到后会在浏览器侧先把 API Key 加密，再发送到后端。
     */
    @GetMapping("/public-key")
    public PublicKeyView publicKey() {
        return new PublicKeyView(
                "RSA-OAEP-256",
                "SPKI",
                apiKeyCryptoService.publicKeyBase64()
        );
    }
}
