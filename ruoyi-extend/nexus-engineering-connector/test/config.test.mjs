import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { validateConfig } from '../dist/config.js';

/** 配置测试只使用临时目录和假可执行文件，不读取真实设备身份。 */
test('HTTP与HTTPS均可配置，仍拒绝配置凭证和未知字段', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-config-'));
  try {
    const executable = join(dir, 'lceda-pro.exe');
    await writeFile(executable, 'fixture');
    const base = { serverUrl: 'https://nexus.example.test', executable, workspaceRoot: join(dir, 'work'), development: false };
    const valid = await validateConfig(base);
    assert.equal(valid.protocolVersion, 'nexus-engineering/1');
    const remote = await validateConfig({ ...base, serverUrl: 'http://10.0.0.1' });
    assert.equal(remote.serverUrl, 'http://10.0.0.1');
    assert.equal(remote.development, false);
    for (const serverUrl of ['ftp://nexus.example.test', 'file:///C:/nexus', 'ws://nexus.example.test'])
      await assert.rejects(validateConfig({ ...base, serverUrl }), /HTTP/);
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

/** 普通HTTP不依赖development开关；覆盖内网IP、域名、环回及IPv6。 */
for (const serverUrl of ['http://192.168.1.20:8080', 'http://nexus.example.test:8080/api', 'http://127.0.0.1:8080', 'http://[::1]:8080']) {
  test(`HTTP地址可直接使用：${serverUrl}`, async () => {
    const dir = await mkdtemp(join(tmpdir(), 'nexus-http-config-'));
    try {
      const executable = join(dir, 'lceda-pro.exe');
      await writeFile(executable, 'fixture');
      const config = await validateConfig({ serverUrl, executable, workspaceRoot: join(dir, 'work') });
      assert.equal(config.serverUrl, serverUrl);
      assert.equal(config.development, false);
      for (const suffix of ['?token=synthetic', '#fragment'])
        await assert.rejects(validateConfig({ ...config, serverUrl: serverUrl + suffix }), /凭证/);
    } finally { await rm(dir, { recursive: true, force: true }); }
  });
}
