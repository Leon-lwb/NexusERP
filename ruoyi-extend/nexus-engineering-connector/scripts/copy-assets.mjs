import { cp, mkdir } from 'node:fs/promises';
/** tsc不编译编辑器脚本体，将固定脚本原样随版本分发。 */
await mkdir('dist/easyeda/scripts', { recursive: true });
await cp('src/easyeda/scripts', 'dist/easyeda/scripts', { recursive: true });
