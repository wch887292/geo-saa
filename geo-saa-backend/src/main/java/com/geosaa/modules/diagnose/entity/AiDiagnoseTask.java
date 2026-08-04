package com.geosaa.modules.diagnose.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_diagnose_task")
public class AiDiagnoseTask extends BaseEntity {

    private String taskName;

    private String taskType;

    private String brandName;

    private String inputParams;

    private String resultContent;

    private Integer status;

    private Long createdBy;

    private String remark;
}