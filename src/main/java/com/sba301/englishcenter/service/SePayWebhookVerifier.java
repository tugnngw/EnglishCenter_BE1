package com.sba301.englishcenter.service;

import com.sba301.englishcenter.config.SePayProperties;
import com.sba301.englishcenter.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component @RequiredArgsConstructor
public class SePayWebhookVerifier {
    private final SePayProperties properties;

    public void verify(byte[] body, String timestamp, String signature) {
        if (properties.getWebhookSecret().isBlank()) throw new AppException(ErrorCode.SEPAY_NOT_CONFIGURED);
        try {
            long seconds = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (seconds < now - 300 || seconds > now + 300 || signature == null || !signature.matches("sha256=[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException();
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(body);
            byte[] actual = HexFormat.of().parseHex(signature.substring(7));
            if (!MessageDigest.isEqual(expected, actual)) throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
    }
}
