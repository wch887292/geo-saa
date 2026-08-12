package com.geosaa.modules.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.modules.monitor.entity.DataMonitorStat;
import com.geosaa.modules.monitor.mapper.DataMonitorStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private static final String CACHE_KEY_PREFIX = "geo:monitor:stats:";
    private static final long CACHE_TTL_MINUTES = 5;

    private final DataMonitorStatMapper monitorStatMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${ai.simulation.enabled:true}")
    private boolean simulationEnabled;

    public Page<DataMonitorStat> listStats(int pageNum, int pageSize, String statType, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<DataMonitorStat> wrapper = new LambdaQueryWrapper<>();
        if (statType != null) {
            wrapper.eq(DataMonitorStat::getStatType, statType);
        }
        if (startDate != null) {
            wrapper.ge(DataMonitorStat::getStatDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(DataMonitorStat::getStatDate, endDate);
        }
        wrapper.orderByDesc(DataMonitorStat::getStatDate);
        return monitorStatMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public List<DataMonitorStat> getStatsByDate(LocalDate date, String statType) {
        LambdaQueryWrapper<DataMonitorStat> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataMonitorStat::getStatDate, date);
        if (statType != null) {
            wrapper.eq(DataMonitorStat::getStatType, statType);
        }
        return monitorStatMapper.selectList(wrapper);
    }

    public void addStat(DataMonitorStat stat) {
        monitorStatMapper.insert(stat);
        // 清除缓存
        String cacheKey = CACHE_KEY_PREFIX + stat.getStatType() + ":" + stat.getStatDate();
        redisTemplate.delete(cacheKey);
    }

    /**
     * 核心指标统计（AI 提及率、首推占比、收录量）- 带 Redis 缓存
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCoreMetrics(String brandName) {
        String cacheKey = CACHE_KEY_PREFIX + "core:" + brandName;
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("命中缓存: coreMetrics, brandName={}", brandName);
            return cached;
        }

        Map<String, Object> metrics = new HashMap<>();

        // 从数据库统计 AI 提及率
        LambdaQueryWrapper<DataMonitorStat> mentionWrapper = new LambdaQueryWrapper<>();
        mentionWrapper.eq(DataMonitorStat::getStatType, "mention_rate");
        if (brandName != null) {
            mentionWrapper.eq(DataMonitorStat::getStatKey, brandName);
        }
        mentionWrapper.orderByDesc(DataMonitorStat::getStatDate).last("LIMIT 1");
        DataMonitorStat mentionStat = monitorStatMapper.selectOne(mentionWrapper);
        metrics.put("mentionRate", mentionStat != null ? mentionStat.getStatValue()
                : (simulationEnabled ? Math.round(Math.random() * 100) : 0L));

        // 首推占比
        LambdaQueryWrapper<DataMonitorStat> recommendWrapper = new LambdaQueryWrapper<>();
        recommendWrapper.eq(DataMonitorStat::getStatType, "first_recommend_rate");
        if (brandName != null) {
            recommendWrapper.eq(DataMonitorStat::getStatKey, brandName);
        }
        recommendWrapper.orderByDesc(DataMonitorStat::getStatDate).last("LIMIT 1");
        DataMonitorStat recommendStat = monitorStatMapper.selectOne(recommendWrapper);
        metrics.put("firstRecommendRate", recommendStat != null ? recommendStat.getStatValue()
                : (simulationEnabled ? Math.round(Math.random() * 100) : 0L));

        // 收录量
        LambdaQueryWrapper<DataMonitorStat> collectWrapper = new LambdaQueryWrapper<>();
        collectWrapper.eq(DataMonitorStat::getStatType, "collection_count");
        if (brandName != null) {
            collectWrapper.eq(DataMonitorStat::getStatKey, brandName);
        }
        collectWrapper.orderByDesc(DataMonitorStat::getStatDate).last("LIMIT 1");
        DataMonitorStat collectStat = monitorStatMapper.selectOne(collectWrapper);
        metrics.put("collectionCount", collectStat != null ? collectStat.getStatValue()
                : (simulationEnabled ? new Random().nextInt(1000) : 0L));

        // 综合评分：metrics 中存放的是装箱数值（Long/Integer），直接 (double)/(long) 强转
        // 会在运行时触发 ClassCastException（Long/Integer 不能强转为 Double/Long）。
        // 统一按 Number 安全拆箱，兼容 Long 与 Integer 两种类型。
        long mentionRate = ((Number) metrics.get("mentionRate")).longValue();
        long firstRecommendRate = ((Number) metrics.get("firstRecommendRate")).longValue();
        long collectionCount = ((Number) metrics.get("collectionCount")).longValue();
        int score = (int) (mentionRate * 0.4
                + firstRecommendRate * 0.35
                + Math.min(100, collectionCount / 10) * 0.25);
        metrics.put("score", Math.min(100, score));

        boolean hasRealData = mentionStat != null && recommendStat != null && collectStat != null;
        metrics.put("simulated", !hasRealData && simulationEnabled);
        metrics.put("hasData", hasRealData);
        if (!hasRealData && !simulationEnabled) {
            log.warn("监测数据缺失且模拟模式已关闭，返回空数据 brandName={}", brandName);
        }

        // 缓存 5 分钟
        redisTemplate.opsForValue().set(cacheKey, metrics, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return metrics;
    }

    /**
     * 趋势数据生成（按日/周/月聚合）- 带 Redis 缓存
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTrendData(String statType, String period, int days) {
        String cacheKey = CACHE_KEY_PREFIX + "trend:" + statType + ":" + period + ":" + days;
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("命中缓存: trendData, statType={}, period={}", statType, period);
            return cached;
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        LambdaQueryWrapper<DataMonitorStat> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataMonitorStat::getStatType, statType != null ? statType : "mention_rate");
        wrapper.ge(DataMonitorStat::getStatDate, startDate);
        wrapper.le(DataMonitorStat::getStatDate, endDate);
        wrapper.orderByAsc(DataMonitorStat::getStatDate);

        List<DataMonitorStat> stats = monitorStatMapper.selectList(wrapper);

        Map<String, Object> trend = new HashMap<>();
        trend.put("period", period);
        trend.put("startDate", startDate.toString());
        trend.put("endDate", endDate.toString());

        if ("week".equals(period)) {
            // 按周聚合
            Map<Integer, List<DataMonitorStat>> weekGroups = stats.stream()
                    .collect(Collectors.groupingBy(s -> s.getStatDate().getDayOfYear() / 7));
            List<Map<String, Object>> weekData = new ArrayList<>();
            weekGroups.forEach((week, weekStats) -> {
                Map<String, Object> point = new HashMap<>();
                point.put("label", "第" + (week + 1) + "周");
                point.put("value", weekStats.stream().mapToLong(DataMonitorStat::getStatValue).average().orElse(0));
                weekData.add(point);
            });
            trend.put("data", weekData);
        } else if ("month".equals(period)) {
            // 按月聚合
            Map<Integer, List<DataMonitorStat>> monthGroups = stats.stream()
                    .collect(Collectors.groupingBy(s -> s.getStatDate().getMonthValue()));
            List<Map<String, Object>> monthData = new ArrayList<>();
            monthGroups.forEach((month, monthStats) -> {
                Map<String, Object> point = new HashMap<>();
                point.put("label", month + "月");
                point.put("value", monthStats.stream().mapToLong(DataMonitorStat::getStatValue).average().orElse(0));
                monthData.add(point);
            });
            trend.put("data", monthData);
        } else {
            // 按日聚合
            List<Map<String, Object>> dayData = stats.stream().map(s -> {
                Map<String, Object> point = new HashMap<>();
                point.put("label", s.getStatDate().format(DateTimeFormatter.ofPattern("MM-dd")));
                point.put("value", s.getStatValue());
                return point;
            }).collect(Collectors.toList());
            trend.put("data", dayData);
        }

        // 缓存 5 分钟
        redisTemplate.opsForValue().set(cacheKey, trend, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return trend;
    }

    /**
     * 竞品对比数据 - 带 Redis 缓存
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCompetitorComparison(String brandName) {
        String cacheKey = CACHE_KEY_PREFIX + "competitor:" + brandName;
        List<Map<String, Object>> cached = (List<Map<String, Object>>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("命中缓存: competitorComparison, brandName={}", brandName);
            return cached;
        }

        List<Map<String, Object>> competitors = new ArrayList<>();
        String[] competitorsList = {"竞品A", "竞品B", "竞品C", "竞品D"};

        // 本品牌数据
        Map<String, Object> self = new HashMap<>();
        self.put("name", brandName != null ? brandName : "本品牌");
        self.put("mentionRate", getAvgStatValue("mention_rate", brandName));
        self.put("firstRecommendRate", getAvgStatValue("first_recommend_rate", brandName));
        self.put("collectionCount", getAvgStatValue("collection_count", brandName));
        self.put("trend", "up");
        competitors.add(self);

        // 竞品数据
        for (String competitor : competitorsList) {
            Map<String, Object> comp = new HashMap<>();
            comp.put("name", competitor);
            comp.put("mentionRate", simulationEnabled ? Math.round(Math.random() * 100) : 0L);
            comp.put("firstRecommendRate", simulationEnabled ? Math.round(Math.random() * 100) : 0L);
            comp.put("collectionCount", simulationEnabled ? new Random().nextInt(1000) : 0L);
            comp.put("trend", simulationEnabled ? (Math.random() > 0.5 ? "up" : "down") : "flat");
            comp.put("simulated", simulationEnabled);
            competitors.add(comp);
        }

        // 缓存 5 分钟
        redisTemplate.opsForValue().set(cacheKey, competitors, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return competitors;
    }

    /**
     * 获取指定统计类型的平均值
     */
    private long getAvgStatValue(String statType, String statKey) {
        LambdaQueryWrapper<DataMonitorStat> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataMonitorStat::getStatType, statType);
        if (statKey != null) {
            wrapper.eq(DataMonitorStat::getStatKey, statKey);
        }
        wrapper.orderByDesc(DataMonitorStat::getStatDate).last("LIMIT 7");
        List<DataMonitorStat> stats = monitorStatMapper.selectList(wrapper);
        if (stats.isEmpty()) {
            return simulationEnabled ? Math.round(Math.random() * 100) : 0L;
        }
        return Math.round(stats.stream().mapToLong(DataMonitorStat::getStatValue).average().orElse(0));
    }
}