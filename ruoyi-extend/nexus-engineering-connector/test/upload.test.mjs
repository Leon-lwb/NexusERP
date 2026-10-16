import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { ArtifactUploader } from '../dist/artifacts/upload.js';
/** 重试先核对服务端清单，不重复登记已确认文件。 */
test('已上传同摘要产物跳过，实际上传使用短期票据和file字段', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-upload-'));
  try {
    const localPath = join(dir, 'bom.json');
    await writeFile(localPath, '{}');
    const artifact = {
      type: 'PCB_BOM',
      relativePath: 'reports/bom.json',
      sha256: createHash('sha256').update('{}').digest('hex'),
      size: 2,
      contentType: 'application/json',
      localPath
    };
    const calls = [];
    const api = {
      request: async (path, body, signal, headers) => {
        calls.push({ path, body, headers });
        if (path.endsWith('/manifest')) return { artifacts: [] };
        if (path.endsWith('/upload-ticket')) return { uploadTicket: 'test-ticket' };
        assert.equal(body.get('file').name, 'bom.json');
        assert.equal(headers['X-Nexus-Upload-Ticket'], 'test-ticket');
        return { artifactId: '2' };
      }
    };
    await new ArtifactUploader(api, async () => {}).upload({ taskId: '1', attempt: 1, revision: 3, leaseToken: 'lease' }, artifact);
    assert.equal(calls.length, 3);
    const existing = { request: async () => ({ artifacts: [{ relativePath: artifact.relativePath, sha256: artifact.sha256, artifactId: '2' }] }) };
    assert.equal((await new ArtifactUploader(existing).upload({ taskId: '1' }, artifact)).artifactId, '2');
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
