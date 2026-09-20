# 销售模块（so）优化 · 交接报告

> 创建: 2026-09-19 | 配套计划: [2026-09-19-so-module-optimization-plan.md](./2026-09-19-so-module-optimization-plan.md)
> 范围: 后端 `ruoyi-modules/ruoyi-erp/.../so`（审计时 85 文件 / 9541 行；改造后 87 文件 / 10716 行）+ 14 张 `so_` 表 + 前端 `plus-ui` 销售页面
> 状态: **计划内 6 批次全部完成**；Q1~Q5 决策已落地实施完毕；**Q7/Q8/Q9 已于后续轮次按用户决策全部实施完毕**（见 §5.2）

---

## 1. 结论摘要

本次工作分两个阶段：

| 阶段 | 内容 | 结果 |
|---|---|---|
| **计划执行**（批次 0~5） | 测试基线 → P0 数据正确性 → 契约与校验 → 功能补齐 → DDL 收敛 → 清理 | 批次 0/2/4 完成；1 除 T1.7 完成；3 除 T3.3/T3.4 完成；5 除死字段清理完成 |
| **加固**（3 轮） | 在已改动范围内做同类模式排查 | 又发现并修复 3 类系统性缺陷（见 §3.2） |

**测试基线**：so 模块从 **25 个用例（3 个测试类）** 增长到 **180 个用例（10 个测试类）**，全绿。

**最高价值的三个修复**（按业务影响排序）：
1. **改单不再重建行 ID**（T1.1）—— 原实现每次改单都把 `so_sales_order_line.line_id` 全部换新，
   而生产计划、出库回写、发票行都按 `line_id` 关联。最狠的一条：出库回写打到已删除的行上，
   **新行 `delivered_qty` 永远为 0，订单永远无法履行**。
2. **金额服务端重算**（T1.8）—— `confirm()` 用客户端传入的 `totalAmount` 做信用额度校验，
   **传 0 即可绕过信用控制**。
3. **开票量占用收口**（T1.4）—— 防超开门禁原本只存在于自动开票路径，手工建票/改票完全绕过。

---

## 2. 交付清单（35 次提交，按时间正序）

| commit | 内容 | 验证 |
|---|---|---|
| `b93c37a0` | 销售订单表单字段显示与自动赋值优化（含后端 `coNo` 回填） | 前端 eslint + build |
| `cd882df9` | 审计计划文档（5 路并行只读审计 + 主链路人工复核） | — |
| `4dddf2e9` | 批次 0 回归基线（+43 用例） | 68/68 |
| `336dd856` | **T1.1 改单差量更新保留行 ID** + 履约字段白名单 + 已履约行禁删 | 79/79 |
| `567fe825` | T1.2 订单删除状态门禁 + 释放信用/预留/关闭生产计划 | 33（本类） |
| `82bd1676` | T1.3 退货门禁（入库要求 CONFIRMED、改/删限 DRAFT） | 87/87 |
| `8f5ab354` | **T1.4/T1.5 开票量占用收口 + 红冲防重复 + 核销上限** | 100/100 |
| `af8c40b7` | **T1.8 订单金额服务端重算** | 100/100 |
| `54146916` | T1.6 报表列名/租户/权限修复（接口从必然 500 恢复） | 106/106 |
| `13a9144a` | 批次 2 契约与校验（BO 校验生效、状态服务端强制、报价白名单与状态机） | 119/119 |
| `33276886` | **T3.1a 定价引擎：折扣率语义反转（D2）+ NULL 边界 + selectList** | 123/123 |
| `b06dd577` | T3.1b `resolvePrice` 接口 + 订单选料自动带价（D1） | 编译 + 前端 |
| `a96e441a` | T3.1c 价目表前端/菜单/权限码/状态字典 | 128/128 + 前端 |
| `2b76d946` | T3.2 报价单工具栏状态门禁（含审计勘误） | 前端 |
| `15c1def9` | 批次 4 DDL 收敛（唯一键补租户维度、14 索引、注释重建） | 132 校验 + 前端 |
| `c1640708` | 批次 5 客户订单幻影字段 + 列表选中项残留 | 前端 |
| `f5543fe5` | 批次 5 明细行改 VO 直出 + 退货/发票工具栏门禁 | 130/130 + 前端 |
| `74bbeb43` | 补齐 4 个 VO 的 `@ExcelIgnoreUnannotated` | 130/130 |
| `cadffdb1` | 发票/合同明细行改 VO（新建 2 个 LineVo） | 132/132 |
| `e5ee5fc0` | 加固：明细「未提交」语义（销售订单） | 135/135 |
| `cbe936b4` | 加固：同类问题在报价/退货/价目表三处一并修复 | 138/138 |
| `71435305` | 加固：服务端权威字段（发票/退货状态、时间戳、红冲标记） | 143/143 |
| `0b139d2f` | 本交接报告 | — |
| `28538940` | 高风险改动补真实场景用例 + 强化断言强度 | 145/145 |
| `baa53156` | 加固：发票表头金额服务端汇总（修 `totalAmount` 伪造绕过核销上限） | 148/148 |
| `d9be67ef` | 加固：流程回调改为条件更新（防复活已取消/已确认订单） | 153/153 |
| `eeb9d10d` | 加固：客户订单引用守卫 + 新建 SoCustomerOrderServiceImplTest | **160/160** |
| `fd973b78` | 交接报告同步（流程回调守卫 / 测试基线） | — |
| `79de49f2` | 记录全模块回归基线（241 用例）与跨模块影响面核查结论 | 全模块 241/241 |
| `9a98f3e4` | 加固：同类流程回调守卫补全到发票/退货/合同（修发票重复过账风险） | 153/153 |
| `82ed5f9f` | **Q4** 死字段清理（复核后实际只删 5 个，原清单 13 个里 8 个是在用 UI 字段） | 252/252 |
| `02c60fe2` | **Q3** 税率口径统一为百分数（注释 + 发票区间校验 + 退货表单 + 更正「含税」误判） | 256/256 |
| `9fd20555` | **Q1/T3.4** 报表保留并修完（菜单 2237 + 报表页 4 个前端缺陷） | 前端 eslint+build |
| `91ab90ab` | **Q2/T3.3** 退货全闭环（`returned_qty` 回写 + 超退守卫 + 前端 confirm/execute 接线） | 259/259 |
| `dfb4d3e4` | **收尾核验**：补完 T3.5 死事件/死 VO + §8.6 验收规格落为可执行测试 | **261/261** |
| `323c0224` | 计划进度表状态标记同步（批次 1~5 全部 ✅） | — |
| （本轮） | **Q7a** GL 引擎按来源放开负数分录（修好红冲过账）+ 借贷校验改 signum 口径 | 新增 `GlVoucherServiceImplTest` 8/8 |
| （本轮） | **Q7b** 发票作废仅限 DRAFT（ISSUED/VERIFIED 引导走红冲）+ 前端按钮同步 | 改写 2 用例 + 新增 1 |
| （本轮） | **Q8** `confirm()` 按 `source_type` 三路分流：委外→委外单、调拨→调拨单 | 新增 8 用例 |
| （本轮） | **Q9** 合同 `terminate` 端点 + `SoContractExpiryTask`（SnailJob）+ 前端终止按钮 | 新增 8 用例 |
| （本轮） | 全量回归 | **285/285**；前端 eslint + build:prod 通过 |

> **测试基线的可复用性**：`SoSalesOrderHeaderServiceImplTest`（49 个）与 `SoInvoiceServiceImplTest`（40 个）
> 覆盖了两个最高风险改动（改单差量更新、开票量占用/释放）。其中关键断言是**增量数值级**的，
> 例如断言占用的是「可开票量 6」而非「已发货 10」、红冲释放的是「-6」而非仅校验红票行数量。
> 修改这两处逻辑后，这两个测试类是最先应该跑、也最可能拦住回归的。

---

## 3. 关键改动说明

### 3.1 计划内改动（要点）

- **T1.1 差量更新**：按 `lineId` 区分「更新/新增/删除」；白名单更新，履约/关联字段保持库中原值
  （顺带修掉"表单快照覆盖并发已发货量"的丢失更新）；只在本单已有行里匹配 `lineId`，防越权改写他单。
- **T1.4 开票量**：抽出统一原语 `adjustInvoicedQty(soLineId, delta, strict)` —— 占用严格（超开即拒）、
  释放宽容（历史票未占用过时跳过，不阻断作废/删除/红冲）；四个写入口全部收口。
- **T1.6 报表**：列名 `so.status`/`so.order_id` → `order_status`/`so_id`（原为必然 500）；
  补 `tenant_id` 条件（原生 SQL 绕过租户插件）；排除 CANCELLED；权限码改用已存在的 `so:orderheader:list`。
- **D2 折扣率**：公式由 `单价 × 折扣率/100` 改为 `单价 × (1 − 折扣率/100)`。原实现方向相反，
  且 DDL 默认值 100 会让**默认配置下成交价恒为 0**。
- **T3.1c 价目表上线**：此前无菜单、无权限码、状态字典不存在、`listCode` 被 `@NotBlank` 挡住自动生成、
  明细 `materialId` 恒空、状态默认 DRAFT（引擎只取 ACTIVE）—— 五重故障叠加导致功能整体不可用。

### 3.2 加固阶段新发现的系统性缺陷

> 这三类都不是计划里列出的问题，是在已改动范围内做同类模式排查时发现的。

**(1) `updateByBo` 传 null 明细会删光所有行**（真实数据丢失风险）
`diffUpdateLines` 把「请求中未出现的已有行」一律逻辑删除，而 `lineBoList == null` 时该分支整体被跳过
→ 所有已有行都成了「未出现」→ 全删；同时金额被算成 0。
即 **API 客户端做部分更新（例如只改备注）会静默清空整单明细与金额**。
同类问题存在于 **销售订单 / 报价 / 退货 / 价目表** 四个服务，已统一为：
`null = 本次不提交明细（保持库中原值）`，`空数组 = 显式清空`（口径与合同模块一致）。

**(2) 客户端可伪造服务端权威字段**

| 位置 | 字段 | 后果 |
|---|---|---|
| 发票 `insertByBo` | `status` | 直建 ISSUED 发票，而 `insertByBo` 不发布 `SoInvoiceIssuedEvent` → **发票标记「已开」却从未过账总账** |
| 发票 `insertByBo` | `verifiedAmount` | `verifyInvoice` 以「已核销额 + 本次」判断是否置为已核销，**预置大值即可用很小金额把发票置为已核销** |
| 发票 `insertByBo` | 明细负数量 | 走占用原语的**释放**分支 → **绕过红冲流程释放已开票量**，重新打开超开缺口 |
| 退货 `insertByBo` | `status` | 直建 RECEIVED 退货单 → 界面显示「已收货」但**库存从未加回**，且 T1.3 后永远无法真正执行 |
| 订单 `insert/updateByBo` | 4 个生命周期时间戳 + 3 个信用字段 | 草稿单上出现假的「确认时间/履行时间」或「已检查」 |
| 发票 `insert/updateByBo` | `amount`/`taxAmount`/`totalAmount` | **`verifyInvoice` 的核销上限就是 `totalAmount`** —— 改大即可用一个远小于真实金额的收款把发票置为「已核销」。已改为按明细汇总表头金额（口径与 `generateDraftFromOrder` 一致）；**未动行金额**，因发票页与订单页的税额口径存在分叉（属 Q3 待决事项） |

**(3) 流程回调无条件改写订单状态，会「复活」已取消/已确认的订单**
`afterFlowHandle` 原用无条件 `updateById`，存在两条可复现的覆盖路径：
- 订单在 `APPROVING` 时被 `cancel()` 取消（`cancel` 允许取消 APPROVING），随后审批人撤销流程
  → 回调把 `CANCELLED` 改回 `DRAFT`，**已取消的订单被复活**；
- 订单 `APPROVED` 后被 `confirm()` 确认（已冻结额度、预留库存、生成生产计划），随后审批人撤销流程
  → 回调把 `CONFIRMED` 改回 `DRAFT`，用户可再次确认 → **重复冻结额度、重复预留库存、重复生成生产计划**。

已改为条件更新（仅 DRAFT/APPROVING/APPROVED/REJECTED 可被回调改写），影响 0 行时记 warn 静默返回。

**(3b) 同一缺陷存在于全部 4 个服务的 `afterFlowHandle`，且发票侧的后果最严重**
`SoContractServiceImpl` / `SoInvoiceServiceImpl` / `SoReturnServiceImpl` 的 `afterFlowHandle`
同样是无条件 `updateById`（我只先修了销售订单，本轮补齐）。其中**发票侧会导致重复过账**：

> 发票已 ISSUED（已过账总账）→ 流程被撤销/撤回 → 回调把状态改回 DRAFT（界面显示未开具而凭证仍在）
> → 用户再次提交审批 → 再次 ISSUED → **再次发布 `SoInvoiceIssuedEvent`，总账多出一张重复凭证**。

因为原实现是「只要 `actStatus=pass` 就无条件发事件」，与状态是否真的流转到 ISSUED 无关。
修复要点：① 改为条件更新（在途状态集合：发票/合同为 DRAFT/APPROVING/REJECTED，
退货为 DRAFT/APPROVING —— 退货无 REJECTED 状态，驳回即置 DRAFT）；
② **事件发布与状态变更绑定**，仅当本次真的流转到 ISSUED 才过账。
其余后果：退货单会把已确认（CONFIRMED）甚至已入库（RECEIVED）的单据改回 DRAFT（库存已加回却显示草稿），
合同会把已生效（EFFECTIVE）改回 DRAFT（重新可编辑/可删除）。

**(4) 客户订单被引用时仍可删除，留下悬空引用**
`so_sales_order_header.co_id` 指向客户订单（库内**无外键约束**），而 `SoCustomerOrderServiceImpl.deleteByIds`
原先无条件删除 —— 删掉被引用的客户订单后，这些销售订单的「客户订单」关联悬空
（详情页回填不到 `coNo`，只能退化显示 `客户订单#<id>`）。
已加引用守卫：被任意销售订单引用即拒绝删除，并提示单号与引用数量。
（同类的合同侧本就有 DRAFT CAS 门禁，无需改动。）

**(5) `@JSONField(serialize=false)` 是无效注解**
出现在 `SoQuotationVo`/`SoReturnVo` 上，是 **fastjson2** 注解而本项目 HTTP 走 Jackson —— 完全不生效；
且其意图本身也是错的（报价/退货表单正需要读明细行）。已删除，改用 VO 直出从类型层面阻止
`tenantId`/`delFlag`/`createBy` 泄漏。

### 3.3 一条审计勘误

计划里曾记录「`convertQuotationToOrder` 前端零调用、报价转单功能不可达」——**这是错的**。
真实原因是检索模式用了 `convertToOrder`，匹配不到前端函数名 `convertQuotationToOrder`；
该函数在 `quotation/index.vue:220` 早已被调用。已在计划文档标注勘误。

---

## 4. 存量库必须执行的脚本

> 全新环境随主脚本 `script/sql/ry-erp.sql` / `ry-vue.sql` 导入即可，**无需**执行下列脚本。
> 三个脚本的「前置检查」都以 `--` **注释形式**写在脚本里（不会自动执行），执行前请复制出来单独跑。
> 其中 **`so-ddl-hardening` 是一次性脚本**（MySQL 的 `ADD INDEX` 不支持 IF NOT EXISTS，
> 重复执行会在 DROP/ADD INDEX 处报错）；另外两个经复核**可重复执行**
> （菜单脚本 21 条全是 `INSERT IGNORE`、无非幂等语句；折扣率脚本的 `MODIFY` 与
> `WHERE discount_rate = 100` 重复执行结果一致）。

| 脚本 | 作用 | 前置检查 |
|---|---|---|
| `script/sql/update/2026-09-19-so-price-list-discount-rate.sql` | D2 折扣率默认值 `100→0` + 存量数据 `100→0` + 税率/折扣率列注释 | 执行 UPDATE 前确认无「真实让利 100%」的行：`SELECT * FROM so_price_list_line WHERE discount_rate = 100;` |
| `script/sql/update/2026-09-19-so-price-list-menu.sql` | 价目表菜单 2225 + 按钮 2226~2229/2236 + 字典 `so_price_list_status` + 角色授权 | 幂等（INSERT IGNORE），可直接执行 |
| `script/sql/update/2026-09-19-so-returned-qty.sql` | Q2 退货闭环：新增 `so_sales_order_line.returned_qty` + **存量回填**（按 `status='RECEIVED'` 的退货单聚合补齐历史已退数量） | **不可重复执行**（列已存在会报错）；执行前先跑脚本内前置检查确认列不存在。回填使超退守卫对历史数据同样生效 |
| `script/sql/update/2026-09-19-so-sales-report-menu.sql` | Q1 销售报表菜单 2237 + 角色授权（权限码沿用 `so:orderheader:list`） | 幂等（INSERT IGNORE），可直接执行 |
| `script/sql/update/2026-09-19-so-tax-rate-unit-comments.sql` | Q3 税率/折扣率「百分数」单位注释统一（3 列）+ **只读**存量数据核对查询 | 幂等；核对查询检出 `0 < tax_rate < 1`（疑似小数写法）与 `> 100`（非法值）—— 因 `0.5` 在百分数语义下也可能是合法的 0.5%，脚本**不做自动迁移**，只给清单 |
| `script/sql/update/2026-09-19-so-drop-dead-columns.sql` | Q4 死字段清理：删 `so_sales_order_header.delivery_address_id`/`invoice_address_id`、`so_sales_order_line.linked_po_id`/`reserved_serials`/`pa_group_id` 共 5 列 | **一次性脚本**（DROP COLUMN 无幂等写法）；脚本内附前置检查，**须先确认这 5 列全为 NULL** 再删 |
| `script/sql/update/2026-09-19-so-ddl-hardening.sql` | 唯一键补 `tenant_id`（2 个）+ 补单号唯一约束（2 个）+ 阶梯唯一约束（1 个）、补 14 个查询索引、`min_qty` 去 NULL、重建 `so_invoice` 乱码注释 | 脚本内每节都以注释给出前置检查 SQL；加唯一约束前须确认无重复数据 |
| `script/sql/update/2026-09-19-so-contract-expiry-task.sql` | **Q9** 登记 SnailJob 到期扫描任务（`sj_job` id=200 + `sj_job_executor` id=200）；**无 DDL 变更** | 幂等（`sj_job` 唯一键 `uk_sj_job_01(namespace_id,biz_id)` + `INSERT IGNORE`），可直接执行。执行后需在 SnailJob 控制台确认任务已出现或重启应用 |

> **本轮（Q7/Q8/Q9）除 `so-contract-expiry-task.sql` 外无新增 DDL**：
> Q7 是代码口径修正（无列变更）、Q8 复用既有 `prd_subcontract`/`wm_transfer` 表、
> Q9 复用 `so_contract.expiry_date` 与 `remark`（终止原因写入备注）。
> 上面 7 个 09-19 脚本 + 本轮的 1 个 = **8 个**，已全部在本地库执行并复核通过
> （本地库实测：MySQL **5.7.14**，注意不是 8.0）。

**执行后注意**：应用若在运行，菜单与字典走 Redis 缓存，需在「菜单管理/字典管理」保存任意一项触发刷新，
或重启应用。**必须先执行 DDL 脚本再部署对应后端版本**（实体与列需一致）。

---

## 5. 待决项 → **已全部决策（2026-09-19）**

> 用户已就下列问题给出方向，剩余工作已解锁，按 **Q4 → Q3 → Q1 → Q2** 顺序推进。

### 5.1 决策结果

| # | 决策 | 对工作的影响 |
|---|---|---|
| **Q5** | **可用**（提交审批功能正常） | ✅ **结案，无需修复**。原判「事务内跨库会失败」是**误判**：被调用方 `WfProcessServiceImpl.startProcessByDataId` 标了 `@Transactional(REQUIRES_NEW)`（`:1947`），会挂起外层事务并重新获取连接，`@DS("master")` 因此**真正生效**（`wf_*` 表 13 张在 `ry-vue`，`ry-erp` 为 0 张）。且 4 个 `submit()` 在工作流调用后**无 erp 写操作**（状态流转由 `afterFlowHandle` 完成），跨库非原子性无暴露面。已更正计划 §三 P0-5 |
| **Q4** | **死字段删** | ✅ **已完成**。复核后**实际只删 5 个**：原清单 13 个里 **8 个是前端真实在用的 UI 字段**（`projectId`/`isPaEnabled`/`distributionChannel`/`customerRefNo` 表头 `v-model`；`discountPercent` 参与行金额计算；`deliveryPriority`/`customerLineRef`/`reservedBatchNo` 行级绑定或展示）。原判定「Java getter/setter 零命中」有缺陷 —— Jackson 反射序列化，源码里 grep 不到 getter 不等于字段已死。详见计划附录 A |
| **Q3** | **百分数**（`13` = 13%） | ✅ **已完成**。核查发现**代码侧本就已统一**（前后端全部 `× 税率 ÷ 100`），本次补齐三处缺口：① DDL 单位注释；② **发票税率区间校验（原先缺失）** —— 新增 `assertLineTaxRate` 校验 0~100，此前发票可落库 `0.13`/`1300` 而无人察觉；③ 退货表单税率由纯文本 `el-input` 改为数值输入。另更正一条审计误判：所谓「发票/合同按含税金额计税」**系误读注释**（合同表单注释误写「行金额(含税)」，代码实为不含税），公式本就一致 |
| **Q1** | **修**（报表保留） | ✅ **已完成**。菜单 2237「销售报表」+ 角色授权（**权限码沿用 `so:orderheader:list`**，与控制器一致，避免「有菜单但 403」）；报表页 4 个前端缺陷已修：① 每次查询重复 `echarts.init`（同一 DOM 反复建实例、旧实例泄漏）→ 改用 `getInstanceByDom` 复用；② `resize` 匿名监听只加不减（查询 N 次即有 N 个监听 → 内存泄漏 + 一次 resize 触发 N 次重绘）→ 改为单例防抖监听并在卸载时解绑；③ 卸载不 `dispose` → 已补；④ 后端返回的 `completionRate`/`completedCount` **完全没被消费** → 已作为独立百分数轴（0~100）绘制，并在 tooltip 展示完成数与完工率 |
| **Q2** | **尽量全闭环** | ✅ **已完成**。① `so_sales_order_line` 新增 `returned_qty`（实体+VO，**BO 不加** —— 与 `delivered_qty`/`invoiced_qty` 同属服务端权威字段）；② `execute()` 入库后按**条件更新**累加，并强制 `已退 + 本次 <= 已发货`（超退抛异常并回滚整个 execute，含状态抢占与库存入库，不留半成品；并发由数据库条件更新保证原子性）；③ 前端补 `confirm`/`execute` 两个 API 函数 + 「确认」「入库」按钮 —— **后端端点早已存在但前端 API 层没有，退货入库在 UI 上完全不可达**；④ 订单表单行加只读「已退货数量」列让闭环可见 |

### 5.2 原待决项说明（存档）

| # | 事项 | 影响 | 原建议 |
|---|---|---|---|
| **Q5** | ~~跨库事务~~ | ✅ 实测可用，**已结案** | — |
| **Q1** | 销售报表是否保留 | SQL/租户/权限已修好（不再 500）；菜单与前端 `completionRate` 消费未做 | ✅ 保留并修完 |
| **Q2** | 退货闭环范围 | 退货与订单**零数量关联**（无 `returned_qty`、无超退校验）；且后端有 `/return/confirm`、`/return/execute` 但**前端 API 层没有这两个函数** → 退货入库在 UI 上不可达 | ✅ 做全闭环 |
| **Q4** | 13 个死字段（`project_id`/`is_pa_enabled`/`pa_group_id` 等，全仓 getter/setter 零命中） | 仅占位，无功能影响 | ✅ 删除 |
| **Q7**（加固轮新发现） | **作废已开票（ISSUED）的发票不冲销总账**：`cancelInvoice` 不发布任何 GL 事件，而总账过账只由 `SoInvoiceIssuedEvent` 触发 → 已开票发票被作废后，**其总账凭证仍留在账上** | 账实不符（财务报表含已作废发票的收入/应收） | ✅ **已实施（决策 A）**：作废仅限 DRAFT，ISSUED/VERIFIED 必须走红冲；前端「作废」按钮同步限 DRAFT |
| **Q7-附**（实施中新发现） | **红冲过账本身也是坏的**：`GlVoucherServiceImpl.validateBalanced` 对**所有**路径一律拒绝负数分录，而 `SoInvoicePoster` 处理红字发票时借贷金额全为负 → 红冲的过账消息必然抛异常、重试 3 次后进死信，**账务冲销从未生效**（注释声称「金额取负」但代码不允许）。若只做「作废限 DRAFT」，用户会被推向一个同样坏掉的路径 | 红冲完全无效，比原记录更严重 | ✅ **已实施（决策 A）**：`createAndPost`/`post` 按**凭证来源**判定（非 MANUAL 即业务自动制单，允许负数），手工来源仍拒绝；借贷「不可同时有值」校验由 `signum() > 0` 改为 `!= 0`（否则借 -100/贷 -100 会被漏过） |
| **Q8**（加固轮新发现） | **履约链路的物料来源类型分支不完整**：`confirm()` 只处理 `PROCESS`（生产计划）与「非 PROCESS」（库存预留）两类，而 `prd_material_source_type` 字典有 6 种来源类型 | `SUBCONTRACT`（委外）/`TRANSFER`（调拨）的销售订单行**既不生成生产计划、也预留不到库存**，只有一条 warn。详见 §6 | ✅ **已实施（决策 A）**：`confirm()` 按来源三路分流 —— PROCESS→生产计划、SUBCONTRACT→自动建委外单（供应商取 `bd_material_vendor` 默认→priority 最小→留空）、TRANSFER→自动建调拨单（目的仓=行默认仓，来源仓=该物料可用库存最多的其他仓；无来源仓则不建单）；三者均**不再占用本仓现货** |
| **Q9**（加固轮新发现） | 合同状态常量 `EXPIRED`/`TERMINATED` 全仓无任何代码设置 | 合同永不过期、无法终止 | ✅ **已实施（决策 A）**：新增 `SoContractExpiryTask`（SnailJob `@JobExecutor`，每天 01:30，`expiry_date < 今天` 的 EFFECTIVE→EXPIRED，当天仍算有效）+ `POST /erp/so/contract/terminate`（限 EFFECTIVE，CAS 条件更新）+ 前端「终止」按钮 |

---

## 6. 已知但本次未处理的问题（不在已完成范围）

> **收尾核验结论（2026-09-19）**：逐条对照计划验收清单后确认 —— **批次 0~5 全部完成**
> （批次 3 的 **T3.5 死事件/死 VO** 在核验轮补完；**T1.7 经实测为误判**，无需修复）。
> §五 DDL 必做 9 项全部落实（第 7 项「注释补百分数」so 模块 100% 覆盖，
> 剩余 5 列全在 `pu_*` 采购模块，超出本计划范围）。
> 下面列出的项**从未被任何批次列入承诺范围**，属审计记录中「已发现但未纳入本计划」的部分，
> 在此明确列出以免被误认为遗漏。

**审计记录但未纳入本计划的项**（评估后判定为独立需求、性能优化或风险较高）：

- 报价转单无幂等兜底（已加 `@RepeatSubmit`，但未加唯一约束）
- 发票/合同/价目表 3 张表缺 `del_flag`（走物理删除，违反项目 `@TableLogic` 规范）
- 金额/单价/税率精度不统一（`(18,2)`/`(18,4)`/`(14,4)` 混用）、`tenant_id` 允许 NULL、
  `sort_order` 类型不统一 —— 均属 §五「可选（风险较高，单独评估）」项
- 合同行 `deliveredQty`/`invoicedQty`/`lineStatus` 三个履约字段全仓无读写（合同履约跟踪未实现）
- `SoSalesOrderLineServiceImpl.updateOqcStatus` 按 `materialId` 更新（同订单同物料多行会被一起改写）
- 性能：循环单条 SQL / N+1（退货、发票、报价、合同多处）、`CreditCheckServiceImpl.getUsedAmount`
  全量载入内存求和（应 `SUM()`）
- 信用额度 TOCTOU：`checkAndFreeze` 先查后插，`so_credit_usage` 无 `(tenant_id,so_id,status)` 唯一约束
- 权限粒度：`so:return:execute`、`so:quotation:confirm/convert` 复用 `edit`，无独立权限点
- 死配置：`orderheader/index.vue` 传入未绑定的 `ref<FormInstance>()`；若干 BO 的 `ids` 无读取
- 注释与语义不符若干处（`so_customer_order.client_code`/`total_amount`、`so_invoice.status` 枚举不全）
- 列表页不可达过滤分支（`buildQueryWrapper` 的 `projectId`/`deptId` 条件前端从不传）
- **履约链路的物料来源类型分支不完整**（= **Q8**，见 §5.2）—— ✅ **本轮已修复**：
  `confirm()` 现按 6 种来源类型分流（PROCESS→生产计划、SUBCONTRACT→委外单、TRANSFER→调拨单、
  其余 PROCUREMENT/CFM/PHANTOM→库存预留），且自制/委外/调拨三类不再占用本仓现货。
  **遗留观察**：`reserved_qty` 仍是展示用冗余字段，`fulfill()`/`cancel()` 只调 `unreserve()`
  释放库存预留记录、不回写行上的 `reserved_qty`，强制履行（部分出库）后该字段可能残留非 0 值
  —— 未纳入本轮范围。

---

## 7. 如何验证

```bash
# 后端：整个 erp 模块的全部单测（241 个，含 bd/prd/pu/sch/wm/so）
# 只跑 so 的用 -Dtest='So*Test,PricingEngineServiceImplTest'
mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false

# 前端：lint（prettier 门禁）+ 生产构建
cd plus-ui && npx eslint --fix && npm run build:prod
```

**已验证的回归基线（2026-09-19 本轮实测）**：整个 erp 模块 **285 个用例全绿**
（so 204 + bd 5 + prd 16 + pu 17 + sch 18 + wm 25 + fin 8 等），说明本次改动**未波及其它模块**。
（上一轮为 261；本轮 +24：GL 凭证 8 + 合同终止/到期 7 + 订单履约分流 9。）

**跨模块影响面核查**：本次改了 4 个 VO 的明细行类型（`SoQuotationVo`/`SoReturnVo`/`SoInvoiceVo`/
`SoContractVo` 的 `soXxxLineList` 由实体列表改为 VO 列表）。已全仓检索确认：
`getSoXxxLineList()` 的调用点**全部是 BO 的 getter**（`bo.getSoXxxLineList()`，BO 仍用实体列表、
刻意未改）且全部位于 `so` 模块内 —— 即 **VO 类型改动没有跨模块影响**。
（fin 模块的 `FinSettlementServiceImpl`/`PayReceivedConsumer` 只调用 `selectById`/`selectList`
的表头字段，不读明细行列表。）

**端到端必测（需真实环境 + 数据库）**：
1. 建单 → 确认 → 出库回写 `delivered_qty` → 履行 → 自动生成发票草稿
2. **改单后再走出库/履行，验证 `line_id` 关联不断**（T1.1 的核心验收）
3. 确认订单 → 生成生产计划 → 改单改交期 → 生产计划在详情页仍可解析
4. 退货入库后尝试删除/改量，应被拒
5. 手工建发票 → 重复建票，应被拒
6. 选物料自动带价（价目表命中）→ 改价不被覆盖
7. 报表接口 200 且只含本租户数据
8. `POST /erp/so/orderheader/{id}/submit` —— 确认跨库事务实际行为（Q5）
9. **红冲已开票发票 → 总账出现红字凭证**（Q7：验证 `so_invoice` 红票落库 + `gl_voucher` 新增负额凭证
   且原蓝票凭证未被重复生成；修复前这一步会进 MQ 死信）
10. **作废已开票发票应被拒**，提示引导到红冲（Q7）
11. **确认含委外行的订单 → `prd_subcontract` 出现草稿单**（Q8）；含调拨行 → `wm_transfer` 出现草稿单（Q8）
12. **合同过期后次日应自动变 EXPIRED**（Q9，需 SnailJob 已登记 `soContractExpiryTask`）；已生效合同可「终止」变 TERMINATED（Q9）

---

## 8. 复审建议（按风险排序）

| 优先级 | 复审对象 | 为什么 |
|---|---|---|
| 高 | `GlVoucherServiceImpl.validateBalanced`（负数分录口径） | **会计核心**：本轮放开了业务来源的负数分录（修好红冲过账），改错会让红字凭证金额符号出错。已用 8 个用例固化「业务来源允许 / 手工来源拒绝 / 借贷两侧不可同时有值 / 不平衡仍拒绝」 |
| 高 | `SoSalesOrderHeaderServiceImpl.updateByBo` / `diffUpdateLines` | 改动最大、逻辑最复杂，直接影响生产计划/出库/发票的行关联；建议重点回归"改单后各下游仍可解析"。已有场景用例：同物料两行互不串改、只加一行不误删 |
| 高 | `SoSalesOrderHeaderServiceImpl.confirm()` 的履约分流（Q8） | 会**实际创建委外单/调拨单**。重点确认：来源仓取值（可用库存最多者）与业务预期一致；委外单无供应商时留空是否符合流程 |
| 高 | `SoInvoiceServiceImpl` 的 `adjustInvoicedQty` 系列 | 资金相关；建议用真实数据验证"占用/释放/换绑/红冲/作废"五种路径后 `invoiced_qty` 始终等于"已开票合计"。已有增量数值级断言（`+ 6` / `+ 2` / `+ -2` / `+ -6`） |
| 高 | 三个 DDL 存量库脚本 | 唯一约束可能因存量重复数据而执行失败（脚本内已附前置检查 SQL） |
| 中 | `SoContractExpiryTask` 的到期判据（Q9） | `expiry_date < 今天` 表示**到期日当天仍算有效**。若业务上「失效日期当天即失效」，需改为 `<=`（改 `SoContractServiceImpl.startOfToday()` 的调用处即可） |
| 中 | `SoReturnServiceImpl` / `SoQuotationServiceImpl` 的 `normalizeLines` + 状态门禁 | 白名单重建会丢弃客户端传入的审计字段，需确认前端没有依赖这些字段回显 |
| 中 | 价目表取价链路（`resolvePrice` → 前端选料） | 折扣率语义反转（D2）会改变实际成交价，**上线前需确认存量 `discount_rate` 已按脚本迁移** |
| 低 | 4 个 VO 的 `@ExcelIgnoreUnannotated` + 明细行 VO 化 | 影响导出列与响应字段集，建议跑一次实际导出 |

---

## 9. 一条经验（供后续同类改造参考）

**改 VO 字段集之前，先比对前端类型定义。** 本次连续三轮都出现同一件事：

| 轮次 | 前端已声明 | 后端 VO 缺失 |
|---|---|---|
| 明细行改 VO（退货） | `baseUomId`/`taxRate`/`taxAmount`/`sortOrder` | `SoReturnLineVo` 缺这 4 个 |
| 明细行改 VO（发票） | `soLineId`/`contractLineId`/`sortOrder` | `SoInvoiceLineVo` 不存在 |
| 明细行改 VO（合同） | `deliveryDate`/`taxRate`/... | `SoContractLineVo` 不存在 |

其中 **`SoInvoiceLineVo.soLineId` 最关键**：前端完全不使用它，但后端靠它判断明细是否换绑订单行 ——
VO 丢掉它会让编辑自动生成的草稿发票被误判为"换绑"，先释放旧占用再按 null 不占用，
**导致 `invoiced_qty` 归零而发票仍在，重新打开 T1.4 刚堵上的超开缺口**。
即：一个纯"清理泄漏字段"的改动，若按直觉只保留前端用到的字段，会静默破坏刚修好的机制。
