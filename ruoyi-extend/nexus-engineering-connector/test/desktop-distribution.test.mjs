import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, writeFile, mkdir, rm } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { unzipSync, strFromU8 } from 'fflate';

/** 验证真实免安装包和图形启动器，临时假进程不访问 Nexus 或 EDA。 */
test('Windows桌面包内置运行环境且可在中文空格目录独立启动', { skip: process.platform !== 'win32' || process.arch !== 'x64', timeout: 180000 }, async () => {
  const result = spawnSync(process.execPath, ['scripts/package-desktop.mjs', '--output-directory', 'NexusEngineeringConnector-http-test'], { encoding: 'utf8', windowsHide: true, timeout: 120000 });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  const archive = await readFile('release/nexus-engineering-connector-0.1.0-win-x64-desktop.zip');
  const files = unzipSync(archive);
  for (const name of ['NexusEngineeringConnector.exe', 'NexusEngineeringConnector.exe.config', 'runtime/node.exe', 'runtime/LICENSE', '快速开始.md', '使用说明.md', 'dist/cli.js'])
    assert.ok(files[name]?.length > 0, name);
  assert.equal(String.fromCharCode(...files['NexusEngineeringConnector.exe'].subarray(0, 2)), 'MZ');
  assert.equal(Object.keys(files).some((name) => name === 'config.json' || name.startsWith('.runtime/') || /identity\.dpapi|journal\.json/.test(name)), false);
  const manifest = JSON.parse(strFromU8(files['distribution-manifest.json']));
  for (const [name, meta] of Object.entries(manifest)) {
    assert.equal(files[name].length, meta.size, name);
    assert.equal(createHash('sha256').update(files[name]).digest('hex'), meta.sha256, name);
  }
  const checksums = await readFile('release/nexus-engineering-connector-0.1.0-win-x64-desktop.zip.sha256', 'utf8');
  assert.ok(checksums.startsWith(createHash('sha256').update(archive).digest('hex')));
  const directory = await mkdtemp(join(tmpdir(), 'Nexus 桌面 分发验证 '));
  try {
    for (const [name, bytes] of Object.entries(files)) {
      const path = join(directory, name);
      await mkdir(dirname(path), { recursive: true });
      await writeFile(path, bytes);
    }
    const reportPath = join(directory, 'desktop-self-test.json');
    const run = spawnSync(join(directory, 'NexusEngineeringConnector.exe'), ['--self-test', reportPath], { windowsHide: true, timeout: 45000 });
    const report = JSON.parse(await readFile(reportPath, 'utf8'));
    assert.equal(run.status, 0, JSON.stringify(report));
    assert.equal(report.failed, null);
    assert.ok(report.checks.length >= 10, JSON.stringify(report));
    assert.ok((await readFile(join(directory, 'desktop-self-test.png'))).length > 1000);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
