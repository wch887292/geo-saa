package com.geosaa.common.exception;

import com.geosaa.common.Result;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleUnauthorizedException(UnauthorizedException e) {
        log.warn("未授权: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(ValidationException e) {
        log.warn("参数校验异常: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", errorMsg);
        return Result.error(400, errorMsg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * 请求体缺失或 JSON 格式错误。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(400, "请求体格式错误或为空");
    }

    /**
     * 方法级鉴权（{@code @PreAuthorize}）拦截，返回 403 而不是 500。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.error(403, "权限不足，无法访问该资源");
    }

    /**
     * 路径不存在。
     *
     * <p>修复前这类异常会掉进最下面的 {@code Exception} 兜底分支，被记成
     * “系统内部异常 + 500”，日志里刷了大量误导性的 ERROR
     * （典型如前端拼错路径请求 {@code /api/v1/auth/userinfo}）。
     * 现在统一按 404 处理，且只打 WARN。
     *
     * <p>{@code NoResourceFoundException} 为 Spring 6.1+ 静态资源未命中时抛出，
     * {@code NoHandlerFoundException} 需配合 {@code spring.mvc.throw-exception-if-no-handler-found=true}。
     */
    @ExceptionHandler({
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            NoHandlerFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(Exception e) {
        log.warn("接口不存在: {}", e.getMessage());
        return Result.error(404, "请求的接口不存在");
    }

    /**
     * 数据库约束冲突（唯一键重复、外键约束、非空约束）。
     *
     * <p>修复前这类异常会掉进最下面的 {@code Exception} 兜底分支：重复创建同名品牌、
     * 重复提交同名角色等正常业务冲突，一律返回 {@code 500服务器内部错误}，
     * 既误导前端（前端无法给出“已存在”这类可读提示），又污染错误日志与告警。
     * 现在按 400 业务错误返回，并把底层 SQL 细节收敛掉，只暴露可读原因。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String rootMsg = extractRootMessage(e);
        log.warn("数据约束冲突: {}", rootMsg);
        String lower = rootMsg.toLowerCase(java.util.Locale.ROOT);
        String friendly;
        if (lower.contains("duplicate entry") || lower.contains("duplicate")) {
            friendly = "记录已存在，请勿重复提交";
        } else if (lower.contains("cannot be null")) {
            friendly = "存在必填字段未填写";
        } else {
            friendly = "数据写入失败，请检查输入是否重复或格式是否正确";
        }
        return Result.error(400, friendly);
    }

    /**
     * 消息队列不可用。
     *
     * <p>业务层已对 MQ 不可达做了降级兜底，这里只兜住漏网场景（例如同步调用 AI 网关
     * 之外的路径），返回 503 让上游明确知道是依赖不可用，而不是"服务器崩了"。
     */
    @ExceptionHandler(AmqpException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleAmqpException(AmqpException e) {
        log.error("消息队列不可用: {}", e.getMessage());
        return Result.error(503, "任务队列暂时不可用，请稍后重试");
    }

    /**
     * 从异常链中提取最底层的原因信息。
     */
    private String extractRootMessage(Throwable e) {
        Throwable cur = e;
        String last = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        while (cur != null) {
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                last = cur.getMessage();
            }
            cur = cur.getCause();
        }
        return last == null ? "" : last;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统内部异常", e);
        return Result.error(500, "服务器内部错误，请稍后重试");
    }
}