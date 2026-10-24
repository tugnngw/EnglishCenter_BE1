package com.sba301.englishcenter.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
@Getter @Setter @NoArgsConstructor
public class Enrollment {
    public enum Status { active, expired, cancelled }
    @Id private UUID enrollmentId;
    private UUID studentId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "course_id") private Course course;
    private UUID paymentTransactionId;
    private LocalDateTime enrolledDate;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "enrollment_status_enum") private Status status;
}
