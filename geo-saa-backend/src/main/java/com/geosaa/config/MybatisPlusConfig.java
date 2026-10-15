package com.geosaa.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.geosaa.config.tenant.GeoTenantLineHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>#7 信创适配：根据 spring.datasource.url 自动检测数据库类型，
 * 支持 MySQL（默认）、达梦 DM、人大金仓 KingbaseES。
 * 多租户拦截器必须在分页拦截器之前添加。
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    private final GeoTenantLineHandler tenantLineHandler;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    public MybatisPlusConfig(GeoTenantLineHandler tenantLineHandler) {
        this.tenantLineHandler = tenantLineHandler;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 多租户拦截器必须在分页拦截器之前添加，否则分页 count SQL 不会被追加租户条件
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(detectDbType()));
        return interceptor;
    }

    /**
     * 根据 JDBC URL 自动检测数据库类型。
     */
    private DbType detectDbType() {
        if (datasourceUrl == null || datasourceUrl.isEmpty()) {
            log.info("未配置 datasource.url，使用默认 MySQL 方言");
            return DbType.MYSQL;
        }
        String url = datasourceUrl.toLowerCase();
        if (url.contains(":dm:")) {
            log.info("检测到达梦 DM 数据库，使用 DM 方言");
            return DbType.DM;
        }
        if (url.contains(":kingbase:")) {
            log.info("检测到人大金仓 KingbaseES，使用 KINGBASE_ES 方言");
            return DbType.KINGBASE_ES;
        }
        if (url.contains(":postgresql:")) {
            log.info("检测到 PostgreSQL，使用 POSTGRE_SQL 方言");
            return DbType.POSTGRE_SQL;
        }
        if (url.contains(":oracle:")) {
            log.info("检测到 Oracle，使用 ORACLE 方言");
            return DbType.ORACLE;
        }
        log.info("使用默认 MySQL 方言");
        return DbType.MYSQL;
    }
}
