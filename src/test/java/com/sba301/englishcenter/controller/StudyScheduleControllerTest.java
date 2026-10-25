package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.config.SecurityConfig;
import com.sba301.englishcenter.dto.response.StudyScheduleResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private static final String VALID_BODY = """
            {"title":"Học Lesson 5","scheduledTime":"2099-01-01T12:00:00Z","repeatRule":"weekly","reminderMinutesBefore":30}
            """;

    @Test
    @DisplayName("Chưa đăng nhập -> 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Mentor không có lịch học cá nhân -> 403")
    void mentorForbidden() throws Exception {
        mockMvc.perform(post(BASE).with(as("MENTOR")).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verify(studyScheduleService, never()).create(any(), any());
    }

    @Test
    @DisplayName("Học viên tạo lịch -> 200, dùng userId đang đăng nhập")
    void studentCreates() throws Exception {
        when(studyScheduleService.create(eq(studentId), any()))
                .thenReturn(StudyScheduleResponse.builder().title("Học Lesson 5").status("pending").build());

        mockMvc.perform(post(BASE).with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.status").value("pending"));
    }

    @Test
    @DisplayName("USER (chưa mua khóa) cũng tạo được lịch học cá nhân")
    void learnerCreates() throws Exception {
        when(studyScheduleService.create(eq(studentId), any())).thenReturn(StudyScheduleResponse.builder().build());
        mockMvc.perform(post(BASE).with(as("USER")).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Thiếu tiêu đề / nhắc trước quá 1440 phút -> 400")
    void validation() throws Exception {
        mockMvc.perform(post(BASE).with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"scheduledTime\":\"2099-01-01T12:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE).with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"A\",\"scheduledTime\":\"2099-01-01T12:00:00Z\",\"reminderMinutesBefore\":2000}"))
                .andExpect(status().isBadRequest());
        verify(studyScheduleService, never()).create(any(), any());
    }

    @Test
    @DisplayName("Lịch không thuộc về mình -> 404 + mã 6018")
    void notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(studyScheduleService.updateStatus(eq(studentId), eq(id), any()))
                .thenThrow(new AppException(ErrorCode.STUDY_SCHEDULE_NOT_FOUND));

        mockMvc.perform(put(BASE + "/" + id + "/status").with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"done\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(6018));
    }

    @Test
    @DisplayName("Xóa lịch -> 200")
    void deleteOk() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete(BASE + "/" + id).with(as("STUDENT"))).andExpect(status().isOk());
        verify(studyScheduleService).delete(studentId, id);
    }
}
