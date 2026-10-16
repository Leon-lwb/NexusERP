#!/usr/bin/env node
import { resolve, join } from 'node:path';
import { hostname } from 'node:os';
import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import { loadConfig } from './config.js';
import { diagnose } from './doctor.js';
import { CredentialStore } from './auth/credential-store.js';
import { NexusClient } from './nexus-client.js';
import { TaskRunner } from './task-runner.js';
import { LocalMcpClient } from './local-mcp-client.js';
import { createMcpBridge } from './mcp-bridge.js';
import { EasyedaAdapter } from './easyeda/adapter.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { readFile } from 'node:fs/promises';
import { TOOL_ACTIONS } from './mcp-bridge.js';
import { ProtectedResumeStore } from './resume-state.js';

/** 只上报实际探测到的版本；未知版本明确置空，传输模式取当前连接器实现。 */
function connectorDiagnostic(raw: Record<string, unknown>) {
  const cliVersion = typeof raw.cliVersion === 'string' ? raw.cliVersion : null;
  return {
    ...raw,
    connected: raw.connected === true,
    cliVersion,
    editorVersion: typeof raw.editorVersion === 'string' ? raw.editorVersion : null,
    transportMode: 'CLI_COMPAT',
    diagnosticCode: typeof raw.diagnosticCode === 'string' ? raw.diagnosticCode : cliVersion ? 'CLI_COMPAT' : 'CLI_VERSION_UNAVAILABLE'
  };
}

/** CLI只读取显式配置；不会将主系统凭证传给编辑器。 */
async function main() {
  const args = process.argv.slice(2),
    command = args[0];
  if (!command || command === '--help') {
    console.log(
      'Nexus工程连接器 0.1.0\n命令: doctor | pair | run | unpair | mcp\n选项: --config <配置文件>\nMCP为本机STDIO受控桥接；不开放监听端口。'
    );
    return;
  }
  if (!['doctor', 'pair', 'run', 'unpair', 'mcp'].includes(command)) throw new Error('未知命令');
  const index = args.indexOf('--config'),
    configPath = resolve(index >= 0 ? args[index + 1] : 'config.json');
  const config = await loadConfig(configPath);
  if (command === 'mcp') {
    await createMcpBridge(new EasyedaAdapter(config), config.workspaceRoot).connect(new StdioServerTransport());
    return;
  }
  if (command === 'doctor') {
    console.log(JSON.stringify(connectorDiagnostic(await diagnose(config)), null, 2));
    return;
  }
  const store = new CredentialStore(config.workspaceRoot),
    api = new NexusClient(config.serverUrl);
  const save = async (credentials: any) => {
    if (
      credentials.protocolVersion !== config.protocolVersion ||
      typeof credentials.deviceToken !== 'string' ||
      typeof credentials.refreshToken !== 'string'
    )
      throw new Error('服务端身份协议不匹配');
    api.setAccessToken(credentials.deviceToken);
    await store.save({ deviceId: credentials.deviceId, refreshToken: credentials.refreshToken, serverUrl: config.serverUrl });
    return credentials;
  };
  if (command === 'pair') {
    const reader = createInterface({ input: stdin, output: stdout });
    let pairingCode: string;
    try {
      pairingCode = (await reader.question('请输入Nexus一次性配对码: ')).trim();
    } finally {
      reader.close();
    }
    const response = await save(
      await api.request('/connector/pair', { protocolVersion: config.protocolVersion, pairingCode, deviceName: hostname() })
    );
    console.log(JSON.stringify({ paired: true, deviceId: response.deviceId }));
    return;
  }
  const identity = await store.load();
  if (!identity || identity.serverUrl !== config.serverUrl) throw new Error('当前Nexus未配对，请先运行pair');
  const resumeStore = new ProtectedResumeStore(config.workspaceRoot, identity.deviceId, config.serverUrl);
  const refresh = async (signal?: AbortSignal) => {
    const current = await store.load();
    if (!current || current.serverUrl !== config.serverUrl) throw new Error('本机绑定已失效');
    await save(await api.request('/connector/refresh', { refreshToken: current.refreshToken }, signal));
  };
  await refresh();
  api.setRefreshHandler(refresh);
  if (command === 'unpair') {
    await api.request('/connector/unpair', {});
    await resumeStore.clear();
    await store.clear();
    console.log('设备已解绑，源工程保留。');
    return;
  }
  const bridge = new LocalMcpClient(configPath);
  await bridge.connect();
  const abort = new AbortController();
  const stop = () => abort.abort();
  process.once('SIGINT', stop);
  process.once('SIGTERM', stop);
  try {
    const template = JSON.parse(await readFile(new URL('../templates/temperature-humidity-demo/manifest.json', import.meta.url), 'utf8'));
    const heartbeat = async () => {
      const diagnostic = connectorDiagnostic(await diagnose(config));
      await api.request(
        '/connector/heartbeat',
        {
          protocolVersion: config.protocolVersion,
          connectorVersion: '0.1.0',
          cliVersion: diagnostic.cliVersion,
          editorVersion: diagnostic.editorVersion,
          transportMode: diagnostic.transportMode,
          diagnosticCode: diagnostic.diagnosticCode,
          capabilities: Object.values(TOOL_ACTIONS),
          templateHashes: { [template.id]: template.sha256 },
          editorAvailable: diagnostic.connected
        },
        abort.signal
      );
    };
    console.log('工程连接器已运行；按Ctrl+C停止领取，已创建工程会保留。');
    await new TaskRunner(api, bridge, config.workspaceRoot, { resumeStore }).run(abort.signal, heartbeat);
  } finally {
    abort.abort();
    await bridge.close();
    process.off('SIGINT', stop);
    process.off('SIGTERM', stop);
  }
}
main().catch(() => {
  console.error('工程连接器执行失败；请检查配对、服务端权限、任务状态和doctor诊断。');
  process.exitCode = 1;
});
