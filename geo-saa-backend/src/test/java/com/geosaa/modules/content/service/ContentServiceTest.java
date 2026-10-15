package com.geosaa.modules.content.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.ai.AiAdapter;
import com.geosaa.ai.AiAdapterFactory;
import com.geosaa.common.Constant;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.content.dto.ContentGenerateRequest;
import com.geosaa.modules.content.entity.AiArticleContent;
import com.geosaa.modules.content.geo.GeoContentValidator;
import com.geosaa.modules.content.geo.GeoValidationResult;
import com.geosaa.modules.content.mapper.AiArticleContentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ContentService 单测（O5 测试补全）。
 *
 * <p>覆盖：createContent 合规拦截/GEO 堆砌拦截/成功落库、checkCompliance 敏感词、
 * 行业模板、list/get/update/delete、generateWithAi 成功/不存在/AI 异常、
 * batchGenerateArticles 批量落库+MQ、asyncGenerateScript 成功/失败、validateGeo 透传。
 */
@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock private AiArticleContentMapper articleContentMapper;
    @Mock private AiAdapterFactory aiAdapterFactory;
    @Mock private AiAdapter aiAdapter;
    @Mock private ObjectProvider<ContentService> selfProvider;
    @Mock private RabbitTemplate rabbitTemplate;

    private final GeoContentValidator geoContentValidator = new GeoContentValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ContentService contentService;

    private static final String GOOD_CONTENT =
            "据《中国AI产业白皮书》显示，2025年中国AI市场规模达 4500 亿元，同比增长 35%（来源：工信部）。" +
            "飞虹智CEO吴赐虹表示：「AI搜索正在改变品牌营销方式。」参考 https://klai.top 查看更多。";

    private static final String STUFFING_CONTENT =
            "AI搜索很重要，AI搜索能帮助企业，企业必须做AI搜索，AI搜索是趋势，" +
            "我们专注于AI搜索，AI搜索营销，AI搜索优化，AI搜索案例，AI搜索报告，" +
            "AI搜索增长，AI搜索策略，AI搜索方案，AI搜索效果，AI搜索团队";

    @BeforeEach
    void setUp() {
        contentService = new ContentService(articleContentMapper, aiAdapterFactory,
                geoContentValidator, objectMapper, selfProvider);
        // 注入敏感词默认词表（@Value 字段）
        ReflectionTestUtils.setField(contentService, "sensitiveWords",
                List.of("违法内容", "赌博", "色情", "暴力", "毒品", "诈骗", "传销", "枪支弹药", "代开发票"));
    }

    private ContentGenerateRequest request(String title, String content, String keywords) {
        ContentGenerateRequest r = new ContentGenerateRequest();
        r.setTitle(title);
        r.setContentType("article");
        r.setBrandName("测试品牌");
        r.setKeywords(keywords);
        r.setSummary("摘要");
        r.setContent(content);
        return r;
    }

    private AiArticleContent article(Long id, String title, Integer status) {
        AiArticleContent c = new AiArticleContent();
        c.setId(id);
        c.setTitle(title);
        c.setContentType("article");
        c.setStatus(status);
        return c;
    }

    // ---------- createContent 成功 ----------

    @Test
    void createContentSuccessInsertsWithPendingStatus() {
        when(articleContentMapper.insert(any(AiArticleContent.class))).thenReturn(1);

        AiArticleContent result = contentService.createContent(
                request("测试标题", GOOD_CONTENT, "AI搜索"), 1L);

        assertEquals("测试标题", result.getTitle());
        assertEquals(Constant.TASK_STATUS_PENDING, result.getStatus());
        assertEquals(1L, result.getCreatedBy());
        verify(articleContentMapper).insert(any(AiArticleContent.class));
    }

    // ---------- createContent 敏感词拦截 ----------

    @Test
    void createContentWithSensitiveWordThrowsBusinessException() {
        ContentGenerateRequest req = request("标题", "这是违法内容的测试", "关键词");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contentService.createContent(req, 1L));
        assertTrue(ex.getMessage().contains("合规检测未通过"));
        // 合规拦截后不应插入
        verify(articleContentMapper, never()).insert(any(AiArticleContent.class));
    }

    // ---------- createContent GEO 堆砌拦截 ----------

    @Test
    void createContentWithKeywordStuffingThrowsBusinessException() {
        ContentGenerateRequest req = request("标题", STUFFING_CONTENT, "AI搜索");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contentService.createContent(req, 1L));
        assertTrue(ex.getMessage().contains("GEO 校验未通过"));
        verify(articleContentMapper, never()).insert(any(AiArticleContent.class));
    }

    // ---------- checkCompliance ----------

    @Test
    void checkComplianceNullReturnsNull() {
        assertNull(contentService.checkCompliance(null));
        assertNull(contentService.checkCompliance(""));
    }

    @Test
    void checkComplianceCleanContentReturnsNull() {
        assertNull(contentService.checkCompliance("这是一段正常的科技文章内容。"));
    }

    @Test
    void checkComplianceSensitiveWordReturnsMessage() {
        String result = contentService.checkCompliance("这里涉及赌博相关内容");
        assertNotNull(result);
        assertTrue(result.contains("赌博"));
    }

    // ---------- 行业模板 ----------

    @Test
    void getIndustryTemplatesReturnsSixIndustries() {
        Map<String, String> templates = contentService.getIndustryTemplates();
        assertEquals(6, templates.size());
        assertTrue(templates.containsKey("科技"));
        assertTrue(templates.containsKey("医疗"));
        assertTrue(templates.containsKey("法律"));
    }

    @Test
    void getTemplateByIndustryKnownReturnsTemplate() {
        String tpl = contentService.getTemplateByIndustry("科技");
        assertNotNull(tpl);
        assertTrue(tpl.contains("科技"));
    }

    @Test
    void getTemplateByIndustryUnknownReturnsDefault() {
        String tpl = contentService.getTemplateByIndustry("不存在的行业");
        assertNotNull(tpl);
        assertTrue(tpl.contains("通用"));
    }

    // ---------- list / get / update / delete ----------

    @Test
    void listContentsReturnsPage() {
        Page<AiArticleContent> page = new Page<>(1, 10);
        when(articleContentMapper.selectPage(any(Page.class), any())).thenReturn(page);

        Page<AiArticleContent> result = contentService.listContents(1, 10, "article", 0);
        assertNotNull(result);
        verify(articleContentMapper).selectPage(any(Page.class), any());
    }

    @Test
    void getContentByIdReturnsContent() {
        AiArticleContent c = article(1L, "标题", 2);
        when(articleContentMapper.selectById(1L)).thenReturn(c);

        assertEquals(c, contentService.getContentById(1L));
    }

    @Test
    void updateContentCallsUpdateById() {
        AiArticleContent c = article(1L, "新标题", 2);
        when(articleContentMapper.updateById(c)).thenReturn(1);

        assertEquals(c, contentService.updateContent(c));
        verify(articleContentMapper).updateById(c);
    }

    @Test
    void deleteContentCallsDeleteById() {
        contentService.deleteContent(1L);
        verify(articleContentMapper).deleteById(1L);
    }

    // ---------- generateWithAi 成功 ----------

    @Test
    void generateWithAiSuccessSetsCompletedStatus() {
        AiArticleContent c = article(1L, "标题", 0);
        when(articleContentMapper.selectById(1L)).thenReturn(c);
        when(aiAdapterFactory.getAdapter("openai")).thenReturn(aiAdapter);
        when(aiAdapter.generateContent(anyString(), anyString(), eq(1000)))
                .thenReturn("{\"title\":\"新标题\",\"content\":\"生成的内容\",\"keywords\":[\"k1\",\"k2\"]}");

        AiArticleContent result = contentService.generateWithAi(1L);

        assertEquals(Constant.TASK_STATUS_COMPLETED, result.getStatus());
        assertEquals("新标题", result.getTitle());
        assertEquals("生成的内容", result.getContent());
        assertEquals("k1,k2", result.getKeywords());
    }

    // ---------- generateWithAi 非 JSON 结果直接存原文 ----------

    @Test
    void generateWithAiNonJsonResultStoresRawContent() {
        AiArticleContent c = article(1L, "标题", 0);
        when(articleContentMapper.selectById(1L)).thenReturn(c);
        when(aiAdapterFactory.getAdapter("openai")).thenReturn(aiAdapter);
        when(aiAdapter.generateContent(anyString(), anyString(), eq(1000)))
                .thenReturn("这是一段纯文本生成结果，不是 JSON");

        AiArticleContent result = contentService.generateWithAi(1L);

        assertEquals(Constant.TASK_STATUS_COMPLETED, result.getStatus());
        assertEquals("这是一段纯文本生成结果，不是 JSON", result.getContent());
    }

    // ---------- generateWithAi 内容不存在 ----------

    @Test
    void generateWithAiNotFoundThrowsBusinessException() {
        when(articleContentMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> contentService.generateWithAi(999L));
    }

    // ---------- generateWithAi AI 异常 → FAILED ----------

    @Test
    void generateWithAiAiExceptionSetsFailedStatus() {
        AiArticleContent c = article(1L, "标题", 0);
        when(articleContentMapper.selectById(1L)).thenReturn(c);
        when(aiAdapterFactory.getAdapter("openai")).thenReturn(aiAdapter);
        when(aiAdapter.generateContent(anyString(), anyString(), eq(1000)))
                .thenThrow(new RuntimeException("AI 服务不可用"));

        AiArticleContent result = contentService.generateWithAi(1L);

        assertEquals(Constant.TASK_STATUS_FAILED, result.getStatus());
    }

    // ---------- batchGenerateArticles 批量落库 + MQ ----------

    @Test
    void batchGenerateArticlesInsertsAllAndSendsMq() {
        ReflectionTestUtils.setField(contentService, "rabbitTemplate", rabbitTemplate);
        AtomicLong idGen = new AtomicLong(100);
        doAnswer(invocation -> {
            AiArticleContent c = invocation.getArgument(0);
            c.setId(idGen.incrementAndGet());
            return 1;
        }).when(articleContentMapper).insert(any(AiArticleContent.class));

        List<ContentGenerateRequest> requests = List.of(
                request("文章1", GOOD_CONTENT, "k1"),
                request("文章2", GOOD_CONTENT, "k2")
        );

        List<Long> ids = contentService.batchGenerateArticles(requests, 1L);

        assertEquals(2, ids.size());
        verify(articleContentMapper, times(2)).insert(any(AiArticleContent.class));
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), anyString());
    }

    // ---------- batchGenerateArticles RabbitMQ 未启用时跳过 ----------

    @Test
    void batchGenerateArticlesWithoutRabbitMqSkipsMessageButInserts() {
        // rabbitTemplate 保持 null（dev 模式）
        AtomicLong idGen = new AtomicLong(200);
        doAnswer(invocation -> {
            AiArticleContent c = invocation.getArgument(0);
            c.setId(idGen.incrementAndGet());
            return 1;
        }).when(articleContentMapper).insert(any(AiArticleContent.class));

        List<Long> ids = contentService.batchGenerateArticles(
                List.of(request("文章", GOOD_CONTENT, "k")), 1L);

        assertEquals(1, ids.size());
        verify(articleContentMapper).insert(any(AiArticleContent.class));
        // rabbitTemplate 为 null，不应调用 convertAndSend（也不会 NPE）
        verifyNoInteractions(rabbitTemplate);
    }

    // ---------- asyncGenerateScript 成功 ----------

    @Test
    void asyncGenerateScriptSuccessSetsCompleted() {
        AiArticleContent c = article(1L, "脚本标题", 0);
        when(articleContentMapper.selectById(1L)).thenReturn(c);
        when(aiAdapterFactory.getAdapter("openai")).thenReturn(aiAdapter);
        when(aiAdapter.generateContent(anyString(), eq("短视频脚本"), eq(800)))
                .thenReturn("生成的短视频脚本内容");

        contentService.asyncGenerateScript(1L, "脚本标题", "品牌", "关键词");

        // 最终状态应为 COMPLETED，content 被写入
        assertEquals(Constant.TASK_STATUS_COMPLETED, c.getStatus());
        assertEquals("生成的短视频脚本内容", c.getContent());
    }

    // ---------- asyncGenerateScript 异常 → FAILED ----------

    @Test
    void asyncGenerateScriptExceptionSetsFailed() {
        AiArticleContent c = article(1L, "脚本标题", 0);
        when(articleContentMapper.selectById(1L)).thenReturn(c);
        when(aiAdapterFactory.getAdapter("openai")).thenReturn(aiAdapter);
        when(aiAdapter.generateContent(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("生成失败"));

        contentService.asyncGenerateScript(1L, "标题", "品牌", "关键词");

        assertEquals(Constant.TASK_STATUS_FAILED, c.getStatus());
    }

    // ---------- validateGeo 透传 ----------

    @Test
    void validateGeoDelegatesToValidator() {
        GeoValidationResult result = contentService.validateGeo(GOOD_CONTENT, "AI搜索");
        assertNotNull(result);
        assertFalse(result.isBlocked());
        assertTrue(result.getTotalScore() > 0);
    }

    @Test
    void validateGeoWithStuffingReturnsBlocked() {
        GeoValidationResult result = contentService.validateGeo(STUFFING_CONTENT, "AI搜索");
        assertTrue(result.isBlocked());
        assertFalse(result.getRedFlags().isEmpty());
    }
}
