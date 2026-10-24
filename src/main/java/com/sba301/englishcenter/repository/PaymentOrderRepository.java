package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.PaymentOrder;
import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.orderCode = :code")
    Optional<PaymentOrder> lockByCode(String code);

    Optional<PaymentOrder> findByStudentIdAndCourseIdAndStatus(UUID studentId, UUID courseId, PaymentOrderStatus status);
    Page<PaymentOrder> findByStudentId(UUID studentId, Pageable pageable);
    Page<PaymentOrder> findByStatus(PaymentOrderStatus status, Pageable pageable);

    @Modifying
    @Query("update PaymentOrder o set o.status = com.sba301.englishcenter.common.enums.PaymentOrderStatus.EXPIRED where o.status = com.sba301.englishcenter.common.enums.PaymentOrderStatus.PENDING and o.expiresAt <= :now")
    int expirePending(Instant now);
}
