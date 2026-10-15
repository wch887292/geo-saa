# AI 大模型底层架构设计文档

## 一、架构概述

GEO-SaaS 平台构建了统一的 AI 大模型底层架构，使所有业务服务（内容生成、知识问答、AI诊断、RAG检索、GEO优化等）都基于可配置的 AI 大模型来服务。用户可在「大模型设置」页面自主配置和管理自己的 AI 模型，包括 API Key、模型名称、基础 URL、推理参数等。

### 核心设计目标

1. **模型可插拔**：支持多种大模型提供商，统一 OpenAI 兼容协议
2. **配置自定义**：用户自主管理 API Key、URL、模型ID、温度等参数
3. **调用可观测**：完整的调用日志、Token 消耗、费用统计、耗时监控
4. **故障可降级**：主模型失败时自动按优先级切换备用模型
5. **多租户隔离**：模型配置按租户隔离，数据安全

---

## 二、系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3 + Element Plus)                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ 大模型设置页  │  │ AI调用日志页  │  │  各业务模块(内容/诊断等)│  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
└─────────┼───────────────────┼───────────────────────┼──────────────┘
          │                   │                       │
┌─────────▼───────────────────▼───────────────────────▼──────────────┐
│                        后端 API 层 (Spring Boot)                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────────┐  │
│  │ AiModelController│  │AiCallLogController│  │  业务Controller    │  │
│  │  (模型CRUD/测试) │  │  (日志查询/统计)  │  │  (内容/诊断/RAG等) │  │
│  └────────┬────────┘  └────────┬────────┘  └─────────┬──────────┘  │
└───────────┼──────────────────────┼───────────────────────┼─────────────┘
            │                      │                       │
┌───────────▼──────────────────────▼───────────────────────▼─────────────┐
│                        服务层 (Service)                                    │
│  ┌────────────────────┐  ┌────────────────────┐  ┌──────────────────┐  │
│  │ AiModelConfigService│  │ AiCallLogService    │  │  AiChatService   │  │
│  │ (模型配置管理)       │  │ (调用日志记录/统计)  │  │ (统一AI对话核心)  │  │
│  └─────────┬──────────┘  └─────────┬──────────┘  └────────┬─────────┘  │
│            │                         │                        │            │
│            │           ┌─────────────▼──────────────┐         │            │
│            │           │    AiPromptTemplateService   │         │            │
│            │           │    (Prompt模板管理/渲染)      │         │            │
│            │           └──────────────────────────────┘         │            │
└────────────┼─────────────────────────────────────────────────────┼────────────┘
             │                                                     │
┌────────────▼─────────────────────────────────────────────────────▼────────────┐
│                        数据层 (MySQL + MyBatis-Plus)                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐   │
│  │  ai_model_config  │  │   ai_call_log    │  │   ai_prompt_template     │   │
│  │  (AI模型配置表)    │  │  (AI调用日志表)   │  │   (Prompt模板表)         │   │
│  └──────────────────┘  └──────────────────┘  └──────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        外部 AI 大模型提供商                                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │  OpenAI  │ │ DeepSeek │ │ 通义千问  │ │   豆包    │ │  Claude  │ │ Ollama │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘ └────────┘ │
│                        (统一 OpenAI 兼容协议 /chat/completions)                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 三、核心模块说明

### 3.1 模型配置管理 (AiModelConfigService)

**功能**：用户自定义大模型配置的增删改查、设置默认模型、测试连接

**核心特性**：
- 支持 7 种提供商：OpenAI、DeepSeek、通义千问、豆包、Claude、Ollama(本地)、自定义(OpenAI兼容)
- 可配置参数：API Key、API URL、模型ID、温度、最大Token、top_p、超时、优先级
- API Key 掩码显示（前4后4），编辑时保留原密钥
- 默认模型唯一约束，设置新默认时自动清除旧默认
- 按租户隔离，超级管理员可查看所有租户

**数据库表 `ai_model_config`**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| tenant_id | BIGINT | 租户ID |
| model_name | VARCHAR(100) | 模型名称（用户自定义） |
| provider | VARCHAR(50) | 提供商 |
| api_key | VARCHAR(500) | API Key |
| api_url | VARCHAR(500) | API 基础URL |
| model_id | VARCHAR(100) | 模型ID |
| temperature | DECIMAL(3,2) | 温度参数 0-2 |
| max_tokens | INT | 最大生成token数 |
| top_p | DECIMAL(3,2) | top_p采样参数 |
| timeout_seconds | INT | 超时时间（秒） |
| is_default | TINYINT | 是否默认模型 |
| status | VARCHAR(20) | 状态：active/inactive |
| priority | INT | 优先级（降级切换用） |

### 3.2 统一 AI 对话服务 (AiChatService)

**功能**：所有业务模块 AI 调用的统一入口，封装模型选择、请求构建、API调用、响应解析、日志记录、费用统计

**核心特性**：

1. **多模型支持**：统一 OpenAI 兼容协议（`/chat/completions`），支持所有 OpenAI 兼容的大模型
2. **自动模型选择**：按 modelConfigId 指定，或使用默认模型
3. **智能降级**：主模型调用失败时，按优先级自动切换到其他可用模型
4. **重试机制**：指数退避重试（最多2次，初始间隔1秒）
5. **模拟模式**：API Key 为空或 provider=mock 时返回模拟响应，便于开发测试
6. **调用日志**：自动记录每次调用的 prompt、响应、token、费用、耗时、状态
7. **费用估算**：按模型类型估算调用费用（支持 GPT-4o、DeepSeek、通义、豆包、Claude 等）
8. **多轮对话**：支持 messages 数组，实现多轮上下文对话
9. **参数覆盖**：请求参数可覆盖模型配置的默认参数（温度、最大Token等）

**调用流程**：

```
业务模块调用 AiChatService.chat(request)
    │
    ├─ 1. 解析模型配置（指定ID or 默认模型）
    │
    ├─ 2. 构建消息列表（system + 历史 + user）
    │
    ├─ 3. 带降级调用 callWithFallback()
    │       │
    │       ├─ 尝试主模型（带重试 callWithRetry）
    │       │       │
    │       │       ├─ 模拟模式？→ 返回模拟响应
    │       │       └─ 真实模式？→ 调用 OpenAI 兼容 API
    │       │
    │       └─ 主模型失败？→ 按优先级遍历其他可用模型
    │
    ├─ 4. 解析响应（提取 content、usage、finish_reason）
    │
    ├─ 5. 估算费用（按模型单价 × token数）
    │
    └─ 6. 记录调用日志（异步写入 ai_call_log）
```

### 3.3 调用日志服务 (AiCallLogService)

**功能**：记录和查询 AI 调用历史，提供调用统计分析

**核心特性**：
- 完整记录：prompt、响应、token消耗、费用、耗时、状态、错误信息
- 多维度筛选：按模型、业务模块、状态、时间范围筛选
- 统计分析：总调用次数、成功率、总Token消耗、总费用、平均耗时
- 链路追踪：每次调用生成唯一 requestId，可关联业务请求

**数据库表 `ai_call_log`**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| tenant_id | BIGINT | 租户ID |
| user_id | BIGINT | 调用用户ID |
| model_config_id | BIGINT | 使用的模型配置ID |
| model_name | VARCHAR(100) | 模型名称快照 |
| provider | VARCHAR(50) | 提供商快照 |
| model_id | VARCHAR(100) | 模型ID快照 |
| business_module | VARCHAR(50) | 业务模块 |
| business_type | VARCHAR(50) | 业务类型 |
| prompt_text | TEXT | 请求prompt（截断2000字符） |
| response_text | TEXT | 响应内容（截断3000字符） |
| prompt_tokens | INT | 输入token数 |
| completion_tokens | INT | 输出token数 |
| total_tokens | INT | 总token数 |
| cost_amount | DECIMAL(10,6) | 估算费用（美元） |
| latency_ms | INT | 耗时（毫秒） |
| status | VARCHAR(20) | 状态：success/failed/timeout |
| error_message | VARCHAR(1000) | 错误信息 |
| request_id | VARCHAR(100) | 请求ID（链路追踪） |

### 3.4 Prompt 模板服务 (AiPromptTemplateService)

**功能**：管理和渲染 AI Prompt 模板，支持变量占位符替换

**核心特性**：
- 模板版本管理：支持多版本，按 version 排序取最新
- 变量渲染：支持 `{{变量名}}` 占位符，运行时替换
- 系统提示词 + 用户提示词分离
- 模板参数覆盖：模板可指定温度、最大Token，覆盖模型默认值
- 预置 5 个默认模板：内容生成、AI诊断、知识问答、RAG检索、GEO优化

---

## 四、API 接口清单

### 4.1 模型管理 API (`/api/v1/ai/model`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/page` | 分页查询模型列表 |
| GET | `/available` | 获取所有可用模型 |
| GET | `/{id}` | 获取模型详情 |
| GET | `/default` | 获取默认模型 |
| POST | `/` | 新增模型 |
| PUT | `/` | 更新模型 |
| DELETE | `/{id}` | 删除模型 |
| PUT | `/{id}/default` | 设置默认模型 |
| POST | `/{id}/test` | 测试模型连接 |

### 4.2 AI 对话 API (`/api/v1/ai/chat`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | AI对话（统一入口，支持多轮对话） |
| POST | `/generate` | 简单文本生成 |

**对话请求体**：

```json
{
  "modelConfigId": 1,
  "messages": [
    {"role": "system", "content": "你是一个GEO优化专家"},
    {"role": "user", "content": "什么是GEO？"}
  ],
  "systemPrompt": "可选，优先于messages中的system",
  "userInput": "简单模式，自动构建messages",
  "temperature": 0.7,
  "maxTokens": 2048,
  "stream": false,
  "businessModule": "content",
  "businessType": "generate"
}
```

**对话响应体**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": "GEO（Generative Engine Optimization）是...",
    "modelName": "我的GPT-4o",
    "modelId": "gpt-4o",
    "provider": "openai",
    "promptTokens": 50,
    "completionTokens": 120,
    "totalTokens": 170,
    "costAmount": 0.002100,
    "latencyMs": 1520,
    "callLogId": 123,
    "requestId": "a1b2c3d4e5f6",
    "success": true,
    "finishReason": "stop"
  }
}
```

### 4.3 调用日志 API (`/api/v1/ai/call-log`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/page` | 分页查询调用日志 |
| GET | `/statistics` | 调用统计 |

### 4.4 Prompt 模板 API (`/api/v1/ai/prompt-template`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/page` | 分页查询模板 |
| GET | `/code/{code}` | 根据编码获取模板 |
| POST | `/render/{code}` | 渲染模板 |
| POST | `/` | 新增模板 |
| PUT | `/` | 更新模板 |
| DELETE | `/{id}` | 删除模板 |

---

## 五、前端页面

### 5.1 大模型设置页面 (`/system/model-config`)

**功能**：
- 模型列表表格：名称、提供商、模型ID、API地址、温度、最大Token、状态、操作
- 新增/编辑对话框：模型名称、提供商（下拉选择自动填充默认URL和模型ID）、API Key、API URL、模型ID、温度、最大Token、超时、优先级、状态、设为默认、备注
- 操作按钮：测试连接、设默认、编辑、删除
- 提供商标签颜色区分：OpenAI(蓝)、DeepSeek(绿)、通义(橙)、豆包(红)、Claude(灰)、Ollama(灰)、自定义(蓝)

### 5.2 AI 调用日志页面 (`/system/ai-call-log`)

**功能**：
- 顶部统计卡片：总调用次数、成功率、总Token消耗、估算费用
- 时间范围筛选：日期选择器
- 日志列表表格：时间、模型、提供商、业务模块、类型、Token、耗时、费用、状态、详情
- 筛选条件：模型名称、业务模块、状态
- 详情对话框：完整调用信息、请求Prompt、响应内容、错误信息

---

## 六、调用治理机制

### 6.1 故障降级

当主模型调用失败时，系统自动按 `priority` 字段降序遍历其他 `active` 状态的模型，尝试调用。降级成功后在响应中标记降级来源。

**配置示例**：
- 模型A（GPT-4o）：priority=100，is_default=1
- 模型B（DeepSeek）：priority=50
- 模型C（通义千问）：priority=10

调用顺序：A → B → C

### 6.2 重试机制

每个模型调用失败时，自动指数退避重试最多 2 次：
- 第1次重试：等待 1 秒
- 第2次重试：等待 2 秒

### 6.3 超时控制

每个模型配置可独立设置 `timeout_seconds`（默认 60 秒），通过 RestTemplate 超时配置实现。

### 6.4 模拟模式

当模型的 API Key 为空，或 provider 为 `mock` 时，进入模拟模式，返回预设的模拟响应，不调用真实 API。便于开发测试和演示。

---

## 七、多租户隔离

- 所有模型配置、调用日志、Prompt模板都带有 `tenant_id` 字段
- 通过 `TenantContext` 线程上下文 + JWT 解析租户
- MyBatis-Plus `TenantLineInnerInterceptor` 自动追加租户过滤条件
- 超级管理员（SUPER_ADMIN）可跳过租户过滤，查看所有租户数据

---

## 八、与现有系统集成

### 8.1 业务模块接入方式

各业务模块（内容生成、知识问答、AI诊断等）通过注入 `AiChatService` 调用 AI：

```java
@Service
@RequiredArgsConstructor
public class ContentService {

    private final AiChatService aiChatService;

    public String generateArticle(String topic, String brand) {
        return aiChatService.generateText(
            "你是一个GEO内容创作专家",
            "请为品牌" + brand + "生成关于" + topic + "的文章",
            "content",
            "generate"
        );
    }
}
```

### 8.2 与旧 AiAdapter 的关系

- 旧的 `com.geosaa.ai` 包（AiAdapter/OpenAiAdapter）保留兼容，可逐步迁移
- 新架构统一通过 `AiChatService` 调用，支持用户自定义模型
- 后续可将旧的 `AiAdapterFactory` 重构为基于 `AiChatService` 的实现

---

## 九、数据库迁移

迁移脚本：`V5__ai_model_architecture.sql`

创建 3 张新表 + 初始化 5 个默认 Prompt 模板：
1. `ai_model_config` - AI模型配置表
2. `ai_call_log` - AI调用日志表
3. `ai_prompt_template` - Prompt模板表（预置内容生成、AI诊断、知识问答、RAG检索、GEO优化 5 个模板）

---

## 十、后续扩展方向

1. **流式输出 (SSE)**：支持 Server-Sent Events 流式响应，提升用户体验
2. **函数调用 (Function Calling)**：支持 AI 调用外部工具/函数，实现 Agent 能力
3. **Embedding 服务**：统一文本向量化接口，服务于 RAG 检索
4. **限流熔断**：按模型/租户/用户维度限流，集成 Sentinel 熔断
5. **结果缓存**：相同 prompt 缓存结果，降低调用成本
6. **模型用量配额**：按租户/用户设置月度调用配额
7. **AI 内容审核**：调用前后自动做内容合规检测
8. **本地大模型集成**：深度集成 Ollama/vLLM，支持私有化部署
