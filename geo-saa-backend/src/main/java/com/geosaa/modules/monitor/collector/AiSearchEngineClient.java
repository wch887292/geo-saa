package com.geosaa.modules.monitor.collector;

/**
 * AI 搜索引擎适配器抽象。
 *
 * <p>每个实现代表一个真实数据源（如 Perplexity、OpenAI 兼容网关），
 * 负责把一个问题投递给引擎并返回回答文本。回答文本随后由
 * {@link GeoDataCollectorService} 聚合为 mentionRate / firstRecommendRate /
 * collectionCount 三类指标。
 */
public interface AiSearchEngineClient {

    /** 引擎标识（写入 remark，便于追溯数据来源） */
    String engineName();

    /**
     * 向引擎投递一个问题，返回回答文本。
     *
     * @param query 完整问题（已替换品牌占位符）
     * @return 引擎回答文本；失败抛异常，由调用方统一捕获
     */
    String probe(String query) throws Exception;
}
