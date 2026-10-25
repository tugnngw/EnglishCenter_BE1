package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BookScheduleRequest {

    @NotNull(message = "scheduleId không được để trống")
    private UUID scheduleId;
}
