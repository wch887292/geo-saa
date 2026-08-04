package com.geosaa.audit;

import com.geosaa.common.Constant;
import com.geosaa.modules.system.entity.SystemAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Pointcut("execution(* com.geosaa.modules.*.controller.*.*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        Object result = joinPoint.proceed();

        long endTime = System.currentTimeMillis();

        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            SystemAuditLog auditLog = new SystemAuditLog();
            auditLog.setOperation(joinPoint.getSignature().getName());
            auditLog.setModule(joinPoint.getTarget().getClass().getSimpleName());
            auditLog.setRequestUrl(request.getRequestURI());
            auditLog.setRequestMethod(request.getMethod());
            auditLog.setUsername(authentication != null ? authentication.getName() : "anonymous");
            auditLog.setDuration((int) (endTime - startTime));
            auditLog.setCreateTime(LocalDateTime.now());

            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            // 审计日志记录失败不影响主流程
        }

        return result;
    }
}