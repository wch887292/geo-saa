package com.geosaa.modules.ai.controller;

import com.geosaa.common.Result;
import com.geosaa.modules.ai.dto.AiChatRequest;
import com.geosaa.modules.ai.dto.AiChatResponse;
import com.geosaa.modules.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI 对话 Controller（统一AI大模型调用入口）
 */
@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping
    public Result<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        return Result.success(aiChatService.chat(request));
    }

    @PostMapping("/generate")
    public Result<String> generate(@RequestParam String systemPrompt,
                              @RequestParam String userPrompt,
                              @RequestParam(required = false) String businessModule,
                              @RequestParam(required = false) String businessType) {
        return Result.success(aiChatService.generateText(systemPrompt, userPrompt, businessModule, businessType));
    }
}
