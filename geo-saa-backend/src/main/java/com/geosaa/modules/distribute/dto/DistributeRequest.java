package com.geosaa.modules.distribute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DistributeRequest {

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @NotNull(message = "内容ID不能为空")
    private Long contentId;

    @NotBlank(message = "目标平台不能为空")
    private String targetPlatform;

    private String targetAccount;

    private String distributeConfig;

    private LocalDateTime scheduledTime;
}