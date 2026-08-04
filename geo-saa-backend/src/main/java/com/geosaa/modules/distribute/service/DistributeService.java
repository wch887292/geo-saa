package com.geosaa.modules.distribute.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.Constant;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.config.RabbitMqConfig;
import com.geosaa.modules.common.TaskProgressService;
import com.geosaa.modules.distribute.dto.DistributeRequest;
import com.geosaa.modules.distribute.entity.DistributeTask;
import com.geosaa.modules.distribute.mapper.DistributeTaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributeService {

    private final DistributeTaskMapper distributeTaskMapper;
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;
    private final TaskProgressService taskProgressService;
    private final ObjectMapper objectMapper;

    // 模拟 151+ 渠道列表
    private static final Map<String, String> CHANNEL_MAP = new LinkedHashMap<>();

    static {
        CHANNEL_MAP.put("weixin", "微信公众号");
        CHANNEL_MAP.put("weibo", "微博");
        CHANNEL_MAP.put("tiktok", "抖音");
        CHANNEL_MAP.put("bilibili", "B站");
        CHANNEL_MAP.put("xiaohongshu", "小红书");
        CHANNEL_MAP.put("zhihu", "知乎");
        CHANNEL_MAP.put("baijia", "百家号");
        CHANNEL_MAP.put("toutiao", "今日头条");
        CHANNEL_MAP.put("douban", "豆瓣");
        CHANNEL_MAP.put("qutoutiao", "趣头条");
        // 模拟更多渠道...
        for (int i = 1; i <= 141; i++) {
            CHANNEL_MAP.put("channel_" + i, "渠道_" + i);
        }
    }

    public Page<DistributeTask> listTasks(int pageNum, int pageSize, String targetPlatform, Integer status) {
        LambdaQueryWrapper<DistributeTask> wrapper = new LambdaQueryWrapper<>();
        if (targetPlatform != null) {
            wrapper.eq(DistributeTask::getTargetPlatform, targetPlatform);
        }
        if (status != null) {
            wrapper.eq(DistributeTask::getStatus, status);
        }
        wrapper.orderByDesc(DistributeTask::getCreateTime);
        return distributeTaskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public DistributeTask getTaskById(Long id) {
        return distributeTaskMapper.selectById(id);
    }

    public DistributeTask createTask(DistributeRequest request, Long userId) {
        // 校验渠道是否存在
        if (!CHANNEL_MAP.containsKey(request.getTargetPlatform())) {
            throw new BusinessException("不支持的渠道: " + request.getTargetPlatform());
        }

        DistributeTask task = new DistributeTask();
        task.setTaskName(request.getTaskName());
        task.setContentId(request.getContentId());
        task.setTargetPlatform(request.getTargetPlatform());
        task.setTargetAccount(request.getTargetAccount());
        task.setDistributeConfig(request.getDistributeConfig());
        task.setScheduledTime(request.getScheduledTime());
        task.setStatus(Constant.TASK_STATUS_PENDING);
        task.setCreatedBy(userId);
        distributeTaskMapper.insert(task);

        // 推送到 RabbitMQ 队列
        Map<String, Object> message = new HashMap<>();
        message.put("taskId", task.getId());
        message.put("contentId", request.getContentId());
        message.put("platform", request.getTargetPlatform());
        message.put("account", request.getTargetAccount());
        message.put("config", request.getDistributeConfig());
        message.put("scheduledTime", request.getScheduledTime());
        message.put("userId", userId);

        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE_DISTRIBUTE,
                    RabbitMqConfig.ROUTING_KEY_DISTRIBUTE_TASK,
                    objectMapper.writeValueAsString(message)
                );
                log.info("分发任务已推送到队列: taskId={}, platform={}", task.getId(), request.getTargetPlatform());
            } catch (JsonProcessingException e) {
                log.error("序列化分发消息失败", e);
            }
        } else {
            log.warn("RabbitMQ 未启用，分发任务已跳过队列，直接处理");
        }

        // 初始化进度追踪
        taskProgressService.initProgress("distribute:" + task.getId(), 3);

        return task;
    }

    public void cancelTask(Long id) {
        DistributeTask task = distributeTaskMapper.selectById(id);
        if (task != null) {
            task.setStatus(3);
            distributeTaskMapper.updateById(task);
            taskProgressService.failProgress("distribute:" + id, "任务已取消");
        }
    }

    public void deleteTask(Long id) {
        distributeTaskMapper.deleteById(id);
        taskProgressService.deleteProgress("distribute:" + id);
    }

    /**
     * 获取渠道列表（模拟 151+ 渠道）
     */
    public Map<String, String> getChannelList() {
        return CHANNEL_MAP;
    }

    /**
     * 获取渠道数量
     */
    public int getChannelCount() {
        return CHANNEL_MAP.size();
    }

    /**
     * 获取任务进度
     */
    public Object getTaskProgress(Long taskId) {
        return taskProgressService.getProgress("distribute:" + taskId);
    }

    /**
     * 处理分发结果回调
     */
    public void handleCallback(Long taskId, boolean success, String resultInfo) {
        DistributeTask task = distributeTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("回调任务不存在: taskId={}", taskId);
            return;
        }
        task.setResultInfo(resultInfo);
        if (success) {
            task.setStatus(Constant.TASK_STATUS_COMPLETED);
            task.setPublishTime(LocalDateTime.now());
            taskProgressService.completeProgress("distribute:" + taskId, resultInfo);
            log.info("分发任务完成: taskId={}, result={}", taskId, resultInfo);
        } else {
            task.setStatus(Constant.TASK_STATUS_FAILED);
            taskProgressService.failProgress("distribute:" + taskId, "分发失败: " + resultInfo);
            log.warn("分发任务失败: taskId={}, result={}", taskId, resultInfo);
        }
        distributeTaskMapper.updateById(task);
    }

    /**
     * 获取分发统计数据
     */
    public Map<String, Object> getDistributeStats() {
        Map<String, Object> stats = new HashMap<>();
        // 按状态统计
        for (int status : Arrays.asList(0, 1, 2, 3)) {
            LambdaQueryWrapper<DistributeTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DistributeTask::getStatus, status);
            long count = distributeTaskMapper.selectCount(wrapper);
            stats.put("status_" + status, count);
        }
        // 按平台统计
        List<Map<String, Object>> platformStats = CHANNEL_MAP.keySet().stream().limit(10).map(platform -> {
            Map<String, Object> item = new HashMap<>();
            item.put("platform", platform);
            LambdaQueryWrapper<DistributeTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DistributeTask::getTargetPlatform, platform);
            item.put("count", distributeTaskMapper.selectCount(wrapper));
            return item;
        }).collect(Collectors.toList());
        stats.put("byPlatform", platformStats);
        return stats;
    }
}