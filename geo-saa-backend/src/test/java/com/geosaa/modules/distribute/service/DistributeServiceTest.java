package com.geosaa.modules.distribute.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.Constant;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.common.TaskProgressService;
import com.geosaa.modules.distribute.dto.DistributeRequest;
import com.geosaa.modules.distribute.entity.DistributeTask;
import com.geosaa.modules.distribute.mapper.DistributeTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DistributeService 单测（O5 测试补全）。
 *
 * <p>覆盖：任务 CRUD、createTask 渠道校验+MQ+进度、cancelTask 状态语义、
 * handleCallback 成功/失败/不存在、151 渠道、getDistributeStats 统计聚合。
 */
@ExtendWith(MockitoExtension.class)
class DistributeServiceTest {

    @Mock private DistributeTaskMapper distributeTaskMapper;
    @Mock private TaskProgressService taskProgressService;
    @Mock private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DistributeService distributeService;

    @BeforeEach
    void setUp() {
        distributeService = new DistributeService(distributeTaskMapper, taskProgressService, objectMapper);
    }

    private DistributeRequest request(String platform) {
        DistributeRequest r = new DistributeRequest();
        r.setTaskName("测试分发任务");
        r.setContentId(100L);
        r.setTargetPlatform(platform);
        r.setTargetAccount("test_account");
        r.setDistributeConfig("{\"key\":\"value\"}");
        // scheduledTime 留空：测试用 plain ObjectMapper 无 JSR310 模块，避免 LocalDateTime 序列化失败
        return r;
    }

    private DistributeTask task(Long id, Integer status, String platform) {
        DistributeTask t = new DistributeTask();
        t.setId(id);
        t.setTaskName("任务" + id);
        t.setStatus(status);
        t.setTargetPlatform(platform);
        return t;
    }

    // ---------- list / get ----------

    @Test
    void listTasksReturnsPage() {
        Page<DistributeTask> page = new Page<>(1, 10);
        when(distributeTaskMapper.selectPage(any(Page.class), any())).thenReturn(page);
        assertNotNull(distributeService.listTasks(1, 10, "weixin", 0));
    }

    @Test
    void getTaskByIdReturnsTask() {
        DistributeTask t = task(1L, 0, "weixin");
        when(distributeTaskMapper.selectById(1L)).thenReturn(t);
        assertEquals(t, distributeService.getTaskById(1L));
    }

    // ---------- createTask ----------

    @Test
    void createTaskSuccessWithValidChannel() {
        ReflectionTestUtils.setField(distributeService, "rabbitTemplate", rabbitTemplate);
        doReturn(1).when(distributeTaskMapper).insert(any(DistributeTask.class));

        DistributeTask result = distributeService.createTask(request("weixin"), 1L);

        assertEquals(Constant.TASK_STATUS_PENDING, result.getStatus());
        assertEquals("weixin", result.getTargetPlatform());
        assertEquals(1L, result.getCreatedBy());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());
        verify(taskProgressService).initProgress(eq("distribute:" + result.getId()), eq(3));
    }

    @Test
    void createTaskInvalidChannelThrowsBusinessException() {
        assertThrows(BusinessException.class,
                () -> distributeService.createTask(request("nonexistent_channel"), 1L));
        verify(distributeTaskMapper, never()).insert(any(DistributeTask.class));
    }

    @Test
    void createTaskWithoutRabbitMqDoesNotThrow() {
        // rabbitTemplate 保持 null（dev 模式）
        doReturn(1).when(distributeTaskMapper).insert(any(DistributeTask.class));

        DistributeTask result = distributeService.createTask(request("weibo"), 1L);
        assertNotNull(result);
        assertEquals(Constant.TASK_STATUS_PENDING, result.getStatus());
        verifyNoInteractions(rabbitTemplate);
        verify(taskProgressService).initProgress(anyString(), eq(3));
    }

    // ---------- cancelTask ----------

    @Test
    void cancelTaskExistingSetsFailedStatus() {
        DistributeTask t = task(1L, Constant.TASK_STATUS_PENDING, "weixin");
        when(distributeTaskMapper.selectById(1L)).thenReturn(t);
        doReturn(1).when(distributeTaskMapper).updateById(t);

        distributeService.cancelTask(1L);

        assertEquals(Constant.TASK_STATUS_FAILED, t.getStatus());
        verify(distributeTaskMapper).updateById(t);
        verify(taskProgressService).failProgress(eq("distribute:1"), contains("已取消"));
    }

    @Test
    void cancelTaskNotFoundDoesNothing() {
        when(distributeTaskMapper.selectById(999L)).thenReturn(null);
        distributeService.cancelTask(999L);
        verify(distributeTaskMapper, never()).updateById(any(DistributeTask.class));
        verifyNoInteractions(taskProgressService);
    }

    // ---------- deleteTask ----------

    @Test
    void deleteTaskDeletesRecordAndProgress() {
        distributeService.deleteTask(1L);
        verify(distributeTaskMapper).deleteById(1L);
        verify(taskProgressService).deleteProgress("distribute:1");
    }

    // ---------- 渠道 ----------

    @Test
    void getChannelListContains151Channels() {
        Map<String, String> channels = distributeService.getChannelList();
        // 10 个具名渠道 + 141 个模拟渠道 = 151
        assertEquals(151, channels.size());
        assertTrue(channels.containsKey("weixin"));
        assertTrue(channels.containsKey("channel_141"));
    }

    @Test
    void getChannelCountReturns151() {
        assertEquals(151, distributeService.getChannelCount());
    }

    // ---------- getTaskProgress ----------

    @Test
    void getTaskProgressDelegatesToService() {
        when(taskProgressService.getProgress("distribute:1")).thenReturn(null);
        assertNull(distributeService.getTaskProgress(1L));
    }

    // ---------- handleCallback ----------

    @Test
    void handleCallbackSuccessSetsCompletedAndPublishTime() {
        DistributeTask t = task(1L, Constant.TASK_STATUS_PROCESSING, "weixin");
        when(distributeTaskMapper.selectById(1L)).thenReturn(t);
        doReturn(1).when(distributeTaskMapper).updateById(t);

        distributeService.handleCallback(1L, true, "发布成功");

        assertEquals(Constant.TASK_STATUS_COMPLETED, t.getStatus());
        assertEquals("发布成功", t.getResultInfo());
        assertNotNull(t.getPublishTime());
        verify(taskProgressService).completeProgress(eq("distribute:1"), eq("发布成功"));
    }

    @Test
    void handleCallbackFailureSetsFailedStatus() {
        DistributeTask t = task(1L, Constant.TASK_STATUS_PROCESSING, "weixin");
        when(distributeTaskMapper.selectById(1L)).thenReturn(t);
        doReturn(1).when(distributeTaskMapper).updateById(t);

        distributeService.handleCallback(1L, false, "平台拒绝");

        assertEquals(Constant.TASK_STATUS_FAILED, t.getStatus());
        assertEquals("平台拒绝", t.getResultInfo());
        assertNull(t.getPublishTime());
        verify(taskProgressService).failProgress(eq("distribute:1"), contains("平台拒绝"));
    }

    @Test
    void handleCallbackNotFoundReturnsSilently() {
        when(distributeTaskMapper.selectById(999L)).thenReturn(null);
        distributeService.handleCallback(999L, true, "ok");
        verify(distributeTaskMapper, never()).updateById(any(DistributeTask.class));
        verifyNoInteractions(taskProgressService);
    }

    // ---------- getDistributeStats ----------

    @Test
    void getDistributeStatsReturnsStatusAndPlatformCounts() {
        when(distributeTaskMapper.selectCount(any())).thenReturn(5L);

        Map<String, Object> stats = distributeService.getDistributeStats();

        assertNotNull(stats);
        // 4 个状态
        assertEquals(5L, stats.get("status_0"));
        assertEquals(5L, stats.get("status_1"));
        assertEquals(5L, stats.get("status_2"));
        assertEquals(5L, stats.get("status_3"));
        // 平台统计取前 10 个渠道
        List<Map<String, Object>> byPlatform = (List<Map<String, Object>>) stats.get("byPlatform");
        assertEquals(10, byPlatform.size());
    }
}
