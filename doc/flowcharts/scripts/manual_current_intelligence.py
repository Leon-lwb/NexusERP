#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""当前AI扩展与电子签章章节，仅调用手册回调，不生成或保存文件。

核对日期2026-10-12，分支plus，基线fe59e4ae26fe8dde42e24dba12a233568b707bbe。
extend_ai接在ch14_ai的14.8后；ch22_esign独立调用，生成器接线由合并方负责。
下列仓库相对路径为代码证据入口；菜单定义只证明安装基线，不证明已部署。
"""

SOURCE_FILES = (
    'plus-ui/src/views/ai/assistant/index.vue',
    'plus-ui/src/views/ai/constants.ts',
    'plus-ui/src/views/ai/config/index.vue',
    'plus-ui/src/views/ai/config/DecisionSettings.vue',
    'plus-ui/src/views/ai/decision/detail.vue',
    'plus-ui/src/views/erp/so/orderheader/index.vue',
    'plus-ui/src/views/ai/flow/index.vue',
    'ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/controller/AiChatController.java',
    'ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/facade/AiServiceFacade.java',
    'ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/facade/CompleteAnswerChatModel.java',
    'ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/tool/ExecuteSqlQueryTool.java',
    'ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/controller/AiAbTestController.java',
    'ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/config/impl/AiConfigServiceImpl.java',
    'ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/decision/DecisionSummaryService.java',
    'ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/decision/DecisionPolicyGuardService.java',
    'ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/decision/DecisionApprovalService.java',
    'ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/service/decision/DecisionActionExecutionService.java',
    'ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/ai/adapter/DeliveryContextAssembler.java',
    'ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/ai/adapter/ErpDecisionApsSimulation.java',
    'ruoyi-modules/ruoyi-flow/src/main/java/com/nexus/flow/adapter/DecisionFlowAccessGuard.java',
    'ruoyi-modules/ruoyi-flow/src/main/java/com/nexus/flow/controller/FlowController.java',
    'ruoyi-common/ruoyi-common-flow/src/main/java/com/nexus/common/flow/service/impl/FlowServiceImpl.java',
    'plus-ui/src/views/system/sign/seal/index.vue',
    'plus-ui/src/views/system/sign/grant/index.vue',
    'plus-ui/src/views/system/sign/record/index.vue',
    'plus-ui/src/views/erp/so/contract/detail.vue',
    'plus-ui/src/views/erp/pu/contract/detail.vue',
    'plus-ui/src/components/sign/SignAuthDialog.vue',
    'plus-ui/src/components/sign/PdfSealPlacer.vue',
    'plus-ui/src/api/system/sign/document.ts',
    'ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign/service/impl/ESignatureServiceImpl.java',
    'ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign/service/impl/EsSignDocumentServiceImpl.java',
    'ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign/support/SealAvailabilityResolver.java',
    'ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign/support/SignAuthVerifier.java',
    'ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign/service/impl/EsSealGrantServiceImpl.java',
    'ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign/support/pdf/PdfDocumentVerifier.java',
    'ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign/support/pdf/SelfSignedCertificateFactory.java',
    'ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign/controller/EsSignDocumentController.java',
    'ruoyi-modules/ruoyi-system/src/main/java/com/nexus/system/sign/PasswordSignCredentialChecker.java',
    'script/sql/ry-vue.sql',
)


def extend_ai(doc, h1, h2, h3, para, bullet, numbered, table):
    """延续14.9起的章节，不重复第14章标题或14.1–14.8。"""
    h2(doc, '14.9 工业交付风险决策')
    para(doc, '看板入口：AI 智能助手 > 工业决策中心 > 交付风险看板，组件plus-ui/src/views/ai/decision/index.vue。'
              '详情组件为同目录detail.vue；菜单名称来自当前安装SQL，账号需相应权限，存量环境需另行核对安装情况。')
    numbered(doc, '在销售订单列表的订单行点击“交付风险分析”（plus-ui/src/views/erp/so/orderheader/index.vue）。'
                  '当前页面提交SALES_ORDER业务ID、30天推演及MANUAL触发，需ai:decision:run；'
                  '接收runId后进入详情，不代表分析已完成。')
    numbered(doc, '观察运行状态和时间线，查看可信业务快照、风险根因、方案KPI比较、逐物料缺口比例及证据。'
                  '看板图表统计的是当前页，不是全租户全部订单；缺少证据、推演不可行或未知指标时先补齐源数据并重新计算。')
    para(doc, '业务事实、工业KPI和方案排名由ERP快照、缺料/APS推演及确定性规则产生。'
              '模型仅生成定性解释和操作清单，引用必须对应已有证据；模型异常或摘要格式不合格可回退服务端模板。'
              '模型不得自行计算KPI、补造证据或直接写业务数据；模板降级不表示业务数据已经过人工验收。')
    numbered(doc, '待审批时逐项复核目标、动作、执行权限和证据，再点击“批准选中项”或“驳回全部”。'
                  '未选动作会被驳回；高风险另有确认提示。批准需要ai:decision:approve、ai:decision:execute，'
                  '并具备选中动作的业务权限；驳回意见至少两字。')
    table(doc,
          ['当前动作', '操作边界'],
          [
              ['催办 / 管理通知', '由明确的通知执行器处理；发送记录不代表对方已收到或已处理。'],
              ['采购申请草稿', '需pu:requisition:add，并核验冻结来源、合同版本及采购资格；不是采购订单或已审批采购。'],
              ['排产方案草稿', '需prd:schedule:run；结果需回查ERP状态，草稿不等于排产已发布。'],
          ],
          widths=[4.0, 12.5])
    numbered(doc, '在“动作审批 / 执行结果”核对审批状态、执行状态、结果ID和错误分类，点击“回查ERP结果”后查看实际记录。'
                  '批准不等于执行成功。只有受控的已批准失败动作允许重试，接口为/ai/decision/action/{actionId}/retry，'
                  '需ai:decision:execute；当前结果组件没有通用重试按钮。')
    para(doc, '审批和执行重读权限、租户、运行版本、快照新鲜度、可信动作来源及幂等键。'
              '源业务变化可转STALE并要求重新分析；旧运行、旧采购合同或权限失效不能靠重复批准绕过。'
              '通用AI回答和通用流程测试不能代替这些守卫；本章不宣称生产部署、全部案例SLA或竞赛验收完成。')

    h2(doc, '14.10 独立Flow流程编排')
    para(doc, '入口：AI 智能助手 > 流程编排 > 流程列表 / 运行历史列表。'
              '组件为plus-ui/src/views/ai/flow/index.vue、designer/index.vue及monitor/index.vue。'
              '该能力位于ruoyi-flow和ruoyi-common-flow，API仍为/ai/flow/**，表仍为ai_flow*；'
              '不是第18章Flowable/BPMN审批引擎，也不是仍嵌在ruoyi-ai里的旧实现。')
    numbered(doc, '“新建流程”或“从模板创建”，进入设计器配置节点、连线和运行入参。'
                  '常规节点包含AI、条件/变量/代码/SQL、HTTP/MQ/OSS、通知以及等待/合并/分批/聚合；'
                  '图校验通过只证明可被引擎接受，不证明外部动作安全。')
    numbered(doc, '保存草稿后进行必要的测试，复核每个节点的输入、输出和耗时。'
                  '草稿测试可使用未保存的画布配置，单节点测试也可能真实调用工具或外部服务；不是只读模拟环境。'
                  '先在明确授权的隔离环境确认通知、网络请求、对象存储和MQ副作用。')
    numbered(doc, '发布DRAFT流程时保存版本快照，正式运行要求PUBLISHED。已发布流程先下线再编辑；'
                  '从历史版本恢复后回到DRAFT，需重新发布。并发版本或状态变化时刷新，不重复覆盖。')
    numbered(doc, '通过“历史”查看实例和节点快照、错误、耗时；普通失败实例可从受控重试入口续跑，'
                  '先核对已成功节点的副作用与幂等约束。重试不是撤销上次执行。权限由ai:flow:list/add/edit/remove/run等接口分别检查。')
    para(doc, 'Webhook在当前控制器中不要求登录；配置webhookToken时校验x-flow-token，未配置时路径标识本身即访问凭据。'
              '部署方需控制网络暴露和令牌，不要复制真实Webhook地址/Token进手册或把它等同于当前用户交互授权。'
              '定时及Webhook执行身份取流程记录，不是调用者提交的任意租户。')
    para(doc, 'system_delivery_risk_*及含industrial.*节点的工业系统图受DecisionFlowAccessGuard保护，'
              '不能通过通用编辑、运行、测试、Webhook、版本恢复或实例重试替换工业决策审批/执行。'
              '应回到14.9的决策接口操作，不能复制受保护节点以绕过策略。')

    h2(doc, '14.11 当前批量问题与完整明细查询')
    para(doc, '当前assistant/index.vue没有问题文件导入、逐题队列、批次启动/停止或按批次重跑面板。'
              '聊天请求仍是一条content；/ai/chat/send服务端限制非空且不超过2000个Java字符长度单位。'
              '不能把知识库文档上传、MCP市场批量加载或A/B测试JSON数组当作助手问题导入；本页没有可宣称的CSV/XLSX导入格式或每批题数上限。')
    numbered(doc, '批量查业务明细时，使用“数据查询”并关闭显式联网开关，写清日期、对象、排序和所需字段。'
                  '例如要求“按订单号列出本月采购订单”。超过输入或输出预算时按日期、订单区间分多次查询，不能通过重复“全部”绕过限制。')
    para(doc, 'AiServiceFacade对纯ERP数据问题且含“全部/所有/全量/逐条/每条/批量/完整列出”等关键词时，'
              '可直接调用ErpQueryAgent，减少长明细再次进入Supervisor调度JSON；资料证据、报表/图表、MCP、联网或知识库组合任务保留其他路由。'
              '这是一条问题的路由优化，不是并发执行多题、无限返回或强制全量数据库导出。')
    bullet(doc, '纯查询由CompleteAnswerChatModel流式发送实际正文；若模型终止原因为LENGTH或模型响应失败，'
                '不会把截断正文保存为完成答案。页面可能已显示部分正文，仍须按终态及历史记录确认。')
    bullet(doc, '返回量受查询工具、SQL行数/超时、模型上下文与输出Token限制。通用ExecuteSqlQueryTool的Markdown预览'
                '最多10行、8列，长值会截断；其Total是本次返回行数，不是全表总量。“完整列出”不能恢复未交给模型的数据。')
    numbered(doc, '一轮生成中页面loading阻止同一输入框再次发送；等待完成或失败后再发下一题。'
                  '新建会话、清空或切换会话不构成服务端取消，不应以这些操作隔离仍在运行的回答。'
                  '当前页面没有按批次runId维护队列，跨会话/多窗口同时生成的归属和恢复不应作保证。')
    numbered(doc, '需要比较一组问题的模型回答时，改用14.6的A/B测试JSON数组。当前执行入口按testId查询结果，'
                  '没有独立的每轮结果筛选，重跑同一测试不清空既有结果；为隔离本轮请新建测试并核对实际问题/模型记录。'
                  '代码中未见统一题数限额，不代表无限安全运行；先限制问题数和费用预算。')

    h2(doc, '14.12 AI运行参数设置')
    para(doc, '入口：AI 智能助手 > 模型配置 > 参数设置，组件plus-ui/src/views/ai/config/index.vue。'
              '读取需ai:config:list，“保存并生效”需ai:config:edit；厂商/默认模型仍在ai_provider/ai_model管理，'
              '本页不是旧版yml的defaultProvider/defaultModel设置。')
    table(doc,
          ['页面分组', '当前可配置内容'],
          [
              ['SQL 安全', '表名前缀、SQL超时和最大返回行数；仅针对AI的ERP SQL查询。'],
              ['向量存储', 'milvus/inmemory、Milvus地址、集合前缀及向量维度。'],
              ['联网搜索', '启用、厂商、API Key、返回条数及请求超时。'],
              ['流程编排', '并行分支开关；不能替代节点权限、租户或幂等控制。'],
              ['知识图谱', '启用、存储、抽取模型、批大小、实体/关系上限、置信度及召回/社区参数。'],
              ['工业决策', '启用、并发、方案数、分析/动作超时、证据过期、推演范围、制度知识库及风险/排名参数。'],
          ],
          widths=[3.5, 13.0])
    numbered(doc, '先读取已保存配置，按实际容量和数据范围修改；有未保存更改时页面会提示。'
                  '“重置”重新加载已保存值，不是恢复全系统出厂配置。表单校验失败时切换到对应分组修正。')
    numbered(doc, '联网搜索Key为写专用字段，读取只返回apiKeySet。留空表示保留原值，输入新值才覆盖；'
                  '不是空值清除密钥，也不会显示原Key。厂商管理的****掩码规则与此字段不同。')
    numbered(doc, '修改向量连接、类型或维度时会提示既有索引可能不可检索；确认保存后按14.4核对并重建有关知识库。'
                  'inmemory重启丢失向量，不能把重启当作索引迁移。')
    para(doc, '工业风险分值合计100、方案排名权重合计1；HIGH和CRITICAL必须人工审批，不可由参数取消。'
              '低风险自动通知开关只涉及允许的通知动作，不授权模型直接创建或发布业务单据。')
    para(doc, '服务从ai_config加载运行值，nexus.ai提供启动兜底；保存后有本节点缓存更新和Redis多节点重载机制。'
              '本地保存成功不证明所有部署节点已同步，应由运维核对缓存、网络和生效范围。'
              '扩展表前缀和调整安全参数会扩大访问/资源风险，应由获授权管理员审查。')

    h2(doc, '14.13 失败处理与结果复核')
    bullet(doc, '“未找到可用模型”先查启用状态、默认类型、厂商及协议；知识库模式先查列表权限、索引状态和召回测试。'
                '不能用自动模式绕过指定知识库的来源要求。')
    bullet(doc, '生成失败终态对前端使用通用提示，服务端以请求标识记录诊断；不要要求前端暴露原始上游响应或密钥。'
                '排错材料只保留时间、会话/运行ID、错误分类和脱敏现象。')
    bullet(doc, '聊天没有已核实的无限自动重试或断线续传保证；确认历史和工具结果后再手动发送。'
                '涉及MCP、Flow通知或外部写入时，先核查上次副作用，重试可能再次执行而非回滚。')
    bullet(doc, '工业决策过期先重新计算，动作失败按受控权限及幂等策略处理；答案、审批状态、执行结果、ERP草稿和正式生效状态分别核对。'
                '本章依据代码，不代替部署检查、真实业务联调或人工验收。')


def ch22_esign(doc, h1, h2, h3, para, bullet, numbered, table):
    """业务签署台账与现有P2 PDF代码分别说明，不推断部署或法律效力。"""
    h1(doc, '第22章 电子签章与签署留痕')
    para(doc, '本章依据2026-10-12实际管理页、业务调用和ruoyi-common-sign代码。'
              '业务签名记录与已盖章PDF是两类对象：签署留痕不必产生PDF，PDF中出现印章图片也不等于已完成业务审批。'
              '系统不能保证法律有效性，本章不作法律结论或合规认证承诺。')

    h2(doc, '22.1 入口与适用范围')
    para(doc, '当前安装SQL定义系统管理 > 电子签章 > 印章管理 / 用章授权 / 签名记录；'
              '这些定义不证明存量数据库已有菜单，也不证明租户套餐、角色已获管理权限。')
    table(doc,
          ['实际组件路径', '用途 / 主要权限'],
          [
              ['plus-ui/src/views/system/sign/seal/index.vue', '印章查询、创建、修改、启停和删除；system:sign:seal:*。'],
              ['plus-ui/src/views/system/sign/grant/index.vue', '公司章用章授权；system:sign:grant:*。'],
              ['plus-ui/src/views/system/sign/record/index.vue', '只读签名记录和链验签；system:sign:record:list/verify。'],
              ['plus-ui/src/views/erp/so/contract/detail.vue', '销售合同正文上传、签署、下载、文档验签。'],
              ['plus-ui/src/views/erp/pu/contract/detail.vue', '采购合同正文上传、签署、下载、文档验签。'],
          ],
          widths=[9.0, 7.5])
    para(doc, '合同列表的“详情”进入合同正文操作页；不是在电子签章目录中假设存在独立PDF文档管理页。'
              '当前用章授权选项为流程审批APPROVAL、销售合同SO_CONTRACT、采购合同PU_CONTRACT；'
              '签名记录还可查看SOP_VIEW和NCR_DISPOSITION，但枚举存在不证明所有场景都有PDF盖章入口。')

    h2(doc, '22.2 印章管理')
    numbered(doc, '在印章管理点击“新增”，填写名称、个人章/公司章、图片、宽高、有效期和备注。'
                  '个人章必须指定归属用户，公司章不得指定归属用户；类型创建后不能变更。'
                  '图片组件接受单张PNG、页面上限2MB，建议透明底；实际上传还需OSS权限和服务端允许。')
    numbered(doc, '保存后印章为草稿，核对图片与有效期再“启用”；启用状态才能被使用，停用可阻止后续正常用章。'
                  '状态码为0草稿、1启用、2停用、3销毁，销毁为不可恢复终态；不要套用其他模块的0启用状态。')
    numbered(doc, '修改章面时同时提供图片宽高，实际图片变化会增加印章版本并重建预览。'
                  '编辑图片留空表示沿用原章面。删除启用印章会被拒绝，须先停用；删除不等于撤销已经生成的签署记录或PDF。')
    para(doc, '页面使用低清水印预览，历史印章缺预览可由服务补生成，失败应核对对象存储。'
              '此展示策略不是绝对防复制或文件权限的证明；原图、OSS访问与存储凭证仍须专门保护，不能公开分发原章面或私有对象地址。')

    h2(doc, '22.3 公司章授权与撤销')
    numbered(doc, '在用章授权点击“新增授权”，选公司章，选择按用户或按部门授权，设置限定场景、有效期、状态和备注。'
                  '个人章仅归属人本人可用，服务端拒绝为个人章创建授权。场景留空表示不限，应审慎使用。')
    para(doc, '用章时服务端实时检查印章启用及其有效期，再检查有效授权的用户/部门、业务场景、有效期和启用状态。'
              '部门匹配取当前登录人的部门ID，不应承诺自动覆盖所有下级部门。授权状态为1启用、0停用，与印章状态码不同。')
    numbered(doc, '撤销某项授权可修改为停用、缩短有效期或删除授权；需要system:sign:grant:edit/remove。'
                  '随后以被授权账号重新读取可用印章并验证服务端是否拒绝用章，不以旧页面缓存的选项判断撤销成功。')
    bullet(doc, '同一人可能还有用户级、部门级或不限场景的其他有效授权。只撤销一条不一定完全禁止用章；'
                '需逐项核对或在获授权的情况下停用印章。')
    bullet(doc, '“可用印章”清单不是签署许可的最终证明，提交时再次检查授权和身份。'
                '拥有印章管理/合同页面权限不等于公司章授权，登录他人账号或跨租户复制印章/授权ID不可作为替代。')
    bullet(doc, '授权撤销、印章停用/销毁仅影响后续使用条件，不删除历史台账，不追回已下载文档，'
                '也不等于证书吊销、PDF作废或业务合同终止。当前没有通用的“撤销全部历史签名”操作。')

    h2(doc, '22.4 业务签署与身份凭证')
    para(doc, '签署由具体业务动作调用ESignatureService，不是在签名记录页手工新增。'
              '流程审批的plus-ui/src/views/workflow/work/detail.vue在需要签署时使用SealPicker和SignAuthDialog；'
              '服务端审批关卡决定签署要求并固定密码认证，客户端不能以LOGIN_ONLY绕过必要的二级认证。')
    numbered(doc, '在业务页复核单据、版本、签署含义及可用印章后，打开“签署身份验证”输入本人登录密码。'
                  '当前弹窗只提供PASSWORD，没有短信验证码或人脸签署流程；SMS/FACE常量不能写成已开放功能。')
    numbered(doc, '确认后等待后端业务结果。密码/授权错误应保持弹窗纠正后再试，不能跳过凭证继续；'
                  '签章成功、业务动作成功及单据生效分别核对，不承诺跨系统/跨库和对象存储天然原子提交。')
    para(doc, 'PasswordSignCredentialChecker校验本人登录密码，连续错误达到5次时锁定签署密码校验10分钟。'
              '锁定提示不是普通网络失败，不应连续重试、切换账号或改为弱认证绕过。')
    para(doc, '凭证仅由前端认证组件在内存中暂存，开关弹窗会清空，不进入其Pinia、localStorage或URL。'
              '不要记录、截取或传播密码、Token及私钥；显示掩码不替代HTTPS与审计配置检查。')
    bullet(doc, 'SOP已阅确认使用LOGIN_ONLY、不盖章，签署失败可记录告警且不阻断查阅；'
                '这是已阅留痕，不是免密授权，也不意味着SOP正文已产生PDF数字签名。')
    bullet(doc, 'NCR处置属于授权动作，服务端拒绝LOGIN_ONLY且签署失败不得降级为成功。'
                '各业务流程的权限、单据可访问性和失败策略分别核对，不能用SOP的降级规则套用于审批、处置或合同。')

    h2(doc, '22.5 合同PDF正文操作（现有P2代码）')
    para(doc, 'P2 PDF盖章是独立于P1逻辑签名的范围，但当前仓库已有合同详情页、PdfSealPlacer、'
              'EsSignDocumentController、PDF盖章/CMS签名与文档验签代码，不能再写成只有计划。'
              '以下说明已核对的代码入口，不声称本次已完成部署联调或人工端到端验收。')
    numbered(doc, '从销售/采购合同列表的“详情”进入“合同正文（电子签章）”。'
                  '首次选择“上传正文(PDF)”，有正文时选择“上传新版本”；需system:sign:document:upload。'
                  '服务端接受真实PDF且最多10MB，检查文件头、文件名/MIME、大小和可解析性，登记摘要、页数及正文版本。')
    numbered(doc, '核对当前正文版本、文件、页数、原文SM3和签署状态；同一业务键重新上传会产生新版本，'
                  '旧版本不再是当前正文，不能继续签署旧docId。上传成功不代表盖章或完成合同审批。')
    numbered(doc, '点击“签署”，需system:sign:document:sign及system:oss:download读取正文。'
                  '在plus-ui/src/components/sign/PdfSealPlacer.vue选择当前业务可用章，点击页面落位并拖动、缩放、旋转，'
                  '填写签署含义后输入密码确认。页面默认最多20个落位，这是组件保护，不是已核实的服务端全局20章限额。')
    para(doc, '落位坐标为0–1比例、左上原点、pageIndex从0开始；服务端检查位置、授权、登录身份及文档摘要，'
              '业务键从文档台账取出，不能用客户端参数把docId改绑其他业务。'
              '正常追加签署以当前签署后PDF为基准，保留先前签章；并发签署、换版冲突和异常恢复仍需实际联调。')
    numbered(doc, '后端盖章后生成带CMS数字签名的新PDF，保存签署后OSS对象、SM3和追加签名台账，返回signatureId。'
                  '等待成功后刷新正文状态，不把页面中摆放的预览印章当成已保存的签章。失败时先查台账和正文，避免重复签署。')
    numbered(doc, '点击“下载”，需system:sign:document:list与system:oss:download。'
                  '解析/system/sign/document/{docId}/download返回的OSS ID后，经鉴权下载接口取文件；'
                  '已签时取签署后文件，否则取上传原文。不要绕过权限改用公开OSS地址。')
    para(doc, '合同查询/路由权限、文档上传/签署权限、OSS正文下载权限和实际用章授权分别生效。'
              '文档台账匹配和租户访问不是所有合同业务状态/单据访问规则的替代；管理员须按最小权限核对。'
              '数据库事务不能回滚已经写入OSS的字节，异常可能遗留无台账引用的对象，不承诺文件与业务库自动原子回滚。')

    h2(doc, '22.6 签名记录、链验签与文档验签')
    h3(doc, '22.6.1 业务签名台账')
    numbered(doc, '在“签名记录”选择业务类型，按业务主键ID查询（不是单据编号）。'
                  '查看签署人、含义、印章类型、认证方式、时间、IP和链哈希；页面只读，不提供修改/删除历史签名。')
    numbered(doc, '点击单条“验签”或“全链验签”，需system:sign:record:verify。'
                  '结果核对passed、chainPassed、checkedCount、brokenAt及说明，不只看绿色提示。')
    para(doc, '链是同租户全局签名链，跨业务穿插；全链验签检查当前筛选范围每条记录自身哈希及其真实前驱衔接，'
              '不是单据内独立链，也不等于验证每一份PDF。零条记录的“无需校验”不证明业务已签署。'
              '业务留痕可绑定源正文摘要但不产出新PDF，此类记录应做链验签，不能当作含CMS的已签文档。')
    h3(doc, '22.6.2 PDF文档验签')
    numbered(doc, '在合同详情点“验签”，需system:sign:document:verify。'
                  '接口/system/sign/document/signature/{signatureId}/verify校验某次签署产出的文件，不以docId代替signatureId。'
                  '刷新页面后定位对应签名通常还需system:sign:record:list；权限不足应联系管理员，不应仅为验签而新增一次签署。')
    para(doc, '文档验签检查CMS、ByteRange覆盖及PDF修订，并比较台账SM3。'
              '当前结论含OK、MODIFIED_IN_PLACE、REVISION_APPENDED、UNSIGNED_TRAILING_BYTES、TRUNCATED、INVALID_SIGNATURE、NO_SIGNATURE；'
              '除OK外应核查文件和签名，不把“能打开PDF”或“看到印章”当成验签通过。')
    para(doc, '现有PDF签名在每次签署时生成自签证书并内嵌CMS，不是已接入可信CA、可信时间戳或证书吊销服务的证明。'
              '链哈希、摘要一致与CMS技术验签仅说明相应技术检查，不保证真实身份、完整业务授权或法律有效性。')

    h2(doc, '22.7 权限、租户与验收边界')
    bullet(doc, '签名/用章从服务端登录上下文取租户、用户和部门，缺少租户或用户时拒绝；'
                '共享台账走master数据源及租户访问约束。不能由客户端提供其他租户/签署人ID代签，异步调用必须有可信身份上下文。')
    bullet(doc, '管理权限、业务权限、印章所有者/授权、印章/授权有效期和认证凭证分别核对。'
                '注销/凭证失效、授权撤销或正文换版后不应继续用缓存中的章或docId；重新读取并由服务端确认。')
    bullet(doc, '授权/用章状态变化不重写append-only历史签名；业务合同终止、撤销授权、删除印章和PDF技术验签是不同操作。'
                '当前没有证明可使所有已下载PDF远程失效的机制。')
    bullet(doc, 'P1 Task 17人工端到端验收未完成；P2 PDF盖章是独立范围，现有组件和服务实现不能替代其联调与验收。'
                '本次仅检查代码和手册回调，未上传真实文档、签署、写库、调用模型/MCP或验证生产服务。')
    bullet(doc, '上线前须单独验证菜单/套餐/角色、租户隔离、凭证错误、授权撤销、有效期、'
                '合同正文换版、对象存储权限、多方/并发签署、失败恢复和验签异常。不得把源码存在、旧报告或本地测试写成生产验收结论。')
