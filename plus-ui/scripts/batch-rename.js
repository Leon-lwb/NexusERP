#!/usr/bin/env node
/**
 * 批量命名整改脚本
 * 将剩余的 camelCase 目录统一改为 kebab-case，并同步更新所有引用
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SRC_DIR = path.join(__dirname, '..', 'src');

// ========== 目录重命名映射 ==========
// 格式: [旧相对路径, 新相对路径]
const VIEWS_RENAMES = [
  ['erp/bd/serialNumberRule', 'erp/bd/serial-number-rule'],
  ['erp/prd/capacityCalendar', 'erp/prd/capacity-calendar'],
  ['erp/pu/vendorEvaluation', 'erp/pu/vendor-evaluation'],
  ['erp/sch/employeeSchedule', 'erp/sch/employee-schedule'],
  ['erp/wm/salesIssue', 'erp/wm/sales-issue'],
  ['erp/wm/stockAlert', 'erp/wm/stock-alert'],
  ['system/tenantPackage', 'system/tenant-package'],
  ['tool/gen/online/components/DispTag', 'tool/gen/online/components/disp-tag'],
  ['workflow/customForm', 'workflow/custom-form'],
];

const API_RENAMES = [
  ['erp/bd/serialNumberRule', 'erp/bd/serial-number-rule'],
  ['erp/prd/capacityCalendar', 'erp/prd/capacity-calendar'],
  ['erp/prd/workOrderResource', 'erp/prd/work-order-resource'],
  ['erp/pu/vendorEvaluation', 'erp/pu/vendor-evaluation'],
  ['erp/sch/employeeSchedule', 'erp/sch/employee-schedule'],
  ['erp/sch/punchRule', 'erp/sch/punch-rule'],
  ['erp/sch/teamSchedule', 'erp/sch/team-schedule'],
  ['erp/so/customerOrder', 'erp/so/customer-order'],
  ['erp/wm/salesIssue', 'erp/wm/sales-issue'],
  ['erp/wm/stockAlert', 'erp/wm/stock-alert'],
  ['monitor/loginInfo', 'monitor/login-info'],
  ['system/ossConfig', 'system/oss-config'],
  ['system/tenantPackage', 'system/tenant-package'],
  ['workflow/customForm', 'workflow/custom-form'],
  ['workflow/workflowCommon', 'workflow/workflow-common'],
];

// ========== 文件内容替换规则 ==========
// 为每个模块生成精确的路径替换规则
function generateReplacements() {
  const reps = [];

  // 辅助函数：添加替换规则
  const add = (from, to) => reps.push({ from, to });

  // --- serialNumberRule ---
  add('@/api/erp/bd/serialNumberRule', '@/api/erp/bd/serial-number-rule');
  add('@/views/erp/bd/serialNumberRule', '@/views/erp/bd/serial-number-rule');
  // URL 已经是 serial-number-rule，无需替换

  // --- capacityCalendar ---
  add('@/api/erp/prd/capacityCalendar', '@/api/erp/prd/capacity-calendar');
  add('@/views/erp/prd/capacityCalendar', '@/views/erp/prd/capacity-calendar');

  // --- workOrderResource (api only) ---
  add('@/api/erp/prd/workOrderResource', '@/api/erp/prd/work-order-resource');

  // --- vendorEvaluation ---
  add('@/api/erp/pu/vendorEvaluation', '@/api/erp/pu/vendor-evaluation');
  add('@/views/erp/pu/vendorEvaluation', '@/views/erp/pu/vendor-evaluation');
  add("/erp/pu/vendorEvaluation'", "/erp/pu/vendor-evaluation'");
  add('/erp/pu/vendorEvaluation/', '/erp/pu/vendor-evaluation/');
  add('`vendorEvaluation_${', '`vendor-evaluation_${');

  // --- employeeSchedule ---
  add('@/api/erp/sch/employeeSchedule', '@/api/erp/sch/employee-schedule');
  add('@/views/erp/sch/employeeSchedule', '@/views/erp/sch/employee-schedule');
  add("/erp/sch/employeeSchedule'", "/erp/sch/employee-schedule'");
  add('/erp/sch/employeeSchedule/', '/erp/sch/employee-schedule/');
  add('`employeeSchedule_${', '`employee-schedule_${');

  // --- punchRule (api only) ---
  add('@/api/erp/sch/punchRule', '@/api/erp/sch/punch-rule');

  // --- teamSchedule (api only) ---
  add('@/api/erp/sch/teamSchedule', '@/api/erp/sch/team-schedule');

  // --- customerOrder (api only) ---
  add('@/api/erp/so/customerOrder', '@/api/erp/so/customer-order');

  // --- salesIssue ---
  add('@/api/erp/wm/salesIssue', '@/api/erp/wm/sales-issue');
  add('@/views/erp/wm/salesIssue', '@/views/erp/wm/sales-issue');
  add("/erp/wm/salesIssue'", "/erp/wm/sales-issue'");
  add('/erp/wm/salesIssue/', '/erp/wm/sales-issue/');
  add('`salesIssue_${', '`sales-issue_${');

  // --- stockAlert ---
  add('@/api/erp/wm/stockAlert', '@/api/erp/wm/stock-alert');
  add('@/views/erp/wm/stockAlert', '@/views/erp/wm/stock-alert');
  add("/erp/wm/stockAlert'", "/erp/wm/stock-alert'");
  add('/erp/wm/stockAlert/', '/erp/wm/stock-alert/');
  add('`stockAlert_${', '`stock-alert_${');

  // --- loginInfo (api only) ---
  add('@/api/monitor/loginInfo', '@/api/monitor/login-info');

  // --- ossConfig (api only) ---
  add('@/api/system/ossConfig', '@/api/system/oss-config');

  // --- tenantPackage ---
  add('@/api/system/tenantPackage', '@/api/system/tenant-package');
  add('@/views/system/tenantPackage', '@/views/system/tenant-package');
  add('`tenantPackage_${', '`tenant-package_${');

  // --- customForm ---
  add('@/api/workflow/customForm', '@/api/workflow/custom-form');
  add('@/views/workflow/customForm', '@/views/workflow/custom-form');
  add('`customForm_${', '`custom-form_${');

  // --- workflowCommon (api only) ---
  add('@/api/workflow/workflowCommon', '@/api/workflow/workflow-common');

  // --- DispTag (views only, component) ---
  add('@/views/tool/gen/online/components/DispTag', '@/views/tool/gen/online/components/disp-tag');

  return reps;
}

// ========== 工具函数 ==========

function renameDir(baseDir, oldRel, newRel) {
  const oldPath = path.join(baseDir, oldRel);
  const newPath = path.join(baseDir, newRel);

  if (!fs.existsSync(oldPath)) {
    console.log(`  ⚠️ 跳过（不存在）: ${oldRel}`);
    return false;
  }
  if (fs.existsSync(newPath)) {
    console.log(`  ⚠️ 跳过（目标已存在）: ${newRel}`);
    return false;
  }

  fs.renameSync(oldPath, newPath);
  console.log(`  ✅ ${oldRel} → ${newRel}`);
  return true;
}

function walkFiles(dir, extensions, callback) {
  if (!fs.existsSync(dir)) return;
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (!entry.name.startsWith('.') && entry.name !== 'node_modules') {
        walkFiles(fullPath, extensions, callback);
      }
    } else if (entry.isFile() && extensions.some(ext => entry.name.endsWith(ext))) {
      callback(fullPath);
    }
  }
}

function replaceInFile(filePath, replacements) {
  let content = fs.readFileSync(filePath, 'utf-8');
  let modified = false;

  for (const { from, to } of replacements) {
    if (content.includes(from)) {
      content = content.split(from).join(to);
      modified = true;
    }
  }

  if (modified) {
    fs.writeFileSync(filePath, content, 'utf-8');
    const relPath = path.relative(path.join(__dirname, '..'), filePath);
    console.log(`  📝 ${relPath}`);
  }

  return modified;
}

// ========== 主流程 ==========

console.log('\n========== 1. 重命名 Views 目录 ==========');
let viewsRenamed = 0;
for (const [oldRel, newRel] of VIEWS_RENAMES) {
  if (renameDir(path.join(SRC_DIR, 'views'), oldRel, newRel)) viewsRenamed++;
}

console.log('\n========== 2. 重命名 API 目录 ==========');
let apiRenamed = 0;
for (const [oldRel, newRel] of API_RENAMES) {
  if (renameDir(path.join(SRC_DIR, 'api'), oldRel, newRel)) apiRenamed++;
}

console.log('\n========== 3. 替换文件内容 ==========');
const replacements = generateReplacements();
let filesModified = 0;

walkFiles(SRC_DIR, ['.vue', '.ts', '.js', '.tsx'], (filePath) => {
  if (replaceInFile(filePath, replacements)) filesModified++;
});

console.log('\n========== 整改完成 ==========');
console.log(`Views 目录重命名: ${viewsRenamed} 个`);
console.log(`API 目录重命名: ${apiRenamed} 个`);
console.log(`文件内容修改: ${filesModified} 个`);
console.log('==============================\n');
