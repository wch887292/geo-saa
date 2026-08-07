# 安全策略（Security Policy）

## 支持的版本

| 版本 | 是否安全维护 |
|------|--------------|
| v1.0.x (beta) | ✅ 当前维护中 |
| < v1.0 | ❌ 不再维护 |

## 报告漏洞

**请勿在公开的 GitHub Issue 中披露安全漏洞。**

请通过以下私有渠道报送，以便我们在公开前完成修复：

- 在仓库中创建 **私密安全公告（Security Advisory）**：`Security → Report a vulnerability`
- 或发送邮件至维护团队（见 [README](README.md) 联系方式），标题注明 `[SECURITY]`

报送时请尽量包含：

- 漏洞类型与影响范围
- 复现步骤 / PoC
- 可能的影响评估
- 建议的修复方案（如有）

我们会在 **72 小时内** 确认收到，并在确认后协商解决时间表。

## 安全配置提醒

部署本系统时，请务必：

1. **不要提交 `.env` 文件**。仓库已通过 `.gitignore` 忽略它，并提供了 `.env.example` 模板。
2. **生成强随机 `JWT_SECRET`**：
   ```bash
   openssl rand -base64 32
   ```
   禁止使用示例值或短密钥。
3. **`CORS_ALLOWED_ORIGINS` 禁止使用 `*`**，仅放行受信任的前端域名。
4. 生产环境请关闭 `AI_SIMULATION_ENABLED`，并配置真实数据库强密码。
5. 定期更新依赖；本仓库已适配 Dependabot 自动PR。

## 模拟模式说明

开发/演示场景下，可设置 `AI_SIMULATION_ENABLED=true` 以返回内置模拟数据，**无需任何第三方 API Key**。该模式仅用于本地体验，切勿用于生产环境。
