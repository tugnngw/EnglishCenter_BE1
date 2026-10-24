package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.PaymentTransaction;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Page<PaymentTransaction> findByStudentId(UUID studentId, Pageable pageable);
}
