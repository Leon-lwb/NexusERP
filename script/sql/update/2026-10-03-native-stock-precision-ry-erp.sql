-- 2026-10-03：库存两表五列原生精度迁移v3；必须另获目标、备份、维护及独立执行授权。
-- 默认直接mysql执行仅SIGNAL；两个真实ALTER模板是普通注释，--force也不会执行它们。
-- 守卫工具：doc/ai/full-flow/scripts/stock_native_precision_migration.py。
-- 不改主初始化SQL：当前全新安装五个原数量列已经DECIMAL(18,4)。
-- 既有2026-10-03 09:27:54探测：MySQL5.7.20-log，库存余额DOUBLE，流水原生DECIMAL(12,2)。
-- 本v2必须同时关闭以下实际物理精度余缺，不能仅迁库存再把流水12,2记成完成：
-- wm_material_stock_tx.tx_quantity/before_quantity/after_quantity：原生DEC无损拓宽18,4。
-- wm_material_stock.stock_quantity/reserved_quantity：原生DOUBLE/DEC严格无损转换18,4。
-- 成本/金额及其他字段不改。已有18,4列为NOOP，不重复DDL，不重写原数量。
--
-- 原数量与默认值：先核全部五列、全部租户及删除行，再允许第一个DDL；范围为正负
-- 99999999999999.9999，小数最多四位。DOUBLE从prepared二进制值判断，不用CAST/ROUND/容差
-- 或显示文本伪认证。不能无损表达（例如DOUBLE的0.1）则BLOCKED；超过15有效位的DOUBLE
-- 原生5.7内部转换尚不证明同值，保守拒绝。DEC拓宽不得缩小原整数域或原小数位能力。
-- 明确NULL策略PRESERVE_NULL_UNKNOWN或REJECT_NULL；NULL与缺库存均不能变成可靠零。
-- 原默认值/可空性/注释/DEC unsigned及zerofill保全；未支持属性及NUL注释拒绝，不静默丢约束。
-- 原flags、单位、单位版本、链头、数量/预留/乐观锁版本、来源/历史单据及候选数量全行保全。
-- 拓宽不恢复历史已经丢失的小数位，不认证单位或来源；旧UNPROVEN及NULL不回填。
-- 迁后即使五列达标，仍sourceCertification=NOT_PERFORMED、historicalPrecisionRecovered=false。
--
-- 固定签名计划与执行：
-- 1. v1/v2旧授权一律拒绝；维护、备份、审查计划及执行v3均须明确scope覆盖两表五列，
--    目标仅DECIMAL(18,4)。三角色Ed25519公钥指纹由外部可信通道取得，不从授权JSON信任。
-- 2. 备份签名绑定实际备份文件哈希/大小、原完整快照、隔离恢复服务器及恢复演练凭据；
--    真实文件逐字节匹配。维护绑定原schema/127.0.0.1:3306/server_uuid/server_id/主机摘要/
--    精确5.7版本、UTC窗口、NULL策略、全局read_only与所有特权写者围栏及其凭据。
--    复制SQL线程和事件调度器必须停写；工具只核状态，不修改全局变量、不启停服务。
-- 3. precheck仅读原生二进制五列和两表全行，生成完整基线元数据、原精度合同、两个明确
--    DDL步骤及三个确定检查点。审查者签署canonical字节，执行者另签计划/签名摘要、scope、
--    stepsSha256、真实OS token操作者、短期限、新nonce及action=EXECUTE_ONCE。
-- 4. 固定顺序先TX、后STOCK，每表至多一个ALTER。获取两表WRITE锁；每一步发送DDL前，
--    再核目标/围栏/期限/备份/检查点，独占nonce并fsync逐步哈希链日志的DDL_INTENT。
--    MySQL5.7 DDL隐式提交，多表不是ACID事务。每一步后重新获取两表WRITE锁并精确回读。
--    ALTER可能解除表锁，间隙靠独立维护围栏，而非假设LOCK TABLES全程覆盖。
-- 5. 日志分别记录verifiedPrefix、knownAppliedSteps、uncertainStep和DDL响应/回读情况。
--    第一表已核、第二表结果未知：PARTIAL_APPLIED_DDL_OUTCOME_UNCERTAIN。
--    首步响应丢失/中断：DDL_OUTCOME_UNCERTAIN，不能认定没提交。回读不符直接BLOCKED。
--    清理失败不报告成功；已消耗nonce保留。绝不自动ROLLBACK或反向ALTER。
--
-- 四项安全审查加固（v3签名与元数据合同，旧v2不可复用）：
-- a. 所有复制写线程必须原生核定：performance_schema开关/线程instrument、全部通道配置/
--    connection_status/applier_status/coordinator/worker/后台线程，交叉SHOW SLAVE STATUS；
--    group_replication_applier不在SHOW清单也不能漏。group插件/变量/成员或instrument未知即拒绝。
--    普通5.7已核插件及变量都不存在才为NOT_INSTALLED；P_S禁用/权限不足不当作零复制。
-- b. 两表全部列读取EXTRA与GENERATION_EXPRESSION；任意stored/virtual generated在DDL前拒绝。
--    metadata全局SELECT/TRIGGER可见性未核或有外键/触发器未支持闭包也拒绝，不能先COPY重算版本。
--    InnoDB目录仅绑定已核ry@002derp/wm_material_stock及ry@002derp/wm_material_stock_tx，
--    BINARY精确匹配，不使用逻辑ry-erp/、通用Unicode/URL编码或Windows大小写猜测。
--    未知目标/目录变体、空行或多行仍拒绝；不因此放松origin/数量/备份/权限门禁。
-- c. 核native TABLES.RowFormat、SHOW CREATE及InnoDB表/空间事实；每条COPY显式ROW_FORMAT
--    保原Compact/Redundant/Dynamic。CREATE_OPTIONS空->row_format=compact的实际改变纳入检查点，
--    不擦除差异。压缩、一般表空间、未知属性或innodb_file_per_table不符合均提前拒绝。
-- d. 同计划原生OS文件锁下遍历所有nonce日志/签名/回执及持久计划索引，锁文件尾保存独立头锚。
--    执行及恢复v3还绑定planHistorySha256/planHeadExecutionSha256/planHighWatermark。
--    只能用最新父链，不能挑A0祖先绕过后续B2成功；即使新nonce/new签名也不能回到低于全计划
--    高水位。截断索引、遗漏/孤立子日志、尾锚不一致或损坏拒绝，不能编辑JSON当修复授权。
--    全日志目录必须独立持久，不与DB一起回滚；不提供整盘恶意回滚的硬件防护，丢失即失败关闭。
--
-- 精确恢复（独立新授权的resume-once，不是编辑日志重试）：
-- 6. 保存原已签计划、原维护/备份、父执行签名及完整.events.jsonl日志。恢复审查者另签
--    recovery-maintenance-v3的新有效窗口/围栏，明确绑定原计划和原备份回执，版本/scope不变。
-- 7. 执行者另签action=RESUME_ONCE、新nonce、最新父执行摘要、完整history/head/high-water、
--    父日志字节摘要、实际原签名检查点
--    completedPrefix/observedSnapshotSha256、remainingStepsSha256及新维护签名摘要。
--    旧执行授权、过期原窗口或一个verified布尔都不能提供续执行权。
-- 8. 恢复现场必须精确匹配原三个检查点之一，全部原数量/行/flags/单位/链头/版本不变。
--    已提交但未记确认的TX不会重复DDL；两表已达目标可只回读确认，不重复DDL。
--    恢复锁后再次核快照、父日志，任何改变拒绝；只跑原计划剩余步骤，不能扩出新DDL。
-- 9. 混合/半列schema、数量或其他字段变化、日志缺失/损坏、工具SQL版本变化均不能自动恢复。
--    本工具不实现真实备份还原；须由主线程独立审查并授权外部恢复。若倒退到已经验证前缀
--    之前，必须新备份、新计划、新授权，不用旧恢复票据。全部仍未获来源认证。
--
-- 依赖：cryptography校验与mysql-connector-python纯Pythonprepared二进制游标。缺项即拒绝，
-- 不安装、不回退文本浮点。内存夹具/文档核对不等于MySQL DDL/并发/恢复实测验收。
-- 公钥/签名为32/64字节独立原始文件；协议字段详见执行器模块说明与离线测试。

SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '必须通过独立签名授权的两表五列原生精度迁移守卫执行';

/* NEXUS_NATIVE_STOCK_PRECISION_DDL_BEGIN wm_material_stock
ALTER TABLE `__SCHEMA__`.`wm_material_stock`
  __COLUMN_CHANGES__,
  ROW_FORMAT=__ROW_FORMAT__,
  ALGORITHM=COPY, LOCK=EXCLUSIVE;
NEXUS_NATIVE_STOCK_PRECISION_DDL_END */

/* NEXUS_NATIVE_STOCK_PRECISION_DDL_BEGIN wm_material_stock_tx
ALTER TABLE `__SCHEMA__`.`wm_material_stock_tx`
  __COLUMN_CHANGES__,
  ROW_FORMAT=__ROW_FORMAT__,
  ALGORITHM=COPY, LOCK=EXCLUSIVE;
NEXUS_NATIVE_STOCK_PRECISION_DDL_END */
