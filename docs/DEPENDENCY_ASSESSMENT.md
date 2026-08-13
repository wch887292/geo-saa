# 前端大版本依赖升级评估（T5）

> 评估对象：GitHub dependabot 针对 `geo-saa-frontend` 的 7 个大版本 PR。
> 结论先行：**不建议直接合并，需开专门分支做一揽子验证**（vue 3.5 联动升级 + vite 8 构建链验证）。

## 当前版本基线（`package.json`）

| 依赖 | 当前（^） | dependabot 目标 | 主版本跨度 |
|------|-----------|-----------------|-----------|
| vite | ^5.0.0 | 8.2.1 | 5 → 8（+3） |
| @vitejs/plugin-vue | ^5.0.0 | 6.0.8 | 5 → 6 |
| vue | ^3.4.0 | （未单独提） | 需联动 3.5+ |
| vue-router | ^4.2.0 | 5.2.0 | 4 → 5 |
| pinia | ^2.1.0 | 4.0.2 | 2 → 4 |
| echarts | ^5.4.0 | 6.1.0 | 5 → 6 |
| vue-echarts | ^6.6.0 | 8.0.1 | 6 → 8 |

## 逐项风险分析

| 依赖 | 破坏性 | 风险点 | 连锁影响 |
|------|--------|--------|----------|
| **vite 8** | 🔴 高 | 基于 rolldown（Rust 打包器）重构，`manualChunks` / 插件机制 / 依赖预构建行为与 rollup 4 差异大；**正是本项目踩过 element-plus 按需摇树坑的区域** | 必须同时升 plugin-vue 6；unplugin-auto-import(v21) / unplugin-vue-components(v32) 兼容性未验证 |
| **plugin-vue 6** | 🟡 中 | 大版本 API 调整，vite 8 仅支持 v6+ | 与 vite 8 绑定，不可单独评估 |
| **vue-router 5** | 🟡 中 | 移除废弃 API；**可能要求 vue 3.5+** | 与 vue 版本联动 |
| **pinia 4** | 🟡 中 | 主版本；pinia 3 起要求 vue ^3.5 | 与 vue 版本联动 |
| **echarts 6** | 🟡 中 | tree-shaking 行为变化、部分 API 清理 | 需配 vue-echarts 8 |
| **vue-echarts 8** | 🟡 中 | 对应 echarts 6；可能要求 vue 3.5+ | 与 echarts/vue 联动 |

**核心判断**：这不是 7 个独立升级，而是 **一次「vue 3.4 → 3.5+ + vite 5→8(rolldown) + 运行库主版本」的整仓前端升级**，任一环节兼容性问题都会导致构建失败或运行时异常。

## 建议执行方案（若决定升级）

1. 开分支 `chore/deps-fe-major`；
2. 一揽子升级：`vue@^3.5` `vite@8` `@vitejs/plugin-vue@6` `vue-router@5` `pinia@4` `echarts@6` `vue-echarts@8`，同步升级 unplugin 系列到兼容版本；
3. 验收标准（缺一不可）：
   - `vite build` 通过；
   - element-plus 按需摇树 chunk 总量**不回退**（当前 ~297KB 基线）；
   - `SMOKE_BASE` 端到端冒烟 15/15；
   - 关键页面手测（dashboard 图表 / 内容创作 / 监测页）；
4. CI 绿灯后合并；期间关闭对应 dependabot PR 避免噪音。

## 暂缓理由（现状可接受）

- 当前 vite 5 + rollup 构建稳定（8.36s），无已知安全告警驱动紧迫升级；
- 大版本收益（性能/新特性）对当前功能无强需求；
- 风险集中在已验证过的高危区（element-plus 按需 + manualChunks + 构建链）。

## 可跟进的小版本项（无风险，随 CI 自动处理）

- `sass`、`axios`、`@element-plus/icons-vue` 等 minor/patch 升级依赖 dependabot 单独 PR 即可。
