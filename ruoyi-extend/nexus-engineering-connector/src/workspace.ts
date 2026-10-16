import { mkdir, lstat, realpath, writeFile, readdir, readFile } from 'node:fs/promises';
import { resolve, join, relative, sep } from 'node:path';
import { unzipSync, zipSync } from 'fflate';

/** 规范清单相对路径；拒绝Windows、URL、路径遍历和空段。 */
export function safePath(value: string): string {
  if (
    typeof value !== 'string' ||
    !value ||
    value.length > 255 ||
    value.includes('\\') ||
    value.includes(':') ||
    value.startsWith('/') ||
    value.includes('\0') ||
    value.split('/').some((s) => !s || s === '.' || s === '..' || /[. ]$/.test(s) || /^(con|nul|prn|aux|com[1-9]|lpt[1-9])(?:\.|$)/i.test(s))
  )
    throw new Error('非法产物相对路径');
  return value;
}
/** 检查所有已存在父目录，阻止符号链接和Windows junction跳出根目录。 */
export async function containedPath(root: string, name: string): Promise<string> {
  safePath(name);
  const base = await realpath(root);
  let path = base;
  for (const segment of name.split('/')) {
    path = join(path, segment);
    try {
      const st = await lstat(path);
      if (st.isSymbolicLink()) throw new Error('禁止链接路径');
      const actual = await realpath(path);
      const rel = relative(base, actual);
      if (rel === '..' || rel.startsWith('..' + sep)) throw new Error('路径逃逸');
    } catch (e) {
      if ((e as NodeJS.ErrnoException).code !== 'ENOENT') throw e;
    }
  }
  return path;
}
/** 写入之前核对目录每一层，防止已有junction将导出重定向到其他目录。 */
export async function ensureDirectory(root: string, name: string): Promise<string> {
  const dest = await containedPath(root, name);
  await mkdir(dest, { recursive: true });
  return containedPath(root, name);
}
/** 创建按任务与尝试分离的目录，不接受服务端提供的磁盘路径。 */
export async function taskDirectory(root: string, taskId: string, attempt: number): Promise<string> {
  if (!/^\d{1,20}$/.test(taskId) || !Number.isSafeInteger(attempt) || attempt < 1) throw new Error('非法任务标识');
  await mkdir(root, { recursive: true });
  const dest = await containedPath(root, `tasks/${taskId}/${attempt}`);
  await mkdir(dest, { recursive: true });
  await containedPath(root, `tasks/${taskId}/${attempt}`);
  return dest;
}
/** 校验ZIP目录元数据后限量解包；拒绝链接、目录逃逸和已有文件覆盖。 */
export async function extractZip(bytes: Uint8Array, directory: string): Promise<string[]> {
  if (bytes.byteLength > 100 * 1024 * 1024) throw new Error('ZIP超过限制');
  let total = 0,
    count = 0;
  const seen = new Set<string>();
  const data = unzipSync(bytes, {
    filter: (file) => {
      if (file.name.endsWith('/')) return false;
      safePath(file.name);
      const lower = file.name.toLowerCase();
      if (seen.has(lower)) throw new Error('重复路径');
      seen.add(lower);
      if (++count > 128 || (total += file.originalSize) > 300 * 1024 * 1024 || file.originalSize > 100 * 1024 * 1024)
        throw new Error('ZIP解压超过限制');
      return true;
    }
  });
  // ZIP中央目录中的Unix符号链接位；普通目录文件不会带此模式。
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  for (let i = 0; i + 46 <= bytes.length; i++)
    if (view.getUint32(i, true) === 0x02014b50) {
      const mode = view.getUint32(i + 38, true) >>> 16;
      if ((mode & 0xf000) === 0xa000) throw new Error('ZIP禁止符号链接');
      i += 45 + view.getUint16(i + 28, true) + view.getUint16(i + 30, true) + view.getUint16(i + 32, true);
    }
  const output: string[] = [];
  let actualTotal = 0;
  for (const [name, body] of Object.entries(data)) {
    if ((actualTotal += body.length) > 300 * 1024 * 1024 || body.length > 100 * 1024 * 1024) throw new Error('ZIP真实内容超限');
    const dest = await containedPath(directory, name);
    await mkdir(resolve(dest, '..'), { recursive: true });
    await writeFile(dest, body, { flag: 'wx' }).catch((e) => {
      if (e.code === 'EEXIST') throw new Error('文件已存在，拒绝覆盖');
      throw e;
    });
    output.push(name);
  }
  return output;
}
/** 归档源工程，排除派生导出和运行凭证；只收集普通文件。 */
export async function zipProject(directory: string): Promise<Uint8Array> {
  const entries: Record<string, Uint8Array> = {};
  let size = 0;
  async function walk(dir: string, prefix: string) {
    for (const e of await readdir(dir, { withFileTypes: true })) {
      const name = prefix + e.name;
      if (e.isSymbolicLink()) throw new Error('源工程含链接');
      if (e.isDirectory()) {
        if (!['exports', '.git', 'node_modules'].includes(e.name)) await walk(join(dir, e.name), name + '/');
      } else if (e.isFile() && /\.(eprj3|epcb2|esch2|ecfg|evar|epan2|json|md)$/i.test(name)) {
        const b = await readFile(join(dir, e.name));
        size += b.length;
        if (size > 100 * 1024 * 1024) throw new Error('源工程过大');
        entries[safePath(name)] = b;
      }
    }
  }
  await walk(directory, '');
  if (!Object.keys(entries).some((x) => x.endsWith('.eprj3'))) throw new Error('缺少源工程索引');
  return zipSync(entries, { level: 6 });
}
