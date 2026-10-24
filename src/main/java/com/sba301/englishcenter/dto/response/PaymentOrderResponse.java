package com.sba301.englishcenter.dto.response;

import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentOrderResponse(UUID orderId, String orderCode, UUID studentId, UUID courseId, String courseTitle,
        BigDecimal amount, String currency, PaymentOrderStatus status, Instant createdAt,
        Instant expiresAt, Instant paidAt, String bankCode, String accountNumber,
        String accountName, String transferContent, String qrUrl) {}
