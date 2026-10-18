import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { constants } from 'node:fs';
import { access, mkdir, mkdtemp, readFile, realpath, rm, stat, writeFile } from 'node:fs/promises';
import { isIP } from 'node:net';
import { delimiter, isAbsolute, join, relative, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';
import { connectCdp } from '../report-browser/cdp.mjs';

const uiRoot = fileURLToPath(new URL('../../', import.meta.url));
const authentication =
  /auth(?:orization|entication)?|jwt|bearer|token|NotLogin|NotPermission|NotRole|密码|口令|凭证|登录|认证|鉴权|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/i;
const routeWords = new Set(
  'report definitions datasets providers sample catalogs engines versions published publish rollback state bootstrap acl runtime runs page layout exports file cancel retry templates package copy import lookups selected bindings queries catalog preview pivot drill assets metadata batches start items pdf dashboards render favorites favorite sources subscriptions executions schedule capabilities deliveries claim audit diff REPORT DATASET QUERY DASHBOARD SUBSCRIPTION ASSET PRINT_BATCH'.split(
    ' '
  )
);

/** 只接受本机回环站点，拒绝生产环境标识和携带路径、查询或凭证的地址。 */
export function liveOrigin(value = process.env.REPORT_LIVE_ORIGIN || 'http://127.0.0.1:18081') {
  assert.ok(
    ![process.env.NODE_ENV, process.env.REPORT_LIVE_ENV].some((item) => /^(prod|production)$/i.test(item || '')),
    '真实浏览器测试禁止生产环境'
  );
  let target;
  try {
    target = new URL(value);
  } catch {
    throw Error('REPORT_LIVE_ORIGIN必须是本机回环站点');
  }
  const host = target.hostname.replace(/^\[|\]$/g, '');
  const loopback = host === 'localhost' || host === '::1' || (isIP(host) === 4 && host.startsWith('127.'));
  assert.ok(loopback && ['http:', 'https:'].includes(target.protocol), 'REPORT_LIVE_ORIGIN仅允许本机回环HTTP站点');
  assert.ok(!target.username && !target.password && target.pathname === '/' && !target.search && !target.hash, '站点地址不得含凭证、路径或查询参数');
  return target.origin;
}

/** 不可信错误仅归类为固定中文摘要，认证及JWT异常不保留原文或堆栈。 */
export function safeSummary(value, fallback = '运行失败（详情已脱敏）') {
  const text = String(value?.message || value || '');
  if (/QUERY_SOURCE_DISABLED|已确认独立SELECT报表数据源未启用/.test(text)) return '已确认独立SELECT报表数据源未启用，本次未重复预览';
  if (/没有可重试的可恢复|缺少可恢复FAILED\/BLOCKED\/RETRY_WAIT/.test(text)) return '本次自身执行缺少可恢复FAILED/BLOCKED/RETRY_WAIT状态';
  if (/没有可取消的未完成|缺少已确认可取消的未完成/.test(text)) return '本次自身执行缺少已确认可取消的未完成状态';
  if (/缺少已取消且已创建子作业|缺少已取消的子作业/.test(text)) return '本次自身执行缺少已取消的子作业，跨入口检查未触发';
  if (/没有可恢复的失败或待入队/.test(text)) return '本次批次全部成功，没有可恢复的失败或待入队项';
  if (/调度和真实通知必须关闭|订阅调度或真实通知未满足关闭前提/.test(text)) return '订阅调度或真实通知未满足关闭前提';
  if (/真实单据未命中字典/.test(text)) return '当前真实单据未命中字典显示名称，缺少可验证的业务行';
  if (value?.code === 'ERR_ASSERTION' || value?.name === 'AssertionError') return '场景断言未通过（详情已脱敏）';
  if (authentication.test(text)) return '认证或鉴权异常（详情已脱敏）';
  if (/权限|授权|permission|forbidden/i.test(text)) return '权限校验失败（详情已脱敏）';
  if (/超时|timeout|timed.out|超过.*秒/i.test(text)) return '操作等待超时（详情已脱敏）';
  if (/运行.*中断|信号.*中断|\babort(?:ed|error)?\b/i.test(text)) return '运行已中断';
  if (/租户|tenant/i.test(text)) return '租户校验未通过（详情已脱敏）';
  if (/不存在|未找到|not.found/i.test(text)) return '目标资源不可用（详情已脱敏）';
  if (/遮挡|命中|hit.test/i.test(text)) return '点击中心被遮挡或目标已变化';
  if (/加载|loading|busy/i.test(text)) return '页面尚未完成加载';
  if (/禁用|disabled|不可用/i.test(text)) return '操作目标尚不可用';
  if (/assert|预期|应当|必须|不能为空|不匹配|检查|断言/i.test(text)) return '场景断言未通过（详情已脱敏）';
  if (/参数|无效|非法|校验|validation|invalid/i.test(text)) return '参数或状态校验未通过（详情已脱敏）';
  if (/配额|quota|并发/i.test(text)) return '执行配额受限';
  if (/网络|fetch|ERR_|连接|socket|ECONN/i.test(text)) return '本机服务连接异常（详情已脱敏）';
  if (/TypeError|ReferenceError|SyntaxError|页面脚本|浏览器异常/i.test(text)) return '页面脚本异常（详情已脱敏）';
  return fallback;
}

/** 步骤名称仅来自测试定义；额外去除环境凭据和可能混入的长标识。 */
export function safeLabel(value) {
  let text = String(value || '').replace(/[\r\n\t]/g, ' ');
  for (const secret of [process.env.REPORT_LIVE_USERNAME, process.env.REPORT_LIVE_PASSWORD]) {
    if (secret) text = text.split(secret).join('[已脱敏]');
  }
  if (authentication.test(text)) return '认证相关步骤（详情已脱敏）';
  return text
    .replace(/https?:\/\/\S+|[A-Za-z]:[\\/]\S+|[\w.+-]+@[\w.-]+\.[A-Za-z]+/g, '[已脱敏]')
    .replace(/\b(?!LIVE_RPT_)[A-Za-z0-9_+/=-]{32,}\b|\b\d{15,}\b/g, '[标识已脱敏]')
    .slice(0, 160);
}

/** 回环网络请求只保存路由模板，移除查询参数及任何动态路径标识。 */
function reportPath(url) {
  const path = new URL(url).pathname;
  const start = path.indexOf('/report/');
  if (start < 0) return null;
  return path
    .slice(start)
    .split('/')
    .map((part) => (!part || routeWords.has(part) ? part : ':id'))
    .join('/');
}

/** 接口消息仅保留固定安全中文，所有业务对象名称和异常原文均留在内存。 */
function safeMessage(value, code, status) {
  if (authentication.test(String(value || '')) || code === 401 || code === 403 || status === 401 || status === 403)
    return '认证或鉴权异常（详情已脱敏）';
  const message = String(value || '').trim();
  if (/^(操作成功|查询成功|请求成功|处理成功|保存成功|删除成功|操作失败|成功|失败)[！!。.]?$/.test(message)) return message;
  if ((code !== null && code !== 0 && code !== 200) || status >= 400) return safeSummary(message, '报表接口处理失败（详情已脱敏）');
  return message ? '接口消息已脱敏' : null;
}

/** 请求、步骤和异常采用有界账本，内部状态不进入最终证据。 */
export function createLedger({ signal, abort } = {}) {
  const ledger = {
    live: true,
    synthetic: false,
    resourcePrefix: 'LIVE_RPT_',
    requests: [],
    checks: [],
    exceptions: [],
    console: [],
    files: [],
    downloads: [],
    pendingRequests: new Set(),
    expectedBlockedRequests: new Set(),
    loginReady: { tenant: false, captcha: false },
    selfUserId: null,
    executionRows: new Map(),
    observedJobStates: new Map(),
    observedRuns: new Map(),
    exportRows: null,
    deliveryRows: null,
    captureDictionary: false,
    expectedLabels: new Set(),
    currencyValues: new Map(),
    layoutCurrencyValues: new Map(),
    dictionary: { observedValues: 0, rawPreserved: 0, previewMatches: 0, pdfMatches: 0 },
    signal,
    abort,
    safeSummary,
    safeLabel,
    noteError(error, source = 'runner') {
      if (ledger.exceptions.length < 200) ledger.exceptions.push({ source: safeLabel(source), message: safeSummary(error) });
    }
  };
  return ledger;
}

export function eligibleExecutionIndices(ledger, subscriptionId, recipient, operation) {
  assert.ok(['cancel', 'retry'].includes(operation), '执行操作必须是取消或重试');
  const rows = ledger.executionRows.get(subscriptionId);
  assert.ok(Array.isArray(rows), '必须先观察当前订阅的真实执行列表');
  const states = operation === 'retry' ? ['FAILED', 'BLOCKED', 'RETRY_WAIT'] : ['PENDING', 'QUEUED', 'PREPARING', 'RETRY_WAIT', 'BLOCKED'];
  return rows.flatMap((row, index) => {
    if (row.recipient !== recipient || !states.includes(row.state)) return [];
    if (row.job === null) return [index];
    const job = ledger.observedJobStates.get(row.job);
    if (!job) return [];
    const eligible = operation === 'retry' ? job.state === 'FAILED' && job.canRetry === true : ['QUEUED', 'RUNNING', 'FAILED'].includes(job.state);
    return eligible ? [index] : [];
  });
}

export function assertCancelledExecution(ledger, subscriptionId, executionId, recipient, requireExport = false) {
  const current = ledger.executionRows.get(subscriptionId)?.find((row) => row.id === executionId && row.recipient === recipient);
  assert.ok(current?.state === 'CANCELLED', '同一本次自身执行必须保持取消终态');
  if (requireExport) {
    assert.ok(current.job !== null && Array.isArray(ledger.exportRows), '取消子作业必须有真实普通导出响应');
    const job = ledger.exportRows.find((row) => row.id === current.job);
    assert.ok(!job || job.canRetry === false, '已取消子作业在普通导出中必须隐藏或不可重试');
    assert.ok(!job || !['QUEUED', 'RUNNING'].includes(job.state), '已取消子作业必须保持非执行状态');
  }
  return current;
}

/** 指定浏览器不静默回退，自动探测仅覆盖本机Edge与Chrome。 */
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
          ...(process.env.LOCALAPPDATA
            ? ['Microsoft/Edge/Application/msedge.exe', 'Google/Chrome/Application/chrome.exe'].map((name) => join(process.env.LOCALAPPDATA, name))
            : [])
        ]
      : process.platform === 'darwin'
        ? ['/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge', '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome']
        : (process.env.PATH || '')
            .split(delimiter)
            .filter(Boolean)
            .flatMap((directory) =>
              ['microsoft-edge', 'microsoft-edge-stable', 'google-chrome', 'google-chrome-stable'].map((name) => join(directory, name))
            );
  for (const candidate of candidates) {
    try {
      if (!(await stat(candidate)).isFile()) continue;
      await access(candidate, process.platform === 'win32' ? constants.F_OK : constants.X_OK);
      return candidate;
    } catch (error) {
      if (!['ENOENT', 'ENOTDIR', 'EACCES'].includes(error.code)) throw error;
    }
  }
  throw Error(specified ? 'REPORT_TEST_BROWSER指定的浏览器不可用' : '未找到Edge或Chrome，请设置REPORT_TEST_BROWSER');
}

/** 同时校验绝对路径和真实路径，递归清理只接受本次分配的自有目录。 */
async function ownedPath(workspace, target) {
  const absolute = resolve(target);
  assert.ok([workspace.runDirectory, workspace.artifactDirectory].includes(absolute), '拒绝清理非本次自有目录');
  for (const candidate of [absolute, await realpath(absolute)]) {
    const contained = relative(workspace.temporaryRoot, candidate);
    assert.ok(contained && !contained.startsWith('..') && !isAbsolute(contained), '拒绝清理临时目录范围外的路径');
  }
  return absolute;
}

/** 自有profile和下载目录相邻，长期证据使用独立且同样私有的目录。 */
export async function createWorkspace() {
  const temporary = resolve(uiRoot, 'node_modules/.tmp');
  await mkdir(temporary, { recursive: true });
  const temporaryRoot = await realpath(temporary);
  const runDirectory = await mkdtemp(join(temporaryRoot, 'report-live-run-'));
  const workspace = {
    temporaryRoot,
    runDirectory,
    artifactDirectory: runDirectory + '-evidence',
    profileDirectory: join(runDirectory, 'profile'),
    downloadDirectory: join(runDirectory, 'downloads')
  };
  await mkdir(workspace.artifactDirectory, { mode: 0o700 });
  await mkdir(workspace.profileDirectory, { mode: 0o700 });
  await mkdir(workspace.downloadDirectory, { mode: 0o700 });
  return workspace;
}

/** 动态CDP端口仅从当前新profile读取，启动及HTTP发现均有时间上限。 */
async function debuggerEndpoint(browser, signal) {
  const started = Date.now();
  while (Date.now() - started < 20000) {
    signal?.throwIfAborted();
    if (browser.launchError) throw Error('自有浏览器启动失败');
    if (browser.child.exitCode !== null || browser.child.signalCode !== null) throw Error('自有浏览器在CDP就绪前退出');
    try {
      const [value] = (await readFile(join(browser.workspace.profileDirectory, 'DevToolsActivePort'), 'utf8')).trim().split(/\r?\n/);
      const port = Number(value);
      if (Number.isInteger(port) && port > 0 && port < 65536) return 'http://127.0.0.1:' + port;
    } catch (error) {
      if (!['ENOENT', 'EBUSY', 'EACCES'].includes(error.code)) throw error;
    }
    await delay(60, undefined, { signal });
  }
  throw Error('自有浏览器CDP启动超时');
}

/** 观察真实网络和页面异常，不安装Mock、响应替换或组件内部调用。 */
async function observe(cdp, ledger, origin) {
  const requests = new Map();
  const captures = new Set();
  cdp.on('Browser.downloadWillBegin', ({ guid, suggestedFilename }) => {
    if (typeof guid !== 'string' || !guid || typeof suggestedFilename !== 'string' || !suggestedFilename || ledger.downloads.length >= 1000) {
      ledger.noteError('浏览器下载事件缺少标识或超出上限', 'download');
      ledger.abort?.(Error('浏览器下载事件无效'));
      return;
    }
    if (!ledger.downloads.some((download) => download.guid === guid))
      ledger.downloads.push({ guid, filename: suggestedFilename, state: 'inProgress', receivedBytes: 0 });
  });
  cdp.on('Browser.downloadProgress', ({ guid, state, receivedBytes, filePath }) => {
    const download = ledger.downloads.find((record) => record.guid === guid);
    if (!download) return;
    if (['inProgress', 'completed', 'canceled'].includes(state)) download.state = state;
    download.receivedBytes = receivedBytes;
    if (typeof filePath === 'string') download.filePath = filePath;
  });
  cdp.on('Network.requestWillBeSent', ({ requestId, request, type }) => {
    const url = new URL(request.url);
    if (!['http:', 'https:'].includes(url.protocol)) return;
    if (url.pathname.includes('__report_mock')) {
      ledger.noteError('禁止加载Mock');
      ledger.abort?.(Error('禁止加载Mock'));
      return;
    }
    if (url.origin !== origin || !['Fetch', 'XHR'].includes(type)) return;
    const path = reportPath(request.url);
    if (ledger.requests.length >= 2000) {
      ledger.noteError('请求记录超过有界上限');
      ledger.abort?.(Error('请求记录超过有界上限'));
      return;
    }
    const record = path
      ? {
          path,
          method: /^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$/.test(request.method) ? request.method : 'OTHER',
          status: null,
          code: null,
          errorCode: null,
          msg: null
        }
      : null;
    if (record) ledger.requests.push(record);
    const login = /\/auth\/tenant\/list$/.test(url.pathname) ? 'tenant' : /\/auth\/code$/.test(url.pathname) ? 'captcha' : null;
    requests.set(requestId, {
      record,
      login,
      userInfo: /\/system\/user\/getInfo$/.test(url.pathname),
      executionsFor: url.pathname.match(/\/report\/subscriptions\/([0-9]+)\/executions$/)?.[1] || null,
      runtimeRunFor: url.pathname.match(/\/report\/runtime\/runs\/([0-9]+)$/)?.[1] || null,
      status: null,
      json: false
    });
    ledger.pendingRequests.add(requestId);
  });
  cdp.on('Network.responseReceived', ({ requestId, response }) => {
    const pending = requests.get(requestId);
    if (!pending) return;
    pending.status = response.status;
    pending.json = /(?:application|text)\/(?:[\w.+-]*\+)?json/i.test(response.mimeType || '');
    if (pending.record) pending.record.status = response.status;
    if (response.mimeType === 'text/event-stream') {
      ledger.pendingRequests.delete(requestId);
      requests.delete(requestId);
    }
  });
  cdp.on('Network.loadingFinished', ({ requestId, encodedDataLength }) => {
    const pending = requests.get(requestId);
    if (!pending) return;
    requests.delete(requestId);
    if (pending.login && pending.status >= 200 && pending.status < 300) ledger.loginReady[pending.login] = true;
    if ((!pending.record && !pending.userInfo) || !pending.json || encodedDataLength > 1024 * 1024) {
      if (pending.record && pending.json) pending.record.msg = '响应超过摘要采集上限';
      ledger.pendingRequests.delete(requestId);
      return;
    }
    const capture = (async () => {
      try {
        const response = await cdp.command('Network.getResponseBody', { requestId }, 12000);
        const text = response.base64Encoded ? Buffer.from(response.body, 'base64').toString('utf8') : response.body;
        if (text.length > 1024 * 1024) {
          if (pending.record) pending.record.msg = '响应超过摘要采集上限';
          return;
        }
        const envelope = JSON.parse(text);
        if (pending.userInfo) {
          const userId = envelope?.code === 200 ? envelope?.data?.user?.userId : null;
          if (typeof userId === 'string' && /^[0-9]+$/.test(userId)) ledger.selfUserId = userId;
          else if (Number.isSafeInteger(userId) && userId > 0) ledger.selfUserId = String(userId);
          return;
        }
        pending.record.code = Number.isInteger(envelope?.code) && Math.abs(envelope.code) < 100000 ? envelope.code : null;
        pending.record.errorCode =
          typeof envelope?.errorCode === 'string' && /^[A-Z][A-Z0-9_]{0,95}$/.test(envelope.errorCode) ? envelope.errorCode : null;
        pending.record.msg = safeMessage(envelope?.msg, pending.record.code, pending.record.status);
        if (pending.record.method === 'GET' && pending.status === 200 && pending.record.code === 200) {
          if (pending.runtimeRunFor) {
            const run = envelope.data;
            assert.ok(
              run?.id === pending.runtimeRunFor && typeof run.reportId === 'string' && /^[0-9]+$/.test(run.reportId) && typeof run.state === 'string',
              '原运行响应必须保留完整运行和模板ID及状态'
            );
            ledger.observedRuns.set(run.id, { id: run.id, reportId: run.reportId, state: run.state });
          }
          if (pending.executionsFor) {
            assert.ok(Array.isArray(envelope.data) && envelope.data.length <= 200, '执行列表必须是有界数组');
            const rows = envelope.data.map((row) => {
              assert.ok(
                typeof row.id === 'string' &&
                  /^[0-9]+$/.test(row.id) &&
                  typeof row.recipient === 'string' &&
                  /^[0-9]+$/.test(row.recipient) &&
                  (row.job === null || (typeof row.job === 'string' && /^[0-9]+$/.test(row.job))) &&
                  typeof row.state === 'string',
                '执行列表必须保留完整字符串标识和状态'
              );
              return { id: row.id, recipient: row.recipient, job: row.job, state: row.state };
            });
            ledger.executionRows.set(pending.executionsFor, rows);
          }
          if (pending.record.path === '/report/runtime/exports' && Array.isArray(envelope.data)) {
            ledger.exportRows = envelope.data.map((job) => {
              assert.ok(
                typeof job.id === 'string' && /^[0-9]+$/.test(job.id) && typeof job.state === 'string' && typeof job.canRetry === 'boolean',
                '普通导出响应必须保留完整字符串标识、状态和重试资格'
              );
              return { id: job.id, state: job.state, canRetry: job.canRetry, runId: job.runId, format: job.format, bytes: job.bytes };
            });
            for (const job of ledger.exportRows) ledger.observedJobStates.set(job.id, { state: job.state, canRetry: job.canRetry });
          }
          if (pending.record.path === '/report/deliveries' && Array.isArray(envelope.data)) {
            ledger.deliveryRows = envelope.data.map((row) => {
              assert.ok(
                typeof row.id === 'string' &&
                  /^[0-9]+$/.test(row.id) &&
                  typeof row.subscriptionId === 'string' &&
                  /^[0-9]+$/.test(row.subscriptionId) &&
                  (row.jobId === null || (typeof row.jobId === 'string' && /^[0-9]+$/.test(row.jobId))) &&
                  typeof row.state === 'string',
                '交付响应必须保留完整字符串标识和状态'
              );
              return { id: row.id, subscriptionId: row.subscriptionId, jobId: row.jobId, state: row.state };
            });
            for (const delivery of ledger.deliveryRows) {
              if (delivery.jobId === null) continue;
              const state = ['READY', 'CLAIMED'].includes(delivery.state)
                ? 'SUCCEEDED'
                : ['FAILED', 'EXPIRED'].includes(delivery.state)
                  ? delivery.state
                  : 'UNKNOWN';
              ledger.observedJobStates.set(delivery.jobId, { state, canRetry: false });
            }
          }
        }
        if (ledger.captureDictionary && pending.record.code === 200 && pending.record.path.endsWith('/page')) {
          for (const row of Array.isArray(envelope?.data?.rows) ? envelope.data.rows : []) {
            const value = row?.currency;
            if (
              value?.type !== 'STRING' ||
              typeof value.value !== 'string' ||
              typeof value.displayValue !== 'string' ||
              !value.displayValue ||
              value.value === value.displayValue
            )
              continue;
            if (ledger.expectedLabels.size >= 100 && !ledger.expectedLabels.has(value.displayValue)) continue;
            ledger.expectedLabels.add(value.displayValue);
            ledger.currencyValues.set(value.value, value.displayValue);
            ledger.dictionary.observedValues++;
          }
        }
        if (ledger.captureDictionary && pending.record.code === 200 && pending.record.path.endsWith('/layout')) {
          const plan = envelope?.data;
          for (const page of Array.isArray(plan?.pages) ? plan.pages : []) {
            for (const cell of Array.isArray(page?.cells) ? page.cells : []) {
              if (cell.band !== 'DETAIL' || plan.columns?.[cell.column]?.field !== 'currency') continue;
              const value = cell.value;
              if (value?.type === 'STRING' && typeof value.value === 'string') ledger.layoutCurrencyValues.set(value.value, value.displayValue);
            }
          }
        }
      } catch (error) {
        if (pending.record) pending.record.msg = '响应信封摘要采集失败（详情已脱敏）';
        ledger.noteError(error, 'response-envelope');
      } finally {
        ledger.pendingRequests.delete(requestId);
      }
    })();
    captures.add(capture);
    void capture.finally(() => captures.delete(capture));
  });
  cdp.on('Network.loadingFailed', ({ requestId, canceled }) => {
    const pending = requests.get(requestId);
    if (!pending) return;
    if (pending.record) {
      pending.record.msg = canceled ? '请求已取消' : '本机请求失败（详情已脱敏）';
      pending.record.errorCode = canceled ? 'REQUEST_CANCELED' : 'NETWORK_FAILED';
    }
    ledger.pendingRequests.delete(requestId);
    requests.delete(requestId);
    if (!canceled) ledger.noteError('本机服务网络请求失败', 'network');
  });
  cdp.on('Runtime.exceptionThrown', ({ exceptionDetails }) => {
    ledger.noteError(exceptionDetails.exception?.description || exceptionDetails.text, 'page-exception');
  });
  cdp.on('Runtime.consoleAPICalled', ({ type, args }) => {
    if (!['error', 'warning', 'assert'].includes(type)) return;
    const message = safeSummary(
      args.map((argument) => (typeof argument.value === 'string' ? argument.value : argument.className || '')).join(' '),
      '控制台异常（详情已脱敏）'
    );
    if (ledger.console.length < 200) ledger.console.push({ type, message });
    if (type === 'assert' || type === 'error') ledger.noteError(message, 'console');
  });
  cdp.on('Page.frameNavigated', ({ frame }) => {
    if (frame.parentId || frame.url === 'about:blank' || frame.url.startsWith('blob:' + origin + '/')) return;
    if (new URL(frame.url).origin !== origin) {
      ledger.noteError('页面离开已授权回环站点');
      ledger.abort?.(Error('页面离开已授权回环站点'));
    }
  });
  await cdp.command('Network.enable', { maxTotalBufferSize: 4 * 1024 * 1024, maxResourceBufferSize: 1024 * 1024, maxPostDataSize: 0 });
  await cdp.command('Page.enable');
  await cdp.command('Runtime.enable');
  return async () => {
    const settled = await Promise.allSettled([...captures]);
    for (const result of settled) if (result.status === 'rejected') ledger.noteError(result.reason, 'response-envelope');
  };
}

/** 立即返回自有进程句柄，启动失败也能由调用方统一清理。 */
export async function launchBrowser(workspace, ledger) {
  const executable = await findBrowser();
  const child = spawn(
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
      '--window-size=1600,1000',
      '--remote-debugging-address=127.0.0.1',
      '--remote-debugging-port=0',
      '--user-data-dir=' + workspace.profileDirectory,
      'about:blank'
    ],
    { windowsHide: true, detached: process.platform !== 'win32', stdio: 'ignore' }
  );
  const browser = { child, workspace, launchError: null, cdp: null, flush: async () => {} };
  browser.exited = new Promise((done) => {
    child.once('exit', done);
    child.once('error', () => {
      browser.launchError = true;
      ledger.noteError('自有浏览器启动失败', 'browser');
      done();
    });
  });
  return browser;
}

/** 只连接当前新profile的页面端点，复用已有CDP实现并为场景命令传播取消。 */
export async function connectBrowser(browser, ledger, origin) {
  const endpoint = await debuggerEndpoint(browser, ledger.signal);
  const response = await fetch(endpoint + '/json/list', {
    signal: AbortSignal.any([AbortSignal.timeout(5000), ...(ledger.signal ? [ledger.signal] : [])])
  });
  assert.ok(response.ok, '无法发现本次自有CDP页面');
  const targets = await response.json();
  const target = targets.find((candidate) => candidate.type === 'page' && candidate.url === 'about:blank');
  assert.ok(target, '本次自有浏览器没有空白页面');
  const socket = new URL(target.webSocketDebuggerUrl);
  assert.ok(socket.protocol === 'ws:' && socket.hostname === '127.0.0.1' && socket.port === new URL(endpoint).port, 'CDP端点必须属于本次自有浏览器');
  browser.cdp = await connectCdp(socket.href, { push: (message) => ledger.noteError(message, 'cdp') }, ledger.signal);
  browser.flush = await observe(browser.cdp, ledger, origin);
  await browser.cdp.command('Browser.setDownloadBehavior', {
    behavior: 'allow',
    downloadPath: browser.workspace.downloadDirectory,
    eventsEnabled: true
  });
  const command = (method, params, timeout) => {
    ledger.signal?.throwIfAborted();
    return browser.cdp.command(method, params, timeout);
  };
  return { command, evaluate: browser.cdp.evaluate, on: browser.cdp.on };
}

/** 所有退出等待均有界，返回进程是否确已退出。 */
async function exitedWithin(browser, timeout) {
  if (browser.launchError || browser.child.exitCode !== null || browser.child.signalCode !== null) return true;
  let timer;
  try {
    return await Promise.race([browser.exited.then(() => true), new Promise((done) => (timer = setTimeout(() => done(false), timeout)))]);
  } finally {
    clearTimeout(timer);
  }
}

/** 只关闭本次spawn的进程；Windows后备终止严格使用自有PID且隐藏窗口。 */
export async function stopBrowser(browser) {
  if (!browser) return { strategy: 'not-started', exited: true };
  browser.cdp?.prepareToClose();
  let closeRejected = false;
  if (browser.cdp) {
    try {
      await browser.cdp.command('Browser.close', {}, 2000);
    } catch {
      closeRejected = true;
    }
  }
  if (await exitedWithin(browser, 2500)) {
    browser.cdp?.close();
    return { strategy: closeRejected ? 'cdp-disconnected' : 'cdp', exited: true };
  }
  assert.ok(Number.isInteger(browser.child.pid) && browser.child.pid > 0, '自有浏览器PID不可用');
  if (process.platform === 'win32') {
    const killer = spawn(join(process.env.SystemRoot || 'C:/Windows', 'System32/taskkill.exe'), ['/PID', String(browser.child.pid), '/T', '/F'], {
      windowsHide: true,
      stdio: 'ignore'
    });
    await new Promise((done, reject) => {
      const timer = setTimeout(() => {
        killer.kill();
        reject(Error('终止自有浏览器进程树超时'));
      }, 5000);
      killer.once('error', (error) => {
        clearTimeout(timer);
        reject(error);
      });
      killer.once('exit', () => {
        clearTimeout(timer);
        done();
      });
    });
  } else {
    process.kill(-browser.child.pid, 'SIGTERM');
    if (!(await exitedWithin(browser, 2000))) process.kill(-browser.child.pid, 'SIGKILL');
  }
  const exited = await exitedWithin(browser, 5000);
  browser.cdp?.close();
  assert.ok(exited, '本次自有浏览器未退出');
  return { strategy: process.platform === 'win32' ? 'owned-pid-tree' : 'owned-process-group', exited };
}

/** 删除本次运行目录及全部真实下载，保留独立证据目录。 */
export async function removeWorkspace(workspace) {
  await rm(await ownedPath(workspace, workspace.runDirectory), { recursive: true, force: true, maxRetries: 8, retryDelay: 100 });
}

/** 最终只写固定结构的脱敏证据，不序列化场景返回的任意业务对象。 */
export async function writeVerification(workspace, ledger, details) {
  const artifactDirectory = await ownedPath(workspace, workspace.artifactDirectory);
  await rm(artifactDirectory, { recursive: true, force: true });
  await mkdir(artifactDirectory, { mode: 0o700 });
  const checks = ledger.checks.map((check) => ({
    name: safeLabel(check.name),
    status: ['PASS', 'FAIL', 'blocked'].includes(check.status) ? check.status : 'FAIL',
    ...(check.reason ? { reason: safeSummary(check.reason, '前置条件或检查未满足（详情已脱敏）') } : {}),
    durationMs: Number.isFinite(check.durationMs) ? check.durationMs : 0
  }));
  const verification = {
    live: true,
    synthetic: false,
    origin: details.origin,
    suite: safeLabel(details.suite || 'all'),
    extended: ledger.extended === true,
    resourcePrefix: /^LIVE_RPT_[A-Za-z0-9_]{0,80}$/.test(ledger.resourcePrefix) ? ledger.resourcePrefix : 'LIVE_RPT_',
    startedAt: details.startedAt,
    finishedAt: new Date().toISOString(),
    status: details.failed ? 'FAIL' : checks.some((check) => check.status === 'blocked') ? 'blocked' : 'PASS',
    counts: {
      total: checks.length,
      passed: checks.filter((check) => check.status === 'PASS').length,
      failed: checks.filter((check) => check.status === 'FAIL').length,
      blocked: checks.filter((check) => check.status === 'blocked').length
    },
    checks,
    requests: ledger.requests.map((record) => ({
      path: reportPath('http://localhost' + record.path),
      method: /^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$/.test(record.method) ? record.method : 'OTHER',
      status: Number.isFinite(record.status) ? record.status : null,
      code: Number.isInteger(record.code) ? record.code : null,
      errorCode: /^[A-Z][A-Z0-9_]{0,95}$/.test(record.errorCode || '') ? record.errorCode : null,
      msg: safeMessage(record.msg, record.code, record.status)
    })),
    console: ledger.console.map(({ type, message }) => ({ type, message: safeSummary(message, '控制台异常（详情已脱敏）') })),
    exceptions: ledger.exceptions.map(({ source, message }) => ({ source: safeLabel(source), message: safeSummary(message) })),
    files: ledger.files.map((file) => ({
      format: ['CSV', 'XLSX', 'PDF', 'PNG', 'ZIP'].includes(String(file.format).toUpperCase()) ? String(file.format).toUpperCase() : 'OTHER',
      bytes: Number.isFinite(file.bytes) ? file.bytes : 0,
      ...(Number.isInteger(file.pages) ? { pages: file.pages } : {})
    })),
    dictionary: Object.fromEntries(
      ['observedValues', 'rawPreserved', 'previewMatches', 'pdfMatches'].map((key) => [
        key,
        Number.isSafeInteger(ledger.dictionary[key]) && ledger.dictionary[key] >= 0 ? ledger.dictionary[key] : 0
      ])
    ),
    downloads: {
      started: ledger.downloads.length,
      completed: ledger.downloads.filter((download) => download.state === 'completed').length,
      canceled: ledger.downloads.filter((download) => download.state === 'canceled').length
    },
    cleanup: details.cleanup
  };
  const path = join(artifactDirectory, 'verification.json');
  await writeFile(path, JSON.stringify(verification, null, 2) + '\n', { encoding: 'utf8', mode: 0o600 });
  return path;
}
