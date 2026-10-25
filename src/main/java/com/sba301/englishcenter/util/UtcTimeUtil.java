package com.sba301.englishcenter.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Chính sách thời gian của Dev 5 (TASK D5-19):
 * - DB lưu giờ UTC trong cột TIMESTAMP (LocalDateTime mang giá trị UTC).
 * - API nhận / trả {@link Instant} (ISO-8601 có "Z"), FE tự hiển thị theo múi giờ người dùng.
 * Không đổi cấu hình chung của project để không ảnh hưởng module của dev khác.
 */
public final class UtcTimeUtil {

    private UtcTimeUtil() {
    }

    /** Thời điểm hiện tại theo UTC */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Instant từ API -> LocalDateTime UTC để lưu DB */
    public static LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** LocalDateTime UTC trong DB -> Instant trả về API */
    public static Instant toInstant(LocalDateTime utcDateTime) {
        return utcDateTime == null ? null : utcDateTime.toInstant(ZoneOffset.UTC);
    }

    /** created_at / last_updated của BaseEntity được JPA Auditing ghi theo giờ máy chủ */
    public static Instant fromServerTime(LocalDateTime serverDateTime) {
        return serverDateTime == null ? null : serverDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
