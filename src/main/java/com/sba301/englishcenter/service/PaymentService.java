package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import com.sba301.englishcenter.entity.PaymentTransaction;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface PaymentService {
    PaymentOrderResponse createOrder(UUID courseId);
    PaymentOrderResponse getOrder(String code);
    PaymentOrderResponse cancelOrder(String code);
    PageResponse<PaymentTransaction> myPayments(Pageable pageable);
    PageResponse<PaymentOrderResponse> myOrders(Pageable pageable);
    PageResponse<PaymentOrderResponse> adminOrders(PaymentOrderStatus status, Pageable pageable);
    PaymentOrderResponse adminOrder(UUID id);
    void processWebhook(byte[] body, String timestamp, String signature);
}
