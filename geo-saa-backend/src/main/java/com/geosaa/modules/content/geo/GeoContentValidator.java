package com.geosaa.modules.content.geo;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GEO 九战术内容校验器。
 *
 * <p>实现依据：Aggarwal et al., <em>GEO: Generative Engine Optimization</em>,
 * ACM SIGKDD 2024（arXiv:2311.09735）GEO-bench 实测相对提升：
 * <ul>
 *   <li>引语 Quotation Addition +41%（权重 0.23）</li>
 *   <li>统计 Statistics Addition +31%（权重 0.18）</li>
 *   <li>流畅度 Fluency Optimization +28%（权重 0.16）</li>
 *   <li>引用源 Cite Sources +28%（权重 0.16）</li>
 *   <li>技术术语 Technical Terms +18%（权重 0.10）</li>
 *   <li>易懂 Easy-to-Understand +14%（权重 0.08）</li>
 *   <li>权威语气 Authoritative +10%（权重 0.06）</li>
 *   <li>独特词 Unique Words +6%（权重 0.03）</li>
 *   <li>关键词堆砌 Keyword Stuffing −8%~−10%（负向，密度超阈值直接拦截）</li>
 * </ul>
 *
 * <p>所有判定均为启发式（正则/计数），不依赖 LLM，保证低成本、可单测、
 * 结果可解释。关键词堆砌是论文中唯一负向战术，本校验器对密度 &gt; 阈值
 * （默认 3%）的内容标记 {@code blocked=true}。
 */
@Component
public class GeoContentValidator {

    public static final String T_AUTHORITATIVE = "authoritative";
    public static final String T_STATISTICS = "statistics";
    public static final String T_KEYWORD_STUFFING = "keyword_stuffing";
    public static final String T_CITE_SOURCES = "cite_sources";
    public static final String T_QUOTATIONS = "quotations";
    public static final String T_EASY_TO_UNDERSTAND = "easy_to_understand";
    public static final String T_FLUENCY = "fluency";
    public static final String T_UNIQUE_WORDS = "unique_words";
    public static final String T_TECHNICAL_TERMS = "technical_terms";
    // ---- GEO v2 新维度（对标 2026 规则）----
    public static final String T_ANSWER_FIRST = "answer_first";
    public static final String T_FACT_DENSITY = "fact_density";
    public static final String T_STRUCTURED_DATA = "structured_data";
    public static final String T_EEAT = "eeat";
    public static final String T_KEY_QUOTE = "key_quote";
    public static final String T_FRESHNESS = "freshness";
    public static final String T_OWN_CITATIONS = "own_citations";

    /** 默认关键词堆砌拦截阈值：密度 &gt; 3% 触发 blocked */
    public static final double KEYWORD_STUFFING_THRESHOLD = 0.03;

    /** 16 项正向策略权重（九战术 + 2026 新维度，归一化总和 = 1.0） */
    private static final Map<String, Double> WEIGHTS = new LinkedHashMap<>();
    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        // ---- 九战术（Princeton KDD 2024）----
        NAMES.put(T_QUOTATIONS, "专家引语");
        NAMES.put(T_STATISTICS, "量化数据");
        NAMES.put(T_FLUENCY, "流畅度");
        NAMES.put(T_CITE_SOURCES, "引用来源");
        NAMES.put(T_TECHNICAL_TERMS, "技术术语");
        NAMES.put(T_EASY_TO_UNDERSTAND, "易于理解");
        NAMES.put(T_AUTHORITATIVE, "权威语气");
        NAMES.put(T_UNIQUE_WORDS, "独特词汇");
        NAMES.put(T_KEYWORD_STUFFING, "关键词堆砌");
        // ---- GEO v2 新维度（2026 规则）----
        NAMES.put(T_ANSWER_FIRST, "答案前置");
        NAMES.put(T_FACT_DENSITY, "事实密度");
        NAMES.put(T_STRUCTURED_DATA, "结构化数据");
        NAMES.put(T_EEAT, "E-E-A-T");
        NAMES.put(T_KEY_QUOTE, "关键引语");
        NAMES.put(T_FRESHNESS, "新鲜度");
        NAMES.put(T_OWN_CITATIONS, "一手来源");

        WEIGHTS.put(T_QUOTATIONS, 0.12);
        WEIGHTS.put(T_STATISTICS, 0.10);
        WEIGHTS.put(T_CITE_SOURCES, 0.08);
        WEIGHTS.put(T_FLUENCY, 0.07);
        WEIGHTS.put(T_TECHNICAL_TERMS, 0.05);
        WEIGHTS.put(T_EASY_TO_UNDERSTAND, 0.05);
        WEIGHTS.put(T_AUTHORITATIVE, 0.04);
        WEIGHTS.put(T_UNIQUE_WORDS, 0.02);
        // 2026 新增：答案前置 / 事实密度 / 结构化数据 权重最高
        WEIGHTS.put(T_ANSWER_FIRST, 0.10);
        WEIGHTS.put(T_FACT_DENSITY, 0.10);
        WEIGHTS.put(T_STRUCTURED_DATA, 0.09);
        WEIGHTS.put(T_EEAT, 0.07);
        WEIGHTS.put(T_KEY_QUOTE, 0.04);
        WEIGHTS.put(T_FRESHNESS, 0.04);
        WEIGHTS.put(T_OWN_CITATIONS, 0.03);
    }

    // ---------- 启发式模式 ----------

    /** 统计：数字 + 常见单位/量词 */
    private static final Pattern STAT_PATTERN = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:%|％|万|亿|倍|家|个|款|种|天|元|人|次|项|年|月|台|件|篇|GB|TB|MB|KB|M|B)");

    /** 引用来源：URL / markdown 链接 / 「来源/参考/报告」字样 */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w.\\-/:?=&%]+|www\\.\\w+[\\w.\\-]*(?::\\d+)?(?:/[\\w.\\-/?=&%]*)?");
    private static final Pattern CITE_HINT_PATTERN = Pattern.compile("来源[:：]|参考[:：]|据[^。；]{0,20}(报告|数据|研究)|数据来源");

    /** 引语：中文引号 / 英文引号 */
    private static final Pattern QUOTE_PATTERN = Pattern.compile("[“”\"\"''「」『』]");

    /** 专家引语：X 表示/指出/认为/强调 */
    private static final Pattern EXPERT_QUOTE_PATTERN = Pattern.compile(
            "[^。；\\n]{0,24}(专家|CEO|创始人|教授|分析师|学者|负责人|院长|主任|研究员)[^。；\\n]{0,12}(表示|指出|认为|强调|称|说|建议|警告|预计)");

    /** 权威信号词 */
    private static final List<String> AUTHORITY_WORDS = Arrays.asList(
            "权威", "官方", "认证", "白皮书", "研究报告", "行业协会", "监管", "标准", "依据",
            "数据显示", "实测", "第三方", "国家级", "工信部", "卫健委");

    /** 内置领域术语表（可按需扩充） */
    private static final List<String> TECHNICAL_TERMS = Arrays.asList(
            "AI", "大模型", "算法", "机器学习", "深度学习", "神经网络", "数据中台", "SaaS", "PaaS", "API",
            "云计算", "数字化", "智能制造", "供应链", "私域", "增长黑客", "转化率", "用户画像", "BI",
            "ERP", "CRM", "开源", "微服务", "分布式", "KPI", "SOP", "闭环", "生态", "算力", "Agent",
            "Prompt", "检索增强", "向量数据库", "多模态", "AIGC", "GEO", "SEO", "AAO");

    /** 常见停用词（用于独特词占比估算） */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "是", "在", "和", "与", "及", "或", "很", "都", "也", "就", "而", "但", "并",
            "一个", "我们", "你们", "他们", "这个", "那个", "这些", "那些", "可以", "能够", "需要", "进行", "以及"));

    // ---------- 主入口 ----------

    /**
     * 校验内容 GEO 健康度（发布时间默认今天，新鲜度按最严口径计分）。
     *
     * @param content  待校验正文
     * @param keywords 目标关键词（逗号分隔），用于关键词密度判定，可为空
     */
    public GeoValidationResult validate(String content, String keywords) {
        return validate(content, keywords, LocalDate.now());
    }

    /**
     * 校验内容 GEO 健康度（GEO v2：九战术 + 2026 新维度）。
     *
     * <p>16 项正向维度加权（九战术 + 2026 新维度，归一化总和 1.0），关键词堆砌密度超阈值时 {@code blocked=true}
     * 且总分折半。新增维度对标 2026 规则：答案前置 200 字、事实密度、JSON-LD 结构化数据、
     * E-E-A-T 信号、关键引语、新鲜度（30/90/365 天三档）、一手来源引用。
     *
     * @param content     待校验正文
     * @param keywords    目标关键词（逗号分隔），可为空
     * @param publishDate 内容发布时间（影响新鲜度评分），可为空（默认今天）
     */
    public GeoValidationResult validate(String content, String keywords, LocalDate publishDate) {
        GeoValidationResult result = new GeoValidationResult();
        if (content == null || content.isBlank()) {
            result.setBlocked(false);
            result.setTotalScore(0);
            result.getRedFlags().add("内容为空，无法评分");
            result.getSuggestions().add("先撰写正文，再进行 GEO 健康度校验");
            for (String code : NAMES.keySet()) {
                result.getTactics().put(code, new TacticScore(code, NAMES.get(code), 0, "空内容"));
            }
            return result;
        }

        String text = content.trim();
        List<String> kwList = splitKeywords(keywords);

        // 九战术
        int statisticsScore = scoreStatistics(text);
        int citeScore = scoreCiteSources(text);
        int quoteScore = scoreQuotations(text);
        int authorityScore = scoreAuthoritative(text);
        int easyScore = scoreEasyToUnderstand(text);
        int fluencyScore = scoreFluency(text);
        int uniqueScore = scoreUniqueWords(text);
        int termScore = scoreTechnicalTerms(text);
        // GEO v2 新维度
        int answerFirstScore = scoreAnswerFirst(text);
        int factDensityScore = scoreFactDensity(text);
        int structuredScore = scoreStructuredData(text);
        int eeatScore = scoreEeat(text);
        int keyQuoteScore = scoreKeyQuote(text);
        int freshnessScore = scoreFreshness(publishDate);
        int ownCitationScore = scoreOwnCitations(text);
        StuffingResult stuffing = scoreKeywordStuffing(text, kwList);

        // 加权总分（不含堆砌负向项）
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(T_STATISTICS, statisticsScore);
        scores.put(T_CITE_SOURCES, citeScore);
        scores.put(T_QUOTATIONS, quoteScore);
        scores.put(T_AUTHORITATIVE, authorityScore);
        scores.put(T_EASY_TO_UNDERSTAND, easyScore);
        scores.put(T_FLUENCY, fluencyScore);
        scores.put(T_UNIQUE_WORDS, uniqueScore);
        scores.put(T_TECHNICAL_TERMS, termScore);
        scores.put(T_ANSWER_FIRST, answerFirstScore);
        scores.put(T_FACT_DENSITY, factDensityScore);
        scores.put(T_STRUCTURED_DATA, structuredScore);
        scores.put(T_EEAT, eeatScore);
        scores.put(T_KEY_QUOTE, keyQuoteScore);
        scores.put(T_FRESHNESS, freshnessScore);
        scores.put(T_OWN_CITATIONS, ownCitationScore);

        double weighted = 0;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            weighted += WEIGHTS.get(e.getKey()) * e.getValue();
            result.getTactics().put(e.getKey(), new TacticScore(e.getKey(), NAMES.get(e.getKey()), e.getValue(), buildDetail(e.getKey(), e.getValue())));
        }

        // 关键词堆砌（唯一负向战术）
        TacticScore stuffingTactic = new TacticScore(T_KEYWORD_STUFFING, NAMES.get(T_KEYWORD_STUFFING),
                stuffing.score, stuffing.detail);
        result.getTactics().put(T_KEYWORD_STUFFING, stuffingTactic);

        if (stuffing.blocked) {
            result.setBlocked(true);
            weighted *= 0.5;
            result.getRedFlags().add(stuffing.detail);
        } else if (stuffing.score < 100) {
            result.getRedFlags().add(stuffing.detail);
        }

        result.setTotalScore((int) Math.round(weighted));
        result.setSuggestions(buildSuggestions(result));
        return result;
    }

    // ---------- 各项评分 ----------

    private int scoreStatistics(String text) {
        int n = count(STAT_PATTERN, text);
        if (n >= 3) return 100;
        if (n == 2) return 70;
        if (n == 1) return 40;
        return 0;
    }

    private int scoreCiteSources(String text) {
        int url = count(URL_PATTERN, text);
        int hint = count(CITE_HINT_PATTERN, text);
        int total = url + hint;
        if (total >= 2) return 100;
        if (total == 1) return 60;
        return 0;
    }

    private int scoreQuotations(String text) {
        int quotes = count(QUOTE_PATTERN, text) / 2; // 引号成对
        int expert = count(EXPERT_QUOTE_PATTERN, text);
        int total = quotes + expert;
        if (total >= 2) return 100;
        if (total == 1) return 70;
        return 0;
    }

    private int scoreAuthoritative(String text) {
        int hits = 0;
        for (String w : AUTHORITY_WORDS) {
            if (text.contains(w)) hits++;
        }
        if (hits >= 3) return 100;
        if (hits == 2) return 70;
        if (hits == 1) return 40;
        return 0;
    }

    private int scoreEasyToUnderstand(String text) {
        int sentenceCount = splitSentences(text).size();
        if (sentenceCount == 0) return 0;
        double avgLen = text.length() * 1.0 / sentenceCount;
        if (avgLen <= 30) return 100;
        if (avgLen <= 50) return 70;
        if (avgLen <= 80) return 40;
        return 10;
    }

    private int scoreFluency(String text) {
        List<String> sentences = splitSentences(text);
        if (sentences.size() < 3) return 60; // 句子太少无法评估方差，给中低分
        double[] lens = sentences.stream().mapToInt(String::length).asDoubleStream().toArray();
        double avg = Arrays.stream(lens).average().orElse(0);
        if (avg <= 0) return 0;
        double variance = 0;
        for (double l : lens) variance += Math.pow(l - avg, 2);
        double cv = Math.sqrt(variance / lens.length) / avg; // 变异系数
        if (cv <= 0.4) return 100;
        if (cv <= 0.7) return 70;
        if (cv <= 1.0) return 40;
        return 10;
    }

    private int scoreUniqueWords(String text) {
        int unique = 0;
        for (String word : text.split("[\\s,，。；;！？!?（）()、:：\"“”'‘’\\[\\]【】]")) {
            String w = word.trim();
            if (w.length() >= 2 && !STOP_WORDS.contains(w)) unique++;
        }
        if (unique >= 8) return 100;
        if (unique >= 4) return 60;
        if (unique >= 2) return 30;
        return 0;
    }

    private int scoreTechnicalTerms(String text) {
        int hits = 0;
        for (String term : TECHNICAL_TERMS) {
            if (text.contains(term)) hits++;
        }
        if (hits >= 3) return 100;
        if (hits == 2) return 70;
        if (hits == 1) return 40;
        return 0;
    }

    /** 关键词堆砌判定：密度 = Σ(关键词出现次数 × 关键词长度) / 正文长度 */
    private StuffingResult scoreKeywordStuffing(String text, List<String> keywords) {
        if (keywords.isEmpty()) {
            return new StuffingResult(100, "未提供目标关键词，跳过堆砌检测", false);
        }
        int totalChars = text.length();
        if (totalChars == 0) {
            return new StuffingResult(100, "空内容", false);
        }
        int keywordChars = 0;
        StringBuilder detail = new StringBuilder();
        for (String kw : keywords) {
            String k = kw.trim();
            if (k.isEmpty()) continue;
            int occurrence = countOccurrences(text, k);
            if (occurrence > 0) {
                keywordChars += occurrence * k.length();
                detail.append(k).append('x').append(occurrence).append(' ');
            }
        }
        double density = keywordChars * 1.0 / totalChars;
        String desc = String.format("关键词密度 %.1f%%（%s）", density * 100, detail.toString().trim());
        // 拦截双条件：密度超阈值 且 关键词占用字符 >= 12（约 3 次完整提及）。
        // 仅单次提及（如 4 字符关键词在短文中密度 3.2%）属正常写作，不判定堆砌。
        if (density > KEYWORD_STUFFING_THRESHOLD && keywordChars >= 12) {
            return new StuffingResult(0, "关键词堆砌：密度 " + String.format("%.1f%%", density * 100)
                    + " 超出阈值 3%，论文实测可见度 −8%~−10%，已拦截", true);
        }
        if (density > KEYWORD_STUFFING_THRESHOLD) {
            return new StuffingResult(50, desc + "，密度略高于 3%（单次提及，暂不拦截），建议自然表述", false);
        }
        if (density > KEYWORD_STUFFING_THRESHOLD / 2) {
            return new StuffingResult(50, desc + "，密度偏高（1.5%~3%），建议自然稀释", false);
        }
        return new StuffingResult(100, desc + "，密度正常", false);
    }

    // ---------- GEO v2 新维度评分（2026 规则） ----------

    /**
     * 答案前置：AI 引擎常只读前 200 字，核心答案必须出现在开头。
     * 命中信号：前 200 字符含结论词（是/为/达到/增长/建议/推荐/提供）或数字或关键词。
     */
    private int scoreAnswerFirst(String text) {
        String head = text.substring(0, Math.min(200, text.length()));
        if (head.length() < 40) return 40; // 短文无法评估前置
        boolean conclusion = Pattern.compile("是|为|达到|增长|建议|推荐|提供|实现|覆盖|支持").matcher(head).find();
        boolean hasNumber = Pattern.compile("\\d").matcher(head).find();
        if (conclusion && hasNumber) return 100;
        if (conclusion || hasNumber) return 70;
        return 30;
    }

    /**
     * 事实密度：每节至少一个可引用事实（数字+名词/名称/日期）。
     * 统计「数字+单位」与「年份/日期」出现密度。
     */
    private int scoreFactDensity(String text) {
        int facts = count(STAT_PATTERN, text) + count(Pattern.compile("\\d{4}年|\\d{4}-\\d{2}"), text);
        if (facts >= 5) return 100;
        if (facts >= 3) return 70;
        if (facts >= 1) return 40;
        return 0;
    }

    /** 结构化数据：JSON-LD / schema.org 标记检测（2026 引用概率 +2.5x） */
    private int scoreStructuredData(String text) {
        if (text.contains("application/ld+json") || text.contains("\"@context\"") || text.contains("@context")) return 100;
        if (text.contains("schema.org") || text.contains("\"@type\"") || text.contains("JSON-LD") || text.contains("jsonld")) return 70;
        return 0;
    }

    /** E-E-A-T：作者署名 / 日期 / 机构 / 资质四类信号 */
    private int scoreEeat(String text) {
        int hits = 0;
        if (Pattern.compile("作者[:：]|撰文|文[:：]|By |byline|作者署名").matcher(text).find()) hits++;
        if (Pattern.compile("\\d{4}年\\d{1,2}月|\\d{4}-\\d{2}-\\d{2}|发布时间|更新于").matcher(text).find()) hits++;
        if (Pattern.compile("公司|研究院|大学|学院|官方|机构|实验室|平台").matcher(text).find()) hits++;
        if (Pattern.compile("认证|博士|教授|专家|资质|证书|高级工程师").matcher(text).find()) hits++;
        if (hits >= 3) return 100;
        if (hits == 2) return 70;
        if (hits == 1) return 40;
        return 0;
    }

    /** 关键引语：blockquote 标记 / 加粗独立引语（AI 视为 key takeaway） */
    private int scoreKeyQuote(String text) {
        if (text.contains(">") || text.contains("**") || text.contains("【核心】") || text.contains("一句话总结")) return 100;
        if (text.contains("核心观点") || text.contains("要点") || text.contains("结论")) return 60;
        return 0;
    }

    /** 新鲜度：内容发布时间距今（30 天内满分，AI 偏好新鲜来源） */
    private int scoreFreshness(LocalDate publishDate) {
        if (publishDate == null) return 50; // 未提供日期按中值
        long days = ChronoUnit.DAYS.between(publishDate, LocalDate.now());
        if (days <= 30) return 100;
        if (days <= 90) return 70;
        if (days <= 365) return 40;
        return 10;
    }

    /** 一手来源引用：外链到一手来源（官方文档/报告/研究） */
    private int scoreOwnCitations(String text) {
        int links = count(URL_PATTERN, text);
        int primaryHint = count(Pattern.compile("(官方|官网|文档|白皮书|研究报告|论文|arXiv|github\\.com|gov\\.|org\\.)"), text);
        if (links >= 2 && primaryHint >= 1) return 100;
        if (links >= 1 && primaryHint >= 1) return 70;
        if (links >= 1) return 40;
        return 0;
    }

    // ---------- 工具方法 ----------

    private List<String> splitKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) return Collections.emptyList();
        return Arrays.stream(keywords.split("[,，、;；]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<String> splitSentences(String text) {
        return Arrays.stream(text.split("[。！？!?；;\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private int count(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private int countOccurrences(String text, String keyword) {
        int idx = 0;
        int n = 0;
        while ((idx = text.indexOf(keyword, idx)) >= 0) {
            n++;
            idx += keyword.length();
        }
        return n;
    }

    private String buildDetail(String code, int score) {
        return switch (score) {
            case 100 -> "充分";
            case 70, 60 -> "基本具备";
            case 40, 30 -> "不足";
            default -> "缺失";
        };
    }

    private List<String> buildSuggestions(GeoValidationResult result) {
        List<String> suggestions = new ArrayList<>();
        TacticScore quotes = result.getTactics().get(T_QUOTATIONS);
        TacticScore stats = result.getTactics().get(T_STATISTICS);
        TacticScore cites = result.getTactics().get(T_CITE_SOURCES);
        if (quotes != null && quotes.getScore() < 70) {
            suggestions.add("加入 1-2 条带署名的专家/客户引语（论文实测最高效 +41%）");
        }
        if (stats != null && stats.getScore() < 70) {
            suggestions.add("用具体数字替换模糊表述，如「成本降低 34%（Forrester, 2025）」");
        }
        if (cites != null && cites.getScore() < 70) {
            suggestions.add("在行内补充可信来源引用（论文实测 +28%，中小品牌最高 +115%）");
        }
        TacticScore stuffing = result.getTactics().get(T_KEYWORD_STUFFING);
        if (stuffing != null && stuffing.getScore() < 100) {
            suggestions.add("降低关键词重复密度，AI 引擎按语义而非词频理解内容");
        }
        // ---- GEO v2 新维度建议 ----
        TacticScore answer = result.getTactics().get(T_ANSWER_FIRST);
        if (answer != null && answer.getScore() < 70) {
            suggestions.add("前 200 字直接给出核心答案（AI 常只读开头，结论前置 + 数字佐证）");
        }
        TacticScore fact = result.getTactics().get(T_FACT_DENSITY);
        if (fact != null && fact.getScore() < 70) {
            suggestions.add("提升事实密度：每节至少 1 个可引用事实（数字/名称/日期，如「覆盖 200+ 企业」）");
        }
        TacticScore schema = result.getTactics().get(T_STRUCTURED_DATA);
        if (schema != null && schema.getScore() < 70) {
            suggestions.add("部署 JSON-LD 结构化数据（Article/FAQPage/Organization，2026 引用概率 +2.5x）");
        }
        TacticScore eeat = result.getTactics().get(T_EEAT);
        if (eeat != null && eeat.getScore() < 70) {
            suggestions.add("补齐 E-E-A-T 信号：作者署名 + 发布日期 + 机构资质（AI 信任度关键）");
        }
        TacticScore fresh = result.getTactics().get(T_FRESHNESS);
        if (fresh != null && fresh.getScore() < 70) {
            suggestions.add("更新内容发布时间（AI 偏好 30 天内的新鲜来源，schema 中补 dateModified）");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("内容 GEO 健康度良好，可保持当前结构");
        }
        return suggestions;
    }

    /** 关键词堆砌评分结果 */
    private static final class StuffingResult {
        final int score;
        final String detail;
        final boolean blocked;

        StuffingResult(int score, String detail, boolean blocked) {
            this.score = score;
            this.detail = detail;
            this.blocked = blocked;
        }
    }
}
