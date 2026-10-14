package com.geosaa.modules.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosaa.ai.AiAdapterFactory;
import com.geosaa.modules.content.dto.ContentGenerateRequest;
import com.geosaa.modules.content.entity.AiArticleContent;
import com.geosaa.modules.content.geo.GeoContentValidator;
import com.geosaa.modules.content.mapper.AiArticleContentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量生成内容在 RabbitMQ 不可用时的降级行为回归测试。
 *
 * <p>复现的真实缺陷：Spring Boot 4.1 把 AMQP 自动配置类从
 * {@code org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration}
 * 迁到 {@code org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration}，
 * 原 dev 配置里的 exclude 因类名过期而静默失效，{@code RabbitTemplate} 仍被创建。
 * 于是 {@code if (rabbitTemplate != null)} 的降级判断形同虚设，
 * {@code convertAndSend} 抛 {@link AmqpConnectException} 直接冒泡成 HTTP 500；
 * 更糟的是旧实现在捕获失败后仅打印日志跳过，
 * 令内容记录永久停留 PENDING，形成静默数据黑洞。
 *
 * <p>本测试锁定修复后的契约：
 * <ol>
 *   <li>broker 不可达时不抛异常（HTTP 不再是 500）；</li>
 *   <li>必然触发一次本地降级生成，绝不留下 PENDING 孤儿记录。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ContentServiceBatchGenerateTest {

    @Mock
    private AiArticleContentMapper contentMapper;
    @Mock
    private AiAdapterFactory aiAdapterFactory;
    @Mock
    private GeoContentValidator geoContentValidator;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private ObjectProvider<ContentService> selfProvider;
    @Mock
    private ContentService selfProxy;

    private ContentService service;

    private final java.util.concurrent.atomic.AtomicLong idSeq = new java.util.concurrent.atomic.AtomicLong(1000);

    @BeforeEach
    void setUp() {
        service = new ContentService(
                contentMapper, aiAdapterFactory, geoContentValidator,
                new ObjectMapper(), selfProvider);
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbitTemplate);
        lenient().when(selfProvider.getObject()).thenReturn(selfProxy);

        // 模拟 MyBatis-Plus insert 后回填自增主键（真实运行时由数据库生成 ID），
        // 否则 content.getId() 为 null，降级断言无法匹配。
        lenient().doAnswer(inv -> {
            AiArticleContent c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(idSeq.incrementAndGet());
            }
            return 1;
        }).when(contentMapper).insert(any(AiArticleContent.class));
    }

    private static ContentGenerateRequest request(String title) {
        ContentGenerateRequest r = new ContentGenerateRequest();
        r.setTitle(title);
        r.setContentType("ARTICLE");
        r.setBrandName("测试品牌");
        r.setKeywords("工业自动化");
        return r;
    }

    @Test
    @DisplayName("broker 不可达时不再抛异常，改为本地降级生成（回归：原会 500）")
    void batchGenerate_shouldFallBackWhenBrokerUnreachable() {
        // RabbitTemplate Bean 存在，但 broker 连不上 —— 这正是原缺陷的触发条件
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        List<Long> ids = service.batchGenerateArticles(List.of(request("文章A")), 1L);

        // 1) 不抛异常，调用方拿到正常 ID 列表
        assertNotNull(ids);
        assertEquals(1, ids.size());
        // 2) 触发本地降级生成，不留孤儿 PENDING
        verify(selfProxy, times(1)).asyncGenerateArticle(anyLong());
    }

    @Test
    @DisplayName("入队成功时不应触发降级生成")
    void batchGenerate_shouldNotFallBackWhenPublishSucceeds() {
        List<Long> ids = service.batchGenerateArticles(List.of(request("文章B")), 1L);

        assertEquals(1, ids.size());
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), anyString());
        verify(selfProxy, never()).asyncGenerateArticle(anyLong());
    }

    @Test
    @DisplayName("批量多条时每条都独立兜底，不会因单条失败丢弃其余任务")
    void batchGenerate_shouldFallBackForEveryItem() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        List<Long> ids = service.batchGenerateArticles(
                List.of(request("文章C"), request("文章D"), request("文章E")), 1L);

        assertEquals(3, ids.size());
        verify(selfProxy, times(3)).asyncGenerateArticle(anyLong());
    }

    @Test
    @DisplayName("无 RabbitTemplate Bean（配置已排除）时同样走降级")
    void batchGenerate_shouldFallBackWhenTemplateAbsent() {
        ReflectionTestUtils.setField(service, "rabbitTemplate", null);

        List<Long> ids = service.batchGenerateArticles(List.of(request("文章F")), 1L);

        assertEquals(1, ids.size());
        verify(selfProxy, times(1)).asyncGenerateArticle(anyLong());
    }
}