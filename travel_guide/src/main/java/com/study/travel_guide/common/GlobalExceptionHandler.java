package com.study.travel_guide.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("business error: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        log.debug("接口不存在: {}", e.getMessage());
        return Result.error(404, "接口不存在");
    }

    // 扫描器/爬虫带奇怪的 Accept 头导致 JSON 写不回去（406），降级处理、不刷屏
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        log.debug("客户端不接受响应格式: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    // 客户端在响应写出途中断开（Broken pipe），降级处理、不当 ERROR
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public void handleNotWritable(HttpMessageNotWritableException e) {
        log.debug("响应写出失败（客户端可能已断开）: {}", e.getMessage());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException e) {
        log.debug("客户端断开连接，异步请求不可用: {}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("unexpected error", e);
        return Result.error(500, "服务器内部错误");
    }
}
