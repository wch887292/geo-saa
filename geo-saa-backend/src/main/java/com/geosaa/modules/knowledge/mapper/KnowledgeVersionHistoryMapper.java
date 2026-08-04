package com.geosaa.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geosaa.modules.knowledge.entity.KnowledgeVersionHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeVersionHistoryMapper extends BaseMapper<KnowledgeVersionHistory> {

    @Select("SELECT * FROM knowledge_version_history WHERE knowledge_id = #{knowledgeId} AND deleted = 0 ORDER BY version DESC")
    List<KnowledgeVersionHistory> selectByKnowledgeId(Long knowledgeId);

    @Select("SELECT COALESCE(MAX(version), 0) FROM knowledge_version_history WHERE knowledge_id = #{knowledgeId} AND deleted = 0")
    Integer getMaxVersion(Long knowledgeId);
}