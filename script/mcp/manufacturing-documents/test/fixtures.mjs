import { copyFile, mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 测试只用合成文本，绝不生成或覆盖仓库内的正式知识资料。
export const DOCUMENT_NAMES = Object.freeze([
  '采购与库存作业规范.md',
  '生产与质量作业规范.md',
  '设备与工位作业指引.md',
]);
export const SERVICE_DIRECTORY = fileURLToPath(new URL('../', import.meta.url));
export const SERVER_PATH = path.join(SERVICE_DIRECTORY, 'server.mjs');
export const TEMP_DIRECTORY = path.join(SERVICE_DIRECTORY, 'test', '.tmp');
export const REPOSITORY_ROOT = path.resolve(SERVICE_DIRECTORY, '../../..');
export const DISPLAY_SEGMENTS = ['doc', 'ai', 'knowledge-base', 'display'];

export async function createFixture(t, { populate = true } = {}) {
  await mkdir(TEMP_DIRECTORY, { recursive: true });
  const temporaryRoot = await mkdtemp(path.join(TEMP_DIRECTORY, 'documents-'));
  // Windows 上递归清理前先核对绝对路径，仅删除本次创建的随机夹具目录。
  t.after(async () => {
    const target = path.resolve(temporaryRoot);
    const relative = path.relative(path.resolve(TEMP_DIRECTORY), target);
    if (!relative || relative.startsWith('..') || path.isAbsolute(relative)) {
      throw new Error('拒绝清理越界的测试目录');
    }
    await rm(target, { recursive: true, force: true });
  });
  const repositoryRoot = path.join(temporaryRoot, 'repository');
  const displayDirectory = path.join(repositoryRoot, ...DISPLAY_SEGMENTS);
  const outsideDirectory = path.join(temporaryRoot, 'outside');
  const workingDirectory = path.join(temporaryRoot, 'unrelated-cwd');
  await Promise.all([
    mkdir(repositoryRoot, { recursive: true }),
    mkdir(outsideDirectory, { recursive: true }),
    mkdir(workingDirectory, { recursive: true }),
  ]);
  const contents = Object.fromEntries(DOCUMENT_NAMES.map((name, index) => [
    name,
    `# 测试夹具 ${index + 1}\n\n这只是自动化测试文本，不是正式业务资料。\n文件：${name}\nUTF-8：中文 ✓\n`,
  ]));
  if (populate) {
    await mkdir(displayDirectory, { recursive: true });
    await Promise.all(DOCUMENT_NAMES.map((name) => writeFile(
      path.join(displayDirectory, name), contents[name], 'utf8',
    )));
  }
  return { temporaryRoot, repositoryRoot, displayDirectory, outsideDirectory, workingDirectory, contents };
}

export async function copyServiceToFixture(fixture) {
  const directory = path.join(fixture.repositoryRoot, 'script', 'mcp', 'manufacturing-documents');
  await mkdir(directory, { recursive: true });
  // 复制真实入口而非假服务器；依赖从外层授权目录的 node_modules 解析。
  await Promise.all(['server.mjs', 'document-store.mjs'].map((name) => copyFile(
    path.join(SERVICE_DIRECTORY, name), path.join(directory, name),
  )));
  return path.join(directory, 'server.mjs');
}
