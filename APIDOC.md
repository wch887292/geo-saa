# GEO-SaaS 平台 API 文档

## 基础信息
- Base URL: `/api/v1`
- 认证方式: Bearer Token (JWT)
- 响应格式: `{ code: int, message: string, data: object }`

## 权限接口
### POST /auth/login
登录
- 请求: `{ username, password }`
- 响应: `{ token, username, nickname, role }`

### POST /auth/logout
退出登录
- 请求头: Authorization: Bearer {token}

### GET /auth/menus
获取动态菜单
- 响应: 菜单树形结构

### GET /auth/userinfo
获取当前用户信息

## 品牌诊断接口
### POST /diagnose
发起AI诊断
- 请求: `{ brandName, keywords, platforms, taskType }`
- 响应: `{ taskId }`

### GET /diagnose/progress/{taskId}
获取诊断进度

### GET /diagnose/report/{taskId}
获取诊断报告

### POST /diagnose/competitor
竞品对比
- 请求: `{ brandName, competitors }`

## 知识库接口
### GET /knowledge
获取知识列表（分页）

### POST /knowledge
新增知识
- 请求: `{ brandId, knowledgeType, rawContent, title }`

### PUT /knowledge/{id}
编辑知识

### GET /knowledge/{id}/jsonld
获取JSON-LD格式

### GET /knowledge/{id}/versions
获取版本历史

### POST /knowledge/{id}/rollback
版本回滚

## AI创作接口
### POST /content/batch
批量生成内容
- 请求: `{ industry, direction, keywords, contentType, count }`

### GET /content
获取内容列表（分页）

### GET /content/{id}
获取内容详情

### GET /content/export
导出内容

### GET /content/templates
获取行业模板列表

## 分发接口
### POST /distribute
创建分发任务
- 请求: `{ contentId, channelIds }`

### GET /distribute/channels
获取渠道列表

### GET /distribute/progress/{taskId}
获取任务进度

### POST /distribute/{taskId}/retry
重试失败项

## 数据看板接口
### GET /monitor/stats
获取核心指标
- 参数: `brandId, startDate, endDate`

### GET /monitor/trends
获取趋势数据
- 参数: `brandId, startDate, endDate, type`

### GET /monitor/competitor
竞品对比数据

## 系统配置接口
### GET /system/config
获取配置列表

### POST /system/config
保存配置

### GET /system/logs
获取审计日志（分页）