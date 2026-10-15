package com.geosaa.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * 国际化消息工具（#2 i18n 中英切换）。
 *
 * <p>封装 Spring {@link MessageSource}，提供静态便捷方法，
 * 自动从 {@link LocaleContextHolder} 获取当前请求的 Locale
 * （由 {@code AcceptHeaderLocaleResolver} 或 {@code ?lang=} 参数解析）。
 *
 * <p>用法：
 * <pre>{@code
 * // 无参数
 * String msg = MessageUtils.get("auth.login_success");
 * // 带参数
 * String msg = MessageUtils.get("content.batch_submit_success", 10);
 * // 带默认值
 * String msg = MessageUtils.getOrDefault("unknown.key", "fallback");
 * }</pre>
 *
 * <p>所有 Service / Controller / ExceptionHandler 中需要返回给前端的文案
 * 均应通过本工具获取，禁止硬编码中文字符串。
 */
@Component
public class MessageUtils {

    private static MessageSource messageSource;

    public MessageUtils(MessageSource messageSource) {
        MessageUtils.messageSource = messageSource;
    }

    /**
     * 获取国际化消息，无参数。
     *
     * @param key 消息键，如 "auth.login_success"
     * @return 本地化消息；key 不存在或 MessageSource 未初始化（如纯单测环境）时返回 key 本身
     */
    public static String get(String key) {
        if (messageSource == null) {
            return key;
        }
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    /**
     * 获取国际化消息，带参数。
     *
     * @param key  消息键
     * @param args 占位符参数，对应 messages 文件中的 {0} {1}
     * @return 本地化消息；MessageSource 未初始化时返回 key 本身
     */
    public static String get(String key, Object... args) {
        if (messageSource == null) {
            return key;
        }
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    /**
     * 获取国际化消息，key 不存在时返回默认值。
     *
     * @param key          消息键
     * @param defaultValue key 不存在时的兜底文案
     * @param args         占位符参数
     * @return 本地化消息或默认值
     */
    public static String getOrDefault(String key, String defaultValue, Object... args) {
        if (messageSource == null) {
            return defaultValue;
        }
        try {
            String msg = messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
            return msg != null ? msg : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 获取当前请求的语言代码，便于日志或业务逻辑判断。
     *
     * @return 语言代码，如 "zh_CN"、"en"
     */
    public static String currentLanguage() {
        return LocaleContextHolder.getLocale().toLanguageTag();
    }
}
