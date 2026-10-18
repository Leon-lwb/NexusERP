import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';
import {
  connectBrowser,
  createLedger,
  createWorkspace,
  launchBrowser,
  liveOrigin,
  removeWorkspace,
  safeSummary,
  stopBrowser,
  writeVerification
} from './report-live-browser/browser.mjs';
import { createActions } from './report-live-browser/actions.mjs';

/** 通过真实预填登录表单进入应用，异步租户初始化结束前不提交。 */
async function login(cdp, actions, ledger) {
  await actions.wait('!!document.querySelector(".login-form .login-btn")', 30000);
  const deadline = Date.now() + 30000;
  while (!ledger.loginReady.tenant || !ledger.loginReady.captcha) {
    ledger.signal.throwIfAborted();
    assert.ok(Date.now() < deadline, '真实登录租户与验证码初始化超时');
    await delay(70, undefined, { signal: ledger.signal });
  }
  await actions.settled();
  if (process.env.REPORT_LIVE_USERNAME !== undefined)
    await actions.fill('.login-form input[placeholder="用户名 / 手机号"]', process.env.REPORT_LIVE_USERNAME);
  if (process.env.REPORT_LIVE_PASSWORD !== undefined) await actions.fill('.login-form input[type="password"]', process.env.REPORT_LIVE_PASSWORD);
  const state = await cdp.evaluate(`(() => {
    const form = document.querySelector('.login-form');
    const captcha = form.querySelector('input[placeholder="验证码"]');
    const tenant = form.querySelector('.el-select');
    return {
      filled: !!form.querySelector('input[placeholder="用户名 / 手机号"]')?.value && !!form.querySelector('input[type="password"]')?.value,
      captchaRequired: !!captcha && captcha.getClientRects().length > 0 && !captcha.value,
      tenantReady: !tenant || !!tenant.querySelector('.el-select__selected-item:not(.is-placeholder)') || Array.from(tenant.querySelectorAll('input')).some(input => !!input.value),
      remembering: !!form.querySelector('.remember-me input:checked')
    };
  })()`);
  if (state.captchaRequired) return { blocked: '真实登录启用了验证码，需要人工完成前置条件' };
  assert.ok(state.filled && state.tenantReady, '真实登录表单或默认租户尚未初始化');
  if (state.remembering) await actions.click('.login-form .remember-me');
  await actions.press('登录', '.login-form .login-btn');
  await actions.wait(
    `(() => {
    const main = document.querySelector('.app-main');
    return location.pathname !== '/login' && !!main && main.getClientRects().length > 0 && Array.from(document.querySelectorAll('.sidebar-container .menu-title')).some(element => element.textContent.trim() === '报表中心');
  })()`,
    30000
  );
  await actions.settled();
  return true;
}

/** 将取消信号传入场景等待，超时后进入统一清理并阻止后续CDP写操作。 */
async function untilAborted(action, signal) {
  signal.throwIfAborted();
  let onAbort;
  try {
    return await Promise.race([
      Promise.resolve().then(action),
      new Promise((done, reject) => {
        onAbort = () => reject(signal.reason || Error('运行已中断'));
        signal.addEventListener('abort', onAbort, { once: true });
      })
    ]);
  } finally {
    if (onAbort) signal.removeEventListener('abort', onAbort);
  }
}

/** 动态执行独立场景文件；任何步骤失败、未执行场景或运行异常均返回非零。 */
export async function main() {
  const origin = liveOrigin();
  assert.ok(typeof WebSocket === 'function', '当前Node必须支持原生WebSocket');
  const timeout = Number(process.env.REPORT_LIVE_TIMEOUT_MS || 900000);
  assert.ok(Number.isInteger(timeout) && timeout >= 1000 && timeout <= 1800000, '全套超时必须为一秒到三十分钟');
  const suite = process.env.REPORT_LIVE_SUITE || 'all';
  const startedAt = new Date().toISOString();
  const controller = new AbortController();
  const ledger = createLedger({ signal: controller.signal, abort: (reason) => controller.abort(reason) });
  let workspace,
    browser,
    failed = false,
    verification;
  const cleanup = { browserExited: false, profileRemoved: false, downloadsRemoved: false };
  const interrupt = () => controller.abort(Error('运行被信号中断'));
  const onFailure = (error) => {
    ledger.noteError(error, 'process');
    controller.abort(Error('脚本发生异常'));
  };
  process.on('SIGINT', interrupt);
  process.on('SIGTERM', interrupt);
  process.on('uncaughtException', onFailure);
  process.on('unhandledRejection', onFailure);
  const deadline = setTimeout(() => controller.abort(Error('真实浏览器测试超时')), timeout);
  try {
    workspace = await createWorkspace();
    const { runScenarios } = await import('./report-live-browser/scenarios.mjs');
    assert.equal(typeof runScenarios, 'function', '场景模块必须导出runScenarios');
    browser = await launchBrowser(workspace, ledger);
    const cdp = await untilAborted(() => connectBrowser(browser, ledger, origin), controller.signal);
    const actions = createActions(cdp, ledger);
    const navigation = await cdp.command('Page.navigate', { url: origin + '/login' });
    assert.ok(!navigation.errorText, '真实本机站点导航失败');
    const authentication = await actions.step('真实表单登录与应用菜单就绪', () => login(cdp, actions, ledger));
    if (authentication.status === 'PASS') {
      const before = ledger.checks.length;
      const result = await untilAborted(
        () =>
          runScenarios({
            cdp,
            actions,
            ledger,
            artifactDirectory: workspace.artifactDirectory,
            downloadDirectory: workspace.downloadDirectory,
            suite
          }),
        controller.signal
      );
      assert.ok(ledger.checks.length > before, '场景必须执行非零步骤');
      if (result !== ledger && result && (result.status === 'FAIL' || Number(result.failed) > 0)) failed = true;
      await actions.settled(15000);
      await browser.flush();
    }
  } catch (error) {
    failed = true;
    ledger.noteError(error, 'runner');
  } finally {
    try {
      const result = await stopBrowser(browser);
      cleanup.browserExited = result.exited;
      cleanup.strategy = result.strategy;
    } catch (error) {
      ledger.noteError(error, 'browser-cleanup');
      browser?.cdp?.close();
    }
    if (workspace) {
      try {
        await removeWorkspace(workspace);
        cleanup.profileRemoved = true;
        cleanup.downloadsRemoved = true;
      } catch (error) {
        ledger.noteError(error, 'directory-cleanup');
      }
      failed ||= ledger.requests.some(
        (request) =>
          (request.status >= 400 || (request.code !== null && request.code !== 0 && request.code !== 200)) &&
          !ledger.expectedBlockedRequests.has(request)
      );
      failed ||=
        controller.signal.aborted ||
        ledger.exceptions.length > 0 ||
        ledger.checks.some((check) => check.status === 'FAIL') ||
        !cleanup.browserExited ||
        !cleanup.profileRemoved ||
        !cleanup.downloadsRemoved;
      try {
        verification = await writeVerification(workspace, ledger, { origin, suite, startedAt, failed, cleanup });
      } catch (error) {
        failed = true;
        ledger.noteError(error, 'evidence');
      }
    }
    clearTimeout(deadline);
    process.off('SIGINT', interrupt);
    process.off('SIGTERM', interrupt);
    process.off('uncaughtException', onFailure);
    process.off('unhandledRejection', onFailure);
  }
  failed ||= ledger.exceptions.length > 0 || ledger.checks.some((check) => check.status === 'FAIL');
  const blocked = ledger.checks.some((check) => check.status === 'blocked');
  process.stdout.write(
    JSON.stringify({
      live: true,
      synthetic: false,
      status: failed ? 'FAIL' : blocked ? 'blocked' : 'PASS',
      steps: ledger.checks.length,
      failed: ledger.checks.filter((check) => check.status === 'FAIL').length,
      blocked: ledger.checks.filter((check) => check.status === 'blocked').length,
      verification
    }) + '\n'
  );
  return failed ? 1 : blocked ? 2 : 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().then(
    (code) => {
      process.exitCode = code;
      setTimeout(() => process.exit(code), 1500).unref();
    },
    (error) => {
      console.error(safeSummary(error));
      process.exitCode = 1;
    }
  );
}
