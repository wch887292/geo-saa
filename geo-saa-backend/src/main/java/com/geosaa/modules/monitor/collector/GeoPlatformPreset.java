package com.geosaa.modules.monitor.collector;

import java.util.Arrays;
import java.util.List;

/**
 * 国内主流大模型平台预设配置。
 *
 * <p>大部分平台提供 OpenAI 兼容的 /chat/completions 接口，
 * 只需配置 API Key 即可使用。预设默认 API URL 和推荐模型。
 */
public enum GeoPlatformPreset {

    // ========== 国内主流平台 ==========
    DEEPSEEK("deepseek", "DeepSeek",
            "https://api.deepseek.com/v1/chat/completions",
            "deepseek-chat", "深度求索，开源模型代表"),

    TONGYI("tongyi", "通义千问",
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            "qwen-plus", "阿里云通义千问"),

    DOUBAO("doubao", "豆包",
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            "doubao-pro-32k", "字节跳动豆包"),

    ZHIPU("zhipu", "智谱清言",
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            "glm-4-plus", "智谱AI清言"),

    KIMI("kimi", "Kimi",
            "https://api.moonshot.cn/v1/chat/completions",
            "moonshot-v1-8k", "月之暗面Kimi"),

    XUNFEI("xunfei", "讯飞星火",
            "https://spark-api-open.xf-yun.com/v1/chat/completions",
            "generalv3.5", "科大讯飞星火"),

    WENXIN("wenxin", "文心一言",
            "https://qianfan.baidubce.com/v2/chat/completions",
            "ernie-4.0-turbo-8k", "百度文心一言"),

    HUNYUAN("hunyuan", "腾讯混元",
            "https://api.hunyuan.cloud.tencent.com/v1/chat/completions",
            "hunyuan-pro", "腾讯混元"),

    QI_360("360", "360智脑",
            "https://api.360.cn/v1/chat/completions",
            "360gpt-pro", "360智脑"),

    // ========== 国际平台 ==========
    PERPLEXITY("perplexity", "Perplexity",
            "https://api.perplexity.ai/chat/completions",
            "sonar", "Perplexity AI搜索引擎"),

    OPENAI("openai", "ChatGPT",
            "https://api.openai.com/v1/chat/completions",
            "gpt-4o-mini", "OpenAI ChatGPT"),

    CLAUDE("claude", "Claude",
            "https://api.anthropic.com/v1/messages",
            "claude-3-5-sonnet-20241022", "Anthropic Claude（非完全OpenAI兼容，需特殊处理）"),

    GEMINI("gemini", "Gemini",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent",
            "gemini-pro", "Google Gemini（非OpenAI兼容格式）");

    private final String platformId;
    private final String platformName;
    private final String defaultApiUrl;
    private final String defaultModel;
    private final String description;

    GeoPlatformPreset(String platformId, String platformName, String defaultApiUrl,
                      String defaultModel, String description) {
        this.platformId = platformId;
        this.platformName = platformName;
        this.defaultApiUrl = defaultApiUrl;
        this.defaultModel = defaultModel;
        this.description = description;
    }

    public String getPlatformId() { return platformId; }
    public String getPlatformName() { return platformName; }
    public String getDefaultApiUrl() { return defaultApiUrl; }
    public String getDefaultModel() { return defaultModel; }
    public String getDescription() { return description; }

    /** 获取所有支持的平台列表（用于前端展示） */
    public static List<GeoPlatformPreset> getAllPlatforms() {
        return Arrays.asList(values());
    }

    /** 根据 platformId 查找预设 */
    public static GeoPlatformPreset getById(String platformId) {
        if (platformId == null) return null;
        for (GeoPlatformPreset p : values()) {
            if (p.platformId.equalsIgnoreCase(platformId)) return p;
        }
        return null;
    }
}
