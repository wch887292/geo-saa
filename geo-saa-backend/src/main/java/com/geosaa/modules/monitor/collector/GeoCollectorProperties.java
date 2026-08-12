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

    /** Perplexity 官方 API（https://docs.perplexity.ai） */
    private Perplexity perplexity = new Perplexity();

    /** OpenAI 兼容端点（可指向通义/豆包/DeepSeek 等，用于模拟 AI 搜索问答链路） */
    private OpenaiCompat openaiCompat = new OpenaiCompat();

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
