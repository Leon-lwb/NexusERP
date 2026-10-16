import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { unzipSync, strFromU8 } from 'fflate';
/** 校验真实分发ZIP，不允许把运行配置、凭证和任务文件打包。 */
test('分发包可核验且没有用户配置或运行文件', async () => {
  const result = spawnSync(process.execPath, ['scripts/package-win.mjs'], { encoding: 'utf8', timeout: 60000 });
  assert.equal(result.status, 0, result.stderr);
  const data = unzipSync(await readFile('release/nexus-engineering-connector-0.1.0-win.zip'));
  assert.ok(data['dist/cli.js']);
  assert.ok(data['templates/temperature-humidity-demo/source.zip']);
  assert.ok(data['scripts/credential-dpapi.ps1']);
  assert.ok(data['node_modules/@modelcontextprotocol/sdk/package.json']);
  assert.ok(data['distribution-manifest.json']);
  assert.equal(
    Object.keys(data).some((p) => p.startsWith('.runtime/') || p === 'config.json' || p.includes('identity.dpapi') || p.includes('journal.json')),
    false
  );
  const manifest = JSON.parse(strFromU8(data['distribution-manifest.json']));
  assert.match(manifest['dist/cli.js'].sha256, /^[0-9a-f]{64}$/);
});
