package com.oj.backend.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    // 业务状态码：0-成功，1-失败
    private Integer code;
    // 提示信息
    private String message;
    // 返回数据
    private T data;

    // 快捷成功响应（带数据）
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "操作成功", data);
    }

    // 快捷成功响应（无数据）
    public static <T> Result<T> success() {
        return new Result<>(0, "操作成功", null);
    }

    // 快捷错误响应
    public static <T> Result<T> error(String message) {
        return new Result<>(1, message, null);
    }
}