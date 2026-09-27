import {spawn} from 'node:child_process';
import {readFile,writeFile,unlink,mkdir} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {fileURLToPath} from 'node:url';
import {createServer} from 'node:http';
const T=tmpdir(),S='`nexus_ai_decision_control_20260927_sys`',E='`nexus_ai_decision_control_20260927_erp`';
const directory=join(T,'nexus-control-faults'),journal=[];
const wait=ms=>new Promise(r=>setTimeout(r,ms));
const assert=(value,name)=>{if(!value)throw Error(name)};
async function db(sql){const child=spawn('python',[fileURLToPath(new URL('./control-db.py',import.meta.url))],{stdio:['pipe','pipe','pipe'],windowsHide:true});let out='';child.stdout.on('data',s=>out+=s);child.stdin.end(JSON.stringify({sql}));const rc=await new Promise(r=>child.on('exit',r));if(rc!==0)throw Error('ISOLATED_DB_FAILED');return JSON.parse(out);}
const row=async sql=>JSON.parse((await db(sql))[0]);
const n=async sql=>Number((await db(sql))[0]);
async function checkpoint(name,body){try{const evidence=await body();journal.push({name,passed:true,evidence,time:new Date().toISOString()});console.log('CONTROL_PASS '+name);}catch(e){journal.push({name,passed:false,error:/^[A-Z0-9_]+$/.test(e.message)?e.message:'TEST_FAILED',time:new Date().toISOString()});console.log('CONTROL_FAIL '+name+' '+journal.at(-1).error);}await writeFile(join(T,'nexus-control-http-journal.json'),JSON.stringify(journal,null,2));}
export async function run({apiAs}){
 const api=(p,b,m)=>apiAs('admin',p,b,m);
 const detail=async id=>{const r=await api('/ai/decision/run/'+id);assert(r.code===200,'DETAIL_REJECTED');return r.data;};
 const latest=async ordinal=>(await db(`SELECT CAST(id AS CHAR) FROM ${S}.\`ai_decision_run\` WHERE biz_id=${9007199254745000n+BigInt(ordinal)} AND tenant_id='000000' ORDER BY id DESC LIMIT 1`))[0];
 const approval=(d,ids=d.actions.map(a=>a.id))=>({runVersion:d.run.version,approvedActionIds:ids,rejectedActionIds:d.actions.filter(a=>!ids.includes(a.id)).map(a=>a.id),expectedSnapshotHash:d.run.inputSnapshotHash,comment:'CONTROL explicit authorization'});
 const until=async(id,states,timeout=95000)=>{const end=Date.now()+timeout;let d;while(Date.now()<end){d=await detail(id);if(states.includes(d.run.status))return d;await wait(500);}throw Error('STATE_TIMEOUT');};
 const fresh=async ord=>{const r=await api('/ai/decision/delivery-risk/run',{bizType:'WORK_ORDER',bizId:String(9007199254745000n+BigInt(ord)),horizonDays:30,forceRestart:true});assert(r.code===200,'ANALYSIS_START_REJECTED');return until(String(r.data.runId),['WAITING_APPROVAL']);};
 const drafts=()=>n(`SELECT (SELECT COUNT(*) FROM ${E}.\`pu_purchase_requisition\` WHERE source_type='AI_DECISION')+(SELECT COUNT(*) FROM ${E}.\`prd_schedule_plan\` WHERE decision_run_id IS NOT NULL)`);
 const compact=d=>({runId:d.run.id,status:d.run.status,executionFlowId:d.run.executionFlowInstanceId,actions:d.actions.map(a=>({id:a.id,type:a.actionType,approval:a.approvalStatus,execution:a.executionStatus,resultBizId:a.resultBizId,errorCode:a.lastErrorCode,compensationStatus:a.compensationStatus}))});
 await mkdir(directory,{recursive:true});await unlink(join(directory,'fault.txt')).catch(()=>{});
 // 在任何 CONTROL 数据变更前，冻结已完成的四十例回放标识。
 await checkpoint('BASELINE_CAPTURE',async()=>{
  const rows=await db(`SELECT JSON_OBJECT('id',CAST(id AS CHAR),'bizId',CAST(biz_id AS CHAR),'status',status,'mode',summary_mode,'risk',risk_level) FROM ${S}.\`ai_decision_run\` WHERE tenant_id='000000' ORDER BY id`);
  assert(rows.length===40,'BASELINE_COUNT');await writeFile(join(T,'nexus-control-baseline-runs.json'),JSON.stringify(rows.map(JSON.parse),null,2));return {count:rows.length};
 });
 await checkpoint('CONTROL001_VERSION_AND_HASH',async()=>{
  const d=await detail(await latest(6)),before=await drafts(),body=approval(d);
  const version=await api('/ai/decision/run/'+d.run.id+'/approve',{...body,runVersion:Number(body.runVersion)+1});
  const hash=await api('/ai/decision/run/'+d.run.id+'/approve',{...body,expectedSnapshotHash:'0'.repeat(64)});
  const after=await detail(d.run.id);assert(version.code!==200&&hash.code!==200,'TAMPER_ACCEPTED');assert(after.run.status==='WAITING_APPROVAL'&&await drafts()===before,'TAMPER_SIDE_EFFECT');return {runId:d.run.id,versionCode:version.code,hashCode:hash.code};
 });
 await checkpoint('CONTROL001_SOURCE_STALE',async()=>{
  const d=await detail(await latest(6)),before=await drafts();
  const original=await row(`SELECT JSON_OBJECT('qty',CAST(planned_qty AS CHAR),'updated',DATE_FORMAT(update_time,'%Y-%m-%d %H:%i:%s')) FROM ${E}.\`prd_work_order\` WHERE wo_id=9007199254745006`);
  try{await db(`UPDATE ${E}.\`prd_work_order\` SET planned_qty=planned_qty+1 WHERE wo_id=9007199254745006`);
   const r=await api('/ai/decision/run/'+d.run.id+'/approve',approval(d)),after=await detail(d.run.id);
   assert(r.code!==200&&after.run.status==='STALE','STALE_NOT_ENFORCED');assert(!after.run.executionFlowInstanceId&&await drafts()===before,'STALE_EXECUTED');return {code:r.code,...compact(after)};
  }finally{await db(`UPDATE ${E}.\`prd_work_order\` SET planned_qty=${original.qty},update_time='${original.updated}' WHERE wo_id=9007199254745006`);}
 });
 await checkpoint('CONTROL002_READ_ONLY_USER',async()=>{
  const d=await detail(await latest(8));const own=await apiAs('control_reader','/ai/decision/run/'+d.run.id);
  const approve=await apiAs('control_reader','/ai/decision/run/'+d.run.id+'/approve',approval(d));
  const retry=await apiAs('control_reader','/ai/decision/action/'+d.actions[0].id+'/retry',{});
  assert(own.code===200,'READER_QUERY_NOT_VALID');assert(approve.code===403&&retry.code===403,'READER_NOT_403');return {runId:d.run.id,queryCode:own.code,approveCode:approve.code,retryCode:retry.code};
 });
 await checkpoint('CONTROL002_TENANT_BOUNDARY',async()=>{
  const d=await detail(await latest(8));
  // 使用显式查询夹具，不虚构租户 B 的分析或 CONTROL 执行。
  const cols=(await db(`SHOW COLUMNS FROM ${E}.\`prd_work_order\``)).map(x=>x.split('\t')[0]);
  const select=cols.map(c=>c==='wo_id'?'9100002':c==='tenant_id'?"'910000'":c==='wo_no'?"'CONTROL-B-WO'":'`'+c+'`').join(',');
  await db(`INSERT INTO ${E}.\`prd_work_order\`(${cols.map(c=>'`'+c+'`').join(',')}) SELECT ${select} FROM ${E}.\`prd_work_order\` WHERE wo_id=9007199254745001`);
  const rc=(await db(`SHOW COLUMNS FROM ${S}.\`ai_decision_run\``)).map(x=>x.split('\t')[0]);
  const rs=rc.map(c=>c==='id'?'9100001':c==='tenant_id'?"'910000'":c==='biz_id'?'9100002':c==='biz_code'?"'CONTROL-B-WO'":'`'+c+'`').join(',');
  await db(`INSERT INTO ${S}.\`ai_decision_run\`(${rc.map(c=>'`'+c+'`').join(',')}) SELECT ${rs} FROM ${S}.\`ai_decision_run\` WHERE biz_id=9007199254745001 LIMIT 1`);
  const own=await apiAs('control_tenant_b','/ai/decision/run/9100001');
  const foreign=await apiAs('control_tenant_b','/ai/decision/run/'+d.run.id);
  const approve=await apiAs('control_tenant_b','/ai/decision/run/'+d.run.id+'/approve',approval(d));
  const retry=await apiAs('control_tenant_b','/ai/decision/action/'+d.actions[0].id+'/retry',{});
  assert(own.code===200,'TENANT_OWN_QUERY_FAILED');assert(foreign.code!==200&&approve.code!==200&&retry.code!==200,'CROSS_TENANT_ACCEPTED');
  assert((await detail(d.run.id)).run.status==='WAITING_APPROVAL','TENANT_SIDE_EFFECT');
  return {ownQueryFixtureId:'9100001',ownCode:own.code,foreignCodes:[foreign.code,approve.code,retry.code],targetId:d.run.id};
 });
 await checkpoint('CONTROL002_FORBIDDEN_ACTION',async()=>{
  const d=await detail(await latest(7)),before=await drafts(),id=d.actions[0].id;
  await db(`UPDATE ${S}.\`ai_decision_action\` SET action_type='PUBLISH_SCHEDULE_PLAN' WHERE id=${id}`);
  const r=await api('/ai/decision/run/'+d.run.id+'/approve',approval(d));await wait(2000);const after=await detail(d.run.id);assert(r.code!==200&&after.run.status==='WAITING_APPROVAL'&&!after.run.executionFlowInstanceId,'FORBIDDEN_APPROVAL_ACCEPTED');
  assert(await drafts()===before,'FORBIDDEN_CREATED_DRAFT');assert(after.actions.every(a=>a.executionStatus!=='SUCCEEDED'),'FORBIDDEN_EXECUTED');
  return {injection:'system-copy action type changed, no execution bypass',approveCode:r.code,...compact(after)};
 });
 await checkpoint('CONTROL004_CONCURRENT_APPROVAL_AND_IDEMPOTENCY',async()=>{
  const d=await detail(await latest(8));const body=approval(d);
  const replies=await Promise.all([api('/ai/decision/run/'+d.run.id+'/approve',body),api('/ai/decision/run/'+d.run.id+'/approve',body)]);
  assert(replies.filter(r=>r.code===200).length===1,'DUPLICATE_APPROVAL_ACCEPTED');
  const after=await until(d.run.id,['SUCCEEDED']);const action=after.actions.find(a=>a.actionType==='CREATE_PURCHASE_REQUISITION_DRAFT');
  const count=await n(`SELECT COUNT(*) FROM ${E}.\`pu_purchase_requisition\` WHERE decision_action_id=${action.id} AND status='DRAFT' AND del_flag='0'`);
  const flowCount=await n(`SELECT COUNT(*) FROM ${S}.\`ai_flow_instance\` WHERE id=${after.run.executionFlowInstanceId}`);
  const retry=await api('/ai/decision/action/'+action.id+'/retry',{});
  assert(count===1&&flowCount===1&&retry.code===200,'IDEMPOTENCY_FAILED');
  assert(await n(`SELECT COUNT(*) FROM ${E}.\`pu_purchase_requisition\` WHERE decision_action_id=${action.id}`)===1,'RETRY_DUPLICATED');
  return {replyCodes:replies.map(r=>r.code),draftCount:count,flowCount,retryCode:retry.code,...compact(after)};
 });
 await checkpoint('CONTROL005_PARTIAL_SUCCESS',async()=>{
  const d=await detail(await latest(33)),purchase=d.actions.find(a=>a.actionType==='CREATE_PURCHASE_REQUISITION_DRAFT'),schedule=d.actions.find(a=>a.actionType==='CREATE_SCHEDULE_PLAN_DRAFT');assert(purchase&&schedule,'TWO_DRAFT_ACTIONS_MISSING');
  await writeFile(join(directory,'fault.txt'),schedule.id+':EXECUTE_FAIL');
  try{const r=await api('/ai/decision/run/'+d.run.id+'/approve',approval(d));assert(r.code===200,'PARTIAL_APPROVAL_REJECTED');const after=await until(d.run.id,['PARTIAL_SUCCESS']);
   assert(await n(`SELECT COUNT(*) FROM ${E}.\`pu_purchase_requisition\` WHERE decision_action_id=${purchase.id} AND status='DRAFT' AND del_flag='0'`)===1,'PARTIAL_DRAFT_MISSING');
   assert(await n(`SELECT COUNT(*) FROM ${E}.\`prd_schedule_plan\` WHERE decision_action_id=${schedule.id}`)===0,'FAILED_SCHEDULE_WROTE');
   const retry=await api('/ai/decision/action/'+schedule.id+'/retry',{});assert(retry.code!==200,'TERMINAL_FAILURE_RETRIED');return {retryCode:retry.code,...compact(after)};
  }finally{await unlink(join(directory,'fault.txt')).catch(()=>{});}
 });
 for(const [name,ordinal,mutation] of [['CONTROL005_REAL_COMPENSATION',9,null],['CONTROL005_NON_DRAFT_NOT_DELETED',10,'status'],['CONTROL005_VERSION_NOT_DELETED',11,'version']]){
  await checkpoint(name,async()=>{
   const d=await detail(await latest(ordinal)),action=d.actions.find(a=>a.actionType==='CREATE_PURCHASE_REQUISITION_DRAFT');assert(action,'PURCHASE_ACTION_MISSING');
   await writeFile(join(directory,'fault.txt'),action.id+':'+(mutation?'VERIFY_PAUSE_FAIL':'VERIFY_FAIL'));
   try{const r=await api('/ai/decision/run/'+d.run.id+'/approve',approval(d,[action.id]));assert(r.code===200,'VERIFY_APPROVAL_REJECTED');
    let bizId;if(mutation){const end=Date.now()+20000;while(Date.now()<end){try{bizId=(await readFile(join(directory,action.id+'.observed'),'utf8')).trim();break;}catch{await wait(100);}}assert(bizId,'REAL_VERIFY_NOT_OBSERVED');
     await db(`UPDATE ${E}.\`pu_purchase_requisition\` SET ${mutation==='status'?"status='APPROVING'":"version=version+1"} WHERE requisition_id=${bizId}`);await writeFile(join(directory,action.id+'.release'),'release');
    }
    const after=await until(d.run.id,[mutation?'FAILED':'ROLLED_BACK']);bizId??=(await readFile(join(directory,action.id+'.observed'),'utf8')).trim();
    const rowState=await row(`SELECT JSON_OBJECT('status',status,'version',version,'deleted',del_flag) FROM ${E}.\`pu_purchase_requisition\` WHERE requisition_id=${bizId}`);
    assert(mutation?rowState.deleted==='0':rowState.deleted==='1','COMPENSATION_GATE_FAILED');
    return {postRealVerificationInjection:true,mutation:mutation||'none',businessId:bizId,businessRow:rowState,...compact(after)};
   }finally{await unlink(join(directory,'fault.txt')).catch(()=>{});}
  });
 }
 await checkpoint('CONTROL003_MODEL_UNAVAILABLE',async()=>{
  const fake='CONTROL_ONLY_FAKE_NOT_A_REAL_KEY';let received=0,unexpected=false;
  const stub=createServer((req,res)=>{received++;if(req.headers.authorization!=='Bearer '+fake)unexpected=true;req.resume();res.writeHead(401,{'Content-Type':'application/json'});res.end(JSON.stringify({error:{message:'CONTROL unavailable',type:'authentication_error'}}));});
  await new Promise((resolve,reject)=>{stub.on('error',reject);stub.listen(18089,'127.0.0.1',resolve)});
  const before=await drafts();
  try{
   const p=await api('/ai/provider',{id:'9100401',name:'CONTROL_401_TEST',apiHost:'http://127.0.0.1:18089/v1',apiKey:fake,providerType:'OPENAI',status:'0'});assert(p.code===200,'DUMMY_PROVIDER_REJECTED');
   const m=await api('/ai/model',{id:'9100402',providerId:'9100401',modelName:'control-unavailable',displayName:'CONTROL unavailable',modelType:'TEXT',inputCapabilities:'TEXT',outputCapabilities:'TEXT',apiProtocol:'OPENAI_CHAT_COMPLETIONS',contextWindow:32768,maxTokens:1024,temperature:0.1,isDefault:'N',status:'0'});assert(m.code===200,'DUMMY_MODEL_REJECTED');
   const set=await api('/ai/model/setDefault/9100402',{},'PUT');assert(set.code===200,'DUMMY_DEFAULT_REJECTED');
   const after=await fresh(12);assert(after.run.summaryMode==='TEMPLATE_FALLBACK','MODEL_FAILURE_NOT_FALLBACK');assert(after.run.status==='WAITING_APPROVAL','MODEL_CHANGED_DETERMINISTIC_STATE');
   assert(received>0&&!unexpected,'STUB_NOT_FAKE_ONLY');assert(!JSON.stringify(after).includes(fake)&&await drafts()===before,'MODEL_FAILURE_LEAK_OR_WRITE');
   return {stubHttp:401,requestCount:received,fakeCredentialsOnly:!unexpected,summaryMode:after.run.summaryMode,...compact(after)};
  }finally{await api('/ai/model/setDefault/2103871286244564993',{},'PUT');await new Promise(r=>stub.close(r));}
 });
 await checkpoint('STOCK_UNCHANGED',async()=>{
  const source=JSON.parse(await readFile('D:/workspace/nexus-erp/doc/ai/benchmark/delivery-risk/control/provision-evidence.json','utf8'));
  const stocks=await db(`CHECKSUM TABLE ${E}.\`wm_material_stock\``);const expected=source.sourceChecksumsBefore.split(/\r?\n/).find(x=>x.includes('.wm_material_stock')).split('\t')[1];
  assert(stocks[0].split('\t')[1]===expected,'STOCK_CHANGED');return {checksum:expected};
 });
 console.log('CONTROL_MATRIX_FINISHED '+JSON.stringify({passed:journal.filter(x=>x.passed).length,failed:journal.filter(x=>!x.passed).length}));
}
