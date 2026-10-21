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
    ;

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
