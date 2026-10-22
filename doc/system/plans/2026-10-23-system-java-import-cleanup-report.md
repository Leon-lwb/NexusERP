# Java 导入规范整改计划与验证

> 日期：2026-10-23。用户明确要求全仓类型/注解改用 import + 简写；确有同名类型共存时保留必要 FQN 并解释；完成后提交并推送 Gitee。此前 AI 第二阶段未提交成果一并保留和交付，.dd/ 不提交。

## 目标与方法

只改变 Java 类型引用和导入写法，保持程序语义。使用 JDK 语法树定位实际类型/静态成员所属类型与 Javadoc 链接，避免把字符串中的框架映射、反射名称和 SQL 改成 Java 标识符。检查主源码、测试源码、工具源码和生成模板；同名冲突结合既有导入、通配符、当前包、嵌套声明与实际简写引用判断。

## 执行步骤

- [x] 核对工作区、plus 分支与 b0a37949 基线；记录已有 AI 第二阶段修改，保留 .dd/；确认 Gitee origin。
- [x] 语法树转换实际全限定类型，补充显式 import；对同文件共存的同名类型添加原因注释。
- [x] 扫描并核对剩余引用和模板、脚本中的 Java 代码；定向格式化，确保无无理由的全限定类型。
- [x] 编译所有 Maven 模块的主/测试代码，执行相关现有回归、前端检查和代码复核；记录非零用例数与失败/跳过，不把跳过当通过。
- [x] 准备分批提交清单，排除 .dd/、编译产物、私有配置与本地备份；核对 Gitee origin/plus。实际提交与推送结果以 Git 历史和本轮最终答复为准。

## 验证与交付

Java AST 清单、转换摘要与最终检查日志保存在本地 target 验证目录；完成后补充本文件的实际文件/引用数量、同名例外、命令和结果。整个整改不执行数据库迁移，不启停用户服务，不启用 AI 第二阶段开关。

## 整改结果

- JDK 语法树完整扫描 4,419 个受维护 Java 源文件，覆盖主源码、测试和工具源码；导入整改涉及 827 个文件（另有此前 AI 实施文件的定向格式处理）。
- 最终检查可消除的全限定类型引用为 0；仅保留 48 个文件中的 49 组真实同名冲突，共 144 次引用（含 Javadoc），均有“同名类型说明”。已逐文件只读复核，无仅由无用 import 引起的假冲突。
- 注解、字段类型、泛型、类字面量、静态调用所属类型均采用 import + 简名；字段注解保持独立成行。生成模板已核查，两个 Python 测试启动器中的内嵌 Java 也改为 import PrintWriter。
- `AGENTS.md` 增加同一写法约定。反射名称、Mapper/序列化配置等字符串保留框架要求的完整标识。

## 验证结果

| 范围 | 实际结果 |
| --- | --- |
| Maven 全仓编译打包 | 56 个模块成功；主源码和测试源码均完成编译，命令为 mvn package -DskipTests=true；此项只作为构建证据 |
| 全仓扩展单测 | 805 个 suite / 11,104 项：10,915 通过、52 失败、137 错误、0 跳过，未宣称全绿 |
| 失败回归对照 | 27 个失败测试类从原始 b0a37949 快照在独立目录重新编译运行，失败数量、用例及分类与当前完全一致 |
| AI 与 Flow 四模块 | 2,116 项通过，0 失败/错误/跳过；包含于全仓计数 |
| 前端标准单测 | npm run test:unit：2,205 项通过，0 失败/取消/跳过 |
| 前端非增量类型检查 | npm run type-check -- --incremental false：通过 |
| 前端生产构建 | npm run build:prod -- --outDir node_modules/.tmp/ai-stage-two-build：通过 |
| 聊天契约往返 | npm run test:ai:roundtrip：Java 场景 1 项、前端消费 38 项通过 |
| 运行时代理独立验证 | 39 项通过，0 失败/跳过；与 Maven 用例有重叠，不叠加为唯一总数 |
| 独立安全工具 | JasyptRotate.java 仅编译通过，未执行密钥工具 |
| 历史 TraceReviewProbe | 独立编译仍有旧的 MesIdentityTraceReader 两参数构造器调用；原始提交同样复现，未改变业务依赖来掩盖问题 |

全仓诊断命令使用 -Dmaven.test.failure.ignore=true 继续收集后续模块；实际结果按本轮生成 XML 汇总，并与日志中的模块总数逐项核对，构建成功未被当成单测全通过。3 个需要外部运行环境的 IM SpringBoot 集成测试未选择执行：ImChatServiceIntegrationTest、ImEnvelopeHelperIntegrationTest、ImRealtimePushTest。

既有失败集中在 ERP/MES 安装 SQL 合同、标签打印日志表及 H2 初始化兼容、IM 可见性与跨模块报表测试。本轮不修改这些业务合同或放松断言。原始快照包含 AGENTS.md 根目录标记及相关脚本/文档资源，避免将缺少夹具文件的失败混作基线问题。

## 可复核证据

本地日志与 JSON 位于 ruoyi-modules/ruoyi-ai/target/java-import-cleanup/：before.json、all-roots-applied.json、after.json、compile-all.log、complete-run.log、complete-summary.json、baseline-all-original.log、baseline-all-comparison.json、frontend-unit.log、frontend-types.log、frontend-build.log、frontend-roundtrip.log、runtime-agent-tests.log，以及历史探针的两份编译对照。target 不提交，本报告固化计数与结论。

## Git 交付边界

此前 AI 第二阶段代码、增量和实施记录已独立提交为 4f1a8c04；其他模块的导入整改、内嵌启动器和本报告另行提交。目标为 Gitee origin/plus，采用非强制推送并核对远端 HEAD。.dd/ 保留且不提交；本轮没有执行数据库迁移、启停服务或启用 AI 第二阶段开关。
