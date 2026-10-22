import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const uiRoot = fileURLToPath(new URL('../', import.meta.url));

function npmCli(env) {
  const candidates = [
    env.npm_execpath,
    resolve(dirname(process.execPath), 'node_modules/npm/bin/npm-cli.js'),
    resolve(dirname(process.execPath), '../lib/node_modules/npm/bin/npm-cli.js')
  ];
  const cli = candidates.find((path) => path && existsSync(path));
  if (!cli) throw new Error('Cannot locate npm CLI; run this entry through npm run test:unit or npm run test:ai');
  return cli;
}

/** Each child gets a separate argv array; stop on the first failure just like the original && chain. */
export function runSteps(steps, { cwd = uiRoot, env = process.env, stdio = 'inherit' } = {}) {
  for (const step of steps) {
    const args = step.npmScript ? [npmCli(env), 'run', step.npmScript] : step.args;
    const result = spawnSync(process.execPath, args, { cwd, env, stdio, shell: false });
    if (result.error || result.status !== 0) {
      console.error(`[${step.name}] ${result.error?.message ?? (result.signal ? `terminated by ${result.signal}` : `exited with ${result.status}`)}`);
      return result.status ?? 1;
    }
  }
  return 0;
}
