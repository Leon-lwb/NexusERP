import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Journal } from '../dist/journal.js';
/** 真实原子文件恢复用于防止重复执行编辑器写入。 */
test('重启后仍能区分已完成和执行不确定步骤', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-journal-'));
  try {
    const j = new Journal(dir);
    await j.start('generate');
    const restored = new Journal(dir);
    await restored.load();
    assert.equal(restored.step('generate'), 'STARTED');
    await assert.rejects(restored.start('generate'), /EXECUTION_UNCERTAIN/);
    await restored.finish('generate', { sessionId: 's' });
    const again = new Journal(dir);
    await again.load();
    assert.equal(again.step('generate'), 'COMPLETED');
    assert.equal(again.result('generate').sessionId, 's');
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
