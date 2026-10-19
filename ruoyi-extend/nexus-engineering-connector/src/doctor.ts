import { spawn } from 'node:child_process';
import type { ConnectorConfig } from './types.js';
/** 以独立argv执行已配置CLI；超时只停止本次子进程，绝不杀掉用户的EDA进程。 */
export function runCli(
  executable: string,
  args: string[],
  timeoutMs = 20_000,
  signal?: AbortSignal
): Promise<{ code: number | null; stdout: string }> {
  signal?.throwIfAborted();
  return new Promise((resolve, reject) => {
    const child = spawn(executable, args, { windowsHide: true, shell: false, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '';
    let settled = false;
    const finish = (code: number | null, error?: Error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      signal?.removeEventListener('abort', abort);
      child.stdout.destroy();
      child.stderr.destroy();
      error ? reject(error) : resolve({ code, stdout: stdout.trim() });
    };
    const abort = () => {
      child.kill();
      finish(null, new DOMException('CLI调用已取消', 'AbortError'));
    };
    const timer = setTimeout(() => {
      child.kill();
      finish(null, new Error('CLI调用超时'));
    }, timeoutMs);
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) {
      abort();
      return;
    }
    child.stdout.on('data', (chunk) => {
      stdout += chunk.toString();
      if (stdout.length > 8 * 1024 * 1024) {
        child.kill();
        finish(null, new Error('CLI返回超过限制'));
      }
    });
    child.stderr.resume();
    child.on('error', (error) => finish(null, error));
    // 常驻编辑器可能继承输出管道，仅等待本次CLI进程退出，不等待用户关闭编辑器。
    child.on('exit', (code) => setTimeout(() => finish(code), 30));
    child.on('close', (code) => finish(code));
  });
}
/** 诊断连接状态；ok与connected分别记录，不以可执行文件存在认定编辑器可用。 */
export async function diagnose(config: ConnectorConfig, runner: typeof runCli = runCli, signal?: AbortSignal) {
  const result = await runner(config.executable, ['doctor'], 20_000, signal);
  const envelope = JSON.parse(result.stdout);
  if (result.code !== 0 || envelope.ok !== true) throw new Error('嘉立创CLI诊断失败');
  const value = envelope.value ?? {};
  return {
    protocolVersion: config.protocolVersion,
    nodeVersion: process.version,
    platform: process.platform,
    connected: value.connected === true,
    bridgeVersion: value.bridgeVersion ?? null,
    cliVersion: value.cliVersion ?? value.bridgeVersion ?? null,
    editorVersion: value.editorVersion ?? null,
    transportMode: 'CLI_COMPAT',
    diagnosticCode: String(value.cliVersion ?? value.bridgeVersion ?? '').startsWith('4.1.60') ? 'NATIVE_MCP_UNSUPPORTED' : 'CLI_COMPAT_UNVERIFIED',
    versionMatch: value.versionMatch ?? null
  };
}
