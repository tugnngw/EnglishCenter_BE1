package com.sba301.englishcenter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@org.springframework.scheduling.annotation.EnableScheduling
@ConfigurationProperties(prefix = "sepay")
@Getter @Setter
public class SePayProperties {
    private String webhookSecret = "";
    private String bankCode = "";
    private String accountNumber = "";
    private String accountName = "";
    private String paymentPrefix = "EC";
    private int orderExpiryMinutes = 15;
}
