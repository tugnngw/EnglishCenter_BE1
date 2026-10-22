package com.sba301.englishcenter.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub kiểm tra quyền ghi danh - phục vụ Dev 2 (Course Content) cho việc quyết định
 * ẩn/hiện nội dung bài học theo quyền học viên.
 * <p>
 * Đây là mock nội bộ đơn giản (chỉ dựa trên thông tin của riêng Dev 2), KHÔNG can thiệp
 * vào domain Payment/Enrollment thuộc Dev 4. Khi Dev 4 hoàn tất, stub này có thể được
 * thay bằng cách gọi sang EnrollmentService của Dev 4 mà không phải sửa các Controller/Service
 * của Dev 2.
 */
@Component
public class SessionEnrollmentChecker {

    /**
     * Mặc định Dev 2 chưa tích hợp Dev 4 nên luôn trả về true để preview logic vẫn chạy được.
     * Khi Dev 4 hoàn tất EnrollmentService, đổi thân hàm sang gọi service của Dev 4.
     */
    public boolean isEnrolled(UUID userId, UUID courseId) {
        return true;
    }
}