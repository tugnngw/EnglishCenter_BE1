package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePaymentOrderRequest(@NotNull UUID courseId) {}
