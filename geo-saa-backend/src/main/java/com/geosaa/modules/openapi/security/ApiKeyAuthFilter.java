package com.geosaa.modules.openapi.security;

import com.geosaa.common.tenant.TenantContext;
import com.geosaa.modules.openapi.entity.ApiKey;
import com.geosaa.modules.openapi.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * API Key 认证过滤器。
 *
 * <p>仅处理 /api/v1/open/** 路径的请求，从 X-API-Key 请求头读取并验证 API Key。
 * 验证成功后设置租户上下文和 Spring Security 上下文（角色 ROLE_API_KEY）。
 * 验证失败时 SecurityContext 为空，Spring Security 返回 401。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    private static final String OPEN_API_PREFIX = "/api/v1/open/";

    private final ApiKeyService apiKeyService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅过滤开放 API 路径
        return !request.getRequestURI().startsWith(OPEN_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKeyStr = request.getHeader(API_KEY_HEADER);

        if (StringUtils.hasText(apiKeyStr) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                ApiKey apiKey = apiKeyService.validateKey(apiKeyStr);

                // 设置租户上下文
                TenantContext.setTenantId(apiKey.getTenantId());
                TenantContext.setSuperAdmin(false); // API Key 不是超级管理员

                // 创建 Authentication（角色 ROLE_API_KEY，权限 API_KEY_ACCESS）
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        "api-key:" + apiKey.getId(),
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_KEY"))
                    );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("API Key 认证成功: keyId={}, tenantId={}", apiKey.getId(), apiKey.getTenantId());
            } catch (Exception e) {
                log.warn("API Key 认证失败: {}", e.getMessage());
                // 认证失败，SecurityContext 为空，Spring Security 返回 401
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
