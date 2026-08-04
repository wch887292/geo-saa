package com.geosaa.modules.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.ai.AiAdapterFactory;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.knowledge.entity.BrandInfo;
import com.geosaa.modules.knowledge.entity.BrandKnowledge;
import com.geosaa.modules.knowledge.entity.KnowledgeVersionHistory;
import com.geosaa.modules.knowledge.mapper.BrandInfoMapper;
import com.geosaa.modules.knowledge.mapper.BrandKnowledgeMapper;
import com.geosaa.modules.knowledge.mapper.KnowledgeVersionHistoryMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final BrandInfoMapper brandInfoMapper;
    private final BrandKnowledgeMapper brandKnowledgeMapper;
    private final KnowledgeVersionHistoryMapper knowledgeVersionHistoryMapper;
    private final AiAdapterFactory aiAdapterFactory;
    private final ObjectMapper objectMapper;

    // ========== 品牌信息 ==========

    public Page<BrandInfo> listBrands(int pageNum, int pageSize, String brandName, String industry) {
        LambdaQueryWrapper<BrandInfo> wrapper = new LambdaQueryWrapper<>();
        if (brandName != null) {
            wrapper.like(BrandInfo::getBrandName, brandName);
        }
        if (industry != null) {
            wrapper.eq(BrandInfo::getIndustry, industry);
        }
        wrapper.orderByDesc(BrandInfo::getCreateTime);
        return brandInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public BrandInfo getBrandById(Long id) {
        return brandInfoMapper.selectById(id);
    }

    public BrandInfo createBrand(BrandInfo brandInfo) {
        brandInfoMapper.insert(brandInfo);
        return brandInfo;
    }

    public BrandInfo updateBrand(BrandInfo brandInfo) {
        brandInfoMapper.updateById(brandInfo);
        return brandInfo;
    }

    public void deleteBrand(Long id) {
        brandInfoMapper.deleteById(id);
    }

    // ========== 品牌知识 ==========

    public List<BrandKnowledge> listKnowledgeByBrand(Long brandId) {
        LambdaQueryWrapper<BrandKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BrandKnowledge::getBrandId, brandId);
        wrapper.orderByDesc(BrandKnowledge::getCreateTime);
        return brandKnowledgeMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public BrandKnowledge createKnowledge(BrandKnowledge knowledge) {
        // 事实源唯一校验：相同品牌+知识类型不允许重复
        LambdaQueryWrapper<BrandKnowledge> uniqueWrapper = new LambdaQueryWrapper<>();
        uniqueWrapper.eq(BrandKnowledge::getBrandId, knowledge.getBrandId());
        uniqueWrapper.eq(BrandKnowledge::getKnowledgeType, knowledge.getKnowledgeType());
        Long count = brandKnowledgeMapper.selectCount(uniqueWrapper);
        if (count > 0) {
            throw new BusinessException("该品牌下已存在相同类型的知识，请勿重复添加");
        }
        knowledge.setStatus(1);
        brandKnowledgeMapper.insert(knowledge);
        // 初始化版本历史
        saveVersionHistory(knowledge, 1, "初始创建");
        return knowledge;
    }

    @Transactional(rollbackFor = Exception.class)
    public BrandKnowledge updateKnowledge(BrandKnowledge knowledge) {
        BrandKnowledge old = brandKnowledgeMapper.selectById(knowledge.getId());
        if (old == null) {
            throw new BusinessException("知识记录不存在");
        }
        // 自动递增版本号
        Integer maxVersion = knowledgeVersionHistoryMapper.getMaxVersion(knowledge.getId());
        int newVersion = (maxVersion == null ? 0 : maxVersion) + 1;

        // 保存旧版本到历史
        saveVersionHistory(old, maxVersion == null ? 0 : maxVersion, "自动备份-更新前版本");

        brandKnowledgeMapper.updateById(knowledge);

        // 保存新版本
        saveVersionHistory(knowledge, newVersion, "内容更新");
        return knowledge;
    }

    public void deleteKnowledge(Long id) {
        brandKnowledgeMapper.deleteById(id);
    }

    /**
     * 获取知识版本历史
     */
    public List<KnowledgeVersionHistory> getKnowledgeVersions(Long knowledgeId) {
        return knowledgeVersionHistoryMapper.selectByKnowledgeId(knowledgeId);
    }

    /**
     * 保存版本历史
     */
    private void saveVersionHistory(BrandKnowledge knowledge, int version, String changeLog) {
        KnowledgeVersionHistory history = new KnowledgeVersionHistory();
        history.setKnowledgeId(knowledge.getId());
        history.setVersion(version);
        history.setTitle(knowledge.getTitle());
        history.setContent(knowledge.getContent());
        history.setSource(knowledge.getSource());
        history.setChangeLog(changeLog);
        knowledgeVersionHistoryMapper.insert(history);
    }

    /**
     * AI 自动结构化转换：将非结构化文本转为结构化 JSON
     */
    public String autoStructureConversion(Long brandId, String unstructuredText) {
        BrandInfo brand = brandInfoMapper.selectById(brandId);
        if (brand == null) {
            throw new BusinessException("品牌不存在");
        }
        String prompt = "请将以下关于品牌「" + brand.getBrandName() + "」的非结构化文本转换为结构化JSON格式，包含：title, summary, keyPoints, tags, category字段：\n\n" + unstructuredText;
        return aiAdapterFactory.getAdapter("openai").generateContent(prompt, "结构化转换", 500);
    }

    /**
     * JSON-LD 格式生成（Schema.org 标准）
     */
    public String generateJsonLd(Long brandId) {
        BrandInfo brand = brandInfoMapper.selectById(brandId);
        if (brand == null) {
            throw new BusinessException("品牌不存在");
        }
        List<BrandKnowledge> knowledgeList = listKnowledgeByBrand(brandId);

        Map<String, Object> jsonLd = new LinkedHashMap<>();
        jsonLd.put("@context", "https://schema.org");
        jsonLd.put("@type", "Brand");
        jsonLd.put("name", brand.getBrandName());
        jsonLd.put("description", brand.getBrandDescription());
        if (brand.getWebsite() != null) {
            jsonLd.put("url", brand.getWebsite());
        }
        if (brand.getBrandLogo() != null) {
            jsonLd.put("logo", brand.getBrandLogo());
        }
        jsonLd.put("industry", brand.getIndustry());

        // 添加知识条目
        List<Map<String, Object>> knowledgeItems = new ArrayList<>();
        for (BrandKnowledge k : knowledgeList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("@type", "CreativeWork");
            item.put("name", k.getTitle());
            item.put("description", k.getContent());
            item.put("about", k.getKnowledgeType());
            knowledgeItems.add(item);
        }
        if (!knowledgeItems.isEmpty()) {
            jsonLd.put("knowsAbout", knowledgeItems);
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonLd);
        } catch (JsonProcessingException e) {
            log.error("生成JSON-LD失败", e);
            return "{}";
        }
    }
}