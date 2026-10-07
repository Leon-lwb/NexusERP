import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// 原标准单测命令作为第一个参数保留；定向模式只运行本任务的非零离线回归。
const [legacyCommand, ...args] = process.argv.slice(2);
if (args.length && (args.length !== 1 || args[0] !== '--ai-knowledge-mcp')) {
  throw new Error('仅支持 --ai-knowledge-mcp 定向参数');
}
const uiRoot = fileURLToPath(new URL('../', import.meta.url));
const suites = ['knowledge.test.mjs', 'mcp.test.mjs', 'f06-tool-duration.test.mjs', 'display-usability.test.mjs'].map((name) =>
  fileURLToPath(new URL(`../tests/ai-knowledge-mcp/${name}`, import.meta.url))
);
const result = spawnSync(process.execPath, ['--test', '--test-concurrency=1', ...suites], { cwd: uiRoot, stdio: 'inherit' });
if (result.error) throw result.error;
if (result.status !== 0) process.exit(result.status ?? 1);
if (!args.length) {
  if (!legacyCommand) throw new Error('原标准单测命令不可为空');
  const legacy = spawnSync(legacyCommand, { cwd: uiRoot, shell: true, stdio: 'inherit' });
  if (legacy.error) throw legacy.error;
  process.exit(legacy.status ?? 1);
}
