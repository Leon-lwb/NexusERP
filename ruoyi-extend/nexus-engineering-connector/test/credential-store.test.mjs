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
