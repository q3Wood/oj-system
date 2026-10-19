package com.oj.backend.common;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 捕获自定义的业务异常 (比如 "用户名已存在")
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        // System.out.println("捕获到业务异常: " + e.getMessage());
        return Result.error(e.getMessage());
    }

    // 2. 捕获 Spring Validation 注解校验失败的异常 (比如密码少于5位)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        FieldError fieldError = bindingResult.getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.error(message);
    }

    // 3. 兜底捕获未知的系统异常 (防止把乱七八糟的堆栈错误直接暴露给前端)
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        e.printStackTrace(); // 后台打日志
        return Result.error("系统繁忙，请稍后再试！");
    }
}