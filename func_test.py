#!/usr/bin/env python3
"""GEO-SaaS 深度功能验证：写操作 + AI 生成 + 合规拦截 + 数据落库校验"""
import json, os, urllib.request, urllib.parse, urllib.error, time

BASE = os.environ.get("SMOKE_BASE", "http://localhost:8080")
results = []

def req(method, path, params=None, token=None, body=None):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = None
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=90) as resp:
            return resp.getcode(), resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return -1, str(e)[:200]

def j(text):
    try:
        return json.loads(text)
    except Exception:
        return None

def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print("%s %-46s %s" % ("PASS" if cond else "FAIL", name, detail))

print("=" * 100)
print("GEO-SaaS 深度功能验证  BASE=%s" % BASE)
print("=" * 100)

# ---------- 1. 登录 ----------
code, text = req("POST", "/api/v1/auth/login", body={"username": "admin", "password": "admin123"})
lj = j(text)
token = (lj or {}).get("data", {}).get("token")
check("1.1 登录 admin/admin123", code == 200 and token, "token=%s" % ("已获取" if token else "无"))
if not token:
    sys.exit(1)

# ---------- 2. 新增品牌（写库） ----------
brand_body = {
    "brandName": "落地测试品牌Alpha",
    "brandCode": "TEST_ALPHA",
    "industry": "科技",
    "brandDescription": "用于验证 GEO-SaaS 写链路的测试品牌，主营工业自动化控制系统。",
    "website": "https://example.com",
}
code, text = req("POST", "/api/v1/knowledge/brands", token=token, body=brand_body)
bj = j(text)
brand_id = None
if isinstance(bj, dict) and isinstance(bj.get("data"), dict):
    brand_id = bj["data"].get("id")
check("2.1 新增品牌(POST)", code == 200 and (bj or {}).get("code") == 200,
      "id=%s" % brand_id)

# 幂等校验：重复新增同名品牌应被拒绝或去重
code2, text2 = req("POST", "/api/v1/knowledge/brands", token=token, body=brand_body)
j2 = j(text2)
dup_rejected = not (code2 == 200 and (j2 or {}).get("code") == 200)
check("2.2 重复品牌被拦截(唯一约束生效)", dup_rejected,
      "code=%s msg=%s" % (code2, (j2 or {}).get("message", "")[:40]))

# ---------- 3. GEO 九战术校验 ----------
good_content = (
    "根据我们服务的 37 家制造企业实测数据，采用 structured knowledge base 的企业，"
    "其品牌在 AI 回答中的被引用率平均提升 42%。"
    "「工业自动化选型」问题中，被推荐概率从 18% 上升至 57%。"
    "这一差异在 2025 年的 12 次独立评测中得到一致验证。"
    "我们认为，可核验的结构化事实比营销软文更能获得模型采信。"
)
bad_content = "我们公司是最好的最好的最好的最好的最好的 SEO 公司，SEO 服务，SEO 优化，SEO 排名"
code, text = req("POST", "/api/v1/content/geo-validate", token=token,
                 body={"content": good_content, "keywords": "工业自动化,知识库"})
gj = j(text)
score = ((gj or {}).get("data") or {}).get("totalScore")
check("3.1 GEO九战术校验-优质内容", code == 200 and score is not None, "totalScore=%s" % score)

code, text = req("POST", "/api/v1/content/geo-validate", token=token,
                 body={"content": bad_content, "keywords": "SEO"})
sj = j(text)
sd = (sj or {}).get("data") or {}
blocked = sd.get("blocked")
check("3.2 GEO九战术校验-堆砌拦截", bool(blocked) or code == 400,
      "blocked=%s code=%s" % (blocked, code))

# ---------- 4. 创建内容 + AI 生成 ----------
content_body = {
    "title": "工业自动化选型指南（GEO测试）",
    "contentType": "ARTICLE",
    "industry": "科技",
    "keywords": "工业自动化,知识库,AI搜索",
    "brandId": brand_id,
}
code, text = req("POST", "/api/v1/content/create", token=token, body=content_body)
cj = j(text)
content_id = None
if isinstance(cj, dict) and isinstance(cj.get("data"), dict):
    content_id = cj["data"].get("id")
check("4.1 创建内容(POST)", code == 200 and (cj or {}).get("code") == 200,
      "id=%s" % content_id)

if content_id:
    t0 = time.time()
    code, text = req("POST", "/api/v1/content/generate/%s" % content_id, token=token)
    gen = j(text)
    gd = (gen or {}).get("data") or {}
    gen_len = len(gd.get("content") or "") if isinstance(gd, dict) else 0
    check("4.2 AI生成内容(模拟模式)", code == 200 and gen_len > 0,
          "%d字 耗时%.1fs simulated=%s" % (gen_len, time.time() - t0,
                                          gd.get("simulated") if isinstance(gd, dict) else "?"))

# ---------- 5. AAO 就绪度评估 ----------
aao_body = {
    "domain": "https://example.com",
    "hasLlmsTxt": True,
    "hasMcpCard": False,
    "hasAgentJson": False,
    "allowAiCrawlers": True,
}
code, text = req("POST", "/api/v1/geo/aao-validate", token=token, body=aao_body)
aj = j(text)
ax = ((aj or {}).get("data") or {}).get("axScore")
check("5.1 AAO就绪度评估", code == 200 and ax is not None,
      "axScore=%s grade=%s" % (ax, ((aj or {}).get("data") or {}).get("grade")))

# ---------- 6. llms.txt / agent.json 生成 ----------
code, text = req("GET", "/api/v1/geo/llms-txt", token=token,
                 params={"brandName": "测试品牌", "siteUrl": "https://example.com",
                         "description": "工业自动化控制系统供应商",
                         "pages": "首页: https://example.com/,产品: https://example.com/p"})
lj2 = j(text)
check("6.1 生成 llms.txt", code == 200 and (lj2 or {}).get("code") == 200,
      "%d字节" % len(text))

code, text = req("GET", "/api/v1/geo/agent-json", token=token,
                 params={"brandName": "测试品牌", "siteUrl": "https://example.com",
                         "description": "工业自动化"})
check("6.2 生成 agent.json", code == 200, "%d字节" % len(text))

# ---------- 7. AI 品牌诊断 ----------
diag_body = {
    "taskName": "测试品牌诊断",
    "taskType": "BRAND",
    "brandName": "落地测试品牌Alpha",
    "inputParams": "{}",
}
code, text = req("POST", "/api/v1/diagnose/create", token=token, body=diag_body)
dj = j(text)
check("7.1 发起AI诊断", code == 200 and (dj or {}).get("code") == 200,
      "msg=%s" % (dj or {}).get("message", "")[:40])

# ---------- 8. 合规检测（敏感词） ----------
code, text = req("POST", "/api/v1/content/check-compliance", token=token,
                 body={"content": "这是正常内容，没有任何违规。"})
check("8.1 敏感词合规检测", code == 200, "")

# ---------- 9. 数据真实性闸门 ----------
code, text = req("GET", "/api/v1/monitor/core-metrics", token=token,
                 params={"brandName": "落地测试品牌Alpha"})
mj = j(text)
md = (mj or {}).get("data") or {}
check("9.1 数据真实性闸门(hasData/simulated 字段)", "hasData" in md,
      "hasData=%s simulated=%s" % (md.get("hasData"), md.get("simulated")))

# ---------- 10. 写操作落库验证 ----------
def page_records(payload):
    """兼容两种返回：裸 list 或 {records:[...]} 分页结构"""
    d = (payload or {}).get("data")
    if isinstance(d, list):
        return d
    if isinstance(d, dict):
        return d.get("records") or []
    return []

code, text = req("GET", "/api/v1/knowledge/brands", token=token,
                 params={"pageNum": 1, "pageSize": 20})
bj2 = j(text)
recs = page_records(bj2)
found = any(r.get("brandName") == "落地测试品牌Alpha" for r in recs)
raw_shape = "裸数组(list)" if isinstance((bj2 or {}).get("data"), list) else "分页对象({records})"
check("10.1 品牌数据已落库(回读)", found, "共%d条, 返回结构=%s" % (len(recs), raw_shape))

code, text = req("GET", "/api/v1/asset/overview", token=token)
oj = j(text)
od = (oj or {}).get("data") or {}
check("10.2 资产总览已统计到新数据",
      (od.get("knowledgeTotal", 0) or 0) >= 1 or (od.get("totalAssets", 0) or 0) >= 1,
      "knowledgeTotal=%s totalAssets=%s brandTotal=%s" % (od.get("knowledgeTotal"), od.get("totalAssets"), od.get("brandTotal")))
check("10.3 brandTotal 字段已返回品牌数",
      od.get("brandTotal") is not None and od.get("brandTotal") >= 1,
      "brandTotal=%s" % od.get("brandTotal"))

# ---------- 11. 鉴权边界 ----------
code, text = req("GET", "/api/v1/content/list", params={"pageNum": 1, "pageSize": 5})
check("11.1 未带token访问被拒(401)", code == 401, "code=%s" % code)

code, text = req("GET", "/api/v1/content/list", token="invalid.token.here",
                 params={"pageNum": 1, "pageSize": 5})
check("11.2 非法token被拒(401)", code == 401, "code=%s" % code)

# ---------- 汇总 ----------
print("=" * 100)
passed = sum(1 for _, ok, _ in results if ok)
failed = len(results) - passed
print("总计: %d 通过 / %d 失败（共 %d 项）" % (passed, failed, len(results)))
if failed:
    print("\n失败项:")
    for n, ok, d in results:
        if not ok:
            print("  - %s  %s" % (n, d))
print("=" * 100)