package com.sba301.englishcenter.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthTokenType {

    REFRESH("REFRESH"),
    ACCESS_REVOKED("ACCESS_REVOKED"),
    ACCOUNT_ACTIVATION("ACCOUNT_ACTIVATION");

    private final String value;
}
