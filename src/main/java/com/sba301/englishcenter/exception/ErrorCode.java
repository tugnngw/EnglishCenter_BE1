package com.sba301.englishcenter.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // === System ===
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),

    // === Validation ===
    VALIDATION_ERROR(1011, "Validation error", HttpStatus.BAD_REQUEST),

    // === Auth ===
    USER_EXISTED(1001, "Email already exists", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1002, "User not found", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS(1003, "Invalid email or password", HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(1004, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1005, "You do not have permission", HttpStatus.FORBIDDEN),
    TOKEN_INVALID(1006, "Token is invalid or expired", HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED(1007, "Token has been revoked", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(1008, "Account is locked due to multiple failed login attempts", HttpStatus.FORBIDDEN),
    ACCOUNT_INACTIVE(1009, "Account is inactive", HttpStatus.FORBIDDEN),

    // === Role ===
    ROLE_NOT_FOUND(1010, "Role not found", HttpStatus.NOT_FOUND),

    // === Refresh Token ===
    REFRESH_TOKEN_REVOKED(1012, "Refresh token has been revoked", HttpStatus.UNAUTHORIZED),

    // === Auth - Password Management ===
    NEW_PASSWORD_SAME_AS_OLD(2015, "Mật khẩu mới không được trùng mật khẩu cũ", HttpStatus.BAD_REQUEST),

    // === Admin Operations ===
    CANNOT_DISABLE_SELF(2016, "Không thể tự khóa tài khoản của mình", HttpStatus.FORBIDDEN),
    CANNOT_REMOVE_LAST_ADMIN(2017, "Không thể thu hồi vai trò ADMIN cuối cùng", HttpStatus.FORBIDDEN),
    USER_ALREADY_HAS_ROLE(2018, "Người dùng đã có vai trò này", HttpStatus.CONFLICT),
    MENTOR_PROFILE_NOT_FOUND(2019, "Không tìm thấy hồ sơ mentor", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(2020, "Email đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    FORBIDDEN_OPERATION(2021, "Thao tác bị từ chối bởi hệ thống", HttpStatus.FORBIDDEN),
    USE_CREATE_MENTOR_ENDPOINT(2022, "Vui lòng dùng API tạo mentor để gán vai trò MENTOR", HttpStatus.FORBIDDEN),
    ROLE_CANNOT_BE_CHANGED(2023, "Vai trò của người dùng là cố định và không thể thay đổi thủ công", HttpStatus.BAD_REQUEST),
    ALREADY_STUDENT(2024, "Người dùng đã là học viên", HttpStatus.BAD_REQUEST),

    // === Dev 2 - Course & Learning Content Management ===
    CERTIFICATE_NOT_FOUND(3001, "Không tìm thấy chứng chỉ", HttpStatus.NOT_FOUND),
    CERTIFICATE_PART_NOT_FOUND(3002, "Không tìm thấy phần của chứng chỉ", HttpStatus.NOT_FOUND),
    COURSE_NOT_FOUND(3003, "Không tìm thấy khóa học", HttpStatus.NOT_FOUND),
    COURSE_CODE_EXISTED(3004, "Mã khóa học đã tồn tại", HttpStatus.CONFLICT),
    COURSE_CONTENT_NOT_FOUND(3005, "Không tìm thấy nội dung khóa học", HttpStatus.NOT_FOUND),
    COURSE_CONTENT_INVALID_TYPE(3006, "Loại nội dung không hợp lệ", HttpStatus.BAD_REQUEST),
    HOMEWORK_SUBMISSION_NOT_FOUND(3007, "Không tìm thấy bài nộp", HttpStatus.NOT_FOUND),
    INVALID_COURSE_STATUS(3008, "Trạng thái khóa học không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_IDENTIFIER(3009, "Định danh không hợp lệ (phải là UUID)", HttpStatus.BAD_REQUEST),
    // === Dev 4 - Payment & Enrollment ===
    PAYMENT_ORDER_NOT_FOUND(4001, "Không tìm thấy đơn thanh toán", HttpStatus.NOT_FOUND),
    COURSE_NOT_PURCHASABLE(4002, "Khóa học chưa mở bán hoặc giá không hợp lệ", HttpStatus.BAD_REQUEST),
    ALREADY_ENROLLED(4003, "Bạn đã đăng ký khóa học này", HttpStatus.CONFLICT),
    PAYMENT_ORDER_NOT_PENDING(4004, "Chỉ có thể hủy đơn đang chờ thanh toán", HttpStatus.CONFLICT),
    SEPAY_NOT_CONFIGURED(4005, "Chưa cấu hình đầy đủ SePay", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_WEBHOOK_SIGNATURE(4006, "Chữ ký webhook không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED),
    ENROLLMENT_REQUIRED(4007, "Bạn cần đăng ký khóa học để truy cập nội dung này", HttpStatus.FORBIDDEN),
    INVALID_WEBHOOK_DATA(4008, "Dữ liệu webhook không hợp lệ", HttpStatus.BAD_REQUEST),
    // === Dev 3 - Exam / Test Management ===
    EXAM_NOT_FOUND(5001, "Không tìm thấy đề thi", HttpStatus.NOT_FOUND),
    EXAM_CODE_EXISTED(5002, "Mã đề thi đã tồn tại", HttpStatus.CONFLICT),
    EXAM_SECTION_NOT_FOUND(5003, "Không tìm thấy section của đề thi", HttpStatus.NOT_FOUND),
    QUESTION_NOT_FOUND(5004, "Không tìm thấy câu hỏi", HttpStatus.NOT_FOUND),
    EXAM_NOT_EDITABLE(5005, "Chỉ được sửa cấu trúc đề thi khi đề ở trạng thái nháp (draft)", HttpStatus.CONFLICT),
    EXAM_HAS_ATTEMPTS(5006, "Đề thi đã có lượt thi, không thể thực hiện thao tác này", HttpStatus.CONFLICT),
    EXAM_NOT_AVAILABLE(5007, "Đề thi hiện không khả dụng", HttpStatus.FORBIDDEN),
    EXAM_ENROLLMENT_REQUIRED(5008, "Bạn cần đăng ký khóa học để làm đề thi này", HttpStatus.FORBIDDEN),
    EXAM_PAYMENT_REQUIRED(5009, "Bạn cần thanh toán để làm đề thi này", HttpStatus.FORBIDDEN),
    EXAM_MAX_ATTEMPTS_REACHED(5010, "Bạn đã dùng hết số lượt thi cho phép", HttpStatus.CONFLICT),
    EXAM_INVALID_FOR_PUBLISH(5011, "Đề thi chưa hợp lệ để công khai", HttpStatus.BAD_REQUEST),
    ATTEMPT_NOT_FOUND(5012, "Không tìm thấy lượt thi", HttpStatus.NOT_FOUND),
    ATTEMPT_ALREADY_SUBMITTED(5013, "Lượt thi đã được nộp, không thể thay đổi hoặc nộp lại", HttpStatus.CONFLICT),
    ATTEMPT_TIME_EXPIRED(5014, "Đã hết thời gian làm bài, hệ thống đã tự động nộp bài của bạn", HttpStatus.CONFLICT),
    ATTEMPT_NOT_SUBMITTED(5015, "Lượt thi chưa được nộp nên chưa có kết quả", HttpStatus.CONFLICT),
    INVALID_QUESTION_OPTIONS(5016, "Đáp án của câu hỏi không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_ANSWER(5017, "Câu trả lời không hợp lệ", HttpStatus.BAD_REQUEST),
    ANSWER_NOT_FOUND(5018, "Không tìm thấy câu trả lời", HttpStatus.NOT_FOUND),
    INVALID_SCORE(5019, "Điểm chấm không hợp lệ", HttpStatus.BAD_REQUEST),
    QUESTION_NOT_MANUALLY_GRADABLE(5020, "Câu hỏi này được chấm tự động, không chấm tay", HttpStatus.BAD_REQUEST),
    QUESTION_IN_USE(5021, "Câu hỏi đã có học viên trả lời, không thể chỉnh sửa", HttpStatus.CONFLICT),
    INVALID_EXAM_SCOPE(5022, "Phạm vi đề thi không hợp lệ", HttpStatus.BAD_REQUEST),
    EXAM_QUESTION_ALREADY_ADDED(5023, "Câu hỏi đã có trong đề thi", HttpStatus.CONFLICT),
    EXAM_NOT_ARCHIVED(5024, "Đề thi không ở trạng thái lưu trữ", HttpStatus.CONFLICT),

    // === Dev 5 - Mentor Schedule ===
    SCHEDULE_NOT_FOUND(6001, "Không tìm thấy lịch", HttpStatus.NOT_FOUND),
    INVALID_SCHEDULE_TIME(6002, "Giờ kết thúc phải sau giờ bắt đầu", HttpStatus.BAD_REQUEST),
    SCHEDULE_TIME_CONFLICT(6003, "Khung giờ bị trùng với lịch khác", HttpStatus.CONFLICT),
    SCHEDULE_NOT_AVAILABLE(6004, "Lịch này không còn trống", HttpStatus.CONFLICT),
    MENTOR_HOURLY_RATE_NOT_SET(6005, "Mentor chưa được cấu hình học phí theo giờ", HttpStatus.BAD_REQUEST),
    SCHEDULE_HAS_BOOKING(6006, "Slot đã có học viên đặt, không thể sửa hoặc xóa", HttpStatus.CONFLICT),
    SCHEDULE_STATUS_INVALID(6007, "Chỉ được mở (available) hoặc đóng (cancelled) slot", HttpStatus.BAD_REQUEST),
    BOOKING_NOT_FOUND(6008, "Không tìm thấy lịch hẹn", HttpStatus.NOT_FOUND),
    BOOKING_NOT_CANCELLABLE(6009, "Lịch hẹn ở trạng thái này không thể hủy hoặc đổi", HttpStatus.BAD_REQUEST),
    BOOKING_CANCEL_DEADLINE_PASSED(6010, "Đã quá thời hạn được hủy hoặc đổi lịch", HttpStatus.BAD_REQUEST),
    RESCHEDULE_MENTOR_MISMATCH(6011, "Chỉ được đổi sang slot của cùng mentor", HttpStatus.BAD_REQUEST),
    BOOKING_STATUS_INVALID(6012, "Trạng thái lịch hẹn không hợp lệ", HttpStatus.BAD_REQUEST),
    BOOKING_NOT_STARTED(6013, "Buổi học chưa bắt đầu, chưa thể cập nhật kết quả", HttpStatus.BAD_REQUEST),
    NOTIFICATION_NOT_FOUND(6014, "Không tìm thấy thông báo", HttpStatus.NOT_FOUND),
    // Dev 5 x Dev 4: thanh toán buổi kèm 1-1 qua SePay
    SESSION_PRICE_INVALID(6015, "Giá buổi học không hợp lệ để thanh toán (phải là số nguyên VND lớn hơn 0)", HttpStatus.BAD_REQUEST),
    BOOKING_PAYMENT_PENDING(6016, "Lịch hẹn chưa được thanh toán, hãy thanh toán hoặc hủy rồi đặt lại", HttpStatus.BAD_REQUEST),
    RESCHEDULE_PRICE_MISMATCH(6017, "Chỉ được đổi sang slot có cùng giá với buổi đã thanh toán", HttpStatus.BAD_REQUEST),
    // Dev 5: lịch học cá nhân (TASK-D5-01)
    STUDY_SCHEDULE_NOT_FOUND(6018, "Không tìm thấy lịch học", HttpStatus.NOT_FOUND),
    STUDY_SCHEDULE_TIME_INVALID(6019, "Thời gian học phải ở tương lai", HttpStatus.BAD_REQUEST),
    // Dev 5: Learning Path (TASK-D5-03)
    LEARNING_PATH_NOT_FOUND(6020, "Không tìm thấy lộ trình học", HttpStatus.NOT_FOUND),
    LEARNING_PATH_ITEM_INVALID(6021, "Chặng của lộ trình không hợp lệ (trùng chặng hoặc điểm mục tiêu chỉ dùng cho đề luyện tập)", HttpStatus.BAD_REQUEST),
    LEARNING_PATH_HAS_STUDENTS(6022, "Lộ trình đã có học viên ghi danh, không thể xóa", HttpStatus.CONFLICT),
    // Dev 5: AI Practice (TASK-D5-04)
    PRACTICE_EXAM_NOT_FOUND(6023, "Không tìm thấy đề luyện tập", HttpStatus.NOT_FOUND),
    PRACTICE_ANSWER_NOT_FOUND(6024, "Không tìm thấy bài làm", HttpStatus.NOT_FOUND),
    PRACTICE_EVALUATION_NOT_FOUND(6025, "Không tìm thấy kết quả chấm", HttpStatus.NOT_FOUND),
    PRACTICE_EXAM_HAS_ANSWERS(6026, "Đề luyện tập đã có bài làm, không thể xóa", HttpStatus.CONFLICT),
    AI_NOT_CONFIGURED(6027, "Chưa cấu hình dịch vụ AI chấm bài", HttpStatus.SERVICE_UNAVAILABLE),
    AI_EVALUATION_FAILED(6028, "AI chấm bài thất bại, vui lòng thử lại sau", HttpStatus.BAD_GATEWAY),
    ;

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
