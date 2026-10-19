import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { CredentialStore } from '../dist/auth/credential-store.js';
/** Windows CurrentUser DPAPI使用合成凭证，绝不访问真实用户Token。 */
test('凭证加密落盘且可恢复，非Windows明确拒绝', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-dpapi-'));
  try {
    const store = new CredentialStore(dir);
    if (process.platform !== 'win32') {
      await assert.rejects(store.save({ deviceId: '1', refreshToken: 'synthetic-secret' }), /Windows/);
      return;
    }
    await store.save({ deviceId: '1', refreshToken: 'synthetic-secret' });
    const raw = await readFile(join(dir, 'identity.dpapi'), 'utf8');
    assert.equal(raw.includes('synthetic-secret'), false);
    assert.deepEqual(await store.load(), { deviceId: '1', refreshToken: 'synthetic-secret' });
    await store.clear();
    assert.equal(await store.load(), null);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

/** 桌面分发在默认禁止脚本的用户环境下仍能使用合成DPAPI，不改系统策略。 */
test('凭证助手使用独立进程策略，不依赖父进程脚本设置', async () => {
  if (process.platform !== 'win32') return;
  const dir = await mkdtemp(join(tmpdir(), 'nexus-desktop-dpapi-policy-'));
  const previousPolicy = process.env.PSExecutionPolicyPreference;
  try {
    process.env.PSExecutionPolicyPreference = 'Restricted';
    const store = new CredentialStore(dir);
    await store.save({ deviceId: '1', refreshToken: 'synthetic-desktop-only' });
    assert.deepEqual(await store.load(), { deviceId: '1', refreshToken: 'synthetic-desktop-only' });
  } finally {
    if (previousPolicy === undefined) delete process.env.PSExecutionPolicyPreference;
    else process.env.PSExecutionPolicyPreference = previousPolicy;
    await rm(dir, { recursive: true, force: true });
  }
});

test('配对保存中取消时保留原有加密身份', {skip:process.platform!=='win32'}, async()=>{
  const dir=await mkdtemp(join(tmpdir(),'nexus-identity-cancel-'));
  try {
    const store=new CredentialStore(dir);
    const previous={deviceId:'1',refreshToken:'synthetic-previous'};
    await store.save(previous);
    const abort=new AbortController();
    const timer=setTimeout(()=>abort.abort(),30);
    try {
      await assert.rejects(store.save({deviceId:'2',refreshToken:'synthetic-replacement'},abort.signal),error=>error.name==='AbortError');
    } finally {clearTimeout(timer);}
    assert.deepEqual(await store.load(),previous);
  } finally {await rm(dir,{recursive:true,force:true});}
});
