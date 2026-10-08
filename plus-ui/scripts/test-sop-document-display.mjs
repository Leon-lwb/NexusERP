import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

// createRequire also supports NODE_PATH when this isolated worktree has no dependencies.
const require = createRequire(import.meta.url);
const { build } = require('esbuild');
const { parse } = require('@vue/compiler-sfc');
const ts = require('typescript');
const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const helperPath = resolve(uiRoot, 'src/views/mes/sop/document/sopDocumentDisplay.ts');
const pagePath = resolve(uiRoot, 'src/views/mes/sop/document/index.vue');
let helperModule;

async function displayHelper() {
  assert.ok(existsSync(helperPath), 'the pure sopDocumentDisplay.ts helper must exist');
  helperModule ??= (async () => {
    const output = await build({ entryPoints: [helperPath], bundle: true, write: false, format: 'esm', platform: 'neutral', logLevel: 'silent' });
    return import(`data:text/javascript;base64,${Buffer.from(output.outputFiles[0].contents).toString('base64')}`);
  })();
  const result = await helperModule;
  for (const name of ['sopTypeForDisplay', 'canPreviewPendingDocument', 'latestPendingVersion']) {
    assert.equal(typeof result[name], 'function', `export ${name}`);
  }
  return result;
}

function sfc() {
  const source = readFileSync(pagePath, 'utf8');
  const { descriptor, errors } = parse(source, { filename: pagePath });
  assert.deepEqual(errors, [], 'the SOP page must remain a parseable SFC');
  assert.ok(descriptor.template?.ast);
  return { template: descriptor.template.ast, script: descriptor.scriptSetup.content };
}

function elements(node, tag) {
  const found = [];
  function visit(current) {
    if (current.type === 1 && current.tag === tag) found.push(current);
    for (const child of current.children ?? []) visit(child);
  }
  visit(node);
  return found;
}

function directive(node, name, arg) {
  return node.props.find((prop) => prop.type === 7 && prop.name === name && (!arg || prop.arg?.content === arg))?.exp?.content;
}

function attribute(node, name) {
  return node.props.find((prop) => prop.type === 6 && prop.name === name)?.value?.content;
}

function handlerCode(names) {
  const { script } = sfc();
  const ast = ts.createSourceFile(pagePath, script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  return names
    .map((name) => {
      const declaration = ast.statements
        .filter(ts.isVariableStatement)
        .flatMap((statement) => statement.declarationList.declarations)
        .find((item) => ts.isIdentifier(item.name) && item.name.text === name);
      assert.ok(declaration?.initializer, `${name} must be wired in the real page script`);
      return `const ${name} = ${declaration.initializer.getText(ast)};`;
    })
    .join('\n');
}

async function pendingHarness(versions) {
  const helpers = await displayHelper();
  const requests = [];
  const warnings = [];
  const previewDialog = { visible: false, title: '', fileUrl: '', fileType: '' };
  const context = {
    ...helpers,
    previewDialog,
    listVersionBySop: async (sopId) => {
      requests.push(sopId);
      return { data: versions };
    },
    proxy: { $modal: { msgWarning: (message) => warnings.push(message) } },
    router: { push: () => assert.fail('pending preview must not enter the signed SOP viewer') },
    handleView: () => assert.fail('pending preview must not register SOP_VIEW'),
    handlePreview: () => assert.fail('pending preview must not preview the document current file')
  };
  const code = ts.transpile(handlerCode(['handlePreviewVersion', 'handlePreviewPending']), { target: ts.ScriptTarget.ES2020 });
  const handle = new Function(...Object.keys(context), `"use strict"; ${code}; return handlePreviewPending;`)(...Object.values(context));
  return { handle, previewDialog, requests, warnings };
}

const version = (status, versionNo, fileUrl = `https://preview.invalid/${versionNo}.pdf`) =>
  Object.freeze({
    versionId: 10,
    sopId: 1,
    versionNo,
    status,
    fileOssId: '100',
    fileType: 'PDF',
    fileUrl
  });
const document = (status = 'DRAFT') =>
  Object.freeze({
    sopId: '9007199254740993001',
    sopName: '装配SOP',
    sopType: 'PROCESS',
    status,
    currentVersion: 'V1.0',
    fileOssId: '0',
    fileType: 'PDF',
    fileUrl: 'https://preview.invalid/current.pdf'
  });

test('only displayed legacy PROCESS maps to the PRODUCTION dictionary value', async () => {
  const { sopTypeForDisplay } = await displayHelper();
  assert.equal(sopTypeForDisplay('PROCESS'), 'PRODUCTION');
  for (const value of ['PRODUCTION', 'INSPECTION', 'CLEAN', 'OTHER', 'UNKNOWN', 'process', ' PROCESS ', '', undefined, null]) {
    assert.equal(sopTypeForDisplay(value), value, `do not invent a label for ${JSON.stringify(value)}`);
  }
});

test('pending preview is available only for DRAFT and IN_REVIEW documents', async () => {
  const { canPreviewPendingDocument } = await displayHelper();
  for (const status of ['DRAFT', 'IN_REVIEW']) assert.equal(canPreviewPendingDocument(status), true);
  for (const status of ['PUBLISHED', 'ARCHIVED', 'PENDING', 'UNKNOWN', '', undefined]) assert.equal(canPreviewPendingDocument(status), false);
});

test('select the latest PENDING in server order without sorting or changing version statuses', async () => {
  const { latestPendingVersion } = await displayHelper();
  const current = version('CURRENT', 'V1.0');
  const newest = version('PENDING', 'V1.2');
  const older = version('PENDING', 'V1.1');
  const history = version('HISTORICAL', 'V0.9');
  const versions = Object.freeze([current, newest, history, older]);
  assert.equal(latestPendingVersion(versions), newest);
  assert.deepEqual(versions, [current, newest, history, older]);
});

test('never fall back to CURRENT or HISTORICAL when no PENDING exists', async () => {
  const { latestPendingVersion } = await displayHelper();
  assert.equal(latestPendingVersion([]), undefined);
  assert.equal(latestPendingVersion([version('CURRENT', 'V1.0'), version('HISTORICAL', 'V0.9')]), undefined);
});

test('a missing latest PENDING file must not select an older or current attachment', async () => {
  const { latestPendingVersion } = await displayHelper();
  const noFile = Object.freeze({ ...version('PENDING', 'V1.2'), fileUrl: undefined });
  assert.equal(latestPendingVersion([noFile, version('PENDING', 'V1.1'), version('CURRENT', 'V1.0')]), noFile);
});

test('the type DictTag uses display normalization, not mutation of list or form data', () => {
  const { template, script } = sfc();
  const column = elements(template, 'el-table-column').find((node) => attribute(node, 'prop') === 'sopType');
  const tag = elements(column, 'dict-tag')[0];
  assert.equal(directive(tag, 'bind', 'options'), 'mes_sop_type');
  assert.equal(directive(tag, 'bind', 'value'), 'sopTypeForDisplay(scope.row.sopType)');
  assert.match(script, /from\s*['"]\.\/sopDocumentDisplay['"]/);
  assert.doesNotMatch(script, /(?:row|form(?:\.value)?)\.sopType\s*=/);
});

test('待审批稿 button uses explicit status gating, list permission and its own handler', () => {
  const { template } = sfc();
  const buttons = elements(template, 'el-button').filter((node) =>
    node.children.some((child) => child.type === 2 && child.content.trim() === '待审批稿')
  );
  assert.equal(buttons.length, 1, 'add one explicitly labelled pending-draft button');
  assert.equal(directive(buttons[0], 'if'), 'canPreviewPendingDocument(scope.row.status)');
  assert.equal(directive(buttons[0], 'on', 'click'), 'handlePreviewPending(scope.row)');
  assert.equal(directive(buttons[0], 'hasPermi'), "['mes:sop:document:list']");
});

test('current version metadata and the history/version-preview entry remain usable', () => {
  const { template } = sfc();
  const current = elements(template, 'el-table-column').find((node) => attribute(node, 'label') === '当前版本');
  assert.equal(attribute(current, 'prop'), 'currentVersion');
  assert.ok(elements(template, 'el-button').some((node) => directive(node, 'on', 'click') === 'handleVersions(scope.row)'));
  assert.ok(elements(template, 'el-button').some((node) => directive(node, 'on', 'click') === 'handlePreviewVersion(scope.row)'));
  assert.match(handlerCode(['handleVersions']), /await listVersionBySop\(row\.sopId\)/);
  assert.match(handlerCode(['handleVersions']), /historyDialog\.list\s*=\s*res\.data/);
  assert.match(handlerCode(['handleVersions']), /historyDialog\.visible\s*=\s*true/);
});

for (const status of ['DRAFT', 'IN_REVIEW']) {
  test(`${status} pending handler loads versions and opens the selected PDF in the existing dialog without SOP_VIEW`, async () => {
    const pending = version('PENDING', 'V1.1');
    const harness = await pendingHarness([version('CURRENT', 'V1.0'), pending]);
    const row = document(status);
    await harness.handle(row);
    assert.deepEqual(harness.requests, [row.sopId], 'keep the original long ID verbatim');
    assert.deepEqual(harness.warnings, []);
    assert.equal(harness.previewDialog.visible, true);
    assert.equal(harness.previewDialog.fileUrl, pending.fileUrl);
    assert.equal(harness.previewDialog.fileType, 'PDF');
    assert.match(harness.previewDialog.title, /待审批稿/);
    assert.ok(harness.previewDialog.title.includes(pending.versionNo));
    assert.doesNotMatch(harness.previewDialog.title, /当前|已发布/);
    assert.equal(row.currentVersion, 'V1.0');
    assert.equal(row.fileOssId, '0');
    assert.equal(row.sopType, 'PROCESS');
  });
}

for (const [name, versions] of [
  ['no pending revision', [version('CURRENT', 'V1.0')]],
  ['no revisions', []],
  ['pending without a file URL', [Object.freeze({ ...version('PENDING', 'V1.2'), fileUrl: '' }), version('PENDING', 'V1.1')]]
]) {
  test(`pending handler warns without opening a fallback file when ${name}`, async () => {
    const harness = await pendingHarness(versions);
    await harness.handle(document());
    assert.equal(harness.previewDialog.visible, false);
    assert.equal(harness.previewDialog.fileUrl, '');
    assert.equal(harness.warnings.length, 1);
    assert.match(harness.warnings[0], /待审批稿/);
  });
}

test('the pending handler also refuses published or archived rows outside the template guard', async () => {
  for (const status of ['PUBLISHED', 'ARCHIVED']) {
    const harness = await pendingHarness([version('PENDING', 'V1.1')]);
    await harness.handle(document(status));
    assert.deepEqual(harness.requests, []);
    assert.equal(harness.previewDialog.visible, false);
  }
});
