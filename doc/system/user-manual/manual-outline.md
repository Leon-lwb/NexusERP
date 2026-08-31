# NexusERP 用户操作手册 V3.0 扩充大纲

> 本文档规划 V3.0 用户操作手册的结构与每个页面的写作要点。
> 状态字典值均来自 `script/sql/ry-vue.sql` 实际数据（已核实）。

## 文档定位
- 面向最终用户（业务操作人员）的操作指南
- 每个功能页：**入口 → 列表页字段/按钮 → 表单字段 → 状态流转 → 操作步骤 → 注意事项**
- 版本 V3.0（2026年8月），对应代码库当前状态

## 章节结构（20 章 + 附录）

| 章 | 标题 | 写作要点 |
|----|------|----------|
| 1 | 系统概述 | 简介、15大模块总览表、登录、界面布局、通用操作、版本说明 |
| 2 | 基础数据管理 | 物料/分类/工序/工艺路线/BOM/客户/供应商/车间工位/计量单位/序列号规则/物料-供应商 |
| 3 | 销售管理 | 询价/报价/合同/销售订单/发票/退货/价格表/报表 |
| 4 | 采购管理 | 部门请购/采购申请/合同/订单/退货/评估/比价/报表 |
| 5 | 请购管理 | 请购单创建/状态流转/转采购订单 |
| 6 | 生产管理 | 计划/工单/物料需求/返工/委外/产能日历/完工入库/报表 |
| 7 | 排产调度 | 班次/班组/工人/排班/模板/打卡/假期/考勤/移动端/人脸打卡 |
| 8 | MES制造执行 | MBOM/工单/派工/流转卡/进站出站/SN/追溯/安灯 |
| 9 | 质量管理与SPC | IQC/IPQC/OQC/追溯/SPC控制图 |
| 10 | 设备管理 | 台账/保养/维修/点检/OEE/备件/巡检/监控 |
| 11 | 批次/工装/SOP/NCR | 批次/工装/SOP/NCR+8D |
| 12 | 数据采集与IoT | 采集点/产品/设备/MQTT/实时/告警/任务 |
| 13 | 视觉识别 | 人脸底库/人脸打卡/OCR |
| 14 | AI智能助手 | 对话/模型/知识库/知识图谱/Agent/Prompt/SQL/A-B/文生图/MCP/Token |
| 15 | 即时通讯 | 私聊/群聊/通知/移动端 |
| 16 | 财务核心 | 总账/应收应付/固定资产/存货成本/报表关账 |
| 17 | 支付管理 | 统一支付/订单/收款联动 |
| 18 | 工作流引擎 | 设计/提交/审批/任务/ERP集成 |
| 19 | 报表管理 | 仪表盘/设计器 |
| 20 | 系统管理 | 用户/角色/菜单/部门/字典/租户/设备/社交/监控 |
| 附录A | 典型业务流程 | 销售生产全链路/采购/生产/MES/追溯/委外/退货/财务/质检 |
| 附录B | 更新日志 | V2.0 + V3.0 |

## 关键状态机（来自字典，写入文档用）

### 销售
- so_order_status: DRAFT草稿→PENDING_REVIEW待评审→APPROVING审批中→APPROVED已批准/REJECTED已驳回→CONFIRMED已确认→PARTIALLY_FULFILLED部分履行→FULFILLED已履行→CLOSED已关闭/CANCELLED已取消
- so_quotation_status: DRAFT→CONFIRMED→CONVERTED
- so_return_status: DRAFT→APPROVING→CONFIRMED→RECEIVED
- so_invoice_status: DRAFT→APPROVING→ISSUED→VERIFIED/CANCELLED

### 采购
- pu_purchase_order_status: DRAFT→SUBMITTED→APPROVING→APPROVED/REJECTED→PARTIALLY_RECEIVED→RECEIVED→CLOSED/CANCELLED
- pu_purchase_requisition_status: DRAFT→SUBMITTED→APPROVING→APPROVED/REJECTED→ORDERED→CLOSED/CANCELLED
- pu_purchase_return_status: DRAFT→APPROVING→CONFIRMED→EXECUTED/CANCELLED
- pu_contract_status: DRAFT→APPROVING→APPROVED/REJECTED→EXECUTING→COMPLETED/TERMINATED

### 生产
- prd_plan_status: DRAFT→CONFIRMED→RELEASED→CLOSED
- prd_wo_status: CREATED→APPROVING→APPROVED→RELEASED→IN_PROGRESS→COMPLETED→CLOSED/CANCELLED
- prd_requirement_status: DRAFT→CONFIRMED→PARTIAL_ISSUED→ISSUED→CLOSED
- prd_rework_status: DRAFT→IN_PROGRESS→COMPLETED
- prd_subcontract_status: DRAFT→SUBMITTED→APPROVED→IN_PROGRESS→COMPLETED→CLOSED

### 排产
- sch_plan_status: draft→published→closed
- sch_schedule_status: normal/swapped/leave/absent/rest
- sch_attendance_status: normal/late/early/absent/leave/late_early_leave
- sch_holiday_type: legal法定/rest调休/company公司假日

### 仓储
- wm_material_receive_status: DRAFT→APPROVING→APPROVED待上架→SHELVED待入库→FINISHED/CANCELED/REJECTED
- wm_sales_status: DRAFT→APPROVING→APPROVED/REJECTED→CONFIRMED→COMPLETED/CANCELLED
- wm_transfer_status: DRAFT→APPROVING→APPROVED/REJECTED→EXECUTED
- wm_stocktaking_status: DRAFT→APPROVING→COMPLETED/REJECTED→GENERATED→POSTED
- wm_notice_status: DRAFT→APPROVING待质检→APPROVED待入库→FINISHED
- wm_production_issue_status: DRAFT→CREATED→PARTIAL→COMPLETED/EXECUTED/BACKFLUSH
- wm_misc_status: DRAFT→EXECUTED
- wm_sn_status: IN_STOCK→OUT/RESERVED/SCRAP
- wm_batch_status: ACTIVE→LOCKED/EXPIRED/CLOSED
- wm_location_status: 0空置/1占用/2锁定/3预警

### MES
- mes_flow_card_status: CREATED→RELEASED→IN_PROGRESS→COMPLETED→CLOSED
- mes_wo_status: CREATED→RELEASED→IN_PROGRESS→COMPLETED/CANCELLED（MES侧）
- mes_lot_status: IN_STOCK→IN_USE→CONSUMED/QUARANTINED→SCRAPPED
- mes_ncr_status: OPEN→REVIEW→DISPOSITION→IN_REWORK→CLOSED
- mes_sop_status: DRAFT→IN_REVIEW→PUBLISHED→ARCHIVED
- mes_tooling_status: IDLE⇄IN_USE/MAINTENANCE→SCRAPPED
- mes_dispatch_status: PENDING→IN_PROGRESS→COMPLETED/CANCELLED
- mes_step_status: PENDING→READY→IN_PROGRESS→COMPLETED/SKIPPED
- mes_andon_status: CALLED→RESPONDED→RESOLVED/CANCELLED
- mes_equipment_status: NORMAL→MAINTENANCE/FAULT/SCRAPPED/OFFLINE
- mes_qc_status: PENDING→INSPECTING→COMPLETED
- mes_qc_result: PENDING→QUALIFIED/UNQUALIFIED
- mes_repair_status: REPORTED→REPAIRING→COMPLETED
- mes_sn_status: IN_PRODUCTION→COMPLETED→SHIPPED/SCRAPPED
- mes_mbom_status: DRAFT→ACTIVE→FROZEN

### 财务
- fin_receipt_status: DRAFT→CONFIRMED→SETTLED/CANCELLED
- fin_receipt_direction: AR应收(收款)/AP应付(付款)
- fin_receipt_type: CASH/BANK/ALIPAY/WECHAT/OTHER
- fin_closing_step: PENDING→TRIAL_CHECK→CARRY_PL→REVALUATION→REPORT_SNAPSHOT→CLOSED

### 支付/工作流
- pay_order_status: waiting未支付→success已支付/closed已关闭/refund已退款
- wf_business_status: draft→waiting→finish/cancel/back/invalid/termination
- wf_task_status: waiting→pass/back/transfer/depute/copy/sign/sign_off/timeout/cancel/termination/invalid

## 待补充（agent 返回后）
- 各页面表单字段清单、工具栏按钮、操作步骤（3 个 Explore agent 收集中）
- 编码生成规则、校验规则、联动行为

## MES/IoT/CV/IM 页面调研结果（agent 已返回）

### MES 工单（pro/workorder）
- 列表：搜 工单编号/产品名称/状态(prd_wo_status)；列 工单编号/产品编码/名称/计划数量/车间/计划开工/状态
- 表单：woNo必填(编辑后禁用)、materialId必填(选后带出编码名称)、plannedQty必填(min0.0001精度4)、优先级默认0、bomId、routingId、equipmentId、planStartDate/planEndDate、备注
- 状态机：CREATED→【下达】→RELEASED→【开工】→IN_PROGRESS→完工
- 操作：新增→保存(状态CREATED)→行内下达→详情提交审批(选流程flowId)→创建流转卡(数量校验:≤剩余可建=计划数量-已建合计)
- 权限：mes:pro:work:add/edit/remove/query

### 工序派工（pro/dispatch）
- 状态机：PENDING→【开工】→IN_PROGRESS→【完工:良品/不良/报废】→COMPLETED；PENDING可取消
- 扫码执行ScanExecute：扫流转卡条码→定位当前工序→开工(输操作员ID)→完工(输数量)
- 刷脸登录FaceLoginDrawer：拍照→opsIdentify(file,3)top-3候选→手选→verifyFace 1:1确认(阈值50%)→emit verified

### 流转卡（pro/flow-card）
- 状态机(cardStatus)：CREATED→【下达】→RELEASED→【进站】→IN_PROGRESS→【完工】→COMPLETED→【关闭】→CLOSED
- 工序级(stepStatus)：PENDING→【进站】→IN_PROGRESS→【出站】→COMPLETED；PENDING可【跳站】(需原因)
- 操作：派工(操作员必填/数量1~剩余可派)→进站(操作员必填/工装可选/批次可选)→出站完工(良品必填默认计划量/不良/报废)→校验未通过弹ProCheckResultVo明细
- 校验类型mes_check_type: SERIAL_GATE串行门禁/SOP_VERSION/TOOLING_AVAILABLE/LOT_AVAILABLE/EQUIPMENT_STATUS/MATERIAL_KITTING/OPERATOR_SKILL/QUANTITY_REACHED/INSPECTION_REQUIRED/EXCEPTION_CLOSED/PARAMETER_OK

### MBOM（pro/mbom）
- 状态机：DRAFT→【激活】→ACTIVE(唯一生效版本)
- 表单：bomId必填(选后带routingId)、routingId、mbomVersion默认1.0、明细行(物料必填/用量>0精度6/损耗率0-100/工序步骤/工位/作业内容/倒冲switch)

### SN（pro/sn）
- 状态机：IN_PRODUCTION→【完工】→COMPLETED→【发货】→SHIPPED；可【报废】(需原因)
- 操作：批量生成(选流转卡+数量1-9999+初始状态默认IN_PRODUCTION)；手动建SN(编码必填+物料必填+可选工单/流转卡/父SN)；追溯页前后向树
- 来源：BATCH批量/手动

### 安灯（pro/andon）
- 状态机：CALLED→【响应】→RESPONDED→【解决】→RESOLVED；CALLED可取消
- 呼叫类型：QUALITY质量/MATERIAL物料/EQUIPMENT设备/OTHER
- SSE监听mes-andon事件自动弹ElNotification，统计卡片区

### 工序指引（pro/step-guide）
- 类型：TEXT文本(内容必填)/SOP_LINK(仅可选PUBLISHED的SOP)
### 工序校验规则（pro/step-check-rule）
- 校验阶段：ENTRY进站/EXIT出站；失败提示errorMsg

### QC三链路（qc/iqc, ipqc, oqc）
- 状态机(共用)：PENDING→【检验】→COMPLETED；结果PENDING/QUALIFIED/UNQUALIFIED
- IQC新增：收货单可选(ERP receive)+物料必填+供应商/检验数量/抽样数量；检验：结果必填(排除PENDING)+抽样数量+不良品JSON
- IPQC新增：工单必填+流转卡可选+工序+物料必填；检验：结果/抽样/不良数量/不良品JSON
- OQC新增：销售订单可选+物料必填；检验同上

### 设备管理（eq）
- 台账：设备编码/名称必填+类型+工作中心+状态默认NORMAL+点检项目(JSON)+IoT在线状态；关联IoT对话框
- 保养：类型(日/周/月/季/年)+周期天数+下次日期(过期红tag)；执行保养推进下次日期
- 维修：REPORTED→【开工】→REPAIRING→【完成:停机时长/成本/更换备件】→COMPLETED；报修联动设备置故障
- 点检：按设备点检项逐项(实际值+合格switch)
- OEE：统计区间/设备/类型筛选+手动聚合(按租户+设备+日期幂等)+KPI卡+趋势图+停机TOP10；稼动率≥85%绿/≥60%黄/<60%红
- 备件：安全库存(低于红tag)；出入库(出库max=当前库存)
- 巡检：任务(PENDING→执行→COMPLETED)+路线(设备ID列表+检查项JSON+频率)+记录(照片≤5MB)
- 监控：30秒轮询+6张状态卡+活跃维修+低库存告警

### 批次（lt/lot）
- 状态机：IN_STOCK→【消耗/绑定】→IN_USE→CONSUMED(剩余≤0自动)；隔离→QUARANTINED→放行回IN_STOCK或报废→SCRAPPED
- QC状态：PENDING→PASS/FAIL
- 编码：MES_LOT_CODE自动生成(previewCode预览不占号)；有效期30天到期黄tag
- FEFO推荐：物料+需求数量→推荐列表(临近到期黄/绿/推荐顺序)
- 消耗：工单+批次+物料+消耗数量(联动扣减批次数量)；FEFO领料：工单+物料+需求数量→推荐→多批次确认领料
- SN绑定：SN必填+批次必填+物料必填(各自带出)

### 工装（tg/tooling）
- 状态机：IDLE→【领用】→IN_USE→【归还:使用次数必填】→IDLE；IDLE→【开始保养】→MAINTENANCE→【完成保养:计数归零】→IDLE；报废→SCRAPPED
- 编码：MES_TOOLING_CODE；寿命已用/总寿命次≥90%红tag
- 保养到期预警列表；存在使用/保养记录不可删

### SOP（sop/document）
- 状态机：DRAFT→【提交审批】→IN_REVIEW→【通过】→PUBLISHED→【归档】→ARCHIVED；IN_REVIEW可驳回
- 编码：MES_SOP_CODE；文件类型PDF/IMAGE/VIDEO/WORD(≤100MB)
- 版本历史；预览PDF iframe/图片/视频/Word下载
- 绑定：工序必填+SOP必填(仅PUBLISHED)+指定版本(空=最新)+默认switch
- 查看器：stepRecordId优先→processId；自动登记view-log；未绑定显示提示

### NCR（ncr/order）
- 状态机：OPEN→【评审】→REVIEW→【处置】→DISPOSITION(→IN_REWORK返工)→CLOSED
- 编码：MES_NCR_CODE；来源IQC/IPQC/OQC/PROCESS/CUSTOMER
- 处置方式：REWORK返工/SCRAP报废/ACCEPT_AS_IS让步接收/RETURN退货
- 详情3Tab：缺陷明细(缺陷代码/原因分类MAN机料法环测/根因描述)、返工工单、8D报告(D1-D8)
- 8D：DRAFT→IN_PROGRESS→【完成】→COMPLETED
- 缺陷代码库：树形层级维护

### 数据采集（dc）
- 采集点：MANUAL/AUTO(AUTO必关联IoT设备+属性编码)；数据类型NUMERIC/TEXT/ENUM；频率秒
- 数据记录：采集点+数值(精度4用于SPC)+文本值+关联工单/流转卡

### SPC
- 控制图：类型X_BAR_R/X_BAR_S/P/C/U；样本大小2-10；UCL/CL/LCL；关联采集点必填
- 分析：ECharts控制图+OOC红点+违规规则+数据点数
- 数据点：样本组号/值/是否OOC/违规规则(由采集记录自动归档)

### IoT
- 产品：编码/名称必填+类型默认DEVICE+协议默认MQTT
- 设备：产品必填+设备编码必填(MQTT ClientID)+设备名称；连接状态ONLINE/OFFLINE；关联MES双向
- 实时：SSE订阅+最新值卡片+3秒刷新60点滚动+断线自动重连(5次失败停止)+历史1小时

### CV
- 人脸底库：注册(工号EMP-xxxx必填/姓名/照片JPEG PNG≤10MB)+重新注册+批量注册+服务状态
- 物料检测：上传照片→勾选OCR/解析料号批次SN→识别(耗时ms)+YOLO通用检测；结果4Tab
- FAIL复查：24h复查窗口；误判改PASS/代打卡确认FAIL；照片经后端代理(私有桶)

### IM
- 左侧4Tab：聊天/联系人/群聊/设置；右侧会话窗口
- 图片≤10MB文件≤20MB；@群成员(mentionUserIds)；撤回限时；转发；失败重试；上滑加载更多
- 私聊已读/未读；群主管理成员

## AI/财务/工作流/系统 页面调研结果（agent 已返回）

### AI 厂商（model/provider）
- 列表：厂商名称/类型(OPENAI/DASHSCOPE/ZHIPU/OLLAMA)/状态；API Key列表脱敏(sk-***+后4位)
- 表单：名称*、API地址*、API Key(show-password)、类型*、状态默认正常

### AI 模型（model/index）
- 列：厂商/模型名/显示名/类型(CHAT/EMBEDDING/RERANK/IMAGE)/最大Token/温度/默认/状态/价格(元/M)
- 表单：厂商*、模型名*、类型*、最大Token(≥0)、温度(0-2步长0.1)、是否默认switch、输入/输出价格(精度6)、币种
- 用途：EMBEDDING→知识库数据源；IMAGE→文生图；RERANK→知识库重排

### AI 对话（assistant）
- 4模式：自动/对话/数据查询Agent/知识库RAG；模型选择；深度思考switch；联网搜索switch；MCP工具勾选
- 消息：思考过程折叠、信息来源卡、工具调用过程、Markdown+ECharts、点赞点踩(LIKE/DISLIKE)
- 会话：新对话/导出/重命名/删除；AI_IMAGE事件嵌入文生图结果

### 知识库（knowledge）
- 文档格式：pdf/docx/doc/xlsx/xls/txt/csv/md/html；单文件≤50MB；状态0待解析/1解析中/2完成/3失败(可重试)
- 表单：名称*、Embedding模型*(无可用红字提示)、向量存储Milvus/InMemory、高级配置(分段符默认#/重叠30/块500/检索5/阈值0.5/重排/混合检索hybridAlpha)
- 检索测试：Top K 1-20默认5、阈值0-1默认0.5、混合检索、重排序

### 知识图谱（graph）
- 状态：0未构建/1构建中/2完成/3失败；分阶段EXTRACTING/DETECTING/SUMMARIZING(2秒轮询)
- 页签：实体/关系/社区摘要/全局检索/邻居；ECharts力导向图按社区着色

### MCP工具（mcp）
- 类型：BUILTIN内置/LOCAL本地STDIO/REMOTE远程HTTP·SSE；工具名snake_case校验
- 市场：市场源CRUD+工具列表+单个/批量加载(加载默认停用，需到工具管理启用)

### Prompt/SQL示例/Token/AB/文生图
- Prompt：名称*/标识*(编辑禁用)/内容*(支持{{variable}})/是否默认/版本
- SQL示例：问题*/SQL模板*/分类(INVENTORY/SALES/PURCHASE/PRODUCTION)/排序
- Token：时间范围+模型筛选；总Token/输入/输出/成本卡；趋势图+模型/用户排行；回填历史
- A/B：名称/模型A/B*/问题JSON数组*；PENDING→RUNNING→COMPLETED；1-5星评分直接保存
- 文生图：提示词(≤500)/模型(IMAGE)/尺寸(1024/1280/720*1280/1280*720)；历史缩略图

### 财务-总账
- 科目：树形表格；科目类型/余额方向(DEBIT/CREDIT)/末级(仅末级可过账)/统驭(AR/AP/FA)；新增子级
- 凭证：DRAFT→【过账】→POSTED→【反过账】；作废CANCELLED；仅DRAFT可改删；分录行(科目仅末级/摘要/借方贷方互斥)；借贷不平衡拒绝保存；凭证字默认J
- 期间：OPEN→【关账】→CLOSED→【锁定】→LOCKED；反结账；生成年度期间(2020-2099批量12期)
- 汇率：币种/本位币CNY/汇率日期/汇率(精度6)/类型(MIDDLE/BID/ASK)
- 试算：期间筛选；期初/本期/期末借贷方；合计行；差<0.005视为平衡

### 财务-收付核销
- 收付款单：方向AR/AP(联动显示客户/供应商)、类型默认BANK、金额*；DRAFT→【确认(触发GL过账)】→CONFIRMED→【核销】→SETTLED；作废
- 核销：方向+发票(未清xx下拉)+收付款单(未清xx下拉)+核销金额*；核销后发票VERIFIED+收付款SETTLED(904清账)

### 财务-固定资产
- 资产：名称*/类别*(带出年限残值率)/原值*/净残值(留空=原值×残值率)/年限(1-600)/购置日*/启用日(留空=购置日)/折旧方法(直线/双倍余额/年数总和/手工)
- 状态：CWIP在建→【转固】→IN_USE→【处置】→DISPOSED
- 折旧：选期间→计划预览→计提并过账(借费用/贷1602累计折旧；同期间幂等)
- 处置：资产(仅IN_USE下拉带净值)/类型(出售/报废)/收入*(报废可0)/日期

### 财务-成本/报表/关账
- 成本：事务类型(RECEIVE_IN/PRODUCTION_ISSUE/SALES_OUT/MISC_IN/MISC_OUT)；期间暂估(采购暂估入账)；入库借存货贷应付暂估/出库借成本贷存货；已过账幂等
- 报表：资产负债表(勾稽平衡提示)/利润表(本期/本年累计)/现金流量表(直接法基础版)
- 月结向导：①试算校验→②损益结转(凭证号)→③外币重估→④关账(不可再过账)；一键执行月结；日志表
- 模拟支付：客户*+收款金额*+渠道(alipay/wechat)+支付单号(留空自动MOCK+时间戳)；右侧未核销发票列表；1.5秒后验证自动核销

### 工作流
- 待办：任务编号/流程名/业务ID/节点/版本/发起人/接收时间；办理跳详情
- 已办：收回recall/撤回revoke
- 我发起的：当前节点/状态/耗时；取消stopProcess/再次发起/删除
- 可认领：签收claimTask→跳待办
- 抄送：打开详情标记已读updateCcReaded
- 发起：加载流程表单→FormRender→清洗驼峰键→先保存业务数据addOrUpdateOnlineData→startProcess
- 详情审批：通过/委派/转办/加签(0前/1后/2多实例)/跳转/退回/驳回/拒绝；审批意见必填
- 模型：标识自动Process_时间戳；设计器保存选新版本或覆盖；部署→版本管理(挂起/激活)
- 分类：应用类型wf_app_type(ZDYYW自定义业务流程才能绑定表单)
- 表单设计器nform：选表→预览结构→拖拽设计；字段智能映射(含type/status自动推断字典)；编号自动生成
- 自定义表单：业务表单+流程绑定(关联流程弹窗)；组件注入方法

### 支付
- 订单：商户订单号/第三方订单号/状态(waiting/success/closed/refund)/金额/支付方式(alipay/wechat/sqb)
- 测试页：创建订单→获取支付链接→二维码(沙箱APP扫码)或网页支付；自动轮询5秒×30次

### 系统管理
- 用户：部门树联动；行内switch启停；重置密码/分配角色(userId≠1)；导入导出模板
- 角色：权限字符/菜单权限树(父子联动switch)/数据权限(1全部/2自定义/3本部门/4本部门及以下/5仅本人/6部门及以下或本人)
- 菜单：树懒加载；级联删除；类型M/C/F；外链http(s)
- 部门：部门类别dept_category/负责人/电话11位校验
- 字典：左右分栏；刷新缓存refreshCache；回显样式listClass
- 租户：同步租户字典/参数(userId=1)；同步套餐；自动创建租户管理员
- 在线用户：强退forceLogout
- 操作日志：详情看完整参数/结果/异常
- 登录日志：解锁账户unlockLoginInfo
