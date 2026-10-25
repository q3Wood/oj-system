package com.oj.backend.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    // 默认业务异常，code 为 1（兼容已有代码）
    public BusinessException(String message) {
        super(message);
        this.code = 1;
    }

    // 支持指定错误码（如 401、403）
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}