# 自研报表首期续开发验证记录

> 日期：2026-10-14；分支：codex/report-module-plan；基线提交：48b9a76d。
> 本文仅记录本轮续开发。上一轮完整首期实施见 [原验证记录](./2026-10-14-system-report-p0-verification-report.md)。

## 交付内容

同一快照运行首次请求排版时，固定 RenderPlan 与用于测量的字体副本，封装成一个私有 ZIP 对象。现有 rpt_run.plan_ref 引用该对象，parameters_json 新增 planChecksum；包含布局格式/引擎版本、runId、源快照校验和、字体校验和。后续预览、PDF 和样式 XLSX 读取同一产物。字体按文件头识别 TrueType / TrueType Collection，临时文件扩展名不影响字体加载。

计划 JSON 最多 32 MiB，字体最多 64 MiB，合并对象仍受配置的单文件限额约束。首请求并发采用数据库原子注册；输家、取消和失去权限/租约的未注册候选清理，已注册产物在到期清理确认前保留。旧运行没有 planChecksum 时按首次排版创建；明细 XLSX 不强制布局或字体。

私有存储孤儿扫描使用跨批次目录游标，每批最多检查 1,000 个文件或目录项；原实现反复从头扫描导致后续对象饥饿的缺陷已复现和修复。轮次结束、异常或 Bean 关闭时释放句柄；保留有效引用、近期对象、未知文件及不合法租户目录。

没有新增/改写数据库结构，没有执行积木清理或主初始化 SQL；继续使用 ruoyi-report / engine 1.0.0。排版对象的格式版本为 1，后续格式扩展需显式增加兼容读取器。

## 自动测试

Maven 串行执行。最终命令：

```powershell
mvn -pl ruoyi-modules/ruoyi-report,ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes -am test '-DskipTests=false' '-Dprofiles.active=' '-Dtest=*Report*Test,QualityTraceDatasetProviderTest' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn -pl ruoyi-admin -am package '-DskipTests=true'
# plus-ui
npm run test:report
npm run type-check
npm run build:prod
```

| 检查                                        | 实际结果                                           |
| ------------------------------------------- | -------------------------------------------------- |
| 后端相关选择器                              | 278 用例，0 失败、0 错误、0 跳过                   |
| common-report / system / ERP / MES / report | 9 / 3 / 30 / 10 / 226                              |
| 新计划 Runtime / Store / 存储恢复           | 11 / 9 / 9 用例，其中恢复原有 3 例；净新增 26 例   |
| 前端报表专项                                | 60 用例，0 失败、0 跳过                            |
| 前端类型检查 / 生产构建                     | 均通过                                             |
| admin 完整聚合打包                          | 通过；本命令跳过测试，测试证据来自上一条非零选择器 |

本轮没有改动前端代码。上一轮完整前端 test:unit 的证据保持其原日期/提交边界，本轮未重复运行该全仓套件。

新回归覆盖持久坐标/精确值、真正并发首预览、缓存撤权、提交后取消/撤权、worker lease、到期清理、旧 payload、无字体明细导出、TTC 文件头、ZIP 身份/固定条目校验、JSON 序列化中容量和取消错误，以及超过 1,000 个保留对象、跨租户扫描、目录消失/被替换、扫描关闭与两个目录流的释放。

红绿证据：首次计划 3 例在原实现中产生 2 个 plan_ref 空断言失败和 1 个缺少源字体错误；恢复测试复现只能访问头部 1,000 项；审查补充的 2 例复现容量/取消被 Jackson 包装成可重试存储故障。对应修复后最终选择器均通过。

首轮 admin 重打包遇到 Windows 运行 JAR 文件锁；核对 8080 的 Java PID 32696 为本项目后端并按用户已授权停止，随后完整打包通过。此前失败没有作为验证通过证据。

## 独立审查

按 requesting-code-review 技能分别审查固定排版链路与恢复游标。排版审查发现 JSON 序列化容量/取消信号被 JsonMappingException 包装，已先复现再保留原因链中的原始控制异常，避免对永久容量超限做存储重试。恢复审查发现目录检查后消失会触发全局游标重置，已先复现两种异常再局部跳过消失/被替换目录；关闭回归补充首批重启进度和目录流关闭的直接观测。两位审查者已分别只读复核修复与新增回归，原 Important / Minor 全部关闭，限定范围内无剩余可确认的重要问题。审查者未代替主线程运行 Maven。

## 本机集成验证

使用实际 Spring 装配、开发 MySQL ry-vue 元数据和真实鉴权适配器，仅生成本次唯一代码的合成 provider / 报表 / 运行。最终包第一 JVM 生成 55 行、多页排版并持久保存 plan_ref；进程正常退出。第一轮第二 JVM 比较失败源于辅助脚本将内存 LongNode 与 JSON 解析后的 IntNode 比较，最小探针确认相同 JSON 数值的节点类型不同；规范为实际传输 JSON 后重跑，不因此修改生产代码。失败轮次已清理其合成资源。最终第二 JVM 正常读取第一 JVM 的排版，原字体配置设为不存在路径后，仍通过下列链路：

- 计划 JSON 与重启前传输内容相同，canonical plan_ref 不变。
- 无登录排版请求被拒绝，已登录 HTTP 预览内容与原计划相同。
- PDF、XLSX_LAYOUT、XLSX_DATA 均通过实际持久 worker、认证下载和文件解析。
- PDF 页数等于固定计划页数，中文可读取；三种格式均保留同一 canonical plan_ref。
- 仅本次唯一代码创建的报表/数据集/运行/任务及私有文件被清理；最终数据库回读合成资源 0、发布模板 6、启用数据集 6、运行 0、导出任务 0。

最终 create / verify 两个进程退出码均为 0。本地标准后端使用最终 admin 包恢复运行，应用 JVM PID 26072（启动器 PID 25164）；匿名 HTTP 探针返回认证码 401。前端 80 的既有 PID 16660 保留。

## 交付边界

单节点私有文件存储及兼容引擎下的固定产物回放；每个运行保存其字体副本，容量规划需计入字体与保留期。未知/不兼容引擎仍按原运行校验拒绝；没有声称跨任意升级版本可执行。没有生产部署、业务签收、跨节点共享存储或峰值内存/吞吐压力验收。

开发日志与合成联调脚本仅在忽略目录 .dsh-tmp/report-p0；不提交凭证、Token、源字体文件、业务数据或构建产物。
