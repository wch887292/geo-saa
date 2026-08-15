# @wch-klzx/geo-engine · npm 发布手册

> 目标：把 GEO/AAO 引擎（`geo-engine/`）发布到 npm，供品牌站点 / CI 内容门禁 / GEO 审计脚本复用。
> 发布自动化已就绪：打 `v*` tag 即触发 GitHub Actions 自动 publish。

## 一、前置（一次性）

1. **注册 npm 账号**：https://www.npmjs.com/signup
2. **创建 Granular Access Token**：npmjs.com → Access Tokens → Generate New Token
   - 类型选 **Granular Access Token**，权限勾选 **Read and write**，仅授权给 `@wch-klzx/geo-engine` 包（最小权限）
3. **把 token 存入仓库 Secrets**：
   - GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret
   - Name: `NPM_TOKEN`，Value: 粘贴 token（只写一次，GitHub 加密存储）
4. **（可选）验证包名可用**：`npm view @wch-klzx/geo-engine` 应报 404（未发布过）

## 二、发布流程（每次发版）

```bash
# 1. 版本号自增（会自动打 git tag 并触发 CI 发布）
cd geo-engine
npm version patch        # 或 minor / major，例如 0.1.0 -> 0.1.1

# 2. 推送 tag（触发 .github/workflows/npm-publish.yml）
git push origin main --tags
```

CI 自动执行：`npm test`（8 用例）→ `npm publish --access public`。

## 三、本地手动发布（备选，需本机 npm 登录）

```bash
cd geo-engine
npm login                 # 本机 npm 账号
npm publish --access public
```

## 四、验证

```bash
npm view @wch-klzx/geo-engine          # 版本 / 描述 / 文件清单
npm install @wch-klzx/geo-engine       # 在任意项目验证可安装
node -e "import('@wch-klzx/geo-engine').then(m => console.log(m.validateGeo('测试内容 100 家客户','品牌').totalScore))"
```

## 五、包内容（files 白名单）

| 文件 | 说明 |
|------|------|
| `index.js` | 入口，聚合导出 |
| `lib/geo-validator.js` | GEO 校验（九战术 + 2026 v2 维度 + 堆砌拦截） |
| `lib/aao-engine.js` | AAO AX Score + llms.txt / agent.json 生成 |
| `README.md` / `LICENSE` | 文档与 MIT 许可 |

`test/` 与构建临时文件不会发布（`.npmignore` + `files` 双保险）。

## 六、注意

- **版本语义**：`0.1.0` 起按 semver；破坏性变更升 minor/major。
- **CI 发布**：Workflow 使用 `secrets.NPM_TOKEN`；token 泄露请立即在 npmjs.com 撤销并重建。
- **镜像源**：`publishConfig.registry` 已固定为 `https://registry.npmjs.org/`，不受本机源配置影响。
