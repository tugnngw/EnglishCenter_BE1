package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Thống kê trang quản lý Mentor (admin) — đếm trên toàn bộ mentor, không phụ thuộc trang đang xem.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MentorStatsResponse {
    long total;
    long active;
    long inactive;
    /** Mentor tham gia trong 30 ngày gần nhất */
    long newLast30Days;
    /** Các department đang dùng, dùng cho dropdown lọc */
    List<String> departments;
}
