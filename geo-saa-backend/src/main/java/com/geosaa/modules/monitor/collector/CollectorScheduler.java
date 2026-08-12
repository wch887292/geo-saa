package com.geosaa.modules.monitor.collector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * GEO 真实数据采集定时任务。
 *
 * <p>仅在 {@code app.geo.collector.enabled=true} 时注册生效（默认关闭），
 * 按配置的 cron（默认每天 01:30）触发全量采集。采集失败不会影响业务接口，
 * 也不会写入任何模拟数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.geo.collector", name = "enabled", havingValue = "true")
public class CollectorScheduler {

    private final GeoDataCollectorService collectorService;

    @Scheduled(cron = "${app.geo.collector.cron:0 30 1 * * *}")
    public void scheduledCollect() {
        log.info("GEO 定时采集开始");
        try {
            var report = collectorService.collectAll();
            log.info("GEO 定时采集结束: {}", report.get("summary"));
        } catch (Exception e) {
            log.error("GEO 定时采集异常", e);
        }
    }
}
