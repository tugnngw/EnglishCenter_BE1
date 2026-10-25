package com.sba301.englishcenter;

import com.sba301.englishcenter.common.enums.LearningPathItemType;
import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.dto.practice.CriterionScoreData;
import com.sba301.englishcenter.dto.request.LearningPathItemRequest;
import com.sba301.englishcenter.dto.request.LearningPathRequest;
import com.sba301.englishcenter.dto.request.PracticeExamRequest;
import com.sba301.englishcenter.dto.request.SubmitPracticeAnswerRequest;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.integration.ai.AiEvaluationClient;
import com.sba301.englishcenter.integration.ai.AiEvaluationResult;
import com.sba301.englishcenter.service.LearningPathService;
import com.sba301.englishcenter.service.PracticeExamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-D5-03 + D5-04 trên PostgreSQL thật: JSONB answers / student_progress, quyền sở hữu, tiến độ thật.
 * AI được giả lập (MockitoBean) để test không cần GEMINI_API_KEY.
 */
@EnabledIfEnvironmentVariable(named = "PAYMENT_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${PAYMENT_TEST_DB_URL}", "spring.datasource.username=postgres",
        "spring.datasource.password=dev4-test", "spring.docker.compose.enabled=false",
        "spring.jpa.show-sql=false", "logging.level.org.flywaydb=INFO",
        "jwt.signer-key=0123456789012345678901234567890123456789012345678901234567890123",
        "app.study-schedule.reminder-job.enabled=false", "app.booking.reminder-job.enabled=false"
})
class LearningPathPracticeIntegrationTest {
    @Autowired PracticeExamService practice;
    @Autowired LearningPathService paths;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiEvaluationClient ai;
    @LocalServerPort int port;

    UUID adminId, studentId, otherStudentId, certificateId, courseId;

    @BeforeEach void fixture() {
        reset(ai);
        adminId = jdbc.queryForObject("select u.user_id from users u join roles r using(role_id) where r.role_name='ADMIN' limit 1", UUID.class);
        studentId = student();
        otherStudentId = student();
        certificateId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        jdbc.update("insert into certificates(certificate_id,created_by_user_id,certificate_name) values (?,?,'IELTS test')", certificateId, adminId);
        jdbc.update("insert into courses(course_id,created_by_user_id,code,title,price,status) values (?,?,?,'IELTS Foundation',10000,'active')",
                courseId, adminId, courseId.toString());
    }

    UUID student() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users(user_id,email,password,full_name,role_id) values (?,?,?,'LP student',(select role_id from roles where role_name='STUDENT'))",
                id, id + "@test.invalid", "not-a-login-password");
        return id;
    }

    UUID practiceExam() {
        var r = new PracticeExamRequest();
        r.setTitle("Writing Task 2 - Technology");
        r.setDescription("Some people think technology makes life easier. Discuss.");
        r.setExamType(PracticeExamType.WRITING);
        r.setCourseId(courseId);
        return practice.create(adminId, r).getPracticeExamId();
    }

    UUID submit(UUID student, UUID examId, String content) {
        var r = new SubmitPracticeAnswerRequest();
        r.setContent(content);
        return practice.submit(student, examId, r).getAnswerId();
    }

    void aiReturns(String band) {
        var c = new CriterionScoreData(new BigDecimal(band), "ok");
        when(ai.evaluate(any(), any(), any())).thenReturn(new AiEvaluationResult(new BigDecimal(band), c, c, c, c,
                "Nhận xét", List.of("Gợi ý 1"), "gemini-test", "{}"));
    }

    int notifications(UUID user) {
        return jdbc.queryForObject("select count(*) from notifications where user_id=? and type='exam_result'", Integer.class, user);
    }

    @Test void practiceSubmitEvaluateOnceAndOwnership() {
        UUID examId = practiceExam();
        UUID first = submit(studentId, examId, "Technology   makes our life easier");
        submit(studentId, examId, "Second attempt");
        submit(otherStudentId, examId, "Other student essay");
        aiReturns("7.0");

        var evaluation = practice.evaluate(studentId, first);
        var again = practice.evaluate(studentId, first);

        assertEquals("7.0", evaluation.getBandScore().toPlainString());
        assertEquals(evaluation.getEvaluationId(), again.getEvaluationId());
        verify(ai, times(1)).evaluate(any(), any(), any());
        assertEquals(1, notifications(studentId));

        // Không chấm / xem được bài của người khác
        assertEquals(ErrorCode.PRACTICE_ANSWER_NOT_FOUND,
                assertThrows(AppException.class, () -> practice.evaluate(otherStudentId, first)).getErrorCode());
        assertEquals(ErrorCode.PRACTICE_EVALUATION_NOT_FOUND, assertThrows(AppException.class,
                () -> practice.getEvaluation(otherStudentId, false, evaluation.getEvaluationId())).getErrorCode());
        assertNotNull(practice.getEvaluation(studentId, false, evaluation.getEvaluationId()));
        assertNotNull(practice.getEvaluation(adminId, true, evaluation.getEvaluationId()));

        var mine = practice.getMyAnswers(studentId);
        assertEquals(2, mine.size());
        assertEquals("Second attempt", mine.get(0).getContent());
        assertEquals(5, mine.get(1).getWordCount());
        assertEquals(evaluation.getEvaluationId(), mine.get(1).getEvaluationId());
        assertEquals(3, jdbc.queryForObject("select jsonb_array_length(answers) from practice_exams where practice_exam_id=?", Integer.class, examId));

        // Đề đã có bài làm thì không xóa được
        assertEquals(ErrorCode.PRACTICE_EXAM_HAS_ANSWERS,
                assertThrows(AppException.class, () -> practice.delete(examId)).getErrorCode());
    }

    @Test void aiFailureKeepsAnswerSubmitted() {
        UUID examId = practiceExam();
        UUID answer = submit(studentId, examId, "Essay");
        when(ai.evaluate(any(), any(), any())).thenThrow(new AppException(ErrorCode.AI_EVALUATION_FAILED));

        assertThrows(AppException.class, () -> practice.evaluate(studentId, answer));

        assertEquals("SUBMITTED", practice.getMyAnswers(studentId).get(0).getStatus());
        assertEquals(0, notifications(studentId));
    }

    LearningPathItemRequest item(int order, LearningPathItemType type, UUID refId, String band) {
        var i = new LearningPathItemRequest();
        i.setStage(order == 1 ? "Foundation" : "Practice");
        i.setOrder(order);
        i.setType(type);
        i.setRefId(refId);
        i.setTargetBand(band == null ? null : new BigDecimal(band));
        return i;
    }

    LearningPathRequest path(List<LearningPathItemRequest> items) {
        var r = new LearningPathRequest();
        r.setCertificateId(certificateId);
        r.setName("IELTS 6.5 Roadmap");
        r.setDescription("Lộ trình test");
        r.setItems(items);
        return r;
    }

    @Test void learningPathProgressFollowsRealData() {
        UUID examId = practiceExam();
        var created = paths.create(adminId, path(List.of(
                item(2, LearningPathItemType.PRACTICE_EXAM, examId, "6.5"),
                item(1, LearningPathItemType.COURSE, courseId, null))));
        UUID pathId = created.getLearningPathId();
        assertEquals("IELTS Foundation", created.getItems().get(0).getTitle());
        assertEquals("Writing Task 2 - Technology", created.getItems().get(1).getTitle());

        // Ghi danh 2 lần vẫn chỉ 1 bản ghi
        assertEquals(0, paths.enroll(studentId, pathId).getProgressPercent());
        paths.enroll(studentId, pathId);
        assertEquals(1, paths.getLearningPath(pathId).getEnrolledCount());

        // COURSE: hoàn thành khi đã ghi danh khóa
        jdbc.update("insert into enrollments(student_id,course_id,status) values (?,?,'active')", studentId, courseId);
        var half = paths.getMyProgress(studentId).get(0);
        assertEquals(50, half.getProgressPercent());
        assertTrue(half.getItems().get(0).getCompleted());
        assertFalse(half.getItems().get(1).getCompleted());

        // PRACTICE_EXAM: band 6.0 chưa đạt mục tiêu 6.5
        aiReturns("6.0");
        practice.evaluate(studentId, submit(studentId, examId, "First try"));
        assertEquals(50, paths.getMyProgress(studentId).get(0).getProgressPercent());

        aiReturns("7.0");
        practice.evaluate(studentId, submit(studentId, examId, "Better try"));
        var done = paths.getMyProgress(studentId).get(0);
        assertEquals(100, done.getProgressPercent());
        assertEquals("completed", done.getStatus());
        assertNotNull(done.getCompletedAt());
        assertEquals("completed", jdbc.queryForObject(
                "select student_progress->0->>'status' from learning_paths where learning_path_id=?", String.class, pathId));

        // Đã có học viên thì không xóa được
        assertEquals(ErrorCode.LEARNING_PATH_HAS_STUDENTS,
                assertThrows(AppException.class, () -> paths.delete(pathId)).getErrorCode());
        assertTrue(paths.getMyProgress(otherStudentId).isEmpty());
    }

    @Test void invalidItemsAreRejected() {
        // targetBand chỉ dùng cho PRACTICE_EXAM
        assertEquals(ErrorCode.LEARNING_PATH_ITEM_INVALID, assertThrows(AppException.class,
                () -> paths.create(adminId, path(List.of(item(1, LearningPathItemType.COURSE, courseId, "6.5"))))).getErrorCode());
        // Trùng chặng
        assertEquals(ErrorCode.LEARNING_PATH_ITEM_INVALID, assertThrows(AppException.class,
                () -> paths.create(adminId, path(List.of(item(1, LearningPathItemType.COURSE, courseId, null),
                        item(2, LearningPathItemType.COURSE, courseId, null))))).getErrorCode());
        // Khóa học không tồn tại
        assertEquals(ErrorCode.COURSE_NOT_FOUND, assertThrows(AppException.class,
                () -> paths.create(adminId, path(List.of(item(1, LearningPathItemType.COURSE, UUID.randomUUID(), null))))).getErrorCode());
    }

    @Test void publicEndpointsWorkWithoutLogin() throws Exception {
        practiceExam();
        HttpClient http = HttpClient.newHttpClient();
        for (String uri : List.of("/api/v1/learning-paths?certificateId=" + certificateId, "/api/v1/practice-exams?type=WRITING")) {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + uri)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, res.statusCode(), uri + " -> " + res.body());
        }
        HttpResponse<String> privateRes = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/practice-exams/me/answers")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, privateRes.statusCode());
    }
}
