## ERP 页面调研结果（agent 已返回）

> 历史 V3.0 写作调研，保留当时记录。当前 V4.0 的操作、流程图与维护方式见 [README](./README.md)，下文不是当前代码或生产配置的验收依据。

### 物料（bd/material）
- 搜索：编码/名称/来源类型/状态；左侧分类树筛选
- 字段：编码*（自动生成previewCode MATERIAL_CODE）/名称*/规格/描述/分类*（树）/基本单位/采购单位/库存单位/销售单位/类型*（bd_material_type）/来源类型/虚拟件switch/批次管理/序列号管理/安全库存*（新增时自动联动minStock=安全×1.5,maxStock=min×3）/最低最高库存/标准成本/参考价格/状态/质量状态（默认PENDING）/需检验/保质期(天)/重量/体积
- 校验：materialCode/materialName/categoryId/materialType/safetyStock 必填
- 供应商Tab（物料-供应商关系）+扩展属性Tab

### 物料分类（bd/category）
- 树形表格/拖拽视图；分类属性模板(CategoryAttrManager)；删除阻断(有下级/关联物料列清单)
- 拖拽改父级：拖入=子级，拖到上/下=同级；防循环

### 工序（bd/process）
- 编码*/名称*/类别(PROCESSING加工/ASSEMBLY装配/INSPECTION检验/PACKAGING包装)/默认工作中心(选后回填code/name)/标准工时/准备工时/人工数/机台数/关键工序/委外/需要质检(switch)

### 工艺路线（bd/routing）
- 编码*/名称*/版本(默认1.0)/适用物料(搜索选择)/生效失效日期/状态
- 工序步骤子表：工序(选后带出工时/关键/委外/质检)/步骤名称(可自定义)/工作中心(可覆盖)/标准工时/准备工时
- 保存：totalSteps=步骤数, totalHours=Σ(标准+准备)；"激活"设当前版本

### BOM（bd/bom）
- 树形表格懒加载；主表：父项物料*/版本号*(默认1.0)/名称*/生效失效/来源类型/类型/工艺路线(带"(当前)"标记)/状态
- 明细子表：子项物料/单位父项用量(精度6)/损耗率(0-100)/工序步骤(自工艺路线)/提前期/来源类型/生效失效/倒冲switch；替代料(物料/用量/优先级/类型FULL-PARTIAL-INTERCHANGEABLE/生效失效)
- 操作：复制BOM(版本.1)/BOM比较(选2条,4类Tab:移除/新增/修改/相同)/Where-Used反查/成本卷积(材料+工序成本)
- 校验：至少保留一行明细

### 客户（bd/client）
- 编码*(自动生成CLIENT_CODE)/名称*/类型*(企业/个人)/社会信用代码/省市县街道四级级联/送货地点*/简介*/业务员(先选部门SALES)/官网/邮箱*/电话*/联系人1*/电话*/LOGO/开票信息(税号/抬头/开户行/账号)

### 供应商（bd/vendor）
- 编码*(VENDOR_CODE)/名称*/税号/联系人/电话/银行账户/省市县街道级联/采购员(先选部门PURCHASE)/行业/地区/分类(原材料/标准件/服务类)/合作状态(合作中/已冻结)/开票信息

### 车间/工位（bd/workshop, workstation）
- 车间：编码*(WORKSHOP_CODE)/名称*/描述/主任*(用户选择)/产能/排序/所属部门(树)
- 工位：编码*(WORKSTATION_CODE)/名称*/所属车间*(对话框)/设备ID/标准操作人数/标准产能/描述

### 计量单位（bd/measure）
- 三栏：类别/基本单位卡片/衍生单位；编码*/名称*/符号*/类别*/基本单位(否时需选基本单位+转换率*)

### 序列号规则（bd/serial-number-rule）
- 编码*/名称*/类型(产品SN/条码/批次号)/前缀/日期格式/流水号长度(1-10默认4)/重置周期(NONE/DAY/MONTH/YEAR)/校验位(NONE/MOD10/MOD11)；行内"生成"测试

### 物料-供应商（bd/material-vendor）
- 物料*(远程搜索)/供应商*(远程搜索)/单价/币种(默认CNY)/供货周期/最小订货量/优先级/默认供应商/生效失效/状态

### 报价单（so/quotation）
- 编号*(SO_QUOTATION_CODE)/客户/销售员/币别/汇率(默认1)/折扣率/付款条件；行：物料/数量/单价/金额(自动=数量×单价)/税率/交货日期
- 状态：DRAFT→确认→CONFIRMED→转换销售订单

### 销售合同（so/contract）
- 编号*/名称*/类型(默认SALES)/客户/代表/币种(默认CNY)/签订生效失效日期/金额/付款条款/状态(默认DRAFT)；行：物料/数量/单位/单价/行金额/交货日期
- 状态硬编码：DRAFT/APPROVING/EFFECTIVE/EXPIRED/TERMINATED

### 销售订单（so/orderheader）
- 编号*(SO_ORDER_CODE)/客户订单CO(带出客户/日期/币种/销售员)/来源客户*/销售代表/订单生成日期*/承诺交期/客户要求交期/发货开票地址/付款条款/币种/汇率/整单折扣率/信用状态/项目/销售部门/分销渠道/客户参考号
- 行：物料/数量/单价/行折扣率/税率/交期/优先级/默认仓库/PA配套组
- 计算：行金额=数量×单价；行税额=(行金额-行折扣额)×税率%；含税总金额=价税合计-整单折扣额
- 状态：DRAFT→提交审批→APPROVING→APPROVED→确认→CONFIRMED→履行(force二次确认)→FULFILLED；关闭/取消(需原因)
- 信用检查(so_credit_status)/定价引擎/履行fulfillOrder

### 销售退货（so/return）
- 编号*(SO_RETURN_CODE)/销售订单/退货日期/类型(全部/部分/破损)/原因/退款金额/客户/仓库；行：物料/数量/单价/金额/税率/原因
- 状态：DRAFT→审批→CONFIRMED→RECEIVED→CLOSED

### 销售发票（so/invoice）
- 发票号*/类型(默认VAT_NORMAL)/客户/代码/开票渠道(默认MANUAL)/购方四要素/币种/行(物料/数量/单价/税率默认13%)
- 操作：核销(ISSUED,本次核销金额)/红冲(生成红字负数发票冲减已核销)/作废(回退订单行已开票数量)
- 状态：DRAFT/APPROVING/ISSUED/VERIFIED/REJECTED/CANCELLED

### 价格表（so/price-list）
- 名称*/类型(BASE基价/CUSTOMER客户专属/PROMOTION促销)/客户/生效失效/币别(默认CNY)/状态(默认DRAFT)；行：物料/单价/最小数量/最大数量/折扣率

### 客户订单CO（so/customerorder）
- 单号*(自动生成)/客户/部门/下单日期/金额/优先级/客户采购订单号/来源渠道(PORTAL/SALES/EDI)

### 采购申请（pu/requisition）
- 单号*(PU_REQUISITION_CODE)/名称/申请日期/采购部门/需求日期/来源类型(默认MANUAL)/状态(默认DRAFT)/审批流程；行：物料/数量/需求日期
- 状态：DRAFT→提交审批→APPROVING→APPROVED→转单；低库存生成(generateFromLowStock)

### 采购订单（pu/order）
- 订单号*(自动生成)/采购申请(选后自动生成订单行)/供应商(可勾"仅显示该物料供应商")/订单日期/预计到货/采购部门/采购员(先选部门)/币别/汇率(修改触发行金额重算)/合同编号(仅已批准)/审批流程；行：物料/数量/单价/金额
- 展开行：收货状态(未收/待收/部分/全部/超收)+进度条；收货联动receivePurchaseOrder生成到货通知；差异报表
- 状态：DRAFT→审批→APPROVING→APPROVED(9态完整状态机)

### 采购合同（pu/contract）
- 编号(系统自动生成)/名称*/供应商/类型(FRAMEWORK框架/SPOT一次性/SERVICE)/签订日期/币种/生效失效/付款条款/审批流程；行：物料/单价/数量/行金额
- 状态：DRAFT/APPROVING/APPROVED/EXECUTING/COMPLETED/TERMINATED/REJECTED

### 采购退货（pu/return）
- 单号*(自动生成)/采购订单(带出供应商)/退货日期/供应商(只读)/原因；行：物料/数量/单价/金额
- 状态：DRAFT→审批→CONFIRMED→EXECUTED；CANCELLED

### 供应商评估（pu/vendor-evaluation）
- 供应商*/评估周期(YYYY-MM)/交货及时率/质量合格率/价格竞争力/综合评分(只读,计算生成)/等级A-D
- 计算评估/批量计算；评分颜色分级≥90绿≥80蓝≥60黄<60红

### 采购比价（pu/price-comparison）
- 纯查询：物料远程搜索→供应商价格对比表(最低价高亮)+统计卡片(最低/最高/平均/价差%)+柱状图+趋势折线

### 采购发票（pu/invoice）
- 发票号*/类型/供应商/销方四要素/行同销售
- 操作：收票(DRAFT→ISSUED)/匹配(关联采购订单)/认证(ISSUED UNCERTIFIED→CERTIFIED)/抵扣(CERTIFIED)/核销/红冲/作废
- 认证状态：UNCERTIFIED/CERTIFIED/DEDUCTED/TRANSFERRED；从订单生成

### 生产计划（prd/prdplan）
- 编号*(PRD_PLAN_CODE默认开启)/销售订单(选CONFIRMED的SO带出)/名称/版本号/类型(MPS/MRP)/计划日期/开始结束/总数量/状态(只读)/优先级/计划员(默认当前用户)/生产部门/备注/BOM(默认isCurrent=1)
- BOM计算generateMaterialRequirementsWithDetails→物料清单树(采购件/加工件)+抽屉(标准用量/损耗率/需求数量/已发料/当前库存/缺料/工序步骤/需求日期)
- 状态：DRAFT→确认→CONFIRMED(下达时间由工单审批通过写入)

### 生产工单（prd/prdwork）
- 编号*(自动生成)/来源计划(带出产品BOM车间数量)/来源类型/销售订单(只读)/产品/计划数量/优先级/BOM/主车间/资源分配(车间/工作站/班组)/负责人/计划开始结束/倒冲switch
- 状态：CREATED→提交审批→APPROVING→APPROVED→下达→RELEASED→开始→IN_PROGRESS→完成→COMPLETED；关闭CLOSED

### 物料需求（prd/requirement）
- 编号*(自动生成)/生产计划/物料/来源类型/需求日期/需求总量/明细(导入BOM按钮)
- 生成工单：须选同一计划下需求单(跨计划提示)

### 返工（prd/rework）
- 单号/来源工单(带出物料)/返工数量/原因(返工/维修/报废返工)/实际数量/报废数量
- 操作：开始(DRAFT→IN_PROGRESS)/完工/返工出库/返工入库

### 委外（prd/subcontract）
- 单号/供应商/物料/单价/关联工单/计划数量/开始结束日期
- 操作：提交审批/下达/委外发料/委外收货/完工
- 状态：DRAFT→SUBMITTED→APPROVED→IN_PROGRESS→COMPLETED→CLOSED

### 产能日历（prd/capacity-calendar）
- 列表+日历双视图；生成(排班计划ID+对象类型+日期范围批量生成)
- 字段：日期/对象类型(车间/工位/产线)/对象/班次/可用工时/已计划/剩余/效率/状态

### 工单资源（prd/work-order-resource）
- 选工单→资源行：资源类型(工作站/班组/工人)/编码/名称/计划开始结束/状态(PENDING/PROCESSING/COMPLETED)

### 班次（sch/shift）
- 编码*/名称*/开始结束时间/工时/跨天/休息时间/迟到阈值/早退阈值/类别(生产/弹性/远程)/颜色

### 班组（sch/team）
- 名称*/类别*(QC/维护/装配/焊接/注塑/冲压/喷涂/包装/调试/切割)/所属车间/班组长(工人选择)/成员数量(输入自动添加等量空行)/成员子表(姓名/工号/角色)

### 工人（sch/worker）
- 关联用户(选择后姓名手机性别禁用)/所属车间/工号/姓名/手机/性别/工种/技能等级(1-5级)/在岗状态(在岗/离职)/入职日期

### 排班计划（sch/plan）
- 编码*/名称*/开始结束/周期类型(周/月/季/年)/轮班方式(白班/两班倒/三班倒)/状态(draft/published/closed)
- 排班页：ShiftManage(计划内班次)/TeamManage(班组加入计划)/TeamScheduleManage(班次分配班组el-transfer)

### 假期（sch/holiday）
- 日历式：农历+节假日tag("班"黄/"休"绿)；类型(法定/调休/公司假日)；是否调休isWorkingDay

### 考勤（sch/attendance）
- 人员/日期/上班下班打卡时间/迟到早退分钟/结果(normal/late/early/absent/leave/late_early_leave)/数据来源
### 打卡规则（sch/punch-rule）
- 规则名称/适用班次(或所有)/上班下班时间/允许打卡时间窗/打卡地点/半径(米)/经纬度/允许外勤/启用
### 员工排班（sch/employee-schedule）
- 人员/班组/班次/日期/开始结束时间/状态(normal/swapped/leave/absent/rest)/是否工作日

### 仓库（wm/warehouse）
- 编码*(auto-gen WAREHOUSE_CODE)/名称/省市县街道/经度纬度/位置/面积/状态(正常/冻结)/负责人/归属部门(WAREHOUSE)
- 3D预览(3d.vue)；库区数量跳转
### 库区（wm/storagearea）
- 编码*(WM_AREA_CODE)/名称/所属仓库/布局类型(网格/货架/流体/散货/托盘)/面积/库位管理开关/启用/冻结
### 库位（wm/storagelocation）
- 编码*/名称/所属库区(仓库/库区级联)/面积/最大载重/允许产品混放/允许批次混放/类型(存储/拣货/中转)/状态(空置/占用/锁定/预警)/占用率；批量生成
### 库存（wm/stock）
- 左分类树+右明细；搜索：物料/批次/仓库/入库时间/有效期/冻结
- 字段：物料/仓库库区库位(级联)/批次/工单/供应商/生产日期/入库时间/有效期/冻结/数量
### 采购收货（wm/receive）
- 编号*(自动生成)/名称/到货通知/采购订单/供应商/入库日期/审批流程
- 状态：DRAFT→审批→APPROVING→APPROVED→上架→SHELVED→入库执行→FINISHED；REJECTED可改
- 上架弹窗：物料(Camera扫描识别料号批次)/入库数量/批次ID(自动生成)/批次号/仓库库区库位级联；detailList明细
### 销售出库（wm/sales-issue）
- 编号*/名称/出库日期/销售订单(带出客户)/仓库/审批流程
- 行：物料/批次(先选物料)/库位(先选仓库)/库存数量(红警)/出库数量(max=库存)；从订单导入
- 状态：DRAFT→审批→APPROVED→确认→CONFIRMED→完成→COMPLETED
### 生产领料（wm/productionissue）
- 编号*/工单(带出物料)/仓库/领料日期；行：物料/批次/库位/库存数量/领料数量
- 执行领料executeProductionIssue；状态：DRAFT/CREATED待领料/PARTIAL部分领料/COMPLETED/EXECUTED/BACKFLUSH倒冲
### 调拨（wm/transfer）
- 编号*/调出仓库/调入仓库/调拨日期/审批流程；行：物料/批次/数量/单位
- 状态：DRAFT→审批→APPROVED→执行→EXECUTED
### 盘点（wm/stocktaking）
- 编号*/名称/盘点日期/仓库/库区(限定范围)/审批流程；行：物料/库位/批次/账面数量/实盘数量(可编辑)/差异
- 操作：生成盘点行(按仓库库区从库存自动生成)→录实盘→过账调整(自动生成库存调整事务)→COMPLETED
### 库存预警（wm/stock-alert）
- 物料/预警类型(低于最低/低于安全/超过最高)/级别(严重/警告/提示)/处理状态；执行检查checkStockAlert；处理(填说明)
### 条码（wm/barcode）
- 条码(可复制)/配置编码/业务类型/状态(ACTIVE/USED/EXPIRED)；生成条码；config规则配置
### 批次（wm/batch）
- 编码/物料/供应商/仓库/生产日期/有效期/数量/已收/已发/状态(ACTIVE有效/LOCKED锁定/EXPIRED过期/CLOSED关闭)/源单号；检查过期checkExpiry；状态变更changeStatus
### 序列号（wm/sn）
- 序列号/物料/规格/批次/仓库/库位/状态(在库/出库/预留/报废)/来源单号/规则ID；批量生成(数量1-1000)；绑定库存(选仓库库位库存ID)/解绑
### 估值（wm/valuation）
- 纯查询：物料/单位成本(红粗)/库存总金额；导出
### 追溯（wm/trace）
- 纯查询：物料/批次/SN/方向(正向/反向)；树形/列表视图；节点含单据类型+号
### 杂项出入库（wm/miscissue, miscrecpt）
- 编号*/类型(出库:报废/损耗/赠品/其他；入库:盘盈/赠品/样品/其他)/仓库/日期；行：物料/数量/单价/金额/库位
- 状态：DRAFT→EXECUTED
### 完工入库（wm/productionrecpt）
- 入库单号*(自动生成)/工单(带出物料)/数量/仓库/库位；执行入库回写工单完成数
### 到货通知（wm/arrivalnotice）
- 编号*/名称/采购订单(带出供应商明细)/供应商/到货日期/联系人/联系方式/审批流程；行：物料/到货数量
- 状态：DRAFT→审批(待质检)→APPROVED(待入库)→FINISHED
### 拣货/上架策略（wm/pickingrule, putawayrule）
- 编码*/名称*/仓库/物料类别(树)/策略类型(FIFO/FEFO/批次优先/库位路径；最近空库位/分类指定/FIFO库位)/优先级/状态
### 台账校验（wm/reconciliation）
- 账面库存vs事务汇总差异(非零红)；执行校验reconcile
### 库存报表（wm/report）
- Tab1汇总(饼/柱+表)/Tab2库龄(区间/SKU/数量/金额)/Tab3周转率(月数1-24默认6)
### 库存预留（wm/reservation）
- 物料/仓库/库位/在库/已预留/可用(着色)/关联工单；手动预留(物料+数量+仓库不限+工单)
