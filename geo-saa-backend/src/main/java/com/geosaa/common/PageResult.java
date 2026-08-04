package com.geosaa.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {

    private int code;
    private String message;
    private List<T> data;
    private long total;
    private long pageSize;
    private long pageNum;
    private long pages;

    private PageResult() {
    }

    public static <T> PageResult<T> success(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageSize(page.getSize());
        result.setPageNum(page.getCurrent());
        result.setPages(page.getPages());
        return result;
    }

    public static <T> PageResult<T> success(List<T> list, long total, long pageNum, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(list);
        result.setTotal(total);
        result.setPageSize(pageSize);
        result.setPageNum(pageNum);
        result.setPages((long) Math.ceil((double) total / pageSize));
        return result;
    }
}