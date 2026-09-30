import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc';
import ts from 'typescript';

/** 只读且限定文件范围的检查，不执行项目类型检查、不启动浏览器或服务，也不生成文件。 */
const require = createRequire(import.meta.url);
const components = new Map();

function scriptAst(filename, source) {
  const ast = ts.createSourceFile(filename, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  assert.equal(ast.parseDiagnostics.length, 0, `${filename}: script must parse without errors`);
  return ast;
}

function loadComponent(filename) {
  if (!components.has(filename)) {
    const source = readFileSync(new URL(`../${filename}`, import.meta.url), 'utf8');
    const { descriptor, errors } = parse(source, { filename });
    assert.deepEqual(errors, [], `${filename}: SFC must parse without errors`);
    const script = descriptor.scriptSetup ?? descriptor.script;
    assert.ok(script, `${filename}: script block is required`);
    const compiled = compileScript(descriptor, { id: 'workflow-explicit-imports' });
    const template = compileTemplate({
      source: descriptor.template.content,
      filename,
      id: 'workflow-explicit-imports',
      compilerOptions: { bindingMetadata: compiled.bindings }
    });
    assert.deepEqual(template.errors, [], `${filename}: template must compile without errors`);
    components.set(filename, { descriptor, ast: scriptAst(filename, script.content), compiled, template });
  }
  return components.get(filename);
}

function namedImport(ast, name) {
  const declaration = ast.statements.find(
    (statement) =>
      ts.isImportDeclaration(statement) &&
      statement.moduleSpecifier.text === 'element-plus' &&
      statement.importClause?.namedBindings &&
      ts.isNamedImports(statement.importClause.namedBindings) &&
      statement.importClause.namedBindings.elements.some((specifier) => specifier.name.text === name)
  );
  if (!declaration) return undefined;
  const specifier = declaration.importClause.namedBindings.elements.find((item) => item.name.text === name);
  return { declaration, specifier };
}

function assertValueImport(ast, name) {
  const imported = namedImport(ast, name);
  assert.ok(imported, `${ast.fileName}: missing explicit element-plus value import for ${name}`);
  const { declaration, specifier } = imported;
  assert.equal((specifier.propertyName ?? specifier.name).text, name, `${name}: import must resolve to the real component`);
  assert.equal(declaration.importClause.isTypeOnly, false, `${name}: runtime use requires a value import`);
  assert.equal(specifier.isTypeOnly, false, `${name}: runtime use cannot use an erased type-only specifier`);
}

function templateElements(node) {
  return [node, ...(node.children ?? []).flatMap(templateElements), ...(node.branches ?? []).flatMap(templateElements)];
}

function findNodes(ast, predicate) {
  const nodes = [];
  function visit(node) {
    if (predicate(node)) nodes.push(node);
    ts.forEachChild(node, visit);
  }
  visit(ast);
  return nodes;
}

function assertInstanceRef(ast, refName) {
  const declarations = findNodes(ast, (node) => ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.name.text === refName);
  assert.equal(declarations.length, 1, `${refName}: template ref must remain declared exactly once`);
  const declaration = declarations[0];
  assert.equal(declaration.type, undefined, `${refName}: do not widen the ref to an explicit any`);
  const initializer = declaration.initializer;
  assert.ok(initializer && ts.isCallExpression(initializer), `${refName}: use ref<FormInstance>(), without assertions`);
  assert.ok(ts.isIdentifier(initializer.expression) && initializer.expression.text === 'ref', `${refName}: retain Vue ref`);
  assert.equal(initializer.typeArguments?.length, 1, `${refName}: retain the explicit instance type`);
  const type = initializer.typeArguments[0];
  assert.ok(ts.isTypeReferenceNode(type) && ts.isIdentifier(type.typeName) && type.typeName.text === 'FormInstance');
  assert.equal(type.typeArguments, undefined, `${refName}: use the real FormInstance type, not an any specialization`);
  assert.equal(initializer.arguments.length, 0, `${refName}: never initialize a template ref with the component definition`);
}

function formMethodCalls(ast, refName) {
  return findNodes(ast, (node) => {
    if (!ts.isCallExpression(node) || !ts.isPropertyAccessExpression(node.expression)) return false;
    const method = node.expression;
    const value = method.expression;
    return (
      ['resetFields', 'validate'].includes(method.name.text) &&
      ts.isPropertyAccessExpression(value) &&
      value.name.text === 'value' &&
      ts.isIdentifier(value.expression) &&
      value.expression.text === refName
    );
  });
}

test('real Element Plus exports an instance type; ElForm itself has no resetFields/validate methods', () => {
  const packagePath = require.resolve('element-plus/package.json');
  const packageRoot = dirname(packagePath);
  const packageInfo = JSON.parse(readFileSync(packagePath, 'utf8'));
  const typesPath = resolve(packageRoot, packageInfo.types);
  const exported = scriptAst(typesPath, readFileSync(typesPath, 'utf8'));
  assert.ok(
    exported.statements.some(
      (statement) =>
        ts.isExportDeclaration(statement) &&
        statement.exportClause &&
        ts.isNamedExports(statement.exportClause) &&
        statement.exportClause.elements.some((specifier) => specifier.name.text === 'FormInstance')
    ),
    'the installed element-plus package must really export FormInstance'
  );
  const formTypesPath = resolve(packageRoot, 'es/components/form/index.d.ts');
  const formTypes = scriptAst(formTypesPath, readFileSync(formTypesPath, 'utf8'));
  const alias = formTypes.statements.find((statement) => ts.isTypeAliasDeclaration(statement) && statement.name.text === 'FormInstance');
  assert.ok(alias && ts.isIntersectionTypeNode(alias.type));
  assert.ok(
    alias.type.types.some(
      (type) =>
        ts.isTypeReferenceNode(type) &&
        ts.isIdentifier(type.typeName) &&
        type.typeName.text === 'InstanceType' &&
        ts.isTypeQueryNode(type.typeArguments[0])
    ),
    'FormInstance must retain the actual component instance type, not any'
  );
  const { ElForm } = require('element-plus');
  const { ref, toRaw } = require('vue');
  const constructorRef = ref(ElForm);
  assert.equal(toRaw(constructorRef.value), ElForm);
  assert.equal(typeof constructorRef.value.resetFields, 'undefined');
  assert.equal(typeof constructorRef.value.validate, 'undefined');
  assert.throws(() => constructorRef.value.resetFields(), TypeError);
  assert.throws(() => constructorRef.value.validate(() => {}), TypeError);
});

test('FlowCheckbox registers the real value-imported ElCheckbox used by its template', () => {
  const component = loadComponent('src/components/Flow/FlowCheckbox.vue');
  assertValueImport(component.ast, 'ElCheckbox');
  assert.ok(templateElements(component.template.ast).some((node) => node.tag === 'el-checkbox'));
  const { outputText } = ts.transpileModule(component.descriptor.script.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  });
  const exports = {};
  runInNewContext(outputText, { exports, require });
  assert.equal(exports.default.components.ElCheckbox, require('element-plus').ElCheckbox);
});

const formRefs = [
  ['model/index.vue', 'modelFormRef', ['resetFields', 'validate']],
  ['model/index.vue', 'queryFormRef', ['resetFields']],
  ['work/claim.vue', 'queryFormRef', ['resetFields']],
  ['work/copy.vue', 'queryFormRef', ['resetFields']],
  ['work/finished.vue', 'queryFormRef', ['resetFields']],
  ['work/index.vue', 'queryForm', ['resetFields']],
  ['work/initiate.vue', 'queryFormRef', ['resetFields']],
  ['work/own.vue', 'queryFormRef', ['resetFields']],
  ['work/todo.vue', 'queryFormRef', ['resetFields']]
];

for (const [page, refName, methods] of formRefs) {
  test(`${page}: ${refName} is a typed FormInstance template ref, not ref(ElForm)`, () => {
    const filename = `src/views/workflow/${page}`;
    const component = loadComponent(filename);
    const imported = namedImport(component.ast, 'FormInstance');
    assert.ok(imported, `${filename}: import the real FormInstance type`);
    assert.equal(imported.declaration.importClause.isTypeOnly, true, `${filename}: use import type for FormInstance`);
    assert.equal((imported.specifier.propertyName ?? imported.specifier.name).text, 'FormInstance');
    assert.equal(namedImport(component.ast, 'ElForm'), undefined, `${filename}: remove the unused component-definition import`);
    assertInstanceRef(component.ast, refName);
    assert.equal(component.compiled.imports.FormInstance.source, 'element-plus');
    assert.equal(component.compiled.imports.FormInstance.isType, true);
    assert.ok(component.compiled.bindings[refName]?.startsWith('setup-'), `${refName}: retain the script-setup binding`);
    assert.ok(
      templateElements(component.template.ast).some(
        (node) => node.tag === 'el-form' && node.props.some((prop) => prop.type === 6 && prop.name === 'ref' && prop.value?.content === refName)
      ),
      `${refName}: retain the matching el-form template ref`
    );
    const { outputText } = ts.transpileModule(component.compiled.content, {
      compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 }
    });
    const emitted = scriptAst(`${filename}.emitted.js`, outputText);
    assert.equal(namedImport(emitted, 'FormInstance'), undefined, 'a type-only import must be erased at runtime');
    assert.equal(namedImport(emitted, 'ElForm'), undefined, 'do not reintroduce a component-definition value import');
  });

  test(`${page}: ${refName} reset/validate calls are safe before mounting`, () => {
    const component = loadComponent(`src/views/workflow/${page}`);
    const calls = formMethodCalls(component.ast, refName);
    assert.deepEqual(calls.map((call) => call.expression.name.text).sort(), [...methods].sort(), 'retain all existing form method calls');
    for (const call of calls) {
      assert.ok(call.expression.questionDotToken, `${refName}.value must be guarded before calling ${call.expression.name.text}`);
      const { outputText } = ts.transpileModule(`${call.getText(component.ast)};`, {
        compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
      });
      /** 仅使用真实的空 Vue ref 执行实际的方法调用表达式。 */
      /** 不调用校验回调，也不执行业务或 API 代码。 */
      assert.doesNotThrow(() => runInNewContext(outputText, { [refName]: require('vue').ref() }));
    }
  });
}
