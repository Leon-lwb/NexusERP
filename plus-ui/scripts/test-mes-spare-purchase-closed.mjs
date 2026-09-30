import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse as parseTemplate } from '@vue/compiler-dom';
import { parse as parseSfc } from '@vue/compiler-sfc';

const source = readFileSync(new URL('../src/views/mes/eq/monitor/index.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parseSfc(source);
assert.equal(errors.length, 0);
const nodes = (node) => [node, ...(node.children ?? []).flatMap(nodes)];
const elements = nodes(parseTemplate(descriptor.template.content)).filter((node) => node.type === 1);
const text = (node) =>
  nodes(node)
    .filter((child) => child.type === 2)
    .map((child) => child.content)
    .join('');
const lowStockCard = elements.find(
  (node) => node.tag === 'el-card' && nodes(node).some((child) => child.tag === 'span' && text(child).includes('低库存告警'))
);
assert.ok(lowStockCard, '低库存告警卡片应保留');
const inCard = nodes(lowStockCard).filter((node) => node.type === 1);
const attr = (node, name) => node.props.find((prop) => prop.type === 6 && prop.name === name)?.value?.content;

test('低库存请购按钮禁用、无点击入口，原权限限制保留', () => {
  const button = inCard.find((node) => node.tag === 'el-button' && text(node).includes('一键生成采购申请'));
  assert.ok(button, '请购按钮应可见以提示功能暂停');
  assert.ok(
    button.props.some((prop) => prop.type === 6 && prop.name === 'disabled'),
    '按钮必须静态禁用'
  );
  assert.ok(!button.props.some((prop) => prop.type === 7 && prop.name === 'on' && prop.arg?.content === 'click'), '禁用按钮不应挂提交事件');
  assert.ok(button.props.some((prop) => prop.type === 7 && prop.name === 'hasPermi' && prop.exp?.content.includes('mes:eq:spare:purchase')));
});

test('低库存卡片常显中文暂停原因，不依赖悬停或有库存', () => {
  const alert = inCard.find((node) => node.tag === 'el-alert');
  assert.ok(alert, '应在卡片中常显告警说明');
  const message = attr(alert, 'title') ?? text(alert);
  assert.match(message, /低库存.*请购.*已暂停/);
  assert.match(message, /来源占用/);
  assert.match(message, /同物料.*防重/);
  assert.match(message, /后端门禁/);
  assert.match(message, /暂不(?:可|能).*采购申请/);
  assert.ok(!alert.props.some((prop) => prop.type === 7 && ['if', 'show'].includes(prop.name)), '暂停提示不能因库存状态而隐藏');
});

test('暂停期间前端不发送请购请求或显示提交成功，低库存列表仍展示', () => {
  assert.doesNotMatch(descriptor.scriptSetup.content, /\bpurchaseRequest\b|\bhandlePurchaseRequest\b|\bmsgSuccess\b/);
  assert.doesNotMatch(source, /已提交 ERP 采购申请生成请求/);
  assert.ok(
    inCard.some(
      (node) =>
        node.tag === 'el-table' &&
        node.props.some((prop) => prop.type === 7 && prop.name === 'bind' && prop.arg?.content === 'data' && prop.exp?.content === 'lowStockList')
    )
  );
});
