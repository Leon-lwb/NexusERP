/**
 * vue-tsc 变更文件类型检查 (P0-6 回归门禁的一部分)
 *
 * 背景: 全项目 vue-tsc 存在约 1580 个既有错误(baseline 噪声), 不能用退出码判断
 * 改动是否引入新错。本脚本改为"变更文件视角":
 *   - 解析 vue-tsc 输出, 提取每条错误所属文件
 *   - 计算本次 PR/commit 的变更文件集合 (git diff)
 *   - 若变更文件存在任何类型错误 → 退出码 1; 未变更文件的既有错误不阻断
 *
 * 用法:
 *   node scripts/check-tsc-diff.mjs <tsc输出文件> [baseRef]
 *   - tsc输出文件: vue-tsc --noEmit 的 stdout/stderr 重定向文件
 *   - baseRef(可选): 对比基准, 默认 PR 场景用 origin 目标分支(CI 注入), 本地默认 HEAD~1
 *
 * vue-tsc 错误行格式:  src/views/foo.vue(12,34): error TS2322: 消息文本
 */
import { execSync } from 'node:child_process';
import fs from 'node:fs';

const [, , tscOutPath, baseRefArg] = process.argv;
if (!tscOutPath || !fs.existsSync(tscOutPath)) {
  console.error('用法: node scripts/check-tsc-diff.mjs <tsc输出文件> [baseRef]');
  process.exit(2);
}

const output = fs.readFileSync(tscOutPath, 'utf8');

// 提取每条错误所在文件(取行首, 相对 plus-ui 根)
const errorFileSet = new Set();
for (const line of output.split(/\r?\n/)) {
  const m = line.match(/^([^\s(]+)\((\d+),(\d+)\):\s*error\s+TS\d+/);
  if (m) {
    errorFileSet.add(m[1].replace(/\\/g, '/'));
  }
}

// 计算变更文件集合
let baseRef = baseRefArg;
if (!baseRef) {
  // CI: PR 目标分支由 Actions 注入; 本地默认与上一次提交比较
  baseRef = process.env.GITHUB_BASE_SHA || process.env.GITHUB_BASE_REF || 'HEAD~1';
}
let changed = [];
try {
  changed = execSync(`git diff --name-only --diff-filter=ACMR ${baseRef}...HEAD`, { encoding: 'utf8' })
    .split(/\r?\n/)
    .filter(Boolean)
    .map((p) => p.replace(/\\/g, '/'));
} catch {
  // 本地浅克隆/无 base 时退回与 HEAD~1 比较
  changed = execSync('git diff --name-only --diff-filter=ACMR HEAD~1', { encoding: 'utf8' })
    .split(/\r?\n/)
    .filter(Boolean)
    .map((p) => p.replace(/\\/g, '/'));
}

// 变更文件里属于 plus-ui 的部分(去掉 plus-ui/ 前缀与 tsc 输出对齐)
const changedInUi = changed.filter((p) => p.startsWith('plus-ui/')).map((p) => p.slice('plus-ui/'.length));

const errorsInChanged = [...errorFileSet].filter((f) => changedInUi.includes(f));
const totalErrors = errorFileSet.size;

if (errorsInChanged.length === 0) {
  console.log(`[check-tsc-diff] 通过: 全项目既有错误 ${totalErrors} 个(基线), 变更文件无新增类型错误`);
  process.exit(0);
}
console.error(`[check-tsc-diff] 失败: 变更文件存在类型错误 ${errorsInChanged.length} 个:`);
for (const f of errorsInChanged) {
  console.error(`  - ${f}`);
}
console.error(`(全项目既有错误 ${totalErrors} 个不阻断, 仅变更文件阻断; 修复请在改动内完成或说明豁免原因)`);
process.exit(1);
