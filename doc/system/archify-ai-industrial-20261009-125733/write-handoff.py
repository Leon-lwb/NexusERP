import hashlib,json,pathlib,subprocess,sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(r'E:\Nexus\nexus-erp'); OUT=ROOT/'doc/system/archify-ai-industrial-20261009-125733'; ED=OUT/'review-4'
REV='5b66fbeeff5a58028f248c68705e29b0bf968dd1'; EDGE=r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
CLI=r'C:\Users\MyPC\.dsh\skills\archify\bin\archify.mjs'
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
write=lambda name,data:(OUT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
spec=read(OUT/'candidate.json'); summary=read(ED/'ai-industrial.finalize-summary.json'); final=read(ED/'ai-industrial.finalize.json')
check=read(OUT/'final-check.json'); browser=read(ED/'ai-industrial.browser-check.json'); visual=read(ED/'ai-industrial.visual-check.json'); delivery=read(OUT/'ai-industrial.delivery.json')
evidence=read(OUT/'source-evidence.json'); supplementary=read(ED/'ai-industrial.supplemental-capture.json')
assert spec['meta']['repository']['revision']==REV and spec['meta']['repository']['link_mode']=='local-only'
assert spec['meta']['output']=='doc/system/archify-ai-industrial-20261009-125733/ai-industrial.html'
assert not any(k in spec['meta'] for k in ['viewBox','visual_preset','subtitle','branding'])
assert all(1<=len(n['sources'])<=3 for n in spec['components'])
assert summary['ok'] and all(s=='pass' for s in summary['gates'].values())
validation=final['stages']['validate']['receipt']['validation']
assert validation['checksPassed']==validation['checkCount']==9 and validation['errors']==validation['warnings']==0
assert check['ok'] and check['provenance']=='current' and all(s['ok'] for s in check['checks'])
assert browser['status']=='pass' and browser['provenance']=='current' and browser['chrome']['executable']==EDGE
assert visual['status']=='pass' and visual['provenance']=='current'
ss=sha(OUT/'candidate.json'); ah=sha(OUT/'ai-industrial.html')
assert ss==summary['specification']['sha256']==final['specification']['sha256']==delivery['specification']['sha256']
assert all(v['artifact']['sha256']==ah for v in [summary,final,check,browser,visual,delivery,supplementary])
assert final['stages']['deliver']['receipt']['evidence']['verified'] and final['stages']['deliver']['receipt']['evidence']['references']==65
source_bytes={}
for record in evidence['files']:
    raw=subprocess.check_output(['git','show',f'{REV}:{record["path"]}'],cwd=ROOT)
    assert hashlib.sha256(raw).hexdigest()==record['committedBytesSha256']
    assert len(raw.decode('utf-8').splitlines())==record['lineCount']
    source_bytes[record['path']]=raw
for r in evidence['rangeChecks']:
    lines=source_bytes[r['path']].decode('utf-8').splitlines()
    assert 1<=r['line']<=r['end_line']<=len(lines)==r['committedLineCount']
    segment=('\n'.join(lines[r['line']-1:r['end_line']])+'\n').encode('utf-8')
    assert hashlib.sha256(segment).hexdigest()==r['rangeSha256']
for s in supplementary['screenshots']:
    assert sha(pathlib.Path(s['path']))==s['sha256']
preview=read(OUT/'preview-provenance.json')
assert preview['preview']['sha256']==preview['preview']['sourceSha256']==sha(OUT/'preview.png')
inspected=[
 (ED/'ai-industrial.visual-check.1440x900.light.png','1440×900 · light · native READ'),
 (ED/'ai-industrial.visual-check.2048x1320.light.png','2048×1320 · light · native READ'),
 (ED/'ai-industrial.visual-check.2048x1320.dark.png','2048×1320 · dark · native READ'),
 (ED/'ai-industrial.supplemental.1440.read.light.full.png','1440 · light · full-page READ (preserved partial capture attempt)'),
 (ED/'ai-industrial.supplemental-2.1440.read.dark.full.png','1440 · dark · full-page READ'),
 (ED/'ai-industrial.supplemental-3.1440.read.light.industrial-plain.png','1440×900 · light · lower industrial area, scrollY=1000, no hover')]
observations=[
 '已用 view_image 检查指定原生 1440 浅色、2048 浅色 / 深色；补看全页与下方工业审批 / 执行区域。',
 '普通 AI 与工业决策分区清晰；审批后执行 Flow 与独立 Flowable BPMN 控制面在要点卡中明确区分。',
 '默认 READ 副标题显示启用、权限、租户、索引、新鲜度、版本与原键恢复条件；无源的部署断言未入图。',
 '浅色 / 深色端点均可辨认；采用正常页面纵向滚动，没有应用样式 / 配置改动。',
 '仍有一处已解析交叉：controlled_analysis_start 与 workflow_port_impl；后者三弯折。机器记录 properCrossings=0；这是非阻断阅读提示，不声称零交叉。',
 '位置调整尝试使 resolvedCrossovers 从 1 变 2，已恢复；没有第二次位置调整。随后只提升已核实条件的可见性。']
manual={'schemaVersion':1,'visualReview':'passed','artifact':{'path':str(OUT/'ai-industrial.html'),'sha256':ah},'authoritativeBrowserEvidence':str(ED/'ai-industrial.browser-check.json'),'nativeCaptureReceipt':str(ED/'ai-industrial.visual-check.json'),'supplementalCaptureReceipt':str(ED/'ai-industrial.supplemental-capture.json'),'inspected':[{'path':str(p),'sha256':sha(p),'scope':scope,'method':'functions.view_image'} for p,scope in inspected],'observations':observations,'correctionRounds':2,'placementRounds':1,'placementRestored':True,'semanticVisibilityRounds':1,'doesNotModifyNativeReceipts':True,'limitations':['This is perceptual review of the static documentation artifact, not ERP / AI runtime acceptance.','Supplemental captures do not replace authoritative Archify browser/check receipts.']}
write('visual-review.json',manual)
checks=[{'name':'candidate hash ↔ latest finalize / delivery','ok':True},{'name':'HTML hash ↔ finalize / delivery / browser / visual / standalone / supplemental','ok':True},{'name':'9/9 showcase with zero errors / warnings','ok':True},{'name':'65 primary source references verified by Archify','ok':True},{'name':'62 committed source files / 131 inclusive ranges reverified with git show','ok':True},{'name':'primary sources max3 per node and bounds valid','ok':True},{'name':'preview byte-identical to authoritative native light capture','ok':True},{'name':'manual inspected capture hashes bound to current artifact','ok':True}]
binding={'schemaVersion':1,'ok':True,'repositoryRevision':REV,'specificationSha256':ss,'artifactSha256':ah,'deliveryReceiptId':browser['deliveryReceiptId'],'checks':checks,'counts':evidence['counts'],'nativeCaptureCount':len(visual['captures']['screenshots']),'supplementalCaptureCount':len(supplementary['screenshots']),'inspectedCaptureCount':len(inspected),'browserViewports':len(browser['containment']['viewports']),'authoritativeFinalizeSummary':str(ED/'ai-industrial.finalize-summary.json'),'standaloneCheck':str(OUT/'final-check.json'),'sourceEvidenceSha256':sha(OUT/'source-evidence.json')}
write('hash-binding.json',binding)
gaps=[{'severity':'advisory','description':'保留 1 处 resolved crossover 与 WorkflowPort 三弯折；唯一位置调整增加交叉后已恢复。','receipt':str(ED/'ai-industrial.finalize-summary.json')},{'severity':'unverified-runtime','description':'未验证生产部署、数据库迁移 / 数据、模型 / Milvus 在线可用性或现场人工审批；本任务禁止且未执行这些操作。'}]
handoff={'schema_version':1,'type':'architecture','title':'AI 与工业决策架构图','locale':'zh-CN','quality_profile':'showcase','repository':spec['meta']['repository'],'output':str(OUT/'ai-industrial.html'),'candidate':str(OUT/'candidate.json'),'preview':str(OUT/'preview.png'),'full_preview':str(OUT/'preview-full.png'),'specification_sha256':ss,'artifact_sha256':ah,'validation':validation,'browser_evidence':'passed','browser':EDGE,'visual_review':'passed','visual_review_receipt':str(OUT/'visual-review.json'),'correction_rounds':2,'placement_rounds':1,'placement_restored':True,'semantic_visibility_rounds':1,'counts':evidence['counts'],'evidence_counts':{'authoritative_native_captures':4,'supplemental_current_captures':4,'visually_inspected_captures':6,'browser_viewports':4},'authoritative_receipts':{'finalize_summary':str(ED/'ai-industrial.finalize-summary.json'),'finalize':str(ED/'ai-industrial.finalize.json'),'delivery':str(OUT/'ai-industrial.delivery.json'),'browser_check':str(ED/'ai-industrial.browser-check.json'),'visual_capture':str(ED/'ai-industrial.visual-check.json'),'standalone_check':str(OUT/'final-check.json'),'hash_binding':str(OUT/'hash-binding.json'),'source_evidence':str(OUT/'source-evidence.json')},'history':['Initial root finalize and browser receipts are history, not current delivery evidence.','review-2 preserves the position-only attempt (2 crossings); review-3 preserves the restored candidate and captures.','review-4 is authoritative after the source-preserving visible-condition edit.'],'remaining_gaps':gaps,'write_scope':str(OUT),'write_paths_receipt':str(OUT/'write-paths.json'),'runtime_changes':False,'application_builds_run':False,'business_endpoints_invoked':False,'databases_connected':False,'committed_or_pushed':False,'subagents_spawned':False}
write('handoff.json',handoff)
base=f"node '{CLI}'"
candidate=str(OUT/'candidate.json'); artifact=str(OUT/'ai-industrial.html')
common=f"{base} finalize architecture '{candidate}' '{artifact}' --repo-root '{ROOT}' --quality showcase"
commands=[
 ('首稿 finalize',common+' --json','exit 0；四道门禁通过；9/9，0 错误、0 警告；1 resolved crossover。'),
 ('一次仅位置调整',common+f" --out-dir '{OUT/'review-2'}' --json",'exit 0；crossovers 增至 2，按约定恢复首稿，不再做位置调整。'),
 ('恢复后 finalize',common+f" --out-dir '{OUT/'review-3'}' --json",'exit 0；恢复原 SHA256；crossovers=1。'),
 ('恢复稿视觉捕获',f"{base} visual-check '{artifact}' --summary --require-provenance --out-dir '{OUT/'review-3'}'",'exit 0；4 张原生截图，保留为历史。'),
 ('可见条件修订后 finalize（权威）',common+f" --out-dir '{ED}' --json",'exit 0；9/9 showcase，0 错误、0 警告；四门禁全 pass。'),
 ('当前视觉捕获（权威）',f"{base} visual-check '{artifact}' --summary --require-provenance --out-dir '{ED}'",'exit 0；containment/readability/viewerChrome/themeStates/captures 全 pass；4 张原生截图。'),
 ('补充本地静态页捕获',f"python '{OUT/'capture-supplemental.py'}'",'最终执行 exit 0；本地 file: HTML、offline Edge，全页 light/dark 与工业区域；首个上下文选择器尝试失败，未影响自动门禁。'),
 ('无 hover 工业区域补看',f"python '{OUT/'capture-industrial.py'}'",'exit 0；scrollY=1000；只生成补充证据，不改 HTML。'),
 ('最终 standalone check',f"{base} check '{artifact}' --require-provenance --json",'exit 0；9/9，provenance=current；结果保存 final-check.json。'),
 ('哈希 / 范围再核对',f"python '{OUT/'write-handoff.py'}'",'exit 0；8 项 hash-binding checks；62 个 committed 文件、131 个闭区间复核。')]
md=f'''# AI 与工业决策架构图 — 文档交接

## 范围与固定来源

- 仓库：`https://gitee.com/helloworld_wh18/nexus-erp.git`。
- 固定 revision：`{REV}`。所有源码证据读取 **`git show revision:path` 的 committed bytes**；未把现有未提交修改当作该提交的证据。
- 图类型 `architecture`；`zh-CN`；`showcase`；源链接 `local-only`。无显式 viewBox / visual_preset / subtitle / branding。
- 仅写入 `{OUT}`。未改应用代码 / 配置、未提交或推送、未启动用户服务、未调用业务端点、未连接或写数据库、未生成代理。现有修改和未跟踪工作未被清理。
- 这是运行时职责与有来源的调用 / 接口实现图，**不是 POM 编译依赖图，也不是已部署拓扑图**。

## 入口与职责

1. 普通 AI：Vue 助手 → `AiServiceFacade`；会话归属、模式路由与异步上下文；`ChatModelFactory` 从模型 / 厂商配置装配和缓存协议客户端。Agent 仅注入已启用的分类内置工具，MCP 按请求 / 会话选择并挂独立角色；不能把所有 MCP 工具泛称只读。
2. RAG：按路由 / 知识库增强提示词，校验可见性和索引就绪；向量按知识库配置选 Milvus 或 InMemory，代码兜底默认 `inmemory`。**不声称现场 Milvus 已安装或在线**。
3. 工业决策：实际发起入口在 ERP 工单 / 销售订单 / 排程 / 缺料页；AI decision 页负责查询 / 详情 / 审阅。控制器按 run/query/approve/execute 权限和服务端登录身份驱动。
4. `common-ai` 定义 DTO、`IndustrialDecisionDataPort`、`DecisionWorkflowPort`，并映射 `ai_decision_run/evidence/action`；接口 / 实体定义不等于执行权威。
5. ERP 工业 adapter 是可信来源快照、APS / 缺料仿真和受控业务动作的权威；AI 服务负责确定性风险 / 排名、证据覆盖、策略、审批和幂等执行状态。
6. `ruoyi-flow` 桥接并校验受保护的、已发布的规范图及注册节点；调度和工作节点绑定租户 / 身份，工作结束清理。通用 Flow 入口不能任意替换工业图。
7. 工业 LLM 只做定性解释，不发明 KPI / 证据或直接写业务；模型 / 制度知识库配置决定可选节点；校验或模型失败回退服务端模板。
8. `DecisionApprovalService` 校验实时权限、runVersion、snapshotHash 与来源新鲜度，批准事务提交后启动**新的受保护执行 Flow**。这不是独立 `/workflow/task` 的 Flowable BPMN 审批。
9. 执行只认批准的冻结动作与实时业务权限；状态写入用 tenant/version CAS。采购先按原键恢复永久 command 再核新鲜度，未确认提交保留断点。源码实际结果：采购 DRAFT/v0；排程方案 COMPLETED，不能仅按 action 名里的 draft 推断状态。

## 当前交付与验收证据

- HTML：`{artifact}`。
- Candidate：`{candidate}`。
- **权威 finalize summary**：`{ED/'ai-industrial.finalize-summary.json'}`；完整回执 `{ED/'ai-industrial.finalize.json'}`。
- `validate / deliver / check / browser-check` 全 pass；**9/9 showcase，0 错误、0 警告**。
- 22 个组件、22 条关系；Archify 验证 **65 条 primary 源引用**，每节点 ≤3；单独证据记录 **131 个闭区间、62 个 committed 源文件**，末行和 SHA256 再次核对。
- Edge：`{EDGE}`。自动浏览器检查 4 个 desktop viewport；当前原生 capture 4 张（1440/2048 × light/dark）。采用正常页面纵向滚动，无横向溢出。
- 已用 `view_image` 检查原生 1440 light、2048 light/dark，并补看完整页和无 hover 的工业审批 / 执行区域；观察范围及图像 SHA256 见 `{OUT/'visual-review.json'}`。视觉结论针对静态文档图，不是业务验收。
- `preview.png` 是权威原生 1440 light 捕获的**逐字节复制**；仅展示顶部 viewport。`preview-full.png` 是补充本地 Edge 全页浅色捕获的水平去边距版本，无缩放 / 重绘；不修改原生证据。复制 / 裁剪证据见 `{OUT/'preview-provenance.json'}`。
- 文档任务未运行 Maven/npm 应用构建、单测或业务集成测试；不把文档门禁写成应用测试通过。

## SHA256 绑定

```text
specification_sha256: {ss}
artifact_sha256:      {ah}
```

`{OUT/'hash-binding.json'}` 核对 candidate、当前 HTML、delivery、权威 finalize、browser、visual、standalone check 及补充捕获的绑定；`{OUT/'handoff.json'}` 是机器交接。

## 有界修订与未验证边界

- 首稿机器通过但建议检查 1 处已解析交叉；唯一一次 positions/sizes-only 尝试令交叉数变 2，恢复并留存 review-2 / review-3。**没有第二轮位置调整**。
- 视觉发现 READ 会隐藏 tag，随后只把有来源的条件提升到可见副标题，保留布局、节点、关系、方向与源证据；当前以 **review-4** 为准。根目录旧 finalize/browser 及 review-2/review-3 是历史，不是当前交付证明；根目录 delivery 是最新 provenance。
- 仍有 1 处 resolved crossover 与 1 条三弯折 WorkflowPort 路由的非阻断阅读提示；`properCrossings=0`，但**不声称零交叉**。
- **未验证生产部署、数据库 / 迁移、模型 / Milvus 在线可用性、真实业务结果或现场人工审批**。这类活动被本任务禁止，未执行。源码中存在的保护机制不等于生产运行或验收结论。

## 精确命令与结果

工作目录：`{ROOT}`。每个 CLI 浏览器 exec 会话显式设置：

```powershell
$env:ARCHIFY_CHROME = '{EDGE}'
```
'''
for title,cmd,result in commands:
    md+=f'\n### {title}\n\n```powershell\n{cmd}\n```\n\n{result}\n'
md+='\n源码证据核验方式：`git show 5b66fbeeff5a58028f248c68705e29b0bf968dd1:<repo-relative-source-path>`；完整路径 / 范围 / committed 文件与区间散列保存在 source-evidence.json。\n'
# Every persistent output path is enumerated, including this script and index itself.
paths=sorted({str(p) for p in OUT.rglob('*') if p.is_file()}|{str(OUT/'README.md'),str(OUT/'write-paths.json')})
md+='\n## 本任务写入路径（绝对路径）\n\n所有文件均位于唯一授权目录。机器列表为 `'+str(OUT/'write-paths.json')+'`。\n\n'
md+='\n'.join('- `'+p+'`' for p in paths)+'\n'
(OUT/'README.md').write_text(md,encoding='utf-8')
paths=sorted(str(p) for p in OUT.rglob('*') if p.is_file())
if str(OUT/'write-paths.json') not in paths: paths=sorted(paths+[str(OUT/'write-paths.json')])
write('write-paths.json',{'writeScope':str(OUT),'fileCount':len(paths),'directoriesCreated':[str(OUT),str(OUT/'review-2'),str(OUT/'review-3'),str(OUT/'review-4')],'files':paths,'otherRepositoryPathsWrittenByThisTask':[]})
assert set(read(OUT/'write-paths.json')['files'])=={str(p) for p in OUT.rglob('*') if p.is_file()}
assert sha(OUT/'candidate.json')==ss and sha(OUT/'ai-industrial.html')==ah
print(json.dumps({'ok':True,'validation':validation,'sourceCounts':evidence['counts'],'hashBindingChecks':len(checks),'filesWritten':len(paths),'authoritativeFinalizeSummary':str(ED/'ai-industrial.finalize-summary.json'),'handoff':str(OUT/'handoff.json')},ensure_ascii=False))

