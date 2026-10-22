import { knowledgeTestFiles } from './unit-test-plan.mjs';
import { runSteps } from './unit-test-runner.mjs';

const args = process.argv.slice(2);
if (args.length && (args.length !== 1 || args[0] !== '--ai-knowledge-mcp')) {
  throw new Error('仅支持 --ai-knowledge-mcp 定向参数');
}
process.exitCode = runSteps([{ name: 'AI knowledge/MCP', args: ['--test', '--test-concurrency=1', ...knowledgeTestFiles()] }]);
