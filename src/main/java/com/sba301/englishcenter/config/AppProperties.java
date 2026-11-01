package com.sba301.englishcenter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Thuộc tính luồng kích hoạt tài khoản qua email.
 * Prefix "app" gom các key app.* trong application.properties.
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private final Frontend frontend = new Frontend();
    private final Auth auth = new Auth();

    @Getter
    @Setter
    public static class Frontend {
        /** Base URL FE dùng dựng link kích hoạt trong email (VD: http://localhost:5173) */
        private String baseUrl = "http://localhost:5173";
    }

    @Getter
    @Setter
    public static class Auth {
        private final Activation activation = new Activation();

        @Getter
        @Setter
        public static class Activation {
            /** Thời hạn hiệu lực của link kích hoạt tài khoản (giờ) */
            private int tokenExpiryHours = 24;
        }
    }
}
