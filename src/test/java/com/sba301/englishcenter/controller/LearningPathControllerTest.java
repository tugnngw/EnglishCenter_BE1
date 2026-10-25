package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.config.SecurityConfig;
import com.sba301.englishcenter.dto.response.LearningPathProgressResponse;
import com.sba301.englishcenter.dto.response.LearningPathResponse;
import com.sba301.englishcenter.repository.AuthTokenRepository;
import com.sba301.englishcenter.security.CustomUserDetailsService;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.security.jwt.JwtAccessDeniedHandler;
import com.sba301.englishcenter.security.jwt.JwtAuthenticationEntryPoint;
import com.sba301.englishcenter.security.jwt.JwtTokenProvider;
import com.sba301.englishcenter.service.LearningPathService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {LearningPathController.class, AdminLearningPathController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class LearningPathControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LearningPathService learningPathService;
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
    @DisplayName("Khách xem danh sách lộ trình không cần đăng nhập")
    void publicList() throws Exception {
        when(learningPathService.getLearningPaths(any(), any()))
                .thenReturn(PageResponse.<LearningPathResponse>builder().data(List.of()).totalElements(0).build());
        mockMvc.perform(get("/api/v1/learning-paths")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER chưa mua khóa không ghi danh lộ trình -> 403")
    void learnerCannotEnroll() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/learning-paths/" + UUID.randomUUID() + "/enroll").with(as("USER")))
                .andExpect(status().isForbidden());
        verify(learningPathService, never()).enroll(any(), any());
    }

    @Test
    @DisplayName("Học viên ghi danh -> 200, dùng userId đang đăng nhập")
    void studentEnrolls() throws Exception {
        UUID pathId = UUID.randomUUID();
        when(learningPathService.enroll(userId, pathId))
                .thenReturn(LearningPathProgressResponse.builder().learningPathId(pathId).status("active").build());
        mockMvc.perform(post("/api/v1/users/me/learning-paths/" + pathId + "/enroll").with(as("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("active"));
    }

    @Test
    @DisplayName("Tiến độ cần đăng nhập -> 401")
    void progressNeedsLogin() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/learning-paths/progress")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin tạo lộ trình thiếu chặng -> 400; mentor gọi API admin -> 403")
    void adminValidationAndRole() throws Exception {
        String body = "{\"certificateId\":\"" + UUID.randomUUID() + "\",\"name\":\"IELTS 6.5\",\"items\":[]}";
        mockMvc.perform(post("/api/v1/admin/learning-paths").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/learning-paths").with(as("MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(learningPathService, never()).create(any(), any());
    }
}
