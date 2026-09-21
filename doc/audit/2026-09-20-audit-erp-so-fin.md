# ERP so + fin 审计报告（2026-09-20）

> 审计人: 子代理 | 范围: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/so`（87 文件）、`.../erp/fin`（118 文件）、`script/sql/ry-erp.sql` 相关表 DDL、`script/sql/ry-vue.sql` 菜单种子 | 覆盖文件数: 30（实际读过，另以 grep 核验约 40 个文件的字段/权限码） | 分支: feature/erp-mes-iot-audit-fix
> 口径: `doc/audit/2026-09-20-审计执行规范.md`；已知清单复核对象: `doc/erp/plans/2026-09-19-so-module-handover.md` §3.2/§6/§8
> so 定位: 只做残留/回归/跨模块链路；fin 为本轮重点。

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| FIN-ARAP-01 | P0 | 校验/幂等 | 收付款单 `status` 由客户端直传，可建/改为 CONFIRMED 而**从不发 GL 过账消息** → 总账永久缺凭证 | `FinReceiptServiceImpl.java:73`、`:122`、`:143`；`FinReceiptBo.java:67` |
| FIN-FA-01 | P1 | 金额精度 | 折旧回写净值的 SQL 在累加 `cumulative_depreciation` 之后再次减本次折旧额，**净值被多扣一次**（MySQL 赋值左到右） | `FaDepreciationServiceImpl.java:166` |
| FIN-GL-01 | P1 | 金额精度 | 外币期末重估的原币净额只累加借方向、**不扣贷方**，贷方外币科目重估差额方向与金额双错（约 2 倍） | `FinClosingServiceImpl.java:233`、`:252` |
| FIN-GL-02 | P1 | 状态机/幂等 | 已关账(CLOSED)期间仍可反过账（只拒 LOCKED），反过账后因 `post()` 要求 OPEN 而**永久无法重新过账**，余额已回冲 | `GlVoucherServiceImpl.java:209`、`:178` |
| FIN-ARAP-02 | P1 | 功能缺失 | **反核销(unsettle) 完全不存在**，收款单一旦确认+核销即不可逆（作废也被明确拒绝） | `FinSettlementServiceImpl.java:70`；`FinReceiptServiceImpl.java:180` |
| FIN-PERM-01 | P1 | 权限 | 7 个 fin 权限码在 `sys_menu` 无种子 → 非超管调用必然 403（收款单改/删/导出、凭证导出、汇率增删改） | `FinReceiptController.java:43/67/75`、`GlVoucherController.java:43`、`GlCurrencyRateController.java:44/51/58` |
| FIN-GL-03 | P2 | 校验 | 手工凭证可自带 `voucherSource=SO_INVOICE`+`sourceId=发票ID` 并过账 → 同一发票重复入账，且抑制后续自动过账 | `GlVoucherServiceImpl.java:94`、`:263` |
| FIN-GL-04 | P2 | 幂等 | 重估幂等键 `periodId*100+1` 对雪花 `period_id` 溢出 long，键不可预测/可碰撞 | `FinClosingServiceImpl.java:203`；`GlPeriodServiceImpl.java:77` |
| FIN-FA-02 | P2 | 功能缺失 | 报废(SCRAP)带残值收入时生成的凭证借贷不平衡 → 直接抛异常，该业务无法过账 | `FaDisposalServiceImpl.java:108`、`:114` |
| FIN-FA-03 | P2 | 校验 | 资产处置凭证来源被标成 `DEPRECIATION`，且幂等键用随机雪花 ID 使同源幂等失效 | `FaDisposalServiceImpl.java:130`、`:132` |
| FIN-FA-04 | P2 | 金额精度 | 年数总和法在超使用年限资产上 `remaining` 可为负 → 生成负折旧额（上限只挡正数） | `FaDepreciationServiceImpl.java:204`、`:213` |
| FIN-ARAP-03 | P2 | 金额精度 | `fin_receipt.amount`/`fin_settlement.settle_amount` 为 (18,4)，凭证/余额表 (18,2)，核销按 4 位累加、按 2 位入账 → 永久对不平 | `FinSettlementServiceImpl.java:80`、`:159`；`ry-erp.sql:2923` |
| ERP-SO-01 | P2 | 数据一致 | `reserved_qty` 单向失真：取消/关闭/强制履行只释放库存侧预留，不回写行字段（后续出库会扣一个不对应任何有效预留的数） | `SoSalesOrderHeaderServiceImpl.java:532/617/642`；`WmStockReservationServiceImpl.java:115`；`SalesOutStrategy.java:176` |
| ERP-SO-02 | P2 | 数据错 | `updateOqcStatus` 仍按 (soId, materialId) 更新 → 同订单同物料多行的 OQC 结果被一起改写 | `SoSalesOrderLineServiceImpl.java:104` |
| ERP-SO-03 | P2 | 并发 | 信用额度 TOCTOU 仍在：`so_credit_usage` 无 `(tenant_id,so_id,status)` 唯一约束，先 SUM 后 INSERT | `ry-erp.sql:5163`；`CreditCheckServiceImpl.java:54/85` |
| ERP-SO-04 | P2 | 幂等 | 报价转单无唯一约束（`so_sales_order_header` 仅 `uk_so_no`）→ 重复转单生成多张订单 | `ry-erp.sql` so_sales_order_header 段；`SoSalesOrderHeaderServiceImpl.java:1321` |
| ERP-SO-05 | P2 | DDL | 价目表两张表仍无 `del_flag`（物理删除）；发票/合同**已有** `del_flag`（原判定过时） | `ry-erp.sql` so_price_list 段；`SoInvoice.java:212`、`SoContract.java:145` |
| ERP-SO-06 | P2 | 功能缺失 | 合同行 `deliveredQty`/`invoicedQty`/`lineStatus` 仍为死字段（全仓无写入点） | `SoContractLine.java:102/107/112`；`SoContractLineVo.java:84/87/90` |
| ERP-SO-07 | P2 | 权限 | 独立权限点仍缺失：`so:return:confirm/execute`、`so:quotation:confirm/convert`、`so:contract:terminate` 全部复用 `edit` | `SoReturnController.java:131/147/164`；`SoQuotationController.java:132/149`；`SoContractController.java:161` |
| ERP-SO-08 | P2 | 性能 | `getUsedAmount` 仍全量载入内存求和（应 `SUM()`） | `CreditCheckServiceImpl.java:149` |
| ERP-SO-09 | P2 | 跨模块链路 | Q8 自动建的委外单 vendor 可空且绕过服务层校验；调拨单数量直接取订购量、未按来源仓可用量裁剪 | `SoSalesOrderHeaderServiceImpl.java:1112`、`:1145`、`:1222` |

## 1. P0 详述

### FIN-ARAP-01 收付款单状态可被客户端伪造，绕过 `confirm()` → 总账永久缺凭证

- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/arap/service/impl/FinReceiptServiceImpl.java:73`（insertByBo）、`:122`（updateByBo）、`:143`（confirm）；`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/arap/domain/bo/FinReceiptBo.java:67`
- **现状**:
  - `FinReceiptBo` 暴露 `status`（`:67`）与 `source`（`:64`）；`insertByBo` 用 `MapstructUtils.convert(bo, FinReceipt.class)` 整体映射，**只在 `status` 为空时兜底 DRAFT**（`:78-80`）；
  - `updateByBo` 同样把 BO 转实体后 `updateById`（`:122-124`），未剔除 `status`；
  - 而**唯一**会发出 `GlPostingMessage(RECEIPT)` 的入口是 `confirm()`（`:143-168`，`messageProducer.send(...)` 在 `:167`）。
- **触发路径**: `POST /erp/fin/receipt`（或 `PUT /erp/fin/receipt`）请求体带 `"status":"CONFIRMED"` → 落库即 CONFIRMED，全程不产生任何 GL 消息；界面「收付款单」显示已确认，可继续被 `settle()` 核销。
- **后果**: 收付款在 AR/AP 子账已发生并被核销，但总账（`gl_voucher`/`gl_account_balance`）**没有对应收款/付款凭证** → 银行/应收科目少计，试算平衡表与明细账对不上，且事后无法从业务单据追溯（因为单据本身就是"已确认"态）。这与 09-19 交接报告 §3.2(2) 在 so 侧修掉的"客户端可伪造服务端权威字段"是**同一系统性缺陷，fin 侧未修**。
- **修复建议**: `insertByBo` 强制 `entity.setStatus(DRAFT)`、`entity.setSource(MANUAL)`（`PAY_CALLBACK` 只允许 `PayReceivedConsumer` 内部走 `save()`）；`updateByBo` 显式拷贝可编辑字段（方向/金额/客户/供应商/日期/备注），**不覆盖 status/source/settled_amount/version**；或对 `status != DRAFT` 的入参抛 `ServiceException`。
- **影响面**: `FinReceiptServiceImpl`、`FinReceiptController`（add/edit/confirm/cancel）、前端 `plus-ui/src/views/erp/fin/receipt/*`；无需 DDL；建议补测试（BO 传 CONFIRMED 应被拒 / 落库仍为 DRAFT）。
- **测试现状**: `ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/fin/` 仅有 `GlVoucherServiceImplTest`（8 例），**FinReceipt/FinSettlement 无任何单测**。

## 2. P1 详述

### FIN-FA-01 折旧回写净值多扣一次（SQL 赋值顺序）

- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/fa/service/impl/FaDepreciationServiceImpl.java:166`
- **现状**:
  ```java
  int assetRows = assetMapper.update(null, Wrappers.<FaAsset>lambdaUpdate()
      .eq(FaAsset::getAssetId, asset.getAssetId())
      .eq(FaAsset::getVersion, asset.getVersion())
      .setSql("cumulative_depreciation = cumulative_depreciation + " + amount)   // ①
      .setSql("net_book_value = original_value - cumulative_depreciation - " + amount) // ②
      .set(FaAsset::getVersion, asset.getVersion() + 1));
  ```
  MySQL 单表 UPDATE 的 SET 子句**从左到右求值，后面的赋值会看到前面刚写入的新值**，故 ② 实际等于 `original - (cum_old + X) - X = original - cum_old - 2X`。
- **触发路径**: 任一期间执行 `POST /erp/fin/fa/depreciation/run`（`FaDepreciationController:49`，权限 `fin:fa:depreciation:run`）。
- **后果**: `fa_asset.net_book_value` 每计提一次就少一个 X，误差逐月累积；资产卡片/资产列表的账面净值全部偏小（例如原值 10000、残值 0、10 期直线每期 1000，第 3 期后净值应为 7000，实际 4000）。折旧额、凭证与累计折旧本身正确（`calcMonthlyDepreciation` 用 `original - cumulative` 现算），因此该错误**不会自我暴露**，只在 `net_book_value` 被读的地方（资产卡片、处置前人工核对）显现。
- **修复建议**: 去掉 ② 末尾的 `- amount`（因 ① 已生效），即 `net_book_value = original_value - cumulative_depreciation`；或改为 `net_book_value = original_value - (cumulative_depreciation + X)` 的显式括号形式以避免依赖求值顺序。
- **影响面**: 仅 `FaDepreciationServiceImpl`；无需 DDL；存量库需一次性重算 `UPDATE fa_asset SET net_book_value = original_value - cumulative_depreciation`；建议补单测（计提一期后断言净值 = 原值 − 累计折旧）。
- **测试现状**: `fin` 目录下无 FA 单测。

### FIN-GL-01 外币期末重估：原币净额未按借贷方向取负

- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/report/service/impl/FinClosingServiceImpl.java:233`（聚合）、`:252`（算差额）、`:258-264`（生成分录）
- **现状**:
  ```java
  dc[0] = dc[0].add(nz(l.getDebit())).subtract(nz(l.getCredit()));  // 本币净额：正确（贷方取负）
  dc[1] = dc[1].add(nz(l.getAmountCurrency()));                    // 原币净额：只加不减
  ...
  BigDecimal revalNet = fxNet.multiply(rate.getRate());
  BigDecimal diff = revalNet.subtract(bookNet);
  ```
- **触发路径**: 月结向导「外币重估」`POST /erp/fin/closing/revaluate`（`FinClosingController:62`），或 `monthlyClosing` 内部调用（`:292`）；需期间内存在 `currency != CNY` 的凭证行。
- **后果**: 对**贷方余额**的外币科目（外币应付账款、预收账款等，典型只有贷方行）`fxNet` 被算成正数，而 `bookNet` 为负 → `diff ≈ 2×|账面|`，且方向相反（把应调增写成调增两倍）。生成的重估凭证金额错误、汇兑损益虚增，并已过账进 `gl_account_balance`（`:275` 走 `createAdjustVoucher` 直接过账）。
- **修复建议**: `dc[1] = dc[1].add(nz(l.getDebit())).subtract(nz(l.getCredit()))`（原币与记账方向同号）；或明确约定 `amount_currency` 恒为正并按 `debit/credit` 决定符号。修复后需对已重估期间人工复核（重估幂等键为 `periodId*100+1`，重复调用不会重算，需先删/红冲旧重估凭证）。
- **影响面**: `FinClosingServiceImpl.revaluate`；无需 DDL；需补测试（贷方外币科目重估差额 = 原币净额×期末汇率 − 本币净额，且符号正确）。
- **测试现状**: `fin` 无 closing 单测。

### FIN-GL-02 已关账期间可反过账，且反过账后无法重新过账

- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/gl/service/impl/GlVoucherServiceImpl.java:209`（unpost 只拒 LOCKED）、`:178`（post 要求 OPEN）、`:215`（先回滚余额再改状态）
- **现状**: `unpost()` 仅当期间 `LOCKED` 时拒绝；`post()` 要求期间 `OPEN`。CLOSED 期间（已执行月结、已生成损益结转与期末结转凭证）的凭证可被反过账 → 余额被 `reversePosting` 回冲、凭证置 DRAFT，此后 `post()` 抛"期间【】非开放状态,不可过账"。
- **触发路径**: 会计期间页面「反过账」按钮（`GlVoucherController:88`，权限 `fin:gl:voucher:unpost`）对一张属于已关账期间的凭证操作。
- **后果**: ① 已关账期间的试算平衡表/资产负债表数字在关账后被静默改变（结转凭证仍留在账上，形成"结转了但发生额被抽掉"的不一致）；② 该凭证永久卡在 DRAFT，除非先「反结账」重开期间；③ 月结的"无草稿凭证才可关账"约束（`GlPeriodServiceImpl.java:100-105`）在关账后失效。
- **修复建议**: `unpost()` 增加 `CLOSED` 判断（提示先反结账），或要求调用方显式传"允许已关账调整"标志并同时置 `gl_period.closing_step`；最简做法：`if (!OPEN.equals(period.getStatus())) throw ...`（与 `post` 口径对称）。
- **影响面**: `GlVoucherServiceImpl.unpost`、期间状态机、报表口径；无需 DDL；需补测试（CLOSED 期间 unpost 应被拒）。
- **测试现状**: `GlVoucherServiceImplTest`（8 例）覆盖负数分录口径，未覆盖期间状态组合。

### FIN-ARAP-02 反核销（unsettle）功能完全缺失，核销不可逆

- **证据**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/arap/service/impl/FinSettlementServiceImpl.java:70`（唯一写入口 `settle`）；`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/arap/service/IFinSettlementService.java`（无 unsettle/reverse）；`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/arap/service/impl/FinReceiptServiceImpl.java:180`
- **现状**: `IFinSettlementService` 只有 `settle/queryPageList/queryList/queryOpenItems`；`FinReceiptServiceImpl.cancel` 的异常文案自认："仅草稿可作废,当前【{}】(已确认单据需先反核销+反过账,F1 暂不支持)"。核销同时改了三处不可逆状态：`fin_settlement` 新行、发票 `verified_amount`/`status`、`gl_voucher_line.cleared`（`:147`、`:159`、`:171-174`）。
- **触发路径**: 误核销（选错发票/金额多输）后，界面无任何撤销入口；`FinSettlementController`（`:35-51`）只有 list/settle/query。
- **后果**: 只能改库修正；发票被错误置为 VERIFIED 后无法再被正确核销，收款单 `settled_amount` 虚高、剩余可核销额错误，GL 未清项被错误清账。
- **修复建议**: 新增 `unsettle(settlementId)`：反向减 `verified_amount`（按 `SoInvoiceServiceImpl.verifyInvoice` 的对称原语，含 `>=` 守卫与乐观锁）、回退 `fin_receipt.settled_amount` 与状态、`gl_voucher_line.cleared` 复位、删/标记 `fin_settlement`；补 `fin:arap:settlement:unsettle` 权限码与 `sys_menu` 种子（注意本报告 FIN-PERM-01 的同类问题）。
- **影响面**: `fin/arap`（service/controller/mapper）、前端 `erp/fin/settlement/index`；无需 DDL（`fin_settlement` 有 `del_flag`）；需补测试。
- **测试现状**: 无。

### FIN-PERM-01 7 个 fin 权限码无 `sys_menu` 种子 → 非超管 403

- **证据（使用侧）**: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/arap/controller/FinReceiptController.java:43`（`fin:arap:receipt:export`）、`:67`（`fin:arap:receipt:edit`）、`:75`（`fin:arap:receipt:remove`）；`.../fin/gl/controller/GlVoucherController.java:43`（`fin:gl:voucher:export`）；`.../fin/gl/controller/GlCurrencyRateController.java:44/51/58`（`fin:gl:currency:add/edit/remove`）
- **证据（种子侧）**: `script/sql/ry-vue.sql` 中 `fin:` 前缀菜单共 26 条（`92683-92698`、`92700-92708`、`92710-92741`），覆盖 `fin:gl:account:*`、`fin:gl:voucher:list/query/add/edit/remove/post/unpost`、`fin:gl:period:list/edit`、`fin:gl:currency:list`、`fin:gl:report:list`、`fin:arap:receipt:list/query/add/confirm/cancel`、`fin:arap:settlement:list/query/settle`、`fin:arap:report:list`、`fin:fa:*`、`fin:cost:*`、`fin:report:*`、`fin:closing:*`、`fin:pay:callback`；**无**上述 7 个码（也无 `fin:gl:currency:add/edit/remove`）。
- **触发路径**: 给某角色分配「收付款单/记账凭证/币种汇率」菜单（父菜单权限码已存在）后，点击「修改」「删除」「导出」「新增汇率」→ `SaCheckPermission` 失败 403（超管因 `*:*:*` 不受影响，因此开发自测不易发现）。
- **后果**: 收付款单的编辑/删除、凭证导出、汇率维护在 UI 上对业务角色不可用（功能不可达）；`fin:gl:currency:*` 三个码缺失还会导致币种汇率只能看不能维护。
- **修复建议**: 在 `script/sql/ry-vue.sql`（并补一条 `script/sql/update/` 存量库脚本，`INSERT IGNORE`）补齐 7 条 `F` 类型按钮菜单，父级分别为 6121（收付款单）、6103（记账凭证）、6105（币种汇率）；或把 controller 上这些码统一收敛到已存在的 `:edit`（不推荐，会放大 ERP-SO-07 那类粒度问题）。
- **影响面**: `ry-vue.sql` + 存量库脚本 + 角色授权；前端按钮 `v-hasPermi` 同步；需 DDL（菜单表 INSERT）。
- **测试现状**: 无（属种子缺失，无法用单测覆盖，建议加"controller 权限码 ⊆ 菜单种子"的静态校验测试）。

## 3. P2 详述

| 编号 | 标题 | 证据 | 现状 / 后果 | 修复建议 |
|---|---|---|---|---|
| FIN-GL-03 | 手工凭证可伪造来源+来源单据ID，重复过账同一发票 | `GlVoucherServiceImpl.java:94`（insertByBo 透传 voucherSource/sourceId）、`:263`（createAndPost 按 source+sourceId 幂等） | `GlVoucherBo` 暴露 `voucherSource`/`sourceId`（`GlVoucherBo.java:41/44`），仅当为 null 才兜底 MANUAL；有 `fin:gl:voucher:add` 者可建一张标着 `SO_INVOICE`+`sourceId=<发票ID>` 的草稿并 `post()` → 同一发票在 GL 出现两张凭证；此后真实自动过账反而因幂等命中而跳过（用假凭证"顶替"真凭证） | `insertByBo` 强制 `voucherSource=MANUAL`（手工入口只允许 MANUAL），业务来源只能由 `createAndPost` 内部产生；或对非 MANUAL 来源拒绝手工建单 |
| FIN-GL-04 | 重估幂等键对雪花 ID 溢出 | `FinClosingServiceImpl.java:203`（`periodId * 100 + IDEMPOTENT_REVALUATION`）、`GlPeriodServiceImpl.java:77`（`periodId` 由 `IdGeneratorUtil.nextLongId()` 生成） | 雪花 ID ≈1.9e18，×100 溢出 long，键值不可预测；同一期间内幂等仍成立（同键），但不同期间/不同 `VOUCHER_SOURCE` 的键存在碰撞可能 → 误判"外币已重估,复用凭证"而跳过重估。`FaDepreciationServiceImpl.java:143` 的注释已明确该模式被废弃（改用雪花键），此处是遗留 | 改为 `IdGeneratorUtil.nextLongId()` 作 `sourceId`，幂等改由"同期间同来源是否存在凭证"查询保证（与折旧一致） |
| FIN-FA-02 | 报废带残值收入时凭证不平衡，业务直接失败 | `FaDisposalServiceImpl.java:108`（`gainLoss = price + accumulated - original`）、`:114`（仅 `DISPOSAL_SALE` 且 `price>0` 才加银行存款借方） | `disposalType=SCRAP` 且 `disposalPrice>0`（报废残值回收，常见）时：损益含 price 但借方无银行存款行 → `validateBalanced` 抛"借贷不平衡" → 报废无法过账（用户只能把价格填 0 规避，导致处置收入不入账） | 银行存款借方行改为"`price.signum() > 0` 即生成"（与类型无关）；`disposalPrice` 大于 0 时校验必须选 SALE 或给出明确提示 |
| FIN-FA-03 | 处置凭证来源与幂等键错 | `FaDisposalServiceImpl.java:130`（`setVoucherSource(SOURCE_DEPRECIATION)`）、`:132`（`disposalId = IdGeneratorUtil.nextLongId()` 作 sourceId） | 处置凭证在 GL 中来源显示为"折旧"，来源维度统计/对账把处置混入折旧；随机 sourceId 使 `createAndPost` 的同源幂等永不生效（当前靠"同资产仅一条处置记录"守卫，属 TOCTOU） | 新增 `GlConstants.SOURCE_FA_DISPOSAL = "FA_DISPOSAL"` 并同步 `ry-erp.sql:3158` 注释；`sourceId` 用处置单 ID（先插处置记录拿 ID 再制单，或先取号） |
| FIN-FA-04 | 年数总和法可产生负折旧 | `FaDepreciationServiceImpl.java:204`（`remaining = life - (monthsInService - 1)`）、`:206`、`:213`（上限只挡正数） | 资产超过使用年限仍在使用、且因四舍五入残留 `netValue - salvage > 0` 时，`remaining` 为负 → 折旧额为负 → 该期贷方累计折旧为负（红字折旧），凭证与资产累计折旧被反向调整 | `remaining = Math.max(0, life - (monthsInService - 1))`，并在 `amount.signum() < 0` 时归零；建议对 `monthsInService > life` 直接按 `netValue - salvage` 一次性提完 |
| FIN-ARAP-03 | 核销精度 4 位 vs 入账 2 位 | `FinSettlementServiceImpl.java:80`（`settleAmount.setScale(2)`）、`:159`（`settled_amount + settleAmount`）、`ry-erp.sql:2923-2924`（receipt amount/settled_amount `decimal(18,4)`）、`ry-erp.sql:3197-3198`（voucher debit/credit `decimal(18,2)`） | 收款单可录 10.125；核销时凭证/余额按 10.13 入账，但 `fin_receipt.settled_amount` 与 `fin_settlement.settle_amount` 按 10.125 累加 → 全额核销判定（`newSettled.compareTo(amount) >= 0`）与 GL 金额长期相差分位；反复部分核销可累积到 0.01 级别以上 | 统一口径：把 `fin_receipt.amount/settled_amount`、`fin_settlement.settle_amount` 收敛为 `decimal(18,2)`（DDL），或核销前把收款单金额也按 2 位规范化并在 `insertByBo` 校验小数位 |
| ERP-SO-01 | `reserved_qty` 单向失真（残留） | `SoSalesOrderHeaderServiceImpl.java:532`（fulfill 兜底 unreserve）、`:617`（close）、`:642`（cancel）；`WmStockReservationServiceImpl.java:115`（`releaseReserved` 确实扣减 `wm_material_stock.reserved_quantity`）；`SalesOutStrategy.java:130/176` | 取消/关闭/强制履行只释放**库存侧**预留，`so_sales_order_line.reserved_qty` 保持非 0；后续对同一行的出库会按 `min(issueQty, reservedQty)` 走"消费预留"分支并条件扣减行字段（`.ge(reservedQty, totalConsumeReserved)`），即**扣减一个已不对应任何有效库存预留的数**；并发出库时第二个请求会因该守卫失败而抛"SO行预留数量更新失败" | `unreserve` 成功后同步 `UPDATE so_sales_order_line SET reserved_qty = 0 WHERE so_id = ?`（或按行回写），并把 `SalesOutStrategy` 的消费预留口径改为以库存侧为准（行字段仅展示） |
| ERP-SO-02 | OQC 状态按物料误伤多行（残留） | `SoSalesOrderLineServiceImpl.java:104-113` | 更新条件为 `so_id + material_id`，同一订单同一物料的多行（不同交期/仓库）会被一次全部改写为同一 OQC 结论 | 接口改为按 `lineId`（或 `soId+materialId+batchId`）定位；调用方需同步传行 ID |
| ERP-SO-03 | 信用额度 TOCTOU（残留） | `ry-erp.sql:5163-5167`（`so_credit_usage` 仅普通索引，无唯一键）、`CreditCheckServiceImpl.java:54`（先 `getUsedAmount` 求和）、`:85`（后 insert） | 两个并发 `confirm()` 可同时读到相同的已用额度并各自冻结 → 客户信用超额使用；无 DB 约束兜底 | 加唯一键 `uk_credit_so(so_id, tenant_id)`（同一订单只允许一条有效占用）并在 `checkAndFreeze` 捕获 `DuplicateKeyException` 后重查；更彻底的做法是把"已用额度"改成对 `so_credit_usage` 的条件插入+重算校验 |
| ERP-SO-04 | 报价转单无唯一约束（残留） | `ry-erp.sql` so_sales_order_header 段（仅 `uk_so_no(so_no,tenant_id)`）；`SoSalesOrderHeaderServiceImpl.java:1321` 附近 | 仅靠 `@RepeatSubmit`（同会话短时间）防重，换客户端/换用户/重放即可由同一报价生成多张销售订单（进而重复预留/重复生产计划） | `so_sales_order_header` 增 `source_quotation_id`（若无）并加 `UNIQUE(source_quotation_id, tenant_id)`，转单时捕获重复键返回既有订单 |
| ERP-SO-05 | 价目表无 `del_flag`（部分残留） | `ry-erp.sql` so_price_list / so_price_list_line 段（无 `del_flag` 列）；`so/domain` 下 `SoPriceList*` 无 `@TableLogic`；对照 `SoInvoice.java:212`、`SoContract.java:145` **已有** `del_flag` | 价目表删除为物理删除（历史报价/订单无法追溯当时的价目表行）；发票/合同当前已有逻辑删除，交接报告 §6 的这一条对它们已过时 | 给 `so_price_list`/`so_price_list_line` 补 `del_flag` + 实体 `@TableLogic`（DDL + 存量库脚本） |
| ERP-SO-06 | 合同履约三字段仍是死字段 | `SoContractLine.java:102/107/112`、`SoContractLineVo.java:84/87/90`（全仓仅出现在实体与 VO，无任何 service/mapper 写入点） | 合同履约跟踪未实现：合同行永远不会显示已交付/已开票数量，`lineStatus` 恒为默认值 → 前端展示"空壳字段" | 二选一：① 实现回写（出库/开票时按 `contract_line_id` 累加，与 `so_sales_order_line` 同型）；② 从前端表单/列表移除这三个字段，避免误读 |
| ERP-SO-07 | 独立权限点缺失（残留） | `SoReturnController.java:131/147/164`（confirm/execute 用 `so:return:edit`）、`SoQuotationController.java:132/149`（confirm/convert 用 `so:quotation:edit`）、`SoContractController.java:161`（terminate 用 `so:contract:edit`） | 无法把"改单"与"确认/执行/转单/终止"分权；与 `so:invoice:verify`（已独立）口径不一致 | 新增 `so:return:confirm`/`so:return:execute`/`so:quotation:confirm`/`so:quotation:convert`/`so:contract:terminate` 并在 `sys_menu` 补按钮种子（同时注意 FIN-PERM-01 的教训：码与种子必须一起加） |
| ERP-SO-08 | `getUsedAmount` 全量载入内存（残留） | `CreditCheckServiceImpl.java:149-153`（`selectList` 后循环累加） | 客户占用记录随订单增长，每次下单/确认都要把该客户全部占用记录拉到内存求和 | 改为 mapper `SUM(amount)` 原生查询（注意带 `tenant_id`，参照 `so` 报表原生 SQL 的写法） |
| ERP-SO-09 | Q8 新建委外/调拨单的下游链路只算"半闭合" | `SoSalesOrderHeaderServiceImpl.java:1112`（vendor 为 null 只 warn 继续）、`:1145`（`prdSubcontractMapper.insert` 直插，绕过 `PrdSubcontractServiceImpl.insertByBo` 的供应商校验，见 `prd/service/impl/PrdSubcontractServiceImpl.java:86`）、`:156`（下达时校验供应商启用状态）、`:1222`/`:1235`（调拨数量 = `line.getOrderedQty()`，未按来源仓可用量裁剪） | ① 无供应商的委外单能落库但**下达时会卡住**（下达校验供应商），需人工补供应商，属"可继续但停在 DRAFT"；② 调拨单数量可能大于来源仓可用量（`resolveSourceStock` 只挑"可用量最多的仓"，不裁剪数量），调拨执行时会出现库存不足 | ① 建委外单前若无供应商，改为不建单 + 明确提示（或建单同时写入待补供应商标记并在委外单列表高亮）；② 调拨数量取 `min(orderedQty, available)`，并把不足量记入备注/日志 |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | AR/AP 反核销（unsettle） | `IFinSettlementService` 仅 settle/query；`FinReceiptServiceImpl.java:180` 自认"F1 暂不支持" | `fin_settlement`（含 `del_flag`）、发票 `verified_amount`、`gl_voucher_line.cleared/cleared_by` 字段齐全 | 中（1 个 service 方法 + 1 个端点 + 权限码/菜单 + 3 处状态回退；需补测试） |
| 2 | 已确认收付款单的作废/反过账 | `FinReceiptServiceImpl.java:179-181`（仅 DRAFT 可作废） | 状态枚举 `CANCELLED` 已存在；`ReceiptPoster` 制单可定位 | 中（依赖 #1） |
| 3 | 合同履约跟踪（行级已交付/已开票/行状态） | `SoContractLine.java:102/107/112` 无写入点 | 字段已在表与 VO 中 | 中（出库/开票回写 + 前端列接线） |
| 4 | 价目表逻辑删除 | `so_price_list`/`so_price_list_line` 无 `del_flag` | 其他 so 表均有 | 小（DDL + `@TableLogic` + 存量脚本） |
| 5 | 固定资产折旧的"计划态"落库 | `fa_depreciation.status` 枚举含 `PLANNED`（`ry-erp.sql:2842`），但 `preview()` 不落库、`runDepreciation` 直接插 DONE | 表/枚举/`preview` 已存在 | 小（可选：先落 PLANNED 再置 DONE，可支持人工调整本期折旧额） |

## 5. 已核查确认无问题（证明覆盖范围）

- **GL 借贷校验无绕过**：写 `gl_voucher_line` 的只有 `GlVoucherServiceImpl.insertLines`（`:376`），三个入口（`insertByBo:96`、`updateByBo:127`、`post:176`、`createAndPost:282`）全部先过 `validateBalanced`；业务来源允许负数、手工来源拒绝、`d.signum()!=0 && c.signum()!=0` 拒绝双值、零额拒绝（`:333-373`）。红冲（`SoInvoicePoster` 全负分录）能过，手工红字被拒，与 Q7 记录一致。
- **GL 过账幂等**：`createAndPost` 按 `(voucherSource, sourceId)` 查重返回既有（`:263-271`）；`gl_voucher` 有 `uk_voucher_no(voucher_no,tenant_id)`（`ry-erp.sql:3177`），凭证号用 `IdWorker.getId()` 雪花（`GlVoucherServiceImpl.java:420-423`）→ 无重号可能；MQ 侧 `BaseMessageConsumer` 用 Redis `messageId` 键做 24h 消费幂等（`ruoyi-common-mq/.../BaseMessageConsumer.java:69-89,112-124`），重投不会重复过账。
- **凭证过账并发**：`post()`/`unpost()` 均为"读校验 + `version` 条件更新 + 0 行抛异常"（`:181-193`、`:217-...`），`applyPosting` 在 CAS 成功之后执行，CAS 失败会回滚余额。
- **科目余额表原子性**：`GlAccountBalanceServiceImpl.apply` 用 `setSql` 原地累加（`:97-104`），余额行缺失时插入并捕获 `DuplicateKeyException` 重查（`:88-95`），`gl_account_balance` 有 `uk_balance(period_id,account_id,tenant_id)`（`ry-erp.sql:3072`）→ 并发不会产生重复余额行。
- **报表取数与余额表同构**：`trialBalance`（`GlAccountBalanceServiceImpl.java:141`）与资产负债表（`ReportDataServiceImpl.java:253`）都用"期初+本期发生"现算，**不依赖从未落库的 `closing_debit/closing_credit`**；`IReportDataService` 的 javadoc 与实现口径一致（该 javadoc 曾按 closing 列描述，实现已改，属文档措辞，不作为发现）。
- **期间唯一性**：`gl_period` 有 `uk_period(fiscal_year,period_no,tenant_id)` → `getOrDerivePeriod` 的"查不到就建"（`GlPeriodServiceImpl.java:67-86`）并发时最多抛重复键，不会产生重复期间。
- **FA 折旧幂等与并发**：`fa_depreciation` 有 `uk_asset_period(asset_id,period_id,tenant_id)`（`ry-erp.sql:2853`），`buildPlans` 先按期间过滤已计提资产（`FaDepreciationServiceImpl.java:244-253`），资产累计折旧用"原子累加 + version 条件更新 + 0 行抛异常"（`:166-174`）→ 并发重跑不会重复计提（失败方整体回滚，凭证同事务）。
- **处置凭证平衡（SALE 路径）**：借累计折旧+银行存款+处置损失 与 贷原值+处置收益 恒等（`FaDisposalServiceImpl.java:108-126`，代数验证通过）；仅 SCRAP+有价 例外（见 FIN-FA-02）。
- **核销上限与并发**：`verifyInvoice` 有上限校验 + `setSql` 原子累加 + version CAS（`SoInvoiceServiceImpl.java:431-449`），`settle` 侧对收款单与发票各做余额校验并用 CAS 累加 `settled_amount`（`FinSettlementServiceImpl.java:94-97`、`:126-129`、`:156-164`）。
- **收付款单幂等**：`fin_receipt` 有 `uk_pay_order(pay_order_number,tenant_id)`（`ry-erp.sql:2942`）+ `existsByPayOrderNumber` 查重（`FinReceiptServiceImpl.java:106-109`），回调重复不会建两张单。
- **Q8 分流已落地**：`confirm()` 按 `PROCESS/SUBCONTRACT/TRANSFER/其他` 四路分流（`SoSalesOrderHeaderServiceImpl.java:1005-1055`），自制/委外/调拨三类不占本仓现货（`:1024`），与交接报告 §5.2 Q8 描述一致。
- **Q9 合同到期/终止已落地**：`so/task/SoContractExpiryTask.java:44-45`（`@JobExecutor(name="soContractExpiryTask")`）存在；`SoContractController.java:161` 有 terminate 端点。
- **so 控制器权限码均有菜单种子**：`so:orderheader:*`/`so:return:*`/`so:quotation:*`/`so:contract:*`/`so:invoice:*`/`so:customerOrder:*`/`so:priceList:*` 均为已存在码（`ry-vue.sql:92154` 等），未出现 fin 那类"码无种子"问题。
- **so 报表原生 SQL**：`so` 报表已排除 CANCELLED 且补了 `tenant_id`（交接报告 §3.1 T1.6，本轮抽查未见回退）。

## 5.1 按 09-19 交接报告 §6/§8 逐条复核结果

| 原编号/事项 | 现状证据 | 结论 |
|---|---|---|
| §6 报价转单无幂等兜底（未加唯一约束） | `ry-erp.sql` so_sales_order_header 段仅 `uk_so_no(so_no,tenant_id)`，无报价来源唯一键 | **仍存在**（见 ERP-SO-04） |
| §6 发票/合同/价目表 3 张表缺 `del_flag`（物理删除） | `SoInvoice.java:212` 与 `SoContract.java:145` 均有 `@TableLogic delFlag`，DDL 中 `so_invoice`/`so_contract` 亦有 `del_flag`；`so_price_list`/`so_price_list_line` 无 `del_flag`、实体无 `@TableLogic` | **部分已修复 / 部分仍存在**：发票、合同已具备逻辑删除（原判定对这两张表已过时）；**价目表仍为物理删除** |
| §6 金额/单价/税率精度不统一（(18,2)/(18,4)/(14,4) 混用） | `so_price_list_line.unit_price decimal(14,4)`、`discount_rate decimal(5,2)`；`so_invoice.amount/total_amount decimal(18,2)`；`fin_receipt.amount decimal(18,4)` vs `gl_voucher_line.debit decimal(18,2)` | **仍存在**（so 侧见 ERP-SO-05；fin 侧新增同类问题见 FIN-ARAP-03） |
| §6 `tenant_id` 允许 NULL、`sort_order` 类型不统一 | 抽查的 so/fin 表 `tenant_id` 均为 `varchar(20) DEFAULT '000000'`（`ry-erp.sql:3153/3053/2837/2916` 等），未发现 NULL 默认 | **未复现**（抽查范围内已修正；未全表穷举） |
| §6 合同行 `deliveredQty`/`invoicedQty`/`lineStatus` 全仓无读写 | `SoContractLine.java:102/107/112`、`SoContractLineVo.java:84/87/90`，全仓 grep 无 service/mapper 写入点 | **仍存在**（见 ERP-SO-06） |
| §6 `updateOqcStatus` 按 `materialId` 更新 | `SoSalesOrderLineServiceImpl.java:104-113`（`eq(soId).eq(materialId).ne(lineStatus,"CANCELLED")`） | **仍存在**（见 ERP-SO-02） |
| §6 性能：N+1/全量载入内存求和 | `CreditCheckServiceImpl.java:149-153` 仍 `selectList` + 循环累加 | **仍存在**（见 ERP-SO-08）；退货/发票/报价/合同的循环单条 SQL 未逐条复读（见 §8 未覆盖） |
| §6 信用额度 TOCTOU：`so_credit_usage` 无 `(tenant_id,so_id,status)` 唯一约束 | `ry-erp.sql:5163-5167`：仅 `idx_cu_so_status` 普通索引，无 UNIQUE | **仍存在**（见 ERP-SO-03） |
| §6 权限粒度：`so:return:execute`、`so:quotation:confirm/convert` 复用 `edit` | `SoReturnController.java:131/147/164`、`SoQuotationController.java:132/149`、`SoContractController.java:161` 全部使用 `:edit` | **仍存在**（见 ERP-SO-07） |
| §6 死配置：`orderheader/index.vue` 传入未绑定的 `ref<FormInstance>()` | 未读前端文件（本轮范围不含 plus-ui） | **未复核**（见 §8） |
| §6 注释与语义不符若干处（`so_customer_order.client_code`/`total_amount`、`so_invoice.status` 枚举不全） | 未逐条核对（属文档/注释类，规范 §1.4 不报风格问题） | **未复核** |
| §6 列表页不可达过滤分支（`buildQueryWrapper` 的 `projectId`/`deptId` 前端从不传） | `SoSalesOrderHeaderServiceImpl.java` 全文 grep `projectId`/`deptId` **零命中**；`buildQueryWrapper`（`:1321`）无条件分支 | **已不存在 / 原判定过时**（当前无该分支） |
| §6 Q8 履约链路来源类型分支不完整 | `confirm()` 四路分流（`SoSalesOrderHeaderServiceImpl.java:1005-1055`），委外/调拨建单（`:1087`/`:1172`） | **已修复**（原问题不复现） |
| §6（Q8 遗留观察）`reserved_qty` 展示口径残留非 0 | `SoSalesOrderHeaderServiceImpl.java:532/617/642` 只调 `unreserve`；`WmStockReservationServiceImpl.java:115` 释放的是库存侧 `reserved_quantity`；行字段无回写 | **仍存在**，且比原记录更明确：库存侧已释放、行字段单向失真（见 ERP-SO-01） |
| §8 高风险复审：`GlVoucherServiceImpl.validateBalanced`（负数分录口径） | `GlVoucherServiceImpl.java:333-373`：按来源放行负数、`signum()!=0` 双值校验、不平衡仍拒绝；四个入口均调用 | **复核通过**（无绕过路径，见 §5） |
| §8 高风险复审：`updateByBo`/`diffUpdateLines`（改单保留行 ID） | 未逐行复读（本轮定位为"残留/回归"，未重审已改逻辑） | **未复核**（见 §8） |
| §8 高风险复审：`confirm()` 履约分流（来源仓取值、无供应商） | `:1112` 无供应商只 warn 继续；`:1203`/`:1250` 来源仓 = 可用库存最多且非目的仓 | **与文档一致**；新增两点观察（无供应商委外单会卡在下达、调拨数量未裁剪）见 ERP-SO-09 |
| §8 高风险复审：`SoInvoiceServiceImpl.adjustInvoicedQty` 系列 | `verifyInvoice`（`:420-449`）上限 + 原子累加 + CAS 通过；红冲入口（`:501`）存在 | **复核通过**（本轮仅核对核销上限与并发口径） |
| §8 中风险：`SoContractExpiryTask` 到期判据（`<` vs `<=`） | `so/task/SoContractExpiryTask.java:44-45` 存在；判据行未复读 | **部分复核**（任务存在，判据未逐行确认，见 §8） |
| §8 中风险：`SoReturnServiceImpl`/`SoQuotationServiceImpl` 的 `normalizeLines` + 状态门禁 | 未复读 | **未复核** |
| §8 中风险：价目表取价链路（`resolvePrice` → 前端选料） | 仅确认价目表无 `del_flag`（ERP-SO-05）；折扣率语义与 `resolvePrice` 未复读 | **部分复核** |
| §8 低风险：4 个 VO 的 `@ExcelIgnoreUnannotated` + 明细行 VO 化 | 未复核导出 | **未复核** |
| §8 低风险：三个 DDL 存量库脚本可重复执行性 | 未复核（不在代码范围） | **未复核** |

## 6. 跨模块链路：业务单据事件 → GL 过账 闭环矩阵

| 业务事件 | 发布点 | 传递通道 | 消费者/Poster | 闭环 |
|---|---|---|---|---|
| 销售发票 ISSUED | `so` `SoInvoiceIssuedEvent`（`GlPostingEventDispatcher.java:38` 监听） | `TOPIC_EXCHANGE` + `GL_POSTING_SO_INVOICE` | `GlPostingConsumer` → `SoInvoicePoster`（`sourceType()=SO_INVOICE`） | ✅ 闭合 |
| 采购发票 ISSUED | `pu` `PuInvoiceIssuedEvent`（`:49` 监听） | `GL_POSTING_PU_INVOICE` | `GlPostingConsumer` → `PuInvoicePoster` | ✅ 闭合 |
| 收付款单确认 | `FinReceiptServiceImpl.java:167` 直接发消息；`PayReceivedConsumer.java:122`（回调建单后） | `GL_POSTING_RECEIPT` | `GlPostingConsumer` → `ReceiptPoster` | ✅ 闭合（**但 status 可绕过 confirm 直建，见 FIN-ARAP-01 → 该路径可被跳过**） |
| 固定资产折旧 | 无 MQ（`FaDepreciationServiceImpl.java:149` 直接 `voucherService.createAndPost`） | 同事务直连 | 无需 Poster | ✅ 闭合（直连设计，非死代码） |
| 固定资产处置 | 无 MQ（`FaDisposalServiceImpl.java:137` 直连 `createAndPost`） | 同事务直连 | 无需 Poster | ✅ 闭合（来源标注错误见 FIN-FA-03） |
| 存货成本结转 | 无 MQ（`CostService.java:117` / `CostPoster.java:91` 直连 `createAndPost`，`CostController.java:34` 调用） | 同事务直连 | 无需 Poster | ✅ 闭合（`CostPoster` 未实现 `AccountingPoster`，但 `AccountingPoster.java:6` 的 javadoc 把它列为 Poster 实现 —— **文档与实现不符**，无功能影响） |
| 期间损益结转 / 外币重估 / 年结 | `FinClosingServiceImpl` 直接 `createAdjustVoucher`（`:185`、`:275`） | 同事务直连 | 无需 Poster | ✅ 闭合 |
| 死代码检查 | `AccountingPosterRegistry` 只注册到 3 个 Poster（`SoInvoicePoster`/`PuInvoicePoster`/`ReceiptPoster`）；`GlConstants.SOURCE_DEPRECIATION`/`SOURCE_COST`/`SOURCE_ADJUSTMENT` **没有对应 Poster** | — | 这三个来源不经 MQ，属设计选择 | **无"有 Poster 无事件"或"有事件无 Poster"** 的情况 |
| MQ 消费幂等 | `BaseMessageConsumer`（Redis `messageId` 24h）+ `createAndPost` 的 `(source,sourceId)` 查重 | — | — | ✅ 幂等成立；`gl_voucher` 的 `idx_source(voucher_source,source_id)` 是**非唯一**索引（`ry-erp.sql:3180`），仅靠应用层查重兜底（Redis 键 24h 过期后理论窗口） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**
  - `FIN-ARAP-01`：`FinReceiptServiceImpl.insertByBo/updateByBo` 强制服务端权威字段（status/source/settledAmount），并补 `FinReceiptServiceImplTest`（伪造 status 应被忽略/拒绝）。
- **批次 B（P1，资金与账务正确性）**
  - `FIN-FA-01`：修净值 SQL（去掉重复扣减）+ 存量 `UPDATE fa_asset SET net_book_value = original_value - cumulative_depreciation`。
  - `FIN-GL-01`：外币重估原币净额按借贷方向取号；对已重估期间给出复核清单。
  - `FIN-GL-02`：`unpost` 拒绝 CLOSED 期间（与 `post` 口径对称）。
  - `FIN-ARAP-02`：补 `unsettle` 原语 + 端点 + 权限码/菜单种子 + 测试。
  - `FIN-PERM-01`：补齐 7 个权限码的 `sys_menu` 种子（主脚本 + 存量库 `INSERT IGNORE` 脚本），并加"权限码 ⊆ 菜单种子"静态校验测试。
- **批次 C（P1/P2，校验与精度）**
  - `FIN-GL-03`（手工凭证来源收口）、`FIN-GL-04`（重估幂等键）、`FIN-FA-02`（报废残值）、`FIN-FA-03`（处置来源/幂等键）、`FIN-FA-04`（负折旧）、`FIN-ARAP-03`（精度统一，含 DDL）。
- **批次 D（so 残留，与 so 计划合并）**
  - `ERP-SO-01`（reserved_qty 回写）、`ERP-SO-03`（信用唯一键，DDL）、`ERP-SO-02`（OQC 按行）、`ERP-SO-04`（报价转单唯一键）、`ERP-SO-05`（价目表 del_flag）、`ERP-SO-09`（委外/调拨收口）。
- **批次 E（P2 技术债）**
  - `ERP-SO-06`、`ERP-SO-07`、`ERP-SO-08`。

## 8. 未覆盖范围

- **未读文件（本轮预算内主动放弃，非"无问题"）**：
  - `fin/cost`（`CostService`/`CostPoster` 仅按 grep 确认调用关系与 `SOURCE_COST` 用法，**未读成本结转/暂估的金额算法**）→ 库存成本（移动平均/FIFO 结转、成本与库存流水一致性、负库存成本）**未审计**；
  - `fin/report` 的利润表/现金流量表取数（`ReportDataServiceImpl:96-151`、`matchCashAmount`）**未读**；
  - `fin/arap` 的 `ArapReportController`/`OpenItemVo` 未读（应收应付账龄报表口径未审计）；
  - `fin/pay/PayMockController` + `PayReceivedConsumer` 仅按 grep 看调用点，**未读回调幂等与金额校验**；
  - `FinClosingServiceImpl` 的 `trialCheck`/`carryPnl`/`closePeriodInternal`/年结（`:1-180`、`:300-370`、`:520-741`）**未读**（仅读了 `revaluate` 与 `carryForward*`）；
  - `so` 的 `SoReturnServiceImpl`/`SoQuotationServiceImpl`/`SoContractServiceImpl`/`SoFulfillmentWritebackServiceImpl` 未读（残留项按 grep 核验，未重审逻辑）；
  - `so/task/SoContractExpiryTask` 的到期判据行未读；
  - 前端 `plus-ui`（全部页面、API 层）**不在本轮范围**。
- **未做的验证**：无编译/无单测执行（只读审计）；`script/sql/update/` 存量脚本未逐条核对可重复执行性；`ry-erp.sql` 未全表穷举 `tenant_id`/精度/唯一键（仅抽查 8 张表）。
- **未复核的交接报告条目**已在 §5.1 标注为「未复核」。
