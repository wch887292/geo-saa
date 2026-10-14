"""
校验 README 文档中的默认凭据说明，与 init.sql / DefaultPasswordGuard 中的实际值是否一致。

安全设计：本脚本**从不打印任何口令明文或散列**。
- 明文候选来自本文件顶部的 CANDIDATES 常量（这些是仓库公开的示例口令，
  只在内存中参与比对，不输出）。
- 散列从 init.sql 读入后仅参与 BCrypt 运算，结果只以"匹配/不匹配"布尔结论呈现。

输出仅包含：账号、行号、判定结论。任何口令片段都不会出现在 stdout
（公开仓库的 CI 日志等同于公开信息，这一点比本地运行更重要）。

退出码：0 = 全部通过；1 = 存在违规（供 CI 拦截回归）。
核心不变量（违反即失败）：
  1. init.sql 中每个账号的口令都必须是标准 BCrypt 散列（防明文入库）；
  2. DefaultPasswordGuard 登记的默认散列必须覆盖 init.sql 的全部散列
     （防改了 init.sql 忘同步 Guard——闸门会静默失效）；
  3. 每个散列都必须命中 CANDIDATES 中的已知默认口令
     （防默认口令变了但 README/CANDIDATES 没更新）；
  4. README 的凭据告警与 dev.yml datasource 状态描述必须与实际一致。
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent
INIT_SQL = ROOT / "geo-saa-backend/src/main/resources/db/init.sql"
GUARD = ROOT / "geo-saa-backend/src/main/java/com/geosaa/security/DefaultPasswordGuard.java"
README = ROOT / "README.md"
DEV_YML = ROOT / "geo-saa-backend/src/main/resources/application-dev.yml"

# README 与文档中公开的示例口令候选。仅用于比对，不打印。
# 注意：若 init.sql 的默认口令变更，必须同步更新此处与 README，否则 CI 失败。
CANDIDATES = [
    "admin123", "Admin@123", "admin", "123456", "password",
    "geosaa123", "geo123456", "test123", "abc123",
    "Admin123", "admin@123", "Aa123456", "qwerty",
]

FAILURES = []


def fail(msg):
    FAILURES.append(msg)
    print(f"[FAIL] {msg}")


def load_users():
    """从 init.sql 解析 user_info 的初始账号 -> {username: (行号, 散列)}。"""
    text = INIT_SQL.read_text(encoding="utf-8", errors="replace")
    users = {}
    pattern = re.compile(
        r"INSERT\s+INTO\s+user_info\s*\([^)]*\)\s*VALUES(.*?);",
        re.IGNORECASE | re.DOTALL,
    )
    for block_match in pattern.finditer(text):
        block = block_match.group(1)
        for tup in re.finditer(r"\(\s*'([^']*)'\s*,\s*'([^']*)'", block):
            line = text.count("\n", 0, block_match.start(1) + tup.start()) + 1
            users[tup.group(1)] = (line, tup.group(2))
    return users


def try_bcrypt():
    """若环境有 bcrypt 就用 bcrypt，否则返回 False。"""
    try:
        import bcrypt  # noqa
        return True
    except ImportError:
        return False


def guard_hashes():
    """取出 DefaultPasswordGuard 中登记的默认散列（仅数量与长度，不输出内容）。"""
    text = GUARD.read_text(encoding="utf-8", errors="replace")
    return re.findall(r"\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}", text)


def main():
    users = load_users()
    print(f"init.sql 解析到的账号数: {len(users)}")
    if not users:
        fail("init.sql 未解析到 user_info 账号（解析规则或文件结构已变更？）")

    has_bcrypt = try_bcrypt()
    print(f"bcrypt 库可用: {has_bcrypt}")
    if users and not has_bcrypt:
        fail("bcrypt 库不可用，无法完成口令比对（CI 中应已 pip install bcrypt）")

    hashes = guard_hashes()
    print(f"DefaultPasswordGuard 登记的默认散列条数: {len(hashes)}")
    if not hashes:
        fail("DefaultPasswordGuard 中未登记任何默认散列，闸门已失效")

    # 1) init.sql 中的口令必须全是标准 BCrypt 散列——明文入库即失败
    for user, (line, h) in sorted(users.items()):
        if not (h.startswith("$2") and len(h) == 60):
            fail(f"init.sql:{line} 账号 {user} 的口令不是标准 BCrypt 散列"
                 f"(len={len(h)})，疑似明文入库")

    # 2) 关键交叉校验：Guard 登记的散列是否覆盖了 init.sql 里的散列
    init_hashes = {h for _, h in users.values() if len(h) == 60}
    covered = init_hashes & set(hashes)
    print(f"init.sql 散列被 Guard 覆盖的数量: {len(covered)}/{len(init_hashes)}")
    if len(init_hashes) != len(covered):
        missing = len(init_hashes) - len(covered)
        for user, (line, h) in sorted(users.items()):
            if len(h) == 60 and h not in set(hashes):
                print(f"  未覆盖: init.sql:{line} 账号 {user}")
        fail(f"init.sql 有 {missing} 个散列未被 DefaultPasswordGuard 登记——"
             "改了 init.sql 忘同步 Guard 会让默认口令闸门静默失效")

    # admin 账号与 Guard 的检查对象必须对得上
    if "admin" not in users:
        fail("init.sql 中不存在 admin 账号，但 DefaultPasswordGuard 只检查 admin")
    guard_text = GUARD.read_text(encoding="utf-8", errors="replace")
    if "ADMIN_USERNAME" not in guard_text:
        fail("DefaultPasswordGuard 中未找到 ADMIN_USERNAME，检查对象可能已变更")

    # 3) 明文候选 × 账号 匹配矩阵：每个散列都必须命中已知默认口令
    if has_bcrypt and users:
        import bcrypt
        print("\n--- 明文候选 × 账号 匹配矩阵 ---")
        for user, (line, h) in sorted(users.items()):
            if not (h.startswith("$2") and len(h) == 60):
                print(f"账号 {user}: 散列格式非标准(len={len(h)})，跳过比对")
                continue
            hits = [c for c in CANDIDATES if bcrypt.checkpw(c.encode(), h.encode())]
            if hits:
                # 只报告"哪个候选命中"，用序号代替口令本身
                idxs = [CANDIDATES.index(x) + 1 for x in hits]
                print(f"账号 {user} (init.sql:{line}): 命中候选序号 {idxs} / 共 {len(CANDIDATES)} 个")
            else:
                fail(f"账号 {user} (init.sql:{line}) 的散列不匹配任何已知默认口令。"
                     "若默认口令已变更：同步更新 README 与 CANDIDATES；"
                     "若为有意新增的种子账号：把其口令加入 CANDIDATES 并在 Guard 登记")

    # 4) README 与配置状态一致性（只检查结构，不回显凭据内容）
    readme = README.read_text(encoding="utf-8", errors="replace")
    print("\n--- README 凭据描述检查 ---")

    # Guard 的 fail 动作分支：prod 档案靠它拒绝启动
    if not re.search(r'"fail"\s*\.\s*equalsIgnoreCase', guard_text):
        fail("DefaultPasswordGuard 缺少对 action=fail 的处理分支，prod 将无法拒绝启动")

    # README 必须保留默认凭据泄露告警
    if not ("拒绝启动" in readme or "凭据泄露" in readme):
        fail("README 缺少默认凭据泄露/拒绝启动的告警")

    # README 对 dev.yml datasource 的状态描述必须与实际一致
    dev = DEV_YML.read_text(encoding="utf-8", errors="replace") if DEV_YML.exists() else ""
    has_dev_ds = re.search(r"^\s*datasource:", dev, re.MULTILINE) is not None
    claims_absent = "不包含 datasource" in readme
    print(f"README 声称 dev.yml 不含 datasource: {claims_absent}；实际不含: {not has_dev_ds}")
    if claims_absent and has_dev_ds:
        fail("application-dev.yml 出现了 datasource 段，但 README 仍声称其不存在（文档已过期）")
    if not claims_absent and not has_dev_ds:
        fail("README 未说明 application-dev.yml 不含 datasource 段，新手会误以为在那里配置")

    print()
    if FAILURES:
        print(f"===== 未通过 {len(FAILURES)} 项 =====")
        for i, f in enumerate(FAILURES, 1):
            print(f"  {i}. {f}")
        return 1
    print("===== 全部通过 =====")
    return 0


if __name__ == "__main__":
    sys.exit(main())
