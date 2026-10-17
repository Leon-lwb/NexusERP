import assert from 'node:assert/strict';

/** 通过实际布局从各菜单离开并返回，覆盖串行过渡与缓存恢复。 */
export async function runNavigationScenarios(cdp, host, scenario, click) {
  const { command, evaluate, ready } = cdp;
  const menus = [
    { path: 'assets', content: '.el-table' },
    { path: 'query', content: '.el-table' },
    { path: 'dashboard', content: '.dashboard-picker' },
    { path: 'subscription', content: '.el-table' },
    { path: 'batch', content: '.el-table' }
  ];
  for (const cache of ['on', 'off']) {
    await command('Page.navigate', { url: host.origin + '/?navigation=1&cache=' + cache });
    await ready('document.querySelector(".report-assets .el-table") && !document.querySelector(".el-loading-mask")');
    for (const animation of [false, true]) {
      if ((await evaluate('document.querySelector("[data-test=animation]").checked')) !== animation) await click('[data-test="animation"]');
      for (const { path: menu, content } of menus) {
        await scenario(`正式布局菜单切换：${menu}，缓存${cache}，随机动画${animation}`, async () => {
          await click('[data-menu="' + menu + '"]');
          await ready(`document.querySelector('.report-${menu} ${content}') && !document.querySelector('.el-loading-mask')`);
          const requestsBefore = host.requests.length;
          await click('[data-menu="exports"]');
          await ready('document.querySelector(".report-exports .el-table") && !document.querySelector(".el-loading-mask")');
          assert.ok(
            host.requests.slice(requestsBefore).some((request) => request.path === '/__report_mock/report/runtime/exports' && request.status === 200),
            '离开菜单后，目标页面必须完成挂载或激活并载入列表'
          );
          await click('[data-menu="' + menu + '"]');
          await ready(`document.querySelector('.report-${menu} ${content}') && !document.querySelector('.el-loading-mask')`);
          assert.deepEqual(await evaluate('window.__reportNavigationWarnings'), [], '页面根节点必须满足正式Transition的要求');
        });
      }
    }
  }
}
