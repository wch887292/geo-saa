"""
核对各处数据库凭据的声明是否一致。只输出长度与布尔结论，不回显任何值。

安全设计：任何口令明文/散列都不会出现在 stdout
（公开仓库的 CI 日志等同于公开信息，这一点比本地运行更重要）。
违规信息只给出行号与键名，不给值。

退出码：0 = 全部通过；1 = 存在违规（供 CI 拦截回归）。
不变量：
  1. application-prod.yml 的 datasource 必须为纯 ${...} 变量引用（禁明文硬编码）；
  2. application-dev.yml 不得含 datasource 段（README 如此声明，且刻意留空）；
  3. docker-compose.yml 的 MYSQL_PASSWORD 必须无默认值（缺失即 fail-fast）；
  4. .env.example 的 MYSQL_PASSWORD 必须非空（开发默认值，README 依赖它）；
  5. README 声明的开发口令必须与 .env.example 实际值一致；
  6. prod 档案的 default-password-action 默认值必须为 fail；
  7. README / README_EN / FAQ_EN 的凭据描述不得回归到修复前的错误说法。
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent

FAILURES = []


def check(label, ok, hint=""):
    """打印结论并登记违规。ok=False 时记一条失败。"""
    print(f"{label}: {ok}" + (f"  (应为 True)" if not ok and not hint else ""))
    if not ok:
        FAILURES.append(f"{label}{('；' + hint) if hint else ''}")


def masked(path, pattern, label):
    p = ROOT / path
    if not p.exists():
        print(f"{label}: 文件不存在 ({path})")
        FAILURES.append(f"{label}: 文件不存在 ({path})")
        return ""
    text = p.read_text(encoding="utf-8", errors="replace")
    for i, line in enumerate(text.splitlines(), 1):
        m = re.match(pattern, line.strip())
        if m:
            key, val = m.group(1), m.group(2).strip().strip('"\'')
            print(f"{label} [line {i}] {key} = <len={len(val)}>")
    return text


print("=== application-dev.yml ===")
dev_text = masked(
    "geo-saa-backend/src/main/resources/application-dev.yml",
    r"(username|password):\s*(.+)$",
    "dev.yml",
)

print("\n=== application-prod.yml ===")
prod_text = masked(
    "geo-saa-backend/src/main/resources/application-prod.yml",
    r"(username|password):\s*(.+)$",
    "prod.yml",
)

print("\n=== docker-compose.yml 中的变量引用 ===")
dc = (ROOT / "docker-compose.yml").read_text(encoding="utf-8", errors="replace")
for i, line in enumerate(dc.splitlines(), 1):
    if "MYSQL_PASSWORD" in line or "MYSQL_USER" in line:
        for var in re.findall(r"\$\{(MYSQL_\w+)(:-[^}]*)?\}", line):
            name, default = var[0], var[1]
            d = default[2:] if default else None
            if d is None:
                print(f"compose [line {i}] ${{{name}}} -> 无默认值（必须由 .env 提供）")
            else:
                print(f"compose [line {i}] ${{{name}:-<默认值>}} len={len(d)}")

print("\n=== .env.example 是否留空 ===")
env = (ROOT / ".env.example").read_text(encoding="utf-8", errors="replace")
env_values = {}
for name in ("MYSQL_USER", "MYSQL_PASSWORD", "RABBITMQ_PASSWORD", "JWT_SECRET"):
    m = re.search(rf"^\s*{name}=(.*)$", env, re.MULTILINE)
    if m:
        v = m.group(1).strip()
        env_values[name] = v
        print(f"{name}: {'<空，必须填写>' if not v else f'<已填 len={len(v)}>'}")
    else:
        print(f"{name}: <未在.env.example 中定义>")

# ---------------------------------------------------------------
# 断言区：以下任一条不满足，脚本以 1 退出，CI  job 失败
# ---------------------------------------------------------------

# 1) prod.yml datasource 必须是纯 ${...} 引用（防有人把口令硬编码进配置）
prod_ds = re.search(
    r"datasource:\s*\n\s*username:\s*(\S+)\s*\n\s*password:\s*(\S+)", prod_text)
if not prod_ds:
    check("prod.yml 存在 datasource 段", False, "结构变更？请同步本脚本")
else:
    u, p = prod_ds.group(1), prod_ds.group(2)
    check("prod.yml datasource.username 为纯变量引用", bool(re.fullmatch(r"\$\{[^}]+\}", u)),
          f"line 值形如 <len={len(u)}>，疑似硬编码")
    check("prod.yml datasource.password 为纯变量引用", bool(re.fullmatch(r"\$\{[^}]+\}", p)),
          f"line 值形如 <len={len(p)}>，疑似硬编码")

# 2) dev.yml 不得出现 datasource 段（README 声明其刻意留空）
check("application-dev.yml 不含 datasource 段",
      re.search(r"^\s*datasource:", dev_text, re.MULTILINE) is None,
      "若确需新增，请先更新 README「开发模式」的说明")

# 3) compose 的 MYSQL_PASSWORD 必须无 :- 默认值（保留缺失即失败的语义）
bad_defaults = [i for i, line in enumerate(dc.splitlines(), 1)
                if re.search(r"\$\{MYSQL_PASSWORD:-", line)]
check("compose MYSQL_PASSWORD 无 :- 默认值", not bad_defaults,
      f"line {bad_defaults} 出现了默认值，会掩盖 .env 缺失")

# 4) .env.example 的 MYSQL_PASSWORD 必须非空（README 宣称开箱即跑的前提）
check(".env.example MYSQL_PASSWORD 非空", bool(env_values.get("MYSQL_PASSWORD")),
      "空值会让 README 的 root/root 说法再次落空")

# 5) README 声明的开发口令必须与 .env.example 实际值一致
rm = (ROOT / "README.md").read_text(encoding="utf-8", errors="replace")
m = re.search(r"数据库开发凭据:\s*`([^`]+)`\s*/\s*`([^`]+)`", rm)
if m and "MYSQL_PASSWORD" in env_values:
    same = m.group(1).strip() == env_values.get("MYSQL_USER", "").strip() \
        and m.group(2).strip() == env_values["MYSQL_PASSWORD"].strip()
    check("README 开发口令与 .env.example 实际值一致", same,
          f"README len={len(m.group(2))} vs env len={len(env_values['MYSQL_PASSWORD'].strip())}")
else:
    check("README 含「数据库开发凭据」声明且 .env.example 有 MYSQL_PASSWORD", False,
          "文档结构变更？请同步本脚本")

# 6) prod 档案的默认口令动作必须是 fail（拒绝启动），不得退化为 warn/disabled
check("prod 档案 default-password-action 默认为 fail",
      re.search(r"default-password-action:\s*\$\{DEFAULT_PASSWORD_ACTION:fail\}", prod_text)
      is not None, "prod 缺少 fail 默认值，默认口令将不再阻断启动")

# 7) 文档语义检查：不得回归到修复前的错误说法
print("\n=== README 凭据描述一致性（语义检查）===")

# 7.1 不得再宣称无条件的「数据库默认凭据」
check("无条件的『数据库默认凭据』表述已移除",
      re.search(r"数据库默认凭据", rm) is None, "应为「数据库开发凭据」并附适用边界")

# 7.2 开发凭据段落必须同时说明适用边界与 datasource 状态
dev_sec = re.search(r"### 开发模式(.*?)(?=\n## |\n### )", rm, re.DOTALL)
if dev_sec:
    body = dev_sec.group(1)
    check("开发模式段标注了仅本地适用", "只适用于本地开发" in body or "仅供本地" in body)
    check("开发模式段提示了 datasource 不存在", "不包含 datasource" in body)
else:
    check("README 含「开发模式」章节", False, "文档结构变更？请同步本脚本")

# 7.3 FAQ 不得再把用户指向 application-dev.yml 的 datasource（该段不存在）
faq = re.search(r"### Q: 数据库连接失败(.*?)(?=\n### )", rm, re.DOTALL)
if faq:
    check("FAQ 不再误导指向不存在的 dev datasource",
          "application-dev.yml 中的配置" not in faq.group(1))
else:
    check("README FAQ 含「数据库连接失败」", False, "文档结构变更？请同步本脚本")

# 7.4 改密步骤必须明确说明不可跳过
first = re.search(r"### 第 1 步：首次部署必须改密(.*?)```", rm, re.DOTALL)
if first:
    check("改密步骤标明不可跳过", "不做" in first.group(1) and "起不来" in first.group(1))
else:
    check("README 含「首次部署必须改密」章节", False, "文档结构变更？请同步本脚本")

# 7.5 英文文档同样修复过（曾宣称 root/root 并误导改 dev.yml），防静默回归
en = (ROOT / "README_EN.md").read_text(encoding="utf-8", errors="replace")
faq_en = (ROOT / "FAQ_EN.md").read_text(encoding="utf-8", errors="replace")
check("README_EN 开发段标注仅本地适用", "for local development only" in en.lower())
check("README_EN 开发段说明 .env.example 预置", ".env.example" in en)
check("README_EN FAQ 不再误导改 dev.yml datasource",
      re.search(r"root/root`?, or edit", en) is None)
check("FAQ_EN 连接失败排查指向 docker compose ps", "docker compose ps" in faq_en.lower())
check("FAQ_EN 默认凭据说明提到 .env.example", ".env.example" in faq_en)

print()
if FAILURES:
    print(f"===== 未通过 {len(FAILURES)} 项 =====")
    for i, f in enumerate(FAILURES, 1):
        print(f"  {i}. {f}")
    sys.exit(1)
print("===== 全部通过 =====")
sys.exit(0)
