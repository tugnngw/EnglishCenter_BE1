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

    private static final String DESCRIPTION = "Đề kiểm tra gồm Reading và Writing, làm trong 30 phút";

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

        // Khóa học riêng cho test: mentorA phụ trách khóa (được chấm bài), học viên đã ghi danh
        // (trigger DB chỉ cho admin tạo user MENTOR, nên tạo user thường rồi cấp quyền MENTOR trong token)
        UUID mentorAId = createStudent();
        UsernamePasswordAuthenticationToken mentorA = auth(mentorAId, "MENTOR");
        UsernamePasswordAuthenticationToken mentorB = auth(createStudent(), "MENTOR");
        UUID courseId = UUID.randomUUID();
        jdbc.update("INSERT INTO courses (course_id, created_by_user_id, certificate_id, code, title, mentor_id, "
                + "status) VALUES (?, (SELECT user_id FROM users WHERE email = 'admin@gmail.com'), ?, ?, "
                + "'IT course', ?, 'active')", courseId, certificateId, "IT-COURSE-" + System.nanoTime(), mentorAId);
        jdbc.update("INSERT INTO enrollments (student_id, course_id, status) VALUES (?, ?, 'active')",
                ((UserPrincipal) student.getPrincipal()).getUserId(), courseId);

        // ---------- Admin tạo đề. Thi thử / kiểm tra đầu vào đã bỏ -> 5028; mô tả quá ngắn -> 5029
        assertThat((Integer) call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"trial","certificateId":"%s","examType":"placement_test","examTitle":"Integration exam",
                 "description":"%s","durationMinutes":30}""".formatted(certificateId, DESCRIPTION))
                .read("$.code")).isEqualTo(5028);
        // "final" (nhãn trùng từ khóa Java) phải qua được converter
        assertThat((Integer) call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"trial","certificateId":"%s","examType":"final","examTitle":"Integration exam",
                 "description":"%s","durationMinutes":30}""".formatted(certificateId, DESCRIPTION))
                .read("$.code")).isEqualTo(5028);
        assertThat((Integer) call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"course","courseId":"%s","examType":"quiz","examTitle":"Integration exam",
                 "description":"Đề test","durationMinutes":30}""".formatted(courseId))
                .read("$.code")).isEqualTo(5029);
        // Thời lượng: tối thiểu 15 phút, Quiz tối đa 60 phút, không quá thời gian thi thật -> 5034
        assertThat((Integer) call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"course","courseId":"%s","examType":"quiz","examTitle":"Integration exam",
                 "description":"%s","durationMinutes":1}""".formatted(courseId, DESCRIPTION))
                .read("$.code")).isEqualTo(5034);
        assertThat((Integer) call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"course","courseId":"%s","examType":"final","examTitle":"Integration exam",
                 "description":"%s","durationMinutes":500}""".formatted(courseId, DESCRIPTION))
                .read("$.code")).isEqualTo(5034);
        Response created = call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"course","courseId":"%s","examCode":"%s","examType":"quiz",
                 "examTitle":"Integration exam","description":"%s","durationMinutes":30,"maxAttempts":1,
                 "passingScore":50,"recommendedLevel":"B1"}""".formatted(courseId, code, DESCRIPTION));
        assertThat(created.status()).as(created.body()).isEqualTo(200);
        String examId = created.read("$.result.exam.examId");
        assertThat((String) created.read("$.result.exam.examType")).isEqualTo("quiz");

        assertThat(call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"course","courseId":"%s","examCode":"%s","examTitle":"x","durationMinutes":30}
                """.formatted(courseId, code)).status()).isEqualTo(409);
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
        Response list = call(get("/api/v1/exams?examType=quiz&level=b1&certificateId=" + certificateId),
                student, null);
        assertThat((List<String>) list.read("$.data[*].examId")).containsExactly(examId);
        // Khách không thấy đề khóa học
        assertThat((List<String>) call(get("/api/v1/exams?certificateId=" + certificateId), null, null)
                .read("$.data[*].examId")).isEmpty();
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

        // ---------- Mentor phụ trách khóa thấy và chấm được; mentor khác thì không
        assertThat((List<String>) call(get("/api/v1/mentor/exam-attempts/pending"), mentorA, null)
                .read("$.data[*].attemptId")).contains(attemptId);
        assertThat((List<String>) call(get("/api/v1/mentor/exam-attempts/pending"), mentorB, null)
                .read("$.data[*].attemptId")).doesNotContain(attemptId);
        assertThat(call(put("/api/v1/mentor/exam-attempts/" + attemptId + "/answers/" + essayId + "/grade"), mentorB,
                "{\"score\":1}").status()).isEqualTo(403);

        // ---------- Nhận chấm: mentor chấm đầu tiên giữ bài, admin vẫn chấm được
        assertThat(call(put("/api/v1/mentor/exam-attempts/" + attemptId + "/answers/" + essayId + "/grade"), mentorA,
                "{\"score\":3}").status()).isEqualTo(200);
        assertThat((String) call(get("/api/v1/mentor/exam-attempts/" + attemptId), admin, null)
                .read("$.result.attempt.gradingByName")).isEqualTo("Student");

        // ---------- Chấm Writing
        assertThat(call(put("/api/v1/mentor/exam-attempts/" + attemptId + "/answers/" + essayId + "/grade"), admin,
                "{\"score\":7}").status()).isEqualTo(400);
        Response graded = call(put("/api/v1/mentor/exam-attempts/" + attemptId + "/answers/" + essayId + "/grade"),
                admin, "{\"score\":4.5,\"feedback\":\"Good structure\"}");
        assertThat((String) graded.read("$.result.attempt.status")).isEqualTo("graded");
        assertThat((Double) graded.read("$.result.attempt.score")).isEqualTo(8.5);
        assertThat(call(get("/api/v1/me/exam-attempts/" + attemptId + "/result"), student, null).body())
                .contains("Good structure");
        // Học viên nhận đúng 1 thông báo khi bài được chấm xong (chấm lại không gửi thêm)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? "
                        + "AND type = 'exam_result' AND title = 'Bài thi đã được chấm'", Integer.class,
                ((UserPrincipal) student.getPrincipal()).getUserId())).isEqualTo(1);

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

        // ---------- Đề đã có lượt thi: sửa được nội dung hiển thị, không đổi được đáp án / cấu trúc
        Response textFix = call(put("/api/v1/admin/exams/" + examId + "/questions/" + mcId), admin, """
                {"questionText":"Đề đã sửa chính tả","explanation":"Giải thích mới"}""");
        assertThat(textFix.status()).as(textFix.body()).isEqualTo(200);
        assertThat((String) textFix.read("$.result.questionText")).isEqualTo("Đề đã sửa chính tả");
        assertThat((Integer) call(put("/api/v1/admin/exams/" + examId + "/questions/" + mcId), admin,
                "{\"points\":5}").read("$.code")).isIn(5006, 5021);
        assertThat(call(put("/api/v1/admin/exams/" + examId + "/sections/" + readingId), admin,
                "{\"instructions\":\"Hướng dẫn mới\",\"audioUrl\":\"https://example.com/new.mp3\"}").status()).isEqualTo(200);
        assertThat((Integer) call(put("/api/v1/admin/exams/" + examId + "/sections/" + readingId), admin,
                "{\"sectionOrder\":9}").read("$.code")).isEqualTo(5006);

        // ---------- Nhân bản: đề nháp mới, đủ phần thi / câu hỏi, câu được chép mới nên sửa đáp án được
        Response clone = call(post("/api/v1/admin/exams/" + examId + "/clone"), admin, null);
        assertThat(clone.status()).as(clone.body()).isEqualTo(200);
        String cloneId = clone.read("$.result.exam.examId");
        assertThat(cloneId).isNotEqualTo(examId);
        assertThat((String) clone.read("$.result.exam.status")).isEqualTo("draft");
        assertThat((Integer) clone.read("$.result.exam.totalQuestions"))
                .isEqualTo((Integer) call(get("/api/v1/admin/exams/" + examId), admin, null).read("$.result.exam.totalQuestions"));
        List<String> cloneQuestionIds = clone.read("$.result.sections[*].questions[*].questionId");
        assertThat(cloneQuestionIds).isNotEmpty().doesNotContain(mcId, essayId);
        assertThat(call(put("/api/v1/admin/exams/" + cloneId + "/questions/" + cloneQuestionIds.get(0)), admin,
                "{\"points\":3}").status()).isEqualTo(200);

        // ---------- Lưu cả bộ đề một lần (sau xem trước file / AI): một câu lỗi thì không lưu gì
        long sectionsBefore = jdbc.queryForObject("SELECT count(*) FROM exam_sections WHERE exam_id = ?::uuid",
                Long.class, cloneId);
        Response bad = call(post("/api/v1/admin/exams/" + cloneId + "/content"), admin, """
                {"sections":[{"section":{"title":"Imported","sectionType":"READING"},
                  "questions":[{"questionType":"multiple_choice","questionText":"OK?","points":1,
                                "options":[{"optionText":"a","isCorrect":true},{"optionText":"b"}]},
                               {"questionType":"multiple_choice","questionText":"No answer","points":1,
                                "options":[{"optionText":"a"},{"optionText":"b"}]}]}]}""");
        assertThat(bad.status()).as(bad.body()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_sections WHERE exam_id = ?::uuid", Long.class, cloneId))
                .isEqualTo(sectionsBefore);
        Response good = call(post("/api/v1/admin/exams/" + cloneId + "/content"), admin, """
                {"sections":[{"section":{"title":"Imported","sectionType":"READING"},
                  "questions":[{"questionType":"multiple_choice","questionText":"OK?","points":1,
                                "options":[{"optionText":"a","isCorrect":true},{"optionText":"b"}]},
                               {"questionType":"essay","questionText":"Write.","points":2}]}],
                 "questions":[{"questionType":"true_false","questionText":"Loose","points":1,
                               "options":[{"optionText":"True","isCorrect":true},{"optionText":"False"}]}]}""");
        assertThat(good.status()).as(good.body()).isEqualTo(200);
        assertThat((Integer) good.read("$.result.sectionCount")).isEqualTo(1);
        assertThat((Integer) good.read("$.result.questionCount")).isEqualTo(3);
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
                String.class)).isEqualTo("Bài kiểm tra nhanh Unit 1: 2 câu ngữ pháp (to be, số nhiều bất quy tắc), làm trong 10 phút");

        // Thang điểm từng phần thi (properties) phải ra đúng JSON để FE quy đổi điểm theo chứng chỉ
        Response ielts = call(get("/api/v1/certificates/d3000000-0000-4000-8000-000000000001"), student, null);
        assertThat(ielts.status()).as(ielts.body()).isEqualTo(200);
        assertThat((Object) ielts.read("$.result.parts[0].properties.maxScore")).isEqualTo(9);
        assertThat(ielts.body()).doesNotContain("nodeType");

        // Đề khóa học: chưa có enrollment -> 5008; có enrollment active -> Quiz làm được, Giữa kỳ mở khi đạt Quiz
        String quiz = "d3000000-0000-4000-8000-000000000200";
        String midterm = "d3000000-0000-4000-8000-000000000100";
        assertThat((String) call(get("/api/v1/admin/exams/" + midterm), admin, null).read("$.result.exam.examScope"))
                .isEqualTo("course");
        assertThat((Integer) call(post("/api/v1/exams/" + quiz + "/attempts"), student, null).read("$.code"))
                .isEqualTo(5008);
        UUID studentId = ((UserPrincipal) student.getPrincipal()).getUserId();
        jdbc.update("INSERT INTO enrollments (student_id, course_id, status) VALUES (?, ?::uuid, 'active')",
                studentId, "d3000000-0000-4000-8000-000000000020");
        assertThat(call(post("/api/v1/exams/" + quiz + "/attempts"), student, null).status()).isEqualTo(200);
        markPassed(quiz, studentId);
        Response midtermAttempt = call(post("/api/v1/exams/" + midterm + "/attempts"), student, null);
        assertThat(midtermAttempt.status()).as(midtermAttempt.body()).isEqualTo(200);
        assertThat((Double) midtermAttempt.read("$.result.exam.maxScore")).isEqualTo(13.0);
    }

    @Test
    void courseExamsAreListedOnlyForBuyersAndUnlockInOrder() throws Exception {
        setUpUsers();
        EncodedResource script = new EncodedResource(new ClassPathResource("db/init/D3_exam_sample_data.sql"),
                StandardCharsets.UTF_8);
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, script);
        }
        String courseId = "d3000000-0000-4000-8000-000000000020";
        String courseQuiz = "d3000000-0000-4000-8000-000000000200";

        // ---------- Đề khóa học không có trong danh sách của khách / học viên chưa mua khóa
        assertThat((List<String>) call(get("/api/v1/exams?size=100"), null, null).read("$.data[*].examId"))
                .doesNotContain(courseQuiz);
        assertThat((List<String>) call(get("/api/v1/exams?size=100"), student, null).read("$.data[*].examId"))
                .doesNotContain(courseQuiz);
        UUID studentId = ((UserPrincipal) student.getPrincipal()).getUserId();
        jdbc.update("INSERT INTO enrollments (student_id, course_id, status) VALUES (?, ?::uuid, 'active')",
                studentId, courseId);
        assertThat((List<String>) call(get("/api/v1/exams?size=100"), student, null).read("$.data[*].examId"))
                .contains(courseQuiz);
        // Admin vẫn thấy mọi đề đã công khai
        assertThat((List<String>) call(get("/api/v1/exams?size=100"), admin, null).read("$.data[*].examId"))
                .contains(courseQuiz);

        // ---------- Đề khóa học phải là quiz / giữa kỳ / cuối kỳ
        assertThat((Integer) call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"course","courseId":"%s","examType":"placement_test","examTitle":"Final",
                 "description":"%s","durationMinutes":30}""".formatted(courseId, DESCRIPTION))
                .read("$.code")).isEqualTo(5028);
        // DB test dùng lại giữa các lần chạy: dọn đề / bài tập do lần chạy trước thêm vào khóa mẫu
        jdbc.update("DELETE FROM course_content WHERE course_id = ?::uuid AND content_type = 'homework'", courseId);
        jdbc.update("DELETE FROM exams WHERE course_id = ?::uuid AND exam_id <> ?::uuid", courseId, courseQuiz);
        // Quiz tối đa 60 phút
        assertThat((Integer) call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"course","courseId":"%s","examType":"quiz","examTitle":"Long quiz",
                 "description":"%s","durationMinutes":90}""".formatted(courseId, DESCRIPTION))
                .read("$.code")).isEqualTo(5034);
        String midterm = publishCourseExam(courseId, "midterm");
        String finalExam = publishCourseExam(courseId, "final");
        // Admin được cảnh báo khi bộ đề của khóa chưa đủ (khóa chưa có bài tập)
        assertThat((List<String>) call(get("/api/v1/admin/exams/" + finalExam), admin, null)
                .read("$.result.warnings")).anyMatch(w -> w.contains("bài tập"));
        UUID homework = UUID.randomUUID();
        jdbc.update("INSERT INTO course_content (content_id, course_id, content_type, title) "
                + "VALUES (?, ?::uuid, 'homework', 'Unit 1 homework')", homework, courseId);
        assertThat(call(get("/api/v1/admin/exams/" + finalExam), admin, null).body()).doesNotContain("\"warnings\"");

        // Danh sách đề cho biết đề nào đang khóa và vì sao
        Response list = call(get("/api/v1/exams?size=100"), student, null);
        assertThat((List<Integer>) list.read("$.data[?(@.examId=='" + midterm + "')].lockCode")).containsExactly(5031);
        assertThat((List<String>) list.read("$.data[?(@.examId=='" + midterm + "')].lockReason"))
                .anyMatch(r -> r.contains("0/1"));
        assertThat((List<Boolean>) list.read("$.data[?(@.examId=='" + courseQuiz + "')].accessible"))
                .containsExactly(true);

        // ---------- Thi nối tiếp: Quiz -> Giữa kỳ -> Cuối kỳ
        // Giữa kỳ: chưa đạt Quiz của khóa -> 5031
        assertThat((Boolean) call(get("/api/v1/exams/" + midterm), student, null).read("$.result.accessible"))
                .isFalse();
        assertThat((Integer) call(post("/api/v1/exams/" + midterm + "/attempts"), student, null).read("$.code"))
                .isEqualTo(5031);
        // Quiz không có điều kiện
        Response quizAttempt = call(post("/api/v1/exams/" + courseQuiz + "/attempts"), student, null);
        assertThat(quizAttempt.status()).as(quizAttempt.body()).isEqualTo(200);
        // Quiz đã nộp, đang chờ mentor chấm -> Giữa kỳ báo "đang chờ chấm" (5033), không phải "chưa đạt"
        jdbc.update("UPDATE exam_attempts SET status = 'submitted', passed = FALSE "
                + "WHERE exam_id = ?::uuid AND student_id = ?", courseQuiz, studentId);
        assertThat((Integer) call(post("/api/v1/exams/" + midterm + "/attempts"), student, null).read("$.code"))
                .isEqualTo(5033);
        markPassed(courseQuiz, studentId);
        // Đạt Quiz -> Giữa kỳ mở; Cuối kỳ vẫn khóa vì chưa đạt Giữa kỳ -> 5032
        assertThat(call(post("/api/v1/exams/" + midterm + "/attempts"), student, null).status()).isEqualTo(200);
        assertThat((Integer) call(post("/api/v1/exams/" + finalExam + "/attempts"), student, null).read("$.code"))
                .isEqualTo(5032);
        markPassed(midterm, studentId);
        // Đạt Giữa kỳ nhưng bài tập chưa nộp -> 5030
        assertThat((Integer) call(post("/api/v1/exams/" + finalExam + "/attempts"), student, null).read("$.code"))
                .isEqualTo(5030);
        jdbc.update("INSERT INTO homework_submissions (homework_content_id, student_id, answer_content) "
                + "VALUES (?, ?, 'done')", homework, studentId);
        // Bài tập đã nộp, chưa chấm -> đang chờ chấm
        assertThat((Integer) call(post("/api/v1/exams/" + finalExam + "/attempts"), student, null).read("$.code"))
                .isEqualTo(5033);
        // Bài tập đạt -> Cuối kỳ mở
        jdbc.update("UPDATE homework_submissions SET passed = TRUE WHERE homework_content_id = ?", homework);
        Response finalAttempt = call(post("/api/v1/exams/" + finalExam + "/attempts"), student, null);
        assertThat(finalAttempt.status()).as(finalAttempt.body()).isEqualTo(200);
    }

    /** Tạo + công khai một đề khóa học 1 câu, trả examId. */
    private String publishCourseExam(String courseId, String examType) throws Exception {
        Response created = call(post("/api/v1/admin/exams"), admin, """
                {"examScope":"course","courseId":"%s","examType":"%s","examTitle":"IELTS Foundation %s",
                 "description":"%s","durationMinutes":30}""".formatted(courseId, examType, examType, DESCRIPTION));
        assertThat(created.status()).as(created.body()).isEqualTo(200);
        String examId = created.read("$.result.exam.examId");
        call(post("/api/v1/admin/exams/" + examId + "/questions"), admin, """
                {"questionType":"multiple_choice","questionText":"Pick A","points":1,
                 "options":[{"optionText":"A","isCorrect":true},{"optionText":"B"}]}""");
        assertThat(call(post("/api/v1/admin/exams/" + examId + "/publish"), admin, null).status()).isEqualTo(200);
        return examId;
    }

    /** Đánh dấu lượt thi của học viên ở đề này là đã chấm và đạt. */
    private void markPassed(String examId, UUID studentId) {
        jdbc.update("UPDATE exam_attempts SET passed = TRUE, status = 'graded' "
                + "WHERE exam_id = ?::uuid AND student_id = ?", examId, studentId);
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
