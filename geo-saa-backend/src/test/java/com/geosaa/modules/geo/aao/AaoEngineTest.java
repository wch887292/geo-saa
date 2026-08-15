package com.geosaa.modules.geo.aao;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AAO 引擎单测（GEO/AAO 升级）：AX Score 六维度评分、分级、llms.txt / agent.json 生成。
 */
class AaoEngineTest {

    private final AaoEngine engine = new AaoEngine();

    private AaoProfile fullProfile() {
        AaoProfile p = new AaoProfile();
        p.setDomain("klai.top");
        p.setHasLlmsTxt(true);
        p.setHasLlmsFullTxt(true);
        p.setHasApiCatalog(true);
        p.setHasMcpCard(true);
        p.setHasAgentJson(true);
        p.setHasOpenApi(true);
        p.setAllowAiCrawlers(true);
        p.setHasContentSignals(true);
        p.setHasStructuredData(true);
        p.setHasFaq(true);
        p.setHttps(true);
        p.setHasPrivacyPolicy(true);
        p.setApiCount(12);
        p.setToolCount(5);
        p.setDescriptionQuality(true);
        return p;
    }

    @Test
    void fullyReadyProfileScoresExcellent() {
        AaoReport r = engine.evaluate(fullProfile());

        assertTrue(r.getAxScore() >= 90, "全就绪画像 AX 应 >= 90，实际 " + r.getAxScore());
        assertEquals("Excellent", r.getGrade());
        assertEquals(6, r.getDimensions().size());
        assertTrue(r.getSuggestions().get(0).contains("优秀"));
    }

    @Test
    void bareProfileScoresPoor() {
        AaoProfile p = new AaoProfile();
        p.setDomain("example.com");

        AaoReport r = engine.evaluate(p);

        assertTrue(r.getAxScore() <= 20, "空画像 AX 应 <= 20，实际 " + r.getAxScore());
        assertEquals("Poor", r.getGrade());
        assertFalse(r.getSuggestions().isEmpty());
    }

    @Test
    void partialProfileScoresGood() {
        AaoProfile p = fullProfile();
        p.setHasLlmsTxt(false);
        p.setHasMcpCard(false);
        p.setHasAgentJson(false);
        p.setHasOpenApi(false);

        AaoReport r = engine.evaluate(p);

        // 去掉 interaction 部分与 discoverability 的 agent.json 后 → AX=82（Good 档）
        assertTrue(r.getAxScore() >= 70 && r.getAxScore() < 90,
                "部分就绪 AX 应在 70-89（Good），实际 " + r.getAxScore());
        assertEquals("Good", r.getGrade());
    }

    @Test
    void llmsTxtGenerationFollowsStandardFormat() {
        String txt = engine.generateLlmsTxt("飞虹智", "https://klai.top",
                "泉州制造业 AI 服务商，提供企业 AI 平台与 GEO 优化。",
                List.of("官网: https://klai.top", "开源矩阵: https://klai.top/opensource.html"));

        assertTrue(txt.startsWith("# 飞虹智"));
        assertTrue(txt.contains("> 泉州制造业 AI 服务商"));
        assertTrue(txt.contains("## Pages"));
        assertTrue(txt.contains("- [官网](https://klai.top)"));
        assertTrue(txt.contains("## AI-friendly"));
        assertTrue(txt.contains("llms.txt: https://klai.top/llms.txt"));
    }

    @Test
    void agentJsonGenerationValidSkeleton() {
        String json = engine.generateAgentJson("飞虹智", "https://klai.top", "企业 AI Agent",
                List.of("品牌诊断", "GEO 内容生成"), "https://klai.top/api/agent");

        assertTrue(json.contains("\"name\": \"飞虹智\""));
        assertTrue(json.contains("\"skills\": [\"品牌诊断\", \"GEO 内容生成\"]"));
        assertTrue(json.contains("\"dispatch\": \"https://klai.top/api/agent\""));
        assertTrue(json.contains("\"@context\"") == false); // A2A AgentCard 骨架含 name/description 即可
    }
}
