# 项目待办

## 已完成

- [x] 使用 `JDK 21` 初始化 Spring Boot 工程
- [x] 建立 Builtin / Skill / MCP Tool 统一能力模型
- [x] 接入官方 MCP Java SDK
- [x] 打通 `stdio` 和 `streamable-http`
- [x] 支持 `tools / resources / prompts`
- [x] 增加 MCP 验证接口和结构化诊断
- [x] 接入登录、Bearer Token、角色控制
- [x] 落地会话、消息历史、能力调用审计
- [x] 接入 Vue 3 + TypeScript 前端工作台
- [x] 支持多模态模型试跑和会话级模型记忆
- [x] 增加 API Key 传输加密和存储加密
- [x] 增加能力文档搜索和按需读取
- [x] 接入背景信息预算评估和历史消息压缩
- [x] 把长能力结果纳入上下文压缩
- [x] 把 `resources/read` 长正文纳入分段压缩
- [x] 把长 Skill 结果纳入压缩链
- [x] 完成“管理员维护全局资源、普通用户按需启用”的核心改造
- [x] 增加模型使用审批
- [x] 支持 workflow skill 最小运行时
- [x] 支持 workflow skill 的条件分支、能力绑定和失败继续
- [x] 支持 workflow skill 的统计摘要、分支摘要以及 `branch / group` 显式归类
- [x] 支持 workflow skill 将 `workflowBranchState / workflowGroupState` 写回 `state`，供后续步骤按分支失败状态做条件回退
- [x] 支持 workflow skill 的显式汇总节点，可通过 `summaryFromBranch / summaryFromGroup` 汇总指定 branch 或 group
- [x] 支持 workflow skill 基于汇总结果做更复杂的条件分支，可决定后续进入哪条 group
- [x] 支持 workflow skill 的组合条件表达式，当前可用于 `when` 中的 `== / != / > / >= / < / <= / in / ! / && / || / ()`
- [x] 清理公开仓库默认敏感配置，数据库连接、管理员密码与 API Key 改为环境变量或空白模板
- [x] 增加管理员资源总览、调用热度和审批概览
- [x] 扩展管理员总览：支持最近 7 天调用趋势、资源状态分布和模型授权状态分布

## 进行中

- [ ] 清理仍沿用旧“用户自己安装一套资源”语义的兼容逻辑和零散文案
- [ ] 继续拆分管理员端和普通用户端页面结构，降低单页复杂度

## 下一阶段

- [ ] 为管理员总览补更细的趋势分析和筛选维度
- [ ] 为管理员总览增加资源使用下钻视图
- [ ] 为普通用户补更清晰的“我的启用能力 / 我的默认模型”工作区
- [ ] 把能力文档动态加载升级成模型驱动的多步 planner
- [ ] 继续扩展 workflow skill 的更复杂编排能力
- [ ] 增加更细粒度的审批、权限策略和风险分级
- [ ] 为全局 Skill / MCP / 模型补统一文档管理入口
- [ ] 增加真实第三方 MCP Server 联调样例矩阵

## 风险 / 阻塞

- [ ] 历史数据如果仍存在旧的“用户自建模型 / 用户自建 MCP 配置”记录，后续需要补迁移脚本
- [ ] `streamable-http` 在部分服务端上的兼容告警仍受官方 SDK 行为影响
- [ ] 管理员端和普通用户端当前仍共用一个 Dashboard，继续扩展时需要注意页面膨胀
- [ ] Windows 终端默认编码可能把 UTF-8 文本显示成乱码，源码必须继续坚持 UTF-8 无 BOM
