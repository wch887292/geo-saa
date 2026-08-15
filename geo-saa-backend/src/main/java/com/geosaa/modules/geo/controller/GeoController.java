package com.geosaa.modules.geo.controller;

import com.geosaa.common.Result;
import com.geosaa.modules.geo.aao.AaoEngine;
import com.geosaa.modules.geo.aao.AaoProfile;
import com.geosaa.modules.geo.aao.AaoReport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * GEO/AAO 能力接口（GEO v2 + AAO 引擎）。
 *
 * <p>提供：AAO 就绪度评估（AX Score）、llms.txt 生成、agent.json 生成。
 * 需登录态访问（安全配置统一拦截）。
 */
@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
public class GeoController {

    private final AaoEngine aaoEngine;

    /**
     * AAO 就绪度评估：提交 Agent-Ready 画像，返回 AX Score 六维度报告。
     * 请求体即 {@link AaoProfile}（字段见其定义）。
     */
    @PostMapping("/aao-validate")
    public Result<AaoReport> aaoValidate(@RequestBody AaoProfile profile) {
        return Result.success(aaoEngine.evaluate(profile));
    }

    /**
     * 生成 llms.txt（llmstxt.org 格式）。
     * 参数：brandName / siteUrl / description / pages（"标题: URL"逗号分隔）。
     */
    @GetMapping("/llms-txt")
    public Result<String> llmsTxt(
            @RequestParam String brandName,
            @RequestParam String siteUrl,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String pages) {
        List<String> pageList = pages == null || pages.isBlank()
                ? List.of()
                : Arrays.stream(pages.split("[,，]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return Result.success(aaoEngine.generateLlmsTxt(brandName, siteUrl, description, pageList));
    }

    /**
     * 生成 /.well-known/agent.json（A2A AgentCard 骨架）。
     * 参数：brandName / siteUrl / description / skills（逗号分隔）/ dispatchUrl。
     */
    @GetMapping("/agent-json")
    public Result<String> agentJson(
            @RequestParam String brandName,
            @RequestParam(required = false) String siteUrl,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String skills,
            @RequestParam(required = false) String dispatchUrl) {
        List<String> skillList = skills == null || skills.isBlank()
                ? List.of()
                : Arrays.stream(skills.split("[,，]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return Result.success(aaoEngine.generateAgentJson(brandName, siteUrl, description, skillList, dispatchUrl));
    }
}
