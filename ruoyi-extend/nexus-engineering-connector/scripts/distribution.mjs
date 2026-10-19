import { readFile, readdir, mkdir, writeFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { createHash } from 'node:crypto';
import { zipSync, strToU8 } from 'fflate';

/** 分发只收集固定目录，不读取用户配置、凭证或工程运行目录。 */
export async function cliEntries() {
  const entries = Object.create(null);
  async function walk(directory, prefix = directory) {
    for (const item of await readdir(directory, { withFileTypes: true })) {
      if (item.isSymbolicLink() || ['.bin', 'typescript', '@types'].includes(item.name)) continue;
      const source = join(directory, item.name);
      const name = (prefix + '/' + item.name).replaceAll('\\', '/');
      if (item.isDirectory()) await walk(source, name);
      else if (item.isFile()) entries[name] = await readFile(source);
    }
  }
  for (const directory of ['dist', 'templates', 'node_modules']) await walk(directory);
  for (const name of ['package.json', 'package-lock.json', 'config.example.json', 'README.md', 'scripts/credential-dpapi.ps1', 'scripts/probe-easyeda.mjs'])
    entries[name] = await readFile(name);
  return entries;
}

/** 逐文件清单与ZIP摘要覆盖实际交付内容；可同步生成完整解压目录。 */
export async function writeDistribution(entries, name, outputDirectory) {
  const manifest = Object.fromEntries(Object.entries(entries).map(([path, bytes]) => [path, { size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') }]));
  entries['distribution-manifest.json'] = strToU8(JSON.stringify(manifest, null, 2));
  if (outputDirectory) {
    for (const [path, bytes] of Object.entries(entries)) {
      if (path.startsWith('/') || path.includes('\\') || path.split('/').some((part) => part === '..' || part === '')) throw new Error('非法分发路径');
      const destination = join(outputDirectory, path);
      await mkdir(dirname(destination), { recursive: true });
      await writeFile(destination, bytes);
    }
  }
  await mkdir('release', { recursive: true });
  const bytes = zipSync(entries, { level: 6 });
  await writeFile(join('release', name), bytes);
  await writeFile(join('release', name + '.sha256'), createHash('sha256').update(bytes).digest('hex') + '  ' + name + '\n');
  return { name, files: Object.keys(entries).length, size: bytes.length, ...(outputDirectory ? { directory: outputDirectory } : {}) };
}
