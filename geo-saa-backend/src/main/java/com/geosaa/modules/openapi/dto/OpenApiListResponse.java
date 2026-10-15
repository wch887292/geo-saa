package com.geosaa.modules.openapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 开放 API 分页列表响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiListResponse<T> {

    private List<T> items;
    private long total;
    private int pageNum;
    private int pageSize;

    public static <T> OpenApiListResponse<T> of(List<T> items, long total, int pageNum, int pageSize) {
        return new OpenApiListResponse<>(items, total, pageNum, pageSize);
    }
}
