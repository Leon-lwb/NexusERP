#!/usr/bin/env node
/**
 * 文件命名规范扫描脚本
 *
 * 扫描范围：src/views, src/api, src/components, src/layout/components
 * 规范依据：docs/NAMING_CONVENTION.md
 *
 * 用法：
 *   node scripts/lint-naming.js
 *   node scripts/lint-naming.js --json
 *   node scripts/lint-naming.js --scope views
 *   node scripts/lint-naming.js --scope api
 *   node scripts/lint-naming.js --scope components
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PROJECT_ROOT = path.resolve(__dirname, '..');
const SRC_DIR = path.join(PROJECT_ROOT, 'src');

// 页面级 Vue 文件白名单（允许全小写）
const PAGE_VUE_WHITELIST = new Set([
  'index.vue',
  'form.vue',
  'detail.vue',
  'list.vue',
  'config.vue',
  'part.vue'
]);

// ==================== 命名转换工具函数 ====================

function toKebabCase(str) {
  return str
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
    .toLowerCase();
}

function toPascalCase(str) {
  const ext = path.extname(str);
  const base = path.basename(str, ext);

  const words = base
    .replace(/[-_]/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .split(/\s+/)
    .filter(Boolean);

  const pascal = words
    .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join('');

  return pascal + ext;
}

// ==================== 文件系统遍历 ====================

function walk(dir, pattern, type = 'file', callback, relativeBase = '') {
  if (!fs.existsSync(dir)) return;

  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    const relativePath = path.join(relativeBase, entry.name);

    if (entry.isDirectory()) {
      if (type === 'directory' && pattern.test(entry.name)) {
        callback(relativePath, fullPath, entry.name);
      }
      if (!entry.name.startsWith('.') && entry.name !== 'node_modules') {
        walk(fullPath, pattern, type, callback, relativePath);
      }
    } else if (entry.isFile() && type === 'file' && pattern.test(entry.name)) {
      callback(relativePath, fullPath, entry.name);
    }
  }
}

// ==================== 扫描配置 ====================

const SCAN_CONFIG = [
  {
    id: 'views-dir',
    name: 'Views 目录命名',
    description: 'src/views 下的子目录应使用 kebab-case 或全小写，禁止 camelCase/PascalCase',
    basePath: path.join(SRC_DIR, 'views'),
    type: 'directory',
    pattern: /.*/,
    validate: (name) => {
      if (/[A-Z]/.test(name)) {
        return {
          valid: false,
          message: `目录名 "${name}" 包含大写字母，应改为 kebab-case（如 ${toKebabCase(name)}）或全小写`,
          suggest: toKebabCase(name)
        };
      }
      return { valid: true };
    }
  },
  {
    id: 'api-dir',
    name: 'API 目录命名',
    description: 'src/api 下的子目录应使用 kebab-case 或全小写，禁止 camelCase/PascalCase',
    basePath: path.join(SRC_DIR, 'api'),
    type: 'directory',
    pattern: /.*/,
    validate: (name) => {
      if (/[A-Z]/.test(name)) {
        return {
          valid: false,
          message: `目录名 "${name}" 包含大写字母，应改为 kebab-case（如 ${toKebabCase(name)}）或全小写`,
          suggest: toKebabCase(name)
        };
      }
      return { valid: true };
    }
  },
  {
    id: 'components-vue',
    name: '公共组件文件命名',
    description: 'src/components 下的 .vue 文件应使用 PascalCase（index.vue 除外）',
    basePath: path.join(SRC_DIR, 'components'),
    type: 'file',
    pattern: /\.vue$/,
    validate: (name) => {
      if (name === 'index.vue') return { valid: true };
      if (!/^[A-Z]/.test(name)) {
        return {
          valid: false,
          message: `组件文件名 "${name}" 应以大写字母开头（PascalCase），建议改为 ${toPascalCase(name)}`,
          suggest: toPascalCase(name)
        };
      }
      return { valid: true };
    }
  },
  {
    id: 'layout-vue',
    name: '布局组件文件命名',
    description: 'src/layout/components 下的 .vue 文件应使用 PascalCase（index.vue 除外）',
    basePath: path.join(SRC_DIR, 'layout', 'components'),
    type: 'file',
    pattern: /\.vue$/,
    validate: (name) => {
      if (name === 'index.vue') return { valid: true };
      if (!/^[A-Z]/.test(name)) {
        return {
          valid: false,
          message: `组件文件名 "${name}" 应以大写字母开头（PascalCase），建议改为 ${toPascalCase(name)}`,
          suggest: toPascalCase(name)
        };
      }
      return { valid: true };
    }
  },
  {
    id: 'views-vue',
    name: '页面级 Vue 文件命名',
    description: 'src/views 下的 .vue 文件：约定文件/单单词文件使用全小写，多单词业务组件使用 PascalCase',
    basePath: path.join(SRC_DIR, 'views'),
    type: 'file',
    pattern: /\.vue$/,
    validate: (name) => {
      if (PAGE_VUE_WHITELIST.has(name)) return { valid: true };

      const ext = path.extname(name);
      const base = path.basename(name, ext);

      // 单单词全小写（如 login.vue, claim.vue, 404.vue）允许
      if (/^[a-z][a-z0-9]*$/.test(base)) {
        return { valid: true };
      }

      // 多单词但首字母小写（camelCase 或 kebab-case）→ 违规
      if (/^[a-z]/.test(name)) {
        return {
          valid: false,
          message: `业务组件 "${name}" 应使用 PascalCase，建议改为 ${toPascalCase(name)}`,
          suggest: toPascalCase(name)
        };
      }
      return { valid: true };
    }
  }
];

// ==================== 一致性扫描 ====================

function scanConsistency() {
  const issues = [];
  const viewsDir = path.join(SRC_DIR, 'views');
  const apiDir = path.join(SRC_DIR, 'api');

  if (!fs.existsSync(viewsDir) || !fs.existsSync(apiDir)) return issues;

  const viewDirMap = new Map();
  const apiDirMap = new Map();

  walk(viewsDir, /.*/, 'directory', (relPath) => {
    const key = relPath.toLowerCase().replace(/\\/g, '/');
    viewDirMap.set(key, relPath.replace(/\\/g, '/'));
  });

  walk(apiDir, /.*/, 'directory', (relPath) => {
    const key = relPath.toLowerCase().replace(/\\/g, '/');
    apiDirMap.set(key, relPath.replace(/\\/g, '/'));
  });

  for (const [key, apiRelPath] of apiDirMap.entries()) {
    if (viewDirMap.has(key)) {
      const viewRelPath = viewDirMap.get(key);
      if (viewRelPath !== apiRelPath) {
        issues.push({
          type: 'consistency',
          message: 'API 目录与 Views 目录命名不一致',
          detail: `api/${apiRelPath} vs views/${viewRelPath}`,
          apiPath: `api/${apiRelPath}`,
          viewPath: `views/${viewRelPath}`
        });
      }
    }
  }

  return issues;
}

// ==================== 主扫描逻辑 ====================

function runScan(scopeFilter = null) {
  const allIssues = [];

  for (const config of SCAN_CONFIG) {
    if (scopeFilter) {
      const scopeMap = {
        views: ['views-dir', 'views-vue'],
        api: ['api-dir'],
        components: ['components-vue'],
        layout: ['layout-vue']
      };
      const allowedIds = scopeMap[scopeFilter] || [scopeFilter];
      if (!allowedIds.includes(config.id)) continue;
    }

    if (!fs.existsSync(config.basePath)) continue;

    walk(
      config.basePath,
      config.pattern,
      config.type,
      (relPath, fullPath, name) => {
        const result = config.validate(name);
        if (!result.valid) {
          allIssues.push({
            ruleId: config.id,
            ruleName: config.name,
            relativePath: path.relative(PROJECT_ROOT, fullPath).replace(/\\/g, '/'),
            fileName: name,
            message: result.message,
            suggest: result.suggest
          });
        }
      }
    );
  }

  if (!scopeFilter || scopeFilter === 'consistency') {
    const consistencyIssues = scanConsistency();
    for (const issue of consistencyIssues) {
      allIssues.push({
        ruleId: 'consistency',
        ruleName: 'Views/API 目录命名一致性',
        relativePath: `${issue.viewPath} | ${issue.apiPath}`,
        fileName: '-',
        message: issue.message,
        detail: issue.detail
      });
    }
  }

  return allIssues;
}

// ==================== 报告输出 ====================

function printTextReport(issues) {
  console.log('\n============================================');
  console.log('  NexusERP-plus 文件命名规范扫描报告');
  console.log('============================================\n');

  if (issues.length === 0) {
    console.log('✅ 未发现命名规范问题，继续保持！\n');
    return;
  }

  const grouped = {};
  for (const issue of issues) {
    if (!grouped[issue.ruleName]) grouped[issue.ruleName] = [];
    grouped[issue.ruleName].push(issue);
  }

  let total = 0;
  for (const [ruleName, list] of Object.entries(grouped)) {
    console.log(`\n【${ruleName}】— 发现 ${list.length} 个问题`);
    console.log('-'.repeat(60));
    for (const issue of list) {
      total++;
      console.log(`\n  #${total}`);
      console.log(`  位置：${issue.relativePath}`);
      console.log(`  问题：${issue.message}`);
      if (issue.suggest) {
        console.log(`  建议：改为 "${issue.suggest}"`);
      }
    }
  }

  console.log(`\n\n============================================`);
  console.log(`  总计发现问题：${issues.length} 个`);
  console.log(`  规范文档：docs/NAMING_CONVENTION.md`);
  console.log(`============================================\n`);

  process.exitCode = 1;
}

function printJsonReport(issues) {
  const report = {
    timestamp: new Date().toISOString(),
    project: 'nexus-erp/plus-ui',
    totalIssues: issues.length,
    summary: {},
    issues
  };

  for (const issue of issues) {
    report.summary[issue.ruleName] = (report.summary[issue.ruleName] || 0) + 1;
  }

  console.log(JSON.stringify(report, null, 2));
}

// ==================== 命令行入口 ====================

function main() {
  const args = process.argv.slice(2);
  const isJson = args.includes('--json');
  const scopeIdx = args.indexOf('--scope');
  const scope = scopeIdx !== -1 ? args[scopeIdx + 1] : null;

  const issues = runScan(scope);

  if (isJson) {
    printJsonReport(issues);
  } else {
    printTextReport(issues);
  }
}

main();
