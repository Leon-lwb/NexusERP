import { spawn } from 'node:child_process';
import { readFile, writeFile, mkdir, unlink, rename } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
/** 只保存设备ID和轮换刷新凭证，不持久化访问凭证。 */
export interface SavedIdentity {
  deviceId: string;
  refreshToken: string;
  serverUrl?: string;
}
/** 当前Windows用户的加密凭证仓库；其他平台不回落到明文。 */
export class CredentialStore {
  constructor(private directory: string) {}
  private get path() {
    return join(this.directory, 'identity.dpapi');
  }
  private transform(mode: 'protect' | 'unprotect', value: string): Promise<string> {
    if (process.platform !== 'win32') return Promise.reject(new Error('凭证保护仅支持Windows'));
    return new Promise((resolve, reject) => {
      const script = fileURLToPath(new URL('../../scripts/credential-dpapi.ps1', import.meta.url));
      // 仅本次受控助手使用进程级策略；不修改用户/计算机策略，组策略仍优先。
      const child = spawn('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', script, '-Mode', mode], {
        windowsHide: true,
        shell: false,
        stdio: ['pipe', 'pipe', 'pipe']
      });
      let output = '';
      const timer = setTimeout(() => {
        child.kill();
        reject(new Error('凭证加密超时'));
      }, 15000);
      child.stdout.on('data', (b) => {
        output += b.toString();
        if (output.length > 64 * 1024) {
          child.kill();
          reject(new Error('凭证超限'));
        }
      });
      child.stderr.resume();
      child.stdin.on('error', () => {});
      child.stdin.end(value);
      child.on('error', (e) => {
        clearTimeout(timer);
        reject(e);
      });
      child.on('close', (c) => {
        clearTimeout(timer);
        c === 0 ? resolve(output) : reject(new Error('Windows凭证保护失败'));
      });
    });
  }
  /** 加密后原子替换凭证文件。 */
  async save(identity: SavedIdentity, signal?: AbortSignal) {
    signal?.throwIfAborted();
    if (!identity.deviceId || !identity.refreshToken) throw new Error('无效设备身份');
    const ciphertext = await this.transform('protect', JSON.stringify(identity));
    signal?.throwIfAborted();
    await mkdir(this.directory, { recursive: true });
    signal?.throwIfAborted();
    const temp = this.path + '.tmp';
    try {
      await writeFile(temp, ciphertext, { mode: 0o600 });
      signal?.throwIfAborted();
      await rename(temp, this.path);
    } catch (error) {
      await unlink(temp).catch(() => {});
      throw error;
    }
  }
  /** 读取后仅返回当前设备；不存在时返回null，损坏不静默重新绑定。 */
  async load(): Promise<SavedIdentity | null> {
    let value;
    try {
      value = await readFile(this.path, 'utf8');
    } catch (e) {
      if ((e as NodeJS.ErrnoException).code === 'ENOENT') return null;
      throw e;
    }
    return JSON.parse(await this.transform('unprotect', value));
  }
  /** 解绑后删除本设备凭证，不删除任何工程文件。 */
  async clear() {
    await unlink(this.path).catch((e) => {
      if (e.code !== 'ENOENT') throw e;
    });
  }
}
