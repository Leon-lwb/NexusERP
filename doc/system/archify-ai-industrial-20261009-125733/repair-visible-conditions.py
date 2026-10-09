import json,pathlib
out=pathlib.Path(r'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733')
p=out/'candidate.json'; spec=json.loads(p.read_text(encoding='utf-8'))
visible={
'chat_ui':'/ai/chat/send；按用户接收 SSE',
'facade':'会话归属校验；异步租户 set→clear',
'models':'ai_model / ai_provider；协议校验与缓存',
'session_sse':'消息落库 / 用户 SSE；恢复租户上下文',
'tools':'启用内置 QUERY/ALL；MCP 按会话选择',
'rag':'RAG 路由；知识库可见 / 索引就绪',
'sql':'仅 SELECT；表前缀 / tenant / LIMIT',
'vectors':'默认 inmemory；milvus 按配置选用',
'decision_trigger':'run 权限；工单 / 订单 / 排程 / 缺料',
'decision_run':'功能启用 / run 权限 / 登录租户',
'contracts':'公共 DTO 与端口；自身不执行业务',
'analysis_flow':'规范已发布图；注册节点 / 绑定身份',
'erp_data':'业务权 / tenant / 快照新鲜度',
'decision_persistence':'状态机；tenant + version CAS / 原键',
'decision_analysis':'数值须证据；动作须过审批策略',
'qualitative_summary':'LLM / 制度 RAG 可选；失败模板兜底',
'approval_ui':'runVersion + snapshotHash；人工选择',
'decision_approval':'权限 + 版本 + 快照；变化→STALE',
'execution_flow':'受保护执行节点；仅续接已批准运行',
'decision_execution':'已批准 / 权限 / 新鲜度 / 原键恢复',
'erp_write':'采购 DRAFT/v0；排程方案 COMPLETED'}
changes=[]
for n in spec['components']:
    if n['id'] in visible:
        changes.append({'component':n['id'],'before':n['sublabel'],'after':visible[n['id']]})
        n['sublabel']=visible[n['id']]
    if n['id']=='facade': n['label']='AiServiceFacade · 普通 AI'
    if n['id']=='models': n['label']='ChatModelFactory · 模型工厂'
orig=json.loads((out/'review-3/candidate.snapshot.json').read_text(encoding='utf-8'))
for x,y in zip(spec['components'],orig['components']):
    assert x['id']==y['id'] and x['pos']==y['pos'] and x['size']==y['size'] and x['sources']==y['sources']
assert spec['connections']==orig['connections'] and spec['boundaries']==orig['boundaries']
p.write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(out/'visibility-repair.json').write_text(json.dumps({'reason':'Native READ captures hide node tags. Move sourced behavior-changing conditions into visible sublabels; retain positions, sizes, sources, edges and ownership.','placementChanged':False,'placementRounds':1,'changes':changes,'authoritativeRound':'review-4'},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
