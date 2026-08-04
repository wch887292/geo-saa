package com.geosaa.modules.monitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_monitor_stat")
public class DataMonitorStat extends BaseEntity {

    private LocalDate statDate;

    private String statType;

    private String statKey;

    private Long statValue;

    private String dimension;

    private String remark;
}