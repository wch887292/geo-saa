package com.geosaa.ai;

public interface AiAdapter {
    /**
     * AI 意图分析 - 分析品牌在 AI 平台的可见度
     */
    String analyzeIntent(String brandName, String keywords, String platform);

    /**
     * 生成 GEO 优化内容
     */
    String generateContent(String prompt, String contentType, int wordCount);

    /**
     * 检查品牌可见度
     */
    VisibilityResult checkVisibility(String brandName, String platform);

    /**
     * 获取适配器类型
     */
    String getType();
}