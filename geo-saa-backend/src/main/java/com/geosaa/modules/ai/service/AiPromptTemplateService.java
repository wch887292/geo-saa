package com.geosaa.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.tenant.TenantContext;
import com.geosaa.modules.ai.entity.AiPromptTemplate;
import com.geosaa.modules.ai.mapper.AiPromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Prompt 模板服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptTemplateService {

    private final AiPromptTemplateMapper templateMapper;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /**
     * 分页查询模板
     */
    public Page<AiPromptTemplate> page(int pageNum, int pageSize, String keyword, String businessModule) {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<AiPromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiPromptTemplate::getTenantId, tenantId)
               .orderByDesc(AiPromptTemplate::getIsActive)
               .orderByDesc(AiPromptTemplate::getCreatedAt);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(AiPromptTemplate::getTemplateName, keyword)
                   .or().like(AiPromptTemplate::getTemplateCode, keyword);
        }
        if (businessModule != null && !businessModule.isEmpty()) {
            wrapper.eq(AiPromptTemplate::getBusinessModule, businessModule);
        }
        return templateMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 根据编码获取启用的模板
     */
    public AiPromptTemplate getByCode(String templateCode) {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<AiPromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiPromptTemplate::getTenantId, tenantId)
               .eq(AiPromptTemplate::getTemplateCode, templateCode)
               .eq(AiPromptTemplate::getIsActive, 1)
               .orderByDesc(AiPromptTemplate::getVersion)
               .last("LIMIT 1");
        return templateMapper.selectOne(wrapper);
    }

    /**
     * 渲染模板（替换 {{变量}} 占位符）
     */
    public String renderTemplate(String templateCode, Map<String, String> variables) {
        AiPromptTemplate template = getByCode(templateCode);
        if (template == null || template.getUserPromptTemplate() == null) {
            return null;
        }
        String result = template.getUserPromptTemplate();
        Matcher matcher = VARIABLE_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 新增模板
     */
    @Transactional
    public AiPromptTemplate create(AiPromptTemplate template) {
        template.setTenantId(TenantContext.getTenantId());
        if (template.getVersion() == null) template.setVersion(1);
        if (template.getIsActive() == null) template.setIsActive(1);
        templateMapper.insert(template);
        log.info("创建Prompt模板: id={}, code={}, name={}", template.getId(), template.getTemplateCode(), template.getTemplateName());
        return template;
    }

    /**
     * 更新模板
     */
    @Transactional
    public AiPromptTemplate update(AiPromptTemplate template) {
        templateMapper.updateById(template);
        return templateMapper.selectById(template.getId());
    }

    /**
     * 删除模板
     */
    @Transactional
    public void delete(Long id) {
        templateMapper.deleteById(id);
    }
}
