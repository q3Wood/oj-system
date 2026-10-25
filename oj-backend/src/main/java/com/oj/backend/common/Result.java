package com.oj.backend.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    // 业务状态码：0-成功，1-通用业务失败，401-未登录/凭证过期，403-无权限
    private Integer code;
    // 提示信息
    private String message;
    // 返回数据
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "操作成功", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(0, "操作成功", null);
    }

    // 默认业务错误 (code = 1)
    public static <T> Result<T> error(String message) {
        return new Result<>(1, message, null);
    }

    // 新增：指定状态码的错误响应 (例如 401 未登录、403 权限不足)
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}