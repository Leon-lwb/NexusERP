import { z } from 'zod';
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { CallToolRequestSchema, ListToolsRequestSchema, RequestSchema, ResultSchema } from '@modelcontextprotocol/sdk/types.js';
import { relative, sep } from 'node:path';
import type { TaskLease } from './types.js';
import type { AdapterResult } from './easyeda/adapter.js';
import { safePath } from './workspace.js';
/** 双向阶段请求只包含任务身份与固定阶段，不携带租约或设备凭证。 */
export const StageRequestSchema = RequestSchema.extend({
  method: z.literal('nexus/stage'),
  params: z.object({
    taskId: z.string().regex(/^\d{1,20}$/),
    attempt: z.number().int().positive(),
    stage: z.enum(['CHECKING', 'PREPARING', 'GENERATING', 'CHECKING_DRC', 'EXPORTING', 'UPLOADING', 'FINALIZING'])
  })
});
/** 阶段只有明确确认才允许继续；空结果或false均拒绝。 */
export const StageResultSchema = ResultSchema.extend({ acknowledged: z.literal(true) });

/** 本地MCP工具名固定映射；原生MCP不可用时，仍保留可审计的受控桥接。 */
export const TOOL_ACTIONS = {
  easyeda_check_connection: 'CHECK_CONNECTION',
  easyeda_open_project: 'OPEN_PROJECT',
  easyeda_create_pcb_draft: 'CREATE_PCB_DRAFT',
  easyeda_export_pcb_artifacts: 'EXPORT_ARTIFACTS',
  easyeda_run_drc: 'RUN_DRC',
  easyeda_submit_design_draft: 'SUBMIT_DRAFT'
} as const;
/** 创建只接收结构化任务的MCP服务；不允许代码、命令、URL或磁盘路径输入。 */
export function createMcpBridge(
  adapter: { execute(task: TaskLease, signal?: AbortSignal, onStage?: (stage: string) => Promise<void>): Promise<AdapterResult> },
  workspaceRoot: string,
  options: { stageTimeoutMs?: number } = {}
) {
  const server = new Server({ name: 'nexus-easyeda-bridge', version: '0.1.0' }, { capabilities: { tools: {} } });
  const properties = {
    taskId: { type: 'string', pattern: '^\\d{1,20}$' },
    attempt: { type: 'integer', minimum: 1 },
    templateId: { type: 'string' },
    templateVersion: { type: 'string' },
    templateSha256: { type: 'string' },
    parentTaskId: { type: 'string' },
    parameters: { type: 'object' }
  };
  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: Object.keys(TOOL_ACTIONS).map((name) => ({
      name,
      description: '在连接器专属目录执行嘉立创草稿动作，不发布生产',
      inputSchema: { type: 'object', properties, required: ['taskId', 'attempt', 'parameters'], additionalProperties: false }
    }))
  }));
  server.setRequestHandler(CallToolRequestSchema, async (request, extra) => {
    try {
      const action = TOOL_ACTIONS[request.params.name as keyof typeof TOOL_ACTIONS];
      if (!action) throw new Error('UNSUPPORTED_ACTION');
      const args = request.params.arguments ?? {};
      for (const key of Object.keys(args)) if (!(key in properties)) throw new Error('INVALID_ARGUMENTS');
      if (
        !/^\d{1,20}$/.test(String(args.taskId)) ||
        !Number.isSafeInteger(args.attempt) ||
        Number(args.attempt) < 1 ||
        !args.parameters ||
        typeof args.parameters !== 'object' ||
        Array.isArray(args.parameters)
      )
        throw new Error('INVALID_ARGUMENTS');
      const result = await adapter.execute({ ...args, action } as unknown as TaskLease, extra.signal, async (stage) => {
        extra.signal.throwIfAborted();
        try {
          await server.request(
            { method: 'nexus/stage', params: { taskId: String(args.taskId), attempt: Number(args.attempt), stage } },
            StageResultSchema,
            { signal: extra.signal, timeout: options.stageTimeoutMs ?? 30_000 }
          );
        } catch {
          extra.signal.throwIfAborted();
          throw new Error('STAGE_ACK_FAILED');
        }
        extra.signal.throwIfAborted();
      });
      const artifacts = result.artifacts.map(({ localPath, ...artifact }) => ({
        ...artifact,
        workspaceRelativePath: safePath(relative(workspaceRoot, localPath).split(sep).join('/'))
      }));
      return {
        content: [{ type: 'text', text: JSON.stringify({ artifacts, drc: result.drc, diagnostic: result.diagnostic, errors: result.errors }) }]
      };
    } catch (e) {
      const raw = e instanceof Error ? e.message : '';
      const code = /^[A-Z_]{1,60}$/.test(raw) ? raw : 'LOCAL_EXECUTION_FAILED';
      return { isError: true, content: [{ type: 'text', text: JSON.stringify({ code, message: '本地工程动作未完成，请查看任务与客户端诊断' }) }] };
    }
  });
  return server;
}
