import assert from 'node:assert/strict';
import { fixture } from './api.mjs';

/** 通过整页设计器验证父级选区、属性、历史和可访问侧栏的真实组合。 */
export async function runDesignerScenarios(cdp, host, scenario, { click, button, escape, rendered }) {
  const { command, evaluate, ready, healthy } = cdp;
  await command('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false });
  await command('Page.navigate', { url: host.origin });
  await ready('window.__reportHarness');
  await evaluate('window.__reportHarness.startDesigner()');
  await ready(
    'document.querySelector(".report-designer [aria-label=报表纸张画布]") && document.querySelector(".report-designer").textContent.includes("当前版本")'
  );

  /** 真实媒体DOM保留打印点坐标，缩放不影响步长断言。 */
  async function position() {
    return evaluate(`(() => { const element = document.querySelector('.report-designer .paper-media');
      return { x: parseFloat(element.style.left) * 3 / 4, y: parseFloat(element.style.top) * 3 / 4,
        width: parseFloat(element.style.width) * 3 / 4, height: parseFloat(element.style.height) * 3 / 4 }; })()`);
  }

  /** 精度允许CSS小数串舍入，但不允许毫米与点数混用。 */
  function near(actual, expected) {
    assert.ok(Math.abs(actual - expected) < 0.002, `${actual} != ${expected}`);
  }

  /** 按键完全由CDP进入浏览器，连续keydown共享一次keyup提交。 */
  async function arrow(count = 1, shift = false, cancel = false) {
    for (let index = 0; index < count; index++)
      await command('Input.dispatchKeyEvent', {
        type: 'keyDown',
        key: 'ArrowRight',
        code: 'ArrowRight',
        windowsVirtualKeyCode: 39,
        modifiers: shift ? 8 : 0,
        autoRepeat: index > 0
      });
    if (cancel) await escape();
    await command('Input.dispatchKeyEvent', { type: 'keyUp', key: 'ArrowRight', code: 'ArrowRight', windowsVirtualKeyCode: 39 });
    await ready('true');
  }

  /** 整页工具栏独立于夹具外层历史按钮。 */
  const undo = () => click('.designer-toolbar button[title^="撤销"]');

  /** 数字输入只经浏览器编辑与Enter提交，不调用组件方法。 */
  async function number(label, value) {
    const selector = `input[aria-label="${label}"], [aria-label="${label}"] input`;
    await click(selector);
    await command('Input.dispatchKeyEvent', { type: 'keyDown', key: 'a', code: 'KeyA', modifiers: 2, windowsVirtualKeyCode: 65 });
    await command('Input.dispatchKeyEvent', { type: 'keyUp', key: 'a', code: 'KeyA', modifiers: 2, windowsVirtualKeyCode: 65 });
    await command('Input.insertText', { text: String(value) });
    for (const type of ['keyDown', 'keyUp'])
      await command('Input.dispatchKeyEvent', { type, key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
    await ready('true');
  }

  await scenario('真实设计器整页：固定数据集契约、更多菜单及校验入口', async () => {
    await click('[aria-label="设计器更多操作"]');
    await ready(
      'Array.from(document.querySelectorAll(".el-dropdown-menu")).some(element => element.textContent.includes("导出模板包") && element.textContent.includes("下载定义"))'
    );
    const before = host.requests.filter((entry) => entry.path === '/__report_mock/report/definitions/validate').length;
    await evaluate(`Array.from(document.querySelectorAll('.el-dropdown-menu__item')).find(element =>
      element.textContent.trim() === '校验定义' && element.getClientRects().length).dataset.designerTarget = 'validate'`);
    await click('[data-designer-target="validate"]');
    await ready('!document.querySelector(".designer-toolbar .is-loading")');
    assert.equal(host.requests.filter((entry) => entry.path === '/__report_mock/report/definitions/validate').length, before + 1);
    await rendered('designer-desktop');
  });

  await scenario('设计器键盘：0.5mm、Shift 5mm、连续按键一次撤销及Escape取消', async () => {
    await button('添加 QR');
    await click('[aria-label="选择二维码 1"]');
    const before = await position();
    await arrow();
    near((await position()).x, before.x + (0.5 * 72) / 25.4);
    await undo();
    near((await position()).x, before.x);
    await click('[aria-label="选择二维码 1"]');
    await arrow(1, true);
    near((await position()).x, before.x + (5 * 72) / 25.4);
    await undo();
    await click('[aria-label="选择二维码 1"]');
    await arrow(4);
    near((await position()).x, before.x + (2 * 72) / 25.4);
    await undo();
    near((await position()).x, before.x);
    await click('[aria-label="选择二维码 1"]');
    await arrow(3, false, true);
    near((await position()).x, before.x);
  });

  await scenario('媒体属性毫米输入、步进立即提交、二维码正方形及输入框方向键保护', async () => {
    await number('横坐标（mm）', 30);
    near((await position()).x, (30 * 72) / 25.4);
    const before = await position();
    await arrow();
    near((await position()).x, before.x);
    await number('宽度（mm）', 25);
    const resized = await position();
    near(resized.width, (25 * 72) / 25.4);
    near(resized.width, resized.height);
    await evaluate(`document.querySelector('input[aria-label="宽度（mm）"], [aria-label="宽度（mm）"] input').closest('.el-input-number')
      .querySelector('.el-input-number__increase').dataset.designerTarget = 'width-increase'`);
    await click('[data-designer-target="width-increase"]');
    await ready('true');
    near((await position()).width, (25.5 * 72) / 25.4);
    await undo();
    near((await position()).width, resized.width);
    assert.equal(await evaluate('!!document.querySelector("[aria-label=媒体绑定字段]")'), true);
  });

  await scenario('删除媒体使旧选区失效，撤销后可重新选择当前媒体', async () => {
    await click('[aria-label="删除选中媒体"]');
    await ready('!document.querySelector(".report-designer .paper-media")');
    assert.equal(await evaluate(`!!document.querySelector('input[aria-label="横坐标（mm）"]')`), false);
    await undo();
    await click('[aria-label="选择二维码 1"]');
    await ready('!!document.querySelector("[aria-label=媒体绑定字段]")');
    await click('[aria-label="删除选中媒体"]');
  });

  await scenario('真实图片属性读取安全元数据、完整固定版本及替换入口', async () => {
    await button('添加私有图片');
    await ready('document.querySelector(".asset-choice img")?.naturalWidth > 0');
    await click('.asset-choice');
    await ready('document.querySelector(".asset-metadata")?.textContent.includes("1 × 1 px")');
    assert.match(await evaluate('document.querySelector(".asset-metadata").textContent'), /合成测试图片/);
    assert.match(await evaluate('document.querySelector(".asset-version").textContent'), new RegExp(fixture.assetVersionId));
    assert.equal(await evaluate('!!document.querySelector("[aria-label=替换图片资产]")'), true);
    assert.ok(host.requests.some((entry) => entry.path.endsWith('/' + fixture.assetVersionId + '/metadata') && entry.status === 200));
  });

  await scenario('元数据撤权保持原图片固定版本，不自动改绑', async () => {
    await evaluate(
      'window.__reportHarness.session.permissions = window.__reportHarness.session.permissions.filter(permission => permission !== "report:runtime:preview")'
    );
    await button('刷新图片属性');
    await ready('document.querySelector(".selection-inspector .el-alert")?.textContent.includes("原版本已保留")');
    assert.match(await evaluate('document.querySelector(".asset-version").textContent'), new RegExp(fixture.assetVersionId));
    await evaluate('window.__reportHarness.session.permissions = [...window.__reportHarness.fixture.permissions]');
    await button('刷新图片属性');
    await ready('!!document.querySelector(".asset-metadata")');
    await evaluate('window.__reportHarness.stopDesigner()');
    await ready('window.__reportBlobUrls.size === 0');
  });

  await command('Emulation.setDeviceMetricsOverride', { width: 390, height: 1000, deviceScaleFactor: 1, mobile: false });
  await command('Page.navigate', { url: host.origin });
  await ready('window.__reportHarness');
  await evaluate('window.__reportHarness.startDesigner()');
  await ready('!!document.querySelector("[aria-label=打开字段面板]")');

  await scenario('390px真实字段侧栏：打开转移焦点，Escape关闭回到触发按钮', async () => {
    await click('[aria-label="打开字段面板"]');
    await ready('document.querySelector("[aria-label=字段侧栏内容]")?.closest(".el-drawer")?.contains(document.activeElement)');
    await healthy();
    await escape();
    await ready('document.activeElement?.getAttribute("aria-label") === "打开字段面板"');
  });

  await scenario('390px真实媒体属性侧栏与深色画面，无页面横向溢出', async () => {
    await button('添加 QR');
    await click('[aria-label="打开属性面板"]');
    await ready('document.querySelector("[aria-label=属性侧栏内容]")?.closest(".el-drawer")?.contains(document.activeElement)');
    assert.equal(await evaluate('!!document.querySelector("[aria-label=媒体绑定字段]")'), true);
    await healthy();
    await evaluate(`document.querySelector('.designer-sidebar [aria-label="媒体绑定字段"]').closest('.el-select')
      .dataset.designerTarget = 'barcode-field-select'`);
    await click('[data-designer-target="barcode-field-select"]');
    await ready('Array.from(document.querySelectorAll(".el-select-dropdown")).some(element => element.getClientRects().length)');
    await escape();
    await ready('!Array.from(document.querySelectorAll(".el-select-dropdown")).some(element => element.getClientRects().length)');
    assert.equal(
      await evaluate('!!document.querySelector("[aria-label=属性侧栏内容]")?.getClientRects().length'),
      true,
      '第一次Escape应只关闭下拉菜单'
    );
    await escape();
    await ready('document.activeElement?.getAttribute("aria-label") === "打开属性面板"');
    await click('[data-test="theme"]');
    await rendered();
  });

  await scenario('应用内专注编辑由Escape退出，画布和选中二维码保持', async () => {
    await button('专注编辑');
    await ready('!!document.querySelector(".focus-editor")');
    await rendered('designer-focus-mode');
    await escape();
    await ready('!document.querySelector(".focus-editor")');
    assert.equal(await evaluate('document.querySelectorAll(".report-designer .paper-media").length'), 1);
    await healthy();
    await evaluate('window.__reportHarness.stopDesigner()');
    await ready('window.__reportBlobUrls.size === 0');
  });
}
