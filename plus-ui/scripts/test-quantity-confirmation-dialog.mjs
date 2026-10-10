import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { test, mock } from 'node:test';
import {
  componentPath,
  controllerPath,
  uiRoot,
  ID,
  ROOT_ID,
  OTHER_ID,
  context,
  unit,
  copy,
  deferred,
  fixture,
  loadQuantityModules
} from './test-quantity-confirmation.mjs';
import * as Vue from 'vue';
import { join } from 'node:path';
import * as sfc from '@vue/compiler-sfc';

test('独立数量核验 Dialog 必须存在（功能缺失 RED）', () => {
  assert.ok(existsSync(componentPath), '缺少 CertifiedQuantityConfirmationDialog.vue');
});

test('单位列表 query-only 入口和专属 Dialog 接线，不把旧 CRUD 当核验', () => {
  const source = readFileSync(join(uiRoot, 'src/views/erp/bd/measure/index.vue'), 'utf8');
  assert.ok(source.includes('CertifiedQuantityConfirmationDialog'), '缺少真实页接线');
  assert.match(source, /v-hasPermi="\['bd:measure:query'\]"[^>]*@click(?:\.stop)?="handleQuantityConfirmation/);
  assert.match(source, /:uom-id="quantityUnitId"/);
  assert.match(source, /@confirmed="refreshQuantityUnits"/);
});

test('真实 SFC 编译；核验范围、NULL 原版本、独立资料表单与关闭事件', () => {
  const { descriptor, errors } = sfc.parse(readFileSync(componentPath, 'utf8'), { filename: componentPath });
  assert.deepEqual(errors, []);
  const script = sfc.compileScript(descriptor, { id: 'quantity-dialog' });
  const template = sfc.compileTemplate({
    source: descriptor.template.content,
    filename: componentPath,
    id: 'quantity-dialog',
    compilerOptions: { bindingMetadata: script.bindings }
  });
  assert.deepEqual(template.errors, []);
  assert.match(descriptor.template.content, /尚无版本/);
  assert.match(descriptor.template.content, /历史库存/);
  assert.match(descriptor.template.content, /全物料/);
  assert.match(descriptor.template.content, /state\.form\.conversionRate/);
  assert.ok(!descriptor.template.content.includes('v-model="state.snapshot'));
});

function host(tag, text = '') {
  return { tag, text, props: {}, children: [], parent: null };
}
/** 真实 Vue setup/watch/render/卸载，Element Plus 仅替换为无 DOM 宿主，非浏览器 E2E。 */
function renderer(teleportTarget) {
  return Vue.createRenderer({
    querySelector: (selector) => {
      assert.equal(selector, '#quantity-overlay', '真实 Teleport 只能进入本测试独立宿主');
      return teleportTarget;
    },
    createElement: (tag) => host(tag),
    createText: (text) => host('#text', text),
    createComment: (text) => host('#comment', text),
    setText: (n, text) => {
      n.text = text;
    },
    setElementText: (n, text) => {
      n.text = text;
      n.children = [];
    },
    patchProp: (n, key, _old, value) => {
      n.props[key] = value;
    },
    parentNode: (n) => n.parent,
    nextSibling: (n) => n.parent?.children[n.parent.children.indexOf(n) + 1] ?? null,
    insert: (n, p, anchor = null) => {
      if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1);
      const index = anchor ? p.children.indexOf(anchor) : -1;
      p.children.splice(index < 0 ? p.children.length : index, 0, n);
      n.parent = p;
    },
    remove: (n) => {
      if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1);
      n.parent = null;
    }
  });
}
const nodes = (n) => [n, ...n.children.flatMap(nodes)];
const text = (n) => n.text + (typeof n.props.title === 'string' ? n.props.title : '') + n.children.map(text).join('');
const button = (n, label) => nodes(n).find((x) => x.tag === 'el-button' && text(x).includes(label));
const flush = async () => {
  for (let n = 0; n < 12; n++) {
    await new Promise((r) => setTimeout(r, 0));
    await Vue.nextTick();
  }
};
let compiled;
async function mountedFixture(patch = {}) {
  if (!compiled) {
    const { descriptor } = sfc.parse(readFileSync(componentPath, 'utf8'), { filename: componentPath });
    const script = sfc.compileScript(descriptor, { id: 'quantity-mounted', genDefaultAs: '__QuantityComponent' });
    const template = sfc.compileTemplate({
      source: descriptor.template.content,
      filename: componentPath,
      id: 'quantity-mounted',
      compilerOptions: { bindingMetadata: script.bindings }
    });
    assert.deepEqual(template.errors, []);
    const normalized = (p) => p.replaceAll('\\', '/');
    compiled = await loadQuantityModules(
      `${script.content}\n${template.code}\n__QuantityComponent.render = render;
      export { __QuantityComponent as component };
      export * as controller from '${normalized(controllerPath)}';
      export * as api from '${normalized(join(uiRoot, 'src/api/erp/bd/measure/index.ts'))}';
      export * as helper from '${normalized(join(uiRoot, 'src/utils/certified-business.ts'))}';`.replaceAll('\\n', '\n')
    );
  }
  const f = fixture(compiled, patch);
  globalThis.__quantityCredential = Vue.ref(f.credential);
  globalThis.__quantityRoute = Vue.reactive({ fullPath: '/measure' });
  const listeners = new Map();
  const listenerOps = [];
  globalThis.window = {
    localStorage: f.storage,
    addEventListener: (name, handler) => {
      listenerOps.push({ operation: 'add', name, handler });
      listeners.set(name, handler);
    },
    removeEventListener: (name, handler) => {
      listenerOps.push({ operation: 'remove', name, handler });
      if (listeners.get(name) === handler) listeners.delete(name);
    }
  };
  const props = Vue.reactive({ modelValue: true, uomId: ID });
  const confirmed = [];
  const visibilityUpdates = [];
  const rootContainer = host('root');
  const overlay = host('quantity-overlay');
  const container = patch.teleport ? overlay : rootContainer;
  const measureActive = Vue.ref(true);
  const child = () =>
    Vue.h(compiled.component, {
      ...props,
      'onUpdate:modelValue': (v) => {
        visibilityUpdates.push(v);
        if (!patch.holdVisibilityUpdates) props.modelValue = v;
      },
      onConfirmed: (id) => confirmed.push(id)
    });
  const measurePage = Vue.defineComponent({ name: 'QuantityMeasurePageFixture', setup: () => child });
  const elsewherePage = Vue.defineComponent({ name: 'QuantityElsewherePageFixture', setup: () => () => Vue.h('elsewhere', '其它页面') });
  const app = renderer(overlay).createApp({
    render: () =>
      patch.keepAlive ? Vue.h(Vue.KeepAlive, null, { default: () => (measureActive.value ? Vue.h(measurePage) : Vue.h(elsewherePage)) }) : child()
  });
  for (const name of [
    'el-dialog',
    'el-alert',
    'el-descriptions',
    'el-descriptions-item',
    'el-form',
    'el-form-item',
    'el-input',
    'el-select',
    'el-option',
    'el-button'
  ]) {
    app.component(
      name,
      Vue.defineComponent({
        inheritAttrs: false,
        setup: (_, ctx) => () => {
          if (name === 'el-dialog' && !(ctx.attrs.modelValue ?? ctx.attrs['model-value'])) return null;
          const content = Vue.h(name, { ...ctx.attrs }, [...(ctx.slots.default?.() ?? []), ...(ctx.slots.footer?.() ?? [])]);
          return name === 'el-dialog' && patch.teleport ? Vue.h(Vue.Teleport, { to: '#quantity-overlay' }, [content]) : content;
        }
      })
    );
  }
  app.directive('loading', {});
  const clock = mock.method(Date, 'now', () => Date.parse('2026-10-09T12:00:00+08:00'));
  app.mount(rootContainer);
  await flush();
  return {
    f,
    helper: compiled.helper,
    props,
    confirmed,
    visibilityUpdates,
    container,
    rootContainer,
    overlay,
    measureActive,
    app,
    listeners,
    listenerOps,
    dispatch: (name) => listeners.get(name)?.({ type: name }),
    navigate: async (fullPath, cached = patch.keepAlive) => {
      globalThis.__quantityRoute.fullPath = fullPath;
      if (cached) measureActive.value = fullPath.split(/[?#]/, 1)[0] === '/measure';
      await flush();
    },
    fill: async () => {
      const input = nodes(container).filter((n) => n.tag === 'el-input');
      input[0].props['onUpdate:modelValue']('12.500000');
      input[1].props['onUpdate:modelValue']('合成规格书第3页');
      nodes(container)
        .find((n) => n.tag === 'el-select')
        .props['onUpdate:modelValue']('2');
      await Vue.nextTick();
    },
    cleanup: () => {
      if (app._instance) app.unmount();
      clock.mock.restore();
      delete globalThis.window;
      delete globalThis.__quantityCredential;
      delete globalThis.__quantityRoute;
    }
  };
}
const mountTest = (name, fn) =>
  test(name, async () => {
    const m = await mountedFixture();
    try {
      await fn(m);
    } finally {
      m.cleanup();
    }
  });
mountTest('真实挂载：NULL 版本显示尚无版本；比例/精度/证据空白，按钮不能提交', async (m) => {
  assert.ok(text(m.container).includes('尚无版本（NULL）'));
  assert.ok(text(m.container).includes('不补认历史库存'));
  assert.equal(nodes(m.container).find((n) => n.tag === 'el-input').props.modelValue, '');
  assert.equal(button(m.container, '明确确认').props.disabled, true);
  await button(m.container, '明确确认').props.onClick();
  assert.equal(m.f.posts.length, 0);
});
mountTest('真实挂载：query-only 打开可看，填写也无法变成写权限', async (m) => {
  m.f.ctx.confirmAllowed = false;
  await button(m.container, '只读重新读取').props.onClick();
  await flush();
  assert.ok(text(m.container).includes('仅查询'));
  await m.fill();
  await button(m.container, '明确确认').props.onClick();
  assert.equal(m.f.posts.length, 0);
  assert.equal(nodes(m.container).find((n) => n.tag === 'el-form').props.disabled, true);
});
mountTest('真实挂载：人工填写+双击只派发一次，同主体回读后才发 confirmed', async (m) => {
  await m.fill();
  assert.equal(button(m.container, '明确确认').props.disabled, false);
  const first = button(m.container, '明确确认').props.onClick();
  const second = button(m.container, '明确确认').props.onClick();
  await Promise.all([first, second]);
  await flush();
  assert.equal(m.f.posts.length, 1);
  assert.deepEqual(m.confirmed, [ID]);
  assert.equal(m.f.posts[0].body.conversionRate, '12.500000');
  assert.equal(m.f.posts[0].body.expectedVersion, null);
});
mountTest('真实挂载：UNKNOWN 网络后关闭重开及只读回查不解锁', async (m) => {
  await m.fill();
  m.f.outcome = 'network';
  await button(m.container, '明确确认').props.onClick();
  await flush();
  assert.ok(text(m.container).includes('结果未知'));
  assert.deepEqual(m.confirmed, []);
  m.props.modelValue = false;
  await flush();
  m.props.modelValue = true;
  await flush();
  await m.fill();
  await button(m.container, '只读重新读取').props.onClick();
  await flush();
  assert.equal(button(m.container, '明确确认').props.disabled, true);
  await button(m.container, '明确确认').props.onClick();
  assert.equal(m.f.posts.length, 1);
});
for (const change of ['unit', 'route', 'account', 'unmount']) {
  mountTest(`真实挂载：迟到写响应 ${change} 切换不会通知新对象或清掉旧锁`, async (m) => {
    await m.fill();
    m.f.postGate = deferred();
    const writing = button(m.container, '明确确认').props.onClick();
    await flush();
    assert.equal(m.f.posts.length, 1);
    if (change === 'unit') m.props.uomId = OTHER_ID;
    if (change === 'route') globalThis.__quantityRoute.fullPath = '/elsewhere';
    if (change === 'account') globalThis.__quantityCredential.value = 'quantity-offline-b';
    if (change === 'unmount') m.app.unmount();
    await flush();
    m.f.postGate.resolve();
    await writing;
    await flush();
    assert.deepEqual(m.confirmed, []);
    if (change !== 'unmount') {
      m.props.modelValue = false;
      m.props.uomId = ID;
      globalThis.__quantityRoute.fullPath = '/measure';
      globalThis.__quantityCredential.value = 'quantity-offline-a';
      await flush();
      m.props.modelValue = true;
      await flush();
      assert.equal(button(m.container, '明确确认').props.disabled, true);
    }
  });
}

mountTest('真实挂载：跨组件持久意图及 storage 事件显示 UNKNOWN 提示，不沿用请填写资料提示', async (m) => {
  await m.fill();
  assert.equal(button(m.container, '明确确认').props.disabled, false);
  m.helper
    .createBusinessIntentStore(m.f.storage)
    .begin(
      { tenantId: m.f.ctx.tenantId, userId: m.f.ctx.userId, feature: 'quantity-confirmation', documentId: ID },
      'confirm-quantity',
      '00000000-0000-4000-8000-000000000002'
    );
  m.listeners.get('storage')();
  await Vue.nextTick();
  assert.equal(button(m.container, '明确确认').props.disabled, true);
  assert.ok(text(m.container).includes('此前确认仍有未确定结果'), '外部锁必须显示未知结果提示而非原表单提示');
  await button(m.container, '明确确认').props.onClick();
  assert.equal(m.f.posts.length, 0);
});

/** 缓存不是卸载：通过真实 KeepAlive/Teleport 驱动生命周期，不直接调用产品钩子。 */
const cacheTest = (name, fn, patch = {}) =>
  test(name, async () => {
    const m = await mountedFixture({ keepAlive: true, teleport: true, ...patch });
    try {
      await fn(m);
    } finally {
      m.cleanup();
    }
  });
cacheTest('P2：真实 KeepAlive 停用后切路由，不得在残留 Teleport 对话框获得新 session 并 POST', async (m) => {
  await m.fill();
  await m.navigate('/elsewhere');
  const residual = button(m.overlay, '明确确认');
  if (residual) {
    await m.fill();
    await residual.props.onClick();
  }
  assert.equal(m.f.posts.length, 0, '停用 /measure 的 Teleport 不得以 /elsewhere 新 session 写数量');
  assert.equal(m.props.modelValue, false);
  assert.ok(m.visibilityUpdates.includes(false));
  assert.equal(button(m.overlay, '明确确认'), undefined);
});
cacheTest('P2：仅 onDeactivated（路由尚未更新）也关闭 Teleport、emit false 并撤销 storage/focus listener', async (m) => {
  m.measureActive.value = false;
  await flush();
  assert.equal(m.props.modelValue, false);
  assert.ok(m.visibilityUpdates.includes(false));
  assert.equal(button(m.overlay, '明确确认'), undefined);
  assert.equal(m.listeners.has('storage'), false);
  assert.equal(m.listeners.has('focus'), false);
});
cacheTest('P2：停用期间 props/token/uom/路由变化不再 GET/open；activated 也不自动重新打开', async (m) => {
  await m.navigate('/elsewhere');
  const before = m.f.calls.length;
  m.props.modelValue = true;
  m.props.uomId = OTHER_ID;
  globalThis.__quantityCredential.value = 'quantity-offline-b';
  await flush();
  assert.equal(m.f.calls.length, before, '缓存停用时 watch 不得获取新上下文/会话');
  assert.equal(m.props.modelValue, false);
  await m.navigate('/measure');
  assert.equal(button(m.overlay, '明确确认'), undefined);
  assert.equal(m.f.calls.length, before, '激活需要用户重新打开，不自动 GET');
});
cacheTest('P2：UNKNOWN 持久意图跨停用/激活/路由保持，不清锁或重发', async (m) => {
  await m.fill();
  m.f.outcome = 'network';
  await button(m.overlay, '明确确认').props.onClick();
  const saved = [...m.f.storage.values.entries()];
  assert.equal(m.f.posts.length, 1);
  await m.navigate('/elsewhere');
  assert.deepEqual([...m.f.storage.values.entries()], saved);
  await m.navigate('/measure');
  assert.deepEqual([...m.f.storage.values.entries()], saved);
  m.props.modelValue = true;
  await flush();
  await m.fill();
  assert.equal(button(m.overlay, '明确确认').props.disabled, true);
  await button(m.overlay, '明确确认').props.onClick();
  assert.equal(m.f.posts.length, 1);
  assert.deepEqual([...m.f.storage.values.entries()], saved);
});
cacheTest('P2：storage listener 每次 activated 一次、deactivated 移除；普通 mount/unmount 同样正常', async (m) => {
  assert.equal(m.listenerOps.filter((x) => x.operation === 'add' && x.name === 'storage').length, 1);
  assert.equal(m.listeners.has('storage'), true);
  await m.navigate('/elsewhere');
  assert.equal(m.listeners.has('storage'), false);
  await m.navigate('/measure');
  assert.equal(m.listeners.has('storage'), true);
  assert.equal(m.listenerOps.filter((x) => x.operation === 'add' && x.name === 'storage').length, 2);
  assert.equal(m.listenerOps.filter((x) => x.operation === 'remove' && x.name === 'storage').length, 1);
  m.app.unmount();
  assert.equal(m.listeners.has('storage'), false);
  assert.equal(m.listeners.has('focus'), false);
});
mountTest('P2：未缓存普通页面离开所属路由也 emit false，回路由不自动打开', async (m) => {
  const prior = m.f.calls.length;
  globalThis.__quantityRoute.fullPath = '/elsewhere';
  await flush();
  assert.equal(m.props.modelValue, false);
  assert.equal(button(m.container, '明确确认'), undefined);
  assert.equal(m.f.calls.length, prior);
  globalThis.__quantityRoute.fullPath = '/measure';
  await flush();
  assert.equal(m.f.calls.length, prior);
  assert.equal(button(m.container, '明确确认'), undefined);
  assert.equal(m.listeners.has('storage'), true);
  m.app.unmount();
  assert.equal(m.listeners.has('storage'), false);
});
cacheTest('P2：同页 fullPath/query 改变只关闭原 scope，不能隐式换路由重新核验', async (m) => {
  await m.fill();
  const prior = m.f.calls.length;
  await m.navigate('/measure?source=other');
  assert.equal(m.props.modelValue, false);
  assert.equal(m.f.calls.length, prior);
  assert.equal(button(m.overlay, '明确确认'), undefined);
  m.props.modelValue = true;
  await flush();
  assert.ok(button(m.overlay, '明确确认'), '同一所属页的新 query 仅在人工重新打开后可读');
  assert.equal(m.f.posts.length, 0);
});
for (const boundary of ['token', 'uom', 'route', 'deactivated'])
  cacheTest(`P2：delayed preflight ${boundary} 变化不 POST、不 emit 旧确认`, async (m) => {
    await m.fill();
    m.f.contextGate = deferred();
    const gate = m.f.contextGate;
    const writing = button(m.overlay, '明确确认').props.onClick();
    await Vue.nextTick();
    if (boundary === 'token') globalThis.__quantityCredential.value = 'quantity-offline-b';
    if (boundary === 'uom') m.props.uomId = OTHER_ID;
    if (boundary === 'route') await m.navigate('/elsewhere');
    if (boundary === 'deactivated') {
      m.measureActive.value = false;
      await Vue.nextTick();
    }
    m.f.contextGate = null;
    gate.resolve();
    await writing;
    await flush();
    assert.equal(m.f.posts.length, 0);
    assert.deepEqual(m.confirmed, []);
    if (boundary === 'route' || boundary === 'deactivated') assert.equal(button(m.overlay, '明确确认'), undefined);
  });
cacheTest('P2：focus 在 delayed preflight 期间不重新 open/清人工表单，原 session 可只执行一次', async (m) => {
  await m.fill();
  assert.equal(m.listeners.has('focus'), true, '激活期间明确管理 focus 边界');
  m.f.contextGate = deferred();
  const gate = m.f.contextGate;
  const writing = button(m.overlay, '明确确认').props.onClick();
  await Vue.nextTick();
  const before = m.f.calls.length;
  m.dispatch('focus');
  await Vue.nextTick();
  assert.equal(m.f.calls.length, before, 'focus 不自动刷新/创建新 session');
  assert.equal(nodes(m.overlay).find((n) => n.tag === 'el-input').props.modelValue, '12.500000');
  m.f.contextGate = null;
  gate.resolve();
  await writing;
  assert.equal(m.f.posts.length, 1);
  assert.deepEqual(m.confirmed, [ID]);
});
cacheTest('P2：已排队 focus 回调在停用后执行也不得 fresh open 或解除原边界', async (m) => {
  const delayedFocus = m.listeners.get('focus');
  assert.equal(typeof delayedFocus, 'function');
  await m.fill();
  m.f.contextGate = deferred();
  const gate = m.f.contextGate;
  const writing = button(m.overlay, '明确确认').props.onClick();
  await m.navigate('/elsewhere');
  const before = m.f.calls.length;
  delayedFocus();
  await Vue.nextTick();
  assert.equal(m.f.calls.length, before);
  m.f.contextGate = null;
  gate.resolve();
  await writing;
  assert.equal(m.f.posts.length, 0);
  assert.equal(button(m.overlay, '明确确认'), undefined);
});
cacheTest('P2：POST 在途后 deactivated，迟到成功不通知、不释放持久 UNKNOWN', async (m) => {
  await m.fill();
  m.f.postGate = deferred();
  const writing = button(m.overlay, '明确确认').props.onClick();
  await flush();
  assert.equal(m.f.posts.length, 1);
  const pending = [...m.f.storage.values.entries()];
  assert.ok(pending.length > 0);
  m.measureActive.value = false;
  await flush();
  m.f.postGate.resolve();
  await writing;
  assert.deepEqual(m.confirmed, []);
  assert.deepEqual([...m.f.storage.values.entries()], pending);
  assert.equal(button(m.overlay, '明确确认'), undefined);
});

cacheTest(
  'P2：父级暂不处理 emit false 也立即隐藏 Teleport；激活和外部 token/uom 不自动重开',
  async (m) => {
    await m.fill();
    await m.navigate('/elsewhere');
    assert.equal(m.props.modelValue, true, '模拟父级迟到/忽略关闭通知');
    assert.ok(m.visibilityUpdates.includes(false));
    assert.equal(button(m.overlay, '明确确认'), undefined);
    const before = m.f.calls.length;
    await m.navigate('/measure');
    globalThis.__quantityCredential.value = 'quantity-offline-b';
    m.props.uomId = OTHER_ID;
    await flush();
    assert.equal(m.f.calls.length, before);
    assert.equal(button(m.overlay, '明确确认'), undefined);
    assert.equal(m.f.posts.length, 0);
    m.props.modelValue = false;
    await flush();
    m.props.modelValue = true;
    await flush();
    assert.ok(button(m.overlay, '明确确认'), '所属页激活后只有明确人工重开才能重新读取');
    assert.equal(m.f.posts.length, 0);
  },
  { holdVisibilityUpdates: true }
);
