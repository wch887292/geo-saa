package com.geosaa.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 国际化配置（#2 i18n 中英切换）。
 *
 * <p>语言解析策略：优先读取请求头 {@code Accept-Language}（浏览器/前端自动携带），
 * 其次支持请求参数 {@code ?lang=en} 临时切换，默认简体中文（zh_CN）。
 *
 * <p>消息源：{@code classpath:messages}（默认 zh_CN）+ {@code messages_en}（英文），
 * UTF-8 编码，支持热加载（dev 环境修改 messages 文件无需重启）。
 *
 * <p>前端配合：vue-i18n 管理 UI 文案，同时在 axios 请求拦截器中设置
 * {@code Accept-Language} 头，使后端返回的错误消息与前端语言保持一致。
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    /** 默认语言：简体中文 */
    private static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // 未找到 key 时返回 key 本身而非抛出异常，避免前端看到 NoSuchMessageException 堆栈
        source.setUseCodeAsDefaultMessage(true);
        // dev 环境缓存 5 秒，prod 环境缓存 -1（永久）
        source.setCacheSeconds(5);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        // 支持的语言列表，超出范围的回退到默认
        resolver.setSupportedLocales(java.util.List.of(
                Locale.SIMPLIFIED_CHINESE,
                Locale.ENGLISH,
                Locale.US
        ));
        return resolver;
    }

    /**
     * 允许通过请求参数 ?lang=en / ?lang=zh_CN 临时切换语言，
     * 便于前端在未设置 Accept-Language 时调试或用户手动选择后透传。
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
