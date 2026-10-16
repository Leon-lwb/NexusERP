import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { safePath, extractZip, taskDirectory } from '../dist/workspace.js';
import { zipSync, strToU8 } from 'fflate';
/** 真实临时文件验证路径边界和ZIP解包限制。 */
test('路径与解包拒绝逃逸、外部路径和重复覆盖', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-work-'));
  try {
    assert.throws(() => safePath('../x'), /路径/);
    assert.throws(() => safePath('C:/x'), /路径/);
    assert.throws(() => safePath('http://x'), /路径/);
    assert.throws(() => safePath('a\\b'), /路径/);
    const t = await taskDirectory(dir, '2000000000000000001', 1);
    assert.ok(t.startsWith(dir));
    await extractZip(zipSync({ 'project.eprj3': strToU8('{}') }), t);
    await assert.rejects(extractZip(zipSync({ '../escape': strToU8('x') }), t), /路径/);
    await assert.rejects(extractZip(zipSync({ 'project.eprj3': strToU8('replacement') }), t), /存在/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
