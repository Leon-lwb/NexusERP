# 物料身份与追溯代码可读性整理记录

日期：2026-10-19。范围为本轮物料身份、条码与追溯交付涉及的代码。

## 实际改动与同步

已将验证后的 463 个源码及脚本文件同步至四个主工作区，保留为未提交改动，未推送。

| 仓库 | 主工作区分支 | 整理前提交 | 实际改动文件 |
| --- | --- | --- | --- |
| nexus-erp | `plus` | `f1e255bc5014` | 418 |
| nexus-pda | `master` | `054d1113552e` | 4 |
| nexus-station | `master` | `3f63ea724c57` | 34 |
| nexus-app | `main` | `ae04af40b84c` | 7 |

逐文件原始及整理后摘要见 [可读性改动清单](2026-10-19-material-trace-readability-changes.json)。清单单独保存，没有覆盖原交付清单或历史迁移证据。

- Java 采用四空格缩进，展开同一行堆叠的声明、调用、分支和异常处理。为简写控制体补充花括号，长的编译期字符串按可读片段排列。
- 类、接口、记录和关键方法使用中文多行 Javadoc，说明职责、正式身份与本地主键、追踪策略版本、实际用量、事务与幂等边界，并补充参数及返回值说明。
- 逐项复核关键上下文，明确区分 ERP/MES 工单、单据行主键与 SQL 字段名、计价组结存与组件用量、单据明细修订与完工清单修订。
- 前端沿用各仓库格式，说明性注释使用中文；Python 采用统一排版及中文文档字符串；XML 展开内联父级与依赖；SQL 元数据断言按条件排列。
- 英文说明性注释审计结果为零。编译条件、测试环境标记、协议分隔符、注释掉的 XML 坐标及技术标识按原含义保留。

## 测试维护及语义核验

源码排版改变后，修正了依赖无空格调用和单行 SQL 字符串的验证工具：合并编译期相邻字符串、忽略源码布局空白，并保留完整列清单、来源表达式和未知动态表达式拒绝规则。原有 25 条 INSERT 的表名、字段集合和覆盖数量完全一致。增加一个多行字符串查询的完整内容与行号回归案例。

| 核验范围 | 结果 |
| --- | --- |
| Java 367 个文件 | 语法结构及字面量等价；比较仅忽略注释、位置、纯常量拼接和单语句控制体的外层花括号 |
| 补充修改的采购收货映射测试 | 保留原审批守卫断言，适配正常空白，并纳入 ERP 完整回归 |
| 前端 211 个脚本、80 个 Vue 模板 | 编译语义等价 |
| Python 43 个文件 | 37 个仅排版及注释变更；生成器另有 3 处 SQL 空白分隔字面量调整；5 个验证工具/测试适配源码布局 |
| SQL 10 个文件及 5 个生成结果 | 词法标记及引号内字面量一致，仅调整 SQL 空白和说明性注释 |
| XML 8 个文件 | 元素、属性及有效文本一致 |

## 本次运行结果

下表均来自本轮新输出，失败、错误、跳过数量均为零。

| 后端核心模块 | 实际执行用例 |
| --- | --- |
| ruoyi-common-trace | 41 |
| ruoyi-erp | 5581 |
| ruoyi-mes | 1285 |
| ruoyi-station | 188 |
| 管理端跨模块定向回归 | 39 |

| 前端及工具 | 实际执行用例 |
| --- | --- |
| PC完整单测 | 2091 |
| PC追溯定向 | 119 |
| PDA完整单测 | 237 |
| Station完整单测 | 256 |
| App完整单测 | 828 |
| 历史对账、转换及迁移工具 | 178 |
| 数据库验证工具的离线单测 | 58 |

其他检查：PC 类型检查及生产构建通过；Station 类型检查及 Electron 应用构建通过；PC/PDA/App 改动文件定向 ESLint 通过；48 个前端文件格式检查、43 个 Python 文件格式检查通过；四个工作区 `git diff --check` 通过。

主要复核命令：

```powershell
mvn -pl ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-station -am test "-DskipTests=false" "-Dprofiles.active="
mvn -pl ruoyi-admin -am test "-DskipTests=false" "-Dprofiles.active=" "-Dtest=MaterialIdentityTraceServiceTest,BatchRecallBoundaryIndependentReviewTest,RecallAllocationHandoffIndependentReviewTest,PdaManufacturingTraceServiceTest,PdaTraceSerializationTest,PdaTraceServiceTest,LabelCanonicalIdentityContextTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

后端复核使用进程级 `JAVA_TOOL_OPTIONS=-Xmx2048m -XX:ActiveProcessorCount=4`，不修改仓库运行配置；各 Maven reactor 和大型构建顺序执行。

```text
PC: npm run test:unit / npm run test:material-trace / npm run type-check / npm run build:prod
PDA: npm test
Station: npm test / npm run typecheck / npm run build
App: npm test -- --maxWorkers=1 --minWorkers=1
Python: python -B -m unittest discover -s script/tools/material_trace/tests -p test_*.py -v
验证工具: python -B -m unittest discover -s script/tools/material_trace/tests_mysql57 -p test_*.py -v
```

Python 复核使用进程级 `PYTHONUTF8=1`，保证 Windows 子进程输出与测试读取编码一致。

首轮发现源码布局依赖和子进程编码问题，修正后已重新执行。一次并行构建发生本机原生内存分配失败，限制进程资源并顺序重跑后的结果以上表为准；原失败日志保留。

## 现有现场与验证边界

本轮未连接或改写业务数据库，未重新执行增量、严格约束或历史修复。原数据库变更登记及四份既有现场记录逐字节保持不变；2026-10-19 本地迁移记录中的原执行摘要和备份证明保持原样。

本轮未重新执行隔离 MySQL 5.7 集成场景、HBuilderX 平台编译、Windows 安装包打包或现场扫码枪、打印机及移动端真机验收。代码等价核验、离线单测及构建结果不代替这些验收。

本机源码快照、语法证明和完整日志保存在 `E:\Nexus\.worktrees\material-trace-20261018\readability-20261019`。同步前已复核四个主工作区的提交、分支及全部目标文件，复制前另存原文件备份，复制后按清单重新核对 SHA-256。
