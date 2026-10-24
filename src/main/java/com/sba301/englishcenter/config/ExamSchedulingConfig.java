package com.sba301.englishcenter.config;

import com.sba301.englishcenter.service.ExamAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Job tự nộp các lượt thi đã hết giờ mà học viên không nộp (đóng tab, mất mạng...), để bài viết
 * vẫn vào hàng đợi chấm của mentor và kết quả có ngay. Tắt bằng {@code exam.sweeper.enabled=false}.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "exam.sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class ExamSchedulingConfig {

    private final ExamAttemptService attemptService;

    @Scheduled(fixedDelayString = "${exam.sweeper.fixed-delay-ms:60000}",
            initialDelayString = "${exam.sweeper.initial-delay-ms:60000}")
    public void autoCloseOverdueAttempts() {
        try {
            attemptService.autoCloseOverdueAttempts();
        } catch (Exception ex) {
            log.error("Lỗi khi tự nộp lượt thi quá hạn: {}", ex.getMessage(), ex);
        }
    }
}
