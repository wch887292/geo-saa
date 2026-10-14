package com.geosaa.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常处理回归测试。
 *
 * <p>核心回归点：唯一约束冲突原会掉进 {@code Exception} 兜底分支返回
 * {@code 500 服务器内部错误}，前端无法给出"已存在"这类可读提示，
 * 同时污染错误日志与告警。修复后应为 400 + 可读原因。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * 读取方法上的 @ResponseStatus，验证 HTTP 状态码语义。
     *
     * <p>注意：必须优先匹配「参数类型精确相等」的处理器。GlobalExceptionHandler 里
     * {@code handleNotFound} 的参数类型是 DataIntegrityViolationException 的父类，
     * 用 {@code isAssignableFrom} 遍历时若先撞上它，会误读到 NOT_FOUND(404)——
     * 而 handleNotFound 的 @ExceptionHandler 注解里列的是另一个异常类型，
     * 根本不会处理 DataIntegrityViolationException。
     * getDeclaredMethods() 的返回顺序 JVM 不作保证，这类测试天然脆弱。
     */
    private HttpStatus statusOf(Class<? extends Throwable> ex) throws Exception {
        Method fallback = null;
        for (Method candidate : GlobalExceptionHandler.class.getDeclaredMethods()) {
            if (candidate.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class) == null
                    || candidate.getParameterCount() != 1) {
                continue;
            }
            Class<?> declared = candidate.getParameterTypes()[0];
            if (declared == ex) {
                return statusAnnotationOf(candidate, ex);
            }
            if (declared.isAssignableFrom(ex) && fallback == null) {
                fallback = candidate;
            }
        }
        assertNotNull(fallback, "未找到处理 " + ex.getSimpleName() + " 的 @ExceptionHandler 方法");
        return statusAnnotationOf(fallback, ex);
    }

    private HttpStatus statusAnnotationOf(Method m, Class<? extends Throwable> ex) {
        org.springframework.web.bind.annotation.ResponseStatus rs =
                m.getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class);
        assertNotNull(rs, m.getName() + " 缺少 @ResponseStatus，HTTP 状态码会退化为 200");
        return rs.value();
    }

    @Test
    @DisplayName("唯一约束冲突应返回 400 而非 500（回归：原返回 500 服务器内部错误）")
    void duplicateKeyShouldReturn400() throws Exception {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement; Duplicate entry 'admin' for key 'user_info.uk_username'");

        var result = handler.handleDataIntegrityViolation(ex);

        assertEquals(400, result.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(DataIntegrityViolationException.class));
        assertEquals("记录已存在，请勿重复提交", result.getMessage());
    }

    @Test
    @DisplayName("非空约束冲突应返回 400 且提示必填字段")
    void notNullViolationShouldReturn400() throws Exception {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Column 'brand_name' cannot be null");

        var result = handler.handleDataIntegrityViolation(ex);

        assertEquals(400, result.getCode());
        assertEquals("存在必填字段未填写", result.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(DataIntegrityViolationException.class));
    }

    @Test
    @DisplayName("其他约束冲突不应把底层 SQL 细节暴露给前端")
    void otherViolationShouldNotLeakSqlDetail() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Cannot delete or update a parent row: a foreign key constraint fails "
                        + "(geo_saa.brand_knowledge, CONSTRAINT fk_xxx FOREIGN KEY (brand_id) REFERENCES brand_info(id))");

        var result = handler.handleDataIntegrityViolation(ex);

        assertEquals(400, result.getCode());
        assertFalse(result.getMessage().contains("CONSTRAINT"),
                "不应把数据库约束名/SQL 片段透出给前端");
        assertFalse(result.getMessage().contains("SELECT"),
                "不应把 SQL 语句透出给前端");
    }

    @Test
    @DisplayName("消息队列不可用应返回 503，明确是依赖问题而非服务端崩溃")
    void amqpFailureShouldReturn503() throws Exception {
        AmqpConnectException ex = new AmqpConnectException(new java.net.ConnectException("refused"));

        var result = handler.handleAmqpException(ex);

        assertEquals(503, result.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, statusOf(AmqpConnectException.class));
    }
}