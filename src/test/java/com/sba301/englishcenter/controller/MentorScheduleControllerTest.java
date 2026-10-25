package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.config.SecurityConfig;
import com.sba301.englishcenter.dto.response.MentorScheduleResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.AuthTokenRepository;
import com.sba301.englishcenter.security.CustomUserDetailsService;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.security.jwt.JwtAccessDeniedHandler;
import com.sba301.englishcenter.security.jwt.JwtAuthenticationEntryPoint;
import com.sba301.englishcenter.security.jwt.JwtTokenProvider;
import com.sba301.englishcenter.service.MentorScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MentorScheduleController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class MentorScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MentorScheduleService mentorScheduleService;

    // Các bean mà JwtAuthenticationFilter cần; request test không gửi JWT nên filter chỉ cho đi qua
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;
    @MockitoBean
    private AuthTokenRepository authTokenRepository;

    private final UUID scheduleId = UUID.randomUUID();
    private final UUID mentorId = UUID.randomUUID();

    // Giả lập người dùng đăng nhập bằng UserPrincipal thật để SecurityUtils lấy được userId
    private static RequestPostProcessor as(String roleName) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .email(roleName.toLowerCase() + "@test.com")
                .password("x")
                .roleName(roleName)
                .active(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + roleName)))
                .build();
        return user(principal);
    }

    // Thời gian gửi dạng ISO-8601 UTC, vd "2026-10-25T12:30:00Z"
    private String slotJson(Instant start, Instant end) {
        return "{\"startTime\":\"" + start + "\",\"endTime\":\"" + end
                + "\",\"meetingUrl\":\"https://meet.google.com/abc-defg-hij\"}";
    }

    private Instant tomorrow() {
        return Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private MentorScheduleResponse response(String status) {
        return MentorScheduleResponse.builder().scheduleId(scheduleId).mentorId(mentorId).status(status).build();
    }

    // ===================== POST /mentor/availability =====================

    @Test
    @DisplayName("Chưa đăng nhập thì không mở slot được -> 401")
    void createSlot_unauthenticated() throws Exception {
        Instant start = tomorrow();
        mockMvc.perform(post("/api/v1/mentor/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(mentorScheduleService);
    }

    @Test
    @DisplayName("Học viên không được mở slot -> 403")
    void createSlot_studentForbidden() throws Exception {
        Instant start = tomorrow();
        mockMvc.perform(post("/api/v1/mentor/availability").with(as("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mentorScheduleService);
    }

    @Test
    @DisplayName("Mở slot ở quá khứ -> 400 validation")
    void createSlot_pastTime() throws Exception {
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(post("/api/v1/mentor/availability").with(as("MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
        verifyNoInteractions(mentorScheduleService);
    }

    @Test
    @DisplayName("Mentor mở slot thành công -> 200")
    void createSlot_success() throws Exception {
        Instant start = tomorrow();
        when(mentorScheduleService.createSlot(any(), any())).thenReturn(response("available"));

        mockMvc.perform(post("/api/v1/mentor/availability").with(as("MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.status").value("available"));
    }

    @Test
    @DisplayName("Service báo trùng giờ -> 409 với mã lỗi chuẩn")
    void createSlot_conflict() throws Exception {
        Instant start = tomorrow();
        when(mentorScheduleService.createSlot(any(), any()))
                .thenThrow(new AppException(ErrorCode.SCHEDULE_TIME_CONFLICT));

        mockMvc.perform(post("/api/v1/mentor/availability").with(as("MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SCHEDULE_TIME_CONFLICT.getCode()));
    }

    // ===================== GET / PUT / DELETE /mentor/availability =====================

    @Test
    @DisplayName("Học viên không xem được /mentor/availability -> 403")
    void getMySlots_studentForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/mentor/availability").with(as("STUDENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mentorScheduleService);
    }

    @Test
    @DisplayName("Mentor xem slot của mình -> 200")
    void getMySlots_success() throws Exception {
        PageResponse<MentorScheduleResponse> page = PageResponse.<MentorScheduleResponse>builder()
                .currentPage(0).pageSize(20).totalPages(1).totalElements(1)
                .data(List.of(response("booked")))
                .build();
        when(mentorScheduleService.getMySlots(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/mentor/availability").with(as("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].status").value("booked"));
    }

    @Test
    @DisplayName("Sửa slot thiếu status -> 400 validation")
    void updateSlot_missingStatus() throws Exception {
        Instant start = tomorrow();
        mockMvc.perform(put("/api/v1/mentor/availability/{id}", scheduleId).with(as("MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotJson(start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mentorScheduleService);
    }

    @Test
    @DisplayName("Đóng slot -> 200")
    void updateSlot_close() throws Exception {
        Instant start = tomorrow();
        when(mentorScheduleService.updateSlot(any(), eq(scheduleId), any())).thenReturn(response("cancelled"));

        mockMvc.perform(put("/api/v1/mentor/availability/{id}", scheduleId).with(as("MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + start + "\",\"endTime\":\"" + start.plus(1, ChronoUnit.HOURS)
                                + "\",\"status\":\"cancelled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("cancelled"));
    }

    @Test
    @DisplayName("Xóa slot còn booking -> 409")
    void deleteSlot_hasBooking() throws Exception {
        doThrow(new AppException(ErrorCode.SCHEDULE_HAS_BOOKING))
                .when(mentorScheduleService).deleteSlot(any(), eq(scheduleId));

        mockMvc.perform(delete("/api/v1/mentor/availability/{id}", scheduleId).with(as("MENTOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SCHEDULE_HAS_BOOKING.getCode()));
    }

    // ===================== GET /mentors/{mentorId}/availability =====================

    @Test
    @DisplayName("Khách chưa đăng nhập xem được lịch trống của mentor -> 200")
    void getAvailability_public() throws Exception {
        when(mentorScheduleService.getAvailability(eq(mentorId), any(), any()))
                .thenReturn(List.of(response("available")));

        mockMvc.perform(get("/api/v1/mentors/{mentorId}/availability", mentorId)
                        .param("from", "2026-10-25T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].status").value("available"));
        verify(mentorScheduleService).getAvailability(eq(mentorId), eq(Instant.parse("2026-10-25T00:00:00Z")), any());
    }
}
