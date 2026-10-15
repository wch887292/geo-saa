package com.geosaa.modules.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.ai.AiAdapter;
import com.geosaa.ai.AiAdapterFactory;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.knowledge.entity.BrandInfo;
import com.geosaa.modules.knowledge.entity.BrandKnowledge;
import com.geosaa.modules.knowledge.entity.KnowledgeVersionHistory;
import com.geosaa.modules.knowledge.mapper.BrandInfoMapper;
import com.geosaa.modules.knowledge.mapper.BrandKnowledgeMapper;
import com.geosaa.modules.knowledge.mapper.KnowledgeVersionHistoryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KnowledgeService 单测（O5 测试补全）。
 *
 * <p>覆盖：品牌 CRUD、知识列表、createKnowledge 唯一性校验+版本初始化、
 * updateKnowledge 版本递增+旧版备份、delete、版本历史、AI 结构化转换、JSON-LD 生成。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock private BrandInfoMapper brandInfoMapper;
    @Mock private BrandKnowledgeMapper brandKnowledgeMapper;
    @Mock private KnowledgeVersionHistoryMapper knowledgeVersionHistoryMapper;
    @Mock private AiAdapterFactory aiAdapterFactory;
    @Mock private AiAdapter aiAdapter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeService = new KnowledgeService(brandInfoMapper, brandKnowledgeMapper,
                knowledgeVersionHistoryMapper, aiAdapterFactory, objectMapper);
    }

    private BrandInfo brand(Long id, String name) {
        BrandInfo b = new BrandInfo();
        b.setId(id);
        b.setBrandName(name);
        b.setBrandDescription("品牌描述");
        b.setIndustry("科技");
        b.setWebsite("https://example.com");
        b.setBrandLogo("https://example.com/logo.png");
        return b;
    }

    private BrandKnowledge knowledge(Long id, Long brandId, String type, String title) {
        BrandKnowledge k = new BrandKnowledge();
        k.setId(id);
        k.setBrandId(brandId);
        k.setKnowledgeType(type);
        k.setTitle(title);
        k.setContent("知识内容");
        k.setSource("manual");
        k.setStatus(1);
        return k;
    }

    // ---------- 品牌 CRUD ----------

    @Test
    void listBrandsReturnsPage() {
        Page<BrandInfo> page = new Page<>(1, 10);
        when(brandInfoMapper.selectPage(any(Page.class), any())).thenReturn(page);

        Page<BrandInfo> result = knowledgeService.listBrands(1, 10, "测试", "科技");
        assertNotNull(result);
        verify(brandInfoMapper).selectPage(any(Page.class), any());
    }

    @Test
    void getBrandByIdReturnsBrand() {
        BrandInfo b = brand(1L, "测试品牌");
        when(brandInfoMapper.selectById(1L)).thenReturn(b);
        assertEquals(b, knowledgeService.getBrandById(1L));
    }

    @Test
    void createBrandInsertsAndReturns() {
        BrandInfo b = brand(null, "新品牌");
        doReturn(1).when(brandInfoMapper).insert(any(BrandInfo.class));
        assertEquals(b, knowledgeService.createBrand(b));
        verify(brandInfoMapper).insert(b);
    }

    @Test
    void updateBrandCallsUpdateById() {
        BrandInfo b = brand(1L, "更新品牌");
        when(brandInfoMapper.updateById(b)).thenReturn(1);
        assertEquals(b, knowledgeService.updateBrand(b));
    }

    @Test
    void deleteBrandCallsDeleteById() {
        knowledgeService.deleteBrand(1L);
        verify(brandInfoMapper).deleteById(1L);
    }

    // ---------- 知识列表 ----------

    @Test
    void listKnowledgeByBrandReturnsList() {
        when(brandKnowledgeMapper.selectList(any())).thenReturn(List.of(
                knowledge(1L, 1L, "product", "产品优势"),
                knowledge(2L, 1L, "data", "权威数据")
        ));
        List<BrandKnowledge> result = knowledgeService.listKnowledgeByBrand(1L);
        assertEquals(2, result.size());
    }

    // ---------- createKnowledge ----------

    @Test
    void createKnowledgeSuccessSetsStatusAndInitVersion() {
        when(brandKnowledgeMapper.selectCount(any())).thenReturn(0L);
        doReturn(1).when(brandKnowledgeMapper).insert(any(BrandKnowledge.class));
        doReturn(1).when(knowledgeVersionHistoryMapper).insert(any(KnowledgeVersionHistory.class));

        BrandKnowledge k = knowledge(null, 1L, "product", "新产品知识");
        BrandKnowledge result = knowledgeService.createKnowledge(k);

        assertEquals(1, result.getStatus());
        verify(brandKnowledgeMapper).insert(k);
        // 初始化版本历史：version=1, changeLog="初始创建"
        verify(knowledgeVersionHistoryMapper).insert(argThat((KnowledgeVersionHistory h) ->
                h.getVersion() == 1 && "初始创建".equals(h.getChangeLog())));
    }

    @Test
    void createKnowledgeDuplicateThrowsBusinessException() {
        when(brandKnowledgeMapper.selectCount(any())).thenReturn(1L);

        BrandKnowledge k = knowledge(null, 1L, "product", "重复知识");
        assertThrows(BusinessException.class, () -> knowledgeService.createKnowledge(k));
        // 重复时不应插入
        verify(brandKnowledgeMapper, never()).insert(any(BrandKnowledge.class));
    }

    // ---------- updateKnowledge ----------

    @Test
    void updateKnowledgeSuccessIncrementsVersionAndBackupsOld() {
        BrandKnowledge old = knowledge(1L, 1L, "product", "旧标题");
        old.setContent("旧内容");
        BrandKnowledge updated = knowledge(1L, 1L, "product", "新标题");
        updated.setContent("新内容");

        when(brandKnowledgeMapper.selectById(1L)).thenReturn(old);
        when(knowledgeVersionHistoryMapper.getMaxVersion(1L)).thenReturn(1);
        when(brandKnowledgeMapper.updateById(updated)).thenReturn(1);
        doReturn(1).when(knowledgeVersionHistoryMapper).insert(any(KnowledgeVersionHistory.class));

        BrandKnowledge result = knowledgeService.updateKnowledge(updated);

        assertEquals(updated, result);
        verify(brandKnowledgeMapper).updateById(updated);
        // 旧版本备份：version=1, changeLog 含"自动备份"
        verify(knowledgeVersionHistoryMapper).insert(argThat((KnowledgeVersionHistory h) ->
                h.getVersion() == 1 && h.getChangeLog().contains("自动备份")));
        // 新版本：version=2, changeLog="内容更新"
        verify(knowledgeVersionHistoryMapper).insert(argThat((KnowledgeVersionHistory h) ->
                h.getVersion() == 2 && "内容更新".equals(h.getChangeLog())));
    }

    @Test
    void updateKnowledgeFirstTimeMaxVersionNull() {
        BrandKnowledge old = knowledge(1L, 1L, "product", "旧标题");
        BrandKnowledge updated = knowledge(1L, 1L, "product", "新标题");

        when(brandKnowledgeMapper.selectById(1L)).thenReturn(old);
        when(knowledgeVersionHistoryMapper.getMaxVersion(1L)).thenReturn(null);
        when(brandKnowledgeMapper.updateById(updated)).thenReturn(1);
        doReturn(1).when(knowledgeVersionHistoryMapper).insert(any(KnowledgeVersionHistory.class));

        knowledgeService.updateKnowledge(updated);

        // maxVersion=null → 旧版 version=0, 新版 version=1
        verify(knowledgeVersionHistoryMapper).insert(argThat((KnowledgeVersionHistory h) -> h.getVersion() == 0));
        verify(knowledgeVersionHistoryMapper).insert(argThat((KnowledgeVersionHistory h) -> h.getVersion() == 1));
    }

    @Test
    void updateKnowledgeNotFoundThrowsBusinessException() {
        when(brandKnowledgeMapper.selectById(999L)).thenReturn(null);
        BrandKnowledge k = knowledge(999L, 1L, "product", "不存在");
        assertThrows(BusinessException.class, () -> knowledgeService.updateKnowledge(k));
    }

    // ---------- deleteKnowledge / getKnowledgeVersions ----------

    @Test
    void deleteKnowledgeCallsDeleteById() {
        knowledgeService.deleteKnowledge(1L);
        verify(brandKnowledgeMapper).deleteById(1L);
    }

    @Test
    void getKnowledgeVersionsDelegatesToMapper() {
        when(knowledgeVersionHistoryMapper.selectByKnowledgeId(1L)).thenReturn(List.of());
        assertNotNull(knowledgeService.getKnowledgeVersions(1L));
    }

    // ---------- AI 结构化转换 ----------

    @Test
    void autoStructureConversionSuccessReturnsAiResult() {
        when(brandInfoMapper.selectById(1L)).thenReturn(brand(1L, "测试品牌"));
        when(aiAdapterFactory.getAdapter("openai")).thenReturn(aiAdapter);
        when(aiAdapter.generateContent(anyString(), eq("结构化转换"), eq(500)))
                .thenReturn("{\"title\":\"结构化标题\"}");

        String result = knowledgeService.autoStructureConversion(1L, "原始文本");
        assertTrue(result.contains("结构化标题"));
    }

    @Test
    void autoStructureConversionBrandNotFoundThrows() {
        when(brandInfoMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> knowledgeService.autoStructureConversion(999L, "文本"));
    }

    // ---------- JSON-LD 生成 ----------

    @Test
    void generateJsonLdSuccessContainsBrandAndKnowledge() {
        when(brandInfoMapper.selectById(1L)).thenReturn(brand(1L, "测试品牌"));
        when(brandKnowledgeMapper.selectList(any())).thenReturn(List.of(
                knowledge(1L, 1L, "product", "产品优势")
        ));

        String jsonLd = knowledgeService.generateJsonLd(1L);

        assertNotNull(jsonLd);
        assertTrue(jsonLd.contains("\"@type\" : \"Brand\""));
        assertTrue(jsonLd.contains("测试品牌"));
        assertTrue(jsonLd.contains("knowsAbout"));
        assertTrue(jsonLd.contains("产品优势"));
        assertTrue(jsonLd.contains("https://example.com"));
    }

    @Test
    void generateJsonLdNoKnowledgeOmitsKnowsAbout() {
        when(brandInfoMapper.selectById(1L)).thenReturn(brand(1L, "空品牌"));
        when(brandKnowledgeMapper.selectList(any())).thenReturn(List.of());

        String jsonLd = knowledgeService.generateJsonLd(1L);
        assertFalse(jsonLd.contains("knowsAbout"));
    }

    @Test
    void generateJsonLdBrandNotFoundThrows() {
        when(brandInfoMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> knowledgeService.generateJsonLd(999L));
    }
}
