package com.geosaa.modules.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 通用任务进度追踪服务，使用 Redis 存储进度
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProgressService {

    private static final String PROGRESS_KEY_PREFIX = "geo:task:progress:";
    private static final String RESULT_KEY_PREFIX = "geo:task:result:";
    private static final long TTL_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 初始化任务进度
     */
    public void initProgress(String taskId, int totalSteps) {
        String key = PROGRESS_KEY_PREFIX + taskId;
        redisTemplate.opsForHash().put(key, "current", 0);
        redisTemplate.opsForHash().put(key, "total", totalSteps);
        redisTemplate.opsForHash().put(key, "percentage", 0);
        redisTemplate.opsForHash().put(key, "status", "processing");
        redisTemplate.opsForHash().put(key, "message", "任务初始化");
        redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        log.info("任务进度初始化: taskId={}, totalSteps={}", taskId, totalSteps);
    }

    /**
     * 更新任务进度
     */
    public void updateProgress(String taskId, int currentStep, String message) {
        String key = PROGRESS_KEY_PREFIX + taskId;
        Integer total = (Integer) redisTemplate.opsForHash().get(key, "total");
        if (total == null) {
            log.warn("任务进度不存在: taskId={}", taskId);
            return;
        }
        int percentage = Math.min(100, currentStep * 100 / total);
        redisTemplate.opsForHash().put(key, "current", currentStep);
        redisTemplate.opsForHash().put(key, "percentage", percentage);
        redisTemplate.opsForHash().put(key, "message", message);
        log.info("任务进度更新: taskId={}, progress={}%, message={}", taskId, percentage, message);
    }

    /**
     * 完成任务
     */
    public void completeProgress(String taskId, String result) {
        String key = PROGRESS_KEY_PREFIX + taskId;
        redisTemplate.opsForHash().put(key, "current", 100);
        redisTemplate.opsForHash().put(key, "total", 100);
        redisTemplate.opsForHash().put(key, "percentage", 100);
        redisTemplate.opsForHash().put(key, "status", "completed");
        redisTemplate.opsForHash().put(key, "message", "任务完成");
        // 保存结果
        redisTemplate.opsForValue().set(RESULT_KEY_PREFIX + taskId, result, TTL_HOURS, TimeUnit.HOURS);
        log.info("任务完成: taskId={}", taskId);
    }

    /**
     * 任务失败
     */
    public void failProgress(String taskId, String errorMessage) {
        String key = PROGRESS_KEY_PREFIX + taskId;
        redisTemplate.opsForHash().put(key, "status", "failed");
        redisTemplate.opsForHash().put(key, "message", errorMessage);
        log.warn("任务失败: taskId={}, error={}", taskId, errorMessage);
    }

    /**
     * 获取任务进度
     */
    public TaskProgress getProgress(String taskId) {
        String key = PROGRESS_KEY_PREFIX + taskId;
        Integer current = (Integer) redisTemplate.opsForHash().get(key, "current");
        if (current == null) {
            return null;
        }
        TaskProgress progress = new TaskProgress();
        progress.setTaskId(taskId);
        progress.setCurrent(current);
        progress.setTotal((Integer) redisTemplate.opsForHash().get(key, "total"));
        progress.setPercentage((Integer) redisTemplate.opsForHash().get(key, "percentage"));
        progress.setStatus((String) redisTemplate.opsForHash().get(key, "status"));
        progress.setMessage((String) redisTemplate.opsForHash().get(key, "message"));
        // 获取结果
        String result = (String) redisTemplate.opsForValue().get(RESULT_KEY_PREFIX + taskId);
        progress.setResult(result);
        return progress;
    }

    /**
     * 删除任务进度
     */
    public void deleteProgress(String taskId) {
        redisTemplate.delete(PROGRESS_KEY_PREFIX + taskId);
        redisTemplate.delete(RESULT_KEY_PREFIX + taskId);
        log.info("任务进度已删除: taskId={}", taskId);
    }
}