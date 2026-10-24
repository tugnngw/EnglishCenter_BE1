package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_orders")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentOrder {
    @Id private UUID orderId;
    @Column(nullable = false, unique = true) private String orderCode;
    @Column(nullable = false) private UUID studentId;
    @Column(nullable = false) private UUID courseId;
    private String courseTitle;
    private BigDecimal amount;
    private String bankCode;
    private String accountNumber;
    private String accountName;
    @Enumerated(EnumType.STRING) private PaymentOrderStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant paidAt;
}
