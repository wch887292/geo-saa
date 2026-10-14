package com.geosaa.modules.asset;

import com.geosaa.modules.asset.mapper.AssetRecordMapper;
import com.geosaa.modules.content.mapper.AiArticleContentMapper;
import com.geosaa.modules.diagnose.mapper.AiDiagnoseTaskMapper;
import com.geosaa.modules.distribute.mapper.DistributeTaskMapper;
import com.geosaa.modules.knowledge.mapper.BrandInfoMapper;
import com.geosaa.modules.knowledge.mapper.BrandKnowledgeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * AssetService.getOverview 资产统计口径回归测试。
 *
 * <p>复现的真实缺陷：overview 接口返回 {@code knowledgeTotal=0} 但用户已新增品牌，
 * 导致前端误以为"知识库为空"。根因是 {@code knowledgeTotal} 统计的是
 * {@code brand_knowledge} 表（知识库内容条目），而非 {@code brand_info} 表（品牌数），
 * 但字段名 "knowledgeTotal" 和 API 路径 {@code /knowledge/brands} 让调用方自然以为
 * 它包含品牌数。
 *
 * <p>修复后的契约：新增 {@code brandTotal} 字段独立返回品牌数，
 * {@code knowledgeTotal} 语义不变但注释明确其含义。
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceOverviewTest {

    @Mock
    private AiArticleContentMapper contentMapper;
    @Mock
    private DistributeTaskMapper distributeTaskMapper;
    @Mock
    private BrandKnowledgeMapper knowledgeMapper;
    @Mock
    private BrandInfoMapper brandInfoMapper;
    @Mock
    private AiDiagnoseTaskMapper diagnoseTaskMapper;
    @Mock
    private AssetRecordMapper assetRecordMapper;

    private AssetService service;

    @Test
    @DisplayName("品牌数独立返回 brandTotal，不混入 knowledgeTotal")
    void overview_shouldReturnBrandTotalSeparately() {
        when(contentMapper.selectCount(null)).thenReturn(5L);
        when(knowledgeMapper.selectCount(null)).thenReturn(0L);
        when(brandInfoMapper.selectCount(null)).thenReturn(3L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(0L);
        when(diagnoseTaskMapper.selectCount(null)).thenReturn(0L);
        when(assetRecordMapper.selectCount(null)).thenReturn(0L);

        service = new AssetService(contentMapper, distributeTaskMapper, knowledgeMapper,
                brandInfoMapper, diagnoseTaskMapper, assetRecordMapper);

        Map<String, Object> result = service.getOverview();

        // brandTotal 独立返回品牌数
        assertEquals(3L, result.get("brandTotal"));
        // knowledgeTotal 仍为知识库条目数（本例为 0，因为没有知识库内容）
        assertEquals(0L, result.get("knowledgeTotal"));
        // totalAssets = content + knowledge，不含品牌（品牌是资产容器，不是资产本身）
        assertEquals(5L, result.get("totalAssets"));
        // byType 包含 brand
        @SuppressWarnings("unchecked")
        Map<String, Long> byType = (Map<String, Long>) result.get("byType");
        assertNotNull(byType.get("brand"));
        assertEquals(3L, byType.get("brand"));
    }

    @Test
    @DisplayName("knowledgeTotal 统计 brand_knowledge 表，brandTotal 统计 brand_info 表，两者独立")
    void overview_knowledgeAndBrandAreSeparateCounts() {
        when(contentMapper.selectCount(null)).thenReturn(2L);
        when(knowledgeMapper.selectCount(null)).thenReturn(7L);
        when(brandInfoMapper.selectCount(null)).thenReturn(4L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(1L);
        when(diagnoseTaskMapper.selectCount(null)).thenReturn(3L);
        when(assetRecordMapper.selectCount(null)).thenReturn(0L);

        service = new AssetService(contentMapper, distributeTaskMapper, knowledgeMapper,
                brandInfoMapper, diagnoseTaskMapper, assetRecordMapper);

        Map<String, Object> result = service.getOverview();

        // 两个字段独立，不互相包含
        assertEquals(7L, result.get("knowledgeTotal"));  // brand_knowledge 条目
        assertEquals(4L, result.get("brandTotal"));     // brand_info 品牌
        // totalAssets = content + knowledge = 2 + 7 = 9
        assertEquals(9L, result.get("totalAssets"));
    }
}