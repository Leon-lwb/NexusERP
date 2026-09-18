import { promises as fs } from 'fs';
import type { Dirent } from 'fs';
import path from 'path';
import zlib from 'zlib';
import { promisify } from 'util';
import type { Plugin, ResolvedConfig } from 'vite';

const gzip = promisify(zlib.gzip);
const brotliCompress = promisify(zlib.brotliCompress);
const compressibleFileRE = /\.(js|mjs|json|css|html)$/i;
const defaultThreshold = 1025;

type CompressionKind = 'gzip' | 'brotli';

const compressionHandlers: Record<CompressionKind, { ext: string; compress: (content: Buffer) => Promise<Buffer> }> = {
  gzip: {
    ext: '.gz',
    compress: (content) => gzip(content, { level: zlib.constants.Z_BEST_COMPRESSION })
  },
  brotli: {
    ext: '.br',
    compress: (content) =>
      brotliCompress(content, {
        params: {
          [zlib.constants.BROTLI_PARAM_QUALITY]: zlib.constants.BROTLI_MAX_QUALITY,
          [zlib.constants.BROTLI_PARAM_MODE]: zlib.constants.BROTLI_MODE_TEXT
        }
      })
  }
};

async function collectFiles(rootDir: string): Promise<string[]> {
  // 这里的断言是绕过 @types/node@25.6.0 的声明缺陷，不是代码问题：
  // 该版本 fs/promises 的 `readdir(path, { withFileTypes: true })` 重载（Promise<Dirent[]>）
  // 实际不可达 —— 无论内联字面量、as const、显式标注 ObjectEncodingOptions，还是
  // 具名导入 / node: 前缀导入，甚至开启 --strict，TS 都选中第一个重载并推断为 string[]。
  // 运行时行为是正确的（Node 确实返回 Dirent[]），故只桥接类型，不改动运行时代码。
  const entries = (await fs.readdir(rootDir, { withFileTypes: true })) as unknown as Dirent[];
  const files = await Promise.all(
    entries.map(async (entry) => {
      const fullPath = path.join(rootDir, entry.name);
      if (entry.isDirectory()) {
        return collectFiles(fullPath);
      }
      return compressibleFileRE.test(entry.name) ? [fullPath] : [];
    })
  );
  return files.flat();
}

function createCompressionPlugin(kind: CompressionKind): Plugin {
  const handler = compressionHandlers[kind];
  let config: ResolvedConfig | undefined;

  return {
    name: `local:compression:${kind}`,
    apply: 'build',
    enforce: 'post',
    configResolved(resolvedConfig) {
      config = resolvedConfig;
    },
    async closeBundle() {
      const outputDir = path.resolve(process.cwd(), config?.build.outDir ?? 'dist');
      try {
        await fs.access(outputDir);
      } catch {
        config?.logger.warn(`[compression:${kind}] output directory not found: ${outputDir}, skipping.`);
        return;
      }
      const files = await collectFiles(outputDir);
      const compressedEntries: Array<{ file: string; originalKb: string; compressedKb: string }> = [];

      await Promise.all(
        files.map(async (filePath) => {
          const stat = await fs.stat(filePath);
          if (stat.size < defaultThreshold) {
            return;
          }

          const content = await fs.readFile(filePath);
          const compressed = await handler.compress(content);
          const outputFile = `${filePath}${handler.ext}`;

          await fs.writeFile(outputFile, compressed);
          compressedEntries.push({
            file: path.relative(outputDir, outputFile).replaceAll('\\', '/'),
            originalKb: (stat.size / 1024).toFixed(2),
            compressedKb: (compressed.byteLength / 1024).toFixed(2)
          });
        })
      );

      if (!compressedEntries.length) {
        return;
      }

      compressedEntries.sort((a, b) => a.file.localeCompare(b.file));
      config?.logger.info(`\n[compression:${kind}] generated ${compressedEntries.length} files`);
      for (const entry of compressedEntries) {
        config?.logger.info(`${path.basename(outputDir)}/${entry.file} ${entry.originalKb}kb -> ${entry.compressedKb}kb`);
      }
      config?.logger.info('');
    }
  };
}

export default (env: Record<string, string>) => {
  const { VITE_BUILD_COMPRESS } = env;
  const plugins: Plugin[] = [];
  if (!VITE_BUILD_COMPRESS) {
    return plugins;
  }

  const compressionList = VITE_BUILD_COMPRESS.split(',').map((item) => item.trim()) as CompressionKind[];
  if (compressionList.includes('gzip')) {
    plugins.push(createCompressionPlugin('gzip'));
  }
  if (compressionList.includes('brotli')) {
    plugins.push(createCompressionPlugin('brotli'));
  }
  return plugins;
};
