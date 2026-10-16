import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import type { Transport } from '@modelcontextprotocol/sdk/shared/transport.js';
import { fileURLToPath } from 'node:url';
import { TOOL_ACTIONS, StageRequestSchema } from './mcp-bridge.js';
import type { TaskLease } from './types.js';

/** 连接本机受控MCP桥接，阶段回报使用带确认的双向请求。 */
export class LocalMcpClient {
  private client = new Client({ name: 'nexus-engineering-worker', version: '0.1.0' });
  private active?: {
    task: TaskLease;
    signal: AbortSignal;
    stage: (stage: string, signal?: AbortSignal) => Promise<void>;
    controller: AbortController;
  };

  constructor(
    private configPath: string,
    private options: { transport?: Transport } = {}
  ) {
    this.client.setRequestHandler(StageRequestSchema, async (request, extra) => {
      const active = this.active;
      if (!active || request.params.taskId !== active.task.taskId || request.params.attempt !== active.task.attempt)
        throw new Error('STAGE_TASK_MISMATCH');
      const signal = AbortSignal.any([active.signal, extra.signal]);
      try {
        signal.throwIfAborted();
        await active.stage(request.params.stage, signal);
        signal.throwIfAborted();
        return { acknowledged: true };
      } catch (error) {
        active.controller.abort(error);
        throw error;
      }
    });
  }

  /** 生产使用自有STDIO子进程；可注入SDK传输进行确定性协议验证。 */
  async connect() {
    const transport =
      this.options.transport ??
      new StdioClientTransport({
        command: process.execPath,
        args: [fileURLToPath(new URL('./cli.js', import.meta.url)), 'mcp', '--config', this.configPath],
        stderr: 'pipe'
      });
    if (transport instanceof StdioClientTransport) transport.stderr?.on('data', () => {});
    try {
      await this.client.connect(transport);
    } catch (error) {
      await this.client.close();
      throw error;
    }
  }

  /** 只传业务参数；阶段拒绝立刻取消工具，不留下异步进度拒绝。 */
  async execute(
    task: TaskLease,
    signal?: AbortSignal,
    onStage: (stage: string, signal?: AbortSignal) => Promise<void> = async () => {}
  ): Promise<any> {
    if (this.active) throw new Error('DEVICE_BUSY');
    const name = Object.entries(TOOL_ACTIONS).find(([, action]) => action === task.action)?.[0];
    if (!name) throw new Error('UNSUPPORTED_ACTION');
    const controller = new AbortController();
    const executionSignal = signal ? AbortSignal.any([signal, controller.signal]) : controller.signal;
    this.active = { task, signal: executionSignal, stage: onStage, controller };
    try {
      executionSignal.throwIfAborted();
      const args = {
        taskId: task.taskId,
        attempt: task.attempt,
        templateId: task.templateId,
        templateVersion: task.templateVersion,
        templateSha256: task.templateSha256,
        parentTaskId: task.parentTaskId,
        parameters: task.parameters
      };
      const result = await this.client.callTool({ name, arguments: JSON.parse(JSON.stringify(args)) }, undefined, {
        timeout: 20 * 60 * 1000,
        signal: executionSignal
      });
      const content = result.content as { type: string; text?: string }[];
      const value = JSON.parse(content.find((item) => item.type === 'text')?.text ?? 'null');
      if (result.isError)
        throw new Error(typeof value?.code === 'string' && /^[A-Z_]{1,60}$/.test(value.code) ? value.code : 'LOCAL_EXECUTION_FAILED');
      return value;
    } finally {
      controller.abort(new Error('LOCAL_EXECUTION_FINISHED'));
      this.active = undefined;
    }
  }

  /** 关闭自有MCP传输并通知当前阶段等待取消。 */
  async close() {
    this.active?.controller.abort(new Error('CONNECTOR_STOPPED'));
    await this.client.close();
  }
}
