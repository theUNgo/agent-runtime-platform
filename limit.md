# 代码规范与长期约束

本文档用于沉淀当前项目已经明确的实现约束，后续新增功能默认按这里执行。

## 通用约束

- 后端默认使用 `Java 21`
- 后端默认使用 `Spring Boot 3.x`
- 前端默认使用 `Vue 3 + TypeScript + Vite`
- 新功能优先在现有分层上扩展，不轻易推翻既有结构

## 注释规范

- 新增代码默认补中文注释
- 注释重点说明职责、边界、原因，不写逐行翻译式注释
- 协议适配、安全处理、编排策略、兼容分支等复杂逻辑必须补中文说明

## 多语言规范

- 所有用户可见错误、校验提示、返回消息都必须接入 i18n
- 不在后端直接硬编码面向用户的中文或英文错误文案
- 消息资源统一维护在：
  - `src/main/resources/i18n/messages.properties`
  - `src/main/resources/i18n/messages_zh_CN.properties`

## 编码规范

- 仓库文本文件统一使用 `UTF-8`
- 源码和文档统一要求 `UTF-8 without BOM`
- Windows 终端乱码优先视为读取链路问题，不轻易重写源码
- 持续遵守 `.editorconfig`

## 安全规范

- API Key 不允许明文回传到前端
- 前端编辑模型时，未修改 API Key 不重新传输
- 用户修改 API Key 时，必须先使用公钥做传输加密
- 后端收到传输密文后，入库前还要再做存储加密
- 用户侧只展示中段脱敏后的 Key
- 公开仓库默认不提交真实数据库地址、数据库账号密码、模型 API Key 和管理员初始密码

## 平台资源模型规范

- 平台采用“管理员维护全局资源，普通用户按需启用”的模式
- 全局资源包括：
  - MCP Server
  - Agent Skill
  - 模型档案
  - 能力说明文档
- 普通用户只保存启用关系和偏好设置，不重复创建完整资源副本
- 运行时可用能力 = 全局开放资源 ∩ 用户已启用资源 ∩ 权限允许资源
- 模型能力额外支持“审批后可用”的约束

## 文档加载规范

- 能力说明文档采用“摘要优先、详情按需”的策略
- 默认先读 `summary`
- 只有在需要时才读 `detail`
- `capability.doc.read` 只能读取白名单文档类型，不开放任意文件路径

## 上下文压缩规范

- 压缩决策不能只看总长度，还要同时看背景信息占比
- 当前用户消息、系统提示和当前轮关键输入优先保留
- 历史消息优先做摘要压缩
- MCP / Skill 长结果必须进入统一预算评估
- 标准 `resources/read` 响应优先按 `contents[]` 分段压缩
- Skill 返回的长 `prompt / input` 也要优先压成执行摘要

## Workflow Skill 规范

- workflow skill 优先采用轻量步骤定义，不直接引入复杂外部流程引擎
- workflow skill 的步骤产物优先写入统一 `state`
- workflow skill 必须显式声明失败策略
- workflow skill 优先产出 `workflowStats`、`workflowBranchSummary` 和 `workflowBranches`
- workflow skill 支持通过 `branch / group` 显式标记步骤归类
- workflow skill 应将 `workflowBranchState / workflowGroupState` 写回 `state`
- workflow skill 允许通过 `summaryFromBranch / summaryFromGroup` 声明显式汇总节点
- 显式汇总节点的结果也应继续写回统一 `state`
- workflow skill 的 `when` 当前至少应支持组合条件表达式（`== / != / && / || / ()`），便于基于汇总结果做分组分流

## 管理员总览规范

- 管理端工作台优先提供平台级总览：资源规模、启用人数、审批状态和调用热度
- 管理员总览的趋势与分布优先复用现有关联表和审计表做轻量聚合
- 先保证统计口径一致，再考虑引入更重的时序分析方案

## 后端实现规范

- 统一异常继续走 `GlobalExceptionHandler`
- 新增接口优先提供清晰的请求/响应 `record`
- 复杂业务逻辑优先放在 `service / provider` 层，不堆在 `controller`
- 与模型、MCP、Skill 相关的核心运行时逻辑优先走统一抽象，不把底层 SDK 类型泄露到上层

## 前端实现规范

- 新增接口先写 TypeScript 类型，再接页面逻辑
- 页面明显膨胀时优先拆分子组件
- 前端请求统一走 `frontend/src/lib/api.ts`
- 管理端和普通用户操作要明确区分，不混淆“资源安装”和“资源启用”语义

## 构建验证规范

- 后端改动完成后优先执行：
  - `mvn -q test`
  - `mvn -q -DskipTests package`
- 前端改动完成后优先执行：
  - `D:\nvm\nodejs\npm.cmd run build`
- 构建失败时优先修复编译和类型问题，再继续扩展功能

## 文档维护规范

- 完成一个阶段性闭环后，要同步更新：
  - `README.md`
  - `todo.md`
  - `limit.md`
- 新形成的长期约束要及时写入 `limit.md`
