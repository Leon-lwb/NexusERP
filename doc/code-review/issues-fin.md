# ERP fin 财务模块审查结果

> 来源: Claude 代码审查代理（2026-08-01），审查范围: ruoyi-modules/ruoyi-erp/fin（118 文件：arap/gl/pay/cost/fa/report）
> 总计: 13 个问题（CRITICAL 1, HIGH 2, MEDIUM 10）

## CRITICAL

### 1. CostController.java:52 — 采购暂估入账无任何过滤条件，已开票/草稿收货单被重复暂估入账
- **问题**: `receiveMapper.selectList(Wrappers.lambdaQuery(WmMaterialReceive.class))` 无条件查全部收货单（含草稿、已开票），注释写"全部未开票收货单暂估"但无任何状态/开票过滤。CostService.provision() 不检查收货单状态与发票关联。暂估凭证永不复核冲销（PuInvoicePoster 过账正式发票时不冲减暂估），幂等键只按"期间"去重——同一收货单被不同月份重复暂估入账。
- **影响**: 已开票收货单再入账一次（存货/应付虚增）；同一收货单跨月重复暂估；应付暂估与正式应付叠加，资产负债表失真。
- **建议**: 按"已收货确认且未开票"过滤（收货单状态 + 发票关联/开票标志），暂估金额按"收货金额-已暂估金额"计算；正式开票时冲销对应暂估（GR/IR）；期间幂等键增加收货单集合校验。

## HIGH

### 2. FaDisposalServiceImpl.java:91 — 资产处置幂等是 check-then-act，无数据库唯一约束，并发双击可重复处置
- **问题**: selectCount 校验后插入；fa_disposal 表只有主键无 asset_id 唯一索引；处置凭证 sourceId 随机雪花，createAndPost 无法去重。150 行 assetMapper.updateById(asset) 忽略返回行数，并发乐观锁失败静默不更新。
- **影响**: 同资产重复处置（1601 贷方多冲、处置损益虚增）；已处置资产继续计提折旧。
- **建议**: fa_disposal 加 (asset_id, tenant_id) 唯一索引捕获 DuplicateKeyException；资产状态翻转用原子条件更新或检查 updateById 行数。

### 3. GlVoucherServiceImpl.java:261 — createAndPost 幂等为 check-then-act，gl_voucher 无 (voucher_source, source_id) 唯一约束
- **问题**: selectOne 判重后插入；DDL idx_source 仅普通索引。CostPoster.postTx 先查 posted==1 再调 createAndPost——并发都在对方提交前读到"未过账"各自生成凭证。MQ 消费者路径靠 Redis messageId 去重（24h TTL），过期/故障后重投可双写。
- **影响**: 同一业务单据生成两张凭证，应收/应付、收入/成本、存货双计入账，余额表翻倍。
- **建议**: gl_voucher 加 (voucher_source, source_id, tenant_id) 唯一索引，createAndPost 捕获 DuplicateKeyException 重查返回既有凭证。

## MEDIUM

### 4. FaAssetServiceImpl.java:64 — queryById 对不存在资产抛 NullPointerException
- **问题**: selectVoById 返回 null 时 List.of(null) 立即 NPE。
- **建议**: if (vo == null) return null。

### 5. GlPeriodServiceImpl.java:62 — getOrDerivePeriod 并发创建期间，DuplicateKeyException 未捕获
- **问题**: selectOne→insert 无异常处理；uk_period 唯一索引存在，并发首笔过账后到者抛 DuplicateKeyException。
- **影响**: 并发首笔过账 500；MQ 路径 3 次重试进死信，单据永不制单。
- **建议**: 捕获后重查返回既有期间。

### 6. FinClosingServiceImpl.java:703 — 关账失败日志与本步骤同事务，异常回滚导致日志从未落库
- **问题**: log() 在 @Transactional 内直接 insert（无 REQUIRES_NEW），catch→logFailed→throw 后事务回滚日志一并回滚；monthlyClosing 把五步骤包在同一事务。
- **影响**: 月结/年结失败时 FinCloseLog 查不到失败记录，无法审计。
- **建议**: 日志写入用 REQUIRES_NEW 独立事务。

### 7. FinClosingServiceImpl.java:208 — 外币期末重估永不生效（凭证行 currency/amount_currency 从未赋值）
- **问题**: revaluate 只处理 currency != CNY 且 amount_currency 有值的行；全部 poster 构建 GlVoucherLine 都未 setCurrency/setAmountCurrency，凭证头恒 CNY，fxCandidates 恒空。
- **影响**: 月结中外币重估形同虚设，外币科目不重估、汇兑损益不确认。
- **建议**: 完成原币/汇率赋值链路，或暂从月结移除并标注未实现。

### 8. GlVoucherServiceImpl.java:317 — 凭证借贷校验允许负数金额，负金额同行借贷可通过
- **问题**: validateBalanced 只查 d>0&&c>0 与 d==0&&c==0；一行 debit=-5, credit=-5 两项都不触发，多行负数可凑平。
- **影响**: 手工凭证可录入异常负数分录，余额表方向错乱。
- **建议**: 拒绝借贷同时非零（含负数）的行；手工凭证金额 ≥0，红字仅业务 poster 生成。

### 9. FinSettlementServiceImpl.java:156 — 收付款单/核销金额 (18,4) 与凭证/余额 (18,2) 精度错配
- **问题**: fin_receipt/settlement (18,4)，gl_voucher_line/gl_account_balance (18,2)；10.125 落凭证 10.13，两行合计明细 20.26 vs 余额表 20.25。
- **影响**: 试算平衡表与凭证明细差 0.01 级，报表核对不平。
- **建议**: 统一 (18,2)，入账前 setScale(2, ROUND_HALF_UP)。

### 10. PayReceivedConsumer.java:87 — 收款单无 pay_order_number 唯一约束，重投/缓存失效时重复建单重复核销
- **问题**: 建单仅靠 Redis messageId 幂等（24h TTL），fin_receipt idx_pay_order 普通索引；重投插第二条 CONFIRMED 收款单再次 FIFO 核销。
- **影响**: 客户收款重复入账并核销更多发票。
- **建议**: 建单前按 pay_order_number 查重或加唯一索引捕获 DuplicateKeyException 跳过。

### 11. FinClosingServiceImpl.java:383 — 期间重开后再过账再结账，期初结转被跳过
- **问题**: carryForward 以目标期间 opening 非零即跳过；reopenPeriod 后补录凭证再关账，新增发生额不补结转。
- **影响**: 重开后补录凭证，后续期间报表期初数错误。
- **建议**: 结转增量（记录已结转快照，重结只转增量），或重开先"反结转"。

### 12. FinSettlementServiceImpl.java:261 — 凭证未生成时清账静默跳过，未清项永久残留
- **问题**: clearReconLine 在 voucher==null（GL 异步制单未完成/失败）仅 warn 返回，无补偿；核销完成但 open_item 行永不置 cleared。
- **影响**: 未清项报表显示已核销项为未清，再次核销报"余额不足"。
- **建议**: 核销事务内同步等待/补偿清账，或未清项查询按 verified_amount 计算。

### 13. FaDepreciationServiceImpl.java:147 — 折旧计提并发执行：唯一索引兜底抛未处理异常；资产累计折旧更新忽略乐观锁失败
- **问题**: 并发两次 runDepreciation 第二次撞 uk_asset_period 抛 DuplicateKeyException（500）；assetMapper.updateById 忽略行数，版本冲突时累计折旧静默不更新但凭证/计划已落库。
- **影响**: 并发计提报错无指引；资产累计折旧与凭证/记录不一致。
- **建议**: 捕获 DuplicateKeyException 返回"期间已有计提"；检查 updateById 行数失败回滚；计提与资产更新用原子 UPDATE。
