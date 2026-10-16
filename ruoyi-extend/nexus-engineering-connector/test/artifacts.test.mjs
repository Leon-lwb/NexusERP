import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { collectArtifacts, validateModelReferences } from '../dist/artifacts/manifest.js';
/** OBJ/MTL资源只能引用当前产物组，不允许默认外连。 */
test('真实文件收集摘要并拒绝外部材质或逃逸路径', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-artifact-'));
  try {
    await writeFile(join(dir, 'board.obj'), 'mtllib board.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n');
    await writeFile(join(dir, 'board.mtl'), 'newmtl pcb\nKd 0 0.5 0\n');
    const files = await collectArtifacts(dir);
    assert.equal(files.length, 2);
    assert.match(files[0].sha256, /^[0-9a-f]{64}$/);
    await validateModelReferences(files);
    await writeFile(join(dir, 'board.mtl'), 'newmtl pcb\nmap_Kd https://evil.test/x.png\n');
    await assert.rejects(validateModelReferences(await collectArtifacts(dir)), /引用/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
/** 本地材质缺失可回传几何和其他产物；非法外链即使允许降级也必须拒绝。 */
test('缺失本组材质仅降级几何，外部引用不能借降级放行', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'nexus-missing-material-'));
  try {
    await writeFile(join(dir, 'board.obj'), 'mtllib board.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n');
    const files = await collectArtifacts(dir);
    const warnings = await validateModelReferences(files, { allowMissingMaterials: true });
    assert.deepEqual(warnings, ['board.obj:MATERIAL_REFERENCE_MISSING']);
    await assert.rejects(validateModelReferences(files), /引用/);
    await writeFile(join(dir, 'board.obj'), 'mtllib https://evil.test/board.mtl\nv 0 0 0\n');
    await assert.rejects(validateModelReferences(await collectArtifacts(dir), { allowMissingMaterials: true }), /引用/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
