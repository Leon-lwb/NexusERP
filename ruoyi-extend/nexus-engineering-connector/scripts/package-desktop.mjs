import { readFile } from 'node:fs/promises';
import { join, dirname, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { cliEntries, writeDistribution } from './distribution.mjs';

/** 使用已验证的构建机Node 24 x64；不会从用户机器收集配置或运行数据。 */
if (process.platform !== 'win32' || process.arch !== 'x64' || !/^v24\./.test(process.version))
  throw new Error('桌面分发必须在Windows x64 Node.js 24上构建');
const build = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', 'scripts/build-desktop.ps1'], {
  encoding: 'utf8', windowsHide: true, shell: false, timeout: 60000
});
if (build.status !== 0) throw new Error('桌面编译失败: ' + (build.stderr || build.stdout));
const entries = await cliEntries();
for (const name of ['NexusEngineeringConnector.exe', 'NexusEngineeringConnector.exe.config'])
  entries[name] = await readFile(join('release', 'desktop-build', name));
entries['快速开始.md'] = await readFile('desktop/快速开始.md');
entries['使用说明.md'] = await readFile('desktop/使用说明.md');
entries['runtime/node.exe'] = await readFile(process.execPath);
entries['runtime/LICENSE'] = await readFile(join(dirname(process.execPath), 'LICENSE'));
entries['runtime/version.json'] = Buffer.from(JSON.stringify({ version: process.version, platform: process.platform, arch: process.arch }, null, 2));
// 可为新版选择独立目录，避免覆盖用户正在运行的旧版程序。
const outputArgument = process.argv.indexOf('--output-directory');
const outputFolder = outputArgument < 0 ? 'NexusEngineeringConnector' : process.argv[outputArgument + 1];
if (typeof outputFolder !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,100}$/.test(outputFolder))
  throw new Error('输出目录必须是release下的简单目录名');
const outputDirectory = resolve('release', outputFolder);
console.log(JSON.stringify(await writeDistribution(entries, 'nexus-engineering-connector-0.1.0-win-x64-desktop.zip', outputDirectory)));
