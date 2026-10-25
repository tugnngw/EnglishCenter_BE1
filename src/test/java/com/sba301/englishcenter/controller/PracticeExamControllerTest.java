package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.config.SecurityConfig;
import com.sba301.englishcenter.dto.response.PracticeEvaluationResponse;
import com.sba301.englishcenter.dto.response.PracticeExamResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.AuthTokenRepository;
import com.sba301.englishcenter.security.CustomUserDetailsService;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.security.jwt.JwtAccessDeniedHandler;
import com.sba301.englishcenter.security.jwt.JwtAuthenticationEntryPoint;
import com.sba301.englishcenter.security.jwt.JwtTokenProvider;
import com.sba301.englishcenter.service.PracticeExamService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PracticeExamController.class, AdminPracticeExamController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class PracticeExamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PracticeExamService practiceExamService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;
    @MockitoBean
    private AuthTokenRepository authTokenRepository;

    private final UUID userId = UUID.randomUUID();

    private RequestPostProcessor as(String roleName) {
        return user(UserPrincipal.builder()
                .userId(userId).email("u@test.com").password("x").roleName(roleName).active(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + roleName))).build());
    }

    @Test
    @DisplayName("Khách xem danh sách đề luyện tập không cần đăng nhập")
    void publicList() throws Exception {
        when(practiceExamService.getPracticeExams(isNull(), isNull(), any()))
                .thenReturn(PageResponse.<PracticeExamResponse>builder().data(List.of()).totalElements(0).build());
        mockMvc.perform(get("/api/v1/practice-exams")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Chưa đăng nhập không xem được lịch sử bài làm / kết quả chấm -> 401")
    void privateEndpointsNeedLogin() throws Exception {
        mockMvc.perform(get("/api/v1/practice-exams/me/answers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/practice-exams/evaluations/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Mentor không nộp bài luyện tập -> 403")
    void mentorCannotSubmit() throws Exception {
        mockMvc.perform(post("/api/v1/practice-exams/" + UUID.randomUUID() + "/submit").with(as("MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"My essay\"}"))
                .andExpect(status().isForbidden());
        verify(practiceExamService, never()).submit(any(), any(), any());
    }

    @Test
    @DisplayName("Bài làm rỗng -> 400")
    void blankContent() throws Exception {
        mockMvc.perform(post("/api/v1/practice-exams/" + UUID.randomUUID() + "/submit").with(as("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Chưa cấu hình Gemini -> 503 mã 6027")
    void aiNotConfigured() throws Exception {
        UUID answerId = UUID.randomUUID();
        when(practiceExamService.evaluate(userId, answerId)).thenThrow(new AppException(ErrorCode.AI_NOT_CONFIGURED));
        mockMvc.perform(post("/api/v1/practice-exams/answers/" + answerId + "/ai-evaluate").with(as("STUDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(6027));
    }

    @Test
    @DisplayName("Admin xem được kết quả chấm của mọi học viên (isAdmin = true)")
    void adminSeesEvaluation() throws Exception {
        UUID evaluationId = UUID.randomUUID();
        when(practiceExamService.getEvaluation(eq(userId), eq(true), eq(evaluationId)))
                .thenReturn(PracticeEvaluationResponse.builder().evaluationId(evaluationId).bandScore(new BigDecimal("6.5")).build());
        mockMvc.perform(get("/api/v1/practice-exams/evaluations/" + evaluationId).with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.bandScore").value(6.5));
    }

    @Test
    @DisplayName("Học viên không gọi được API admin -> 403")
    void studentCannotManage() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/practice-exams/" + UUID.randomUUID()).with(as("STUDENT")))
                .andExpect(status().isForbidden());
        verify(practiceExamService, never()).delete(any());
    }
}
