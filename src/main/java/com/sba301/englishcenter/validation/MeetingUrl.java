package com.sba301.englishcenter.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Link phòng học online: chỉ nhận https của Google Meet, Zoom, Microsoft Teams, Discord.
 * Để trống (null / "") thì bỏ qua — mentor có thể cập nhật link sau.
 */
@Documented
@Constraint(validatedBy = MeetingUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MeetingUrl {

    String message() default "Link phòng học chỉ nhận Google Meet, Zoom, Microsoft Teams hoặc Discord (https://...)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
