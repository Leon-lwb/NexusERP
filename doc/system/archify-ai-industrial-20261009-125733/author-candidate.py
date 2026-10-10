import hashlib, json, pathlib, subprocess
ROOT=pathlib.Path(r'E:\Nexus\nexus-erp')
OUT=ROOT/'doc/system/archify-ai-industrial-20261009-125733'
REV='5b66fbeeff5a58028f248c68705e29b0bf968dd1'
AI='ruoyi-modules/ruoyi-ai/src/main/java/com/nexus/ai/'
FLOW='ruoyi-modules/ruoyi-flow/src/main/java/com/nexus/flow/'
ERP='ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/'
COMMON='ruoyi-common/ruoyi-common-ai/src/main/java/com/nexus/common/ai/'
def ref(path,a,b,label): return {'path':path,'line':a,'end_line':b,'label':label}
def a(file,start,end,label): return ref(AI+file,start,end,label)
def f(file,start,end,label): return ref(FLOW+file,start,end,label)
def e(file,start,end,label): return ref(ERP+file,start,end,label)
def c(file,start,end,label): return ref(COMMON+file,start,end,label)
def u(file,start,end,label): return ref('plus-ui/src/'+file,start,end,label)
components=[]
def node(id,t,label,sub,tag,x,y,sources):
    assert 1<=len(sources)<=3
    components.append({'id':id,'type':t,'label':label,'sublabel':sub,'tag':tag,'pos':[x,y],'size':[280,126],'sources':sources})
node('chat_ui','frontend','AI 助手工作台','Vue · /ai/chat/send','发送对话 / 接收用户 SSE',40,90,[u('views/ai/assistant/index.vue',663,678,'助手 · 发送'),u('views/ai/assistant/index.vue',402,429,'助手 · SSE'),u('api/ai/chat.ts',28,46,'聊天 API')])
node('facade','backend','普通 AI · 对话门面','AiServiceFacade · 模式与 Agent 编排','会话归属 / 异步上下文 set→clear',430,90,[a('controller/AiChatController.java',50,56,'聊天入口'),a('service/facade/AiServiceFacade.java',158,205,'Facade · 会话'),a('service/facade/AiServiceFacade.java',237,269,'Facade · 路由')])
node('models','backend','模型工厂与协议策略','ChatModelFactory · 配置驱动客户端','ai_model / ai_provider；有界缓存',820,90,[a('service/chat/ChatModelFactory.java',69,98,'模型工厂'),a('service/chat/provider/ChatServiceProviderFactory.java',66,109,'协议与厂商校验'),c('service/impl/AiModelServiceImpl.java',274,280,'厂商配置装配')])
node('session_sse','backend','会话记忆与 SSE 输出','消息落库 / 按用户分发事件','异步写库恢复 TenantHelper',40,350,[a('service/chat/PersistentChatMemoryStore.java',48,65,'持久化记忆'),a('service/facade/AiServiceFacade.java',751,761,'回复 · 租户写入'),a('service/sse/AiSseMessageHelper.java',287,294,'用户事件发布')])
node('tools','backend','启用工具提供者','内置分类工具 / 独立 MCP Agent','内置 QUERY/ALL；MCP 按会话选择',430,350,[a('mcp/service/ToolProviderFactory.java',38,61,'工具统一入口'),a('tool/BuiltinToolRegistry.java',133,151,'内置启用与分类'),a('mcp/service/McpClientManager.java',143,153,'MCP · 启用选择')])
node('rag','backend','RAG 检索与提示词增强','RetrieverService · 检索 / 可选重排','RAG 路由；知识库可见 / 索引就绪',820,350,[a('service/facade/AiServiceFacade.java',352,370,'RAG · 调用'),a('service/rag/RetrieverService.java',98,116,'检索配置与索引'),a('service/rag/RetrieverService.java',365,378,'提示词增强')])
node('sql','security','ERP 只读查询门','SqlSafetyValidator → ErpQueryService','SELECT / 表前缀 / tenant / LIMIT',430,610,[a('tool/ExecuteSqlQueryTool.java',43,60,'SQL 工具调用'),a('service/query/SqlSafetyValidator.java',94,153,'SELECT / 租户护栏'),a('service/query/ErpQueryService.java',26,60,'ERP 数据源读取')])
node('vectors','database','向量存储 · 二选一','VectorStoreService · 按知识库隔离','默认 inmemory；milvus 需显式配置',820,610,[a('service/rag/VectorStoreService.java',104,109,'知识库优先配置'),a('service/rag/VectorStoreService.java',139,158,'Milvus / 堆内存'),a('config/AiProperties.java',60,66,'默认 inmemory')])
node('decision_trigger','frontend','ERP 业务页 · 决策入口','工单 / 销售订单 / 排程 / 缺料','run 权限；打开决策详情',40,870,[u('views/erp/prd/prdwork/index.vue',219,225,'工单 · 发起分析'),u('views/erp/prd/shortage/index.vue',138,148,'缺料 · 发起分析'),u('api/ai/decision.ts',209,220,'决策 API')])
node('decision_run','backend','工业决策 · 受控入口','AiDecisionController / Orchestrator','已启用 / 登录租户 / run 权限',430,870,[a('controller/AiDecisionController.java',47,59,'决策入口 · 鉴权'),a('service/decision/DeliveryRiskDecisionOrchestrator.java',115,126,'业务权 / 活跃运行'),a('service/decision/DeliveryRiskDecisionOrchestrator.java',230,248,'启用与范围校验')])
node('contracts','backend','common-ai · 公共契约','IndustrialDecisionDataPort / WorkflowPort','DTO / 端口；接口不是业务执行者',820,870,[c('decision/port/IndustrialDecisionDataPort.java',15,50,'数据 / 仿真 / 动作端口'),c('decision/port/DecisionWorkflowPort.java',6,19,'分析 / 执行 Flow 端口')])
node('analysis_flow','backend','ruoyi-flow · 受保护分析图','适配桥接 / FlowEngine / 已注册节点','规范已发布图；节点绑定租户与身份',430,1130,[f('adapter/AiDecisionWorkflowAdapter.java',43,56,'桥接启动分析'),f('adapter/DecisionFlowDefinitionFactory.java',62,101,'受保护分析 DAG'),f('engine/FlowEngineImpl.java',959,976,'工作节点上下文')])
node('erp_data','backend','ERP · 可信数据与推演','工业适配器 / APS / 缺料仿真','DataPort：业务权 / 租户 / 新鲜度',820,1130,[e('ai/adapter/ErpIndustrialDecisionDataAdapter.java',129,165,'ERP 端口实现'),e('ai/adapter/DeliveryContextAssembler.java',213,248,'ERP · 一致性快照'),e('ai/adapter/ErpDecisionApsSimulation.java',239,257,'确定性 APS / 不可行')])
node('decision_persistence','backend','AI · 决策状态持久化','DecisionPersistenceService · 状态机','tenant + 版本 CAS；动作原幂等键',40,1130,[a('service/decision/DecisionPersistenceService.java',169,179,'状态 CAS 写入'),a('service/decision/DecisionPersistenceService.java',347,389,'动作 / 原幂等键'),a('service/decision/DecisionRunStateMachine.java',14,31,'运行状态机')])
node('decision_store','database','common-ai · 决策实体映射','ai_decision_run / evidence / action','快照 / 证据谱系 / 审批与执行留痕',40,1390,[c('domain/entity/AiDecisionRun.java',19,60,'运行与冻结快照'),c('domain/entity/AiDecisionEvidence.java',18,63,'可核验数值 / 谱系'),c('domain/entity/AiDecisionAction.java',19,63,'动作 / 审批 / 执行')])
node('decision_analysis','security','AI · 证据、风险与策略','确定性评分 / 排名 / 动作提案校验','数值需证据；策略不允许跳过审批',430,1390,[a('service/decision/DecisionAnalysisAssemblyService.java',90,138,'风险与证据组装'),a('service/decision/DecisionEvidenceService.java',89,105,'数值事实覆盖'),a('service/decision/DecisionPolicyGuardService.java',89,132,'摘要与动作护栏')])
node('qualitative_summary','backend','工业摘要 · 定性解释','可选 LLM / 制度 RAG；失败用模板','不编 KPI / 证据；不写业务数据',820,1390,[a('service/decision/DecisionSummaryService.java',33,57,'摘要解析 / 重试 / 兜底'),a('config/AiPromptTemplateInitializer.java',28,38,'只读定性摘要契约'),f('adapter/DecisionFlowDefinitionFactory.java',72,85,'可选 RAG / LLM 节点')])
node('approval_ui','frontend','决策详情 · 人工审阅','风险 / 方案 / 证据 / 动作审批','提交 runVersion + snapshotHash',40,1650,[u('views/ai/decision/detail.vue',44,55,'详情 · 审批权限'),u('views/ai/decision/detail.vue',205,218,'版本 / 快照 / 审批'),u('api/ai/decision.ts',225,235,'审批与原动作重试')])
node('decision_approval','security','AI · 工业决策审批','DecisionApprovalService · 非 Flowable','实时权限 / 版本 / 快照；变化→STALE',430,1650,[a('controller/AiDecisionController.java',83,94,'工业审批入口'),a('service/decision/DecisionApprovalService.java',73,108,'审批权限 / 版本 / 新鲜度'),a('service/decision/DecisionApprovalService.java',142,156,'提交后启动执行 Flow')])
node('execution_flow','backend','ruoyi-flow · 审批后执行图','新执行实例 / 注册 execute 节点','受保护图；仅续接已批准运行',820,1650,[f('adapter/AiDecisionWorkflowAdapter.java',59,67,'受控执行桥接'),f('adapter/DecisionFlowDefinitionFactory.java',104,108,'单节点执行图'),f('node/industrial/DecisionExecuteApprovedActionsNodeProvider.java',22,30,'调用执行状态服务')])
node('decision_execution','security','AI · 幂等执行与恢复','DecisionActionExecutionService','已批准 / 业务权 / 新鲜度 / 原键恢复',820,1910,[a('service/decision/DecisionActionExecutionService.java',129,164,'运行 / 动作状态校验'),a('service/decision/DecisionActionExecutionService.java',426,449,'恢复优先 / 冻结重核'),a('service/decision/executor/AbstractErpDraftExecutor.java',14,33,'受控 ERP 执行端口')])
node('erp_write','backend','ERP · 受控业务动作权威','采购认证 writer / 排程方案 executor','采购 DRAFT/v0；排程方案 COMPLETED',430,1910,[e('ai/adapter/ErpIndustrialDecisionDataAdapter.java',168,176,'权限后选择 ERP 执行器'),e('pu/service/PuCertifiedPurchaseWriteService.java',118,156,'冻结合同 / 恢复 / 业务权'),e('ai/adapter/ErpDecisionActionExecutor.java',193,232,'排程方案 · 实际状态')])
connections=[]; traces=[]
def edge(id,src,dst,label,sources,variant='default',**kw):
    connections.append(dict(id=id,**{'from':src,'to':dst,'label':label,'variant':variant},**kw))
    traces.append({'connection':id,'claim':label,'sources':sources})
edge('chat_send','chat_ui','facade','发送对话',[u('api/ai/chat.ts',28,46,'POST send'),a('controller/AiChatController.java',50,56,'Facade 调用')],'emphasis')
edge('model_client','facade','models','获取模型客户端',[a('service/facade/AiServiceFacade.java',277,285,'流式模型'),a('service/facade/AiServiceFacade.java',446,457,'Agent 模型')])
edge('memory_and_events','facade','session_sse','记忆 / 消息 / 事件',[a('service/facade/AiServiceFacade.java',277,292,'记忆与事件'),a('service/facade/AiServiceFacade.java',751,761,'回复落库')])
edge('user_sse','session_sse','chat_ui','用户 SSE',[a('service/sse/AiSseMessageHelper.java',287,294,'用户 SSE 发布'),u('views/ai/assistant/index.vue',402,429,'事件接收'),u('views/ai/assistant/index.vue',873,876,'绑定事件')],'dashed')
edge('enabled_agent_tools','facade','tools','Agent 启用工具',[a('service/facade/AiServiceFacade.java',455,460,'按分类启用'),a('service/facade/AiServiceFacade.java',499,515,'独立 MCP Agent'),a('service/facade/AiServiceFacade.java',855,866,'会话工具选择')])
edge('rag_route','facade','rag','RAG 路由',[a('service/facade/AiServiceFacade.java',263,269,'模式路由'),a('service/facade/AiServiceFacade.java',364,370,'检索增强')],'dashed')
edge('tool_read_query','tools','sql','只读 ERP 查询',[a('tool/ExecuteSqlQueryTool.java',43,60,'净化后执行')])
edge('vector_retrieval','rag','vectors','向量检索',[a('service/rag/RetrieverService.java',255,259,'向量读取'),a('service/rag/VectorStoreService.java',139,158,'存储模式选择')])
edge('decision_start','decision_trigger','decision_run','发起分析',[u('views/erp/prd/prdwork/index.vue',219,225,'启动并打开详情'),a('controller/AiDecisionController.java',47,51,'权限后编排')],'emphasis')
edge('public_port_contracts','decision_run','contracts','依赖公共端口',[a('service/decision/DeliveryRiskDecisionOrchestrator.java',37,50,'注入公共端口')],'dashed')
edge('controlled_analysis_start','decision_run','analysis_flow','校验并启动分析',[a('service/decision/DeliveryRiskDecisionOrchestrator.java',154,163,'校验 / startAnalysis')],'emphasis')
edge('workflow_port_impl','analysis_flow','contracts','实现 WorkflowPort',[f('adapter/AiDecisionWorkflowAdapter.java',31,46,'WorkflowPort 实现')],'dashed')
edge('data_port_impl','erp_data','contracts','实现 DataPort',[e('ai/adapter/ErpIndustrialDecisionDataAdapter.java',30,41,'DataPort 实现')],'dashed')
edge('industrial_source_simulation','analysis_flow','erp_data','节点读取 / 推演',[f('node/industrial/DeliveryContextNodeProvider.java',21,30,'快照读取'),f('node/industrial/ShortageSimulationNodeProvider.java',21,25,'缺料端口'),a('service/decision/ApsWhatIfSimulationService.java',28,43,'APS 端口')],'emphasis')
edge('node_persistence','analysis_flow','decision_persistence','节点留痕',[f('node/industrial/DeliveryContextNodeProvider.java',21,30,'快照留痕'),f('node/industrial/DecisionActionProposalNodeProvider.java',80,95,'证据 / 动作 / 定稿')])
edge('registered_analysis_services','analysis_flow','decision_analysis','节点评分 / 策略',[f('node/industrial/ScenarioRankNodeProvider.java',29,42,'确定性事实准备'),f('node/industrial/DecisionPolicyGuardNodeProvider.java',44,52,'组装与策略'),f('node/industrial/DecisionActionProposalNodeProvider.java',71,81,'动作策略检查')])
edge('qualitative_only','decision_analysis','qualitative_summary','可选定性摘要',[a('service/decision/DecisionAnalysisAssemblyService.java',125,151,'摘要 / 策略 / 回退'),a('service/decision/DecisionSummaryService.java',33,57,'LLM 可选兜底')],'dashed')
edge('decision_entity_io','decision_persistence','decision_store','Mapper 读写',[a('service/decision/DecisionPersistenceService.java',162,179,'运行读取 / 写入'),a('service/decision/DecisionPersistenceService.java',323,334,'证据写入'),a('service/decision/DecisionPersistenceService.java',377,389,'动作写入')])
edge('human_approval','approval_ui','decision_approval','人工审批',[u('views/ai/decision/detail.vue',205,218,'版本快照提交'),a('controller/AiDecisionController.java',83,87,'审批 Service')],'security')
edge('approval_continuation','decision_approval','execution_flow','批准提交后启动',[a('service/decision/DecisionApprovalService.java',142,156,'审批事务后执行')],'security')
edge('execute_registered','execution_flow','decision_execution','执行已批准动作',[f('node/industrial/DecisionExecuteApprovedActionsNodeProvider.java',22,30,'受保护执行节点')],'emphasis')
edge('trusted_business_execution','decision_execution','erp_write','DataPort 受控执行',[a('service/decision/executor/AbstractErpDraftExecutor.java',14,33,'端口执行 / 核验'),e('ai/adapter/ErpIndustrialDecisionDataAdapter.java',168,176,'ERP 权限与选择')],'security')
extra=[
 {'topic':'普通 AI 不等同于工业决策；MCP 不能泛称只读','sources':[a('service/facade/AiServiceFacade.java',499,515,'MCP 独立角色'),a('service/facade/AiServiceFacade.java',531,572,'上下文 / 资料证据 / 清理'),a('service/facade/AiServiceFacade.java',855,866,'按会话启用外部工具')]},
 {'topic':'工作线程身份来自持久化 actor 与实时权限交集；通用 Flow 禁止替换工业图','sources':[f('node/industrial/DecisionNodeSupport.java',66,96,'身份与实时权限'),f('adapter/AiDecisionWorkflowAdapter.java',89,117,'规范图与发布版本'),f('adapter/AiDecisionWorkflowAdapter.java',146,157,'已注册节点配置'),f('adapter/DecisionFlowAccessGuard.java',11,24,'通用入口隔离')]},
 {'topic':'分析定稿重读 ERP 来源，不能把模型说明当证据','sources':[a('service/decision/DeliveryRiskDecisionOrchestrator.java',65,90,'身份 / 新鲜度 / 事实重读'),a('service/decision/DeliveryRiskDecisionOrchestrator.java',91,111,'冻结合同 / 定稿'),a('service/decision/DecisionEvidenceService.java',89,105,'数值谱系')]},
 {'topic':'审批与执行均调用持久化状态服务；图中的 Mapper 箭头以该服务为写入所有者','sources':[a('service/decision/DecisionApprovalService.java',142,149,'审批留痕'),a('service/decision/DecisionActionExecutionService.java',158,188,'执行状态收敛'),a('service/decision/DecisionPersistenceService.java',887,894,'tenant / version CAS')]},
 {'topic':'采购业务 authority 与跨库边界：ERP RR 独立事务、永久 command 与原键恢复','sources':[e('pu/service/PuCertifiedPurchaseWriteService.java',165,199,'RR 与创建'),e('pu/service/PuCertifiedPurchaseWriteService.java',200,222,'来源额度 / 业务写入'),e('ai/adapter/ErpCertifiedPurchaseDecisionAdapter.java',54,82,'真实提交与恢复'),a('service/decision/DecisionActionExecutionService.java',82,109,'原动作重试 / 恢复')]},
 {'topic':'Flowable BPMN 是独立业务审批控制面；本工业审批续接 DecisionWorkflowPort → FlowEngine，不画 Flowable 调用边','sources':[ref('ruoyi-modules/ruoyi-flowable/src/main/java/com/nexus/workflow/controller/WfTaskController.java',95,100,'独立 workflow 审批入口'),ref('ruoyi-modules/ruoyi-flowable/src/main/java/com/nexus/workflow/service/impl/WfTaskServiceImpl.java',320,331,'Flowable TaskService.complete'),a('service/decision/DecisionApprovalService.java',142,156,'实际工业审批续接')]},
 {'topic':'前端发起入口包括四个业务上下文；AI decision 页面负责查询 / 详情 / 审阅，不凭空声称 index 发起运行','sources':[u('views/erp/so/orderheader/index.vue',214,223,'销售订单入口'),u('views/erp/prd/schedule/index.vue',453,467,'排程入口'),u('views/erp/prd/shortage/index.vue',138,148,'缺料入口'),u('views/ai/decision/index.vue',120,125,'查询页面')]}
]
spec={'schema_version':1,'diagram_type':'architecture','meta':{'title':'AI 与工业决策架构图','locale':'zh-CN','quality_profile':'showcase','output':'doc/system/archify-ai-industrial-20261009-125733/ai-industrial.html','repository':{'url':'https://gitee.com/helloworld_wh18/nexus-erp.git','provider':'gitee','revision':REV,'link_mode':'local-only'}},'components':components,'boundaries':[{'kind':'region','label':'普通 AI · 工作台、对话门面与条件启用的工具 / 检索','wraps':[n['id'] for n in components[:8]],'pad':22},{'kind':'region','label':'工业决策 · 公共契约、受保护节点 Flow 与批准后的受控执行','wraps':[n['id'] for n in components[8:]],'pad':22}],'connections':connections,'cards':[
 {'dot':'cyan','title':'三个控制面，不混用','items':['AI 对话：AiServiceFacade 路由普通聊天、Agent 与 RAG。','节点 Flow：注册节点执行受保护的工业分析 / 执行图。','Flowable：独立 /workflow/task BPMN 业务审批；本图不画未证实的工业调用边。']},
 {'dot':'violet','title':'数值与业务动作的权威','items':['ERP 提供来源快照、缺料与 APS 推演；AI 服务确定性评分 / 排名并核对证据。','工业 LLM 仅做定性解释；不得编造 KPI、证据或直接写业务。','模型 / policyKnowledgeId 有效时才加入 LLM / 制度 RAG；失败由服务端模板兜底。']},
 {'dot':'amber','title':'审批、失效与幂等边界','items':['批准要求版本、snapshotHash、实时业务权与冻结来源一致；已证明变化则 STALE。','采购执行先恢复永久 command，再核对新鲜度；未确认写入保留原运行 / 动作断点。','动作名含 draft 不等于每种业务都为 DRAFT：采购 DRAFT/v0；排程方案实际 COMPLETED。']},
 {'dot':'slate','title':'证据范围 · 固定提交，不是上线证明','items':['源码固定于 5b66fbeeff5a58028f248c68705e29b0bf968dd1；来源链接 local-only。','只解释代码职责与有证据的调用 / 接口实现；不以 POM 推断运行时关系。','未连接数据库、未调用业务接口；不声明生产部署、模型端点可用或现场审批验收。']}
]}
# Verify every inclusive range against committed bytes; preserve additional evidence separately.
allrefs=[s for n in components for s in n['sources']]+[s for t in traces+extra for s in t['sources']]
files={}; checks=[]
for r in allrefs:
    path=r['path']
    if path not in files:
        raw=subprocess.check_output(['git','show',f'{REV}:{path}'],cwd=ROOT)
        files[path]={'raw':raw,'lines':raw.decode('utf-8').splitlines()}
    lines=files[path]['lines']; assert 1<=r['line']<=r['end_line']<=len(lines),r
    checks.append({**r,'committedLineCount':len(lines),'rangeSha256':hashlib.sha256(('\n'.join(lines[r['line']-1:r['end_line']])+'\n').encode()).hexdigest()})
evidence={'repository':spec['meta']['repository'],'method':'git show <pinned-revision>:<repo-relative-path>; inclusive ranges checked against committed line counts','nodeSources':[{'component':n['id'],'sources':n['sources']} for n in components],'relationships':traces,'additionalEvidence':extra,'files':[{'path':p,'lineCount':len(v['lines']),'committedBytesSha256':hashlib.sha256(v['raw']).hexdigest()} for p,v in sorted(files.items())],'rangeChecks':checks,'counts':{'components':len(components),'connections':len(connections),'primaryReferences':sum(len(n['sources']) for n in components),'allEvidenceReferences':len(checks),'uniqueSourceFiles':len(files)},'limitations':['Source/configuration structure only; no runtime, DB, live service, production or approval acceptance test.','Supporting helper calls grouped by subsystem; not every internal method call is drawn.','Flowable is deliberately documented as a separate control plane, not a fictitious runtime hop.']}
(OUT/'source-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(OUT/'candidate.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(evidence['counts']))
