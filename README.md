# Agent Runtime 项目说明

这是一个面向 `MCP + Skill + Model` 的统一智能体平台。

当前系统已经从“每个用户自己安装一套资源”调整为“管理员维护全局资源，普通用户按需启用”的平台模式：

- 管理员负责搜索、安装、配置全局 `MCP Server / Agent Skill / 模型档案`
- 普通用户可以查看全量可用模块，并按需启用自己要使用的能力和模型
- 能力说明文档统一绑定到全局资源，便于复用、搜索、按需读取和上下文压缩

## 当前架构

### 后端

- `agent`
  负责 Agent 编排、请求路由、上下文预算和结果整合
- `auth`
  负责登录、Bearer Token 鉴权和角色控制
- `catalog`
  负责全局资源目录和用户启用关系
- `capability`
  负责 Builtin / Skill / MCP Tool 的统一抽象
- `context`
  负责背景信息预算评估和上下文压缩
- `document`
  负责能力说明文档的摘要读取和详情读取
- `mcp`
  负责官方 MCP Java SDK 接入，支持 `stdio / streamable-http`
- `skill`
  负责本地 Skill 扫描、`skill.yaml` 解析和 workflow skill 运行时
- `model`
  负责全局模型档案、用户模型启用关系和多模态试跑
- `conversation`
  负责会话、消息历史和会话级模型记忆
- `audit`
  负责能力调用审计、执行耗时和时间线数据
- `admin`
  负责管理员总览统计和平台级运营数据聚合
- `web`
  负责 REST API、统一异常和本地化响应

### 前端

- 独立工程目录：`frontend/`
- 技术栈：`Vue 3 + TypeScript + Vite`
- 当前工作台支持：
  - 登录与角色识别
  - 全局目录浏览
  - 用户启用 / 停用 Skill、MCP、模型
  - 多模态模型试跑
  - 会话时间线与审计轨迹
  - 管理员资源总览和资源分布统计

## 当前资源模型

### 全局资源

由管理员统一维护：

- 全局 MCP Server
- 全局 Agent Skill
- 全局模型档案
- 全局能力说明文档

### 用户启用关系

普通用户不再重复安装资源，而是保存：

- 我启用了哪些 Skill
- 我启用了哪些 MCP
- 我启用了哪些模型
- 我的默认模型和会话级模型选择

运行时可用能力集合：

`全局开放资源 ∩ 当前用户已启用资源 ∩ 权限允许资源`

## 当前已支持能力

- `JDK 21 + Spring Boot 3.3`
- 官方 MCP Java SDK 主路径接入
- `stdio` 与 `streamable-http` 传输
- `tools / resources / prompts`
- MCP 验证接口和结构化诊断
- Vue 3 + TypeScript 前端工作台
- 登录、Bearer Token、管理员 / 普通用户角色
- 全局资源目录和用户启用关系
- 模型使用审批
- 会话级模型记忆与临时覆盖
- 多模态模型试跑：
  - 文本
  - 图片 URL / 本地图片
  - 音频 URL / 本地音频
  - `extraBody` 扩展参数
- API Key 传输加密和存储加密
- 能力说明文档搜索与按需读取
- 上下文预算评估与背景信息压缩
- 长能力结果压缩
- `resources/read` 长正文分段压缩
- 长 Skill 结果压缩
- workflow skill：
  - `when`
  - `when` 支持组合条件表达式（`== / != / && / || / ()`）
  - `capabilityId`
  - `capabilityInput`
  - `continueOnFailure`
  - `branch / group`
  - `summaryFromBranch / summaryFromGroup`
  - `workflowStats`
  - `workflowBranchSummary`
  - `workflowBranches`
  - `workflowBranchState / workflowGroupState`
  - 可基于汇总结果决定后续走哪条 `group`
- 会话时间线、轮次折叠和审计轨迹
- 管理员总览：
  - 资源规模
  - 用户启用关系
  - 模型审批概览
  - 能力调用热度 Top 10
  - 最近 7 天调用趋势
  - 全局资源状态分布
  - 模型授权状态分布

## 主要接口

### 鉴权

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`

### 管理员总览

- `GET /api/admin/overview`

### 能力文档

- `GET /api/capability-docs/search?q=...`
- `GET /api/capability-docs/read?docId=...&docType=summary|detail`

### 用户模型

- `GET /api/me/models`
- `POST /api/me/models/{profileId}/validate`
- `POST /api/me/models/{profileId}/preview`

## 已知限制

- 管理员端和普通用户端目前仍共用一个 Dashboard，后续还需要进一步拆分
- 管理员总览当前采用轻量聚合，适合平台概览，不是完整 BI 分析
- Windows 终端默认编码可能把 UTF-8 文本显示成乱码，源码本身统一按 UTF-8 无 BOM 维护

## 构建验证

后端：

- `mvn -q test`
- `mvn -q -DskipTests package`

前端：

- `D:\nvm\nodejs\npm.cmd run build`

## 安全部署说明

- 仓库默认不内置数据库地址、数据库账号密码、模型 API Key 和管理员初始密码
- 敏感配置请通过环境变量或本地未提交配置注入
- 可参考根目录的 `.env.example` 填写本地部署所需变量
