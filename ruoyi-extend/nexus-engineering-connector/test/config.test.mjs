import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { validateConfig } from '../dist/config.js';

/** 配置测试只使用临时目录和假可执行文件，不读取真实设备身份。 */
test('仅允许HTTPS或明确环回开发地址，拒绝配置凭证和未知字段', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-config-'));
  try {
    const executable = join(dir, 'lceda-pro.exe');
    await writeFile(executable, 'fixture');
    const base = { serverUrl: 'https://nexus.example.test', executable, workspaceRoot: join(dir, 'work'), development: false };
    const valid = await validateConfig(base);
    assert.equal(valid.protocolVersion, 'nexus-engineering/1');
    await assert.rejects(validateConfig({ ...base, serverUrl: 'http://10.0.0.1' }), /HTTPS/);
    await assert.rejects(validateConfig({ ...base, refreshToken: 'secret' }), /未知配置/);
    await assert.rejects(validateConfig({ ...base, protocolVersion: 'nexus-engineering/2' }), /协议/);
    await assert.rejects(validateConfig({ ...base, serverUrl: 'https://user:pass@example.test' }), /凭证/);
    await assert.rejects(validateConfig({ ...base, executable: join(dir, 'missing.exe') }), /执行文件/);
    const local = await validateConfig({ ...base, serverUrl: 'http://127.0.0.1:18080', development: true });
    assert.equal(local.serverUrl, 'http://127.0.0.1:18080');
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
