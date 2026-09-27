import {createRequire} from 'node:module';
import {spawn} from 'node:child_process';
import {createCipheriv,createDecipheriv,randomBytes} from 'node:crypto';
import {readFile,writeFile,unlink,appendFile} from 'node:fs/promises';
import {join,dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {tmpdir} from 'node:os';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../../../..').replaceAll('\\','/');const require=createRequire(root+'/plus-ui/package.json');const JSEncrypt=require('jsencrypt');
const envText=await readFile(root+'/plus-ui/.env.development','utf8');
function env(name){const m=envText.match(new RegExp('^'+name+'\\s*=\\s*(.+)$','m'));if(!m)throw Error('Missing frontend auth setting');return m[1].trim().replace(/^['"]|['"]$/g,'');}
const client=env('VITE_APP_CLIENT_ID');const publicRsa=new JSEncrypt();publicRsa.setPublicKey(env('VITE_APP_RSA_PUBLIC_KEY'));const privateRsa=new JSEncrypt();privateRsa.setPrivateKey(env('VITE_APP_RSA_PRIVATE_KEY'));
const loginSource=await readFile(root+'/plus-ui/src/views/login.vue','utf8');const defaultUsername=loginSource.match(/username:\s*'([^']+)'/)?.[1];const password=loginSource.match(/password:\s*'([^']+)'/)?.[1];if(!defaultUsername||!password)throw Error('No known local demo login');
const control=join(tmpdir(),'nexus-control-live-command.txt');const captchaPath=join(tmpdir(),'nexus-control-live-captcha.png');let token,uuid,stop=false;
const wait=ms=>new Promise(r=>setTimeout(r,ms));
async function command(){while(true){try{const msg=(await readFile(control,'utf8')).trim();await unlink(control);return msg;}catch{await wait(350);}}}
async function api(base,path,body,encrypted=false,methodOverride){const headers={'Content-Type':'application/json;charset=utf-8',clientid:client};if(token)headers.Authorization='Bearer '+token;let data=body==null?undefined:JSON.stringify(body);if(encrypted){const key=randomBytes(24).toString('base64');const cipher=createCipheriv('aes-256-ecb',Buffer.from(key),null);headers['encrypt-key']=publicRsa.encrypt(Buffer.from(key).toString('base64'));data=Buffer.concat([cipher.update(data,'utf8'),cipher.final()]).toString('base64');}
 const r=await fetch(base+path,{method:methodOverride||(body==null?'GET':'POST'),headers,body:data,signal:AbortSignal.timeout(75000)});let text=await r.text();const eh=r.headers.get('encrypt-key');if(eh){const rsa=privateRsa.decrypt(eh);if(!rsa)throw Error('API_RESPONSE_DECRYPT_FAILED');const dec=createDecipheriv('aes-256-ecb',Buffer.from(rsa,'base64'),null);text=Buffer.concat([dec.update(Buffer.from(text,'base64')),dec.final()]).toString('utf8');}try{const parsed=JSON.parse(text);await appendFile(join(tmpdir(),'nexus-control-http-transport.ndjson'),JSON.stringify({time:new Date().toISOString(),path,method:methodOverride||(body==null?'GET':'POST'),httpStatus:r.status,code:parsed.code})+'\n');return parsed;}catch{throw Error('API_RESPONSE_NOT_JSON');}}
async function captcha(){const r=await api('http://127.0.0.1:8081','/auth/code');if(r.code!==200)throw Error('CAPTCHA_UNAVAILABLE');if(r.data.captchaEnabled===false){uuid=undefined;await writeFile(control,'0');console.log('CAPTCHA_DISABLED_TARGET_LOGIN');return;}uuid=r.data.uuid;await writeFile(captchaPath,Buffer.from(r.data.img,'base64'));console.log('CAPTCHA_READY '+captchaPath);}
function classification(message){if(/401|authenticat|api.?key|凭据|认证/i.test(message||''))return 'AUTH_OR_CREDENTIAL_REJECTED';if(/timeout|timed.?out|超时|Timeout/i.test(message||''))return 'MODEL_TIMEOUT';if(/400|bad.?request|token|parameter|参数|response.*content|content.*response|empty.*content/i.test(message||''))return 'MODEL_RESPONSE_OR_PARAMETERS';return 'OTHER_FAILURE';}

let loginName=defaultUsername,loginTenant='000000',pendingLogin=true;const tokens=new Map();
const base='http://127.0.0.1:8081';
try{
 await captcha();
 while(!stop){const cmd=await command();if(cmd==='quit')break;
  try {
   if(pendingLogin){if(!/^\d{1,3}$/.test(cmd)){console.log('CAPTCHA_REQUIRED');continue;}
    const r=await api(base,'/auth/login',{tenantId:loginTenant,username:loginName,password,clientId:client,grantType:'password',code:cmd,uuid},true);
    if(r.code!==200){console.log('LOGIN_FAILED '+JSON.stringify({code:r.code}));await captcha();continue;}
    token=r.data.access_token;tokens.set(loginName,token);pendingLogin=false;console.log('LOGIN_OK '+loginName+' TOKEN_IN_MEMORY');continue;
   }
   if(cmd.startsWith('login ')){loginName=cmd.split(' ')[1];loginTenant=loginName==='control_tenant_b'?'910000':'000000';
    if(tokens.has(loginName)){token=tokens.get(loginName);console.log('LOGIN_REUSED '+loginName);continue;}
    token=null;pendingLogin=true;await captcha();continue;}
 if(cmd==='full'){
  const cases=(await readFile(root+'/doc/ai/benchmark/delivery-risk/cases.jsonl','utf8')).trim().split(/\r?\n/).map(JSON.parse);
  const expected=(await readFile(root+'/doc/ai/benchmark/delivery-risk/expected-results.jsonl','utf8')).trim().split(/\r?\n/).map(JSON.parse);
  for(let shard=1;shard<=4;shard++){const selected=cases.filter((_,i)=>i%4===shard-1);const ids=new Set(selected.map(x=>x.caseId));const prefix=join(tmpdir(),'nexus-control-shard'+shard);await writeFile(prefix+'-cases.jsonl',selected.map(JSON.stringify).join('\n')+'\n');await writeFile(prefix+'-expected.jsonl',expected.filter(x=>ids.has(x.caseId)).map(JSON.stringify).join('\n')+'\n');}
  const startedAt=new Date().toISOString();console.log('FINAL_FOUR_SHARD_REPLAY_START '+JSON.stringify({startedAt,concurrency:4,timeoutSeconds:90,pollMs:500}));
  const outcomes=await Promise.all([1,2,3,4].map(async shard=>{
   const prefix='C:/Users/admin/AppData/Local/Temp/nexus-control-shard'+shard;
   const output=prefix+'-raw.md';
   const child=spawn('pwsh',['-NoProfile','-File',root+'/doc/ai/benchmark/delivery-risk/scripts/run-benchmark.ps1','-BaseUrl','http://127.0.0.1:8081','-ClientId',client,'-Cases',prefix+'-cases.jsonl','-Expected',prefix+'-expected.jsonl','-Output',output,'-TimeoutSeconds','90','-PollIntervalMilliseconds','500'],{cwd:root,env:{...process.env,AI_DECISION_BENCHMARK_TOKEN:token},stdio:['ignore','pipe','pipe'],windowsHide:true});
   const redact=data=>String(data).replace(/eyJ[A-Za-z0-9._-]{20,}/g,'[JWT REDACTED]').replace(/Bearer\s+[A-Za-z0-9._-]+/g,'Bearer [REDACTED]');
   child.stdout.on('data',data=>process.stdout.write('[SHARD '+shard+'] '+redact(data)));child.stderr.on('data',data=>process.stderr.write('[SHARD '+shard+'] '+redact(data)));
   const rc=await new Promise((resolve,reject)=>{child.on('close',resolve);child.on('error',reject)});
   console.log('SHARD_FINISHED '+JSON.stringify({shard,rc,output}));return {shard,rc,output};
  }));
  console.log('FINAL_FOUR_SHARD_REPLAY_FINISHED '+JSON.stringify({startedAt,finishedAt:new Date().toISOString(),outcomes}));continue;
 }

   if(cmd==='retry34'){const child=spawn('pwsh',['-NoProfile','-File',root+'/doc/ai/benchmark/delivery-risk/scripts/run-benchmark.ps1','-BaseUrl',base,'-ClientId',client,'-Cases',join(tmpdir(),'nexus-control-retry34-cases.jsonl'),'-Expected',join(tmpdir(),'nexus-control-retry34-expected-results.jsonl'),'-Output',join(tmpdir(),'nexus-control-retry34-raw.md'),'-TimeoutSeconds','90','-PollIntervalMilliseconds','500'],{cwd:root,env:{...process.env,AI_DECISION_BENCHMARK_TOKEN:tokens.get(defaultUsername)},stdio:['ignore','pipe','pipe'],windowsHide:true});child.stdout.on('data',d=>process.stdout.write(d));child.stderr.on('data',d=>process.stderr.write(d));await new Promise(r=>child.on('exit',r));console.log('RETRY34_FINISHED');continue;}
   if(cmd==='matrix'){const m=await import('./control-http-matrix.mjs');await m.run({apiAs:async(name,path,body,method)=>{token=tokens.get(name);if(!token)throw Error('SESSION_MISSING');return api(base,path,body,false,method);}});continue;}
   if(cmd.startsWith('{')){const op=JSON.parse(cmd);if(op.kind==='request'){
    if(!/^\/(?:ai\/decision|erp\/prd\/work|system\/user\/getInfo)/.test(op.path))throw Error('PATH_NOT_ALLOWED');
    const r=await api(base,op.path,op.body);if(!/^[A-Za-z0-9_-]+$/.test(op.name))throw Error('NAME_INVALID');
    await writeFile(join(tmpdir(),'nexus-control-'+op.name+'.json'),JSON.stringify(r,null,2));
    console.log('HTTP_RESULT '+JSON.stringify({name:op.name,code:r.code,status:r.data?.run?.status,runId:r.data?.runId,actions:r.data?.actions?.map(a=>({id:a.id,type:a.actionType,status:a.executionStatus}))}));continue;
   }}
   console.log('COMMAND_NOT_SUPPORTED');
  }catch(e){console.log('TEST_ERROR '+(e?.message?.match(/^[A-Z_]{1,60}$/)?e.message:e.name));}
 }
}catch(e){console.log('CONTROL_ERROR '+e.name);}finally{token=null;tokens.clear();console.log('CONTROL_AUTH_CLOSED');}
