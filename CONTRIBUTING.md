# 贡献指南（Contributing Guide）

感谢你考虑为 **GEO-SaaS 全域 AI 搜索优化平台** 做出贡献！本文档将帮助你快速上手。

## 行为准则

参与本项目即表示你同意遵守我们的 [行为准则](CODE_OF_CONDUCT.md)。请在所有互动中保持尊重与专业。

## 如何开始

### 1. 提 Issue

在动手前，请先搜索是否已有相关的 [Issue](https://github.com/wch887292/geo-saa/issues)。如果没有，请新建 Issue 并尽量提供：

- 清晰的问题标题与复现步骤
- 期望行为与实际行为
- 环境信息（OS / JDK / Node 版本 / 浏览器）
- 相关日志或截图

我们提供了 Issue 模板（Bug 报告 / 功能建议），提交时会自动套用。

### 2. 开发环境搭建

```bash
# 1. Fork 并克隆你的仓库
git clone https://github.com/<your-username>/geo-saa.git
cd geo-saa

# 2. 后端：编译验证
cd geo-saa-backend
mvn clean compile -DskipTests

# 3. 前端：安装依赖并构建验证
cd ../geo-saa-frontend
npm install
npm run build

# 4. 本地一键启动（需 MySQL 8 + Redis 7）
cd ..
.\start.ps1
```

> 不希望配置真实 AI Key？将 `AI_SIMULATION_ENABLED=true` 即可使用内置模拟数据体验全部功能。

### 3. 代码规范

- **后端（Java）**：遵循 Spring Boot 惯例；类与方法添加必要的 Javadoc；禁止在源码中硬编码密钥与密码。
- **前端（Vue 3）**：使用 `<script setup>` 组合式 API；组件命名使用 PascalCase；API 封装统一放在 `src/api/`。
- **提交信息**：采用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：
  - `feat:` 新功能
  - `fix:` 缺陷修复
  - `docs:` 文档
  - `refactor:` 重构
  - `test:` 测试
  - `chore:` 构建/工具链

  示例：`feat(monitor): 新增品牌声量趋势接口`

### 4. 提交 Pull Request

1. 从 `main` 切出特性分支：`git checkout -b feat/your-feature`
2. 提交变更并确保本地编译/构建通过
3. 推送分支并到 GitHub 发起 PR，填写 PR 模板
4. 至少 1 名维护者 Review 通过后合并

## 安全相关

请勿在公开 Issue 中报告安全漏洞。请改为阅读 [SECURITY.md](SECURITY.md) 中的私有报送流程。

## 许可

贡献即表示你同意你的代码以 [MIT License](LICENSE) 发布。
