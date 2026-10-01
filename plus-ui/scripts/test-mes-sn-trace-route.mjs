import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parse as parseTemplate } from '@vue/compiler-dom';
import { parse as parseSfc } from '@vue/compiler-sfc';
import ts from 'typescript';
import { createMemoryHistory, createRouter } from 'vue-router';

/** 只读取实际源码；路由匹配使用真实 Vue Router，不启动 DOM、服务或网络请求。 */
const snId = '2104806275306942466';
const tracePath = `/mes/pro/sn/trace/${snId}`;
const traceRecordPath = '/mes/pro/sn/trace/:snId';
const traceView = '@/views/mes/pro/sn/trace.vue';
const queryPermission = 'mes:pro:sn:query';
const Layout = Object.freeze({ name: 'LayoutRouteStub', __source: '@/layout/index.vue' });
const readSource = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

/** 解析真实 TypeScript 源码，语法问题必须独立报错，不能冒充缺失路由的 RED。 */
function scriptAst(filename, source = readSource(filename)) {
  const ast = ts.createSourceFile(filename, source, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
  assert.equal(ast.parseDiagnostics.length, 0, `${filename} 必须无语法错误`);
  return ast;
}

const routerAst = scriptAst('src/router/index.ts');
const permissionAst = scriptAst('src/store/modules/permission.ts');

/** 提取顶层变量的真实初始化表达式，不根据正则或测试内自建路由判断存在性。 */
function initializer(ast, name) {
  const declarations = ast.statements
    .filter(ts.isVariableStatement)
    .flatMap((statement) => [...statement.declarationList.declarations])
    .filter((declaration) => ts.isIdentifier(declaration.name) && declaration.name.text === name);
  assert.equal(declarations.length, 1, `${ast.fileName} 必须唯一声明 ${name}`);
  assert.ok(declarations[0].initializer, `${name} 必须有初始化表达式`);
  return declarations[0].initializer;
}

/** 仅执行提取出的表达式，移除类型但不导入整个业务模块，也不写构建产物。 */
function evaluate(expression, context = {}) {
  const { outputText } = ts.transpileModule(`const value = (${expression});`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }
  });
  return new Function(...Object.keys(context), `${outputText}\nreturn value;`)(...Object.values(context));
}

/** 从实际路由数组中仅替换懒加载 import，保持 path、children、meta 等真实结构。 */
function readRoutes(name) {
  const expression = initializer(routerAst, name);
  assert.ok(ts.isArrayLiteralExpression(expression), `${name} 必须来自实际路由数组`);
  const transformed = ts.transform(expression, [
    (context) => {
      const visit = (node) => {
        if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword) {
          assert.equal(node.arguments.length, 1, '懒加载组件必须只有一个模块路径');
          assert.ok(ts.isStringLiteral(node.arguments[0]), '懒加载组件必须使用真实静态模块路径');
          return ts.factory.createCallExpression(ts.factory.createIdentifier('__routeView'), undefined, [node.arguments[0]]);
        }
        return ts.visitEachChild(node, visit, context);
      };
      return (node) => ts.visitNode(node, visit);
    }
  ]);
  try {
    const source = ts.createPrinter().printNode(ts.EmitHint.Expression, transformed.transformed[0], routerAst);
    return evaluate(source, {
      Layout,
      __routeView: (path) => Promise.resolve({ name: 'ViewRouteStub', __source: path })
    });
  } finally {
    transformed.dispose();
  }
}

/** 提取实际处理函数，执行按钮、返回、追溯 API 和权限过滤的现行实现。 */
function binding(ast, name, context) {
  const declaration = ast.statements.find((statement) => ts.isFunctionDeclaration(statement) && statement.name?.text === name);
  return evaluate((declaration ?? initializer(ast, name)).getText(ast), context);
}

/** 与应用一样先注册公共路由，再按真实权限过滤函数调用 addRoute。 */
function routeFixture(permissions) {
  const constantRoutes = readRoutes('constantRoutes');
  const dynamicRoutes = readRoutes('dynamicRoutes');
  const allowedRoutes =
    permissions === undefined
      ? dynamicRoutes
      : binding(permissionAst, 'filterDynamicRoutes', {
          auth: {
            hasPermiOr: (required) => required.some((permission) => permissions.includes(permission)),
            hasRoleOr: () => false
          }
        })(dynamicRoutes);
  const router = createRouter({ history: createMemoryHistory(), routes: constantRoutes });
  allowedRoutes.forEach((route) => router.addRoute(route));
  return { router, constantRoutes, dynamicRoutes };
}

/** 递归检查原始路由名称，防止重复名称导致 Vue Router 静默替换旧记录。 */
function flattenRoutes(routes) {
  return routes.flatMap((route) => [route, ...flattenRoutes(route.children ?? [])]);
}

/** 解析现有 SFC 的模板和脚本，不编译或挂载业务组件。 */
function viewSource(filename) {
  const { descriptor, errors } = parseSfc(readSource(filename), { filename });
  assert.deepEqual(errors, [], `${filename} 必须是可解析的现有组件`);
  assert.ok(descriptor.template?.content, `${filename} 必须有模板`);
  assert.ok(descriptor.scriptSetup?.content, `${filename} 必须有 setup 脚本`);
  return { template: descriptor.template.content, ast: scriptAst(filename, descriptor.scriptSetup.content) };
}

/** 使用实际匹配记录检查 Layout 和叶子组件，不能把 404 或仅父路由匹配当成功。 */
async function assertDetailMatch(resolved, parentPath, view, activeMenu) {
  assert.equal(resolved.matched.length, 2, `${resolved.path} 必须匹配 Layout 和详情子路由，不能落入 404`);
  assert.equal(resolved.matched[0].path, parentPath);
  assert.equal(resolved.matched[0].components.default, Layout, '详情必须位于 Layout 下以提供 app-main');
  const leaf = resolved.matched[1];
  assert.equal(typeof leaf.components.default, 'function', '详情必须保留实际懒加载组件路由');
  assert.equal((await leaf.components.default()).__source, view, '匹配的叶子组件必须是正确详情而非 404');
  assert.equal(resolved.meta.activeMenu, activeMenu);
  assert.ok(typeof resolved.name === 'string' && resolved.name.length > 0, '详情必须有非空路由名称');
}

test('SN 追溯必须是 Layout 下权限受控的隐藏动态详情路由，名称唯一', async () => {
  const { constantRoutes, dynamicRoutes } = routeFixture();
  const records = dynamicRoutes.filter((route) => route.path === traceRecordPath);
  assert.equal(records.length, 1, 'dynamicRoutes 缺少 /mes/pro/sn/trace/:snId 详情路由');
  const route = records[0];
  assert.ok(
    routerAst.statements.some(
      (statement) =>
        ts.isImportDeclaration(statement) &&
        statement.importClause?.name?.text === 'Layout' &&
        statement.moduleSpecifier.text === '@/layout/index.vue'
    )
  );
  assert.equal(route.component, Layout);
  assert.equal(route.hidden, true);
  assert.deepEqual(route.permissions, [queryPermission]);
  assert.equal(route.children.length, 1);
  const child = route.children[0];
  assert.equal(child.path, '');
  assert.equal((await child.component()).__source, traceView);
  assert.equal(child.meta.activeMenu, '/mes/pro/sn');
  assert.equal(child.name, 'MesSnTrace');
  assert.equal(parseSfc(readSource('src/views/mes/pro/sn/trace.vue')).descriptor.scriptSetup.attrs.name, 'MesSnTrace');
  const allRoutes = flattenRoutes([...constantRoutes, ...dynamicRoutes]);
  assert.equal(allRoutes.filter((record) => record.name === child.name).length, 1, 'SN 详情名称不能覆盖相邻路由');
  assert.equal(
    flattenRoutes(constantRoutes).some((record) => record.path === traceRecordPath),
    false,
    'SN 详情不能绕过动态权限注册'
  );
});

test('持有 SN query 权限时，真实 Vue Router 解析详情并原样保留 19 位字符串 snId', async () => {
  const { router } = routeFixture([queryPermission]);
  const resolved = router.resolve(tracePath);
  await assertDetailMatch(resolved, traceRecordPath, traceView, '/mes/pro/sn');
  assert.equal(typeof resolved.params.snId, 'string');
  assert.equal(resolved.params.snId, snId);
  assert.deepEqual(Object.keys(resolved.params), ['snId']);
  assert.equal(resolved.path, tracePath);
  assert.equal(resolved.href, tracePath);
  assert.equal(resolved.name, 'MesSnTrace');
  assert.equal(router.resolve({ name: 'MesSnTrace', params: { snId } }).fullPath, tracePath, '按名称解析也不能丢失雪花 ID 精度');
});

test('无 SN query 权限或仅有 list 权限时，不得注册追溯详情', async () => {
  for (const permissions of [[], ['mes:pro:sn:list']]) {
    const { router } = routeFixture(permissions);
    const resolved = router.resolve(tracePath);
    assert.equal(resolved.matched.length, 1, '没有查询权限时必须落入公共 404');
    assert.equal((await resolved.matched[0].components.default()).__source, '@/views/error/404.vue');
    assert.equal(resolved.params.snId, undefined);
  }
});

test('现有 SN 按钮和详情组件存在，追溯入口、权限、API 导入及返回路径契约一致', () => {
  const list = viewSource('src/views/mes/pro/sn/index.vue');
  const trace = viewSource('src/views/mes/pro/sn/trace.vue');
  const nodes = (node) => [node, ...(node.children ?? []).flatMap(nodes)];
  const buttons = nodes(parseTemplate(list.template)).filter(
    (node) =>
      node.type === 1 &&
      node.tag === 'el-button' &&
      node.props.some(
        (prop) => prop.type === 7 && prop.name === 'on' && prop.arg?.content === 'click' && prop.exp?.content === 'handleTrace(scope.row)'
      )
  );
  assert.equal(buttons.length, 1, '现有追溯按钮必须调用真实 handleTrace');
  const permission = buttons[0].props.find((prop) => prop.type === 7 && prop.name === 'hasPermi');
  assert.ok(permission?.exp?.content, '追溯按钮必须保留查询权限');
  assert.deepEqual(evaluate(permission.exp.content), [queryPermission]);
  const navigations = [];
  const context = { router: { push: (path) => navigations.push(path) } };
  binding(list.ast, 'handleTrace', context)({ snId });
  binding(trace.ast, 'goBack', context)();
  assert.deepEqual(navigations, [tracePath, '/mes/pro/sn']);
  assert.ok(
    trace.ast.statements.some(
      (statement) =>
        ts.isImportDeclaration(statement) &&
        statement.moduleSpecifier.text === '@/api/mes/pro/sn' &&
        statement.importClause?.namedBindings &&
        ts.isNamedImports(statement.importClause.namedBindings) &&
        statement.importClause.namedBindings.elements.some((element) => element.name.text === 'traceSn')
    ),
    '详情必须调用现有 SN 追溯 API'
  );
});

test('真实路由 params 传入现有 loadTrace 和 traceSn 后，GET 路径保留完整 SN ID', async () => {
  const { router } = routeFixture([queryPermission]);
  const resolved = router.resolve(tracePath);
  await assertDetailMatch(resolved, traceRecordPath, traceView, '/mes/pro/sn');
  const trace = viewSource('src/views/mes/pro/sn/trace.vue');
  const requests = [];
  const traceSn = binding(scriptAst('src/api/mes/pro/sn.ts'), 'traceSn', {
    request: async (options) => {
      requests.push(options);
      return { data: { sn: { snId } } };
    }
  });
  const state = {
    loading: { value: true },
    sn: { value: {} },
    forwardSteps: { value: [] },
    children: { value: [] },
    parentChain: { value: [] }
  };
  await binding(trace.ast, 'loadTrace', { route: resolved, traceSn, ...state })();
  assert.deepEqual(requests, [{ url: tracePath, method: 'get' }], '仅记录内存请求，不发送真实 HTTP');
  assert.equal(state.sn.value.snId, snId);
  assert.equal(state.loading.value, false);
});

/** 相邻 MES 表单及详情作为正向对照，检测路由覆盖、参数改名和菜单高亮回归。 */
const neighbors = [
  {
    path: '/mes/pro/workorder/form',
    view: '@/views/mes/pro/workorder/form.vue',
    name: 'MesWorkOrderForm',
    permission: 'mes:pro:work:query',
    activeMenu: '/mes/wo/workorder'
  },
  {
    path: '/mes/pro/workorder/detail/:mesWoId',
    view: '@/views/mes/pro/workorder/detail.vue',
    name: 'MesWorkOrderDetail',
    permission: 'mes:pro:work:query',
    activeMenu: '/mes/wo/workorder',
    param: 'mesWoId'
  },
  {
    path: '/mes/pro/dispatch/detail/:dispatchId',
    view: '@/views/mes/pro/dispatch/detail.vue',
    name: 'MesDispatchDetail',
    permission: 'mes:pro:dispatch:query',
    activeMenu: '/mes/pro/dispatch',
    param: 'dispatchId'
  },
  {
    path: '/mes/pro/flow-card/detail/:cardId',
    view: '@/views/mes/pro/flow-card/detail.vue',
    name: 'MesFlowCardDetail',
    permission: 'mes:pro:flowcard:query',
    activeMenu: '/mes/wo/flow-card',
    param: 'cardId'
  }
];

for (const neighbor of neighbors) {
  test(`相邻 MES 路由保持可解析：${neighbor.name}`, async () => {
    const { router, dynamicRoutes } = routeFixture();
    const record = dynamicRoutes.find((route) => route.path === neighbor.path);
    assert.ok(record, `${neighbor.name} 原有动态路由必须保留`);
    assert.equal(record.hidden, true);
    assert.deepEqual(record.permissions, [neighbor.permission]);
    assert.equal(record.children.length, 1);
    assert.equal(record.children[0].path, '');
    const path = neighbor.param ? neighbor.path.replace(`:${neighbor.param}`, snId) : neighbor.path;
    const resolved = router.resolve(path);
    await assertDetailMatch(resolved, neighbor.path, neighbor.view, neighbor.activeMenu);
    assert.equal(resolved.name, neighbor.name);
    assert.equal(resolved.path, path);
    assert.deepEqual(Object.keys(resolved.params), neighbor.param ? [neighbor.param] : []);
    if (neighbor.param) {
      assert.equal(typeof resolved.params[neighbor.param], 'string');
      assert.equal(resolved.params[neighbor.param], snId);
    }
  });
}
