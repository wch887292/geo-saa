package com.geosaa.modules.monitor.collector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geosaa.modules.monitor.entity.DataMonitorStat;
import com.geosaa.modules.monitor.mapper.DataMonitorStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GEO 真实数据采集服务（G-01）。
 *
 * <p>对每个品牌 × 每个启用的 AI 搜索引擎做多次探测，把回答文本聚合为
 * 三类指标并写入 {@code data_monitor_stat}：
 * <ul>
 *   <li>mention_rate        提及率：回答中出现品牌名的比例</li>
 *   <li>first_recommend_rate 首推率：回答开头即提及品牌的占比</li>
 *   <li>collection_count    收录强度：全部回答中品牌被提及的总次数</li>
 * </ul>
 *
 * <p>铁律：任何引擎调用失败都只记日志、跳过，<b>绝不写入随机数或模拟值</b>。
 * 若某品牌所有探测全部失败，则不写该品牌当日数据（hasData 保持 false）。
 * 写入采用 upsert（唯一键 stat_date+stat_type+stat_key），同一自然日重复执行
 * 只更新不新增，避免唯一键冲突与监控查询歧义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoDataCollectorService {

    private static final String DIMENSION = "geo-collector";
    public static final String TYPE_MENTION_RATE = "mention_rate";
    public static final String TYPE_FIRST_RECOMMEND = "first_recommend_rate";
    public static final String TYPE_COLLECTION = "collection_count";

    private final GeoCollectorProperties properties;
    private final List<AiSearchEngineClient> clients;
    private final DataMonitorStatMapper monitorStatMapper;

    /**
     * 全量采集：遍历配置的品牌与启用的引擎。
     *
     * @return 采集报告（品牌 → 各指标与成功率）
     */
    public Map<String, Object> collectAll() {
        Map<String, Object> report = new LinkedHashMap<>();
        if (!properties.isEnabled()) {
            report.put("skipped", "app.geo.collector.enabled=false");
            return report;
        }
        if (clients.isEmpty()) {
            report.put("skipped", "未启用任何采集引擎（perplexity / openai-compat）");
            return report;
        }
        if (properties.getBrands().isEmpty()) {
            report.put("skipped", "未配置 brands");
            return report;
        }

        LocalDate today = LocalDate.now();
        int okBrands = 0;
        int failBrands = 0;
        for (String brand : properties.getBrands()) {
            try {
                BrandCollectResult r = collectForBrand(brand, today);
                if (r.written) {
                    okBrands++;
                } else {
                    failBrands++;
                }
                report.put(brand, Map.of(
                        "written", r.written,
                        "probes", r.probes,
                        "successProbes", r.successProbes,
                        "engines", r.engines,
                        "metrics", r.metrics));
            } catch (Exception e) {
                failBrands++;
                log.error("GEO 采集失败 brand={}", brand, e);
                report.put(brand, Map.of("written", false, "error", e.getMessage()));
            }
        }
        report.put("summary", Map.of("date", today.toString(), "brands", properties.getBrands().size(),
                "ok", okBrands, "fail", failBrands));
        return report;
    }

    /**
     * 采集单个品牌并 upsert 写库。
     */
    private BrandCollectResult collectForBrand(String brand, LocalDate date) {
        List<String> answers = new ArrayList<>();
        List<String> engines = new ArrayList<>();
        int probes = 0;
        for (AiSearchEngineClient client : clients) {
            for (int i = 0; i < Math.max(1, properties.getProbesPerBrand()); i++) {
                probes++;
                String query = properties.getQueryTemplate().replace("{brand}", brand);
                try {
                    String answer = client.probe(query);
                    if (answer != null && !answer.isBlank()) {
                        answers.add(answer);
                        if (!engines.contains(client.engineName())) {
                            engines.add(client.engineName());
                        }
                    }
                } catch (Exception e) {
                    log.warn("GEO 引擎探测失败 engine={} brand={} probe={}: {}",
                            client.engineName(), brand, i, e.getMessage());
                }
            }
        }

        BrandCollectResult r = new BrandCollectResult();
        r.probes = probes;
        r.successProbes = answers.size();
        r.engines = String.join(",", engines);

        if (answers.isEmpty()) {
            log.warn("GEO 采集无有效回答，不写库 brand={}", brand);
            return r; // written=false
        }

        Map<String, Long> metrics = computeMetrics(brand, answers);
        r.metrics = metrics;

        upsert(date, brand, TYPE_MENTION_RATE, metrics.getOrDefault("mentionRate", 0L), r.engines);
        upsert(date, brand, TYPE_FIRST_RECOMMEND, metrics.getOrDefault("firstRecommendRate", 0L), r.engines);
        upsert(date, brand, TYPE_COLLECTION, metrics.getOrDefault("collectionCount", 0L), r.engines);
        r.written = true;
        log.info("GEO 采集完成 brand={} date={} engines={} probes={}/{} metrics={}",
                brand, date, r.engines, answers.size(), probes, metrics);
        return r;
    }

    /**
     * 从回答列表聚合三类指标（纯函数，便于单测）。
     *
     * <p>提及率 = 出现品牌名的回答数 / 回答总数 × 100；首推率 = 首段（前 80 字符）
     * 含品牌名的回答占比 × 100；收录强度 = 品牌名在所有回答中的出现总次数。
     */
    public static Map<String, Long> computeMetrics(String brand, List<String> answers) {
        if (answers == null || answers.isEmpty()) {
            return Map.of("mentionRate", 0L, "firstRecommendRate", 0L, "collectionCount", 0L);
        }
        String name = brand == null ? "" : brand;
        int total = answers.size();
        int mention = 0;
        int firstRecommend = 0;
        int collection = 0;
        for (String answer : answers) {
            if (answer == null) continue;
            if (answer.contains(name)) {
                mention++;
                collection += countOccurrences(answer, name);
                String head = answer.substring(0, Math.min(80, answer.length()));
                if (head.contains(name)) {
                    firstRecommend++;
                }
            }
        }
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("mentionRate", Math.round(mention * 100.0 / total));
        m.put("firstRecommendRate", Math.round(firstRecommend * 100.0 / total));
        m.put("collectionCount", (long) collection);
        return m;
    }

    private static int countOccurrences(String text, String keyword) {
        if (keyword.isEmpty()) return 0;
        int idx = 0;
        int n = 0;
        while ((idx = text.indexOf(keyword, idx)) >= 0) {
            n++;
            idx += keyword.length();
        }
        return n;
    }

    /**
     * 按唯一键 (stat_date, stat_type, stat_key) upsert。
     */
    private void upsert(LocalDate date, String brand, String statType, Long value, String engines) {
        LambdaQueryWrapper<DataMonitorStat> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataMonitorStat::getStatDate, date)
                .eq(DataMonitorStat::getStatType, statType)
                .eq(DataMonitorStat::getStatKey, brand);
        DataMonitorStat exist = monitorStatMapper.selectOne(wrapper);
        String remark = "engines=" + engines + ";probes=" + properties.getProbesPerBrand()
                + ";auto=" + java.time.LocalDateTime.now().toLocalDate();
        if (exist != null) {
            exist.setStatValue(value);
            exist.setDimension(DIMENSION);
            exist.setRemark(remark);
            monitorStatMapper.updateById(exist);
        } else {
            DataMonitorStat stat = new DataMonitorStat();
            stat.setStatDate(date);
            stat.setStatType(statType);
            stat.setStatKey(brand);
            stat.setStatValue(value);
            stat.setDimension(DIMENSION);
            stat.setRemark(remark);
            monitorStatMapper.insert(stat);
        }
    }

    /** 单品牌采集结果 */
    private static final class BrandCollectResult {
        boolean written = false;
        int probes = 0;
        int successProbes = 0;
        String engines = "";
        Map<String, Long> metrics = Collections.emptyMap();
    }
}
