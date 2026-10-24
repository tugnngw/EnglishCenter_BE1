package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.*;
import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import com.sba301.englishcenter.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor @Tag(name = "Admin Payment", description = "Admin payment order monitoring and SePay webhook reconciliation")
@RequestMapping("/api/v1/admin/payments") @PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {
    private final PaymentService payments;
    private final JdbcTemplate jdbc;
    @GetMapping
    @Operation(summary = "Get payment orders",
            description = "Admin retrieves paginated orders across all students, optionally filtered by payment order status.")
    public ApiResponse<PageResponse<PaymentOrderResponse>> list(@RequestParam(required = false) PaymentOrderStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(payments.adminOrders(status, pageable));
    }
    @GetMapping("/{id}")
    @Operation(summary = "Get payment order details",
            description = "Admin retrieves an order by its UUID, including buyer, course, amount, payment status and bank details.")
    public ApiResponse<PaymentOrderResponse> detail(@PathVariable UUID id) {
        return ApiResponse.success(payments.adminOrder(id));
    }
    @GetMapping("/{id}/webhook-events")
    @Operation(summary = "Get order webhook events",
            description = "Admin retrieves up to 100 latest SePay events for an order, including processing outcomes and original payloads for reconciliation.")
    public ApiResponse<List<Map<String, Object>>> events(@PathVariable UUID id) {
        String code = payments.adminOrder(id).orderCode();
        return ApiResponse.success(jdbc.queryForList("select sepay_id, outcome, raw_payload, received_at from sepay_webhook_events where order_code = ? order by received_at desc limit 100", code));
    }

    @GetMapping("/webhook-events")
    @Operation(summary = "Get recent SePay webhook events",
            description = "Admin retrieves up to 100 latest authenticated webhook events across all orders, including unmatched and review-required transfers.")
    public ApiResponse<List<Map<String, Object>>> recentEvents() {
        return ApiResponse.success(jdbc.queryForList("select sepay_id, order_code, outcome, raw_payload, received_at from sepay_webhook_events order by received_at desc limit 100"));
    }
}
