import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';
import { fileURLToPath } from 'node:url';
import { build, transformSync } from 'esbuild';
import { parse } from '@vue/compiler-sfc';
import ts from 'typescript';
import validatorModule from 'async-validator';

const Schema = validatorModule.default ?? validatorModule;
const menuSource = readFileSync(new URL('./index.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parse(menuSource);
assert.deepEqual(errors, [], '实际菜单组件必须可以解析');

// 编译现有纯校验 helper，不加载 Pinia、鉴权、API 或任何服务。
const { outputFiles } = await build({
  entryPoints: [fileURLToPath(new URL('../../../utils/validate.ts', import.meta.url))],
  bundle: true,
  write: false,
  format: 'esm',
  platform: 'node',
  logLevel: 'silent'
});
const helpers = await import(`data:text/javascript;base64,${Buffer.from(outputFiles[0].text).toString('base64')}`);

// 提取并执行实际表单 rules，避免在测试中复制一份权限规则造成假绿。
const source = ts.createSourceFile('menu.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
let rulesExpression;
function findRules(node) {
  if (ts.isVariableDeclaration(node) && node.name.getText(source) === 'data' && ts.isCallExpression(node.initializer)) {
    const data = node.initializer.arguments[0];
    assert.ok(ts.isObjectLiteralExpression(data));
    const rules = data.properties.find((property) => property.name?.getText(source) === 'rules');
    assert.ok(rules && ts.isPropertyAssignment(rules), '实际表单须声明 rules');
    rulesExpression = rules.initializer.getText(source);
  }
  ts.forEachChild(node, findRules);
}
findRules(source);
assert.ok(rulesExpression, '必须读取实际菜单表单而非测试替身');
const rules = runInNewContext(transformSync(`(${rulesExpression})`, { loader: 'ts' }).code, { ...helpers, Error });
// Element Plus 按字段校验；本回归只验证实际 perms 规则，不牵涉其他菜单字段。
const schema = new Schema({ perms: rules.perms ?? [] });
const menu = { menuName: '离线权限测试', orderNum: 1, path: 'permission-test' };

for (const permission of [
  undefined,
  null,
  '',
  'system:user:list',
  'tool:build:list',
  'pu:order:terms:add',
  'pu:order:terms:confirm',
  'pu:order:terms:review:confirm',
  'A1_b:C2_d:E3_f',
  '1:2:3',
  'system:*:*',
  'system:user:*',
  'system:u*er:li*t',
  'pu:order:terms:*',
  'pu:*:terms:*'
]) {
  test(`实际 PC 表单允许权限 ${JSON.stringify(permission)}`, async () => {
    await schema.validate({ ...menu, perms: permission });
  });
}

for (const permission of [
  'system',
  'system:user',
  '*',
  '*:*:*',
  'sys*tem:user:list',
  ':user:list',
  'system::list',
  'system:user:',
  'system:user:list:',
  'system:user::list',
  'system:user:terms::add',
  'system:user:terms:add:',
  ' system:user:list',
  'system:user:list ',
  'system:us er:list',
  ' ',
  '\t',
  '\n',
  '\r',
  '\r\n',
  'system:user:list\n',
  'system:user:list\r\n',
  '\nsystem:user:list',
  'system:user:li\nst',
  'system:user:li\tst',
  'system:user:list\u2028',
  'system:user:list\u2029',
  'system:user:list\u00a0',
  'system:user:list\u200b',
  'system:user:li-st',
  'system:user:li.st',
  'system:user:list/add',
  'system:user:list,edit',
  'system:user:list;edit',
  'system:user:权限',
  'system：user：list'
]) {
  test(`实际 PC 表单拒绝权限 ${JSON.stringify(permission)}`, async () => {
    await assert.rejects(schema.validate({ ...menu, perms: permission }), (error) => Boolean(error.fields?.perms));
  });
}

test('权限输入框接入 perms 字段校验，保留 100 字符上限并说明至少三段', () => {
  assert.match(descriptor.template.content, /<el-form-item\s+prop="perms">/);
  assert.match(descriptor.template.content, /v-model="form\.perms"[^>]*maxlength="100"/);
  assert.match(descriptor.template.content, /至少三段/);
  assert.match(descriptor.template.content, /pu:order:terms:add/);
});

// 读取真实 npm scripts，仅解析仓库现有显式 && 链，不执行 shell 或递归启动测试。
const { scripts } = JSON.parse(readFileSync(new URL('../../../../package.json', import.meta.url), 'utf8'));
const permissionSuite = 'src/views/system/menu/permission-validation.test.mjs';
function calledScripts(command) {
  return command.split(/\s*&&\s*/).flatMap((part) => {
    const match = /^npm(?:\.cmd)? run ([\w:-]+)$/.exec(part.trim());
    return match ? [match[1]] : [];
  });
}
function registeredSerialSuites(command) {
  const prefix = 'node --test --test-concurrency=1 ';
  return command
    .split(/\s*&&\s*/)
    .filter((part) => part.trim().startsWith(prefix))
    .flatMap((part) => part.trim().slice(prefix.length).trim().split(/\s+/));
}

test('默认 unit 链实际调用 full-flow UI 回归', () => {
  assert.ok(calledScripts(scripts['test:unit']).includes('test:full-flow-ui'));
});

test('full-flow 显式串行 node --test 清单执行权限套件且只注册一次', () => {
  assert.equal(registeredSerialSuites(scripts['test:full-flow-ui']).filter((suite) => suite === permissionSuite).length, 1);
});

test('只 echo 权限套件文件名不能伪装成测试接线', () => {
  const fake = scripts['test:full-flow-ui'].replace(permissionSuite, '') + ` && echo ${permissionSuite}`;
  assert.equal(registeredSerialSuites(fake).includes(permissionSuite), false);
});

test('只 echo npm run 调用不能伪装成默认链可达', () => {
  const fake = scripts['test:unit'].replace('npm run test:full-flow-ui', 'echo npm run test:full-flow-ui');
  assert.equal(calledScripts(fake).includes('test:full-flow-ui'), false);
});

const hundredCharacterPermission = 'system:user:' + 'a'.repeat(88);
const oversizedPermission = hundredCharacterPermission + 'a';

test('真实 helper 允许格式合法的 100 字符权限', () => {
  assert.equal(hundredCharacterPermission.length, 100);
  assert.equal(helpers.validPermission(hundredCharacterPermission), true);
});

test('真实 helper 拒绝格式合法但超过 100 字符的权限', () => {
  assert.equal(oversizedPermission.length, 101);
  assert.equal(helpers.validPermission(oversizedPermission), false);
});

test('实际表单 validator 允许程序赋值的 100 字符权限', async () => {
  await schema.validate({ ...menu, perms: hundredCharacterPermission });
});

test('实际表单 validator 拒绝程序赋值的 101 字符权限并提示长度上限', async () => {
  await assert.rejects(schema.validate({ ...menu, perms: oversizedPermission }), (error) =>
    Boolean(error.fields?.perms?.some((failure) => /100/.test(failure.message)))
  );
});
