import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { constants } from 'node:fs';
import { access, mkdir, mkdtemp, readFile, realpath, rm, stat, writeFile } from 'node:fs/promises';
import { createConnection } from 'node:net';
import { basename, delimiter, isAbsolute, join, relative, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { connectCdp } from './report-browser/cdp.mjs';
import { startServer, uiRoot } from './report-browser/server.mjs';
import { runScenarios } from './report-browser/scenarios.mjs';

/** 指定浏览器优先且不可静默回退，默认仅探测本机Edge和Chrome。 */
async function findBrowser() {
  const specified = process.env.REPORT_TEST_BROWSER;
  const candidates = specified
    ? [resolve(specified)]
    : process.platform === 'win32'
      ? [
          join(process.env['PROGRAMFILES(X86)'] || 'C:/Program Files (x86)', 'Microsoft/Edge/Application/msedge.exe'),
          join(process.env.PROGRAMFILES || 'C:/Program Files', 'Microsoft/Edge/Application/msedge.exe'),
          join(process.env.PROGRAMFILES || 'C:/Program Files', 'Google/Chrome/Application/chrome.exe'),
          join(process.env['PROGRAMFILES(X86)'] || 'C:/Program Files (x86)', 'Google/Chrome/Application/chrome.exe'),
          join(process.env.LOCALAPPDATA || '', 'Microsoft/Edge/Application/msedge.exe'),
          join(process.env.LOCALAPPDATA || '', 'Google/Chrome/Application/chrome.exe')
        ]
      : process.platform === 'darwin'
        ? ['/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge', '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome']
        : (process.env.PATH || '')
            .split(delimiter)
            .flatMap((directory) =>
              ['microsoft-edge', 'microsoft-edge-stable', 'google-chrome', 'google-chrome-stable', 'chromium', 'chromium-browser'].map((name) =>
                join(directory, name)
              )
            );
  for (const candidate of candidates) {
    try {
      if (!(await stat(candidate)).isFile()) continue;
      await access(candidate, process.platform === 'win32' ? constants.F_OK : constants.X_OK);
      return candidate;
    } catch {}
  }
  throw Error(
    specified ? 'REPORT_TEST_BROWSER指定的浏览器不存在或不可执行：' + specified : '未找到Edge/Chrome，请用REPORT_TEST_BROWSER指定浏览器可执行文件'
  );
}

/** 等待仅由当前隔离profile创建的动态调试端口。 */
async function debuggerEndpoint(profile, child, launchError, signal) {
  const started = Date.now();
  while (Date.now() - started < 20000) {
    signal.throwIfAborted();
    if (launchError()) throw launchError();
    if (child.exitCode !== null || child.signalCode !== null) throw Error('隔离浏览器在CDP就绪前退出，exit=' + child.exitCode);
    try {
      const [value] = (await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).trim().split(/\r?\n/);
      const port = Number(value);
      if (Number.isInteger(port) && port > 0 && port < 65536) return { port, origin: 'http://127.0.0.1:' + port };
    } catch (error) {
      if (!['ENOENT', 'EBUSY', 'EACCES'].includes(error.code)) throw error;
    }
    await delay(50, undefined, { signal });
  }
  throw Error('浏览器启动超时，没有生成DevToolsActivePort');
}

/** 核对自有监听端口已经释放，不停止任何外部服务。 */
async function portClosed(port) {
  if (!port) return true;
  return new Promise((resolve) => {
    const socket = createConnection({ host: '127.0.0.1', port });
    socket.once('connect', () => {
      socket.destroy();
      resolve(false);
    });
    socket.once('error', (error) => {
      socket.destroy();
      resolve(error.code === 'ECONNREFUSED');
    });
    socket.setTimeout(1000, () => {
      socket.destroy();
      resolve(false);
    });
  });
}

/** 只终止spawn返回的自有进程树，Windows使用隐藏窗口且不按进程名清理。 */
async function stopBrowser(child, exited, cdp) {
  if (!child) return 'not-started';
  if (child.exitCode !== null || child.signalCode !== null) return 'already-exited';
  cdp?.prepareToClose();
  if (cdp) await cdp.command('Browser.close', {}, 2000).catch(() => {});
  if (await Promise.race([exited.then(() => true), delay(2000, undefined, { ref: false }).then(() => false)])) return 'cdp';
  if (process.platform === 'win32') {
    const killer = spawn('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' });
    await new Promise((resolve, reject) => {
      killer.once('exit', resolve);
      killer.once('error', reject);
    });
  } else {
    process.kill(-child.pid, 'SIGTERM');
    if (!(await Promise.race([exited.then(() => true), delay(2000, undefined, { ref: false }).then(() => false)])))
      process.kill(-child.pid, 'SIGKILL');
  }
  assert.ok(await Promise.race([exited.then(() => true), delay(5000, undefined, { ref: false }).then(() => false)]), '自有浏览器未退出');
  return process.platform === 'win32' ? 'pid-tree' : 'process-group';
}

/** 成功、失败、拒绝及中断共用同一finally清理路径。 */
async function main() {
  if (typeof WebSocket !== 'function') throw Error('当前Node缺少原生WebSocket，请使用支持原生WebSocket的Node版本（本机验证v24.21.0）');
  const executable = await findBrowser();
  const temporaryRoot = resolve(uiRoot, 'node_modules/.tmp/report-browser');
  await mkdir(temporaryRoot, { recursive: true });
  const runDirectory = await mkdtemp(join(temporaryRoot, 'run-'));
  const profile = join(runDirectory, 'profile');
  const artifactDirectory = join(temporaryRoot, 'artifacts-' + basename(runDirectory));
  const startedAt = new Date().toISOString();
  const controller = new AbortController();
  const failures = [];
  let host, child, exited, endpoint, cdp, launchError;
  let stderr = '';
  let result;
  let failure, browserInfo, pageFailureState;
  /** 中断只取消当前任务，释放工作在finally中完成。 */
  const interrupt = (signal) => controller.abort(Error('浏览器回归被中断：' + signal));
  const onInterrupt = () => interrupt('SIGINT');
  const onTerminate = () => interrupt('SIGTERM');
  /** 脚本级异常也必须返回非零并释放自有资源。 */
  const onFailure = (error) => {
    failures.push(String(error?.stack || error));
    controller.abort(error);
  };
  process.on('SIGINT', onInterrupt);
  process.on('SIGTERM', onTerminate);
  process.on('uncaughtException', onFailure);
  process.on('unhandledRejection', onFailure);
  const deadline = setTimeout(() => controller.abort(Error('浏览器回归超过120秒')), 120000);
  try {
    await mkdir(artifactDirectory);
    host = await startServer(runDirectory);
    Object.assign(host, { artifactDirectory, checks: [], screenshots: [] });
    child = spawn(
      executable,
      [
        '--headless=new',
        '--disable-gpu',
        '--no-first-run',
        '--no-default-browser-check',
        '--disable-extensions',
        '--disable-background-networking',
        '--disable-component-update',
        '--disable-default-apps',
        '--disable-sync',
        '--disable-breakpad',
        '--disable-crash-reporter',
        '--metrics-recording-only',
        '--safebrowsing-disable-auto-update',
        '--disable-features=Translate,MediaRouter,OptimizationHints,AutofillServerCommunication',
        '--remote-debugging-address=127.0.0.1',
        '--remote-debugging-port=0',
        '--user-data-dir=' + profile,
        'about:blank'
      ],
      { windowsHide: true, detached: process.platform !== 'win32', stdio: ['ignore', 'ignore', 'pipe'] }
    );
    exited = new Promise((resolve) => {
      child.once('exit', resolve);
      child.once('error', (error) => {
        launchError = error;
        resolve();
      });
    });
    child.stderr.on('data', (chunk) => {
      stderr = (stderr + chunk.toString()).slice(-6000);
    });
    endpoint = await debuggerEndpoint(profile, child, () => launchError, controller.signal);
    const response = await fetch(endpoint.origin + '/json/list', { signal: AbortSignal.any([controller.signal, AbortSignal.timeout(5000)]) });
    const targets = await response.json();
    const target = targets.find((item) => item.type === 'page' && item.url === 'about:blank');
    assert.ok(target, '隔离浏览器未创建空白测试页面');
    cdp = await connectCdp(target.webSocketDebuggerUrl, failures, controller.signal);
    await cdp.guardPage(host.origin);
    browserInfo = await cdp.command('Browser.getVersion');
    await cdp.command('Browser.setDownloadBehavior', { behavior: 'deny' });
    if (process.env.REPORT_TEST_FAULT === 'node-rejection') void Promise.reject(Error('合成Node未处理拒绝'));
    if (process.env.REPORT_TEST_FAULT === 'interrupt') process.emit('SIGINT');
    if (process.env.REPORT_TEST_FAULT === 'cdp-disconnect') {
      cdp.close(false);
      await delay(100);
    }
    process.stdout.write('隔离浏览器：' + executable + '\nVite：' + host.origin + '；自有PID：' + child.pid + '\n');
    result = await runScenarios(cdp, host);
    assert.ok(result.passed > 0, '浏览器场景数必须大于零');
    assert.deepEqual(failures, []);
    assert.deepEqual(host.failures, []);
  } catch (error) {
    failure = String(error.stack || error);
    if (cdp) {
      try {
        const state = await cdp.command(
          'Runtime.evaluate',
          {
            expression: `({ active: document.activeElement?.outerHTML,
          viewport: { width: innerWidth, height: innerHeight }, scrollWidth: document.documentElement.scrollWidth,
          pageErrors: window.__reportBrowserFailures, text: document.body.textContent.slice(0, 6000) })`,
            returnByValue: true
          },
          3000
        );
        pageFailureState = state.result?.value;
        const image = await cdp.command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }, 3000);
        await writeFile(join(artifactDirectory, 'failure-synthetic.png'), Buffer.from(image.data, 'base64'));
      } catch {}
    }
    if (stderr && !cdp) console.error('浏览器启动诊断：' + stderr);
    if (failures.length || host?.failures.length) console.error('隔离防线诊断：' + JSON.stringify({ browser: failures, mock: host?.failures }));
    throw error;
  } finally {
    const cleanupErrors = [];
    let browserExitStrategy;
    try {
      browserExitStrategy = await stopBrowser(child, exited, cdp);
    } catch (error) {
      cleanupErrors.push(error.message);
    }
    cdp?.close();
    try {
      await host?.server.close();
    } catch (error) {
      cleanupErrors.push(error.message);
    }
    try {
      const contained = relative(await realpath(temporaryRoot), await realpath(runDirectory));
      assert.ok(contained && !contained.startsWith('..') && !isAbsolute(contained), '拒绝清理临时目录范围外的路径');
      await rm(runDirectory, { recursive: true, force: true, maxRetries: 10, retryDelay: 100 });
    } catch (error) {
      cleanupErrors.push(error.message);
    }
    const vitePort = host ? Number(new URL(host.origin).port) : undefined;
    const vitePortClosed = await portClosed(vitePort),
      cdpPortClosed = await portClosed(endpoint?.port);
    if (!vitePortClosed) cleanupErrors.push('Vite端口未释放：' + vitePort);
    if (!cdpPortClosed) cleanupErrors.push('CDP端口未释放：' + endpoint?.port);
    clearTimeout(deadline);
    process.off('SIGINT', onInterrupt);
    process.off('SIGTERM', onTerminate);
    process.off('uncaughtException', onFailure);
    process.off('unhandledRejection', onFailure);
    const cleanup = {
      browserPid: child?.pid,
      browserExitStrategy,
      browserExited: !child || !!launchError || child.exitCode !== null || child.signalCode !== null,
      cdp: cdp?.status,
      viteClosed: !host || !host.server.httpServer?.listening,
      profileRemoved: !(await stat(profile).catch(() => null)),
      temporaryDirectoryRemoved: !(await stat(runDirectory).catch(() => null)),
      ports: { vite: { port: vitePort, closed: vitePortClosed }, cdp: { port: endpoint?.port, closed: cdpPortClosed } },
      errors: cleanupErrors
    };
    const lateFailures = !failure && (failures.length > 0 || host?.failures.length > 0);
    if (lateFailures) failure = '清理前捕获迟到异常：' + JSON.stringify({ browser: failures, mock: host?.failures });
    await writeFile(
      join(artifactDirectory, 'verification.json'),
      JSON.stringify(
        {
          synthetic: true,
          suite: process.env.REPORT_TEST_SUITE || 'all',
          startedAt,
          completedAt: new Date().toISOString(),
          node: process.version,
          browser: { executable, product: browserInfo?.product, pid: child?.pid },
          viteOrigin: host?.origin,
          counts: { passed: host?.checks.length || 0, failed: failure || cleanupErrors.length ? 1 : 0, skipped: 0 },
          scenarios: host?.checks || [],
          screenshots: host?.screenshots || [],
          lifecycle: host?.lifecycle,
          mockRequests: host?.requests || [],
          failures,
          mockFailures: host?.failures || [],
          failure,
          pageFailureState,
          cleanup
        },
        null,
        2
      )
    );
    process.stdout.write('清理：' + JSON.stringify(cleanup) + '\n证据目录：' + artifactDirectory + '\n');
    assert.deepEqual(cleanupErrors, [], '隔离资源清理失败');
    if (lateFailures) throw Error(failure);
  }
  process.stdout.write('浏览器回归：' + JSON.stringify({ synthetic: true, ...result, failed: 0, skipped: 0 }) + '\n');
}

main().catch((error) => {
  console.error('报表浏览器回归失败：' + (error.stack || error));
  process.exitCode = 1;
});
