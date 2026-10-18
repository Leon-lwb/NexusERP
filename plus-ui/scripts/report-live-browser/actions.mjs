import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import { safeLabel, safeSummary } from './browser.mjs';

const dom = `
  const normalized = value => String(value || '').replace(/\\s+/g, '').replace(/[：:*＊]/g, '');
  const visible = element => {
    if (!element || !element.isConnected || !element.getClientRects().length) return false;
    for (let parent = element; parent instanceof Element; parent = parent.parentElement) {
      const style = getComputedStyle(parent);
      if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0 || parent.hidden || parent.inert) return false;
    }
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  };
  const enabled = element => !element.matches(':disabled,[aria-disabled="true"]') && !element.closest('.is-disabled,[inert]');
  const dialogs = Array.from(document.querySelectorAll('.el-dialog,.el-message-box,[role="dialog"]')).filter(visible);
  const scope = dialogs.at(-1) || document.querySelector('.app-main') || document;
`;

/** 仅通过可观察DOM和有界CDP输入操作真实页面，步骤失败继续记账。 */
export function createActions(cdp, ledger) {
  assert.ok(Array.isArray(ledger.requests) && Array.isArray(ledger.checks), '账本必须提供requests与checks数组');
  let serial = 0;

  /** 在Node侧逐次轮询，九十秒导出等待不会占用单次十五秒CDP调用。 */
  async function wait(expression, timeout = 12000) {
    assert.ok(typeof expression === 'string' && expression.length > 0, '等待条件必须是表达式');
    assert.ok(Number.isFinite(timeout) && timeout > 0 && timeout <= 300000, '等待超时必须在五分钟以内');
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
      ledger.signal?.throwIfAborted();
      const result = await cdp.evaluate(expression);
      if (result) return result;
      await delay(Math.min(75, Math.max(1, deadline - Date.now())), undefined, { signal: ledger.signal });
    }
    throw Error('等待页面条件超时');
  }

  /** 临时标识只保留定位信息，不将DOM文本或业务对象写入账本。 */
  function mark(expression) {
    const attribute = 'data-report-live-target-' + ++serial;
    return `(() => {
      ${dom}
      const element = (${expression});
      if (!element) return null;
      element.setAttribute(${JSON.stringify(attribute)}, '');
      return '[${attribute}]';
    })()`;
  }

  /** 每次键盘按键均发送按下和释放，不调用组件处理器。 */
  async function key(name, code, virtualKey, modifiers = 0) {
    const params = { key: name, code, windowsVirtualKeyCode: virtualKey, nativeVirtualKeyCode: virtualKey, modifiers };
    await cdp.command('Input.dispatchKeyEvent', { type: 'keyDown', ...params });
    await cdp.command('Input.dispatchKeyEvent', { type: 'keyUp', ...params });
  }

  /** 中心点点击前再次命中测试，悬浮变化或遮罩不能造成假点击。 */
  async function click(selector) {
    assert.ok(typeof selector === 'string' && selector, '点击目标必须是DOM选择器');
    const target = await wait(
      mark(`Array.from(document.querySelectorAll(${JSON.stringify(selector)})).find(element => visible(element) && enabled(element))`)
    );
    await cdp.evaluate(
      `(() => { document.querySelector(${JSON.stringify(target)}).scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' }); return true; })()`
    );
    await cdp.evaluate(
      `Promise.all(document.getAnimations().filter(animation => animation.playState === 'running' && animation.effect?.getComputedTiming().iterations !== Infinity).map(animation => animation.finished.catch(() => {})))`
    );
    let point = await wait(`(() => {
      ${dom}
      const element = document.querySelector(${JSON.stringify(target)});
      if (!visible(element) || !enabled(element)) return null;
      const rect = element.getBoundingClientRect();
      const centerX = rect.left + rect.width / 2, centerY = rect.top + rect.height / 2;
      if (centerX < 0 || centerY < 0 || centerX >= innerWidth || centerY >= innerHeight) return null;
      const hit = document.elementFromPoint(centerX, centerY);
      return hit && (hit === element || element.contains(hit)) ? { x: centerX, y: centerY } : null;
    })()`);
    await cdp.command('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point });
    point = await wait(`(() => {
      ${dom}
      const element = document.querySelector(${JSON.stringify(target)});
      if (!visible(element) || !enabled(element)) return false;
      const rect = element.getBoundingClientRect();
      const target = document.elementFromPoint(${point.x}, ${point.y});
      if (Math.abs(rect.left + rect.width / 2 - ${point.x}) > 1 || Math.abs(rect.top + rect.height / 2 - ${point.y}) > 1) {
        const x = rect.left + rect.width / 2, y = rect.top + rect.height / 2;
        const center = document.elementFromPoint(x, y);
        return center && (center === element || element.contains(center)) ? { x, y } : null;
      }
      return target && (target === element || element.contains(target)) ? { x: ${point.x}, y: ${point.y} } : null;
    })()`);
    await cdp.command('Input.dispatchMouseEvent', { type: 'mousePressed', ...point, button: 'left', clickCount: 1 });
    await cdp.command('Input.dispatchMouseEvent', { type: 'mouseReleased', ...point, button: 'left', clickCount: 1 });
    return true;
  }

  /** 按文字等待可见且启用的按钮，优先操作当前可见对话框。 */
  async function press(text, selector = 'button') {
    assert.ok(typeof text === 'string' && text.trim(), '按钮文字不能为空');
    const target = await wait(
      mark(`(() => {
      const matches = Array.from(document.querySelectorAll(${JSON.stringify(selector)})).filter(element =>
        visible(element) && enabled(element) && [element.textContent, element.getAttribute('aria-label'), element.getAttribute('title')].some(value => normalized(value) === normalized(${JSON.stringify(text)}))
      );
      const local = matches.filter(element => scope.contains(element));
      return (local.length ? local : matches)[0];
    })()`)
    );
    return click(target);
  }

  /** 字段内容只经CDP键盘输入，凭据不会出现在结果或日志中。 */
  async function fill(selector, text) {
    assert.ok(typeof text === 'string', '字段输入必须是字符串');
    const target = await wait(
      mark(
        `Array.from(document.querySelectorAll(${JSON.stringify(selector)})).find(element => visible(element) && enabled(element) && !element.readOnly && (element.matches('input,textarea') || element.isContentEditable))`
      )
    );
    await click(target);
    await key('a', 'KeyA', 65, process.platform === 'darwin' ? 4 : 2);
    if (text) await cdp.command('Input.insertText', { text });
    else await key('Backspace', 'Backspace', 8);
    const matched = await cdp.evaluate(`(() => {
      const element = document.querySelector(${JSON.stringify(target)});
      return !!element && (element.isContentEditable ? element.textContent : element.value) === ${JSON.stringify(text)};
    })()`);
    assert.ok(matched, '键盘输入后的字段内容不匹配');
    await key('Tab', 'Tab', 9);
    return true;
  }

  /** 按可见表单标签和控件顺序定位，kind兼容原生类型及场景CSS选择器。 */
  async function field(label, kind = 'input', index = 0) {
    assert.ok(Number.isInteger(index) && index >= 0, '字段索引必须是非负整数');
    const selector =
      kind === 'select' ? '.el-select,select' : kind === 'input' ? 'input:not([type="hidden"]):not([type="checkbox"]):not([type="radio"])' : kind;
    return wait(
      mark(`(() => {
      const labels = Array.from(scope.querySelectorAll('.el-form-item__label,label')).filter(element => visible(element) && normalized(element.textContent) === normalized(${JSON.stringify(label)}));
      const candidates = [];
      for (const element of labels) {
        const control = element.getAttribute('for') ? document.getElementById(element.getAttribute('for')) : null;
        if (control && control.matches(${JSON.stringify(selector)}) && visible(control)) candidates.push(control);
        const container = element.closest('.el-form-item') || element.parentElement;
        for (const candidate of container.querySelectorAll(${JSON.stringify(selector)})) if (visible(candidate)) candidates.push(candidate);
      }
      return Array.from(new Set(candidates))[${index}];
    })()`)
    );
  }

  async function closeSelect() {
    const target = await cdp.evaluate(
      mark(`Array.from(scope.querySelectorAll('.el-select')).filter(visible)
        .map(element => element.querySelector('input[role="combobox"][aria-expanded="true"]'))
        .find(element => element && enabled(element))`)
    );
    if (!target) return;
    for (let attempt = 0; attempt < 2; attempt++) {
      const expanded = await cdp.evaluate(`(() => {
        const element = document.querySelector(${JSON.stringify(target)});
        if (!element || element.getAttribute('aria-expanded') !== 'true') return false;
        element.focus({ preventScroll: true });
        if (document.activeElement !== element) throw Error('无法聚焦展开的下拉控件');
        return true;
      })()`);
      if (!expanded) return;
      await key('Escape', 'Escape', 27);
    }
    await wait(`document.querySelector(${JSON.stringify(target)})?.getAttribute('aria-expanded') !== 'true'`);
  }

  /** Element下拉操作前后均释放旧浮层，已选中的多选项不会被反向取消。 */
  async function choose(selector, labelPrefix) {
    assert.ok(typeof labelPrefix === 'string', '选项文字前缀必须是字符串');
    await closeSelect();
    const control = await wait(
      mark(`Array.from(document.querySelectorAll(${JSON.stringify(selector)})).find(element => visible(element) && enabled(element))`)
    );
    await click(control);
    const option = await wait(`(() => {
      ${dom}
      const control = document.querySelector(${JSON.stringify(control)});
      const input = control.matches('[role="combobox"]') ? control : control.querySelector('[role="combobox"],input[aria-controls]');
      const popupId = input?.getAttribute('aria-controls') || input?.getAttribute('aria-owns') || control.getAttribute('aria-controls');
      const popup = popupId ? document.getElementById(popupId) : Array.from(document.querySelectorAll('.el-select-dropdown,[role="listbox"]')).filter(visible).at(-1);
      if (!visible(popup)) return null;
      const matches = Array.from(popup.querySelectorAll('.el-select-dropdown__item,[role="option"]')).filter(element => visible(element) && enabled(element) && normalized(element.textContent).startsWith(normalized(${JSON.stringify(labelPrefix)})));
      const exact = matches.filter(element => normalized(element.textContent) === normalized(${JSON.stringify(labelPrefix)}));
      const choices = exact.length ? exact : matches;
      if (!choices.length) return null;
      if (choices.length !== 1 && ${JSON.stringify(labelPrefix)}.trim()) throw Error('选项前缀匹配不唯一');
      const element = choices[0];
      element.setAttribute('data-report-live-option-${++serial}', '');
      return { selector: '[data-report-live-option-${serial}]', label: element.textContent.trim(), selected: element.classList.contains('is-selected') || element.classList.contains('selected') || element.getAttribute('aria-selected') === 'true' };
    })()`);
    if (!option.selected) await click(option.selector);
    await wait(`(() => {
      ${dom}
      const element = document.querySelector(${JSON.stringify(option.selector)});
      const control = document.querySelector(${JSON.stringify(control)});
      return !!control && ((!!element && (element.classList.contains('is-selected') || element.classList.contains('selected') || element.getAttribute('aria-selected') === 'true')) || normalized(control.textContent).includes(normalized(${JSON.stringify(option.label)})) || Array.from(control.querySelectorAll('input')).some(input => normalized(input.value) === normalized(${JSON.stringify(option.label)})));
    })()`);
    await closeSelect();
    return true;
  }

  /** 仅等待可见加载遮罩、有限动画和短暂网络静默，忽略登录背景等无限动画。 */
  async function settled(timeout = 12000) {
    assert.ok(Number.isFinite(timeout) && timeout > 0 && timeout <= 300000, '页面稳定等待必须有界');
    const deadline = Date.now() + timeout;
    let quietSince = 0;
    while (Date.now() < deadline) {
      ledger.signal?.throwIfAborted();
      const idle = await cdp.evaluate(`(() => {
        ${dom}
        if (document.readyState !== 'complete') return false;
        const loading = Array.from(document.querySelectorAll('.el-loading-mask,.el-loading-spinner,.vxe-loading,.vxe-loading--wrapper,.vxe-table--loading,.is-loading,[aria-busy="true"]')).some(visible);
        const moving = document.getAnimations().some(animation => animation.playState === 'running' && animation.effect && animation.effect.getComputedTiming().iterations !== Infinity && visible(animation.effect.target));
        return !loading && !moving;
      })()`);
      if (idle && !(ledger.pendingRequests?.size > 0)) {
        if (!quietSince) quietSince = Date.now();
        if (Date.now() - quietSince >= 200) return true;
      } else quietSince = 0;
      await delay(70, undefined, { signal: ledger.signal });
    }
    throw Error('等待当前可见页面加载和动画结束超时');
  }

  /** 通过真实侧栏逐层展开并点击链接，以路由地址和可见标题确认页面就绪。 */
  async function menu(text, expectedTitle = text) {
    await key('Escape', 'Escape', 27);
    let destination;
    for (let attempt = 0; attempt < 8; attempt++) {
      const choice = await wait(`(() => {
        ${dom}
        const roots = Array.from(document.querySelectorAll('.sidebar-container .menu-title,.el-menu--popup .menu-title'));
        const labels = roots.filter(element => normalized(element.textContent) === normalized(${JSON.stringify(text)}));
        const label = labels.find(visible) || labels[0];
        if (!label) {
          const parent = roots.find(element => visible(element) && normalized(element.textContent) === normalized('报表中心'))?.closest('.el-sub-menu');
          const title = parent && !parent.classList.contains('is-opened') ? parent.querySelector(':scope > .el-sub-menu__title') : null;
          if (!title) return null;
          for (const previous of document.querySelectorAll('[data-report-live-menu]')) previous.removeAttribute('data-report-live-menu');
          title.setAttribute('data-report-live-menu', 'expand');
          return { selector: '[data-report-live-menu="expand"]' };
        }
        const closed = [];
        for (let parent = label.parentElement; parent; parent = parent.parentElement)
          if (parent.matches('.el-sub-menu') && !parent.classList.contains('is-opened')) closed.unshift(parent);
        const title = closed.map(parent => parent.querySelector(':scope > .el-sub-menu__title')).find(visible);
        if (title) {
          for (const previous of document.querySelectorAll('[data-report-live-menu]')) previous.removeAttribute('data-report-live-menu');
          title.setAttribute('data-report-live-menu', 'expand');
          return { selector: '[data-report-live-menu="expand"]' };
        }
        const link = label.closest('a[href]');
        if (!link || !visible(link)) throw Error('菜单尚未形成真实路由链接');
        const url = new URL(link.href, location.href);
        if (url.origin !== location.origin || !url.pathname.startsWith('/report/')) throw Error('菜单必须指向本机真实报表路由');
        for (const previous of document.querySelectorAll('[data-report-live-menu]')) previous.removeAttribute('data-report-live-menu');
        link.setAttribute('data-report-live-menu', 'destination');
        return { selector: '[data-report-live-menu="destination"]', path: url.pathname, search: url.search, hash: url.hash };
      })()`);
      await click(choice.selector);
      if (choice.path) {
        destination = choice;
        break;
      }
      await settled();
    }
    assert.ok(destination, '菜单未进入真实路由页面');
    await wait(`(() => {
      ${dom}
      return location.pathname === ${JSON.stringify(destination.path)} && location.search === ${JSON.stringify(destination.search)} && location.hash === ${JSON.stringify(destination.hash)} && Array.from(document.querySelectorAll('.app-main .report-page h1')).some(element => visible(element) && normalized(element.textContent) === normalized(${JSON.stringify(expectedTitle)}));
    })()`);
    await settled();
    return true;
  }

  /** 仅显式返回blocked才记为阻塞，任何抛错均记FAIL且不重抛。 */
  async function step(name, action) {
    ledger.signal?.throwIfAborted();
    const started = Date.now();
    const requestStart = ledger.requests.length;
    const record = { name: safeLabel(name), status: 'FAIL', durationMs: 0 };
    let timer;
    let onAbort;
    try {
      assert.equal(typeof action, 'function', '步骤必须提供实际动作');
      const result = await Promise.race([
        Promise.resolve().then(action),
        new Promise((done, reject) => {
          timer = setTimeout(() => {
            const error = Error('步骤执行超过180秒');
            ledger.abort?.(error);
            reject(error);
          }, 180000);
          onAbort = () => reject(ledger.signal.reason || Error('运行已中断'));
          ledger.signal?.addEventListener('abort', onAbort, { once: true });
        })
      ]);
      assert.notEqual(result, false, '步骤显式返回失败');
      if (result && Object.hasOwn(result, 'blocked')) {
        assert.ok(typeof result.blocked === 'string' && result.blocked.trim(), '阻塞必须明确提供原因');
        record.status = 'blocked';
        record.reason = safeSummary(result.blocked, '前置条件未满足（详情已脱敏）');
        for (const request of ledger.requests.slice(requestStart)) {
          if ((request.status === 409 || request.code === 409) && request.errorCode === 'QUERY_SOURCE_DISABLED')
            ledger.expectedBlockedRequests?.add(request);
        }
      } else record.status = 'PASS';
    } catch (error) {
      record.reason = safeSummary(error);
      const positions = Array.from(String(error.stack || '').matchAll(/(?:scenarios|actions)\.mjs:\d+:\d+/g), (position) => position[0]);
      if (positions.length) process.stdout.write('失败定位 ' + [...new Set(positions)].join(' -> ') + ' ' + (error.name || 'Error') + '\n');
    } finally {
      clearTimeout(timer);
      if (onAbort) ledger.signal?.removeEventListener('abort', onAbort);
      record.durationMs = Date.now() - started;
      ledger.checks.push(record);
      process.stdout.write(record.status + ' ' + record.name + '\n');
      if (record.status === 'FAIL' && process.env.REPORT_LIVE_STOP_ON_FAILURE === '1') ledger.abort?.(Error('步骤失败，停止当前调试运行'));
    }
    return record;
  }

  return { wait, click, press, fill, field, choose, menu, settled, step };
}
