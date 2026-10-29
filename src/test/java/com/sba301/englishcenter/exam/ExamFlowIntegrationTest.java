package com.sba301.englishcenter.exam;

import com.jayway.jsonpath.JsonPath;
import com.sba301.englishcenter.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Integration test end-to-end cho module Exam: Flyway chạy V1..V5 rồi đi hết luồng
 * admin tạo đề -> publish -> học viên làm bài -> nộp -> mentor chấm -> kết quả,
 * cùng các ca: nộp trùng, nộp đồng thời, hết giờ, vượt số lượt, đề private, file init dữ liệu mẫu.
 * <p>
 * Giống PaymentIntegrationTest: chỉ chạy khi có biến EXAM_TEST_DB_URL trỏ tới một DB PostgreSQL
 * RIÊNG dùng để test (KHÔNG phải DB của ứng dụng), ví dụ:
 * <pre>
 * docker run -d --name exam-test-db -e POSTGRES_PASSWORD=dev3-test -p 55433:5432 postgres:16-alpine
 * EXAM_TEST_DB_URL=jdbc:postgresql://localhost:55433/postgres mvn test -Dtest=ExamFlowIntegrationTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "EXAM_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${EXAM_TEST_DB_URL}", "spring.datasource.username=postgres",
        "spring.datasource.password=${EXAM_TEST_DB_PASSWORD:dev3-test}", "spring.docker.compose.enabled=false",
        "spring.jpa.show-sql=false", "logging.level.org.flywaydb=INFO", "exam.sweeper.enabled=false",
        "jwt.signer-key=0123456789012345678901234567890123456789012345678901234567890123"
})
@AutoConfigureMockMvc
class ExamFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UsernamePasswordAuthenticationToken admin;
    private UsernamePasswordAuthenticationToken student;

    private record Response(int status, String body) {
        <T> T read(String path) {
            return JsonPath.read(body, path);
        }
    }

    private UsernamePasswordAuthenticationToken auth(UUID userId, String role) {
        UserPrincipal principal = UserPrincipal.builder().userId(userId).email(role + "@test.local")
                .roleName(role).active(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role))).build();
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void setUpUsers() {
        UUID adminId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = 'admin@gmail.com'", UUID.class);
        admin = auth(adminId, "ADMIN");
        student = auth(createStudent(), "STUDENT");
    }

    private UUID createStudent() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, email, password, full_name, role_id) VALUES (?, ?, 'x', 'Student', "
                + "(SELECT role_id FROM roles WHERE role_name = 'STUDENT'))", id, id + "@test.local");
        return id;
    }

    private UUID createCertificate() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO certificates (certificate_id, created_by_user_id, certificate_name) "
                + "VALUES (?, (SELECT user_id FROM users WHERE email = 'admin@gmail.com'), 'IELTS test')", id);
        return id;
    }

    private Response call(MockHttpServletRequestBuilder request, UsernamePasswordAuthenticationToken who,
                          String json) throws Exception {
        if (who != null) {
            request.with(authentication(who));
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        MvcResult result = mvc.perform(request).andReturn();
        return new Response(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    @Test
    void fullExamLifecycle() throws Exception {
        setUpUsers();
        UUID certificateId = createCertificate();
        String code = "IT-EXAM-" + System.nanoTime();

        // ---------- Admin tạo đề (examType "final" kiểm tra converter cho nhãn trùng từ khóa Java)
        Response created = call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"trial","certificateId":"%s","examCode":"%s","examType":"final",
                 "examTitle":"Integration exam","durationMinutes":30,"maxAttempts":1,"passingScore":50,
                 "recommendedLevel":"B1"}""".formatted(certificateId, code));
        assertThat(created.status()).as(created.body()).isEqualTo(200);
        String examId = created.read("$.result.exam.examId");
        assertThat((String) created.read("$.result.exam.examType")).isEqualTo("final");

        assertThat(call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"trial","certificateId":"%s","examCode":"%s","examTitle":"x","durationMinutes":5}
                """.formatted(certificateId, code)).status()).isEqualTo(409);
        assertThat(call(post("/api/v1/admin/exams"), student, "{}").status()).isEqualTo(403);

        // ---------- Sections & câu hỏi
        String readingId = call(post("/api/v1/admin/exams/" + examId + "/sections"), admin,
                "{\"title\":\"Reading\",\"sectionType\":\"reading\"}").read("$.result.sectionId");
        String writingId = call(post("/api/v1/admin/exams/" + examId + "/sections"), admin,
                "{\"title\":\"Writing\",\"sectionType\":\"WRITING\"}").read("$.result.sectionId");

        Response mc = call(post("/api/v1/admin/exams/" + examId + "/questions"), admin, """
                {"sectionId":"%s","questionType":"multiple_choice","questionText":"Pick A","points":1,
                 "options":[{"optionText":"A","isCorrect":true},{"optionText":"B"}]}""".formatted(readingId));
        assertThat(mc.status()).as(mc.body()).isEqualTo(200);
        String mcId = mc.read("$.result.questionId");
        String mcCorrect = mc.read("$.result.options[0].optionId");
        String fbId = call(post("/api/v1/admin/exams/" + examId + "/questions"), admin, """
                {"sectionId":"%s","questionType":"fill_blank","questionText":"He ___ home.","points":1,
                 "options":[{"optionText":"went"}]}""".formatted(readingId)).read("$.result.questionId");
        String matchId = call(post("/api/v1/admin/exams/" + examId + "/questions"), admin, """
                {"sectionId":"%s","questionType":"matching","questionText":"Match","points":2,
                 "options":[{"optionText":"dog::chó"},{"optionText":"cat::mèo"}]}""".formatted(readingId))
                .read("$.result.questionId");
        String essayId = call(post("/api/v1/admin/exams/" + examId + "/questions"), admin, """
                {"sectionId":"%s","questionType":"essay","questionText":"Write 150 words","points":6}
                """.formatted(writingId)).read("$.result.questionId");

        Response invalid = call(post("/api/v1/admin/exams/" + examId + "/questions"), admin, """
                {"questionType":"multiple_choice","questionText":"bad",
                 "options":[{"optionText":"A","isCorrect":true},{"optionText":"B","isCorrect":true}]}""");
        assertThat((Integer) invalid.read("$.code")).isEqualTo(5016);

        // ---------- Đề private
        assertThat(call(get("/api/v1/exams/" + examId), null, null).status()).isEqualTo(404);
        assertThat((Integer) call(post("/api/v1/exams/" + examId + "/attempts"), student, null).read("$.code"))
                .isEqualTo(5007);

        // ---------- Publish + danh sách public (bind enum PostgreSQL)
        Response published = call(post("/api/v1/admin/exams/" + examId + "/publish"), admin, null);
        assertThat(published.status()).as(published.body()).isEqualTo(200);
        Response list = call(get("/api/v1/exams?examType=final&level=b1&certificateId=" + certificateId), null, null);
        assertThat((List<String>) list.read("$.data[*].examId")).containsExactly(examId);
        Response outline = call(get("/api/v1/exams/" + examId), null, null);
        assertThat(outline.body()).doesNotContain("Pick A").doesNotContain("isCorrect");
        assertThat(call(delete("/api/v1/admin/exams/" + examId + "/questions/" + mcId), admin, null).status())
                .isEqualTo(409);

        // ---------- Học viên làm bài
        Response started = call(post("/api/v1/exams/" + examId + "/attempts"), student, null);
        assertThat(started.status()).as(started.body()).isEqualTo(200);
        String attemptId = started.read("$.result.attemptId");
        assertThat(started.body()).contains("Pick A").doesNotContain("isCorrect").doesNotContain("dog::chó");
        assertThat((String) call(post("/api/v1/exams/" + examId + "/attempts"), student, null)
                .read("$.result.attemptId")).isEqualTo(attemptId);

        Response saved = call(put("/api/v1/attempts/" + attemptId + "/answers"), student, """
                {"answers":[{"questionId":"%s","selectedOptionId":"%s"},
                            {"questionId":"%s","answerText":"Went"},
                            {"questionId":"%s","answerText":"{\\"dog\\":\\"chó\\",\\"cat\\":\\"mèo\\"}"}]}
                """.formatted(mcId, mcCorrect, fbId, matchId));
        assertThat(saved.status()).as(saved.body()).isEqualTo(200);

        Response submitted = call(post("/api/v1/attempts/" + attemptId + "/submit"), student, """
                {"answers":[{"questionId":"%s","answerText":"This is my essay."}]}""".formatted(essayId));
        assertThat((String) submitted.read("$.result.attempt.status")).isEqualTo("submitted");
        assertThat((Double) submitted.read("$.result.attempt.score")).isEqualTo(4.0);
        assertThat((Integer) call(post("/api/v1/attempts/" + attemptId + "/submit"), student, null).read("$.code"))
                .isEqualTo(5013);
        assertThat((Integer) call(post("/api/v1/exams/" + examId + "/attempts"), student, null).read("$.code"))
                .isEqualTo(5010);
        assertThat(call(delete("/api/v1/admin/exams/" + examId), admin, null).status()).isEqualTo(409);

        // ---------- Mentor (không phụ trách khóa nào) thấy và mở được bài của đề thi thử
        UsernamePasswordAuthenticationToken mentor = auth(UUID.randomUUID(), "MENTOR");
        assertThat((List<String>) call(get("/api/v1/mentor/exam-attempts/pending"), mentor, null)
                .read("$.data[*].attemptId")).contains(attemptId);
        assertThat(call(get("/api/v1/mentor/exam-attempts/" + attemptId), mentor, null).status()).isEqualTo(200);

        // ---------- Chấm Writing
        assertThat(call(put("/api/v1/mentor/exam-attempts/" + attemptId + "/answers/" + essayId + "/grade"), admin,
                "{\"score\":7}").status()).isEqualTo(400);
        Response graded = call(put("/api/v1/mentor/exam-attempts/" + attemptId + "/answers/" + essayId + "/grade"),
                admin, "{\"score\":4.5,\"feedback\":\"Good structure\"}");
        assertThat((String) graded.read("$.result.attempt.status")).isEqualTo("graded");
        assertThat((Double) graded.read("$.result.attempt.score")).isEqualTo(8.5);
        assertThat(call(get("/api/v1/me/exam-attempts/" + attemptId + "/result"), student, null).body())
                .contains("Good structure");

        // ---------- Hết giờ
        jdbc.update("UPDATE exams SET max_attempts = 0 WHERE exam_id = ?::uuid", examId);
        String timedOut = call(post("/api/v1/exams/" + examId + "/attempts"), student, null).read("$.result.attemptId");
        jdbc.update("UPDATE exam_attempts SET start_time = ?, expires_at = ? WHERE attempt_id = ?::uuid",
                LocalDateTime.now().minusMinutes(40), LocalDateTime.now().minusMinutes(10), timedOut);
        assertThat((Integer) call(post("/api/v1/attempts/" + timedOut + "/submit"), student, null).read("$.code"))
                .isEqualTo(5014);
        assertThat((String) call(get("/api/v1/me/exam-attempts/" + timedOut + "/result"), student, null)
                .read("$.result.attempt.status")).isEqualTo("expired");

        // ---------- Đồng thời: start x2 -> 1 lượt; submit x2 -> 1 thành công
        List<Response> starts = parallel(() -> call(post("/api/v1/exams/" + examId + "/attempts"), student, null));
        String concurrentId = starts.get(0).read("$.result.attemptId");
        assertThat((String) starts.get(1).read("$.result.attemptId")).isEqualTo(concurrentId);
        List<Response> submits = parallel(() -> call(post("/api/v1/attempts/" + concurrentId + "/submit"), student, null));
        assertThat(submits).extracting(Response::status).containsExactlyInAnyOrder(200, 409);

        assertThat(call(get("/api/v1/attempts/" + attemptId), auth(UUID.randomUUID(), "STUDENT"), null).status())
                .isEqualTo(404);

        // ---------- Năng lực: chỉ tính đề thi thử loại placement_test
        assertThat((List<Object>) call(get("/api/v1/me/proficiency"), student, null).read("$.result")).isEmpty();
        jdbc.update("UPDATE exams SET exam_type = 'placement_test' WHERE exam_id = ?::uuid", examId);
        Response proficiency = call(get("/api/v1/me/proficiency"), student, null);
        assertThat(proficiency.status()).as(proficiency.body()).isEqualTo(200);
        assertThat((List<String>) proficiency.read("$.result[*].examId")).containsExactly(examId);
        assertThat((String) proficiency.read("$.result[0].status")).isIn("assessed", "pending_grading");
        assertThat((List<Object>) proficiency.read("$.result[0].sections")).isNotEmpty();
        assertThat((Object) proficiency.read("$.result[0].sections[0].percentage")).isNotNull();
        jdbc.update("UPDATE exams SET exam_type = 'final' WHERE exam_id = ?::uuid", examId);

        // ---------- Lưu trữ: ẩn khỏi học viên, giữ lịch sử; khôi phục về nháp
        assertThat((Integer) call(post("/api/v1/admin/exams/" + examId + "/restore"), admin, null).read("$.code"))
                .isEqualTo(5024);
        Response archived = call(post("/api/v1/admin/exams/" + examId + "/archive"), admin, null);
        assertThat((String) archived.read("$.result.exam.status")).as(archived.body()).isEqualTo("archived");
        assertThat(call(get("/api/v1/exams/" + examId), null, null).status()).isEqualTo(404);
        assertThat((List<String>) call(get("/api/v1/exams"), null, null).read("$.data[*].examId")).doesNotContain(examId);
        assertThat((Integer) call(post("/api/v1/exams/" + examId + "/attempts"), student, null).read("$.code"))
                .isEqualTo(5007);
        assertThat(call(get("/api/v1/me/exam-attempts/" + attemptId + "/result"), student, null).status()).isEqualTo(200);
        assertThat((List<String>) call(get("/api/v1/admin/exams?status=archived"), admin, null).read("$.data[*].examId"))
                .contains(examId);
        Response restored = call(post("/api/v1/admin/exams/" + examId + "/restore"), admin, null);
        assertThat((String) restored.read("$.result.exam.status")).isEqualTo("draft");
    }

    /** File init dữ liệu mẫu chạy được 2 lần (idempotent); đề course bị chặn khi chưa có enrollment. */
    @Test
    void sampleDataScriptLoadsAndRespectsEnrollment() throws Exception {
        setUpUsers();
        EncodedResource script = new EncodedResource(new ClassPathResource("db/init/D3_exam_sample_data.sql"),
                StandardCharsets.UTF_8);
        for (int i = 0; i < 2; i++) {
            try (Connection connection = jdbc.getDataSource().getConnection()) {
                ScriptUtils.executeSqlScript(connection, script);
            }
        }
        assertThat(jdbc.queryForObject("SELECT description FROM exams WHERE exam_code = 'D3-COURSE-QUIZ-01'",
                String.class)).isEqualTo("Bài kiểm tra nhanh Unit 1");

        String trialExam = "d3000000-0000-4000-8000-000000000100";
        assertThat((Boolean) call(get("/api/v1/exams/" + trialExam), student, null).read("$.result.accessible")).isTrue();
        assertThat((Double) call(post("/api/v1/exams/" + trialExam + "/attempts"), student, null)
                .read("$.result.exam.maxScore")).isEqualTo(13.0);

        // Đề khóa học: chưa có enrollment -> 5008; có enrollment active -> làm được
        String courseExam = "d3000000-0000-4000-8000-000000000200";
        assertThat((Integer) call(post("/api/v1/exams/" + courseExam + "/attempts"), student, null).read("$.code"))
                .isEqualTo(5008);
        UUID studentId = ((UserPrincipal) student.getPrincipal()).getUserId();
        jdbc.update("INSERT INTO enrollments (student_id, course_id, status) VALUES (?, ?::uuid, 'active')",
                studentId, "d3000000-0000-4000-8000-000000000020");
        assertThat(call(post("/api/v1/exams/" + courseExam + "/attempts"), student, null).status()).isEqualTo(200);
    }

    private static List<Response> parallel(Callable<Response> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Response>> futures = List.of(pool.submit(task), pool.submit(task));
            List<Response> results = new ArrayList<>();
            for (Future<Response> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }
}
