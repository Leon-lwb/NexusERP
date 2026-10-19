# 物料身份与追溯集成交付状态

更新：2026-10-19。状态：P0–P5源码、分库脚本、历史工具及自动化验证已交付；生产迁移、真实证据核实和现场验收未执行。

本文件覆盖前序中间检查点；最终事实入口为[实施与验证记录](2026-10-19-material-trace-verification.md)、[四仓库完整变更](2026-10-19-material-trace-changes.json)、[API契约](../guides/material-trace-api.md)及[切换验收表](../guides/material-trace-rollout-checklist.md)。代码均在`E:/Nexus/.worktrees/material-trace-20261018`，未提交或推送。

最终后端reactor10退出0：common-trace41、ERP5581、MES1285、Station188；管理端定向reactor04为39。均0失败/错误/跳过。PC完整2091及专项119、PDA237、App828、Station客户端256和对应类型/构建验证通过。MySQL最终矩阵268及离线转换SQL32通过，均为自建合成环境。

原中间缺口已关闭：WO冻结策略及受控原档采纳、最后一件及兼容完工路由、取消审计/迟到ACK/零良品矛盾结果、PC三个实际详情事实字段、条码typed主对象/不可重绑/GTIN二次断言、批次裸累计和删除、严格阶段NULLtenant防线、Station离线任务跨通道与操作员归属。旧未知身份、未知来源及无归属任务保留待核实，不从当前主数据补造历史。

当前不再存在代码待办或活动构建进程。既有库停写/备份/增量执行、实物盘点、独立审批、硬件/真机、签名发行和生产SLA属于未执行的外部验收。共享native目录的四个构建中间文件副作用及缺事前快照限制已在最终记录明确披露，不声称原目录从未改变。
