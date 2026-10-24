package com.sba301.englishcenter.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentTransaction {
    public enum Status { pending, success, failed }
    public enum Type { course_purchase, mentor_session, trial_exam_access }
    @Id private UUID transactionId;
    private UUID studentId;
    private UUID courseId;
    private UUID orderId;
    private BigDecimal amount;
    private String paymentMethod;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "transaction_status_enum") private Status transactionStatus;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "transaction_type_enum") private Type transactionType;
    private LocalDateTime paymentDate;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private Long sepayTransactionId;
    private String providerReference;
}
