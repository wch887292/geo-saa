package com.geosaa.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geosaa.modules.system.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 Mapper（#4 多租户 SaaS 化）。
 *
 * <p>注意：tenant 表本身在 TenantLineHandler 中被配置为忽略租户过滤，
 * 因为超级管理员需要管理所有租户。
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
