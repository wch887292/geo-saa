# -*- coding: utf-8 -*-
"""补全 package-lock.json 中缺失的 rollup 平台 optional 包条目（npm#4828 修复）

用法: python fix-lock-rollup.py <package-lock.json 路径>
"""
import json
import sys

lock_path = sys.argv[1] if len(sys.argv) > 1 else "package-lock.json"

PLATFORMS = {
    "rollup-android-arm-eabi": (["android"], ["arm"]),
    "rollup-android-arm64": (["android"], ["arm64"]),
    "rollup-darwin-arm64": (["darwin"], ["arm64"]),
    "rollup-darwin-x64": (["darwin"], ["x64"]),
    "rollup-freebsd-x64": (["freebsd"], ["x64"]),
    "rollup-linux-arm-gnueabihf": (["linux"], ["arm"]),
    "rollup-linux-arm-musleabihf": (["linux"], ["arm"]),
    "rollup-linux-arm64-gnu": (["linux"], ["arm64"]),
    "rollup-linux-arm64-musl": (["linux"], ["arm64"]),
    "rollup-linux-loong64-gnu": (["linux"], ["loong64"]),
    "rollup-linux-ppc64-gnu": (["linux"], ["ppc64"]),
    "rollup-linux-ppc64le-gnu": (["linux"], ["ppc64"]),
    "rollup-linux-riscv64-gnu": (["linux"], ["riscv64"]),
    "rollup-linux-riscv64-musl": (["linux"], ["riscv64"]),
    "rollup-linux-s390x-gnu": (["linux"], ["s390x"]),
    "rollup-linux-x64-gnu": (["linux"], ["x64"]),
    "rollup-linux-x64-musl": (["linux"], ["x64"]),
    "rollup-openbsd-x64": (["openbsd"], ["x64"]),
    "rollup-win32-arm64-msvc": (["win32"], ["arm64"]),
    "rollup-win32-ia32-msvc": (["win32"], ["ia32"]),
    "rollup-win32-x64-gnu": (["win32"], ["x64"]),
    "rollup-win32-x64-msvc": (["win32"], ["x64"]),
}

with open(lock_path, encoding="utf-8") as f:
    lock = json.load(f)
pkgs = lock["packages"]
rollup_ver = pkgs["node_modules/rollup"]["version"]

fixed = 0
for name, (os_list, cpu_list) in PLATFORMS.items():
    key = f"node_modules/@rollup/{name}"
    node = pkgs.get(key)
    # Windows 生成的 lock 只记录本机平台包，其它平台条目缺失 →
    # 在 CI（Linux）上 npm 不装对应原生模块（npm#4828）。补全所有平台条目。
    if not node or "version" not in node:
        pkgs[key] = {
            "version": rollup_ver,
            "dev": True,
            "license": "MIT",
            "optional": True,
            "os": os_list,
            "cpu": cpu_list,
        }
        fixed += 1
        print(f"补全: {name} -> v{rollup_ver} {os_list}/{cpu_list}")

lzma_ver = None
for key in pkgs:
    if key.startswith("node_modules/@napi-rs/lzma-") and "version" in pkgs[key]:
        lzma_ver = pkgs[key]["version"]
        break
for key in list(pkgs):
    if key.startswith("node_modules/@napi-rs/lzma-") and (not pkgs.get(key) or "version" not in pkgs[key]):
        pkgs[key] = {"version": lzma_ver, "dev": True, "license": "MIT", "optional": True}
        fixed += 1
        print(f"补全: {key.split('/')[-1]} -> v{lzma_ver}")

with open(lock_path, "w", encoding="utf-8") as f:
    json.dump(lock, f, ensure_ascii=False, indent=2)
print(f"\n共补全 {fixed} 个平台 optional 条目")
