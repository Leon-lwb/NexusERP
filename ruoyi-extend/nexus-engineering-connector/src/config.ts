import { readFile, stat, mkdir, realpath } from 'node:fs/promises';
import { resolve, isAbsolute, dirname } from 'node:path';
import { PROTOCOL, type ConnectorConfig } from './types.js';

/** 校验配置并创建专属目录；凭证字段或不受信地址在网络调用前拒绝。 */
export async function validateConfig(raw: unknown): Promise<ConnectorConfig> {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('配置必须是对象');
  const v = raw as Record<string, unknown>;
  const allowed = new Set(['protocolVersion', 'serverUrl', 'executable', 'workspaceRoot', 'development']);
  for (const key of Object.keys(v)) if (!allowed.has(key)) throw new Error(`未知配置字段: ${key}`);
  if (v.protocolVersion !== undefined && v.protocolVersion !== PROTOCOL) throw new Error('不支持的协议版本');
  if (typeof v.serverUrl !== 'string') throw new Error('缺少服务端地址');
  const url = new URL(v.serverUrl);
  if (url.username || url.password || url.search || url.hash) throw new Error('服务端地址不能包含凭证、查询或片段');
  if (url.protocol !== 'https:' && url.protocol !== 'http:') throw new Error('服务端地址只支持HTTP或HTTPS');
  if (typeof v.executable !== 'string' || !isAbsolute(v.executable)) throw new Error('执行文件必须是绝对路径');
  let executable: string;
  try {
    executable = await realpath(v.executable);
    if (!(await stat(executable)).isFile()) throw new Error();
  } catch {
    throw new Error('嘉立创执行文件不存在或不可读');
  }
  if (typeof v.workspaceRoot !== 'string' || !v.workspaceRoot.trim()) throw new Error('必须设置连接器工作目录');
  const workspaceRoot = resolve(v.workspaceRoot);
  if (dirname(workspaceRoot) === workspaceRoot) throw new Error('工作目录不能是磁盘根目录');
  await mkdir(workspaceRoot, { recursive: true });
  if (!(await stat(workspaceRoot)).isDirectory()) throw new Error('工作目录不是目录');
  return {
    protocolVersion: PROTOCOL,
    serverUrl: url.toString().replace(/\/$/, ''),
    executable,
    workspaceRoot: await realpath(workspaceRoot),
    development: v.development === true
  };
}
/** 从明确的本地配置文件读取，不自动收集主系统或用户的其他配置。 */
export async function loadConfig(path: string): Promise<ConnectorConfig> {
  return validateConfig(JSON.parse(await readFile(path, 'utf8')));
}
