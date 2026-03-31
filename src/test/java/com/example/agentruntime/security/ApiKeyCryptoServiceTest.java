package com.example.agentruntime.security;

import com.example.agentruntime.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import com.example.agentruntime.i18n.MessageService;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyCryptoServiceTest {

    private final ApiKeyCryptoService service = new ApiKeyCryptoService(
            new AgentRuntimeProperties(
                    "./skills",
                    ".",
                    null,
                    null,
                    new AgentRuntimeProperties.SecurityProperties(
                            new AgentRuntimeProperties.CryptoProperties("UnitTestSecret-32Chars-Minimum-123456")
                    )
            ),
            messageService()
    );

    @Test
    void shouldEncryptAndDecryptStorageValue() {
        String encrypted = service.encryptForStorage("sk-test-secret");

        assertTrue(encrypted.startsWith("enc:v1:"));
        assertEquals("sk-test-secret", service.decryptFromStorage(encrypted));
    }

    @Test
    void shouldDecryptFrontendTransportCiphertext() throws Exception {
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(service.publicKeyBase64())));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal("sk-transport-secret".getBytes()));

        assertEquals("sk-transport-secret", service.decryptTransportValue(encrypted));
    }

    private MessageService messageService() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("security.error.cryptoInitFailed", Locale.ENGLISH, "init failed");
        source.addMessage("security.error.invalidEncryptedApiKey", Locale.ENGLISH, "invalid encrypted");
        source.addMessage("security.error.encryptApiKeyFailed", Locale.ENGLISH, "encrypt failed");
        source.addMessage("security.error.malformedStoredApiKey", Locale.ENGLISH, "malformed");
        source.addMessage("security.error.decryptStoredApiKeyFailed", Locale.ENGLISH, "decrypt failed");
        source.addMessage("security.error.deriveStorageKeyFailed", Locale.ENGLISH, "derive failed");
        return new MessageService(source);
    }
}
