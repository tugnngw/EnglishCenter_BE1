package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.*;
import com.sba301.englishcenter.dto.request.CreatePaymentOrderRequest;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import com.sba301.englishcenter.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor @Tag(name = "Payment", description = "SePay QR checkout and payment history")
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;

    @PostMapping("/orders") @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Create course payment order",
            description = "User or student creates a SePay QR order using the course price from the database. Reuses an unexpired pending order; a USER becomes STUDENT only after successful settlement.")
    public ApiResponse<PaymentOrderResponse> create(@Valid @RequestBody CreatePaymentOrderRequest request) {
        return ApiResponse.success(payments.createOrder(request.courseId()));
    }
    @GetMapping("/orders/{orderCode}")
    @Operation(summary = "Get my payment order",
            description = "Retrieve an order's status, payment instructions and QR URL. Only the order owner can access it.")
    public ApiResponse<PaymentOrderResponse> get(@PathVariable String orderCode) {
        return ApiResponse.success(payments.getOrder(orderCode));
    }
    @PostMapping("/orders/{orderCode}/cancel")
    @Operation(summary = "Cancel my pending payment order",
            description = "Cancel an owned pending order. This does not reverse a bank transfer; money arriving after cancellation is held for review.")
    public ApiResponse<PaymentOrderResponse> cancel(@PathVariable String orderCode) {
        return ApiResponse.success(payments.cancelOrder(orderCode));
    }
    @GetMapping("/orders")
    @Operation(summary = "Get my payment orders",
            description = "Retrieve the current user's paginated payment orders, including pending, paid, cancelled, expired and review-required orders.")
    public ApiResponse<PageResponse<PaymentOrderResponse>> orders(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(payments.myOrders(pageable));
    }
    // SePay requires this acknowledgement, rather than the application's ApiResponse envelope.
    @PostMapping(value = "/sepay/webhook", consumes = "application/json")
    @Operation(summary = "Receive SePay payment webhook",
            description = "SePay sends the original JSON body with X-SePay-Timestamp and X-SePay-Signature HMAC headers. Validates payment and grants enrollment once. Returns {success:true}; no JWT is required.")
    public Map<String, Boolean> webhook(@RequestBody byte[] body,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature) {
        payments.processWebhook(body, timestamp, signature);
        return Map.of("success", true);
    }
}
