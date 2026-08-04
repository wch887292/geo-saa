package com.geosaa.modules.diagnose.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DiagnoseRequest {

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @NotBlank(message = "诊断类型不能为空")
    private String taskType;

    private String brandName;

    private String inputParams;

    private String remark;
}