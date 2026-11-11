package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.config.SecurityConfig;
import com.sba301.englishcenter.dto.response.BookingResponse;
import com.sba301.englishcenter.repository.AuthTokenRepository;
import com.sba301.englishcenter.security.CustomUserDetailsService;
import com.sba301.englishcenter.security.jwt.JwtAccessDeniedHandler;
import com.sba301.englishcenter.security.jwt.JwtAuthenticationEntryPoint;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.security.jwt.JwtTokenProvider;
import com.sba301.englishcenter.service.StudyScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StudyScheduleController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class StudyScheduleControllerTest {

    private static final String BASE = "/api/v1/users/me/study-schedules";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyScheduleService studyScheduleService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;
    @MockitoBean
    private AuthTokenRepository authTokenRepository;

    private final UUID studentId = UUID.randomUUID();

    private RequestPostProcessor as(String roleName) {
        return user(UserPrincipal.builder()
                .userId(studentId).email("u@test.com").password("x").roleName(roleName).active(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + roleName))).build());
    }

    private PageResponse<BookingResponse> page(BookingResponse... items) {
        return PageResponse.<BookingResponse>builder()
                .currentPage(0).pageSize(20).totalPages(1).totalElements(items.length)
                .data(List.of(items)).build();
    }

    @Test
    @DisplayName("Chưa đăng nhập -> 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Mentor không xem lịch học của học viên -> 403")
    void mentorForbidden() throws Exception {
        mockMvc.perform(get(BASE).with(as("MENTOR"))).andExpect(status().isForbidden());
        verify(studyScheduleService, never()).getMySchedules(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Học viên xem lịch -> trả các buổi kèm 1-1 của mình")
    void studentGetsMentorSessions() throws Exception {
        when(studyScheduleService.getMySchedules(eq(studentId), isNull(), isNull(), isNull(), any()))
                .thenReturn(page(BookingResponse.builder()
                        .mentorName("TH.s Nguyen Van A").status("confirmed").build()));

        mockMvc.perform(get(BASE).with(as("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mentorName").value("TH.s Nguyen Van A"))
                .andExpect(jsonPath("$.data[0].status").value("confirmed"));
    }

    @Test
    @DisplayName("Lọc theo trạng thái buổi học")
    void filterByStatus() throws Exception {
        when(studyScheduleService.getMySchedules(eq(studentId), isNull(), isNull(), eq(BookingStatus.completed), any()))
                .thenReturn(page());

        mockMvc.perform(get(BASE).param("status", "completed").with(as("STUDENT")))
                .andExpect(status().isOk());
        verify(studyScheduleService).getMySchedules(eq(studentId), isNull(), isNull(), eq(BookingStatus.completed), any());
    }

    @Test
    @DisplayName("USER (chưa có gói kèm) vẫn gọi được, nhận danh sách rỗng")
    void learnerGetsEmpty() throws Exception {
        when(studyScheduleService.getMySchedules(eq(studentId), any(), any(), any(), any())).thenReturn(page());
        mockMvc.perform(get(BASE).with(as("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
