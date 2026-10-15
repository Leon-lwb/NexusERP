# 自研报表首期续开发计划

> 日期：2026-10-14；分支：codex/report-module-plan；基线：48b9a76d。
> 继续执行用户已批准的 P0 范围、自研核心和基础库方案，完成后提交 Git。

目标：补齐同次运行的不可变排版产物及私有存储的持续恢复能力。保持现有九表结构和业务数据源；不增加 P1/P2 功能。

## 1. 固定排版与字体

已确认问题：ReportRuntimeService.layout 与 ReportExportRenderer 各自从数据快照重新布局，rpt_run.plan_ref 未使用。字体配置或引擎变化可能使同一 runId 的预览与导出不同。

方案：第一次请求排版时生成私有单对象 ZIP，内含 RenderPlan、引擎/格式版本、runId、快照校验和和用于测量的字体副本。字体先复制到受限临时对象，测量与 PDF 都使用该副本。以现有 plan_ref 和 parameters_json 中新增的 planChecksum 原子注册；多请求竞争只能选择一个产物，失败候选立即清理。后续预览/PDF/样式 Excel 读取同一计划；明细 XLSX 和十万行任务不强制排版。旧运行缺少 planChecksum 可在首次排版时创建计划，不修改旧发布定义。

文件：新增 runtime/ReportRenderPlanStore.java；修改 runtime/ReportRuntimeService.java、mapper/JdbcReportStore.java、engine/FontSupport.java、export/ReportExportRenderer.java、config/ReportConfiguration.java。

- [x] 先新增 ReportRenderPlanRuntimeTest，在现有实现上验证失败：layout 后 run.plan 非空；更改 fontPath 后重建 runtime 能返回完全相同的计划并生成 PDF；篡改计划返回 PLAN_INVALID。
- [x] 新增计划存储，JSON/字体分别限额 32/64 MiB，ZIP 仅接受固定两个条目；完整性、身份、引擎版本、取消均校验；临时文件在成功和失败路径清理。
- [x] DAO 新增 publishRenderPlan(tenant, runId, snapshot, reference, payload, claimed)，仅接收有效、SUCCEEDED 的 SNAPSHOT 和首次 plan_ref，并校验导出 lease。
- [x] Runtime.layout 首次原子注册，输家删除候选并读取赢家；每次读取及 PDF 交付前后重新鉴权。原到期清理/孤儿恢复复用单 plan_ref 即可清理字体。
- [x] 回归并发首预览、租约失效、撤权、校验失败、字体不可用、过期清理，以及两种字体文件头的识别。

命令：mvn -pl ruoyi-modules/ruoyi-report -am test -DskipTests=false -Dprofiles.active= -Dtest=*ReportRenderPlan*Test -Dsurefire.failIfNoSpecifiedTests=false。

## 2. 分批孤儿恢复遍历

已确认问题：LocalReportArtifactStore.reap 每次从目录头检查最多 1000 项；当头部都是保留对象时，后面的陈旧孤儿永远不被访问。

方案：保持有界目录游标，跨扫描批次继续遍历；一轮结束、异常和服务关闭时释放目录句柄。每轮最多检查 1000 个文件或目录项，不装载全部路径。使用现有租户、文件类型、年龄和引用校验。

文件：storage/LocalReportArtifactStore.java、storage/ReportArtifactRecoveryTest.java、config/ReportConfiguration.java。

- [ ] 先新增超过 1000 个保留对象的回归，确认数次 reap 能覆盖全部引用检查；从第一轮未访问对象中选择孤儿，验证后续被清理。
- [x] 实现可关闭、同步的扫描游标，验证跨租户进度及关闭后可重新扫描；异常仍保留对象等待恢复。

## 3. 验证、审查与提交

- [x] 运行报表全部后端用例并检查 Tests run > 0；追加既有 system/ERP/MES provider 选择器。
- [x] 新功能完成时按 requesting-code-review 技能安排独立代码审查；主线程同时处理存储回归与联调，修复重要问题后复测。
- [x] 完整 admin 打包；必要时重启已核对 PID 的本地后端，以合成数据验证实际持久队列、配置字体变化后的预览及 PDF；不打印凭证，不导入主初始化 SQL。
- [x] 更新使用指南、首期验收记录和本计划；Git diff --check；只提交本次任务文件，不推送。

最终结果：[本轮验证记录](./2026-10-14-system-report-p0-continuation-verification.md)。278 个后端用例和 60 个前端报表用例通过，类型检查/生产构建/admin 打包通过；两次 JVM 实际验证固定计划与三格式鉴权导出，合成资源已清理。仅本次任务变更提交 Git，不推送。
