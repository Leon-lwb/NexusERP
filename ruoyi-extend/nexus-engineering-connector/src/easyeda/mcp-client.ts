import { readFile } from 'node:fs/promises';
import { runCli } from '../doctor.js';

/** 固定随包脚本，调用方只能传入业务参数，不能提供代码。 */
const scripts = new Set(['create-draft', 'export-artifacts', 'inspect']);
/** 嘉立创CLI兼容适配器；4.1.60的原生MCP拒绝短生命周期调用，不能自报原生MCP可用。 */
export class EasyedaClient {
  readonly transportMode = 'CLI_COMPAT';
  constructor(
    private executable: string,
    private runner: typeof runCli = runCli
  ) {}
  /** 调用真实CLI并同时核验退出码和ok包裹。 */
  private async command(args: string[], signal?: AbortSignal): Promise<any> {
    const r = await this.runner(this.executable, args, 65_000, signal);
    let envelope;
    try {
      envelope = JSON.parse(r.stdout.trim());
    } catch {
      throw new Error('CLI返回非法JSON');
    }
    if (r.code !== 0 || envelope.ok !== true) throw new Error(`嘉立创调用失败: ${String(envelope.error?.code ?? 'UNKNOWN').slice(0, 100)}`);
    return envelope.value;
  }
  /** 打开任务拥有的工程路径，具体路径在适配器验证。 */
  async open(path: string, signal?: AbortSignal): Promise<string> {
    const v = await this.command(['open', '--path', path], signal);
    const id = v?.sessionId ?? v?.id;
    if (typeof id !== 'string') throw new Error('CLI未返回会话ID');
    return id;
  }
  /** 只执行预先审查的随包脚本；参数通过JSON argv传递，不插值进源码。 */
  async invoke(sessionId: string, script: string, args: Record<string, unknown>, signal?: AbortSignal): Promise<any> {
    if (!scripts.has(script)) throw new Error('不允许的编辑器脚本');
    if (!/^[a-zA-Z0-9-]{1,80}$/.test(sessionId)) throw new Error('非法会话ID');
    const source = await readFile(new URL(`./scripts/${script}.js`, import.meta.url), 'utf8');
    return this.command(
      ['invoke', '--session', sessionId, '--ext-uuid', 'eda', '--code', source, '--args', JSON.stringify(args), '--timeout', '60000'],
      signal
    );
  }
  /** 检查实际会话和编辑器连接，不从路径存在推导连接成功。 */
  async doctor(signal?: AbortSignal) {
    return this.command(['doctor'], signal);
  }
  /** 仅关闭本连接器创建的会话，不停止整个EDA服务。 */
  async close(sessionId: string) {
    return this.command(['session', 'close', '--session', sessionId]);
  }
}
