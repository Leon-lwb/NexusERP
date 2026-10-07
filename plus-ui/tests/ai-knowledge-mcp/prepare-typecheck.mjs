import { execFileSync } from 'node:child_process';
import { existsSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const uiRoot = resolve(here, '../..');
let autoImports = resolve(uiRoot, 'src/types/auto-imports.d.ts');
if (!existsSync(autoImports)) {
  // 新工作树未带入 Git 忽略的生成声明，只读借用同一仓库主工作区的真实声明。
  const commonDir = execFileSync('git', ['rev-parse', '--path-format=absolute', '--git-common-dir'], { cwd: uiRoot, encoding: 'utf8' }).trim();
  autoImports = resolve(commonDir, '../plus-ui/src/types/auto-imports.d.ts');
}
if (!existsSync(autoImports)) throw new Error('缺少真实自动导入声明；请先在获准的开发环境生成，不能用 any 替代');
const config = {
  extends: '../../tsconfig.json',
  compilerOptions: { incremental: false, types: ['node', 'vite/client', 'element-plus/global'] },
  include: [
    '../../src/types/**/*.d.ts',
    '../../src/views/ai/knowledge/**/*.vue',
    '../../src/views/ai/mcp/**/*.vue',
    '../../src/api/ai/knowledge.ts',
    '../../src/api/ai/mcp*.ts',
    autoImports.replaceAll('\\', '/')
  ],
  exclude: ['../../node_modules', '../../dist', '../../src/**/__tests__/*']
};
writeFileSync(resolve(here, '.typecheck.tsconfig.json'), JSON.stringify(config, null, 2) + '\n');
process.stdout.write('已生成专属定向类型检查配置；自动导入声明只读复用，无依赖或生产文件写入。\n');
