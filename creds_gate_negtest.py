"""
凭据门禁的负向自检：证明 verify_creds.py / check_db_creds.py 不是空转的打印器。

在临时副本中制造 5 类真实回归，每类都必须让对应脚本 exit 1；
同时验证失败输出不泄露散列/口令值。任一项不符，本脚本 exit 1。

设计要点：
- 只在临时目录中变异文件，不动工作区/CI 检出；
- 不打印任何口令明文或散列（与受测脚本同样的安全铁律）；
- 变异锚点（旧散列、prod.yml 行、.env.example 行等）若因文档演进而找不到，
  会以明确报错退出——那是提醒维护者同步更新本测试，而不是静默跳过。

本地运行：python creds_gate_negtest.py
CI 运行：creds-guard.yml 中作为最后一步（阳性检查通过后才有意义）。
"""
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent
PY = sys.executable
OLD_HASH = "$2b$10$dcgzsX.fhPkxrBlI2T1Md.K1yV2FARNMIJjBCm.8q1EL0LqVpicOu"

FILES = [
    "verify_creds.py", "check_db_creds.py", "README.md", "README_EN.md",
    "FAQ_EN.md", ".env.example", "docker-compose.yml",
    "geo-saa-backend/src/main/resources/db/init.sql",
    "geo-saa-backend/src/main/resources/application-dev.yml",
    "geo-saa-backend/src/main/resources/application-prod.yml",
    "geo-saa-backend/src/main/java/com/geosaa/security/DefaultPasswordGuard.java",
]

results = []


def report(name, ok, detail=""):
    results.append((name, ok))
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f"  | {detail}" if detail else ""))


def make_base(tmp):
    """把受测文件复制进临时目录，返回副本根路径。"""
    d = pathlib.Path(tempfile.mkdtemp(prefix="creds-gate-neg-", dir=tmp))
    for f in FILES:
        src = ROOT / f
        if not src.exists():
            print(f"FAIL  受测文件缺失: {f}")
            sys.exit(1)
        dst = d / f
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
    return d


def run(d, script):
    r = subprocess.run([PY, script], cwd=d, capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def edit(d, rel, old, new):
    p = d / rel
    t = p.read_text(encoding="utf-8")
    if old not in t:
        print(f"FAIL  变异锚点未找到（{rel}）: {old!r}")
        print("      文档/配置已演进？请同步更新本自检脚本的锚点。")
        sys.exit(1)
    p.write_text(t.replace(old, new), encoding="utf-8")


def main():
    import bcrypt  # T1 需要生成一个"新的"散列来模拟回归

    with tempfile.TemporaryDirectory(prefix="creds-gate-selftest-") as tmp:
        # T0 干净副本：两个脚本都应通过（证明测试副本本身有效）
        d = make_base(tmp)
        c1, _ = run(d, "verify_creds.py")
        c2, _ = run(d, "check_db_creds.py")
        report("T0 干净副本两脚本 exit 0", c1 == 0 and c2 == 0, f"codes={c1},{c2}")

        # T1 init.sql 散列重新生成但 Guard 未同步（本门禁存在的核心理由）
        d = make_base(tmp)
        new_hash = bcrypt.hashpw(b"brand-new-password-xyz", bcrypt.gensalt(10)).decode()
        edit(d, "geo-saa-backend/src/main/resources/db/init.sql", OLD_HASH, new_hash)
        code, out = run(d, "verify_creds.py")
        report("T1 散列变更未同步Guard -> verify_creds exit 1", code == 1)
        report("T1 失败信息点名'未被 Guard 登记'", "未被 DefaultPasswordGuard 登记" in out)
        report("T1 输出不泄露新散列", new_hash not in out)

        # T2 README 删除默认凭据告警
        d = make_base(tmp)
        p = d / "README.md"
        p.write_text(p.read_text(encoding="utf-8")
                     .replace("拒绝启动", "").replace("凭据泄露", ""), encoding="utf-8")
        code, _ = run(d, "verify_creds.py")
        report("T2 README删告警 -> verify_creds exit 1", code == 1)

        # T3 prod.yml datasource 硬编码口令
        d = make_base(tmp)
        edit(d, "geo-saa-backend/src/main/resources/application-prod.yml",
             "password: ${MYSQL_PASSWORD}", "password: HardcodedDevPass123")
        code, out = run(d, "check_db_creds.py")
        report("T3 prod.yml硬编码口令 -> check_db_creds exit 1", code == 1)
        report("T3 输出不泄露硬编码值", "HardcodedDevPass123" not in out)

        # T4 .env.example 的 MYSQL_PASSWORD 被清空
        d = make_base(tmp)
        edit(d, ".env.example", "MYSQL_PASSWORD=root", "MYSQL_PASSWORD=")
        code, _ = run(d, "check_db_creds.py")
        report("T4 .env.example密码清空 -> check_db_creds exit 1", code == 1)

        # T5 README_EN FAQ 回归到修复前的错误说法
        d = make_base(tmp)
        edit(d, "README_EN.md", "A: First confirm the MySQL container is up",
             "A: Ensure MySQL is running, credentials `root/root`, or edit `application-dev.yml`.")
        code, _ = run(d, "check_db_creds.py")
        report("T5 README_EN FAQ回归 -> check_db_creds exit 1", code == 1)

    print()
    failed = [n for n, ok in results if not ok]
    if failed:
        print(f"===== 未通过 {len(failed)}/{len(results)} 项：门禁脚本可能已被改空转 =====")
        return 1
    print(f"===== 全部 {len(results)} 项通过：门禁真实有效 =====")
    return 0


if __name__ == "__main__":
    sys.exit(main())
