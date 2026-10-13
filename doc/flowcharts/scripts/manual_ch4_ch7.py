#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第4章 采购管理 + 第5章 请购管理 + 第6章 生产管理 + 第7章 排产调度
供 gen_manual_v3.py 导入调用。
2026-10-12 按 plus 分支 fe59e4ae 的现行前后端源码核对；不代表生产部署或人工验收。
"""


def ch4_pu(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第4章 采购管理')
    para(doc, '采购管理涉及采购申请、合同、采购订单、到货收货、退货、供应商评估与进项发票。'
              '部门请购是独立的需求登记页面，并非采购申请审批和转单的同一对象。')
    para(doc, '核对说明：本章依据2026-10-12的plus分支源码（基线fe59e4ae），不保证生产环境已部署或现场已验收。'
              '菜单名称、层级和按钮可见性由动态菜单、字典及角色权限决定；下列功能页路径均相对plus-ui/src/views，'
              'API路径用于辨认后端能力，不等于可直接访问的浏览器路由。')

    # ---------- 4.1 部门请购 ----------
    h2(doc, '4.1 部门请购')
    para(doc, '功能页：erp/po/requisition/index.vue；API：/erp/po/requisition。详细操作见第5章。')
    para(doc, '单头维护请购单号、部门、申请人、需求日期、状态和备注；明细维护物料、规格、需求数量、单位、需求日期和备注。'
              '当前页面没有“建议供应商”“价格”“提交审批”或“转采购订单”操作，不能据状态名称推断已经实现采购联动。')

    # ---------- 4.2 采购申请 ----------
    h2(doc, '4.2 采购申请')
    para(doc, '功能页：erp/pu/requisition/index.vue、form.vue；API：/erp/pu/requisition。'
              '列表可按申请单号、申请名称和状态检索，提供查看、历史单维护、导出及独立认证入口。')
    h3(doc, '4.2.1 可信来源创建')
    numbered(doc, '点击列表“可信来源创建”，在CertifiedPurchaseDialog.vue填写真实物料ID、供应商ID、需求窗口截止日及需求明细ID。'
                   '需求明细来自生产物料需求，不是部门请购行，也不能把物料ID当成MRP明细ID。')
    numbered(doc, '先执行“服务端完整预览”。页面调用/erp/ai/certified-source/preview，显示基本单位、订货单位、覆盖需求、订货数量、'
                   '需求日期、库存证据及来源指纹；数量以服务端核验结果为准，无库存记录不等于库存为0。')
    numbered(doc, '核对预览后明确创建，调用/erp/pu/requisition/certified生成DRAFT采购申请。'
                   '保留本次请求键和原载荷；超时、断线等结果未知时使用“明确恢复原请求”，不要换键或改数量盲目重试。')
    bullet(doc, '普通BO新增已关闭；旧“低库存生成”按钮只显示暂停提示，generateFromLowStock()也明确拒绝。'
                '后端保留认证/mrp及/low-stock入口，但这不代表旧低库存扫描已恢复或存在自动周期采购。')
    bullet(doc, '认证来源、租户、单位和占额不允许通过普通表单补填；历史单查看或维护不会升级为认证单。')
    h3(doc, '4.2.2 审批与转单边界')
    para(doc, '认证申请从列表“认证生命周期”进入，重新读取原生详情、状态与版本。'
              '现行approve直接将DRAFT变为APPROVED；convert只接受已批准且版本匹配的申请，生成未定价DRAFT订单并将申请置ORDERED。'
              '批准和转单是两次独立确认，分别需要pu:requisition:approve、pu:requisition:convert权限。'
              '该链路不走旧Flowable；带select_flow_id的申请不能据此直接转换。')
    table(doc,
          ['状态', '含义', '实际操作边界'],
          [
              ['DRAFT 草稿', '认证创建的初始状态', '认证生命周期批准；普通表单不编辑认证来源'],
              ['APPROVED 已批准', '申请已批准，尚未形成采购订单', '认证生命周期转单，重新核验来源及版本'],
              ['ORDERED 已转单', '已关联实际采购订单', '查看原订单；不得再通过旧转单重复新增'],
              ['APPROVING 审批中', '历史普通申请的Flowable状态', '仅适用通过普通来源守卫的历史单'],
              ['REJECTED 已驳回', '历史流程驳回', '与认证直接批准不是同一路径；旧submit仅接收DRAFT'],
              ['CLOSED / CANCELLED', '关闭 / 作废', '后端专属接口校验来源、版本与下游订单；不释放已承接来源'],
          ],
          widths=[4.0, 4.5, 7.5])
    para(doc, '历史普通申请详情保留申请单号、名称、日期、采购部门、需求日期、来源类型、审批流程、备注及物料数量明细。'
              '经来源守卫允许的历史单可用旧Flowable审批，回调通过置APPROVED、驳回置REJECTED、撤回等置DRAFT；'
              '但convertToPurchaseOrder()已停止旧转单，所以“历史审批通过后点旧转单”不是现行可用的新采购流程。'
              '源码依据：PuPurchaseRequisitionServiceImpl、PuCertifiedPurchaseWriteService、PuCertifiedPurchaseWorkflowService。')

    # ---------- 4.3 采购合同 ----------
    h2(doc, '4.3 采购合同')
    para(doc, '功能页：erp/pu/contract/index.vue、detail.vue；API：/erp/pu/contract。')
    para(doc, '表单维护合同编号、合同名称、供应商、合同类型、签订日期、币种、生效/失效日期、付款条款、审批流程及备注；'
              '明细维护物料、数量、单价和税率等。编号可由系统生成，明细金额和税额最终由服务端重算。')
    numbered(doc, '新增草稿并维护合同及明细，选择已配置的合同审批流程后提交。')
    numbered(doc, '查看Flowable处理结果：doing为APPROVING，通过为APPROVED，驳回为REJECTED，撤回等回到DRAFT。'
                   '字典中出现执行中、完成或终止名称，不表示当前页面提供对应独立状态操作。')
    para(doc, '历史普通订单表单的合同选择只查询APPROVED合同，但合同不能代替全局需求来源认证。'
              'PuContractServiceImpl.createPurchaseOrderFromContract()当前拒绝直接建订单，不能写成“合同审批后自动生成订单”。'
              '付款条款也只是合同资料，不会触发付款。')

    # ---------- 4.4 采购订单 ----------
    h2(doc, '4.4 采购订单')
    para(doc, '功能页：erp/pu/order/index.vue、form.vue；API：/erp/pu/order。'
              '列表按订单号、供应商、状态检索；展开订单查看行数量、已收数量、退货数量、净采购量及收货进度。'
              '收货状态与订单审批状态分开显示。')
    h3(doc, '4.4.1 认证订单报价与批准')
    numbered(doc, '先在采购申请的认证生命周期完成转单。采购订单列表“新增”当前仅提示从认证申请转入，普通BO新增也被后端拒绝。')
    numbered(doc, '选中原订单，打开“认证报价／生命周期”。录入真实供应报价：未税单价、税率、供应商报价引用、联系人、承诺到货日、'
                   '有效期、证据引用及SHA-256、付款条款和交付条款；现行认证报价只支持CNY、汇率1。不得用缺省0补造报价。')
    numbered(doc, '由不同于报价录入人的操作人选择原报价并填写复核说明，执行“独立确认原报价”；'
                   '随后以相应权限执行“批准原订单”，核验原报价、原订单版本和全部需求来源。'
                   '批准把未定价DRAFT订单变为APPROVED并固化真实条款，不自动生成到货通知或收货单。')
    para(doc, '认证报价录入、复核、价格查看和批准是独立权限；关闭、取消还校验未履约来源、收退货和发票事实。'
              '当前来源、版本或结果未知时回查原请求，不能用普通编辑或旧Flowable补来源。'
              '源码依据：PuCertifiedPurchaseOrderWorkflowService及CertifiedOrderWorkflowDialog.vue。')
    h3(doc, '4.4.2 历史普通订单详情')
    table(doc,
          ['字段', '说明'],
          [
              ['订单号 / 采购申请号', '用于查看原订单与来源；不能通过普通新增承接新需求'],
              ['供应商', '历史表单可按物料-供应商关系筛选；认证订单必须通过原报价入口核验'],
              ['订单日期/预计到货', '计划信息'],
              ['采购部门/采购员', '采购员需先选部门'],
              ['币别/汇率/运费', '历史普通表单字段，不代表认证报价支持任意币种'],
              ['合同编号', '历史选择器只提供已批准合同'],
              ['明细行', '物料、数量、单价、税率与金额；已收和已退数量来自业务回写'],
              ['审批流程', '仅普通来源守卫允许的历史订单可用旧Flowable'],
          ],
          widths=[5.0, 11.5])
    para(doc, '订单状态编码包括DRAFT、SUBMITTED、APPROVING、APPROVED、PARTIALLY_RECEIVED、RECEIVED、CLOSED、CANCELLED、REJECTED。'
              '认证链路直接批准与历史Flowable审批的合法操作不同，不能机械按九个状态逐一执行。'
              '行级PARTIAL_RECEIVED、FULL_RECEIVED、OVER_RECEIVED等用于显示数量差异，不等于允许任意超收；'
              '收货和回写有剩余量及容差守卫。“差异报表”查询未达到FULL_RECEIVED的行，不是自动补货或结算。')
    h3(doc, '4.4.3 到货、IQC、上架与显式入库')
    numbered(doc, '确认订单已经批准。普通历史订单“收货联动”只创建DRAFT到货通知，认证订单需通过仓储认证入口，'
                   '不能复制列表数量走旧联动。到货通知功能页为erp/wm/arrivalnotice/index.vue，认证API为/erp/wm/arrivalnotice/certified。')
    numbered(doc, '核对到货通知及实际采购行来源，提交相应到货审批。普通通知审批通过后生成DRAFT物料收货单；'
                   '认证通知按专属创建及流程同步入口核验，不借用普通通知旁路。')
    numbered(doc, '在erp/wm/receive/index.vue、form.vue核对实际收货行、实收数量、批次和采购订单行。'
                   '普通自动生成收货行的实收量初始为0，应按实物填写，不能直接把订单量视为实收量。')
    numbered(doc, '收货单提交审批，通过后为APPROVED（待上架），不是已入库。'
                   '审批后仅为到货通知中iqc_check=1的需检物料发送ReceiveApprovedMessage，由MES创建IQC；'
                   '无通知关联的普通收货不自动创建IQC，需另行核实检验来源。')
    numbered(doc, '在MES完成来料检验。ErpIqcCompletedConsumer消费结果并校验原始收货、单位和检验证据；'
                   '不合格进行隔离，合格证据用于后续放行。IQC完成不会调用executeReceive，也不会自动把库存过账完成。')
    numbered(doc, '维护并保存上架明细，分配真实库位，核对明细数量与实收量。'
                   '明确执行“确认上架”：普通通道校验需检物料合格，认证通道还核验原生IQC证据；APPROVED变为SHELVED（待入库）。')
    numbered(doc, '在待入库状态明确执行“入库”（executeReceive），或由PDA的finish显式调用执行入口。'
                   '认证单路由到WmCertifiedReceiptPostingService，普通历史单路由到WmMaterialReceiveExecutorServiceImpl；'
                   '分别核验来源和防重凭据后完成库存及流水过账，收货单成为FINISHED。')
    numbered(doc, '回查库存流水、收货单及采购行已收数量。执行提交后发送采购数量回写MQ，'
                   'PurchaseReceiveWritebackConsumer调用采购回写服务更新已收数量和订单PARTIALLY_RECEIVED/RECEIVED状态。'
                   '认证过账的notificationPending表示库存可能已提交但通知仍待恢复，不能再新建收货单或重复入库。')
    para(doc, '库存MQ边界：StockInConsumer仍有PURCHASE消息过账及采购回写分支；它不是IQC完成后的自动执行器。'
              '正常收货审批不再发送StockInMessage，现行显式执行路径自身完成库存过账后再通知采购回写。'
              '消息接受、消费完成和页面状态刷新是不同事实，需要核对各自凭据。')
    para(doc, '财务边界：采购申请批准、订单批准、收货或IQC完成均不自动付款，也不自动完成应付核销。'
              '进项发票、财务收付款单、结算和总账过账应分别按其真实单据及权限办理，见4.8。')

    # ---------- 4.5 采购退货 ----------
    h2(doc, '4.5 采购退货')
    para(doc, '功能页：erp/pu/return/index.vue、form.vue；API：/erp/pu/return。')
    para(doc, '表单字段：退货单号*（自动生成）、采购订单（选择后带出供应商）、退货日期、供应商（只读）、退货原因；退货明细：物料、退货数量、单价、金额。')
    para(doc, '普通历史通道：DRAFT→Flowable审批中APPROVING→通过后CONFIRMED；驳回或撤回回到DRAFT。'
              '后端还保留草稿confirm接口；页面具体按钮以权限和状态为准。认证采购来源不能绕过来源守卫使用普通退货写入口。')
    numbered(doc, '选采购订单并核对供应商，填写退货原因、物料、数量、单价及原订单行等来源资料，保存草稿。')
    numbered(doc, '完成适用的确认或审批。审批通过只变为CONFIRMED，不会自动生成并执行退货出库。')
    numbered(doc, '明确执行退货，PuPurchaseReturnServiceImpl.execute()校验CONFIRMED后扣减库存、登记退货流水并置EXECUTED，'
                   '回写关联采购行returned_quantity。应先核对可退数量和库存，不得靠修改状态代替执行。')
    para(doc, '进项税联动：普通退货执行会查找关联采购行的已抵扣蓝字发票，按退货量生成部分红字并进行进项转出。'
              '这是税额与发票处理，不是自动向供应商退款，也不是付款单或应付余额自动核销。')

    # ---------- 4.6 供应商评估 ----------
    h2(doc, '4.6 供应商评估')
    para(doc, '功能页：erp/pu/vendor-evaluation/index.vue、form.vue、report.vue；API：/erp/pu/vendor-evaluation。')
    para(doc, '表单维护供应商、评估周期（YYYY-MM）和备注；交货及时率、质量合格率、价格竞争力、综合评分、'
              '订单数、按时交货数、收货和退货数量为计算展示项。质量得分使用收货与退货数量口径，不能直接称为MES检验通过率。')
    para(doc, '评估等级：A优秀（绿）/B良好（蓝）/C合格（黄）/D不合格（红）。')
    para(doc, '操作：维护评估资料后选择供应商点“计算评估”，或用“批量计算”计算ACTIVE供应商。'
              'calculateByVendor()与batchCalculateAll()实际计算当前月份，并非按选中历史行的月份重算。'
              '另有评估报表组件，可查询趋势与等级分布；是否配置为可见菜单需在目标环境核实。')

    # ---------- 4.7 采购价格比较 ----------
    h2(doc, '4.7 采购价格比较')
    para(doc, '功能页：erp/pu/price-comparison/index.vue；选择真实物料后查询，数据来自物料供应商价格及历史趋势接口。')
    para(doc, '纯查询页面：选择物料→展示供应商价格对比表（最低价高亮）、统计卡片（最低/最高/平均价、价差比例）、供应商价格柱状图与趋势折线图。')

    # ---------- 4.8 采购发票 ----------
    h2(doc, '4.8 采购发票')
    para(doc, '功能页：erp/pu/invoice/index.vue；API：/erp/pu/invoice。')
    para(doc, '单头维护发票号、类型、代码、供应商、采购订单ID、来源渠道、销方税号/抬头/开户行/账号/地址电话、'
              '开票日期、币种及备注。明细维护物料、数量、单位、单价、税率和采购订单行ID；金额、税额和价税合计由业务计算。'
              '“从订单生成”只创建发票草稿，数量来自已收货但尚未开票的采购行余量，不代表已收票或已付款。')
    para(doc, '行内操作（按状态）：')
    table(doc,
          ['操作', '说明'],
          [
              ['收票', 'DRAFT→ISSUED（已收），发布采购发票收票事件'],
              ['匹配', '三单匹配：订单、收货、发票的物料、数量、单价与税率；展示差异'],
              ['认证', '已收且未认证→已认证'],
              ['抵扣', 'CERTIFIED→DEDUCTED，不是TRANSFERRED'],
              ['核销', '显式填写核销金额，三单匹配通过后累计核销；全额才变VERIFIED'],
              ['红冲/作废', '生成红字或作废'],
              ['从订单生成', '按已收未开票余量生成DRAFT，另行收票确认'],
          ],
          widths=[5.0, 11.5])
    para(doc, '发票状态DRAFT/ISSUED/VERIFIED/CANCELLED与进项认证状态UNCERTIFIED/CERTIFIED/DEDUCTED/TRANSFERRED相互独立。'
              'TRANSFERRED用于已抵扣发票的红冲或退货进项转出，不是“抵扣成功”的状态。')
    para(doc, '财务联动：PuInvoiceServiceImpl.receiveInvoice()发出PuInvoiceIssuedEvent；'
              'GlPostingEventDispatcher.onPuInvoiceIssued()在提交后异步发送总账过账消息，由采购发票会计处理器按配置生成凭证。'
              '这不等于银行付款或应付结清，且消息投递、凭证生成仍须核实目标环境配置与消费结果。'
              '财务收付款单确认和结算单核销是另外的操作，不能用采购审批或仓库收货代替。')

    # ---------- 4.9 采购报表 ----------
    h2(doc, '4.9 采购报表')
    para(doc, '功能页：erp/pu/report/index.vue；API封装：src/api/erp/pu/report/index.ts。选择月数（1-24）后查询。')
    bullet(doc, '采购执行趋势：按月展示订单数及完成率，而非采购金额趋势。')
    bullet(doc, '供应商交货准时率：展示供应商维度的准时率。')
    bullet(doc, '质量、评分趋势或价格对比请使用各自评估、价格比较页面；当前采购报表页没有完整采购周期、金额多维分析面板。')


def ch5_req(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第5章 请购管理')
    para(doc, '部门请购（po）用于登记企业内部物料需求。2026-10-12源码核对显示，其控制器和服务只实现查询、'
              '新增、修改、删除与导出；它不同于第4章采购申请（pu）的来源认证、批准和转单。'
              '本章描述源码已有功能，不证明目标环境菜单、客户端或生产数据已经验收。')

    h2(doc, '5.1 创建请购单')
    para(doc, '功能页：plus-ui/src/views/erp/po/requisition/index.vue；API：/erp/po/requisition。'
              '按请购单号、部门、申请人、状态检索，使用新增、修改、查看、删除及导出；菜单层级以动态配置为准。')
    numbered(doc, '点击“新增”，填写请购单号。当前是输入框，不能把它写成与采购申请相同的自动认证编号入口。')
    numbered(doc, '选择部门，再通过该部门的用户选择框指定申请人；填写需求日期、状态和备注。'
                   '页面没有独立“请购原因”字段，可在备注记录需求背景。')
    numbered(doc, '点击“添加行”，选择物料，核对编码、名称、规格，填写需求数量、单位、行需求日期和备注；'
                   '不需要填写供应商或价格，页面没有这些列。删除行前选中相应明细。')
    numbered(doc, '点击对话框“确定”，新增写入请购单头和明细；修改保存会按单据重建明细。'
                   '保存后通过“查看”核对单头、数量、日期和明细完整性。')

    h2(doc, '5.2 请购单状态流转')
    para(doc, '状态下拉使用pu_purchase_requisition_status字典，但PoRequisitionServiceImpl仅按BO保存状态，'
              'PoRequisitionController没有submit、approve、convert等接口，页面也没有提交或查看审批流程按钮。'
              '因此不能把字典标签写成已实现的“草稿→审批→转采购订单”状态机。')
    para(doc, '维护人员应按企业约定登记状态与备注，状态名称只表示录入值，不证明审批发生；'
              '需要采购审批时应进入第4章采购申请的真实功能，并核实对应可信来源。')

    h2(doc, '5.3 请购转采购订单')
    para(doc, '当前部门请购没有直转采购订单的页面操作或服务实现，本节不能作为转单步骤使用。')
    numbered(doc, '先核对部门请购资料，明确真实物料需求；请购登记本身不会占用MRP来源或生成采购供应。')
    numbered(doc, '需要采购时，由具备权限的人员在erp/pu/requisition/index.vue使用“可信来源创建”，'
                   '指定真实物料、供应商和MRP需求明细，并完成服务端完整预览。不能把部门请购行ID当成MRP明细来源。')
    numbered(doc, '经采购申请的认证生命周期批准并转单后，再到erp/pu/order/index.vue办理原供应报价、独立确认与订单批准，'
                   '详见4.2、4.4；不会自动回写部门请购为“已转采购订单”。')
    para(doc, '源码依据：ruoyi-erp的po/controller/PoRequisitionController.java、po/service/impl/PoRequisitionServiceImpl.java，'
              '以及plus-ui/src/api/erp/po/requisition/index.ts。不能以历史说明或字典共用推定额外联动。')


def ch6_prd(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第6章 生产管理')
    para(doc, '生产管理包括生产计划、BOM展开与物料需求、工单、APS排产、报工、返工、委外及完工库存处理。'
              '2026-10-12依据现行源码核对；下列功能页均相对plus-ui/src/views，菜单与字典以目标环境动态配置为准。'
              'ERP单据状态、MES执行、MQ消费和库存过账应分别核实，本章不证明生产部署或现场验收。')

    # ---------- 6.1 生产计划 ----------
    h2(doc, '6.1 生产计划')
    para(doc, '功能页：erp/prd/prdplan/index.vue、form.vue；API：/erp/prd/plan。')
    para(doc, '表单字段：')
    table(doc,
          ['字段', '说明'],
          [
              ['生产计划编号*', '自动生成（PRD_PLAN_CODE，新增默认开启）'],
              ['销售订单', '选择器查询已确认CONFIRMED订单，选订单行后带出来源、数量及计划参考信息'],
              ['计划名称/版本号', '草稿维护；版本变更有独立后端校验，不等于BOM自动升级'],
              ['计划类型', '由prd_plan_type字典显示，不是单独的MRP运行页面'],
              ['计划日期/开始/结束', '计划时间范围'],
              ['计划总数量', '核对销售订单行带出的数量或实际计划数量'],
              ['优先级/计划员/生产部门', '计划员默认当前登录用户'],
              ['BOM名称/版本', '必须明确选择计划使用的BOM；服务端核验父物料、版本、租户和有效期'],
          ],
          widths=[5.5, 11.0])
    numbered(doc, '保存计划及明确选定的BOM，再点击“BOM计算”。实际写入口为'
                   'POST /erp/prd/plan/generateMaterialRequirementsWithDetails/{planId}，不是仅查询BOM树的calculateBom接口。')
    numbered(doc, '核对生成的物料需求树：有BOM的加工节点可形成独立需求单，采购件不继续展开子BOM，虚拟件跳层。'
                   '查看标准用量、损耗率、需求数量、已发料、库存、缺料、工序步骤和需求日期，再到物料需求页面生成工单。')
    para(doc, '重新计算不是无条件覆盖来源：已有物料需求与选定BOM的根物料、租户或版本不一致时会拒绝，'
              '应先人工审计来源，不要切换BOM后反复点击计算。')
    para(doc, '状态：DRAFT→确认→CONFIRMED；工单审批通过后autoReleaseProductionPlan()可将对应已确认计划置RELEASED并写下达时间。'
              '计划自身也有后端release入口；计划已下达不代表工单已经发送到MES。'
              '关联工单均完工或关闭时，工单完工服务可把RELEASED计划置COMPLETED。')

    # ---------- 6.2 生产工单 ----------
    h2(doc, '6.2 生产工单')
    para(doc, '功能页：erp/prd/prdwork/index.vue、form.vue；API：/erp/prd/work。')
    para(doc, '表单字段：工单编号（自动生成）、来源计划、来源类型、产品、计划数量、单位、优先级、BOM名称/版本、主车间、'
              '资源分配（车间/工作站/班组）、生产部门、负责人、计划日期、实际开始/结束时间、审批流程、倒冲开关与备注。'
              '来源类型保留后台字典，前端补充MATERIAL_REQUIREMENT物料需求；委外创建工单为SUBCONTRACT，不能只列手工/计划/销售订单。')
    para(doc, '状态流转：')
    table(doc,
          ['状态', '可执行操作'],
          [
              ['CREATED 已创建', '维护、提交审批；后端release也允许直接下达，页面行内下达通常只显示于APPROVED'],
              ['APPROVING 审批中', '查看流程'],
              ['APPROVED 已审批', '下达'],
              ['RELEASED 已下达', '开始'],
              ['IN_PROGRESS 进行中', '完成；close服务拒绝直接关闭生产中工单'],
              ['COMPLETED 已完成', '后端可关闭；当前列表已完成行不显示关闭按钮'],
              ['CLOSED 已关闭', '查看；WoStatusEnum没有CANCELLED状态'],
          ],
          widths=[6.0, 10.5])
    para(doc, '操作步骤：')
    numbered(doc, '新增工单或由物料需求生成。选择来源计划后核对产品、BOM、版本、车间和数量；'
                   'MRP工单的来源单号是物料需求单号，不能称为销售订单号。来源类型以实际字典与后端数据为准。')
    numbered(doc, '资源分配页签：添加分配车间/工作站（先选车间）/班组')
    numbered(doc, '保存→选择Flowable流程提交→审批通过为APPROVED。驳回、退回或撤回回到CREATED，'
                   '不是另一个工单REJECTED状态；已下达后不能被迟到审批回调重新打开。')
    numbered(doc, '明确执行“下达”。PrdWorkOrderServiceImpl.release()校验工单来源需求和实际工艺路线，'
                   '原子置RELEASED，按该工单需求生成非倒冲领料单；缺料检查当前仅告警，不代表库存自动充足。'
                   '正式下达事务提交后发送MES工单快照，审批通过本身不发送该快照。')
    numbered(doc, '下达后执行“开始”，置IN_PROGRESS并记录开始时间。PC“完成”调用无数量参数的complete(woId)，'
                   '当前按计划数量作为本次完工量，报废量为0；它不是采集实际产量的报工表单。')
    para(doc, '数量边界：报工审核会累计工单完工及报废数量，MES完工消息则携实际完工/报废量调用数量版complete。'
              '已有报工累计或MES完工结果时，应先对账，避免再从PC“完成”追加整单计划量。'
              '已完成和报废数量在工单表单中只读；完工状态并不单独证明成品已经入库，见6.8。')

    # ---------- 6.3 物料需求 ----------
    h2(doc, '6.3 物料需求')
    para(doc, 'MRP相关的实际功能页是erp/prd/requirement/index.vue、form.vue；API为/erp/prd/requirement。'
              'BOM展开写入口位于生产计划；当前不是一个独立的“MRP运行”页面或/erp/prd/mrp接口。')
    para(doc, '单头字段：物料需求单编号、生产计划编号/ID、BOM ID、根物料编码/名称、来源类型、需求日期、需求总量、'
              '物料种类总数、计划员、生产部门、状态、确认时间和备注。明细可添加、删除及导入BOM，'
              '核对标准用量、需求量、已发料量、库存、缺料和行需求日期。')
    numbered(doc, '优先从生产计划“BOM计算”产生有来源的需求。手工“导入BOM”先填写真实BOM ID与需求总量，'
                   '会替换当前编辑明细，确认后仍需保存；不能据此声称采购申请已自动创建。')
    numbered(doc, '在列表选择同一生产计划的需求单，点击“生成工单”，调用'
                   'POST /erp/prd/plan/generateWorkOrders/{planId}。后端先校验整批归属、租户、根物料和BOM再写入，跨计划整批拒绝。')
    para(doc, 'PrdMaterialRequirementServiceImpl.calculateNetRequirements()查询未冻结的正库存，结合安全库存显示缺料。'
              '该展示口径不能替代认证采购的完整来源核验，也不是包含全部预留、在途与交期的自动采购执行。'
              '状态下拉来自prd_requirement_status；页面没有独立确认、发料、关闭状态操作，不能把字典项写成已实现的操作链。'
              '计划完工相关的倒冲处理另有将需求置COMPLETED的逻辑。')

    # ---------- 6.4 返工与委外 ----------
    h2(doc, '6.4 返工订单')
    para(doc, '功能页：erp/prd/rework/index.vue；API：/erp/prd/rework。'
              '字段为返工单号、来源工单（远程查询带出物料）、返工数量、返工原因字典项、实际数量、报废数量和备注。')
    para(doc, '实际状态为DRAFT→开始→IN_PROGRESS→完工→COMPLETED；没有该页面的审批、下达或关闭链。'
              '页面仅在草稿显示修改、删除，进行中显示返工出库、返工入库和完工。')
    numbered(doc, '草稿保存前核对来源物料、返工量、实际量和报废量，开始后再执行库存动作。'
                   '返工出库使用返工数量，返工入库使用实际数量，均经库存服务处理，不是点击“完工”即自动出入库。')
    numbered(doc, '先核实出入库流水再手工完工，完工会把返工报废量累加到来源工单。'
                   '另有ReworkStockConsumer库存消息推进状态的路径，入库数量不足返工量时不按全量完成处理。')
    para(doc, '手工返工库存入口仍需有效仓储及物料条件，不能把按钮存在视为已完成现场库位验收；'
              'COMPLETED本身也不能替代对实际返工入库凭据的核对。')

    h2(doc, '6.5 委外加工')
    para(doc, '功能页：erp/prd/subcontract/index.vue、form.vue、detail.vue；API：/erp/prd/subcontract。'
              '表单维护委外单号、供应商、加工单价、物料、BOM及版本、计划数量、计划日期、收货目标仓库、工艺要求与备注。')
    para(doc, '现行可操作主线：DRAFT或APPROVED→下达→RELEASED→开始→IN_PROGRESS→完工→COMPLETED→关闭→CLOSED。'
              '下达会创建关联的WO-SC工单；开始接口也接受APPROVED。'
              '“提交审批”当前只是DRAFT→SUBMITTED，控制器/服务没有配套Flowable通过回调或批准接口，'
              '不能写成提交后必然自动审批并进入APPROVED的完整流程。')
    numbered(doc, '草稿先选择物料与BOM，进入“委外闭环详情”生成/重建BOM快照；核对技术文件、报价并选定报价，'
                   '按真实资料设置目标仓库和工艺要求。快照重建当前仅允许草稿，不要把它当成生产中自动换版。')
    numbered(doc, '在闭环详情办理备料建议和实际发料单，保存真实库存分配后明确过账。'
                   '仓储WmSubcontractIssueServiceImpl处理发料单DRAFT→POSTED、库存扣减及BOM行已发数量，不是仅改变委外头状态。')
    numbered(doc, '委外成品到货后，在详情建立实际收货单并明确过账。WmSubcontractReceiveServiceImpl处理DRAFT→POSTED、'
                   '成品库存与委外实际收货数量；累计实际量达到计划量时，accumulateActualQty()可将RELEASED或IN_PROGRESS订单自动置COMPLETED。'
                   '人工完工和库存达量完工是不同触发点，随后再核对关闭条件。')
    para(doc, '特别区分：/erp/prd/subcontract/issue/{id}当前仅调用start()，/receive/{id}仅调用complete()；'
              '这两个兼容接口不是仓储发料/收货过账。真实仓储单走/erp/wm/sc-issue、/erp/wm/sc-receive。'
              '委外单完工不等于实物已入库或加工费已付款，财务结算需另行办理。')

    # ---------- 6.6 产能日历与工单资源 ----------
    h2(doc, '6.6 产能日历')
    para(doc, '功能页：erp/prd/capacity-calendar/index.vue；API：/erp/prd/capacityCalendar。')
    para(doc, '按车间/工位/产线维护每日可用产能，支持列表+日历双视图（每日显示“班次名: 剩余h/可用h”，按剩余率着色）。')
    para(doc, '工具栏“生成”：输入真实排班计划ID、对象类型、对象ID/编码/名称和日期范围，按计划班次生成日历行。'
              '对象类型为WORKSHOP、WORKSTATION或LINE，不能把排班计划ID当成生产计划ID。')
    para(doc, '字段：日期、对象类型（车间/工位/产线）、对象、班次、可用工时、已计划工时、剩余工时、效率(%)、状态。')
    para(doc, '产能已被排程占用后，不应通过编辑已计划工时、删日历或重复生成抹掉占用；相关服务有来源与占额校验。'
              '发布前核对资源、日期、班次及ACTIVE状态，保证是真实可用产能，而不是只有显示数值。')
    h3(doc, '6.6.1 APS智能排产')
    para(doc, '实际APS功能页：erp/prd/schedule/index.vue；API：/erp/prd/schedule。'
              '它与第7章人员排班计划不同，不能使用/erp/sch/plan替代，也不要虚构独立/erp/prd/aps入口。')
    numbered(doc, '点击“执行排产”，填写方案名称、起始日、跨度（1-90天）及排序策略：EDD最早交期、CR临界比或PRIORITY优先级。'
                   'POST /erp/prd/schedule/run生成方案，参与工单状态包括CREATED、APPROVED、RELEASED、IN_PROGRESS。')
    numbered(doc, '查看甘特图、任务、工单数、拖期数、跳过数、总工时及完工日，核对实际工艺路线和资源。'
                   '委外工序不占本地产能；生成时无日历的日期可能按8h/日估算，这只能用于方案生成，不能作为发布产能证据。')
    numbered(doc, '选中一条新版已生成COMPLETED方案，明确点击“发布”。'
                   'PrdScheduleServiceImpl.publish()重新锁定工单、任务和产能，要求非委外逐日负荷对应真实ACTIVE班次日历且剩余产能足够。'
                   '发布成功才置PUBLISHED并登记占用；生成成功或capacityCalcVersion=2本身不保证可发布。')
    para(doc, '历史口径方案保留原状态和甘特数据，不应当作新版可信产能发布；已发布或已占用方案不能通过删除清除。'
              '交付风险分析入口还要求新版方案已经发布并具备相应权限，不会在排产生成时自动执行业务动作。')

    h2(doc, '6.7 工单资源分配')
    para(doc, '功能页：erp/prd/work-order-resource/index.vue；API：/erp/prd/workOrderResource。')
    para(doc, '先选择真实工单加载资源行，编辑资源类型、资源编码/名称、计划开始/结束时间并批量保存；可删除所选资源行。'
              '资源类型为WORKSHOP车间、WORKSTATION工作站、EQUIPMENT设备、TEAM班组、PRODUCTION_LINE生产线，当前不是“工人”资源类型。'
              '状态为PENDING待执行、PROCESSING执行中、COMPLETED已完成。')
    para(doc, '资源分配只维护工单资源资料，不能据此宣称已完成APS发布、产能占用或MES派工；这些操作各有独立入口和校验。')

    # ---------- 6.8 完工入库与倒冲 ----------
    h2(doc, '6.8 完工入库与倒冲')
    h3(doc, '6.8.1 普通领料')
    para(doc, '功能页：erp/wm/productionissue/index.vue；API：/erp/wm/productionissue。'
              '工单下达可生成该工单来源需求的非倒冲领料单，仓库仍需核对实发量和库存分配并明确执行。'
              '新单状态CREATED，可分批执行成PARTIAL，全部领完为COMPLETED；EXECUTED是历史等价完成值。'
              '工单完工只对未完成普通领料记录告警，不会替仓库补扣这些物料或把领料单直接置完成。')
    h3(doc, '6.8.2 MES完工与成品过账')
    numbered(doc, 'MES完工消息由ErpWorkOrderCompletedConsumer解析ERP工单来源，必要时将已下达工单推进生产中，'
                   '再调用complete(woId, completedQty, scrappedQty)，回写工单完工状态、数量和结束时间。'
                   '来源计划关联销售行时还回写已投产数量；这不等于已发货数量。')
    numbered(doc, 'ERP完工发送ProductionCompleteMessage，ProductionBackflushConsumer调用'
                   'ProductionCompletionPostingService.postCompletion()核对真实工单和本次完工数量，串行执行倒冲及成品入库。')
    numbered(doc, '只有工单backflushFlag=1时PrdBackflushServiceImpl才启动倒冲。当前queryBackflushDetails()按工单planId汇集同计划需求单明细，'
                   '不再按明细backflush_flag过滤，并按本次完工量、BOM用量与损耗计算扣料，创建倒冲领料单及流水。'
                   '多工单共享计划或已经普通领料时必须先对账，不能宣称此处已保证逐工单独立扣料或可自动替代普通领料。')
    numbered(doc, '同一ERP事务解析成品上架仓库/库位，并通过WmProductionRecptServiceImpl.createAndExecute()'
                   '创建执行生产入库单（功能页erp/wm/productionrecpt/index.vue）。缺少库存、单位、合法仓位或原凭据时拒绝并回滚本次过账，'
                   '需要核实消费和对账结果，不能把MQ已发送写成入库成功。')
    para(doc, '手工生产入库也有DRAFT→EXECUTING→EXECUTED执行入口，核验真实工单完工量、批次及完整库存凭据。'
              '生产入库自身不再次回写工单完工数量；现行数量回写在工单完工/报工审核，重复入库不能当成补报工。'
              '重放只认可完整原生产入库单、库存行和流水，不新造补差额，也不把历史半过账自动修复为成功。')
    para(doc, '财务与质量边界：生产完工、库存过账、质量放行及成本归集并非同一状态。'
              '本节不保证生产成本已结转、费用已支付或现场质量流程已验收；需按实际检验、财务凭据另行核查。')

    # ---------- 6.9 生产报表 ----------
    h2(doc, '6.9 生产报表')
    para(doc, '实际功能页：erp/prd/report/index.vue，含“报工单”和“统计图表”两个页签；'
              'API封装：src/api/erp/prd/report/index.ts。当前不仅是只读生产图表，也不是完整成本或设备利用率报表。')
    numbered(doc, '在“报工单”按报工编号、工单、产品、操作工、状态和报工途径检索，新增或查看报工。'
                   '单头与明细资料包含工单、产品、工序、任务、工作站、车间、设备、批次、班次、操作工与班组。')
    numbered(doc, '填写本次报工、合格、报废（工废/料废/其他）、待检数量及开始/结束时间、人工/机器/准备/等待/暂停工时，'
                   '核对质量状态、质检单号及备注。后端新增强制SUBMITTED，不能靠表单选APPROVED跳过审核。')
    numbered(doc, '有审核权限的人员审核通过，PrdWorkReportServiceImpl.updateByBo()仅在首次变为APPROVED时累计工单产量与报废量，'
                   '并校验父工单及超产边界。报工审核本身不等于工单终态完工或成品入库。')
    bullet(doc, '统计图表按月数查询工单完工率：总工单、已完工工单、完工率。')
    bullet(doc, '计划与实际完工对比：计划数量、实际数量、效率率；不是设备利用率或实际成本核算。')
    para(doc, '生产成本核算和MES质量统计需要在对应模块查看，不能以当前两张图表宣称具备完整成本、良率与不良分析报表。')


def ch7_sch(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第7章 排产调度')
    para(doc, '本章管理人员班次、班组、工人档案、排班、假期、打卡规则和考勤；生产APS排产另见6.6.1。'
              '2026-10-12按现行sch控制器、服务及PC页面核对，源码存在不代表App客户端已交付或生产已启用。'
              '功能页路径均相对plus-ui/src/views，菜单与角色权限以目标环境动态配置为准。')

    # ---------- 7.1 班次 ----------
    h2(doc, '7.1 班次管理')
    para(doc, '功能页：erp/sch/shift/index.vue；API：/erp/sch/shift。')
    para(doc, '表单字段：班次编码、班次名称、开始/结束时间（HH:mm:ss）、工时（小时）、是否跨天、休息开始/结束时间、'
              '迟到阈值、早退阈值（分钟）、班次类别、显示颜色、排序和备注；类别选项来自sch_shift_category字典，不能据类别推断远程客户端能力。')
    numbered(doc, '新增班次并核对实际起止时间、工时和跨天设置；例如夜班结束时间早于开始时间时，应配置跨天，'
                   '生成员工排班时会按跨日时间处理。')
    numbered(doc, '将班次加入排班计划后核对班组排班模板；考勤判定使用员工排班时间及其班次迟到/早退阈值，'
                   '不是只配置打卡规则即可自动生成排班。')

    # ---------- 7.2 班组 ----------
    h2(doc, '7.2 班组管理')
    para(doc, '功能页：erp/sch/team/index.vue；API：/erp/sch/team。')
    para(doc, '表单维护班组名称、班组类别、所属车间、班组长、成员数量、状态和备注。'
              '班组类别来自字典，班组长和成员应通过工人档案选择，不是任意系统用户名称。')
    para(doc, '成员子表维护姓名、工号、角色、生效日期、失效日期与状态。输入成员数量可增加空行，但空行不等于已选择有效成员；'
              '保存前逐行选择真实工人并核对有效期，成员数量应与有效成员一致。')
    para(doc, '员工排班生成会从班组排班模板展开有效成员；只有班组名称或数量、缺少模板与有效成员时，不会自动得到员工排班。')

    # ---------- 7.3 工人档案 ----------
    h2(doc, '7.3 工人档案')
    para(doc, '功能页：erp/sch/worker/index.vue；API：/erp/sch/worker。')
    para(doc, '表单字段：关联用户、所属车间、员工工号、员工姓名、手机、性别、工种、技能等级、在岗状态、入职日期、转正日期与备注。'
              '关联用户后姓名/手机/性别随用户带出且禁用；工种、技能与在岗状态按实际字典选择。用户已建档时选择器会提示并禁止重复选择。')
    para(doc, 'App考勤主体通过当前登录用户的sch_worker.user_id绑定解析，不接受客户端随意指定员工。'
              '开通前必须核对登录用户与工人档案关联、租户和考勤权限；只有工号或姓名不能保证App能够打卡。')

    # ---------- 7.4 排班计划 ----------
    h2(doc, '7.4 排班计划')
    para(doc, '功能页：erp/sch/plan/index.vue及ShiftManage.vue、TeamManage.vue、TeamScheduleManage.vue；API：/erp/sch/plan。')
    para(doc, '表单维护计划编码（自动编码规则SCH_PLAN_CODE或手输）、名称、开始/结束日期、周期类型、轮班方式、发布人/时间和备注。'
              '状态编码是小写draft、published、closed，周期week/month/quarter/year；不要和APS的COMPLETED/PUBLISHED混用。')
    numbered(doc, '新增并先保存草稿，使计划拥有真实ID；已有ID后表单显示班次、班组页签，可维护计划内配置。')
    numbered(doc, '点击草稿表单“发布”，页面经updatePlan更新为published；列表已发布行可点击“排班”进入管理。'
                   '发布并不调用员工排班generate，也不代表员工明细或产能日历已经生成。')
    para(doc, '排班配置：')
    bullet(doc, '班次管理：计划内班次列表，可新增/修改/删除，点“排班”进入该班次排班')
    bullet(doc, '班组管理：选择班组加入计划（添加/移除）')
    bullet(doc, '班组排班：为班次分配班组（穿梭框：待分配/已分配）')
    para(doc, '核对模板的日期、班次、班组及休息日标记，再生成员工明细。'
              '已发布计划列表不显示普通修改；计划已有考勤引用时后端删除会阻止，避免历史考勤失去排班来源。'
              '是否关闭、如何处理已引用明细须按实际管理权限与业务制度确认，不是自动清理历史数据。')
    para(doc, '当前界面限制：TeamScheduleManage.vue穿梭分配保存只提交计划、班组、班次等，不提交scheduledDate，'
              '也没有逐日日期输入控件；SchTeamScheduleServiceImpl新增只直接保存BO，不补齐整个计划日期。'
              '员工生成依赖真实模板日期，不会按计划起止自动展开整周或整月。管理员须先核实日期完整的模板及可用维护入口，'
              '缺少日期时需要另行完善功能，不能把分配保存成功称为完整排班已配置。')

    # ---------- 7.5 员工排班 ----------
    h2(doc, '7.5 员工排班')
    para(doc, '功能页：erp/sch/employee-schedule/index.vue；API：/erp/sch/employeeSchedule。')
    para(doc, '表单维护排班计划ID、人员ID/姓名、班组ID/名称、班次ID/名称、排班日期、开始/结束时间、实际班次ID、'
              '排班状态、是否工作日及备注；多处是ID输入框，不能描述成全部带有业务选择器。状态标签由sch_schedule_status字典提供。')
    numbered(doc, '优先按已发布计划及班组模板“生成排班”。选择计划时处理该计划，不选择时处理全部已发布且日期完整的计划；'
                   'SchEmployeeScheduleServiceImpl.generate()按有效成员展开，模板休息日生成休息行，跨天班次结束时间进入次日。')
    numbered(doc, '生成前确认覆盖范围。该操作会清理被处理计划的已有员工排班再生成，不能对已有考勤引用或手工调班的数据盲目重生成。'
                   '没有模板或有效成员时可能生成0条，不能把HTTP成功或提示成功当成完整排班已生成。')
    numbered(doc, '生成后按人员、班组、班次、日期查看；核对每人每日唯一排班、夜班跨日与休息标记。'
                   '重复成员或跨班组/跨计划冲突可能被跳过，应核查生成数量及日志再确认计划覆盖完整。')

    # ---------- 7.6 假期与打卡规则 ----------
    h2(doc, '7.6 假期管理')
    para(doc, '功能页：erp/sch/holiday/index.vue；API：/erp/sch/holiday。')
    para(doc, '日历式管理：自定义月历（左右翻月），每日显示农历与节假日标记（“班”黄/“休”绿）；'
              '点日期或右键打开设置：日期、节假日名称、类型（法定/调休/公司假日）、是否调休、备注。')
    para(doc, '是否调休字段实际为isWorkingDay，用于区分该日期是否工作日；录入后应回查“班/休”显示。'
              '农历提示和节假日字典并不证明法定节假日安排已经自动从外部同步，应按企业适用日期维护。')

    h2(doc, '7.7 打卡规则')
    para(doc, '功能页：erp/sch/punch-rule/index.vue；API：/erp/sch/punchRule。')
    para(doc, '表单字段：规则名称、适用班次ID/名称、上班/下班时间、上下班允许打卡起止、地点描述、半径（米）、中心纬度/经度、'
              '允许外勤、外勤是否要求拍照、允许补卡、补卡截止天数、迟到/早退/旷工阈值、启用和备注。')
    para(doc, '现行普通签到/签退定位门禁取已启用且ruleId最小的一条规则；坐标和半径齐全时，未上报定位或超范围会拒绝打卡。'
              '无完整围栏配置时不能据成功返回宣称已校验定位；也不能把“适用班次”字段写成已实现的多规则按班次自动路由。')
    para(doc, '迟到/早退主要由员工排班及对应班次阈值判定。普通clock-in/clock-out服务并未把所有允许打卡时间窗口字段逐项用作门禁。'
              '补卡和外勤的允许、截止或照片规则由SchAppApplyServiceImpl另行校验，不应把配置字段等同于所有客户端能力已完成。')

    # ---------- 7.8 考勤 ----------
    h2(doc, '7.8 考勤记录')
    para(doc, '功能页：erp/sch/attendance/index.vue；API：/erp/sch/attendance。')
    para(doc, '搜索：人员姓名、考勤日期、考勤结果、数据来源。')
    para(doc, '考勤结果按字典展示；服务实际使用normal、late、early_leave、late_early_leave，另有请假审批回写leave。'
              '显示“旷工”字典值不证明存在自动逐日缺勤结算。')
    para(doc, 'PC表单维护人员ID/姓名、考勤日期、上班/下班打卡时间、考勤结果、数据来源和备注；'
              '迟到/早退分钟数是列表展示与打卡服务计算项，不是当前表单的独立输入控件。')
    numbered(doc, '管理员可按权限新增、维护、查看、删除或导出记录；手工维护与App签到/签退是不同入口，'
                   '不能把手工选择“设备”来源视为硬件打卡已经接入。')
    numbered(doc, '核对人员排班、记录日期及跨天班次：签到/签退匹配排班区间，夜班可归属前一天的排班日期。'
                   '同一天已有迟到再早退会合成为late_early_leave，不应在补卡时抹平已有迟到/早退事实。')

    # ---------- 7.9 移动端与App ----------
    h2(doc, '7.9 移动端考勤')
    para(doc, '后端实际入口：ruoyi-erp的sch/controller/app/AppSchAttendanceController.java，API前缀/app/sch。'
              '当前仓库可核验的是服务端接口，不把它写成所有移动客户端版本都已上线；实际App页面、权限、定位与拍照适配仍需客户端验收。')
    table(doc,
          ['功能', '真实接口与边界'],
          [
              ['今日/个人排班', 'GET /employee-schedule/today、/employee-schedule/my-schedule，只查询登录用户绑定工人'],
              ['今日打卡状态', 'GET /attendance/today-status，返回签到/签退状态及考勤结果'],
              ['签到/签退', 'POST /attendance/clock-in、/attendance/clock-out，需要sch:attendance:clock；使用服务端当前时间'],
              ['记录与月统计', 'GET /attendance/my、/attendance/recent、/attendance/monthly/{month}，按当前工人查询'],
              ['规则/定位预校验', 'GET /punch-rule/list、POST /attendance/validate-location；预校验不能替代签到服务端门禁'],
              ['补卡申请', 'POST /supplement-apply及个人列表；待审查询、/supplement-approve需要sch:appApply:approve'],
              ['请假申请', 'POST /leave-apply及个人列表；待审查询、/leave-approve需要sch:appApply:approve'],
              ['外勤打卡', 'POST /outdoor-punch及个人列表；单独登记外勤证据，不等于普通签到/签退自动放宽围栏'],
          ],
          widths=[4.2, 12.3])
    numbered(doc, '先确认当前账号已绑定工人并具有打卡权限，查询今日排班、规则及今日状态。'
                   '客户端可以上报设备ID/名称、纬度、经度、地址和定位精度，但打卡员工和时间由服务端确定。')
    numbered(doc, '定位预校验通过后仍由签到/签退重新校验围栏；后端支持经纬度距离判断，不据此承诺已适配GPS设备、WiFi识别或防定位伪造。'
                   '签到可额外上传Base64 faceImage用于异步人脸核验；当前clock-out没有对应的faceImage验证调用。')
    numbered(doc, '补卡和请假提交初始为pending，审批后approved或rejected。补卡通过补足相应打卡时间并保留既有异常判定；'
                   '请假通过按区间回写leave考勤，月统计据记录汇总。审批不是自动通过，也不是前端改状态即可生效。')
    numbered(doc, '外勤打卡按启用规则校验允许外勤及是否必须照片，独立保存外勤位置、原因和照片证据。'
                   '它不会通过普通考勤performClockIn/performClockOut自动生成同一条签到或签退记录。')
    para(doc, '未核验能力：该仓库没有据此可确认的指纹采集、IC卡读卡或WiFi身份识别客户端流程，不能列为已实现打卡方式。'
              '设备名称和数据来源字段也不是硬件适配实现的证明。')

    # ---------- 7.10 人脸打卡 ----------
    h2(doc, '7.10 人脸打卡')
    para(doc, '人脸底库功能页为cv/face/index.vue；考勤人脸复查为cv/face-review.vue，详见第13章。'
              '考勤链路使用VisionClient调用独立视觉服务，不是PC考勤页面本身采集指纹或读卡。')
    numbered(doc, '先按租户与工号规范注册员工人脸，考勤验证subject由CvSubjectHelper使用租户和工号生成。'
                   '普通显示名称或跨租户同工号不能代替对应底库身份。')
    numbered(doc, 'App签到上传faceImage后，SchAttendanceServiceImpl.asyncFaceVerify()在cvExecutor中存证并调用1:1验证，'
                   '按视觉服务isMatched结果写PASS或FAIL并记录相似度。它是签到后的异步校验，不阻断正常业务打卡。')
    bullet(doc, '当前考勤没有先1:N识别再1:1确认的流程，且代码不固定50%阈值；不要把其他视觉识别接口拼成已实现的考勤操作。')
    bullet(doc, '无照片、视觉服务关闭/不可用或调用失败时写NONE；NONE不是PASS，也不表示人脸已通过。'
                '需要核查OSS存证、视觉服务配置、告警及后续复查。')
    bullet(doc, 'FaceVerifyBackfillTask在视觉服务开启且可用时每小时17分扫描24小时内的NONE；每轮最多200条，尝试上限3次，'
                '有可读取存证照片才补跑。补跑仍为NONE不刷新入队时间，超窗、无照片或达上限不能保证自动恢复。')
    numbered(doc, '管理员在cv/face-review.vue筛选FAIL，核对存证照片与排班证据后选择误判改PASS或代打卡确认FAIL，'
                   '填写复核依据；接口需要sch:attendance:review并追加审计备注，不自动修改考勤出勤结果或工资。')
    para(doc, '复查页提示24小时内处理，是页面提示与业务建议；当前markFaceVerified()没有强制24小时拒绝条件。'
              '严格24小时窗口属于NONE补跑，不应误写为FAIL人工复查的后端截止期限。'
              '本地源码核对不保证视觉服务、定时任务、App拍照与生产现场已经联通。')
