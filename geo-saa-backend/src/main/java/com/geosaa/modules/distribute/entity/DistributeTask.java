package com.geosaa.modules.distribute.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("distribute_task")
public class DistributeTask extends BaseEntity {

    private String taskName;

    private Long contentId;

    private String targetPlatform;

    private String targetAccount;

    private String distributeConfig;

    private Integer status;

    private LocalDateTime scheduledTime;

    private LocalDateTime publishTime;

    private String resultInfo;

    private Long createdBy;
}