import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fixture } from './api.mjs';
import { runBusinessScenarios } from './business-scenarios.mjs';
import { runDesignerScenarios } from './designer-scenarios.mjs';
import { assertVisualReady, waitForVisualReady } from './visual.mjs';
import { runLifecycleScenario } from './lifecycle-scenario.mjs';

/** 运行真实组件初始回归；后续props与业务场景可在本文件追加。 */
export async function runScenarios(cdp, host) {
  const { command, evaluate, ready, healthy } = cdp;
  const completed = host.checks;
  const suite = process.env.REPORT_TEST_SUITE || 'all';
  assert.ok(['all', 'business'].includes(suite), '未知REPORT_TEST_SUITE：' + suite);

  /** 每个计数对应完整断言和异常、网络、页面宽度检查。 */
  async function scenario(name, action) {
    await action();
    await healthy();
    assert.deepEqual(host.failures, []);
    completed.push(name);
    process.stdout.write('  PASS ' + completed.length + ' ' + name + '\n');
  }

  /** 用真实可见控件中心生成指针坐标，拒绝零尺寸或屏外目标。 */
  async function point(selector) {
    await evaluate(`document.querySelector(${JSON.stringify(selector)})?.scrollIntoView({ block: 'center', inline: 'nearest' })`);
    await ready(`document.querySelector(${JSON.stringify(selector)})`);
    await evaluate(`Promise.all(document.getAnimations().filter(animation =>
      animation.effect?.getComputedTiming().iterations !== Infinity).map(animation => animation.finished.catch(() => {})))`);
    await ready(`(() => {
      const element = document.querySelector(${JSON.stringify(selector)});
      if (!element) return false;
      const rect = element.getBoundingClientRect();
      return !element.disabled && element.contains(document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2));
    })()`);
    const rect = await evaluate(`(() => {
      const element = document.querySelector(${JSON.stringify(selector)});
      const rect = element.getBoundingClientRect();
      const x = rect.left + rect.width / 2, y = rect.top + rect.height / 2;
      return { x, y, width: rect.width, height: rect.height, disabled: !!element.disabled,
        hit: element.contains(document.elementFromPoint(x, y)), blocker: document.elementFromPoint(x, y)?.outerHTML.slice(0, 320) };
    })()`);
    assert.ok(rect.width > 0 && rect.height > 0 && !rect.disabled, '目标控件不可交互：' + selector);
    assert.ok(rect.hit, '目标控件被遮挡或不在视口内：' + selector + ' ' + JSON.stringify(rect));
    return { x: rect.x, y: rect.y };
  }

  /** 鼠标事件由CDP注入，不调用组件内部处理器。 */
  async function mouse(type, position, pressed = false) {
    await command('Input.dispatchMouseEvent', { type, ...position, button: 'left', buttons: pressed ? 1 : 0, clickCount: 1 });
  }

  /** 点击可见DOM元素，使真实浏览器完成焦点与指针行为。 */
  async function click(selector) {
    const position = await point(selector);
    await mouse('mouseMoved', position);
    await mouse('mousePressed', position, true);
    await mouse('mouseReleased', position);
  }

  /** 按文字定位真实组件按钮，排除页面隐藏副本。 */
  async function button(text) {
    const found = await evaluate(`(() => {
      const element = [...document.querySelectorAll('button')].find(button => button.textContent.trim() === ${JSON.stringify(text)} && button.getClientRects().length);
      if (!element) return false;
      element.dataset.browserTarget = 'current';
      return true;
    })()`);
    assert.ok(found, '未找到按钮：' + text);
    try {
      await click('[data-browser-target="current"]');
    } finally {
      await evaluate('document.querySelector("[data-browser-target]")?.removeAttribute("data-browser-target")');
    }
  }

  /** 键盘事件由操作系统风格的CDP输入进入组件。 */
  async function escape() {
    for (const type of ['keyDown', 'keyUp'])
      await command('Input.dispatchKeyEvent', { type, key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
  }

  /** 一个拖动包含多个移动事件，释放前检查没有提交历史。 */
  async function drag(selector, dx, dy = 0, cancel = false) {
    const position = await point(selector);
    const before = await evaluate('window.__reportHarness.updateCount');
    if (cancel) await evaluate('document.querySelector(".grid-toolbar button").focus()');
    await mouse('mouseMoved', position);
    await mouse('mousePressed', position, true);
    await ready('window.__reportHarness.gesture');
    for (const fraction of [0.25, 0.5, 1]) await mouse('mouseMoved', { x: position.x + dx * fraction, y: position.y + dy * fraction }, true);
    assert.equal(await evaluate('window.__reportHarness.updateCount'), before, '指针预览不应写入历史');
    if (cancel) await escape();
    await mouse('mouseReleased', { x: position.x + dx, y: position.y + dy });
    await ready('!window.__reportHarness.gesture');
    assert.equal(await evaluate('window.__reportHarness.updateCount'), before + (cancel ? 0 : 1));
  }

  /** 读取草稿完整序列化结果，用于历史整次恢复断言。 */
  const snapshot = () => evaluate('JSON.stringify(window.__reportHarness.dsl)');

  /** 确保弹窗关闭并完成动画与私有缩略图回收。 */
  async function closedDialog() {
    await ready('![...document.querySelectorAll(".el-overlay")].some(element => getComputedStyle(element).display !== "none")');
  }

  /** 等待真实动画与加载层收尾后截图，失败时也保存原始样式和合成画面。 */
  async function rendered(name = 'scenario-' + (completed.length + 1)) {
    await healthy();
    const visual = await evaluate(`(${waitForVisualReady.toString()})()`);
    const image = await command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    const bytes = Buffer.from(image.data, 'base64');
    assert.ok(bytes.length > 1000, '浏览器没有产生有效画面');
    const path = join(host.artifactDirectory, name + '-synthetic.png');
    await writeFile(path, bytes);
    const evidence = { path, synthetic: true, viewport: visual.viewport, visual };
    host.screenshots.push(evidence);
    await healthy();
    evidence.styleAssertions = assertVisualReady(visual);
    return visual;
  }

  await command('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false });
  await command('Page.navigate', { url: host.origin });
  await ready('window.__reportHarness && document.querySelectorAll(".fixed-cell").length === 5');

  /** 可复核失败防线；默认不注入，外部网络故障同时验证CSP及后端路径阻断。 */
  const fault = process.env.REPORT_TEST_FAULT;
  if (fault) {
    const injections = {
      exception: 'setTimeout(() => { throw Error("合成页面异常"); }, 0)',
      rejection: 'void Promise.reject(Error("合成未处理拒绝"))',
      network: 'void fetch("https://report-browser.invalid/unexpected").catch(() => {});',
      overflow: 'document.body.appendChild(Object.assign(document.createElement("div"), { style: "width:3000px;height:1px" }))'
    };
    assert.ok(Object.hasOwn(injections, fault), '未知REPORT_TEST_FAULT：' + fault);
    await evaluate(injections[fault].replace(/;$/, ''));
    if (fault === 'network') await evaluate('void fetch("/dev-api/report/forbidden").catch(() => {})');
    await evaluate('new Promise(resolve => setTimeout(resolve, 100))');
    await healthy();
    throw Error('注入故障没有被防线捕获：' + fault);
  }

  await runBusinessScenarios(cdp, host, scenario, click, rendered);
  if (suite === 'business') return { suite, passed: completed.length, scenarios: completed };
  await command('Page.navigate', { url: host.origin });
  await ready('window.__reportHarness && document.querySelectorAll(".fixed-cell").length === 5');

  await scenario('桌面1440×1100：真实画布、纸张设置与完整ID', async () => {
    assert.equal(await evaluate('window.__reportHarness.dsl.datasetVersionId'), fixture.datasetVersionId);
    assert.equal(await evaluate('document.querySelector(".grid-toolbar .el-select").textContent.includes("%")'), true);
    assert.equal(await evaluate('document.querySelectorAll(".detail-column").length'), 3);
    assert.match(await evaluate('document.body.textContent'), /隔离合成数据/);
    await rendered();
  });

  await scenario('列边界多次移动仅一次历史，撤销与重做恢复整次手势', async () => {
    const before = await snapshot();
    const total = await evaluate('window.__reportHarness.dsl.columns.reduce((sum, column) => sum + column.width, 0)');
    await drag('.column-resizer', 24);
    const after = await snapshot();
    assert.notEqual(after, before);
    assert.equal(await evaluate('window.__reportHarness.dsl.columns.reduce((sum, column) => sum + column.width, 0)'), total);
    await click('[data-test="undo"]');
    assert.equal(await snapshot(), before);
    assert.equal(await evaluate('document.querySelector("[data-test=undo]").disabled'), true);
    await click('[data-test="redo"]');
    assert.equal(await snapshot(), after);
  });

  await scenario('工具栏焦点下Escape取消拖动，无新历史', async () => {
    const before = await snapshot();
    await drag('.column-resizer', 24, 0, true);
    assert.equal(await snapshot(), before);
  });

  await scenario('固定行拖动仅提交一次且可以整次撤销', async () => {
    const before = await snapshot();
    await drag('.row-resizer', 0, 16);
    assert.notEqual(await snapshot(), before);
    await click('[data-test="undo"]');
    assert.equal(await snapshot(), before);
  });

  await scenario('图库过滤未发布资源，选择绑定完整不可变版本', async () => {
    const requests = host.requests.filter((item) => item.path === '/__report_mock/report/assets').length;
    await button('添加私有图片');
    await ready('document.querySelector(".asset-choice img")?.naturalWidth > 0 && !document.querySelector(".asset-choice").disabled');
    assert.equal(await evaluate('document.querySelectorAll(".asset-choice").length'), 1);
    assert.match(await evaluate('document.querySelector(".asset-choice").textContent'), /合成测试图片/);
    assert.equal(host.requests.filter((item) => item.path === '/__report_mock/report/assets').length, requests + 1);
    await click('.asset-choice');
    await closedDialog();
    await ready('document.querySelector(".paper-media img")?.naturalWidth > 0');
    assert.equal(await evaluate('window.__reportHarness.dsl.print.media[0].assetVersionId'), fixture.assetVersionId);
    assert.equal(await evaluate('window.__reportBlobUrls.size'), 1, '图库缩略图必须回收，仅保留画布图片');
  });

  await scenario('媒体移动仅一次历史，位置受纸张边界约束', async () => {
    const before = await snapshot();
    await drag('.paper-media', 80, 100);
    const media = await evaluate('window.__reportHarness.dsl.print.media[0]');
    assert.ok(
      media.x >= 36 && media.y >= 36 && media.x + media.width <= 595.28 - 36 && media.y + media.height <= 841.89 - 36,
      '媒体超出纸张：' + JSON.stringify({ media, page: await evaluate('window.__reportHarness.dsl.page') })
    );
    assert.notEqual(await snapshot(), before);
    await click('[data-test="undo"]');
    assert.equal(await snapshot(), before);
  });

  await scenario('真实打印设置添加二维码及Code128，仅绑定批准编码字段', async () => {
    await button('添加 QR');
    await button('添加 Code128');
    const media = await evaluate('window.__reportHarness.dsl.print.media');
    assert.deepEqual(
      media.map((item) => item.kind),
      ['IMAGE', 'QR', 'CODE128']
    );
    assert.equal(media[1].field, 'materialCode');
    assert.equal(media[1].width, media[1].height);
    assert.equal(media[2].field, 'materialCode');
    assert.equal(await evaluate('document.querySelectorAll(".paper-media").length'), 3);
  });

  await scenario('取消图库不改变媒体，释放弹窗Blob', async () => {
    const before = await snapshot();
    const blobs = await evaluate('window.__reportBlobUrls.size');
    await button('添加私有图片');
    await ready('document.querySelector(".asset-choice img")?.naturalWidth > 0');
    assert.ok((await evaluate('window.__reportBlobUrls.size')) > blobs);
    await escape();
    await closedDialog();
    assert.equal(await snapshot(), before);
    assert.equal(await evaluate('window.__reportBlobUrls.size'), blobs);
  });

  await scenario('桌面深色模式真实主题变量与有效画面', async () => {
    const light = await evaluate('getComputedStyle(document.body).backgroundColor');
    await click('[data-test="theme"]');
    await ready('document.documentElement.classList.contains("dark")');
    assert.notEqual(await evaluate('getComputedStyle(document.body).backgroundColor'), light);
    await rendered();
  });

  await scenario('390×1000窄屏首次加载画布及媒体，无页面横向溢出', async () => {
    await evaluate('window.__reportHarness.unmount()');
    await healthy();
    await command('Emulation.setDeviceMetricsOverride', { width: 390, height: 1000, deviceScaleFactor: 1, mobile: false });
    await command('Page.navigate', { url: host.origin });
    await ready('window.__reportHarness && document.querySelectorAll(".fixed-cell").length === 5');
    await ready('document.querySelector(".grid-viewport").clientWidth < 390');
    await button('添加 QR');
    assert.equal(await evaluate('document.querySelectorAll(".paper-media").length'), 1);
    await rendered();
  });

  await scenario('390px图库可选择且弹窗保持在视口内', async () => {
    await button('添加私有图片');
    await ready('document.querySelector(".asset-choice img")?.naturalWidth > 0');
    const rect = await evaluate(
      '(() => { const rect = document.querySelector(".el-dialog").getBoundingClientRect(); return { left: rect.left, right: rect.right }; })()'
    );
    assert.ok(rect.left >= 0 && rect.right <= 390);
    await healthy();
    await escape();
    await closedDialog();
  });

  await scenario('目录权限撤销：服务端拒绝并展示真实图库错误', async () => {
    await evaluate('window.__reportHarness.session.permissions = ["report:runtime:preview"]');
    await button('添加私有图片');
    await ready('document.querySelector(".asset-search") && document.querySelector(".el-dialog .el-alert--error")');
    assert.match(await evaluate('document.querySelector(".el-dialog").textContent'), /图片目录读取失败/);
    assert.equal(host.requests.at(-1).status, 403);
    await escape();
    await closedDialog();
    await evaluate('window.__reportHarness.setPermissions([])');
    assert.equal(await evaluate('[...document.querySelectorAll("button")].some(button => button.textContent.trim() === "添加私有图片")'), false);
  });

  await scenario('图片预览权限撤销：不可读取、不可选择、不替换固定绑定', async () => {
    const before = await snapshot();
    await evaluate('window.__reportHarness.setPermissions(["report:asset:list"])');
    await button('添加私有图片');
    await ready('document.querySelector(".asset-placeholder")?.textContent.includes("预览不可用")');
    assert.equal(await evaluate('document.querySelector(".asset-choice").disabled'), true);
    assert.ok(host.requests.some((item) => item.path.includes('/versions/') && item.status === 403));
    await escape();
    await closedDialog();
    assert.equal(await snapshot(), before);
  });

  await scenario('Mock严格验证code、完整ID、租户、权限及409发布版本', async () => {
    const previousRuns = host.runs.length;
    await evaluate('window.__reportHarness.reset()');
    const cases = await evaluate(`(async () => {
      const harness = window.__reportHarness;
      const fixture = harness.fixture;
      const run = { reportId: fixture.reportId, mode: 'SNAPSHOT', parameters: { orderId: fixture.reportId }, expectedVersionId: fixture.reportVersionId };
      const results = [];
      const attempt = async (url, data) => {
        try { await harness.request({ url, method: 'POST', data }); return 200; }
        catch (error) { return error.status; }
      };
      results.push(await attempt('/report/definitions', { dsl: { ...harness.dsl, code: 'invalid-code' } }));
      results.push(await attempt('/report/definitions', { dsl: { ...harness.dsl, datasetVersionId: Number(fixture.datasetVersionId) } }));
      results.push(await attempt('/report/definitions', { dsl: harness.dsl }));
      results.push(await attempt('/report/runtime/runs', { ...run, reportId: Number(fixture.reportId) }));
      results.push(await attempt('/report/runtime/runs', { ...run, expectedVersionId: fixture.assetVersionId }));
      harness.session.tenant = 'OTHER_TENANT';
      results.push(await attempt('/report/runtime/runs', run));
      harness.session.tenant = fixture.tenant;
      harness.session.permissions = [];
      results.push(await attempt('/report/runtime/runs', run));
      harness.session.permissions = [...fixture.permissions];
      results.push(await attempt('/report/runtime/runs', run));
      return results;
    })()`);
    assert.deepEqual(cases, [400, 400, 200, 400, 409, 403, 403, 200]);
    assert.equal(host.runs.length, previousRuns + 1, '所有拒绝情况都不能创建run');
    assert.equal(host.runs.at(-1).reportVersionId, fixture.reportVersionId);
    assert.equal(host.runs.at(-1).parameters.orderId, fixture.reportId);
  });

  await scenario('卸载真实组件后所有私有Blob均已回收', async () => {
    await evaluate('window.__reportHarness.unmount()');
    await ready('!document.querySelector(".paper-editor") && window.__reportBlobUrls.size === 0');
  });

  await runDesignerScenarios(cdp, host, scenario, { click, button, escape, rendered });
  await runLifecycleScenario(cdp, host, scenario, { click, button, rendered });
  return { suite, passed: completed.length, scenarios: completed };
}
