# 物料追溯历史对账、证据修复与回退（2026-10-18）

离线工具本身只读写JSON及生成待审SQL，不建立数据库或网络连接；本切片未接触任何既有业务库，未提交或推送。另有显式opt-in验证器在自建隔离MySQL5.7容器和合成fixture中实际执行生成SQL，见[实际MySQL验证边界与记录](material-trace-converter-mysql57-validation.md)。该验证不代表现有库迁移、真实历史凭据、现场扫码/打印或生产发布验收。

## 数据源与迁移顺序

ERP增量为 `script/sql/update/2026-10-18-material-trace-erp.sql`，MES增量为同目录 `2026-10-18-material-trace-mes.sql`。只读预检为对应的 `material-trace-preflight-erp.sql` / `-mes.sql`。字段协调及完整新增表清单见 [数据库契约](material-trace-schema.md)。`script/sql/ry-erp.sql` / `ry-mes.sql`只同步空库安装结构，仍包含原有删除重建语句，绝不能导入已有业务库。

1. 由操作者另行核实环境、ERP/MES各自数据库、服务器UUID、基线、备份可恢复性及停写窗口。先审阅只读预检，再由已获授权的操作者取得结果；本工具不会代为连接。不要将口令、Token、完整连接串或真实业务备份加入仓库。
2. 每个数据源单独设置会话变量 `@material_trace_expected_schema` 与 `@material_trace_backup_reference`，只运行该数据源的独立增量。未显式设置、目标库不匹配或缺少基线InnoDB表会拒绝。禁止使用忽略错误的 `--force`。
3. DDL可能隐式提交；这不是跨库事务，也不能用ROLLBACK撤销已完成DDL。失败时保留证据、核对已完成结构和元数据再续跑，不删除业务表、不重新初始化。若同名字段/索引偏离契约，先审查，不能把存在当作正确。
4. 物料、库存、事务、身份及批次的新版本默认为1；物料默认为AUDIT、默认允许混批。`active_sn_id`只保护新版正库存，旧租户不因加索引自动升级。新版活跃SN冲突会停止迁移，不静默修复。历史SN状态/编码、打印SUCCESS及旧数量金额不回填或改写。
5. 新版写入口部署及两库事件通道另由主实现验证。完成该物料的活跃行、数量金额及身份审计后，才可由已授权业务流程切换STRICT；离线工具不直接改物料策略或帮忙跳过业务校验。

## 离线输入与证据边界

工具为 `script/tools/material_trace/cli.py`，仅Python 3.10+标准库，不提供任何数据库/网络连接器、口令参数或执行SQL功能。无子命令仅显示帮助。`audit`只读JSON；`apply/revert`只生成新JSON；所有输出排他创建，拒绝覆盖输入、日志、证据或已有输出。

合成输入见 `script/tools/material_trace/examples/synthetic-snapshot.json` 和 `synthetic-plan.json`。这些文件全部是假数据，不是现场证据或可执行生产配置。快照格式为：

```json
{
  "schema_version": 1,
  "target": {"environment": "synthetic", "erp_database": "fixture_erp", "mes_database": "fixture_mes"},
  "tenant_scope": ["T1"],
  "server_ids": {"erp": "00000000-0000-0000-0000-000000000001", "mes": "00000000-0000-0000-0000-000000000002"},
  "captured_at": "2026-10-18 00:00:00",
  "watermarks": {"erp": "fixture-erp-1", "mes": "fixture-mes-1"},
  "complete_tables": ["这里必须填写实际完整导出的白名单表"],
  "tables": {},
  "evidence": []
}
```

这只是格式说明，不是可通过验证的空快照。修复要求下列35表在选定租户范围完整导出并明确声明，即使表为空也要保留空列表：`bd_material,wm_sn,wm_batch,wm_sn_alias,wm_material_stock,wm_material_stock_tx,pro_sn,lt_lot,lt_wo_consumption,lt_sn_consumption,lt_sn_material_guard,lt_lot_allocation,wm_valuation_group,wm_trace_posting_group,wm_document_sn,pro_completion_manifest,pro_completion_manifest_line,wm_trace_outbox,wm_trace_inbox,mes_trace_outbox,mes_trace_inbox,mes_label_template,mes_label_print_log,mes_label_print_task,pro_completion_material_allocation,wm_trace_material_consumption,wm_trace_material_reservation,wm_trace_material_reservation_line,wm_serial_reservation,wm_serial_sequence,pro_product_lot_output,wm_serial_lifecycle_command,wm_work_order_tracking_policy,wm_work_order_policy_adoption,wm_barcode`。额外只读规则表可按审核白名单导入。修复日志作为独立journal文件及每库日志表保存，不混入业务快照。

2026-10-19新增来源安全要求：`mes_label_print_task`、`pro_completion_material_allocation`、ERP实际消耗/预留头/预留行、`wm_serial_reservation`和`wm_serial_sequence`必须出现在tables及complete_tables中，空表也不能省略。它们和既有`lt_wo_consumption`的每行必须包含metadata的全部列；来源状态、租约、消费量、序列值和新增`source_allocation_id`不能只导出ID或省略列。`source_allocation_id BIGINT NULL`对应实际消耗的原额度来源引用，旧行保持NULL，工具不会推算回填。另纳入实际产品产出pro_product_lot_output及手工SN退休审计wm_serial_lifecycle_command，均要求完整列投影；产出量/原分配引用、退休原因/前后版本/原镜像及回执都参与完整基线。所有这些来源表只读，不进入WRITABLE五表。

完整快照及SQL基线会比较每张来源表的行数和所有已导出字段。新增PENDING打印任务即属于新业务，无需等到发送日志；同一任务状态/租约变化、实际分配/预留消费量变化、发号序列推进，即使行数不变，也禁止旧journal直接revert。旧23/30/31表快照或journal不能补空来源表后继续回退：范围不完整先拒绝；人为补表后完整快照摘要仍不匹配，必须重新核实或走业务补偿。单库待审SQL包含其来源表范围锁和完整行SHA256 CAS，不能代替另一库的停写和新鲜快照核验。

所有行值必须为字符串或null，包括64位ID、版本、数量和金额；顶层schema_version为整数。禁止JSON浮点、NaN、指数数值及重复字段。修复前后的行必须包含各表审核白名单中的完整列，SQL导出不接受部分投影。特别是数值、日期、时间应按数据库 `CAST(column AS CHAR CHARACTER SET utf8mb4)` 的值导出；保留DECIMAL固定小数位及null，不用浏览器浮点、四舍五入或当前时间替代。未知额外列会被拒绝，须先审查兼容性，不能任意加入SQL标识符。

跨库导出必须记录各自事务边界、水位和归档保留范围；两次导出不是天然同一快照。切换/回退窗口应冻结相关写入口和消息消费者，重新取得覆盖35表的同一业务边界导出。不完整导出只能出差异报告，不能据此申请STRICT或直接回退。工具不能独立证明操作者的完整性声明，归档缺口必须用原期初/原凭据补齐，不能用当前差额倒推期初。可选 `opening_balances` 的每条记录含租户、物料、quantity、amount和evidence_id；冻结凭据中必须逐项包含 `{"opening_balance":{"tenant_id":"...","material_id":"...","quantity":"...","amount":"..."}}` 的精确断言，并核验其来源行摘要，引用无关SN凭据不能填平差额。

每个计划项明确指定 `item_id,datasource,table,key,before_hash,changes,evidence_id`。`key`必须包含租户及完整实际主键；`snapshot_hash`绑定整个快照，`target`与快照相同，`run_id`不能重用于另一计划。数量成本、状态、正式/本地原码、签署DHR和原凭据不在修复白名单。库存/事务的冗余SN引用及冗余码，只能在精确正式身份凭据下修正，旧值完整留在日志中。

证据必须有唯一ID、SOURCE_DOCUMENT/SIGNED_REVIEW/PHYSICAL_COUNT类型、原凭据引用、审核人、租户、精确主键及变更断言、明确来源行和来源行摘要。冻结 `artifact` JSON必须与这些字段一致，`artifact_sha256`必须等于其规范摘要。证据和来源都不能跨租户；关联的身份/批次/计价组/过账组必须已存在且物料一致。**同码、同物料、相近日期、相同数量均不是关联证据**，工具不会生成候选后自动确认，也不会创建历史SN或将批次历史均摊到单件。

SHA256用于发现内容变化，不能替代原单据真实性、审核人身份或数字签名验证。保全原始文件和批准的artifact摘要，审核原凭据是否确实证明该关联；不能仅把手填断言标成SOURCE_DOCUMENT就当作已验收。SIGNED_REVIEW只是证据分类，不表示本Python工具完成了签名验真。

## 只读对账与守恒

从仓库根运行，输出路径必须不存在；下例都只处理合成JSON：

```powershell
python -B script/tools/material_trace/cli.py audit --snapshot script/tools/material_trace/examples/synthetic-snapshot.json --output script/tools/material_trace/examples/local-audit.json
python -B script/tools/material_trace/cli.py hash --file script/tools/material_trace/examples/synthetic-plan.json
```

报告包含完整性声明、行数、规范快照摘要、按租户/物料的数量金额差额、分物料STRICT阻断项及逐条证据分类。空表、缺表及缺水位不会伪装成已核验。旧SN多件行、缺失/跨租户/跨物料身份、正库存重复占用、软删除仍有正库存和批次冲突分别报告。别名歧义保留全部事实，不改码、不自动选一个身份。

- 库存：原基本单位数量与完整事务/有证据期初对平。`期末 = 期初 + tx_flag × tx_quantity`，tx_flag为1/-1/0；0只代表预留类动作，不计实物出入。最后实物事务余额还需等于该库存当前数量。
- 金额：以原四位总金额守恒，不用 `数量 × 四舍五入后单价` 重算历史成本。报告库存金额与净事务金额及有凭据期初的差额。计价组和过账组的关联明细分别与冻结数量/金额对平。物料/仓库/批次计价范围及冲正方向仍须按原策略核实。
- 单件拆分：只能由另行审核的业务修复流程以既有真实身份、实盘/单据证据及冻结计价组实施。原组数量、四位金额不能改变；稳定身份排序分配尾差可用于新拆分事实，禁止重算旧凭证。当前工具不会自动拆分多件历史库存，也不会直接写数量/金额。
- MES：组件净消耗为CONSUME减REVERSE；冲正必须引用同租户、同实物、同组件、同批次、同原工单消耗的CONSUME，累计不能超原数量。原工单消耗与单件净消耗不平时保持未分配，不按产量均摊补造。禁止混批时核对守卫批次及正净消耗批次集合。
- 清单：按当前约定GOOD/SCRAP对行数、合格/报废数量、SN一件一行及身份去重进行核对。工具不自行定义公共契约未定义的内容摘要序列化规则；清单content_hash与实际业务冻结字节的验真仍由主服务/原事件证据负责。若服务结果值域不同须先协调后调整工具，不能把未知结果算成零合格。
- 事件：分别校验原始payload字节SHA256、流版本唯一性/缺口、租约成对性、两库相同事件的载荷与聚合身份一致性。归档过的来源须有独立凭据，不能把孤立inbox当作已证明。
- 标签：核对冻结模板UTF8字节摘要，以及rendered_payload按原payload_charset编码后的发送摘要。未知字符集不声明已验证；旧SUCCESS和缺冻结快照记录保留为历史兼容事实。补打必须引用同租户原任务，不用同SN最新任务替代。TCP已发送不等于打印机确认或实际出纸。

报告中的ready只是本次导出/已实现规则未发现阻断项，不是自动切换授权，更不是生产或实物验收。

## 离线apply、幂等与恢复

```powershell
python -B script/tools/material_trace/cli.py apply --snapshot script/tools/material_trace/examples/synthetic-snapshot.json --plan script/tools/material_trace/examples/synthetic-plan.json --output script/tools/material_trace/examples/local-after.json --journal-output script/tools/material_trace/examples/local-journal.json
python -B script/tools/material_trace/cli.py revert --snapshot script/tools/material_trace/examples/local-after.json --journal script/tools/material_trace/examples/local-journal.json --output script/tools/material_trace/examples/local-restored.json --journal-output script/tools/material_trace/examples/local-reverted-journal.json
```

先核验完整快照摘要，再核验每项before_hash及来源行摘要，验证表/列/主键白名单、冻结证据、版本递增与正式身份；全部项目验证完才生成新的JSON。重复目标、重复item_id、跨库错表、伪造身份、原码不一致及新引入的数量/金额/唯一性冲突会拒绝整份计划。已有错误不能作为把库存挂到不守恒计价组的理由；修复目标及其证明必须通过相关校验。

允许修复的既有表为 `wm_sn,wm_material_stock,wm_material_stock_tx,pro_sn,lt_lot`，具体列在 `core.py/metadata.py` 审核；不插入、删除或新建历史业务行，不更改租户/主键。发号注册命令只能由原注册器从既有冻结预留恢复，工具不会补造registration_command。业务状态机、打印、DHR及元数据以外的动作走所属服务。

日志保存运行/项目状态和版本、完整前后镜像、证据JSON及其摘要、计划及整个前后快照摘要。`apply --previous-journal 原日志`对相同runId/plan及完全一致结果幂等返回；已回退runId不可重新复用。所有新输出仍须用新路径。

`revert`要求原APPLIED日志和整个导出范围仍与after_snapshot_hash完全相同，再逐行核对after_hash，按原计划与证据重现，并恢复原始快照摘要。新增库存事务、组件消耗、消息、清单、模板或打印尝试等任何已导出业务变化都会返回 `COMPENSATION_REQUIRED`。这时应保留原修复日志，按原过账组/原消耗分录创建冲正及新修订，走业务权限、租户、幂等、审批和签署留痕；不得覆盖新业务、删除消息、回收号码或强灌旧备份。工具不自动产生补偿交易。

## 生成待审数据库SQL

```powershell
python -B script/tools/material_trace/cli.py emit-sql --snapshot script/tools/material_trace/examples/synthetic-snapshot.json --journal script/tools/material_trace/examples/local-journal.json --target mes:fixture_mes --backup-reference SYNTHETIC-BACKUP-ONLY --allow-db-writes --output script/tools/material_trace/examples/local-apply-mes.sql
python -B script/tools/material_trace/cli.py emit-sql --mode revert --snapshot script/tools/material_trace/examples/local-after.json --journal script/tools/material_trace/examples/local-journal.json --target mes:fixture_mes --backup-reference SYNTHETIC-BACKUP-ONLY --allow-db-writes --output script/tools/material_trace/examples/local-revert-mes.sql
```

三个标记必须同时显式给出：`--allow-db-writes`只确认生成文件包含DML，`--target`是单个数据源及实际库名，`--backup-reference`是操作者另外核实的备份引用。工具永远不执行该文件，也不把这个标记当作对真实库的执行授权。实际执行由用户另行批准。

待审SQL绑定导出中的server_uuid及目标库；使用固定表列、包含租户的完整主键、完整规范行SHA256 CAS、SERIALIZABLE/范围与行锁、受影响行数检查、SQL异常ROLLBACK及每库修复运行/项目日志。同runId核对计划与日志摘要后才幂等返回；回退同时核对日志状态/版本。外部自定义触发器不在镜像范围时拒绝执行。保留原有自动更新时间列，避免元数据修正伪造旧记录的新业务时间。

每个文件只提交一个数据源。本地表/来源事实重新锁定核验；另一库的证据仅在离线导出中核验，因此必须保持已核实的两库停写/消息窗口，不能宣称原子跨库修复。自定义触发器、未在35表中的业务、旁路写入、归档缺口和外部实物变化仍须单独审查。大表全范围锁与逐行CAS的执行成本尚未在真实MySQL测量，不允许据此承诺停机时间或吞吐。文件中的过程创建/清理只涉及专用辅助过程，不删除业务表。

## 验证记录与未覆盖项

```powershell
python -B -m unittest discover -s script/tools/material_trace/tests -v
```

2026-10-18初始SQL工具验证：77个纯Python合成测试通过；覆盖SQL静态契约与安装同步、标签最终字段、ACK可空重试、离线CLI往返、精确十进制对账、歧义/缺凭据、跨租户、来源/前行摘要CAS、幂等、白名单、受保护SQL文本及新业务后拒绝回退。执行前有缺失功能的失败回归，随后实现并重跑；测试无跳过。

2026-10-19补充验证：离线套件100例通过，无跳过；含SQL契约27例、来源安全13例及真实CLI拒绝后不生成回退文件。7种新来源逐一覆盖新增行、同行状态变化、漏表/漏完整声明/漏列、只读写边界、两数据源SQL范围锁及完整行哈希；实际消耗source_allocation_id变化也要求补偿。合成snapshot/plan已同步新范围与摘要，正常apply/revert仍可精确往返。测试只处理合成JSON和待审SQL文本，没有连接或写入现有库。

额外运行原 `script/sql/tests/test_p1_purchase_ddl_contract.py`：6个测试共31个断言失败（含subTest），在未修改HEAD `1ab08b09`读取的原始SQL上同样为31个，失败测试及原因前缀一致。涉及旧空格/显式NULL/种子格式要求；相应原表DDL未改变。本切片没有修改该旧测试或采购DDL来掩盖基线问题。

没有验证MySQL实际解析/重复执行、真实InnoDB约束/并发/死锁、DDL锁时长、真实历史关联、外部凭据签名真实性、原业务计价回放、跨库消息故障、真实打印出纸或生产迁移。初始SQL工具切片未执行Java/Maven/UI检查；同轮MES标签切片另以Java 17隔离编译及JUnit 79例核验，未运行全Maven，完整模块及前端集成由主线程汇总。静态测试不能替代以上验收。

## 2026-10-19 当前库存转换独立入口

原repair五表映射契约保持不变。已新增代码化的[当前库存身份转换](material-trace-stock-conversion.md)：通过真实physical census批准，将完整当前整数库存转换为已有正式SN库存，保留旧号/原聚合流水、原计价范围与数量金额，物料保持AUDIT；不是历史SN生成器或原流水重写器。新业务后只生成补偿任务。相关CLI、可审SQL、证据缺口和当前178例离线验证详见该指南。

取消链路复用的wm_trace_outbox/inbox、mes_trace_outbox/inbox及pro_completion_manifest现也强制每行metadata全部列；不能只保留ID或省略status/result_payload。即使同一APPLIED事件行数与状态未变，新增取消ACK的result_payload变化仍阻止旧journal直接revert及输出回退SQL。


2026-10-19最终扩展范围：35张业务来源表完整快照，18张来源表强制全部列。原工单策略库、独立采纳审批与条码主对象字段均进入行hash/SQL基线；原五表repair写集不变。历史策略UNKNOWN的独立导入入口见[审批采纳指南](material-trace-policy-adoption.md)，只有原归档和独立审批可被实际ERP服务采纳，不能从当前master回填。租户NULL唯一性缺口由[严格约束阶段](material-trace-schema.md#清理后严格租户约束)单独关闭，不自动修复未知租户。
