# 当前库存converter独立MySQL5.7验证（2026-10-19）

实际结果：**最终独立SQL矩阵32/32 PASS，0失败、0跳过；离线工具178/178；入口安全单测4/4**。最终报告为`final-harness-current-20261019-10/report.json`，21个输入在执行期间未变，交付时重新逐项核对hash一致。日期按Asia/Shanghai记为2026-10-19，容器名和报告时间使用UTC，所以前缀为20261018。该矩阵仅验证本工具生成的converter、审批导入和独立严格租户SQL；Hume负责通用SQL/EXPLAIN，主线程负责统一Maven。

用户授权仅用于自建隔离容器与合成fixture。复用tests_mysql57.DockerSandbox，实际MySQL为5.7.44 Community Server，缓存镜像sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb。network=none、无端口、bind mount或已有卷；/var/lib/mysql为768MiB tmpfs，/tmp为64MiB tmpfs。操作及清理均核对精确CID、唯一名称、run-id/工作树标签、镜像、网络和存储。十轮自有容器均已删除，交付时逐个inspect精确CID确认不存在；没有接触任何既有业务库、用户容器或服务。

MySQL默认隔离为REPEATABLE-READ；生成的转换过程使用SERIALIZABLE。实际sql_mode为ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION。DDL协调已完成，但严格阶段仍须目标环境清理、审核和停写后独立执行，本报告不表示生产迁移或验收。

## 实际执行入口与证据

```powershell
$env:PYTHONUTF8='1'
python -B script/tools/material_trace/tests_mysql57/converter_mysql57_runner.py --run --output results/converter-sql/another-unique-run
```

无--run返回2，不构造容器。入口没有host、外部URL、既有数据库或container参数；输出只允许在tests_mysql57/results/converter-sql的新子目录，拒绝覆盖。转换fixture从当前ry-erp.sql提取20张必需ERP来源表及转换审计表，共21张CREATE TABLE，不执行主安装DROP或业务种子。审批用例另取当前prd_work_order的CREATE TABLE。所有业务种子均显式合成并单独记录；原生JSON_OBJECT/CAST导出全部字段，数量、金额、版本和ID以字符串或NULL保留。

真实convert-check/convert-apply/convert-revert/convert-emit-sql子进程生成新快照、完整journal和review SQL，再由runner执行原文件。文件绑定本轮真实server_uuid、新建库名及合成备份引用，不能改目标复用到其他库。容器已清理，复跑须重新生成快照和SQL。

[持久验证摘要](../../../script/tools/material_trace/examples/converter-mysql57-validation.json)保留十轮报告SHA256、最终21个输入hash、32个用例及清理证据。完整本地证据在忽略跟踪的`script/tools/material_trace/tests_mysql57/results/converter-sql/`：

| 轮次 | 结果 | 证据解释 |
| --- | --- | --- |
| red-20261019-01 | 0/15通过 | 实际触发1267，保留原生输出及待审SQL |
| after-literal-20261019-02 | 15/16通过 | 非升序stock ID导出被误判为有新业务 |
| green-20261019-03 | 17/17通过 | 早期快照；历史共享SN及采纳草案后续已修订 |
| legacy-identity-green-20261019-04 | 19/20通过 | 一个证据文件名超过Windows路径长度；缩短fixture路径 |
| policy-bank-inverse-20261019-05 | 21/22通过 | 修复NULL断言；harness执行中变化，该轮不作为冻结验收 |
| policy-adoption-pools-20261019-06 | 26/27通过 | 修复误以为既有计价组为空的fixture断言，改核实原组保留 |
| final-strict-adoption-20261019-07 | 31/31通过 | 采纳草案阶段；不作为最终APPROVED导入语义的证明 |
| final-approval-barcode-20261019-08 | 32/32通过 | 最终语义通过，但harness事后变化，已重跑下一轮 |
| final-transport-freeze-20261019-09 | 32/32通过 | 共享harness事后再次变化，已按最新输入重跑 |
| [final-harness-current-20261019-10](../../../script/tools/material_trace/tests_mysql57/results/converter-sql/final-harness-current-20261019-10/report.json) | **32/32通过** | 最新21输入hash一致，容器已删除 |

每个case保留fresh-erp-tables.sql、fixture-mutations.sql、原生before.json、批准plan、journal、CLI退出码/输出及实际待审SQL执行结果。正常回退另存actual-after.json；新业务用例保留当前快照和补偿JSON，验证未输出不安全恢复文件。预期失败SQL保留ERROR1644或1062，case的PASS表示正确拒绝及全量业务/审计行不变。

## 实际修复与行为

1. `sql_writer.literal`原用CONVERT(hex USING utf8mb4)，coercibility=2，与表列比较可触发1267。改用_utf8mb4十六进制字面量，实测coercibility=4，保留UTF8字节和空字符串，不改数据库排序规则或减弱CAS。
2. 稳定SN顺序和显式stock ID顺序可以不同。原生按主键导出时，旧apply replay/revert把列表顺序误判为新业务；现仅按完整主键比较行集合及审计状态列表，冻结journal、原hash和证据不改。重复行、值、状态或范围变化仍拒绝。
3. 历史qty>1且带旧sn_id的共享汇总库存明确拒绝为LEGACY_MULTI_SN_REVIEW，即使提供全新REGISTERED身份也不自动挑一件继承旧身份。原正库存、旧IN_STOCK/stock_id、旧码和原聚合流水全量不变。早期草案的自动继承不再是支持范围。
4. 新增反向IN_STOCK核查、零数量非零金额及计价组反向总量/金额门禁。已有孤立非零pool或身份悬空不能通过转换掩盖；0/0历史空组可保留。
5. 策略采纳只导入已独立批准的APPROVED/version1申请，不直接写policy bank或倒写生效时间。实际服务互操作另有隔离H2证明，见[审批采纳指南](material-trace-policy-adoption.md)及[摘要](../../../script/tools/material_trace/examples/policy-adoption/interop-verification.json)。

## 最终32例实际覆盖

- 转换及拒绝路径25例：数量→SN、批次→批次+SN、历史单件SN转换/回退；物料、供应商、库位、批次、原单位成本与原聚合流水保持。合成3件/10.0000稳定分配为1件/3.3334、1件/3.3333、1件/3.3333，计价池数量金额守恒；SN位置指向新行，策略CAS并保留AUDIT，batch/mixed不改。
- 重复apply无新增业务/审计；无新业务revert恢复原业务值并保留REVERTED审计，重复revert不变。源hash变化、缺正式身份、未结预留（包括计数0但真实预留存在）、遗漏整物料正库存、跨租户ID碰撞均拒绝。故意在pool写入和原行清零后触发唯一键失败，实际验证全部DML及PREPARED审计回滚。
- 共享历史SN拒绝、REGISTERED替换仍拒绝、源行事后改为共享SN时旧SQL拒绝；IN_STOCK无正库存、零库存金额残留及孤立非零pool拒绝。0/0历史组和既有零库存peer在转换及回退中保留。Unicode、引号、控制字符及非升序stock ID实际hash一致。
- 新v2流水/余额/身份变化或新增工单策略后，旧revert SQL拒绝且当前值不变；CLI只给补偿清单，不输出恢复快照/SQL。
- 审批导入2例：完整独立批准行导入后精确重放，无policy-bank/库存/流水业务写入；原WO变化时拒绝且无部分审计。该测试不冒充HTTP鉴权或过去策略已经生效。
- 独立严格约束4例：确认清理后NOT NULL真实拒绝显式NULL并可重放；旧NULL、旧码冲突或缺少明确cleanup确认时，在首个ALTER前停止。无未知tenant回填。
- 条码来源1例：转换后新增typed wm_barcode阻止旧revert，主对象和原码不重绑。完整35表/18张全列只读来源，包括29列条码均参与离线快照保护；并未在本MySQL矩阵逐表注入所有来源变化。

## 数据库与应用边界

本矩阵是MySQL原生客户端执行生成的单ERP库SQL，不是Java/Spring过账、权限入口、跨库投递或ERP应用事务验收。MES在快照中显式为合成空逻辑范围，未连接MES库。测试中的v2业务由显式合成SQL注入；现场实盘、原始凭据、签署真实性和实际打印均未验真。

独立严格约束有MySQL DDL隐式提交，不能靠事务回滚两个ALTER；目标环境必须完成真实清理、独占停写和可恢复备份确认。扩展安装保留NULL兼容，只有另行执行严格阶段才收紧两张身份/库存表的tenant列。

未验证存量迁移、真实备份恢复、多写者竞争/死锁、大数据范围锁成本、金额极值、max_allowed_packet上限或生产切换。共享多件SN、预留/在制及无法识别的成本范围只出差异任务；不会生成历史SN、DHR、会计凭证或Printer ACK。原五表repair写白名单不变，工具正常CLI不连接数据库。无提交、无全Maven、无既有库写入。
