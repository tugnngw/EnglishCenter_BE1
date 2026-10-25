package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import com.sba301.englishcenter.common.enums.PaymentOrderType;
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
    // COURSE: courseId bắt buộc. MENTOR_SESSION: courseId null, bookingId bắt buộc (V7)
    @Enumerated(EnumType.STRING) @Builder.Default private PaymentOrderType orderType = PaymentOrderType.COURSE;
    private UUID courseId;
    private UUID bookingId;
    // Tên hiển thị của đơn: tên khóa học hoặc "Buổi kèm 1-1 với mentor ..."
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
