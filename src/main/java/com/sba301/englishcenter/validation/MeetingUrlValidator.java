package com.sba301.englishcenter.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * Kiểm tra host của link thuộc danh sách nền tảng họp online được phép.
 * So khớp theo host (không dùng contains) để chặn link giả kiểu https://meet.google.com.evil.com
 * hoặc https://facebook.com/?q=zoom.us.
 */
public class MeetingUrlValidator implements ConstraintValidator<MeetingUrl, String> {

    /** Domain gốc được phép — host trùng khớp hoặc là subdomain (VD us05web.zoom.us) */
    private static final List<String> ALLOWED_DOMAINS = List.of(
            "meet.google.com",
            "zoom.us",
            "zoom.com",
            "teams.microsoft.com",
            "teams.live.com",
            "discord.com",
            "discord.gg"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return isAllowed(value);
    }

    public static boolean isAllowed(String value) {
        try {
            URI uri = new URI(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            for (String domain : ALLOWED_DOMAINS) {
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    return true;
                }
            }
            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
