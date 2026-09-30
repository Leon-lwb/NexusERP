import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse as parseTemplate } from '@vue/compiler-dom';
import { parse as parseSfc } from '@vue/compiler-sfc';
import ts from 'typescript';

const source = readFileSync(new URL('../src/views/erp/pu/requisition/form.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parseSfc(source);
assert.deepEqual(errors, []);
const script = ts.createSourceFile('requisition-form.ts', descriptor.scriptSetup.content, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
const allNodes = (node) => [node, ...(node.children ?? []).flatMap(allNodes)];
const elements = allNodes(parseTemplate(descriptor.template.content)).filter((node) => node.type === 1);
const attr = (node, name) => node.props.find((prop) => prop.type === 6 && prop.name === name)?.value?.content;
const directive = (node, name, argument) =>
  node.props.find((prop) => prop.type === 7 && prop.name === name && (!argument || prop.arg?.content === argument))?.exp?.content;
const sourceField = elements.find((node) => node.tag === 'el-form-item' && attr(node, 'label') === '来源类型');
assert.ok(sourceField, '来源类型表单项应保留');
const select = allNodes(sourceField).find((node) => node.tag === 'el-select' && directive(node, 'model') === 'form.sourceType');
const option = allNodes(sourceField).find((node) => node.tag === 'el-option' && directive(node, 'for')?.includes('sourceTypeOptions'));
assert.ok(select && option, '来源类型选择框及字典选项应保留');

function sourceState() {
  const form = { value: { sourceType: 'MANUAL' } };
  const pu_source_type = {
    value: [
      { value: 'MANUAL', label: '手工' },
      { value: 'REQ_CONVERT', label: '需求转入' },
      { value: 'RECEIVE_LINK', label: '到货联动' },
      { value: 'AI_DECISION', label: 'AI交付风险决策' }
    ]
  };
  const declarations = script.statements.filter(ts.isVariableStatement).flatMap((statement) => statement.declarationList.declarations);
  const names = ['pendingSourceLabels', 'isSourceLedgerPending', 'sourceTypeOptions'];
  const definitions = names.map((name) => {
    const declaration = declarations.find((item) => ts.isIdentifier(item.name) && item.name.text === name);
    assert.ok(declaration?.initializer, `缺少来源状态计算：${name}`);
    return `const ${name} = ${declaration.initializer.getText(script)};`;
  });
  const js = ts.transpileModule(definitions.join('\n'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }
  }).outputText;
  const computed = (fn) => ({
    get value() {
      return fn();
    }
  });
  const state = new Function(
    'computed',
    'form',
    'pu_source_type',
    `${js}\nreturn { pendingSourceLabels, isSourceLedgerPending, sourceTypeOptions };`
  )(computed, form, pu_source_type);
  return { form, ...state };
}
const evaluate = (expression, context) => new Function(...Object.keys(context), `return (${expression});`)(...Object.values(context));

test('新增表单明确提示来源占用未接入，且说明需求转入暂不可用', () => {
  const alert = elements.find((node) => node.tag === 'el-alert' && attr(node, 'title')?.includes('来源占用未接入'));
  assert.ok(alert, '新增表单须常显禁用原因，不能仅靠悬停提示');
  assert.match(attr(alert, 'title'), /需求转入.*暂不(?:可|能)/);
  assert.match(directive(alert, 'if'), /!form\.requisitionId/);
  assert.match(attr(alert, 'title'), /后端|服务端/);
});

test('来源占用尚未接入的来源不可选；MANUAL 和其他合法来源仍可选', () => {
  const state = sourceState();
  const disabled = directive(option, 'bind', 'disabled');
  assert.ok(disabled, '受限字典项应显式置灰');
  for (const sourceType of ['REQ_CONVERT', 'MRP', 'PRD_MRP', 'MATERIAL_REQUIREMENT', 'LOW_STOCK', 'MES_SPARE_LOW_STOCK']) {
    assert.equal(state.isSourceLedgerPending(sourceType), true, `${sourceType} 应受限`);
    assert.equal(evaluate(disabled, { dict: { value: sourceType }, isSourceLedgerPending: state.isSourceLedgerPending }), true);
  }
  for (const sourceType of ['MANUAL', 'RECEIVE_LINK']) {
    assert.equal(state.isSourceLedgerPending(sourceType), false, `${sourceType} 不能被误禁用`);
    assert.equal(evaluate(disabled, { dict: { value: sourceType }, isSourceLedgerPending: state.isSourceLedgerPending }), false);
  }
  const options = state.sourceTypeOptions.value;
  assert.ok(options.some((dict) => dict.value === 'MANUAL'));
  assert.ok(options.some((dict) => dict.value === 'RECEIVE_LINK'));
  assert.ok(
    options.some((dict) => dict.value === 'REQ_CONVERT'),
    '置灰时仍须看得到需求转入字典项'
  );
  assert.ok(!options.some((dict) => dict.value === 'AI_DECISION'), '手工新增仍不能冒充 AI 来源');
});

test('历史来源值可只读识别，未收录字典的 MES 低库存来源也显示名称', () => {
  const state = sourceState();
  const disabled = directive(select, 'bind', 'disabled');
  assert.ok(disabled, '历史受限来源的下拉框应只读');
  const isDisabled = (sourceType) => evaluate(disabled, { form: { sourceType }, isSourceLedgerPending: state.isSourceLedgerPending });
  assert.equal(isDisabled('MANUAL'), false);
  assert.equal(isDisabled('RECEIVE_LINK'), false);
  assert.equal(isDisabled('REQ_CONVERT'), true);
  assert.equal(isDisabled('MES_SPARE_LOW_STOCK'), true);
  assert.equal(isDisabled('AI_DECISION'), true);
  state.form.value.sourceType = 'REQ_CONVERT';
  assert.equal(state.sourceTypeOptions.value.find((dict) => dict.value === 'REQ_CONVERT')?.label, '需求转入');
  state.form.value.sourceType = 'MES_SPARE_LOW_STOCK';
  assert.match(state.sourceTypeOptions.value.find((dict) => dict.value === 'MES_SPARE_LOW_STOCK')?.label ?? '', /备件.*低库存/);
  state.form.value.sourceType = 'AI_DECISION';
  assert.ok(
    state.sourceTypeOptions.value.some((dict) => dict.value === 'AI_DECISION'),
    '已有 AI 单据仍可只读显示'
  );
});
