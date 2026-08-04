package com.geosaa.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geosaa.modules.system.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    @Select("SELECT * FROM system_config WHERE config_group = #{group} AND deleted = 0 AND status = 1")
    List<SystemConfig> selectByGroup(String group);

    @Select("SELECT * FROM system_config WHERE config_key = #{key} AND deleted = 0 AND status = 1")
    SystemConfig selectByKey(String key);
}