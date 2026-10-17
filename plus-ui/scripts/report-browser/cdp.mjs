import assert from 'node:assert/strict';

/** 使用Node原生WebSocket连接指定任务页面，所有命令均有超时和断线处理。 */
export async function connectCdp(url, failures, signal) {
  const socket = new WebSocket(url);
  const pending = new Map();
  const listeners = new Map();
  let serial = 0;
  let closing = false;
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      socket.close();
      reject(Error('CDP连接超时'));
    }, 15000);
    socket.addEventListener(
      'open',
      () => {
        clearTimeout(timer);
        resolve();
      },
      { once: true }
    );
    socket.addEventListener(
      'error',
      () => {
        clearTimeout(timer);
        reject(Error('CDP连接失败'));
      },
      { once: true }
    );
  });

  /** 断线和主动关闭均释放所有等待者，避免测试或清理无限等待。 */
  function rejectPending() {
    for (const item of pending.values()) {
      clearTimeout(item.timer);
      item.reject(Error('CDP连接已关闭'));
    }
    pending.clear();
  }
  socket.addEventListener('close', (event) => {
    rejectPending();
    if (!closing) failures.push('CDP连接意外关闭：' + event.code + ' ' + event.reason);
  });
  socket.addEventListener('error', (event) => {
    if (!closing) failures.push('CDP WebSocket异常：' + (event.message || '连接错误'));
  });
  socket.addEventListener('message', (event) => {
    try {
      const message = JSON.parse(event.data);
      if (message.id) {
        const item = pending.get(message.id);
        if (!item) return;
        pending.delete(message.id);
        clearTimeout(item.timer);
        if (message.error) item.reject(Error(item.method + ': ' + JSON.stringify(message.error)));
        else item.resolve(message.result);
      } else {
        for (const listener of listeners.get(message.method) || [])
          Promise.resolve(listener(message.params)).catch((error) => {
            if (!closing) failures.push(error.message);
          });
      }
    } catch (error) {
      failures.push('CDP事件处理失败：' + error.message);
    }
  });

  /** 提交有界协议命令，取消信号不会影响清理阶段的Browser.close。 */
  function command(method, params = {}, timeout = 15000) {
    if (socket.readyState !== WebSocket.OPEN) return Promise.reject(Error('CDP连接不可用：' + method));
    return new Promise((resolve, reject) => {
      const id = ++serial;
      const timer = setTimeout(() => {
        pending.delete(id);
        reject(Error('CDP命令超时：' + method));
      }, timeout);
      pending.set(id, { resolve, reject, timer, method });
      socket.send(JSON.stringify({ id, method, params }));
    });
  }

  /** 注册事件不会覆盖同一协议事件的其他观察者。 */
  function on(method, listener) {
    const callbacks = listeners.get(method) || [];
    callbacks.push(listener);
    listeners.set(method, callbacks);
  }

  /** 页面异常直接失败；先取JSON快照，避免CDP将Vue响应式Proxy读成空对象。 */
  async function evaluate(expression) {
    signal?.throwIfAborted();
    const result = await command('Runtime.evaluate', {
      expression: `(async () => { const value = await (${expression}); return value === undefined ? undefined : JSON.parse(JSON.stringify(value)); })()`,
      returnByValue: true,
      awaitPromise: true
    });
    if (result.exceptionDetails) throw Error('页面脚本异常：' + JSON.stringify(result.exceptionDetails));
    return result.result.value;
  }

  /** 按可观察状态等待真实组件，不依赖长时间固定休眠。 */
  async function ready(expression) {
    return evaluate(`(async () => {
      const start = performance.now();
      while (!(${expression})) {
        if (performance.now() - start > 12000) throw Error('组件等待超时：' + ${JSON.stringify(expression)});
        await new Promise(resolve => setTimeout(resolve, 40));
      }
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      return true;
    })()`);
  }

  /** 允许同一隔离站点的静态模块与Mock，真实后端路径也会被阻断。 */
  function allowed(value, origin) {
    const target = new URL(value);
    return (
      ['data:', 'blob:', 'about:'].includes(target.protocol) ||
      (target.origin === origin &&
        /^\/(?:$|main\.ts|App\.vue|NavigationApp\.vue|api\.mjs|user\.mjs|@|src\/|node_modules\/|__report_mock\/)/.test(target.pathname))
    );
  }

  /** 在页面执行前安装异常、私有Blob和网络防线。 */
  async function guardPage(origin) {
    on('Runtime.exceptionThrown', ({ exceptionDetails }) =>
      failures.push('浏览器异常：' + (exceptionDetails.exception?.description || exceptionDetails.text))
    );
    on('Runtime.consoleAPICalled', (event) => {
      if (event.type === 'error') failures.push('console.error：' + event.args.map((argument) => argument.value || argument.description).join(' '));
    });
    on('Fetch.requestPaused', async ({ requestId, request }) => {
      if (allowed(request.url, origin)) await command('Fetch.continueRequest', { requestId });
      else {
        failures.push('阻断意外网络：' + request.url);
        await command('Fetch.failRequest', { requestId, errorReason: 'BlockedByClient' });
      }
    });
    on('Network.webSocketCreated', ({ url }) => {
      if (new URL(url).origin !== origin.replace(/^http/, 'ws')) failures.push('意外WebSocket连接：' + url);
    });
    await command('Runtime.enable');
    await command('Page.enable');
    await command('Network.enable');
    await command('Network.setCacheDisabled', { cacheDisabled: true });
    await command('Fetch.enable', { patterns: [{ urlPattern: '*', requestStage: 'Request' }] });
    await command('Page.addScriptToEvaluateOnNewDocument', { source: `(${installPageGuards.toString()})()` });
  }

  /** 每个场景验证页面宽度和累计异常，未处理拒绝不会被吞掉。 */
  async function healthy() {
    const state = await evaluate(`({
      failures: window.__reportBrowserFailures,
      width: innerWidth,
      scrollWidth: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)
    })`);
    assert.deepEqual(state.failures, [], '存在页面异常或未处理拒绝');
    assert.deepEqual(failures, [], '存在脚本异常或意外网络请求');
    assert.ok(state.scrollWidth <= state.width, '页面横向溢出：' + JSON.stringify(state));
  }

  return {
    command,
    on,
    evaluate,
    ready,
    guardPage,
    healthy,
    prepareToClose() {
      closing = true;
    },
    get status() {
      return { readyState: socket.readyState, pendingCommands: pending.size };
    },
    close(expected = true) {
      closing = expected;
      rejectPending();
      socket.close();
    }
  };
}

/** 此函数在浏览器新文档内运行，捕获被框架处理的错误和Blob生命周期。 */
function installPageGuards() {
  window.__reportBrowserFailures = [];
  window.__reportBlobUrls = new Set();
  window.__reportBlobs = new Map();
  window.addEventListener(
    'error',
    (event) => {
      window.__reportBrowserFailures.push(event.message || '资源加载失败：' + (event.target?.src || event.target?.href || 'unknown'));
    },
    true
  );
  window.addEventListener('unhandledrejection', (event) => window.__reportBrowserFailures.push('未处理拒绝：' + String(event.reason)));
  window.addEventListener('securitypolicyviolation', (event) => window.__reportBrowserFailures.push('CSP阻断意外网络：' + event.blockedURI));
  const create = URL.createObjectURL.bind(URL);
  const revoke = URL.revokeObjectURL.bind(URL);
  URL.createObjectURL = (blob) => {
    const url = create(blob);
    window.__reportBlobUrls.add(url);
    window.__reportBlobs.set(url, blob);
    return url;
  };
  URL.revokeObjectURL = (url) => {
    window.__reportBlobUrls.delete(url);
    window.__reportBlobs.delete(url);
    revoke(url);
  };
  window.open = () => {
    throw Error('隔离回归禁止打开新页面');
  };
}
