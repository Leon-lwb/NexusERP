import { constants } from 'node:fs';
import { lstat, open, realpath } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 始终以模块所在仓库为根，启动方的 cwd 不会改变读取范围。
export const REPOSITORY_ROOT = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
export const DOCUMENT_NAMES = Object.freeze([
  '采购与库存作业规范.md',
  '生产与质量作业规范.md',
  '设备与工位作业指引.md',
]);
const DISPLAY_SEGMENTS = ['doc', 'ai', 'knowledge-base', 'display'];

export class DocumentError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'DocumentError';
    this.code = code;
  }
}

function unsafePath() {
  return new DocumentError('DOCUMENT_UNSAFE_PATH', '资料目录或文件存在软链接或越界引用，拒绝读取。');
}

function requireDocumentName(name) {
  if (typeof name !== 'string' || !DOCUMENT_NAMES.includes(name)) {
    throw new DocumentError('DOCUMENT_NOT_ALLOWED', '仅允许三份正式文件名，不接受路径、网址或其他文件。');
  }
}

function requireContained(base, target) {
  const relative = path.relative(base, target);
  // 使用路径分段判断边界，不能用字符串前缀接受 display-other 等同前缀目录。
  if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw unsafePath();
  }
}

function publicError(error, name) {
  if (error instanceof DocumentError) return error;
  if (error?.code === 'ENOENT' || error?.code === 'ENOTDIR') {
    return new DocumentError('DOCUMENT_NOT_FOUND',
      `正式资料「${name}」缺失；请确认维护者已提供该文件。服务不会编造内容。`);
  }
  if (error?.code === 'ELOOP') return unsafePath();
  // 绝不把系统异常 message/stack、真实磁盘路径或底层调用信息发送给客户端。
  return new DocumentError('DOCUMENT_READ_FAILED', `无法读取正式资料「${name}」，请检查文件权限及状态。`);
}

function metadata(name) {
  return { name, title: name.slice(0, -3), mimeType: 'text/markdown' };
}

function requireSameFile(expected, actual) {
  if (!actual.isFile() || expected.dev !== actual.dev || expected.ino !== actual.ino) throw unsafePath();
}

export function createDocumentStore({ repositoryRoot = REPOSITORY_ROOT } = {}) {
  if (typeof repositoryRoot !== 'string' || !path.isAbsolute(repositoryRoot)) {
    throw new TypeError('资料仓库根必须是绝对路径。');
  }

  async function inspect(name) {
    requireDocumentName(name);
    const root = await realpath(repositoryRoot);
    let directory = root;
    // 目录链的任意软链接（含 Windows junction）均拒绝，内外部链接一视同仁。
    for (const segment of DISPLAY_SEGMENTS) {
      directory = path.join(directory, segment);
      const state = await lstat(directory);
      if (state.isSymbolicLink()) throw unsafePath();
      if (!state.isDirectory()) {
        throw new DocumentError('DOCUMENT_NOT_FOUND', `正式资料「${name}」缺失；资料目录不存在。服务不会编造内容。`);
      }
      directory = await realpath(directory);
      requireContained(root, directory);
    }
    const filePath = path.join(directory, name);
    const state = await lstat(filePath, { bigint: true });
    if (state.isSymbolicLink()) throw unsafePath();
    if (!state.isFile()) {
      throw new DocumentError('DOCUMENT_NOT_REGULAR_FILE', `正式资料「${name}」不是普通文件，拒绝读取。`);
    }
    const actualPath = await realpath(filePath);
    requireContained(directory, actualPath);
    return { filePath: actualPath, state };
  }

  return Object.freeze({
    async listDocuments() {
      return Promise.all(DOCUMENT_NAMES.map(async (name) => {
        try {
          await inspect(name);
          return { ...metadata(name), available: true };
        } catch (error) {
          const failure = publicError(error, name);
          return { ...metadata(name), available: false, error: { code: failure.code, message: failure.message } };
        }
      }));
    },

    async readDocument(name) {
      requireDocumentName(name);
      try {
        const approved = await inspect(name);
        // 不创建、不截断文件；支持 O_NOFOLLOW 的平台再加一层防护。
        const handle = await open(approved.filePath, constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0));
        try {
          const openedState = await handle.stat({ bigint: true });
          requireSameFile(approved.state, openedState);
          // 打开后复核目录链及文件身份，在读正文前拒绝检查期间发生的路径替换。
          const current = await inspect(name);
          requireSameFile(current.state, openedState);
          const content = await handle.readFile({ encoding: 'utf8' });
          return { ...metadata(name), content };
        } finally {
          await handle.close();
        }
      } catch (error) {
        throw publicError(error, name);
      }
    },
  });
}
