package com.geosaa.modules.distribute.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosaa.common.Constant;
import com.geosaa.config.RabbitMqConfig;
import com.geosaa.modules.common.TaskProgressService;
import com.geosaa.modules.distribute.entity.DistributeTask;
import com.geosaa.modules.distribute.mapper.DistributeTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 分发任务消息监听器 - 监听 RabbitMQ 队列，异步处理分发任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
public class DistributeMessageListener {

    private final DistributeTaskMapper distributeTaskMapper;
    private final TaskProgressService taskProgressService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_DISTRIBUTE_TASK)
    public void handleDistributeTask(String message) {
        log.info("接收到分发任务消息: {}", message);
        try {
            Map<String, Object> msgMap = objectMapper.readValue(message, Map.class);
            Long taskId = Long.valueOf(msgMap.get("taskId").toString());
            String platform = (String) msgMap.get("platform");
            String account = (String) msgMap.get("account");

            DistributeTask task = distributeTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("分发任务不存在: taskId={}", taskId);
                return;
            }

            // 更新状态为处理中
            task.setStatus(Constant.TASK_STATUS_PROCESSING);
            distributeTaskMapper.updateById(task);
            taskProgressService.updateProgress("distribute:" + taskId, 1, "正在分发到" + platform);

            // 模拟分发处理
            Thread.sleep(1000); // 模拟分发耗时

            // 步骤2: 内容发布
            taskProgressService.updateProgress("distribute:" + taskId, 2, "内容发布中...");
            Thread.sleep(1000);

            // 分发完成
            task.setStatus(Constant.TASK_STATUS_COMPLETED); // 已完成
            task.setPublishTime(LocalDateTime.now());
            task.setResultInfo("分发成功: 平台=" + platform + ", 账号=" + account);
            distributeTaskMapper.updateById(task);

            taskProgressService.completeProgress("distribute:" + taskId, task.getResultInfo());
            log.info("分发任务完成: taskId={}, platform={}", taskId, platform);

        } catch (Exception e) {
            log.error("分发任务处理失败", e);
            if (message != null) {
                try {
                    Map<String, Object> msgMap = objectMapper.readValue(message, Map.class);
                    Long taskId = Long.valueOf(msgMap.get("taskId").toString());
                    DistributeTask task = distributeTaskMapper.selectById(taskId);
                    if (task != null) {
                        task.setStatus(Constant.TASK_STATUS_FAILED); // 失败
                        task.setResultInfo("分发失败: " + e.getMessage());
                        distributeTaskMapper.updateById(task);
                    }
                    taskProgressService.failProgress("distribute:" + taskId, "分发失败: " + e.getMessage());
                } catch (Exception ex) {
                    log.error("更新分发任务失败状态出错", ex);
                }
            }
        }
    }
}