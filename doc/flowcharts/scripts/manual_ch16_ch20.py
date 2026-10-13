#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第16章 财务核心 + 第17章 支付管理 + 第18章 工作流引擎
+ 第19章 报表管理 + 第20章 系统管理 + 附录
供 gen_manual_v3.py 导入调用；正文按 2026-10-12 plus 分支源码核对。
"""


def ch16_fin(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第16章 财务核心模块')
    para(doc, '财务核心模块覆盖总账、应收应付、固定资产、存货成本与期末关账。'
              '发票和收付款通过事件/MQ异步制单，成本、折旧及关账使用各自过账入口；'
              '不能将业务单据审批、收付款确认或生产完工等同于全部财务过账完成。'
              '建议先配置科目/期间/汇率，再处理凭证/收付款/折旧，核对异步凭证后执行期末关账。')
    para(doc, '本章核对日期为2026-10-12，基线为plus分支fe59e4ae。以下菜单层级仅作业务定位；'
              '页面文件均相对plus-ui/src/views，实际菜单名称、浏览器路由和可见按钮由sys_menu、'
              '租户套餐、角色权限及运行环境共同决定，页面存在不代表生产已开通。')

    h2(doc, '16.1 会计科目管理')
    para(doc, '菜单定位：财务核心 > 总账 > 会计科目；页面：erp/fin/gl/account/index.vue。')
    para(doc, '科目采用树形结构维护，分为汇总科目与末级科目。只有末级科目（"末级"标志为"是"）'
              '才能被凭证分录引用。')
    para(doc, '表单字段：')
    table(doc,
          ['字段', '必填', '说明'],
          [
              ['上级科目', '否', '树形选择，顶级科目留空'],
              ['科目编码', '是', '如 1001（库存现金）'],
              ['科目名称', '是', '科目中文名称'],
              ['科目类型', '是', '由gl_account_type字典显示；损益结转实际识别INCOME收入、EXPENSE费用'],
              ['余额方向', '是', '借方DEBIT/贷方CREDIT'],
              ['是否末级', '是', '末级=可过账；非末级仅作汇总'],
              ['统驭科目', '否', '开启后选统驭类型：应收AR/应付AP/固定资产FA'],
              ['排序', '否', '显示顺序'],
          ],
          widths=[3.0, 1.2, 12.0])
    para(doc, '新增下级时父科目自动改为非末级；删除要求末级、无子科目、无凭证引用且无余额记录。'
              '当前修改接口校验编码唯一和父子循环，并未实现“使用后禁止改编码”的硬限制；'
              '已使用科目不宜改码，尤其自动制单按固定编码查科目，调整前必须核对相关映射。')

    h2(doc, '16.2 记账凭证')
    para(doc, '菜单定位：财务核心 > 总账 > 记账凭证；页面：erp/fin/gl/voucher/index.vue。')
    para(doc, '功能说明：凭证是总账的核心单据，支持手工录入、过账、反过账与作废。'
              '发票开票/收票事件、收付款确认消息可触发自动制单；折旧和成本需执行相应操作。'
              '自动制单仍校验科目、借贷平衡和期间开放状态，异步业务需另查凭证是否真正POSTED。')
    para(doc, '凭证状态流转：')
    table(doc,
          ['状态', '说明', '可执行操作'],
          [
              ['DRAFT 草稿', '新建凭证，可修改删除', '修改、删除、过账、作废'],
              ['POSTED 已过账', '已更新科目余额', 'OPEN期间可反过账/作废；查看'],
              ['CANCELLED 已作废', '作废终态', '查看'],
          ],
          widths=[3.5, 5.0, 7.5])
    para(doc, '操作步骤：')
    numbered(doc, '点击“新增”，填凭证字（默认J）、凭证日期（默认今天）、附件张数')
    numbered(doc, '添加分录行：选末级科目（自动带出科目编码/名称）、填摘要、填借方或贷方金额（借贷互斥）')
    numbered(doc, '检查底部借贷平衡栏：差额为0显示“平衡”标签，不平衡则无法保存（提示差额金额）')
    numbered(doc, '保存为DRAFT；点击“过账”时按凭证日期派生或查找期间，仅OPEN可过账并更新余额')
    numbered(doc, 'OPEN期间可将POSTED反过账为DRAFT，或作废并回冲余额；CLOSED须先反结账，LOCKED不可直接重开')
    para(doc, '注意事项：至少一行有效分录；借贷不能同时有值、不能全零，手工分录不能为负，'
              '服务端要求借贷合计严格相等。红字业务由业务Poster生成负数分录。'
              '手工新增强制MANUAL来源，不能伪造发票/成本来源；反过账或作废凭证不自动撤销业务单据及核销记录。')

    h2(doc, '16.3 会计期间')
    para(doc, '菜单定位：财务核心 > 总账 > 会计期间；页面：erp/fin/gl/period/index.vue。')
    numbered(doc, '工具栏“生成年度期间”：输入会计年度（页面范围2020-2099），生成12个OPEN期间，已存在月份跳过')
    numbered(doc, '期间状态：OPEN开放→关账→CLOSED已关账→锁定→LOCKED已锁定')
    numbered(doc, 'CLOSED可“反结账”回到OPEN')
    para(doc, '直接期间关账只允许OPEN且会检查关联草稿凭证，不等于执行月结全部步骤。'
              'CLOSED/LOCKED均禁止过账、反过账和作废已过账凭证；只有CLOSED提供反结账，'
              'LOCKED无解锁入口。凭证按日期查不到期间时可自动建立OPEN月份，应事先核对年度期间配置。')

    h2(doc, '16.4 汇率管理')
    para(doc, '菜单定位：财务核心 > 总账 > 汇率维护；页面：erp/fin/gl/currencyRate/index.vue。')
    para(doc, '字段：币种*、本位币（默认CNY）、汇率日期*、汇率*（6位精度>0）、类型（中间价/买入价/卖出价）。')

    h2(doc, '16.5 试算平衡表')
    para(doc, '菜单定位：财务核心 > 总账 > 试算平衡表；页面：erp/fin/gl/report/trialBalance.vue。')
    para(doc, '按会计期间输出各科目期初/本期/期末借贷方余额，自动校验平衡'
              '（页面提示“试算平衡:本期借方合计 = 贷方合计”，前端差<0.005视为平衡）。'
              '这不是月结校验：月结在服务端检查期末借贷净额严格相等，并检查关联草稿凭证。')

    h2(doc, '16.6 应收应付（AR/AP）')
    para(doc, '菜单定位：财务核心 > 收付款 > 收付款单 / 核销管理；页面：'
              'erp/fin/receipt/index.vue、erp/fin/settlement/index.vue；'
              '账龄及余额查看erp/fin/arap/report/index.vue。')
    para(doc, '应收/应付发票仍在销售/采购发票页面维护（erp/so/invoice/index.vue、'
              'erp/pu/invoice/index.vue）。销售发票通过审批回调首次转ISSUED时发布SoInvoiceIssuedEvent；'
              '采购发票“收票”由DRAFT转ISSUED并发布PuInvoiceIssuedEvent。'
              'GlPostingEventDispatcher在提交后异步发送消息，GlPostingConsumer路由至对应Poster制单。'
              '当前基线没有独立postToGl接口或页面按钮，不应把“保存草稿”写成过账。')
    table(doc,
          ['来源', '当前自动制单口径', '边界'],
          [
              ['销售发票SO_INVOICE', '借1122应收；贷6001收入及2221.01销项税', '开票事件异步制单，保存草稿不触发'],
              ['采购发票PU_INVOICE', '借1405库存商品及2221.02进项税；贷2202应付', '收票触发；认证/抵扣子状态不重复过账'],
              ['收付款RECEIPT', 'AR：借1002/贷1122；AP：借2202/贷1002', '确认发消息，渠道类型不自动切换银行/现金科目'],
          ],
          widths=[3.5, 7.0, 5.5])
    para(doc, '收付款单状态流转：')
    table(doc,
          ['状态', '说明', '可执行操作'],
          [
              ['DRAFT 草稿', '新建收付款单', '修改、删除、确认、作废'],
              ['CONFIRMED 已确认', '已发送GL消息，可尚未制单或仅部分核销', '核销剩余金额、查看'],
              ['SETTLED 已结算', '收付款金额已全部被核销', '查看；可经核销记录反核销'],
              ['CANCELLED 已作废', '作废终态', '查看'],
          ],
          widths=[3.5, 5.0, 7.5])
    para(doc, '收付款单字段：方向*（应收AR显示客户/应付AP显示供应商）、收付款类型（现金/银行/支付宝/微信/其他）、'
              '客户/供应商、付款方、金额*（精度2）、日期、备注。')
    para(doc, '核销操作步骤：')
    numbered(doc, '进入“核销管理”，点击“核销”')
    numbered(doc, '选择方向（AR/AP），系统加载未清发票与未清收付款单')
    numbered(doc, '分别选择发票（“未清 xx”下拉）与已确认收付款单，并核对方向和客户/供应商；下拉并非按同一往来自动配对')
    numbered(doc, '填写大于0且不超过双方未清余额的核销金额（2位小数），点击确认；采购发票关联PO行时还会执行三单匹配校验')
    numbered(doc, '核销累计发票verifiedAmount、收付款settledAmount；部分核销仍分别保持ISSUED、CONFIRMED，只有各自达额才变VERIFIED、SETTLED')
    numbered(doc, '全额时标记对应总账统驭分录已清；核销本身不生成“904清账凭证”。查看发票、收付款和总账，分别核实结果')
    numbered(doc, '需要撤销时在核销记录行点击“反核销”（需fin:arap:settlement:unsettle权限）：冲回双方累计额、恢复未清标志并删除该核销记录')
    para(doc, '手工新增只能为DRAFT/MANUAL且已结算额为0，确认才发送GL消息；已确认收付款不能直接修改、删除或作废。'
              '反核销不会自动反过账收付款凭证；发票页面的单独“核销”只处理发票累计额，'
              '不能代替本节同时关联发票与收付款单的财务核销。核销和异步制单分别执行，'
              '不能凭核销成功认定总账未清标志已经同步。')

    h2(doc, '16.7 固定资产')
    para(doc, '菜单定位：财务核心 > 固定资产 > 资产卡片 / 折旧计提 / 资产处置；页面：'
              'erp/fin/fa/asset.vue、depreciation.vue、disposal.vue；类别配置为erp/fin/fa/category.vue。')
    para(doc, '资产卡片字段：')
    table(doc,
          ['字段', '必填', '说明'],
          [
              ['资产名称', '是', '资产中文名称'],
              ['资产类别', '是', '下拉显示“类别(折旧方法)”，选择后自动带出使用年限与净残值'],
              ['原值', '是', '资产购入原值'],
              ['净残值', '否', '留空=原值×类别残值率'],
              ['使用年限(月)', '是', '1-600个月'],
              ['折旧方法', '否', '直线法/双倍余额递减法/年数总和法/手工，留空随类别'],
              ['购置日期', '是', '购置日期'],
              ['启用日期', '否', '留空=购置日'],
              ['状态', '否', '使用中/在建'],
          ],
          widths=[3.0, 1.2, 12.0])
    para(doc, '资产状态流转：CWIP在建→（转固）→IN_USE使用中→（处置）→DISPOSED已处置。')
    para(doc, '折旧计提：选OPEN会计期间→“计划预览”核对→“计提并过账”（按费用科目汇总借方/贷累计折旧1602）。'
              '已完成的资产/期间记录跳过；MANUAL手工折旧资产不参加自动计划。计提后不允许普通修改或删除资产卡片。')
    para(doc, '资产处置：选使用中资产（显示净值）→处置类型（出售/报废）→处置日期/收入（报废可0）→自动过账计算损益。')
    para(doc, '处置日期须已有OPEN期间；处置凭证来源为FA_DISPOSAL，与DEPRECIATION分开。'
              '转固只是卡片由CWIP改为IN_USE及设置启用日期，不应写成自动生成在建转固总账凭证。')

    h2(doc, '16.8 存货成本')
    para(doc, '菜单定位：财务核心 > 成本 > 成本过账；页面：erp/fin/cost/index.vue。'
              '列表为库存流水只读查询，按行“过账”调用/erp/fin/cost/postTx/{txId}；'
              '不是全部库存业务落库后即自动完成财务过账。')
    para(doc, 'CostPoster.postTx实际按事务类型和物料类型映射。金额=流水unitCost×txQuantity，'
              '四舍五入2位，缺单位成本或金额为0则跳过；期日优先erpDate，其次txDate。过账口径：')
    table(doc,
          ['事务类型', '过账分录'],
          [
              ['RECEIVE_IN 采购入库', '借:存货 / 贷:2202应付暂估'],
              ['PRODUCTION_ISSUE 生产领料', '借:5001生产成本 / 贷:存货'],
              ['SALES_OUT 销售出库', '借:6401主营业务成本 / 贷:存货'],
              ['MISC_IN 杂项入库', '借:存货 / 贷:2202（并非可配置对应科目）'],
              ['MISC_OUT 杂项出库', '借:6401 / 贷:存货'],
              ['PRODUCTION_IN / TARGET_IN', '按入库分支：借存货/贷2202；并非生产成本自动结转'],
              ['VIRTUAL_OUT / TRANSFER_IN / TRANSFER_OUT', '虚拟转储/内部调拨跳过成本制单'],
              ['其他事务类型', '当前落出库默认分支：借6401/贷存货；须核对退货等特殊业务，不能泛化为正确成本规则'],
          ],
          widths=[6.0, 10.0])
    para(doc, '存货科目：物料类型1原材料/4辅料走1403，类型3成品走1405，其余走1406。'
              '同流水在事务内加锁，只有返回POSTED凭证并成功回写posted=1才认定完成；重复已过账流水跳过。')
    para(doc, '工具栏“期间暂估”默认传当前年月，CostService.provision汇总借存货/贷2202，'
              '单价取物料参考价、其次标准成本，无价格按0。现控制器仅筛SHELVED/FINISHED状态，'
              '未按传入月份过滤收货日期，且发票关联排除使用收货单ID近似查询grLineId，须人工核对漏排和重复暂估风险。'
              '该入口按年月认领同一COST来源凭证，不是逐流水补过账，也不能保证与入库成本、采购发票自动冲销对账完成。')

    h2(doc, '16.9 财务报表与期末关账')
    para(doc, '菜单定位：财务核心 > 报表 / 财务核心 > 关账；页面：'
              'erp/fin/report/balance.vue、income.vue、cashFlow.vue、itemConfig.vue，'
              '以及erp/fin/closing/monthly.vue、yearly.vue。')
    bullet(doc, '资产负债表：按报表项目科目映射读取科目余额，页面核对资产总计与负债及权益总计')
    bullet(doc, '利润表：本期/本年累计两种口径')
    bullet(doc, '现金流量表：科目发生额映射的直接法基础版，非逐笔现金/银行交易追溯；应检查项目配置、科目范围及方向')
    para(doc, '月结向导四步：')
    numbered(doc, '① 试算校验trialCheck：无关联草稿凭证，且各科目期初+本期发生的期末借贷净额严格相等')
    numbered(doc, '② 损益结转carryPnl：INCOME/EXPENSE科目净额结转到4103本年利润；无损益余额时记录跳过，不生成凭证')
    numbered(doc, '③ 外币重估revaluate：仅聚合本期POSTED凭证中非CNY分录，按科目+币种汇总本币净额与原币绝对额的借正贷负净额；以期末或此前最近汇率计算差额，对方科目为6603.02')
    numbered(doc, '④ 关账：当前页面该按钮与“执行月结”均调用monthlyClosing，重新执行试算→损益结转→外币重估→期间关账→下期期初结转，不是只执行最后一步')
    para(doc, '重估缺汇率会记录跳过该币种，无差额时不制单；已有期间重估凭证则复用。'
              '当前不读取外币期初余额，也未按货币性科目筛选，汇率查询未限定本位币和汇率类型；'
              '发票/收付款Poster未透传币种及原币金额，不能仅填写外币汇率就宣称这些业务已完整参与重估。'
              '整体月结先结转损益再重估，不能假定新生成的汇兑损益也被前一步再次结转。')
    para(doc, '月结主流程在一个ERP事务中执行，日志通过独立事务保留，状态为SUCCESS/FAILED；'
              'SUCCESS也可能表示“跳过”或“复用”，失败后的旧成功日志不等于业务数据已提交。'
              '结转下期期初会跳过目标期已有非零期初的科目，反结账后变更并非自动重算完整结转。'
              '月结不自动完成库存计价、暂估、折旧、AR/AP核销、MQ积压检查或报表快照，应事先逐项核对。')
    para(doc, '年结需12月期间：必要时先月结，将4103转4104未分配利润（凭证日期落次年1月），'
              '结转期初并锁定12月。应核查全年各期、结转凭证和次年期初；执行成功不代表财务人工签核完成。')

    h2(doc, '16.10 模拟支付入口')
    para(doc, '菜单定位：财务核心 > 模拟支付；页面：erp/fin/pay/mock.vue。'
              '后端/erp/fin/pay/mock/callback仅在dev/test/local Profile注册，需fin:pay:callback权限；'
              '生产Profile不加载该入口，不能用模拟收款证明真实资金到账。')
    para(doc, '模拟消息仅验证AR收款与FIFO核销处理，不调用真实渠道、不执行AP付款：')
    numbered(doc, '选择客户（自动加载其未核销发票列表）')
    numbered(doc, '填写收款金额、支付渠道（支付宝/微信/其他）；支付单号留空自动生成')
    numbered(doc, '点击“发送收款回调”：发送PayReceivedMessage；消费者建PAY_CALLBACK/CONFIRMED的AR收款单、发送异步GL消息，再按客户ISSUED发票日期FIFO核销')
    numbered(doc, '页面约1.5秒后刷新只是定时查询，不是完成承诺；分别核查收款单、核销明细与RECEIPT凭证')
    para(doc, '缺clientId或非正金额时消费者跳过；同支付单号用于防重。超出可核销发票的金额保留在收款单，'
              '某张发票核销失败即停止本轮，不保证整笔回调全额核销。MQ消费、财务制单及核销不是一个跨模块原子事务，'
              '重发同号会被已有收款单拦截，不能把重发当作失败核销的可靠补偿。')


def ch17_pay(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第17章 支付管理')
    para(doc, '支付管理模块提供支付链接/二维码、订单状态查询、退款及支付发票记录管理；'
              '后端通道实现包括支付宝、微信和收钱吧，是否可用取决于商户配置、证书、回调网络及渠道环境。'
              '下述页面路径相对plus-ui/src/views，实际菜单和操作权限须以目标租户运行配置为准。')

    h2(doc, '17.1 统一支付')
    para(doc, '菜单定位：支付管理 > 支付测试；页面：pay/paytest/index.vue；API：/pay/order、/pay/{channel}/url/{orderNumber}。')
    para(doc, '联调：填订单内容/金额/支付方式→“创建订单并获取支付链接”→'
              'https或alipay链接显示二维码，返回<form>时展示网页支付。'
              '页面每5秒轮询订单状态，约30次后停止，也可手动查询；停止轮询不代表渠道交易关闭。'
              '沙箱APP只适用于已配置相应沙箱的通道，页面本身不保证处于沙箱，不应在真实渠道随意测试资金。')

    h2(doc, '17.2 支付订单')
    para(doc, '菜单定位：支付管理 > 订单；页面：pay/order/index.vue；支付发票页面：pay/invoice/index.vue。')
    para(doc, '列：商户订单号、第三方订单号、订单状态（未支付/已支付/已关闭/已退款）、'
              '总金额/实际支付、订单内容、支付方式、支付时间、支付人。')
    para(doc, '发票管理：订单号*、发票类型*、发票抬头*、纳税人识别号、收票人手机/邮箱、发票备注。')
    para(doc, '支付发票为pay_invoice记录维护，不是ERP销售/采购发票、税务开票认证或总账过账入口。'
              '订单查询和退款分别受pay:order:query、pay:order:refund等权限约束；退款仅允许“已支付”，'
              '“已退款”为终态。发起渠道退款不自动反核销ERP收款，也不等于供应商应付付款。')

    h2(doc, '17.3 收款联动')
    para(doc, '真实回调入口为/pay/{channel}/notify及其带订单号/类型的变体。'
              'AliPayServiceImpl.notify在订单查询、验签、成功状态与金额校验处理后更新订单并发送PayReceivedMessage；'
              'SQBServiceImpl的支付成功回调也调用sendPayReceived。微信WxPayServiceImpl.notify当前更新支付/退款订单，'
              '未发送这类ERP收款消息，不能把所有通道写成同一自动核销链。')
    para(doc, 'ERP的PayReceivedConsumer只建AR收款单，并对该客户ISSUED销售发票FIFO核销；'
              '消息须有clientId及正金额，测试页面未填写客户关联，只有支付成功状态并不能保证生成ERP收款。'
              '消费者不按bizType/bizId定向核销某张业务单据，也不创建AP付款或自动核销采购应付。'
              '退款、查询状态更新和回调收款要分别核对；财务模拟入口仅验证消息后的AR链路（见16.10），'
              '不验证渠道验签、真实到账、退款会计处理或生产支付部署。')


def ch18_workflow(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第18章 工作流引擎')
    para(doc, '本章指ruoyi-flowable的Flowable审批引擎（API /workflow/**），'
              '不是ruoyi-flow的节点式编排画布（/ai/flow/**）。销售、采购、生产、仓储等单据按各自服务接入审批回调，'
              '不能用固定“11类”概括当前范围，也不能把所有状态机入口视为Flowable审批。')
    para(doc, '以下页面均相对plus-ui/src/views；菜单由部署环境和权限动态提供，模型存在、已部署、'
              '业务表单绑定、发起权限和当前任务办理权限是不同条件。')

    h2(doc, '18.1 流程设计')
    para(doc, '菜单定位：流程管理 > 流程模型 / 流程部署 / 流程分类；页面：'
              'workflow/model/index.vue、workflow/deploy/index.vue、workflow/category/index.vue。')
    para(doc, '流程模型：模型标识/名称自动生成（Process_/业务流程_+时间戳）；'
              '“设计”打开BPMN设计器（拖拽式：开始/结束/用户任务/服务任务/排他网关/子流程），'
              '保存时可选“保存为新版本”或“覆盖当前版本”。')
    para(doc, '流程部署：“部署”后到部署列表；版本管理：同流程多版本可挂起/激活。')
    para(doc, '流程分类：分类名称/编码/应用类型（OA/自定义业务流程/钉钉）；'
              '注意：非“自定义业务流程”应用类型不能绑定业务表单。')
    para(doc, '自定义业务表单（workflow/custom-form/index.vue）可选数据表、预览表结构并设计字段、主子表关联；'
              '字段字典推断结果仍须核对实际字典，不能把表结构自动识别当作业务校验已经配置。'
              '动态表单另见workflow/nform/index.vue，绑定流程后再检查部署版本使用的表单。')

    h2(doc, '18.2 提交审批')
    para(doc, '支持Flowable的业务单据先保存，在允许提交的草稿/新建状态选择有效审批流程，再调用该单据提交入口；'
              '业务服务校验状态并以serviceName关联业务回调，审批状态与执行/入出库/过账状态不能混为一谈。'
              '可信来源采购申请/订单使用专用认证生命周期，不应强行走旧通用提交或编辑接口。')
    para(doc, '发起流程（workflow/work/start.vue、initiate.vue）：选已部署且可发起流程，'
              '按绑定的OA/自定义业务表单填写保存后发起；未绑定正确表单或挂起的定义不能作为正常发起路径。')

    h2(doc, '18.3 审批处理')
    para(doc, '菜单定位：工作流 > 待办 / 已办 / 我发起的 / 可认领 / 抄送；页面分别为'
              'workflow/work/todo.vue、finished.vue、own.vue、claim.vue、copy.vue；详情为workflow/work/detail.vue。')
    para(doc, '审批操作（详情页任务办理页签）：')
    table(doc,
          ['操作', '说明'],
          [
              ['通过', '校验表单后完成当前任务'],
              ['驳回', '按模型及可回退节点回到上一节点或发起人，并非任意固定上一级'],
              ['退回', '选择可退回节点'],
              ['拒绝', '流程终止'],
              ['委派/转办', '选用户转交处理'],
              ['加签', '前加签/后加签/多实例加签（用户多选）'],
              ['跳转', '选择目标节点跳转'],
              ['收回/撤回', '已办任务收回/我发起的撤回，受任务历史、流程状态与权限限制'],
          ],
          widths=[4.0, 12.5])
    para(doc, '详情包含任务办理、表单信息、流转记录（时间线+评论分类）、流程跟踪（BPMN高亮当前节点）；'
              '任务办理仅在当前可办理时显示，表单页签也由表单类型和数据决定，不保证始终有四个可操作页签。')
    para(doc, '页面审批意见必填；抄送人/指定审批人通过部门树+用户列表选择。'
              '委派不等于转办，候选任务须先认领；加签、跳转、退回等操作还取决于节点设置及服务端权限/参与者检查。')

    h2(doc, '18.4 ERP审批流集成')
    para(doc, '当前ERP源码可见afterFlowHandle回调的业务包括（不代表目标环境均已配置可用流程）：')
    bullet(doc, '销售订单 / 销售退货 / 销售合同 / 销售发票')
    bullet(doc, '采购申请 / 采购订单 / 采购合同 / 采购退货')
    bullet(doc, '生产工单')
    bullet(doc, '物料入库 / 销售出库 / 调拨单 / 盘点计划 / 到货通知')
    para(doc, '业务表单上可嵌入流程按钮组件：发起审批（ActApplyBtn，支持重新发起/继续老流程）、'
              '处理按钮（ActHandleBtn：通过/驳回/退回/委派）。')
    para(doc, '采购发票收票、收付款确认、成本制单及认证采购生命周期不是上述通用Flowable回调。'
              '生产工单审批通过只更新审批状态并可下达来源计划，正式release才发送MES工单消息；'
              '入库审批通过只是待上架，销售出库审批通过只是允许后续检验/执行，均不代表库存与总账已全部完成。')


def ch19_report(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第19章 报表管理')
    para(doc, '报表模块基于JimuReport（积木报表），以iframe方式集成仪表盘与设计器，'
              '与16.9财务报表的科目余额计算入口不同。页面依赖报表服务地址、独立数据源和登录鉴权；'
              '前端壳页面存在不代表仪表盘、数据集或生产服务已经配置完成。')

    h2(doc, '19.1 报表仪表盘')
    para(doc, '菜单定位：报表管理 > 仪表盘；页面：plus-ui/src/views/report/dashboard/index.vue。')
    para(doc, '页面读取VITE_APP_JMUREPORT_DASHBOARD_URL并附登录token加载iframe；'
              '展示内容取决于报表端已保存的仪表盘与数据集。空白或无权限时先核对配置地址、登录态、'
              '报表服务可达性及目标数据源，不能凭静态菜单判断业务报表已可用。')

    h2(doc, '19.2 报表设计器')
    para(doc, '菜单定位：报表管理 > 设计器；页面：plus-ui/src/views/report/design/index.vue，'
              '从VITE_APP_JMUREPORT_DESIGN_URL加载并携带登录token。')
    para(doc, '设计操作由所部署JimuReport界面提供，本仓库前端只提供嵌入入口：')
    bullet(doc, '数据源/SQL分析：由报表端维护；修改连接或SQL前须确认目标库、租户过滤、只读账号与业务数据授权')
    bullet(doc, '图表和数据集：可配置的组件以实际部署版本为准，不能由iframe入口推定所有图表或模板已具备')
    bullet(doc, '分享与定时推送：当前Vue入口和Token桥接未提供独立操作实现，不作为本手册承诺的已上线功能')
    bullet(doc, '权限：JimuReportTokenService沿用Sa-Token登录用户、租户、角色和菜单权限；已认证用户追加查看取数权限，设计/数据源高危权限仅向超级管理员或superadmin/admin角色追加')
    para(doc, '不要在截图、日志或分享链接中暴露带token的iframe地址。Token桥接不等于任意SQL自动按租户隔离；'
              '具体数据集过滤、查看/编辑/导出规则仍需在报表服务和实际权限配置中核验。')


def ch20_system(doc, h1, h2, h3, para, bullet, numbered, table):
    h1(doc, '第20章 系统管理')
    para(doc, '系统管理模块提供用户、角色、菜单、组织、字典与租户等配置。'
              '本章页面路径均相对plus-ui/src/views，菜单定位不是生产URL保证；'
              '/system/menu/getRouters由后端按登录用户生成路由，前端permission状态库映射组件。'
              '租户套餐、角色菜单权限、按钮权限、数据权限及运行开关需分别核对，不能只凭页面可见推定接口有权或数据全部可见。')

    h2(doc, '20.1 用户管理')
    para(doc, '菜单定位：系统管理 > 用户管理；页面：system/user/index.vue。')
    para(doc, '左侧部门树联动筛选；列表行内状态开关直接启停用户。')
    para(doc, '表单字段：用户昵称*、归属部门（树）、手机号码、邮箱、用户名称*（仅新增）、用户密码*（仅新增）、性别、状态、岗位（多选）、角色（多选）。')
    para(doc, '操作：新增/修改/删除/重置密码/分配角色/导入导出（Excel模板下载）。')
    para(doc, '新增与重置密码涉及加密请求及敏感数据，不在手册中记录真实密码、Token或导出用户资料。'
              '用户停用、强退与角色分配是不同操作，具体执行受system:user:*权限及管理员保护约束。')

    h2(doc, '20.2 角色管理')
    para(doc, '菜单定位：系统管理 > 角色管理；页面：system/role/index.vue，分配用户为authUser.vue。')
    para(doc, '表单字段：角色名称*、权限字符*、角色顺序*、状态、菜单权限树（父子联动开关）、备注。')
    para(doc, '数据权限弹窗：权限范围（1全部/2自定义/3本部门/4本部门及以下/5仅本人/6部门及以下或本人）+部门树勾选。')
    para(doc, '操作：修改/删除/数据权限/分配用户。')
    para(doc, '菜单权限控制功能入口，数据权限由后端对应查询的权限注解/拦截执行；'
              '配置角色数据范围不代表所有自定义SQL或所有跨模块接口自动获得同样过滤。')

    h2(doc, '20.3 菜单管理')
    para(doc, '菜单定位：系统管理 > 菜单管理；页面：system/menu/index.vue。')
    para(doc, '树懒加载；表单字段：上级菜单、菜单类型（目录/菜单/按钮）、图标、名称、排序、是否外链、路由地址、组件路径、权限标识、是否缓存、显示状态、菜单状态。')
    para(doc, '工具栏“级联删除”批量删除多级菜单；新增、修改和删除接口同时要求超级管理员角色及相应system:menu:*权限。'
              '普通删除会检查子菜单及角色关联；生产变更菜单需核对受影响角色和租户套餐，不应为显示页面盲目删除或重建菜单。'
              '组件路径是src/views下的文件映射（通常不含.vue），路由地址是另一配置项，两者不能互当生产访问地址。')

    h2(doc, '20.4 部门管理')
    para(doc, '菜单定位：系统管理 > 部门管理；页面：system/dept/index.vue。')
    para(doc, '树形表格；字段：上级部门、部门名称*、部门类别*、显示排序*、负责人（本部门用户下拉）、联系电话（11位手机号校验）、邮箱、部门状态。')

    h2(doc, '20.5 字典管理')
    para(doc, '菜单定位：系统管理 > 字典管理；页面：system/dict/index.vue。')
    para(doc, '左右分栏：左字典类型（名称/类型/备注），右该类型的字典数据（标签/键值/排序/回显样式cssClass）。')
    para(doc, '工具栏“刷新缓存”清理服务端字典缓存，已打开页面的前端字典状态可能仍需重新加载。'
              '字典键值参与业务状态显示，不应仅为修改标签而随意更换状态值。')

    h2(doc, '20.6 租户管理')
    para(doc, '菜单定位：系统管理 > 租户管理；页面：system/tenant/index.vue；套餐页面：system/tenant-package/index.vue。')
    para(doc, '表单字段：企业名称*、联系人*、联系电话*、用户名/密码（仅新增，自动创建租户管理员）、租户套餐、过期时间、用户数量、绑定域名、企业代码、企业简介。')
    para(doc, '工具栏：同步租户字典/同步租户参数；行内同步套餐。租户控制器仅在tenant.enable=true时注册，'
              '列表、增删改、动态租户切换及同步均要求超级管理员角色，套餐同步还要求system:tenant:edit权限。'
              '租户管理员不等于平台超级管理员；同步是实际写操作，不能作为查看手册或演示准备自动执行。')

    h2(doc, '20.7 系统监控')
    para(doc, '菜单定位：系统监控；页面：monitor/online/index.vue、operlog/index.vue、logininfor/index.vue、'
              'admin/index.vue、snailjob/index.vue；实际可见范围仍取决于菜单权限与服务配置。')
    bullet(doc, '在线用户：登录会话管理，支持强退')
    bullet(doc, '操作日志：操作记录/请求参数/结果/异常详情查看，支持清空')
    bullet(doc, '登录日志：登录成功/失败记录，支持解锁账户')
    bullet(doc, '服务监控：Spring Boot Admin')
    bullet(doc, '任务调度：SnailJob控制台')
    para(doc, 'Spring Boot Admin与SnailJob是独立服务入口，页面存在不说明服务已启动或定时任务正在运行；'
              '本手册不自动启停服务。日志可能包含业务和请求数据，查看、导出、清空及解锁操作须按授权执行，'
              '不得把真实凭证、个人信息或完整敏感配置复制到文档。')


def appendix(doc, h1, h2, h3, para, bullet, numbered, table):
    doc.add_page_break()
    h1(doc, '附录A 典型业务流程')
    para(doc, '以下为2026-10-12当前源码可核对的操作与联动，不是历史演示“完整闭环”的延续保证。'
              '各箭头包含人工操作、状态校验或异步消息，须核对来源单据、租户、权限、单位、数量、'
              '检验结果和消息处理结果；不表示同一事务自动完成，也不表示生产部署或人工验收已完成。')

    h2(doc, 'A.1 销售生产全链路')
    numbered(doc, '客户/报价/合同按相应页面维护，关联销售订单；不假定询价、报价和合同必然逐级自动转单')
    numbered(doc, '销售订单关联生产计划，按真实BOM、物料单位和需求来源执行物料需求运算，检查持久化需求与已占用供应')
    numbered(doc, '缺料采购使用可信来源创建申请及认证生命周期转单；实际到货、IQC和收货入库按A.2分别办理')
    numbered(doc, 'ERP生产工单正式release发送MES消息，消费者建立工单与路线/MBOM快照，执行流转卡、派工、报工与关键工序检验')
    numbered(doc, '工单完工消息触发ERP完工及倒冲/成品入库链路；正常领料未过账不被完工自动补齐。OQC由销售出库审批链触发，不是所有成品入库必做的统一自动步骤')
    numbered(doc, '销售出库审批、需检物料OQC合格后执行发货；销售发票开票事件异步GL制单，再登记/确认AR收款并核销或处理合格渠道回调')

    h2(doc, 'A.2 采购流程')
    numbered(doc, '采购申请页“可信来源创建”：关联真实MRP/受支持低库存来源并由服务端认证；旧低库存一键生成已暂停，普通BO直接新增申请或订单会被拒绝')
    numbered(doc, '可信来源单据走认证申请生命周期的批准/转单，再由认证订单入口录入真实供应条款、不同操作人复核、按版本及报价批准；这些步骤不自动创建到货或收货')
    numbered(doc, '认证订单通过专用到货/收货入口核对原订单行和本次真实收货批次；旧非受保护订单保留合法收货联动，不能用旧入口绕过来源占用和认证守卫')
    numbered(doc, '普通收货审批通过为APPROVED待上架；有到货通知且iqcCheck=1的行才投递IQC创建消息，合格结果回写后确认上架，再执行入库；审批、上架和实际库存写入是不同步骤')
    numbered(doc, '采购进项发票登记/从订单生成草稿 > 收票ISSUED并异步GL制单 > 关联PO行三单匹配 > 按适用条件认证/抵扣 > AP付款单确认 > 财务核销；支付收款回调不替代AP付款')
    para(doc, '可信来源采购还受永久来源账、版本、库存/收货/退货/发票和占用事件守卫约束。'
              '不能为完成示例强行改数量、单位、tenantId、verified字段或将来源不明历史单据冒充已认证。')

    h2(doc, 'A.3 生产流程')
    numbered(doc, '生产计划 > 按BOM和单位换算计算物料需求 > 检查真实库存/采购/工单占用；计算缺口不代表自动补齐采购或可开工')
    numbered(doc, 'ERP工单CREATED（可按配置提交审批转APPROVED）> 正式下达RELEASED > 开始IN_PROGRESS；release才发送MES工单消息，审批通过不等于MES快照已生成')
    numbered(doc, '下达生成正常领料单（排除倒冲物料），核验仓库、数量和单位后执行领料；派工、生产与报工按MES状态和检验门禁处理')
    numbered(doc, '关键工序IPQC合格 > MES工单完工 > MQ回写ERP工单 > 生产完工消息消费，按倒冲配置扣料并进行成品入库；正常领料未完成时只告警，不自动倒冲补齐')
    para(doc, '生产完工数量、仓储流水、成本计价及总账成本凭证须分别核实，任何一个状态完成都不能泛化为全部业务和财务完成。')

    h2(doc, 'A.4 MES制造执行流程')
    numbered(doc, '手工MES工单或ERP下达消息创建工单；核对真实BOM、当前工艺路线、单位及关联ERP工单')
    numbered(doc, '手工工单审批/下达调用releaseWo及快照逻辑；ERP消息路径消费时创建工序与可用MBOM快照，不能仅凭APPROVED认为已有快照')
    numbered(doc, '创建并下达流转卡，按SN管理或对应生成入口生成/绑定SN；流转卡创建不等同于所有SN自动生成')
    numbered(doc, '按已下达路线派工、开工及进站校验，报工或出站调用工序完成逻辑；下一工序受前序、SN、工装、批次和可用规则约束')
    numbered(doc, '工序完成聚合流转卡完工，卡数量聚合工单；所有卡完成并满足关键工序IPQC等门禁后触发工单完工。关闭流转卡是单独操作，不是成品入库前提或入库完成证明')

    h2(doc, 'A.5 SN追溯流程')
    numbered(doc, '在SN或追溯页面输入SN/工单/组件物料，区分SN记录查询与正反向装配追溯')
    numbered(doc, '反向backward：成品工单/SN > MBOM快照所需组件及实际装配组件SN；parentSnId从组件指向父成品')
    numbered(doc, '正向forward：组件物料/SN > 消费该组件的成品工单及上行父SN链；不是“原材料向后、客户向前”的任意标签')
    para(doc, '批次、工序、检验或交付信息以已持久化关联记录为准；ProTraceabilityServiceImpl的装配追溯'
              '不提供自动补全发货、客户或售后记录的保证。')

    h2(doc, 'A.6 委外加工流程')
    numbered(doc, '创建委外订单，核对供应商、物料、BOM快照、报价/工程文件、计划量及目标仓；当前release允许DRAFT/APPROVED，不应写成一律先经Flowable审批')
    numbered(doc, '委外发料单过账，按真实行数量扣库存及登记流水；发料完成推进订单RELEASED、生成关联ERP工单并发送MES SN生成消息')
    numbered(doc, '委外厂商加工')
    numbered(doc, 'RELEASED/IN_PROGRESS订单创建委外收货草稿，核对收货量与目标仓 > postReceive按委外收货策略入库、创建批次、累计实际量，达计划量自动COMPLETED')
    numbered(doc, '检验、委外费用、采购发票及付款按相应业务单据另行核对；当前委外收货入口没有统一“先IQC合格再自动入库/结算”链，收货完工不代表费用自动结清')

    h2(doc, 'A.7 退货流程')
    bullet(doc, '销售退货：建DRAFT > 确认或审批转CONFIRMED > execute入库并记RETURN_IN流水、回写订单行已退数量，状态RECEIVED；退款与销售发票红冲另行办理，execute不调用支付退款')
    bullet(doc, '采购退货：合法旧订单关联行建单 > 确认或审批转CONFIRMED > execute扣库存记退货流水、回写订单行退货量，状态EXECUTED；仅覆盖已DEDUCTED蓝字进项发票时按退货行部分红冲/进项转出，不自动收取供应商退款')
    para(doc, '认证来源采购订单/行不得走普通退货入口绕过来源账，服务端认证守卫会拒绝；'
              '退货数量、实际库存、发票核销/红冲和现金退回须分别核对。')

    h2(doc, 'A.8 质检触发链路')
    bullet(doc, 'IQC：普通ERP收货审批通过后按关联到货通知需检行投MQ，MES消费建任务；无到货通知或免检行不统一自动建IQC，人工建检验仍须关联正确收货和物料。需检未合格阻止上架')
    bullet(doc, 'IPQC：关键工序完成/报工尝试幂等建过程检验，自动创建失败只告警；completeWo按关键工序存在QUALIFIED检验兜底门禁，不能用报工成功代替检验合格')
    bullet(doc, 'OQC：销售出库审批通过且关联销售订单时，仅按物料oqcCheck需检项投MQ；无关联、无明细或全部免检会跳过，需检未合格阻止销售出库执行')
    bullet(doc, '检验不合格：对应IQC/IPQC/OQC服务可创建NCR并按关联批次同步质量状态；NCR需人工处置选择返工/报废/让步等，返工及8D为独立办理，不是每张不合格单自动走完全部闭环')
    para(doc, 'IQC/OQC完成消息回写ERP后再核对门禁状态；MQ重试、死信、检验模板及抽样/明细判定均会影响真实结果，'
              '应查看任务、检验记录及ERP回写，而非只看审批或检验页面提示。')

    h2(doc, 'A.9 财务收款流程')
    numbered(doc, '实际销售出库及订单行数量核对 > 销售发票草稿 > 审批通过转ISSUED发布开票事件；出库本身不等于已开票或已有应收凭证')
    numbered(doc, '发票事件 > 异步GlPostingConsumer/SoInvoicePoster > createAndPost生成POSTED应收凭证；需OPEN期间、末级科目和借贷平衡，失败须查重试/死信，不另假设postToGl按钮')
    numbered(doc, '客户真实付款 > AR收款登记DRAFT > confirm已确认并发送GL消息 > 财务核销settle关联发票和收款单；部分核销保留未清额，满额才分别VERIFIED/SETTLED，允许按记录反核销')
    numbered(doc, '支付宝/收钱吧有客户关联的成功支付回调可自动建AR收款并FIFO核销，微信当前不发送此ERP消息；模拟入口仅dev/test/local使用，绝不是采购AP付款自动核销')
    numbered(doc, '期末先分别核对库存计价/成本流水过账、采购暂估、折旧、AR/AP及异步凭证，再按实际月结顺序试算→损益结转→外币重估→关账→下期期初；核对配置后的三大报表，不能将月结当作前述所有业务的一键完成')

    doc.add_page_break()
    h1(doc, '附录B 更新日志')

    h2(doc, 'B.1 历史版本记录：V2.0（2026年6月）')
    para(doc, '保留当时的BOM管理增强（Phase 1-5）记录，不作为当前操作、生产部署或人工验收证据：')
    table(doc,
          ['阶段', '功能', '说明'],
          [
              ['Phase 1', 'BOM基础增强', '新增BOM类型（EBOM/MBOM/SBOM/CBOM）、版本管理、状态流转'],
              ['Phase 2', 'BOM工具增强', 'BOM复制、BOM比较、Where-Used反查、BOM验证'],
              ['Phase 3', '替代料管理', 'FULL/PARTIAL/INTERCHANGEABLE替代类型，有效期管理'],
              ['Phase 4', 'BOM成本卷积', '材料参考/标准价格与工艺标准工时、人工/设备费率计算；非实际报工成本自动核算'],
              ['Phase 5', '工艺路线融合', '工艺路线版本管理、工序关联、费率配置'],
          ],
          widths=[2.0, 4.0, 10.0])
    para(doc, '历史V2条目记录ERP BOM与MES MBOM的关联方向。当前ERP BOM用于需求展开与成本卷积，'
              'MES在下达/ERP消息消费路径对可用MBOM及路线保存快照，实际仍受BOM、路线、单位和发布配置约束。'
              'BdBomServiceImpl.costRollup使用展开物料成本、标准工时及费率，并非读取实际报工后完成财务成本核算；'
              '历史“深度融合/完整闭环”不能直接当作当前业务全部自动完成。')

    h2(doc, 'B.2 历史版本记录：V3.0（2026年7-8月）')
    para(doc, '下表仅保留旧版新增功能清单。V3不是当前手册定位，功能代码存在、历史演示通过、'
              '本地测试、生产已部署和人工验收是不同结论；当前操作以正文和B.3源码核对为准。')
    table(doc,
          ['模块', '新增功能', '说明'],
          [
              ['财务核心', '总账GL（F0）', '科目/凭证/期间/汇率/试算平衡'],
              ['财务核心', '应收应付核销（F1）', '发票过账/收付款/核销/账龄'],
              ['财务核心', '固定资产（F2）', '卡片/折旧/处置'],
              ['财务核心', '存货成本（F3-a）', '流水计价字段/成本过账/暂估；实际映射与限制见16.8'],
              ['财务核心', '报表与关账（F5）', '三大报表/月结/年结'],
              ['IoT物联网', '设备接入', '产品/设备/MQTT Broker/认证/实时SSE/告警'],
              ['视觉识别', '人脸与OCR', '人脸底库/1:1/1:N/物料OCR/标签解析/人脸打卡'],
              ['即时通讯', 'IM', '私聊/群聊/通知/撤回/SSE推送/移动端'],
              ['MES P0', '批次管理', '批次主档/状态机/FEFO/SN绑定/隔离放行'],
              ['MES P0', '工装管理', '台账/寿命计数/保养/领用归还'],
              ['MES P0', '电子SOP', '文档库/工序绑定/查阅记录/工位推送'],
              ['MES P0', 'NCR与返工', 'NCR单据/缺陷代码/返工派生/8D报告'],
              ['MES P0', '进站出站', '进站/出站/跳站校验/流程指引/校验规则'],
              ['质量管理', 'QC三链路', '有条件IQC创建/IPQC过程/OQC出货/异步回写ERP，非无条件全部自动完成'],
              ['质量管理', 'SPC', '控制图/数据点/OOC告警/Cp-Cpk/趋势'],
              ['设备管理', 'MES eq', '台账/保养/维修/点检/OEE/备件/巡检/监控'],
              ['AI', '知识图谱', '构建/查询/邻居/社区/全局检索'],
              ['AI', '文生图', '通义万象图片生成 + OSS持久化'],
              ['AI', '推理与检索', '推理模式/多厂商路由/Rerank/联网搜索'],
              ['移动端', 'App', '打卡考勤/排班查询/即时通讯'],
          ],
          widths=[3.0, 4.5, 8.5])

    h2(doc, 'B.3 V4（2026-10-12）源码核对记录')
    para(doc, '本次以plus分支fe59e4ae及核对时的实际服务、控制器、前端页面/API为事实来源，'
              '保留第16-20章与附录的操作细节，直接修正旧版状态、菜单及联动描述；'
              '不运行数据库写脚本、不以历史方案、旧测试报告或演示数据代替当前实现。')
    table(doc,
          ['范围', '源码核对点', '本次纠正'],
          [
              ['总账与期间', 'erp/fin/gl/service/impl/GlAccountServiceImpl、GlVoucherServiceImpl、GlPeriodServiceImpl', '末级/引用/余额删除约束；MANUAL来源、严格平衡；OPEN过账与反过账，CLOSED重开、LOCKED无解锁'],
              ['发票与AR/AP', 'erp/so/.../SoInvoiceServiceImpl.afterFlowHandle；erp/pu/invoice/.../PuInvoiceServiceImpl.receiveInvoice；fin/arap的事件、Poster及settle/unsettle', '当前无独立postToGl；ISSUED事件异步制单，收付款确认≠总账完成；部分核销、反核销、未清分录分开核对'],
              ['成本与关账', 'erp/fin/cost/CostController、CostPoster.postTx、CostService.provision；fin/report/.../FinClosingServiceImpl', '真实事务分录、0成本跳过、暂估筛选边界；实际月结顺序、外币重估范围/缺汇率跳过、期初结转及独立日志'],
              ['真实/模拟支付', 'pay/controller/PayController；AliPayServiceImpl、SQBServiceImpl、WxPayServiceImpl；erp/fin/arap/mq/PayReceivedConsumer、fin/pay/controller/PayMockController', '回调只可能联动客户AR，非AP付款；微信未发送ERP消息；模拟仅dev/test/local，不证明真实到账'],
              ['工作流与报表', 'workflow/controller/WfModelController、WfDeployController、WfProcessController、WfTaskController；report/config/JimuReportTokenService', 'Flowable与节点Flow分开；操作需节点/参与者权限；iframe与Token桥接不证明模板、推送或数据隔离已验收'],
              ['系统与菜单', 'system/controller/system/SysMenuController、SysTenantController；SysMenuServiceImpl；plus-ui/src/store/modules/permission.ts', '当前组件路径≠生产URL；菜单与套餐/角色动态配置；超级管理员及租户开关边界'],
              ['业务链路', 'erp/pu认证采购服务及wm认证收货入口；prd工单/委外、so/pu退货；mes/pro、mes/qc服务与ERP/MES消费者', '认证采购、正常领料/倒冲、条件QC、退货库存/红冲/退款及委外费用不能泛化为一次审批后的自动完整闭环'],
          ],
          widths=[2.3, 6.5, 7.2])
    para(doc, '表中包路径相对各业务模块的src/main/java/com/nexus；模块根目录为'
              'ruoyi-modules/ruoyi-erp、ruoyi-modules/ruoyi-pay、ruoyi-modules/ruoyi-flowable、'
              'ruoyi-modules/ruoyi-report、ruoyi-modules/ruoyi-system以及ruoyi-modules/ruoyi-mes。'
              'CostController位于fin/cost/controller，CostPoster在poster，CostService在service；'
              '省略号表示service/impl等子目录，不是API路径。'
              '正文所列Vue文件均为组件位置，目标生产菜单需另行读取配置核验。')
    para(doc, '本次核对只形成源码级文档证据，不保证生产部署、真实支付回调联调、MQ实际运行、'
              '全年财务正确性、税务认证或人工端到端验收已完成。未执行渠道交易、数据库迁移、'
              '业务写入或现场设备操作；后续验收必须记录具体环境、数据、权限、消息结果与人工结论。'
              'AI、Station及电子签章等既有历史短条目不由此获得额外验收结论，相关当前说明另由对应章节维护。')

    para(doc, '本手册正文结束；请按目标环境权限与真实业务证据核对后使用。')
