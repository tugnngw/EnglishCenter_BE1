package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.config.SecurityConfig;
import com.sba301.englishcenter.dto.response.BookingResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.AuthTokenRepository;
import com.sba301.englishcenter.security.CustomUserDetailsService;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.security.jwt.JwtAccessDeniedHandler;
import com.sba301.englishcenter.security.jwt.JwtAuthenticationEntryPoint;
import com.sba301.englishcenter.security.jwt.JwtTokenProvider;
import com.sba301.englishcenter.service.BookingService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BookingController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    // Các bean mà JwtAuthenticationFilter cần; request test không gửi JWT nên filter chỉ cho đi qua
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;
    @MockitoBean
    private AuthTokenRepository authTokenRepository;

    private final UUID scheduleId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

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

    private BookingResponse response(String status) {
        return BookingResponse.builder().bookingId(bookingId).scheduleId(scheduleId).status(status).build();
    }

    private PageResponse<BookingResponse> page(BookingResponse... items) {
        return PageResponse.<BookingResponse>builder()
                .currentPage(0).pageSize(20).totalPages(1).totalElements(items.length)
                .data(List.of(items))
                .build();
    }

    // ===================== cancel =====================

    @Test
    @DisplayName("Hủy lịch không gửi body -> vẫn hợp lệ (lý do không bắt buộc)")
    void cancel_withoutBody() throws Exception {
        when(bookingService.cancel(any(), eq(bookingId), isNull())).thenReturn(response("cancelled"));

        mockMvc.perform(put("/api/v1/bookings/{id}/cancel", bookingId).with(as("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("cancelled"));
    }

    @Test
    @DisplayName("Hủy quá hạn -> 400 với mã lỗi chuẩn")
    void cancel_deadlinePassed() throws Exception {
        when(bookingService.cancel(any(), eq(bookingId), any()))
                .thenThrow(new AppException(ErrorCode.BOOKING_CANCEL_DEADLINE_PASSED));

        mockMvc.perform(put("/api/v1/bookings/{id}/cancel", bookingId).with(as("MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Bận\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKING_CANCEL_DEADLINE_PASSED.getCode()));
    }

    // ===================== lịch của tôi / mentor / admin =====================

    @Test
    @DisplayName("Chưa đăng nhập xem lịch của tôi -> 401")
    void mySchedule_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/me/schedule"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("Học viên xem lịch đã qua: type=past")
    void mySchedule_past() throws Exception {
        when(bookingService.getStudentSchedule(any(), eq(true), any())).thenReturn(page(response("completed")));

        mockMvc.perform(get("/api/v1/me/schedule").param("type", "past").with(as("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("completed"));
    }

    @Test
    @DisplayName("Mentor xem lịch dạy sắp tới (mặc định)")
    void mentorSchedule_upcoming() throws Exception {
        when(bookingService.getMentorSchedule(any(), eq(false), any())).thenReturn(page(response("confirmed")));

        mockMvc.perform(get("/api/v1/mentor/schedule").with(as("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Mentor cập nhật kết quả buổi học -> 200")
    void updateStatus_success() throws Exception {
        when(bookingService.updateStatusByMentor(any(), eq(bookingId), any())).thenReturn(response("no_show"));

        mockMvc.perform(put("/api/v1/mentor/bookings/{id}/status", bookingId).with(as("MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"no_show\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("no_show"));
    }

    @Test
    @DisplayName("Học viên không xem được danh sách booking của admin -> 403")
    void adminBookings_studentForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings").with(as("STUDENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("Admin lọc booking theo trạng thái -> 200")
    void adminBookings_filter() throws Exception {
        when(bookingService.getAllBookings(eq(BookingStatus.confirmed), isNull(), isNull(), any()))
                .thenReturn(page(response("confirmed")));

        mockMvc.perform(get("/api/v1/admin/bookings").param("status", "confirmed").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("confirmed"));
        verify(bookingService).getAllBookings(eq(BookingStatus.confirmed), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("Admin xem chi tiết booking -> service nhận cờ admin = true")
    void getBooking_admin() throws Exception {
        when(bookingService.getBooking(any(), eq(true), eq(bookingId))).thenReturn(response("confirmed"));

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId).with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.bookingId").value(bookingId.toString()));
    }
}
