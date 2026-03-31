package com.example.agentruntime.security;

import com.example.agentruntime.AgentRuntimeProperties;
import com.example.agentruntime.i18n.MessageService;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * 负责两条安全链路：
 * 1. 前端到后端：使用 RSA-OAEP 加密 API Key，避免明文上送。
 * 2. 后端入库：使用 AES-GCM 再次加密存储，避免数据库中出现明文。
 */
@Service
public class ApiKeyCryptoService {

    private static final String STORAGE_PREFIX = "enc:v1";
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec storageKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final MessageService messageService;

    public ApiKeyCryptoService(AgentRuntimeProperties properties, MessageService messageService) {
        this.storageKey = new SecretKeySpec(deriveStorageKey(properties.security().crypto().encryptionSecret()), "AES");
        this.messageService = messageService;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(messageService.get("security.error.cryptoInitFailed"), exception);
        }
    }

    /**
     * 返回前端用于 RSA 加密的公钥。
     * 前端只拿到公钥，真正的私钥始终留在服务端内存中。
     */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 解密前端上送的 API Key 密文。
     * 这里使用一次性传输加密，避免 API Key 以明文出现在请求体中。
     */
    public String decryptTransportValue(String encryptedValue) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException(messageService.get("security.error.invalidEncryptedApiKey"), exception);
        }
    }

    /**
     * 对即将入库的 API Key 做二次加密。
     * 即使数据库泄漏，也不会直接暴露模型供应商的明文密钥。
     */
    public String encryptForStorage(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, storageKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return STORAGE_PREFIX
                    + ":" + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(messageService.get("security.error.encryptApiKeyFailed"), exception);
        }
    }

    /**
     * 从数据库中解密 API Key。
     * 为了兼容历史数据，如果发现不是密文前缀，则按旧版明文直接返回。
     */
    public String decryptFromStorage(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        if (!encryptedValue.startsWith(STORAGE_PREFIX + ":")) {
            return encryptedValue;
        }
        String[] parts = encryptedValue.split(":", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException(messageService.get("security.error.malformedStoredApiKey"));
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[2]);
            byte[] encrypted = Base64.getDecoder().decode(parts[3]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, storageKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException(messageService.get("security.error.decryptStoredApiKeyFailed"), exception);
        }
    }

    /**
     * 使用部署侧的加密主密钥派生固定长度的 AES Key。
     */
    private byte[] deriveStorageKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(messageService.get("security.error.deriveStorageKeyFailed"), exception);
        }
    }
}
