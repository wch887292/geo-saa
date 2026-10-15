package com.geosaa.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路 TraceId 过滤器（O3 可观测性）。
 *
 * <p>每个 HTTP 请求入口生成（或透传）traceId，写入 SLF4J MDC，
 * 贯穿 Controller → Service → Mapper → MQ 消费的整条调用链。
 * 结构化日志（prod profile）会将 traceId 作为顶层字段输出，
 * 日志平台可按 traceId 检索单次请求的全部日志。
 *
 * <p>优先读取请求头 {@code X-Trace-Id}（便于上游服务透传），
 * 缺失时生成 UUID 短码。请求结束后在 finally 中清理 MDC，防止线程池复用导致串号。
 */
@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_PATH = "path";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_PATH, request.getRequestURI());
        // 响应头回传 traceId，便于前端/调用方关联日志
        response.setHeader(TRACE_ID_HEADER, traceId);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            MDC.put("duration_ms", String.valueOf(duration));
            log.debug("请求完成: path={}, method={}, status={}, duration={}ms",
                    request.getRequestURI(), request.getMethod(), response.getStatus(), duration);
            MDC.clear();
        }
    }
}
