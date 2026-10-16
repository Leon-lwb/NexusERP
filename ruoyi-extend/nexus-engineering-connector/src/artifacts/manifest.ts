import { readdir, readFile, lstat } from 'node:fs/promises';
import { join, posix } from 'node:path';
import { createHash } from 'node:crypto';
import type { LocalArtifact } from '../types.js';
import { safePath } from '../workspace.js';
const kinds: Record<string, [string, string]> = {
  '.obj': ['PCB_MODEL_OBJ', 'text/plain'],
  '.mtl': ['PCB_MODEL_MTL', 'text/plain'],
  '.png': ['PCB_MODEL_TEXTURE', 'image/png'],
  '.jpg': ['PCB_MODEL_TEXTURE', 'image/jpeg'],
  '.jpeg': ['PCB_MODEL_TEXTURE', 'image/jpeg']
};
/** 收集任务导出目录，保存真实摘要和大小，拒绝链接和超限。 */
export async function collectArtifacts(directory: string): Promise<LocalArtifact[]> {
  const files: LocalArtifact[] = [];
  let total = 0;
  async function walk(dir: string, prefix: string) {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const name = safePath(prefix + entry.name),
        path = join(dir, entry.name);
      if (entry.isSymbolicLink()) throw new Error('禁止链接产物');
      if (entry.isDirectory()) {
        await walk(path, name + '/');
        continue;
      }
      const st = await lstat(path);
      if (!st.isFile()) throw new Error('产物不是普通文件');
      if (st.size > 100 * 1024 * 1024 || (total += st.size) > 300 * 1024 * 1024 || files.length >= 64) throw new Error('产物超过配额');
      let type: string, mime: string;
      const lower = name.toLowerCase();
      if (lower.endsWith('source.zip')) {
        type = 'PCB_SOURCE';
        mime = 'application/zip';
      } else if (lower.endsWith('bom.json')) {
        type = 'PCB_BOM';
        mime = 'application/json';
      } else if (lower.endsWith('drc.json')) {
        type = 'PCB_DRC_REPORT';
        mime = 'application/json';
      } else if (lower.endsWith('.net')) {
        type = 'PCB_NETLIST';
        mime = 'text/plain';
      } else if (lower === 'preview/top.png') {
        type = 'PCB_PREVIEW_2D';
        mime = 'image/png';
      } else {
        const suffix = lower.slice(lower.lastIndexOf('.'));
        const kind = kinds[suffix];
        if (!kind) continue;
        [type, mime] = kind;
      }
      const data = await readFile(path);
      files.push({
        type,
        relativePath: name,
        localPath: path,
        size: data.length,
        sha256: createHash('sha256').update(data).digest('hex'),
        contentType: mime,
        ...(type.startsWith('PCB_MODEL_') ? { groupId: 'model-1' } : {})
      });
    }
  }
  await walk(directory, '');
  return files;
}
/** 拒绝外链和非法路径；显式允许时，本组缺失材质作为几何降级警告回传。 */
export async function validateModelReferences(files: LocalArtifact[], options: { allowMissingMaterials?: boolean } = {}): Promise<string[]> {
  const warnings: string[] = [];
  const paths = new Set(files.filter((x) => x.groupId === 'model-1').map((x) => x.relativePath));
  for (const file of files.filter((x) => ['PCB_MODEL_OBJ', 'PCB_MODEL_MTL'].includes(x.type))) {
    const text = await readFile(file.localPath, 'utf8');
    if (file.type === 'PCB_MODEL_OBJ' && (text.match(/^v /gm)?.length ?? 0) > 2_000_000) throw new Error('模型顶点超限');
    for (const line of text.split(/\r?\n/))
      if (/^\s*(mtllib|map_\w+|bump|disp|decal|refl)\s+/i.test(line)) {
        const ref = line.trim().replace(/^\S+\s+/, '');
        try {
          safePath(ref);
        } catch {
          throw new Error('模型引用含非法路径');
        }
        const full = posix.join(posix.dirname(file.relativePath), ref);
        if (!paths.has(full)) {
          if (!options.allowMissingMaterials) throw new Error('模型引用未列入同组清单');
          warnings.push(file.relativePath + ':MATERIAL_REFERENCE_MISSING');
        }
      }
  }
  return [...new Set(warnings)];
}
