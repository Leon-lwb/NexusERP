import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { createMcpBridge } from '../../dist/mcp-bridge.js';
/** 合成STDIO桥接仅在ACK后落盘标记，不调用编辑器或业务服务。 */
const root = process.argv[2];
await createMcpBridge(
  {
    execute: async (task, signal, onStage) => {
      await onStage('GENERATING');
      signal.throwIfAborted();
      await writeFile(join(root, 'effect.txt'), 'synthetic effect');
      return { artifacts: [], errors: [] };
    }
  },
  root
).connect(new StdioServerTransport());
