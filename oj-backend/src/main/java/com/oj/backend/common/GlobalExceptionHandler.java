package com.oj.backend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 捕获自定义的业务异常 (透传异常自带的 code，如 1、401、403)
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常 [code={}]: {}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    // 2. 捕获 Spring Validation 参数校验失败异常
    // 注意：改用父类 BindException，同时兼容 @RequestBody (POST/PUT) 和 @ModelAttribute/QueryDTO (GET) 校验！
    @ExceptionHandler(BindException.class)
    public Result<Void> handleValidException(BindException e) {
        BindingResult bindingResult = e.getBindingResult();
        FieldError fieldError = bindingResult.getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数不合法";
        log.warn("参数校验失败: {}", message);
        return Result.error(1, message);
    }

    // 3. 兜底捕获未知的系统异常
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统内部未知异常: ", e); // 使用 log.error 打印完整堆栈到日志系统，替代 e.printStackTrace()
        return Result.error(500, "系统繁忙，请稍后再试！");
    }
}