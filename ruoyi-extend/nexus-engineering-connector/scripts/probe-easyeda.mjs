import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { randomInt } from 'node:crypto';
import { loadConfig } from '../dist/config.js';
import { diagnose } from '../dist/doctor.js';
import { LocalMcpClient } from '../dist/local-mcp-client.js';
import { EasyedaClient } from '../dist/easyeda/mcp-client.js';
import { ensureDirectory, containedPath } from '../dist/workspace.js';

/** 默认只读诊断；显式--create-demo才在专属工作目录创建两块演示板，不连接Nexus或上传资料。 */
const args = process.argv.slice(2);
const configIndex = args.indexOf('--config');
if (configIndex < 0 || !args[configIndex + 1]) {
  console.error('用法: node scripts/probe-easyeda.mjs --config config.json [--create-demo]');
  process.exitCode = 1;
} else {
  const configPath = resolve(args[configIndex + 1]);
  const config = await loadConfig(configPath);
  const diagnostic = await diagnose(config);
  console.log(JSON.stringify({ diagnostic, transportMode: 'CLI_COMPAT' }));
  if (args.includes('--create-demo')) {
    const template = JSON.parse(await readFile(new URL('../templates/temperature-humidity-demo/manifest.json', import.meta.url), 'utf8'));
    const mcp = new LocalMcpClient(configPath);
    const editor = new EasyedaClient(config.executable);
    const tasks = [];
    await mcp.connect();
    try {
      for (const [boardWidthMm, boardHeightMm] of [
        [50, 35],
        [60, 40]
      ]) {
        const taskId = String(BigInt(Date.now()) * 1000n + BigInt(randomInt(1000)));
        const stages = [];
        const task = {
          taskId,
          action: 'CREATE_PCB_DRAFT',
          attempt: 1,
          revision: 1,
          leaseToken: 'LOCAL_PROBE_ONLY',
          leaseExpiresAt: new Date(Date.now() + 1_200_000).toISOString(),
          deadlineAt: new Date(Date.now() + 1_200_000).toISOString(),
          templateId: template.id,
          templateVersion: template.version,
          templateSha256: template.sha256,
          parameters: { boardWidthMm, boardHeightMm, layers: 2 }
        };
        try {
          const result = await mcp.execute(task, AbortSignal.timeout(1_200_000), async (stage) => {
            stages.push(stage);
            console.error(`本地验证 ${taskId}: ${stage}`);
          });
          tasks.push({
            taskId,
            parameters: task.parameters,
            stages,
            drc: result.drc,
            errors: result.errors,
            artifacts: result.artifacts.map(({ type, relativePath, size, sha256 }) => ({ type, relativePath, size, sha256 }))
          });
        } finally {
          // 只从本次任务日志读取本次open创建的session；不枚举或停止用户既有窗口。
          try {
            const journalPath = await containedPath(config.workspaceRoot, `tasks/${taskId}/1/journal.json`);
            const journal = JSON.parse(await readFile(journalPath, 'utf8'));
            const sessionId = journal.generate?.result?.sessionId ?? journal.open?.result?.sessionId;
            if (typeof sessionId === 'string') await editor.close(sessionId);
          } catch {
            console.error(`本地验证 ${taskId}: 会话未确认关闭，请保留文件核对。`);
          }
        }
      }
      const evidence = await ensureDirectory(config.workspaceRoot, 'evidence');
      const destination = await containedPath(evidence, 'mcp-probe.json');
      await writeFile(
        destination,
        JSON.stringify(
          {
            timestamp: new Date().toISOString(),
            diagnostic,
            transportMode: 'CLI_COMPAT',
            template: { id: template.id, version: template.version, sha256: template.sha256 },
            tasks
          },
          null,
          2
        )
      );
      console.log(
        JSON.stringify({
          saved: 'evidence/mcp-probe.json',
          tasks: tasks.map(({ taskId, artifacts, errors }) => ({ taskId, artifacts: artifacts.length, errors }))
        })
      );
    } finally {
      await mcp.close();
    }
  }
}
