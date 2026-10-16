import { readFile, readdir, mkdir, writeFile, lstat } from 'node:fs/promises';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { zipSync, strToU8 } from 'fflate';
/** 分发清单只读取固定代码目录；不接触用户配置、凭证、日志和工程运行产物。 */
const entries = {};
async function walk(directory, prefix = directory) {
  for (const item of await readdir(directory, { withFileTypes: true })) {
    if (item.isSymbolicLink()) continue;
    if (['.bin', 'typescript', '@types'].includes(item.name)) continue;
    const source = join(directory, item.name),
      name = (prefix + '/' + item.name).replaceAll('\\', '/');
    if (item.isDirectory()) await walk(source, name);
    else if (item.isFile()) entries[name] = await readFile(source);
  }
}
for (const dir of ['dist', 'templates', 'node_modules']) await walk(dir);
for (const name of [
  'package.json',
  'package-lock.json',
  'config.example.json',
  'README.md',
  'scripts/credential-dpapi.ps1',
  'scripts/probe-easyeda.mjs'
])
  entries[name] = await readFile(name);
const manifest = Object.fromEntries(
  Object.entries(entries).map(([name, bytes]) => [name, { size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') }])
);
entries['distribution-manifest.json'] = strToU8(JSON.stringify(manifest, null, 2));
await mkdir('release', { recursive: true });
const bytes = zipSync(entries, { level: 6 }),
  name = 'nexus-engineering-connector-0.1.0-win.zip';
await writeFile('release/' + name, bytes);
await writeFile('release/' + name + '.sha256', createHash('sha256').update(bytes).digest('hex') + '  ' + name + '\n');
console.log(JSON.stringify({ name, files: Object.keys(entries).length, size: bytes.length }));
