package com.geosaa.modules.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.Result;
import com.geosaa.modules.ai.entity.AiCallLog;
import com.geosaa.modules.ai.service.AiCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 调用日志 Controller
 */
@RestController
@RequestMapping("/api/v1/ai/call-log")
@RequiredArgsConstructor
public class AiCallLogController {

    private final AiCallLogService callLogService;

    @GetMapping("/page")
    public Result<Page<AiCallLog>> page(@RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize,
                                    @RequestParam(required = false) String modelName,
                                    @RequestParam(required = false) String businessModule,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                    @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(callLogService.page(pageNum, pageSize, modelName, businessModule, status, startTime, endTime));
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                              @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(callLogService.getStatistics(startTime, endTime));
    }
}
