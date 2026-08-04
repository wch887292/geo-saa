package com.geosaa.modules.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContentGenerateRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String contentType;

    private String brandName;

    private String keywords;

    private String summary;

    private String content;
}