# 物料追溯离线工具

仅使用Python 3.10+标准库。所有命令都不建立数据库或网络连接；`apply/revert`只创建新的JSON文件。`emit-sql`只输出一个数据源的待审SQL，永不执行。SQL渲染器同时维护ERP/MES增量和主安装DDL，主安装文件不得用于存量库。

完整说明见 [历史对账与修复指南](../../../doc/system/guides/material-trace-repair.md) 和 [字段协调契约](../../../doc/system/guides/material-trace-schema.md)。

```powershell
python -B script/tools/material_trace/cli.py
python -B script/tools/material_trace/cli.py audit --snapshot script/tools/material_trace/examples/synthetic-snapshot.json
python -B -m unittest discover -s script/tools/material_trace/tests -v
```

`examples/`只包含合成租户T1/000001、SYNTH码和虚拟服务器UUID，不能作为真实历史凭据或正式环境配置。所有输出排他创建，禁止覆盖输入、原始证据和备份。

文件职责：`metadata.py`为审核后的读表/列白名单，`core.py`处理严格JSON和规范摘要，`audit.py`做数量/成本/身份/消耗/事件/标签对账，`repair.py`做证据、CAS与幂等回退，`sql_writer.py`只生成保护性SQL，`cli.py`提供实际离线入口，`schema_contract.py`只渲染DDL。

完整导出当前要求35张表；原工单策略库、独立采纳审批、条码主对象也必须完整导出；新增打印任务、完工实际用料分配、ERP实际消耗/预留头/行、发号预留/序列、实际产品产出及身份退休审计不得省略，即使为空。18张只读来源表必须逐行包含metadata全部列，来源状态/租约/数量/序列/source_allocation_id均参与回退及所属库SQL基线CAS。这些账不开放自动修复写入；任何新增行或状态变化后只能核实并走补偿，不能补空表绕过旧journal范围。

Windows终端若默认编码不是UTF-8，可在当前测试进程先设置`$env:PYTHONUTF8='1'`再运行离线测试，确保CLI子进程输出编码一致。2026-10-19当前离线测试178例通过，未连接数据库。

原repair apply/revert只修复有明确证据的既有行映射元数据，不创建历史SN、拆分多件库存、调整数量成本或覆盖签署DHR。证据不足及已有新业务必须保持原状，另行走审核后的业务补偿。

当前库存身份转换已另行代码化，入口为`convert-check / convert-apply / convert-revert / convert-emit-sql`。见[独立转换契约与真实CLI](../../../doc/system/guides/material-trace-stock-conversion.md)和[合成可审快照/日志/SQL](examples/stock-conversion/)。只用physical census批准的已有ERP正式SN，不创建历史身份、不改原流水、零数量/金额净变化；不能分摊预留或在制。转换额外要求完整conversion_run_states；新增wm_trace_stock_conversion_run只存独立审计，不扩大原五表repair写白名单。2026-10-19当前离线套件178例通过；convert-*入口不执行SQL或连接数据库。

取消不新增表列；四张inbox/outbox及manifest也强制完整投影，以冻结状态和取消回执参与CAS。[当前schema与MySQL输入交接](examples/schema-handoff.json)记录实际字段、当前来源hash和分开绑定的验证报告；主线程/Hume的最终输入复验与统一Maven另行负责。

2026-10-19新增独立opt-in测试runner：[converter_mysql57_runner.py](tests_mysql57/converter_mysql57_runner.py)。只在自建隔离MySQL5.7容器执行本工具生成文件，最终32/32实际用例通过，21个输入hash一致；十轮自有容器均已清理并逐个核实精确CID不存在，未跑Hume通用矩阵。见[验证记录](../../../doc/system/guides/material-trace-converter-mysql57-validation.md)和[红绿摘要](examples/converter-mysql57-validation.json)。


历史WO策略采纳是独立审批导入契约：`policy-adoption-check / policy-adoption-plan / policy-adoption-emit-sql`只将完整原策略归档、独立审批、census及WO完整行CAS写入候选JSON/待审SQL。只INSERT `wm_work_order_policy_adoption`，不直接写policy bank、不改业务或旧历史；当前主数据不能兜底。通过`GET /erp/prd/work/tracking-policy/preview/{woId}`取得原审批来源，随后由`POST /erp/prd/work/tracking-policy/adopt`在ERP REQUIRED→MANDATORY事务及`prd:work:trackingPolicyAdopt`权限下生效并幂等重放，真实登录用户必须为原approvedBy。见[入口与证据](../../../doc/system/guides/material-trace-policy-adoption.md)。

独立严格租户阶段：`python -B script/tools/material_trace/schema_contract.py --strict-constraints`只生成[清理后约束](../../sql/update/2026-10-19-material-trace-strict-constraints-erp.sql)。清理NULL/空白租户、旧唯一性冲突及停写/备份确认全部通过后才NOT NULL；不回填未知租户，不靠CHECK。默认generator也同步该文件，扩展迁移不会自动调用它。
