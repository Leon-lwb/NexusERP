import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

/** 使用本机临时HTTP假服务，绝不兑换真实配对码或保存真实身份。 */
test('桌面配对CLI自动识别代理并报告无效配对码，不输出凭证或延迟提示', async () => {
  const requests = [];
  const server = createServer(async (request, response) => {
    const chunks = []; for await (const chunk of request) chunks.push(chunk);
    requests.push({url:request.url,method:request.method,body:Buffer.concat(chunks).toString()});
    if (request.url === '/prod-api/ai/engineering/connector/pair') {
      response.setHeader('Content-Type','application/json');
      if (request.method === 'GET') response.end(JSON.stringify({code:405}));
      else { response.statusCode=401; response.end(JSON.stringify({code:401,msg:'synthetic-private-server-body',data:{errorCode:'PAIRING_INVALID'}})); }
    } else { response.setHeader('Content-Type','text/html'); response.end('<html>frontend</html>'); }
  });
  const directory = await mkdtemp(join(tmpdir(),'nexus-cli-pair-'));
  let child;
  try {
    await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
    const config = join(directory,'config.json');
    await writeFile(config,JSON.stringify({serverUrl:`http://127.0.0.1:${server.address().port}`,executable:process.execPath,workspaceRoot:join(directory,'workspace')}));
    child=spawn(process.execPath,[resolve('dist/cli.js'),'pair','--desktop','--config',config],{windowsHide:true,stdio:['pipe','pipe','pipe']});
    let output=''; child.stdout.on('data',b=>output+=b.toString()); child.stderr.on('data',b=>output+=b.toString());
    const closed = new Promise((resolve,reject)=>{child.once('close',resolve);child.once('error',reject);});
    const timer=setTimeout(()=>child.kill(),15000);
    child.stdin.write('synthetic-invalid-pair-code\n');
    let code; try {code=await closed;} finally {clearTimeout(timer);}
    assert.equal(code,1,output);
    assert.match(output,/PAIRING_INVALID/);
    assert.match(output,/配对码/);
    assert.equal(output.includes('synthetic-private-server-body'),false);
    assert.equal(output.includes('synthetic-invalid-pair-code'),false);
    assert.equal(output.includes('请输入'),false);
    assert.equal(requests.filter(r=>r.method==='POST').length,1);
    assert.equal(requests.at(-1).url,'/prod-api/ai/engineering/connector/pair');
    assert.ok(requests.filter(r=>r.method==='GET').every(r=>r.body===''));
  } finally {
    if(child?.exitCode===null) child.kill();
    server.closeAllConnections(); await new Promise(resolve=>server.close(resolve));
    await rm(directory,{recursive:true,force:true});
  }
});

test('桌面退出在只读识别阶段取消配对，不再发送凭证POST', async () => {
  let postCount=0, releaseProbe;
  let markProbe;
  const probeSeen=new Promise(resolve=>{markProbe=resolve;});
  const server=createServer((request,response)=>{
    if(request.method==='GET') {
      releaseProbe=()=>{if(!response.destroyed){response.setHeader('Content-Type','application/json');response.end(JSON.stringify({code:405}));}};
      markProbe();
    } else {postCount++;response.setHeader('Content-Type','application/json');response.end(JSON.stringify({code:401,data:{errorCode:'PAIRING_INVALID'}}));}
  });
  const directory=await mkdtemp(join(tmpdir(),'nexus-pair-cancel-'));
  let child,timer;
  try {
    await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
    const config=join(directory,'config.json');
    await writeFile(config,JSON.stringify({serverUrl:`http://127.0.0.1:${server.address().port}`,executable:process.execPath,workspaceRoot:join(directory,'workspace')}));
    child=spawn(process.execPath,[resolve('dist/cli.js'),'pair','--desktop','--config',config],{windowsHide:true,stdio:['pipe','pipe','pipe']});
    child.stdout.resume(); child.stderr.resume();
    const closed=new Promise((resolve,reject)=>{child.once('close',resolve);child.once('error',reject);});
    timer=setTimeout(()=>child.kill(),12000);
    child.stdin.write('synthetic-never-pair-code\n');
    await Promise.race([probeSeen,new Promise((_,reject)=>setTimeout(()=>reject(new Error('probe not started')),5000).unref())]);
    child.stdin.end('stop\n');
    await new Promise(resolve=>setTimeout(resolve,30));
    releaseProbe();
    const code=await closed;
    assert.equal(postCount,0);
    assert.equal(code,130);
  } finally {
    clearTimeout(timer); if(child?.exitCode===null) child.kill();
    server.closeAllConnections(); await new Promise(resolve=>server.close(resolve));
    await rm(directory,{recursive:true,force:true});
  }
});

/** 完整成功路径仍只连接临时假服务，合成身份仅写入独立临时目录。 */
test('桌面模拟配对成功后加密保存身份，输出不含访问或刷新凭证', {skip:process.platform!=='win32'}, async()=>{
  const {CredentialStore}=await import('../dist/auth/credential-store.js');
  const fake={protocolVersion:'nexus-engineering/1',deviceId:'9223372036854775807',deviceToken:'synthetic-access-only',refreshToken:'synthetic-refresh-only'};
  let pairCount=0;
  const server=createServer(async(request,response)=>{
    for await(const _chunk of request) {}
    if(request.url==='/prod-api/ai/engineering/connector/pair') {
      response.setHeader('Content-Type','application/json');
      if(request.method==='GET')response.end(JSON.stringify({code:405}));
      else{pairCount++;response.end(JSON.stringify({code:200,data:fake}));}
    }else{response.setHeader('Content-Type','text/html');response.end('<html>frontend</html>');}
  });
  const directory=await mkdtemp(join(tmpdir(),'nexus-pair-success-'));
  let child,timer;
  try {
    await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
    const base=`http://127.0.0.1:${server.address().port}`;
    const workspace=join(directory,'workspace');
    const config=join(directory,'config.json');
    await writeFile(config,JSON.stringify({serverUrl:base,executable:process.execPath,workspaceRoot:workspace}));
    child=spawn(process.execPath,[resolve('dist/cli.js'),'pair','--desktop','--config',config],{windowsHide:true,stdio:['pipe','pipe','pipe']});
    let output='';child.stdout.on('data',b=>output+=b.toString());child.stderr.on('data',b=>output+=b.toString());
    const closed=new Promise((resolve,reject)=>{child.once('close',resolve);child.once('error',reject);});
    timer=setTimeout(()=>child.kill(),15000);
    child.stdin.write('synthetic-success-code\n');
    assert.equal(await closed,0,output);
    assert.equal(pairCount,1);
    assert.match(output,/"paired":true/);
    assert.match(output,/"deviceId":"9223372036854775807"/);
    for(const value of [fake.deviceToken,fake.refreshToken,'synthetic-success-code'])assert.equal(output.includes(value),false);
    assert.deepEqual(await new CredentialStore(workspace).load(),{deviceId:fake.deviceId,refreshToken:fake.refreshToken,serverUrl:base});
  }finally{
    clearTimeout(timer);if(child?.exitCode===null)child.kill();
    server.closeAllConnections();await new Promise(resolve=>server.close(resolve));
    await rm(directory,{recursive:true,force:true});
  }
});
