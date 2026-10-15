package com.geosaa.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.common.i18n.MessageUtils;
import com.geosaa.modules.system.entity.Tenant;
import com.geosaa.modules.system.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 租户管理服务（#4 多租户 SaaS 化）。
 *
 * <p>仅超级管理员（SUPER_ADMIN）可调用本服务的方法。
 * 租户表本身在 TenantLineHandler 中被配置为忽略租户过滤，
 * 因此超级管理员可以查看和管理所有租户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;

    /**
     * 分页查询租户列表。
     */
    public Page<Tenant> page(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Tenant::getTenantName, keyword)
                    .or().like(Tenant::getTenantCode, keyword));
        }
        wrapper.orderByDesc(Tenant::getCreateTime);
        return tenantMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 根据 ID 获取租户。
     */
    public Tenant getById(Long id) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException(MessageUtils.get("common.not_found"));
        }
        return tenant;
    }

    /**
     * 创建租户。
     */
    public Tenant create(Tenant tenant) {
        // 校验租户编码唯一
        Long count = tenantMapper.selectCount(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getTenantCode, tenant.getTenantCode()));
        if (count != null && count > 0) {
            throw new BusinessException("租户编码已存在: " + tenant.getTenantCode());
        }
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
        tenantMapper.insert(tenant);
        log.info("创建租户: id={}, code={}, name={}", tenant.getId(), tenant.getTenantCode(), tenant.getTenantName());
        return tenant;
    }

    /**
     * 更新租户。
     */
    public Tenant update(Tenant tenant) {
        Tenant exist = getById(tenant.getId());
        // 校验租户编码唯一（排除自身）
        Long count = tenantMapper.selectCount(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getTenantCode, tenant.getTenantCode())
                        .ne(Tenant::getId, tenant.getId()));
        if (count != null && count > 0) {
            throw new BusinessException("租户编码已存在: " + tenant.getTenantCode());
        }
        tenantMapper.updateById(tenant);
        log.info("更新租户: id={}, code={}, name={}", tenant.getId(), tenant.getTenantCode(), tenant.getTenantName());
        return tenantMapper.selectById(tenant.getId());
    }

    /**
     * 禁用/启用租户。
     */
    public void updateStatus(Long id, Integer status) {
        Tenant tenant = getById(id);
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
        log.info("更新租户状态: id={}, status={}", id, status);
    }

    /**
     * 删除租户（逻辑删除）。
     * 注意：删除租户不会删除该租户下的业务数据，业务数据仍保留但无法访问。
     * 如需物理删除租户数据，需单独的数据清理流程。
     */
    public void delete(Long id) {
        Tenant tenant = getById(id);
        if (id.equals(1L)) {
            throw new BusinessException("默认租户不可删除");
        }
        tenantMapper.deleteById(id);
        log.info("删除租户: id={}, code={}, name={}", id, tenant.getTenantCode(), tenant.getTenantName());
    }

    /**
     * 检查租户是否启用且未过期。
     */
    public boolean isTenantActive(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return false;
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
            return false;
        }
        if (tenant.getExpireTime() != null && tenant.getExpireTime().isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }
}
