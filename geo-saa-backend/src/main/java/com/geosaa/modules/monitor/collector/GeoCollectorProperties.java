package com.geosaa.modules.monitor.collector;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * GEO 真实数据采集器配置（app.geo.collector.*）。
 *
 * <p>默认全部关闭：只有显式配置 {@code app.geo.collector.enabled=true}
 * 且至少启用一个引擎并填入 API Key 后，调度任务才会真正去 AI 搜索
 * 引擎采集数据。未配置时不产生任何调用，也绝不写入模拟数据。
 *
 * <p>v2 新增多平台配置：通过 platforms 列表可同时配置多个大模型平台
 * （DeepSeek/通义/豆包/智谱/Kimi/讯飞/文心/混元/360 等），每个平台
 * 独立配置 API Key，采集时自动遍历所有启用的平台。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.geo.collector")
public class GeoCollectorProperties {

    /** 采集总开关 */
    private boolean enabled = false;

    /** 定时任务 cron（默认每天 01:30） */
    private String cron = "0 30 1 * * *";

    /** 待采集的品牌列表（为空则不采集） */
    private List<String> brands = new ArrayList<>();

    /** 每个品牌 × 每个引擎的探测次数（决定 mentionRate 分母） */
    private int probesPerBrand = 3;

    /** 探测问题模板，{brand} 会被替换为品牌名 */
    private String queryTemplate = "请介绍一下品牌「{brand}」的产品或服务，并结合公开信息给出你的推荐意见。";

    /** 单次请求超时（秒） */
    private int timeoutSeconds = 20;

    /** 品牌官网 URL（用于来源引用检测，key=品牌名，value=官网域名） */
    private java.util.Map<String, String> brandWebsites = new java.util.HashMap<>();

    /** 品牌实体基准信息（用于实体正确性检测，key=品牌名，value=实体信息JSON） */
    private java.util.Map<String, String> brandEntities = new java.util.HashMap<>();

    /** 目标问句库（用于 AAO 答案命中检测） */
    private List<String> targetQueries = new ArrayList<>();

    /** 竞品列表（用于竞品对标） */
    private List<String> competitors = new ArrayList<>();

    /** Perplexity 官方 API（https://docs.perplexity.ai） */
    private Perplexity perplexity = new Perplexity();

    /** OpenAI 兼容端点（可指向通义/豆包/DeepSeek 等，用于模拟 AI 搜索问答链路） */
    private OpenaiCompat openaiCompat = new OpenaiCompat();

    /**
     * 多平台配置列表（v2 新增）。
     * 每个元素配置一个大模型平台，支持同时启用多个平台进行采集。
     * platformId 参考 {@link GeoPlatformPreset} 中的预设值。
     */
    private List<PlatformConfig> platforms = new ArrayList<>();

    @Data
    public static class PlatformConfig {
        /** 平台标识（参考 GeoPlatformPreset，如 deepseek/tongyi/doubao/zhipu/kimi/xunfei/wenxin/hunyuan/360/openai） */
        private String platformId;
        /** 平台名称（可选，不填则使用预设名称） */
        private String platformName;
        /** 是否启用 */
        private boolean enabled = false;
        /** API Key（必填） */
        private String apiKey = "";
        /** API URL（可选，不填则使用预设 URL） */
        private String apiUrl = "";
        /** 模型名称（可选，不填则使用预设模型） */
        private String model = "";
    }

    @Data
    public static class Perplexity {
        private boolean enabled = false;
        private String apiKey = "";
        private String apiUrl = "https://api.perplexity.ai/chat/completions";
        private String model = "sonar";
    }

    @Data
    public static class OpenaiCompat {
        private boolean enabled = false;
        private String apiKey = "";
        private String apiUrl = "";
        private String model = "";
    }
}
