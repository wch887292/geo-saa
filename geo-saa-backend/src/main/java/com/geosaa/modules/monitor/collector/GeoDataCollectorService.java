package com.geosaa.modules.monitor.collector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geosaa.common.metrics.BusinessMetrics;
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
    /** 来源引用率：回答中引用了官网链接的比例（GEO核心指标） */
    public static final String TYPE_SOURCE_CITATION = "source_citation_rate";
    /** 引用次数：官网链接在所有回答中出现的总次数 */
    public static final String TYPE_CITATION_COUNT = "citation_count";
    /** 外链引用率：回答中引用了非官网外部链接的比例（竞品/第三方信源） */
    public static final String TYPE_EXTERNAL_CITATION = "external_citation_rate";
    /** 实体准确率：大模型回答中品牌实体信息与基准信息一致的比例 */
    public static final String TYPE_ENTITY_ACCURACY = "entity_accuracy";
    /** 实体冲突数：检测到的实体信息冲突总次数 */
    public static final String TYPE_ENTITY_CONFLICT = "entity_conflict_count";
    /** AAO答案命中率：目标问句下品牌出现在回答中的比例 */
    public static final String TYPE_AAO_HIT_RATE = "aao_answer_hit_rate";
    /** AAO首答率：目标问句下品牌出现在回答开头的比例 */
    public static final String TYPE_AAO_FIRST_RATE = "aao_first_answer_rate";

    private final GeoCollectorProperties properties;
    private final List<AiSearchEngineClient> clients;
    private final DataMonitorStatMapper monitorStatMapper;
    /** 业务指标：采集器成功/失败计数（O3 可观测性） */
    private final BusinessMetrics businessMetrics;

    /**
     * 构建所有可用的采集客户端列表（包括 Spring 注入的 + 多平台动态创建的）。
     */
    private List<AiSearchEngineClient> buildAllClients() {
        List<AiSearchEngineClient> all = new ArrayList<>(clients);
        // 动态创建多平台客户端
        if (properties.getPlatforms() != null) {
            for (GeoCollectorProperties.PlatformConfig cfg : properties.getPlatforms()) {
                if (!cfg.isEnabled() || cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
                    continue;
                }
                GeoPlatformPreset preset = GeoPlatformPreset.getById(cfg.getPlatformId());
                String platformId = cfg.getPlatformId();
                String platformName = (cfg.getPlatformName() != null && !cfg.getPlatformName().isBlank())
                        ? cfg.getPlatformName()
                        : (preset != null ? preset.getPlatformName() : platformId);
                String apiUrl = (cfg.getApiUrl() != null && !cfg.getApiUrl().isBlank())
                        ? cfg.getApiUrl()
                        : (preset != null ? preset.getDefaultApiUrl() : "");
                String model = (cfg.getModel() != null && !cfg.getModel().isBlank())
                        ? cfg.getModel()
                        : (preset != null ? preset.getDefaultModel() : "");
                // 检查是否已存在同 platformId 的客户端（避免重复）
                boolean exists = all.stream().anyMatch(c -> c.engineName().equalsIgnoreCase(platformId));
                if (!exists) {
                    all.add(new PlatformAiSearchClient(platformId, platformName, apiUrl,
                            cfg.getApiKey(), model, properties.getTimeoutSeconds()));
                    log.info("动态创建多平台采集客户端: platform={}, name={}, model={}", platformId, platformName, model);
                }
            }
        }
        return all;
    }

    /**
     * 获取所有启用的平台信息（用于前端展示和采集报告）。
     */
    public List<Map<String, String>> getEnabledPlatforms() {
        List<Map<String, String>> result = new ArrayList<>();
        for (AiSearchEngineClient client : buildAllClients()) {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("platformId", client.engineName());
            if (client instanceof PlatformAiSearchClient) {
                info.put("platformName", ((PlatformAiSearchClient) client).getPlatformName());
            } else {
                info.put("platformName", client.engineName());
            }
            result.add(info);
        }
        return result;
    }

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
        List<AiSearchEngineClient> allClients = buildAllClients();
        if (allClients.isEmpty()) {
            report.put("skipped", "未启用任何采集引擎（请配置 platforms 或 perplexity/openai-compat）");
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
                BrandCollectResult r = collectForBrand(brand, today, allClients);
                if (r.written) {
                    okBrands++;
                    businessMetrics.incrementCollectorSuccess(brand, r.engines.isEmpty() ? "unknown" : r.engines);
                } else {
                    failBrands++;
                    businessMetrics.incrementCollectorFailure(brand, r.engines.isEmpty() ? "unknown" : r.engines);
                }
                report.put(brand, Map.of(
                        "written", r.written,
                        "probes", r.probes,
                        "successProbes", r.successProbes,
                        "engines", r.engines,
                        "metrics", r.metrics));
            } catch (Exception e) {
                failBrands++;
                businessMetrics.incrementCollectorFailure(brand, "exception");
                log.error("GEO 采集失败 brand={}", brand, e);
                report.put(brand, Map.of("written", false, "error", e.getMessage()));
            }
        }
        report.put("summary", Map.of("date", today.toString(), "brands", properties.getBrands().size(),
                "ok", okBrands, "fail", failBrands));
        return report;
    }

    /**
     * 竞品对标采集：遍历配置的竞品列表，采集各竞品的 GEO 指标。
     * 竞品数据写入 data_monitor_stat 表，stat_key 使用 "competitor:{竞品名}" 前缀。
     */
    public Map<String, Object> collectCompetitors() {
        Map<String, Object> report = new LinkedHashMap<>();
        if (!properties.isEnabled()) {
            report.put("skipped", "app.geo.collector.enabled=false");
            return report;
        }
        List<AiSearchEngineClient> allClients = buildAllClients();
        if (allClients.isEmpty()) {
            report.put("skipped", "未启用任何采集引擎");
            return report;
        }
        List<String> competitors = properties.getCompetitors();
        if (competitors == null || competitors.isEmpty()) {
            report.put("skipped", "未配置 competitors 竞品列表");
            return report;
        }

        LocalDate today = LocalDate.now();
        int ok = 0;
        int fail = 0;
        for (String competitor : competitors) {
            try {
                // 复用 collectForBrand，但 stat_key 需要加 competitor: 前缀
                // 这里直接调用采集，然后手动修改 stat_key
                BrandCollectResult r = collectForBrand(competitor, today, allClients);
                if (r.written) {
                    ok++;
                    // 将竞品数据的 stat_key 从品牌名改为 competitor:品牌名
                    renameCompetitorStats(today, competitor);
                } else {
                    fail++;
                }
                report.put(competitor, Map.of(
                        "written", r.written,
                        "probes", r.probes,
                        "successProbes", r.successProbes,
                        "metrics", r.metrics));
            } catch (Exception e) {
                fail++;
                log.error("竞品采集失败 competitor={}", competitor, e);
                report.put(competitor, Map.of("written", false, "error", e.getMessage()));
            }
        }
        report.put("summary", Map.of("date", today.toString(), "competitors", competitors.size(),
                "ok", ok, "fail", fail));
        return report;
    }

    /**
     * 将竞品数据的 stat_key 从品牌名改为 "competitor:品牌名" 前缀，
     * 避免与自有品牌数据混淆。
     */
    private void renameCompetitorStats(LocalDate date, String competitor) {
        try {
            LambdaQueryWrapper<DataMonitorStat> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DataMonitorStat::getStatDate, date)
                    .eq(DataMonitorStat::getStatKey, competitor);
            List<DataMonitorStat> stats = monitorStatMapper.selectList(wrapper);
            for (DataMonitorStat stat : stats) {
                stat.setStatKey("competitor:" + competitor);
                monitorStatMapper.updateById(stat);
            }
        } catch (Exception e) {
            log.warn("竞品数据重命名失败 competitor={}: {}", competitor, e.getMessage());
        }
    }

    /**
     * 获取品牌 vs 竞品的对比数据（从数据库读取最新数据）。
     */
    public List<Map<String, Object>> getCompetitorComparison(String brandName) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 读取自有品牌数据
        Map<String, Object> brandData = getBrandStats(today, brandName);
        brandData.put("isCompetitor", false);
        result.add(brandData);

        // 读取竞品数据
        List<String> competitors = properties.getCompetitors();
        if (competitors != null) {
            for (String competitor : competitors) {
                Map<String, Object> compData = getBrandStats(today, "competitor:" + competitor);
                compData.put("isCompetitor", true);
                compData.put("competitorName", competitor);
                result.add(compData);
            }
        }
        return result;
    }

    /**
     * 从数据库读取指定品牌的最新 GEO 指标。
     */
    private Map<String, Object> getBrandStats(LocalDate date, String statKey) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("brand", statKey);
        try {
            LambdaQueryWrapper<DataMonitorStat> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DataMonitorStat::getStatDate, date)
                    .eq(DataMonitorStat::getStatKey, statKey);
            List<DataMonitorStat> stats = monitorStatMapper.selectList(wrapper);
            for (DataMonitorStat stat : stats) {
                data.put(stat.getStatType(), stat.getStatValue());
            }
        } catch (Exception e) {
            log.warn("读取品牌数据失败 statKey={}: {}", statKey, e.getMessage());
        }
        return data;
    }

    /**
     * 采集单个品牌并 upsert 写库。
     */
    private BrandCollectResult collectForBrand(String brand, LocalDate date, List<AiSearchEngineClient> clientList) {
        List<String> answers = new ArrayList<>();
        List<String> engines = new ArrayList<>();
        int probes = 0;
        for (AiSearchEngineClient client : clientList) {
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

        // 获取品牌官网域名（用于来源引用检测）
        String officialWebsite = properties.getBrandWebsites() != null
                ? properties.getBrandWebsites().get(brand) : null;

        Map<String, Long> metrics = computeMetrics(brand, answers, officialWebsite);
        r.metrics = metrics;

        upsert(date, brand, TYPE_MENTION_RATE, metrics.getOrDefault("mentionRate", 0L), r.engines);
        upsert(date, brand, TYPE_FIRST_RECOMMEND, metrics.getOrDefault("firstRecommendRate", 0L), r.engines);
        upsert(date, brand, TYPE_COLLECTION, metrics.getOrDefault("collectionCount", 0L), r.engines);
        upsert(date, brand, TYPE_SOURCE_CITATION, metrics.getOrDefault("sourceCitationRate", 0L), r.engines);
        upsert(date, brand, TYPE_CITATION_COUNT, metrics.getOrDefault("citationCount", 0L), r.engines);
        upsert(date, brand, TYPE_EXTERNAL_CITATION, metrics.getOrDefault("externalCitationRate", 0L), r.engines);

        // 实体信息正确性检测
        String entityJson = properties.getBrandEntities() != null
                ? properties.getBrandEntities().get(brand) : null;
        if (entityJson != null && !entityJson.isBlank()) {
            GeoEntityValidator.AggregatedEntityResult entityResult =
                    GeoEntityValidator.validateAll(answers, entityJson);
            upsert(date, brand, TYPE_ENTITY_ACCURACY, (long) entityResult.avgAccuracy, r.engines);
            upsert(date, brand, TYPE_ENTITY_CONFLICT, (long) entityResult.totalConflicts, r.engines);
            r.entityAccuracy = entityResult.avgAccuracy;
            r.entityConflicts = entityResult.totalConflicts;
            log.info("实体检测完成 brand={} accuracy={}% conflicts={}",
                    brand, entityResult.avgAccuracy, entityResult.totalConflicts);
        }

        // AAO 答案命中检测：针对目标问句库进行探测
        if (properties.getTargetQueries() != null && !properties.getTargetQueries().isEmpty()) {
            int aaoTotal = 0;
            int aaoHit = 0;
            int aaoFirst = 0;
            for (String queryTemplate : properties.getTargetQueries()) {
                String query = queryTemplate.replace("{brand}", brand);
                for (AiSearchEngineClient client : clientList) {
                    try {
                        String answer = client.probe(query);
                        if (answer != null && !answer.isBlank()) {
                            aaoTotal++;
                            if (answer.contains(brand)) {
                                aaoHit++;
                                String head = answer.substring(0, Math.min(100, answer.length()));
                                if (head.contains(brand)) {
                                    aaoFirst++;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("AAO探测失败 engine={} query={}: {}", client.engineName(), query, e.getMessage());
                    }
                }
            }
            if (aaoTotal > 0) {
                long hitRate = Math.round(aaoHit * 100.0 / aaoTotal);
                long firstRate = Math.round(aaoFirst * 100.0 / aaoTotal);
                upsert(date, brand, TYPE_AAO_HIT_RATE, hitRate, r.engines);
                upsert(date, brand, TYPE_AAO_FIRST_RATE, firstRate, r.engines);
                log.info("AAO检测完成 brand={} hitRate={}% firstRate={}% total={}",
                        brand, hitRate, firstRate, aaoTotal);
            }
        }

        r.written = true;
        log.info("GEO 采集完成 brand={} date={} engines={} probes={}/{} website={} metrics={}",
                brand, date, r.engines, answers.size(), probes, officialWebsite, metrics);
        return r;
    }

    /**
     * 从回答列表聚合六类指标（纯函数，便于单测）。
     *
     * <p>提及率 = 出现品牌名的回答数 / 回答总数 × 100；首推率 = 首段（前 80 字符）
     * 含品牌名的回答占比 × 100；收录强度 = 品牌名在所有回答中的出现总次数。
     * 来源引用率 = 引用官网链接的回答数 / 回答总数 × 100；引用次数 = 官网链接出现总次数；
     * 外链引用率 = 引用非官网外部链接的回答数 / 回答总数 × 100。
     *
     * @param brand 品牌名
     * @param answers 回答列表
     * @param officialWebsite 官网域名（如 example.com），为空则引用相关指标返回0
     */
    public static Map<String, Long> computeMetrics(String brand, List<String> answers, String officialWebsite) {
        if (answers == null || answers.isEmpty()) {
            return Map.of("mentionRate", 0L, "firstRecommendRate", 0L, "collectionCount", 0L,
                    "sourceCitationRate", 0L, "citationCount", 0L, "externalCitationRate", 0L);
        }
        String name = brand == null ? "" : brand;
        String domain = officialWebsite == null ? "" : officialWebsite.toLowerCase().trim();
        int total = answers.size();
        int mention = 0;
        int firstRecommend = 0;
        int collection = 0;
        int sourceCitation = 0;
        int citationCount = 0;
        int externalCitation = 0;

        for (String answer : answers) {
            if (answer == null) continue;
            // 品牌提及检测
            if (answer.contains(name)) {
                mention++;
                collection += countOccurrences(answer, name);
                String head = answer.substring(0, Math.min(80, answer.length()));
                if (head.contains(name)) {
                    firstRecommend++;
                }
            }
            // URL 提取与引用检测
            List<String> urls = extractUrls(answer);
            if (!urls.isEmpty()) {
                boolean hasOfficial = false;
                boolean hasExternal = false;
                for (String url : urls) {
                    String urlLower = url.toLowerCase();
                    if (!domain.isEmpty() && urlLower.contains(domain)) {
                        hasOfficial = true;
                        citationCount++;
                    } else if (urlLower.startsWith("http")) {
                        hasExternal = true;
                    }
                }
                if (hasOfficial) sourceCitation++;
                if (hasExternal) externalCitation++;
            }
        }
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("mentionRate", Math.round(mention * 100.0 / total));
        m.put("firstRecommendRate", Math.round(firstRecommend * 100.0 / total));
        m.put("collectionCount", (long) collection);
        m.put("sourceCitationRate", Math.round(sourceCitation * 100.0 / total));
        m.put("citationCount", (long) citationCount);
        m.put("externalCitationRate", Math.round(externalCitation * 100.0 / total));
        return m;
    }

    /**
     * 从文本中提取所有 URL（http/https 开头）。
     */
    public static List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();
        if (text == null || text.isEmpty()) return urls;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
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
        int entityAccuracy = 0;
        int entityConflicts = 0;
    }
}
