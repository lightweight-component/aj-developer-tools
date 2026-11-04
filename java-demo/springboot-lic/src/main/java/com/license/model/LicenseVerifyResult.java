package com.license.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 验证结果类
 */
@RequiredArgsConstructor
public class LicenseVerifyResult {
    @Getter
    private final boolean valid;

    @Getter
    private final String message;

    @Getter
    private final License license;

    public LicenseVerifyResult(boolean valid, String message) {
        this(valid, message, null);
    }
}