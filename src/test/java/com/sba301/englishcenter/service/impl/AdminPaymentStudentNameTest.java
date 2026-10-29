package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import com.sba301.englishcenter.entity.PaymentOrder;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.repository.PaymentOrderRepository;
import com.sba301.englishcenter.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPaymentStudentNameTest {
    @Mock PaymentOrderRepository orders;
    @Mock UserRepository users;
    @InjectMocks PaymentServiceImpl payments;

    @Test
    void orderPageMatchesEachBuyerNameAndKeepsPagination() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var firstOrder = order(first);
        var secondOrder = order(second);
        var pageable = PageRequest.of(1, 2);
        when(orders.findAll(pageable)).thenReturn(new PageImpl<>(List.of(firstOrder, secondOrder), pageable, 5));
        when(users.findAllById(any())).thenReturn(List.of(
                User.builder().userId(second).fullName("Trần Bình").build(),
                User.builder().userId(first).fullName("Nguyễn An").build()));

        var result = payments.adminOrders(null, pageable);

        assertEquals("Nguyễn An", result.getData().get(0).studentName());
        assertEquals("Trần Bình", result.getData().get(1).studentName());
        assertEquals(first, result.getData().get(0).studentId());
        assertEquals(1, result.getCurrentPage());
        assertEquals(5, result.getTotalElements());
        verify(users).findAllById(List.of(first, second));
        verify(users, never()).findById(any());
    }

    @Test
    void orderDetailIncludesBuyerName() {
        UUID student = UUID.randomUUID();
        var order = order(student);
        when(orders.findById(order.getOrderId())).thenReturn(Optional.of(order));
        when(users.findById(student)).thenReturn(Optional.of(User.builder().userId(student).fullName("Nguyễn An").build()));

        assertEquals("Nguyễn An", payments.adminOrder(order.getOrderId()).studentName());
    }

    private PaymentOrder order(UUID studentId) {
        return PaymentOrder.builder().orderId(UUID.randomUUID()).studentId(studentId)
                .status(PaymentOrderStatus.PAID).build();
    }
}
