package com.sba301.englishcenter;

import com.sba301.englishcenter.config.SePayProperties;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.service.SePayWebhookVerifier;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class SePayWebhookVerifierTest {
    static final String SECRET = "whsec_test_only_never_use_in_live";
    private SePayWebhookVerifier verifier() {
        var properties = new SePayProperties();
        properties.setWebhookSecret(SECRET);
        return new SePayWebhookVerifier(properties);
    }

    static String signature(byte[] body, String timestamp) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test void verifiesOriginalUtf8Bytes() throws Exception {
        byte[] body = "{ \"content\": \"Phan Đức Giao\" }".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        verifier().verify(body, timestamp, signature(body, timestamp));
        assertThrows(AppException.class, () -> verifier().verify("{}".getBytes(), timestamp, signature(body, timestamp)));
    }

    @Test void rejectsStaleFutureMissingAndMalformedHeaders() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        for (long offset : new long[]{-301, 301}) {
            String timestamp = Long.toString(Instant.now().getEpochSecond() + offset);
            assertThrows(AppException.class, () -> verifier().verify(body, timestamp, signature(body, timestamp)));
        }
        assertThrows(AppException.class, () -> verifier().verify(body, null, null));
        assertThrows(AppException.class, () -> verifier().verify(body, "123", "bad"));
    }

    @Test void refusesUnconfiguredSecret() {
        assertThrows(AppException.class, () -> new SePayWebhookVerifier(new SePayProperties()).verify(new byte[0], "0", ""));
    }
}
