# Claude 对话记录: NexusERP 全模块代码优化方案

> 会话 ID: 6609de05-6612-46d9-b73d-e32b4add1e23
> 时间: 2026-08-12 ~ 2026-08-13
> 分支: optimize/p0-quick-wins (基于 plus)

---

## 用户提问

我的NexusERP项目是一个中型的Java项目，现在需要你对我的整个项目做一个初步探查，了解基础结构后开始帮我制定所有模块的代码优化方案

---

## 🤖 Assistant

我来帮你做整个项目的初步探查。这是一个较大的工程（RuoYi-Vue-Plus 二次开发 + ERP/MES/IoT/AI/IM 多模块），我会先并行派出 3 个探索代理分区域扫描代码库，收集结构数据和代码质量问题，再制定分模块的优化方案。

---

## 🤖 Assistant

3 个探查代理已在后台并行扫描（ERP/MES 业务模块与项目统计、公共层/系统模块/配置安全、AI/IM/IoT/前端等模块）。在等待期间，我先确认几个影响方案设计方向的问题：

---

## 🤖 Assistant

明确了：**侧重代码质量/重复消除 + 性能/并发**，产出**分阶段全量路线图（P0 快赢 → P1 → P2）**，**前后端都覆盖**。

3 个探查代理正在后台并行扫描代码库，等它们的报告返回后，我会汇总统计数据和问题清单，再让规划代理设计分模块的优化路线图，最终整理成完整方案给你审阅。

---

### 📋 子代理完成: Agent "探查ERP/MES模块与项目统计" finished

## 🤖 Assistant

第 1 份探查报告已返回（ERP/MES + 项目统计），发现不少实质问题：ERP 9.8 万行代码仅 3 个测试类、6 处重复的 Flowable 降级查询、库存表软删除反模式、N+1 查询等。另外两个代理（公共层/安全、AI/IM/IoT/前端）还在扫描中，等齐三份报告后我会汇总并设计分阶段优化路线图。

---

### 📋 子代理完成: Agent "探查公共层/系统模块/配置" finished

## 🤖 Assistant

第 2 份报告也已返回（公共层/系统模块/安全审查），发现的安全问题比预期严重：`/app/auth/smsCode` 接口可无认证冒用任意手机号登录、prod 配置明文密码进 git、git 历史含支付宝/微信密钥、actuator 弱口令等。还剩最后一份（AI/IM/IoT/前端）在扫描，返回后我会汇总三份报告，进入方案设计阶段。

---

### 📋 子代理完成: Agent "探查AI/IM/IoT/前端模块" finished

## 🤖 Assistant

三份探查报告已全部返回。现在进入方案设计阶段，我把全部发现汇总给规划代理，让它设计分阶段优化路线图。

---

## 🤖 Assistant

规划代理正在后台设计路线图。等待期间，我先把探查结果和项目概览写入方案文件。

---

## 🤖 Assistant

方案文件的探查部分已就绪（项目统计 + 问题清单 + 可复用样板 + 验证约束）。规划代理仍在后台设计分阶段路线图，完成后我会把 P0/P1/P2 的条目明细和依赖关系整合进方案，再交你审阅。

---

### 📋 子代理完成: Agent "设计全模块代码优化路线图" finished

## 🤖 Assistant

路线图设计已完成。在最终定稿前，我亲自核验两个最关键的 P0 修复点（安全紧急项），确保方案基于真实代码。

---

## 🤖 Assistant

两处关键代码均已核实，与探查报告一致：
- `AppAuthController.java:71-84` — smsCode 接口确认 `@SaIgnore`、无验证码/限流、明文返回验证码且打印日志
- `SqlSafetyValidator.java:187-189` — 确认 `contains("TENANT_ID")` 即跳过注入的绕过逻辑；`extractOuterTenantColumn` 只处理最外层第一张表，JOIN 表确实无隔离

定稿前还剩一个范围决策需要你确认：

---

## 🤖 Assistant

明白——安全项移出，另立安全专项。我更新方案文件：本路线图聚焦代码质量/重复消除 + 性能/并发，安全项保留清单供专项计划引用。

---

## 🤖 Assistant

方案已完成并写入计划文件。核心内容：

- **探查结论**：全仓 ~2,200 Java 文件 / 23.7 万行，ERP 占 9.8 万行且仅 3 个测试类；确认 6 个高严重度质量/性能问题 + 大量跨模块复制样板
- **P0 快赢（8-12 人天）**：3 处 N+1 修复、IoT 无界删除改分批、事务注解收尾、杂项快修、建立回归门禁
- **P1 结构性（40-60 人天）**：Flowable 降级查询去重（7 处复制）、MQ 租户样板上收基类、消费者直注 Mapper 改造、ERP ServiceImpl 基类化（试点 wm/bd 后推广）、前端 useCrud 试点
- **P2 大重构（60-90 人天）**：11 个上帝类拆分、前端 171 页 useCrud 全量化、缓存策略统一、TS 严格化渐进
- **安全项已按你的决定移出**，附录 A 保留清单供后续安全专项引用

---

## 🤖 Assistant

方案已批准。开始执行 P0 阶段——先建任务清单和 feature 分支（按方案风险控制要求：全部走 feature 分支、每条目一个 commit）。

---

## 🤖 Assistant

先修 `FaDepreciationServiceImpl` 的 N+1。文件已通读，确认 4 处循环内查库（preview 1 处、runDepreciation 计提聚合 2 处、回填 1 处、buildPlans 的 category 1 处）。再看 `FaAssetServiceImpl`：

---

## 🤖 Assistant

`FaAssetServiceImpl` 的 `fillExtra` 用 `computeIfAbsent → selectById` 逐类别查库。先看它的 import 区，再统一修改两个文件：

---

## 🤖 Assistant

现在开始修改。先修 `FaDepreciationServiceImpl`：

---

## 🤖 Assistant

`FaDepreciationServiceImpl` 5 处修改完成。继续修 `FaAssetServiceImpl`：

---

## 🤖 Assistant

两个文件改完。后台启动 ERP 模块编译验证（`-am` 从源码重建依赖，避免 .m2 旧 jar 干扰），同时开始 P0-2 读 BOM 文件：

---

## 🤖 Assistant

需要找出旧版递归方法（`buildTreeNode`/`buildIndentedLines`）还有哪些调用方：

---

## 🤖 Assistant

编译通过（exit 0），P0-1 修改无误。继续 P0-2：关键发现——`buildTreeNode`（旧版）和 `buildIndentedLines`（旧版）在整个模块**无任何外部调用方**，只存在自我递归，是死代码。N+1 就藏在这两段死代码里。再确认 `checkCircularRef`/`findCurrentBom` 的调用关系，决定删除范围：

---

## 🤖 Assistant

确认了：旧版 `checkCircularRef`、`buildTreeNode`、`buildIndentedLines` 都只有自我递归调用，是死代码；`findCurrentBom` 仅被这两段死代码调用。P0-2 的正确修法是把这 4 段死代码连同其 javadoc 一起删除（消除潜伏 N+1 + 减负 ~170 行）。先读剩余两段的精确文本：

---

## 🤖 Assistant

四段死代码边界已明确。执行删除：

---

## 🤖 Assistant

P0-1、P0-2 代码修改完成。重新编译验证（覆盖两个文件的改动），同时开始 P0-3 读 IoT 文件：

---

## 🤖 Assistant

P0-3：先看正确样板 `ImMessageClearTask` 的分批删除实现：

---

## 🤖 Assistant

看 `deleteBefore` 的调用方（`IotDataCleanupTask`），确认事务上下文再决定是否沿用 IM 样板的无事务分批语义：

---

## 🤖 Assistant

调用方 `IotDataCleanupTask` 无外层事务。按 `ImMessageClearTask` 样板改造：移除包裹事务（每批独立提交，避免长事务锁表）+ LIMIT 1000 分批 + 循环护栏：

---

## 🤖 Assistant

ERP 编译通过（exit 0）。P0-3 代码已改。启动 IoT 模块编译验证，同时按方案提交 P0-1、P0-2（每个条目一个 commit）：

---

## 🤖 Assistant

P0-1、P0-2 已提交。IoT 编译在后台跑，先启动 P0-4 事务注解收尾——先摸清全仓 `@Transactional` 分布：

---

## 🤖 Assistant

IoT 编译通过。提交 P0-3，并扩大 P0-4 的扫描范围（含带属性的注解）：

---

## 🤖 Assistant

52 个命中里混入了 `@TransactionalEventListener`、注释和多行注解。精确过滤出真正的裸注解（排除测试类）：

---

## 🤖 Assistant

这些可能是多行注解（rollbackFor 在下一行）。拉上下文确认：

---

## 🤖 Assistant

确认了模式：这些是 Flowable 回调（`afterFlowHandle` 写方法 / `getBusinessDataById` 读方法）的 `@Transactional(propagation = REQUIRES_NEW)` 无 rollbackFor。批量导出全部 38 处的下一行方法签名，统一分类：

---

## 🤖 Assistant

awk 脚本有 bug，改用 `grep -A2` 直接看注解后两行：

---

## 🤖 Assistant

模式完全清晰：14 个 `afterFlowHandle`（写）+ 14 个 `getBusinessDataById`（纯读）+ AutoCodeUtil + SopDocument。再确认 9 处裸注解和可能的多行注解：

---

## 🤖 Assistant

分类完成：14 写方法补 `rollbackFor`、14 读方法加 `readOnly`、AutoCodeUtil 补 `rollbackFor`、9 处裸注解补 `rollbackFor`，共 38 处与探查一致。先全仓复查裸注解无遗漏（排除上次 head 截断影响），然后用 perl 批量执行：

---

## 🤖 Assistant

开始批量替换（perl 多行模式，三类模式分别处理）：

---

## 🤖 Assistant

分类器暂时不可用，稍后重试 perl 批量。先做 P0-5 的只读调查（Edit 工具不受影响，可直接改文件）：

---

## 🤖 Assistant

P0-5 准备中。继续读剩余文件（JimuReportTokenService、双 JacksonConfig）：

---

## 🤖 Assistant

P0-5 各项调查基本完成。`JimuReportTokenService` 修复方案明确（getToken 入口清理残留 + getTenantId 消费后 finally remove）。双 JacksonConfig 合并前先核查 `BigNumberSerializer` 的引用面和 extend 服务的依赖关系，判断合并是否安全：

---

## 🤖 Assistant

`BigNumberSerializer` 被 IM 集成测试显式守卫（import web 包 + 断言注册行为），双 JacksonConfig 拆分实为有测试守护的刻意设计——合并需跨模块移动类并改守卫测试，风险大于收益，**此子项暂缓**，稍后向你说明。继续 P0-4 的 perl 批量（重试）和 P0-5 其余调查：

---

## 🤖 Assistant

分类器仍不可用。切换策略：P0-4 的 perl 批量稍后重试，先用 Edit 工具推进 P0-5（无需 Bash）。先核查 demo 引用和 flowable TODO 分布：

---

## 🤖 Assistant

demo 无引用，可安全移除。看 flowable TODO 的具体上下文决定处理方式：

---

## 🤖 Assistant

上下文清楚了：9 个 `validEntityBeforeSave` 空壳方法（仅含 TODO 注释）+ 10 个 `deleteWithValidByIds` 空 if 块。先用 Edit 做 P0-5 的三个独立项，flowable 批量稍后随 perl 一起。先确认 `GlobalExceptionHandler` 中 `JsonUtils` 的全部使用点：

---

## 🤖 Assistant

三处 Edit 同时执行：

---

## 🤖 Assistant

P0-5 三项完成。重试 P0-4 的 perl 批量（含 flowable TODO 清理一并处理）：

---

## 🤖 Assistant

Bash 分类器持续不可用。改用 Edit 工具逐文件完成 P0-4（32 处编辑，分批执行）。第一批——8 个文件的 `afterFlowHandle` 注解：

---

## 🤖 Assistant

第二批——剩余 5 个 ERP 文件的 `afterFlowHandle` + SopDocument + AutoCodeUtil：

---

## 🤖 Assistant

第三批——14 个 `getBusinessDataById` 读方法加 `readOnly = true`：

---

## 🤖 Assistant

第四批——9 处裸注解补 `rollbackFor`：

---

## 🤖 Assistant

替换后必须立即核查 `public ` 后空格是否被误删（`public` → `publicint` 语法错误风险）：

---

## 🤖 Assistant

确认失误——空格被吞。立即修复两处文件：

---

## 🤖 Assistant

修复完成。核查两文件无残留语法破坏，并获取 10 个 `deleteWithValidByIds` 的精确文本：

---

## 🤖 Assistant

`publicint` 已清零，Bash 也恢复了。用 perl 完成 flowable TODO 清理（模式统一）：

---

## 🤖 Assistant

Bash 又不可用。改用 Edit 完成 flowable 清理（模式已明确）。第一批 4 个文件：

---

## 🤖 Assistant

validEntityBeforeSave 的实际文本与猜测不符，读实际上下文：

---

## 🤖 Assistant

是 `entity){` 无空格。修正三处：

---

## 🤖 Assistant

剩余 4 个文件继续清理：

---

## 🤖 Assistant

flowable 清理全部完成。核查残留并启动全量编译验证（覆盖 P0-4/P0-5 所有改动模块）：

---

## 🤖 Assistant

核查通过（剩余 3 处是 javadoc 非注解，UserTaskListener 是有意保留的扩展点模板）。后台启动覆盖所有改动模块的编译，同时查 P0-6 的 CI 现状：

---

## 🤖 Assistant

现有 CI 只覆盖 IM。P0-6 设计：新增全仓后端回归 workflow（编译+测试）+ 前端 vue-tsc 基线机制。先看 plus-ui 的 package.json：

---

## 🤖 Assistant

写 P0-6 的三个交付物：后端回归 workflow、前端变更文件类型检查脚本、npm script：

---

## 🤖 Assistant

全部受影响模块编译通过（exit 0）。P0-4/P0-5 代码验证完成。继续 P0-6 剩余交付：前端 workflow + npm script，并启动现有测试运行：

---

## 🤖 Assistant

测试全部通过（exit 0）——P0 门禁绿灯。按条目提交 P0-4/P0-5/P0-6：

---

## 🤖 Assistant

P0 全部 6 项已提交。更新任务状态并记录进度到记忆：

---

## 🤖 Assistant

# P0 阶段完成 ✅

`optimize/p0-quick-wins` 分支上 6 个独立 commit（未推送），全部通过验证：

| Commit | 条目 | 内容 |
|---|---|---|
| `f60f1f63` | P0-1 | 折旧计提 N+1 修复：`FaDepreciationServiceImpl` 新增 `preloadAssets/preloadCategories` 批量预取（preview/计提聚合/过账回填三处），`FaAssetServiceImpl.fillExtra` 同步修复，O(n)→O(1) |
| `b377e097` | P0-2 | BOM 死代码删除 186 行：旧版 `buildTreeNode/buildIndentedLines/checkCircularRef/findCurrentBom` 经核实**无任何调用方**（探查报告里的两处 N+1 就藏在这段死代码里），活路径早已全部走 WithContext 预加载实现 |
| `fa5f28e6` | P0-3 | IoT 历史数据删除按 `ImMessageClearTask` 样板改 LIMIT 1000 分批 + 循环护栏，去掉包裹事务改为每批独立提交 |
| `d3f538d0` | P0-4 | 事务注解收尾 38 处：14 个 Flowable `afterFlowHandle` 写回调补 `rollbackFor`、14 个 `getBusinessDataById` 纯读回调加 `readOnly`、AutoCodeUtil + 9 处裸注解补齐 |
| `028b1786` | P0-5 | demo 模块移出生产包、SseException 返回 `R<Void>`、JimuReportTokenService ThreadLocal 双保险清理、flowable 10 个 ServiceImpl 模板 TODO/空 if 壳清除 |
| `cf8c3708` | P0-6 | CI 门禁：`backend-regression.yml`（全仓 `mvn test -DskipTests=false`）+ `frontend-typecheck.yml`（vue-tsc 基线噪声下改用"变更文件视角"判定，新增 `check-tsc-diff.mjs` 脚本） |

**验证**：受影响 8 个模块全量编译 exit 0；im/mes/erp/ai 现有 44 个测试全部通过。

**与方案的三处偏差**（已在 commit 消息中注明）：
1. P0-2 实为死代码删除而非 N+1 改写
2. 双 JacksonConfig 合并暂缓——深入后发现是 IM 集成测试显式守卫的刻意设计（Module Bean 注册机制），合并需跨模块移动类并改守卫测试，风险>收益，留 P2 评估
3. skipTests profile 化未做——`skipTests=true` 是 CLAUDE.md 文档化的本地工作流行为，CI 已用 `-D` 覆盖，改默认反而破坏现有习惯

**顺带发现的新问题**（未修）：`FaAssetServiceImpl.generateAssetNo` 用 `selectCount+1` 生成资产编号，并发创建会撞号（唯一索引冲突），建议列入后续阶段或安全专项一起处理。

**下一步**：按路线图进入 P1 结构性收敛（P1-1 Flowable 降级查询去重 → P1-2 MQ 租户上收 → P1-3 消费者改经 Service → P1-4 ServiceImpl 基类化试点 wm/bd）。P1 工作量约 40-60 人天，且方案要求试点 commit 合入后观察一个迭代再推广——你想现在就继续 P1，还是先审阅 P0 分支（可以推送到远端）再决定？

---


