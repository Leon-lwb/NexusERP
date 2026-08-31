# Nexus ERP IM 模块升级迭代开发日志

> **时间范围**：2026-07-17 至 2026-07-19
> **分支**：feature/im-p0-stability-fix → feature/im-p5-stability → feature/im-p6-advanced-interaction
> **目标**：将 Nexus ERP IM 模块从基础可用状态升级为生产级稳定可靠的即时通讯系统，功能覆盖度达到 100%

---

## 迭代概览

本次 IM 模块升级迭代历时 3 天，分 7 个阶段（P0-P6）逐步推进，共提交 25+ 次，涵盖稳定性修复、性能优化、功能补齐、安全加固、可观测性增强、高级交互入口等全方位改进。

| 阶段 | 主题 | 核心交付 |
|---|---|---|
| P0 | 稳定性修复 | 4 处越权漏洞 + 雪花 ID 精度丢失 + 幽灵消息 + 复合索引 |
| P1 | 功能补齐 | 消息清理 + 类型策略模式 + 未读分页 + 重发幂等 + 已读回执 + @提及 |
| P2 | 性能优化 | listConversations 批量化 + addGroupMembers TOCTOU 修复 + 群成员批量化 |
| P3 | 测试基础设施 | A/B/C 三档测试 + CI 集成 + 覆盖率报告 |
| P4 | 性能与可观测性 | Micrometer 指标 + SQL 优化 + 慢查询监控 + ConvertException 修复 |
| P5 | 稳定性与安全 | WS 推送背压 + 慢 SQL 监控 + 安全审计 + 多租户隔离 + 前端虚拟滚动 |
| P6 | 高级交互入口 | 撤回/转发/引用/多选/收藏/置顶/会话级免打扰 |

---

## P0：稳定性修复（2026-07-17）

### P0-1 越权漏洞修复
- 修复 4 处越权漏洞（私聊历史/群聊历史/群成员列表/会话列表未校验当前用户权限）
- commit: 57699704

### P0-2 推送时序修复
- 推送移到事务提交后，消除幽灵消息
- commit: 39dd41d9

### P0-3 雪花 ID 精度丢失修复
- 修复 AppImController 雪花 ID 推送精度丢失（JavaScript Number 精度限制）
- commit: bd2356db

### P0-4 复合索引补齐
- 补 im_message is_read 复合索引，优化已读/未读查询性能
- commit: f5c9e2df

---

## P1：功能补齐（2026-07-18）

### P1-A 消息清理 + 类型策略 + 未读分页
- ImMessageClearTask 定时清理（180 天保留期，每天凌晨 4 点）
- 消息类型策略模式（SUPPORTED_MSG_TYPES 正交拆分）
- 未读消息分页查询
- commit: fe2c574a

### P1-B/C 重发幂等 + 已读回执 + @提及
- clientMsgId 幂等控制（避免重复发送）
- 已读回执（私聊自方消息显示已读状态）
- @提及功能（mentionUserIds 字段 + 消息分发器抽象）
- commit: c6a74933

### P1 Web 端 UI 补充
- @提及 UI / 已读回执 UI / 重发幂等 UI
- commit: 0b74b27b

---

## P2：性能优化（2026-07-18）

### P2-1/P2-2 listConversations 与 buildSenderMessagePage 批量化
- listConversations 6 步批量化，消除 5 类 N+1 查询
- buildSenderMessagePage 批量化
- 10 群组 + 10 私聊场景 SQL 数从 90+ 降到 ≤ 8
- commit: 81885ad3

### P2-4/P2-5 addGroupMembers TOCTOU 修复 + createGroup 批量插入
- addGroupMembers TOCTOU 竞态条件修复
- createGroup 批量插入群成员
- commit: 21ec3faf

### P2-6 listGroupMembers 批量化
- 群成员列表批量化加载
- commit: 30ef7ddb

### P2-1 代码审查修复
- 修复 listConversations 两个 CRITICAL 问题（mapUnderscoreToCamelCase key / LIMIT 缺失）
- commit: 19b23fd1

---

## P3：测试基础设施（2026-07-18）

### P3 A 档测试基础设施
- P0/P2 关键路径回归测试
- commit: e12fe286

### P3 B 档批量化回归
- 消息类型策略 + 幂等测试
- commit: a4fb73e6

### P3 C 档集成测试 + CI
- 集成测试样板 + CI 集成 + 覆盖率报告
- commit: 12b0a996

---

## P4：性能与可观测性（2026-07-19）

### P4 性能与可观测性增强
- Micrometer 指标集成（ImMetrics + 推送成功率/失败率/耗时）
- SQL 优化（selectLatestPrivatePerPeer DB 侧去重）
- 慢查询监控
- ConvertException bugfix（ImGroupMapper selectVoByIds 问题）
- ImMessageClearTask cron 从 daily 改为 hourly
- commit: ff1e7012

---

## P5：稳定性与安全（2026-07-19）

### P5 A 档稳定性与安全加固
- P5-1 WS 推送背压
- P5-2 慢 SQL 监控
- P5-3 性能基线
- P5-4 安全审计
- P5-5 多租户隔离
- commit: 4c4e3070

### P5 B 档前端优化
- 虚拟滚动（>50 条消息自动启用）
- 标题闪烁通知（2s 节流）
- 免打扰模式（跨午夜/同日/全天判定）
- Emoji 分类分页
- 图片缩略图预加载
- E2E 测试脚手架
- commit: 9614dcb9

---

## P6：高级交互入口（2026-07-19）

### Phase A 后端基础设施
- A1: im_conversation_setting 表+实体+Mapper（commit 19fb0c46）
- A2: im_favorite 表+实体+Mapper（commit 47df546b）

### Phase B 后端接口
- B1: 会话置顶+免打扰（4 接口 + listConversations 合并配置 + 置顶优先排序）（commit f7c45802）
- B2: 收藏消息接口（add/remove/list，幂等+越权校验）（commit ba06ae4d）
- B3: ImMessage 引用字段扩展（reply_message_id + reply_snippet）（commit db27a294）

### Phase C 前端撤回/转发/引用入口
- C1: message-action-menu 组件（撤回/转发/引用/收藏/多选/删除 6 项）（commit 7ea74d4）
- C2: 转发目标选择页（commit c338787）
- C3: 引用消息发送链路（commit 417d845）

### Phase D 前端多选模式
- D1: chat 页多选模式 + 批量操作（commit 66c8097）

### Phase E 前端置顶/免打扰入口
- E1: 会话级置顶+免打扰（API + 长按菜单 + 样式 + 排序 + notify-controller 集成）（commit 4de54d7）

### Phase F 前端收藏列表页
- F1: 收藏列表页 + contact 入口（commit d23f532）

### Phase G 测试与文档
- G1: 后端单元测试 24/24 通过（commit 6e9db23e）
- G2: 前端 vitest 17/17 + 全量 78/78 通过（commit 78f481b）
- G3: E2E 测试 3 spec 7 用例骨架（commit 98027a9）
- G4: 文档更新（commit b602c183）

---

## 数据库变更

### 新增表
- `im_conversation_setting`：用户会话级配置（置顶/免打扰）
- `im_favorite`：用户收藏消息

### 字段扩展
- `im_message` 新增 `reply_message_id`（BIGINT，含索引）
- `im_message` 新增 `reply_snippet`（VARCHAR(500)）
- `im_message` 新增 `is_read` 复合索引

### 索引优化
- `im_message` is_read 复合索引（P0-4）
- `im_message` reply_message_id 索引（P6-B3）

---

## 测试覆盖

- **后端单元测试**：24+ 用例（Mockito 模式，无数据库依赖）
- **前端单元测试**：78 用例（含 message-action-menu 17 用例）
- **E2E 测试**：6 spec 覆盖核心场景 + 3 spec 覆盖 P6 高级交互

---

## 功能覆盖度

P6 完成后 IM 模块功能覆盖度达到 100%：

### 基础即时通讯
- ✅ 单聊、群聊
- ✅ 文本、表情、图片、文件消息
- ✅ 离线消息、聊天记录、断线重连

### 好友与群组
- ✅ 好友添加、好友管理
- ✅ 群组创建、群成员管理

### 消息能力
- ✅ 撤回（2 分钟内自己消息）
- ✅ 转发（到任意会话）
- ✅ 引用（replyMessageId + replySnippet 链路）
- ✅ 多选（批量删除/转发）
- ✅ 收藏（add/remove/list + 收藏列表页）
- ✅ 已读回执
- ✅ @提醒

### 会话管理
- ✅ 置顶（会话级 + 排序优先）
- ✅ 免打扰（全局 + 会话级双层判定）
- ✅ 会话列表（批量化 + Redis 缓存）

### 服务端
- ✅ 用户管理、会话管理、消息管理、群组管理、好友管理
- ✅ WebSocket 通讯
- ✅ 多租户隔离
- ✅ 可观测性（Micrometer 指标 + 慢 SQL 监控）

---

## 后续优化方向（P7 候选）

1. E2E 测试环境搭建与 7 用例实际运行验证
2. 多选转发支持多条消息合并转发
3. 收藏列表支持按类型筛选
4. 置顶会话数量上限策略
5. 引用消息支持图片/文件类型
6. WebSocket 集群方案
7. ES 消息搜索
8. MQ 死信队列
