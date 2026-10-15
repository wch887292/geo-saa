package com.geosaa.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.Result;
import com.geosaa.modules.system.entity.Tenant;
import com.geosaa.modules.system.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 租户管理接口（#4 多租户 SaaS 化）。
 *
 * <p>仅超级管理员（SUPER_ADMIN）可访问。所有接口路径前缀 /api/v1/system/tenant。
 * 租户表本身忽略租户过滤，超级管理员可管理所有租户。
 */
@RestController
@RequestMapping("/api/v1/system/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    /**
     * 分页查询租户列表。
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:tenant:view')")
    public Result<Page<Tenant>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(tenantService.page(pageNum, pageSize, keyword));
    }

    /**
     * 获取租户详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:view')")
    public Result<Tenant> getById(@PathVariable Long id) {
        return Result.success(tenantService.getById(id));
    }

    /**
     * 创建租户。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:tenant:create')")
    public Result<Tenant> create(@RequestBody Tenant tenant) {
        return Result.success(tenantService.create(tenant));
    }

    /**
     * 更新租户。
     */
    @PutMapping
    @PreAuthorize("hasAuthority('system:tenant:update')")
    public Result<Tenant> update(@RequestBody Tenant tenant) {
        return Result.success(tenantService.update(tenant));
    }

    /**
     * 禁用/启用租户。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:tenant:update')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        tenantService.updateStatus(id, status);
        return Result.success(null);
    }

    /**
     * 删除租户（逻辑删除）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:tenant:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return Result.success(null);
    }
}
