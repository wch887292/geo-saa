package com.geosaa.modules.monitor.service;

import com.geosaa.modules.monitor.entity.DataMonitorStat;
import com.geosaa.modules.monitor.mapper.DataMonitorStatMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * MonitorService 核心指标单测（O5）。
 *
 * <p>覆盖：评分公式、simulation 闸门（模拟出数打标 vs 生产返回真实空值）、
 * 装箱数值安全拆箱（历史 ClassCastException 回归）。
 */
@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private DataMonitorStatMapper mapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;

    private MonitorService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new MonitorService(mapper, redisTemplate);
    }

    // ---------- 场景一：空库 + 模拟模式开 → 出数但打 simulated 标 ----------

    @Test
    void emptyDbSimulationOnReturnsSimulatedFlag() {
        ReflectionTestUtils.setField(service, "simulationEnabled", true);
        when(mapper.selectOne(any())).thenReturn(null);

        Map<String, Object> metrics = service.getCoreMetrics("测试品牌");

        assertEquals(false, metrics.get("hasData"));
        assertEquals(true, metrics.get("simulated"));
        Number rate = (Number) metrics.get("mentionRate");
        assertNotNull(rate);
        assertTrue(rate.longValue() >= 0 && rate.longValue() <= 100);
        Number score = (Number) metrics.get("score");
        assertTrue(score.intValue() >= 0 && score.intValue() <= 100);
    }

    // ---------- 场景二：空库 + 模拟模式关 → 真实空值，绝不用随机数 ----------

    @Test
    void emptyDbSimulationOffReturnsZeroAndNoRandom() {
        ReflectionTestUtils.setField(service, "simulationEnabled", false);
        when(mapper.selectOne(any())).thenReturn(null);

        Map<String, Object> metrics = service.getCoreMetrics("测试品牌");

        assertEquals(false, metrics.get("hasData"));
        assertEquals(false, metrics.get("simulated"));
        assertEquals(0L, ((Number) metrics.get("mentionRate")).longValue());
        assertEquals(0L, ((Number) metrics.get("firstRecommendRate")).longValue());
        assertEquals(0L, ((Number) metrics.get("collectionCount")).longValue());
        assertEquals(0, ((Number) metrics.get("score")).intValue());
    }

    // ---------- 场景三：有真实数据 → hasData=true，评分按公式 ----------

    @Test
    void realDataComputesScoreByFormula() {
        ReflectionTestUtils.setField(service, "simulationEnabled", false);

        DataMonitorStat mention = stat("mention_rate", 50L);
        DataMonitorStat recommend = stat("first_recommend_rate", 40L);
        DataMonitorStat collect = stat("collection_count", 100L);
        // selectOne 依次被调用 3 次：mention → recommend → collect
        when(mapper.selectOne(any())).thenReturn(mention, recommend, collect);

        Map<String, Object> metrics = service.getCoreMetrics("测试品牌");

        assertEquals(true, metrics.get("hasData"));
        assertEquals(false, metrics.get("simulated"));
        // score = 50*0.4 + 40*0.35 + min(100, 100/10)*0.25 = 20 + 14 + 2.5 = 36.5 → 36
        assertEquals(36, ((Number) metrics.get("score")).intValue());
    }

    // ---------- 回归：Number 安全拆箱（Long/Integer 兼容，不再 ClassCastException） ----------

    @Test
    void scoreComputationDoesNotThrowOnBoxedLongs() {
        ReflectionTestUtils.setField(service, "simulationEnabled", false);

        DataMonitorStat mention = stat("mention_rate", 80L);
        DataMonitorStat recommend = stat("first_recommend_rate", 60L);
        DataMonitorStat collect = stat("collection_count", 500L);
        when(mapper.selectOne(any())).thenReturn(mention, recommend, collect);

        Map<String, Object> metrics = service.getCoreMetrics("品牌X");
        // score = 80*0.4 + 60*0.35 + min(100,50)*0.25 = 32 + 21 + 12.5 = 65.5 → 66（四舍五入取整为 int 截断 → 65）
        // (int) 65.5 = 65
        assertEquals(65, ((Number) metrics.get("score")).intValue());
    }

    private DataMonitorStat stat(String type, Long value) {
        DataMonitorStat s = new DataMonitorStat();
        s.setStatType(type);
        s.setStatValue(value);
        return s;
    }
}
