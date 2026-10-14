package com.sba301.englishcenter.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleName {

    ADMIN("ADMIN"),
    MENTOR("MENTOR"),
    STUDENT("STUDENT");

    private final String value;
}
