import assert from 'node:assert/strict';
import { fixture } from './api.mjs';

/** 通过真实业务入口完成单张、确认顺序、固定提交恢复及Blob错误回归。 */
export async function runBusinessScenarios(cdp, host, scenario, click, rendered) {
  const { evaluate, ready, command } = cdp;
  const business = host.business;
  await evaluate('window.__reportHarness.startBusiness()');
  const navigation = await evaluate('window.__reportNavigation');
  const filter = await evaluate('window.__reportHarness.businessFilter');
  const selected = await evaluate('window.__reportHarness.businessIds');

  /** 按可见内容定位按钮或菜单项，避免误点已关闭抽屉的残留DOM。 */
  async function press(text, selector = 'button') {
    await ready(`Array.from(document.querySelectorAll(${JSON.stringify(selector)})).some(element =>
      element.textContent.trim() === ${JSON.stringify(text)} && element.getClientRects().length && !element.disabled)`);
    await evaluate(`(() => {
      const element = Array.from(document.querySelectorAll(${JSON.stringify(selector)})).find(element =>
        element.textContent.trim() === ${JSON.stringify(text)} && element.getClientRects().length && !element.disabled);
      element.dataset.businessTarget = 'current';
    })()`);
    try {
      await click('[data-business-target="current"]');
    } finally {
      await evaluate('document.querySelector("[data-business-target]")?.removeAttribute("data-business-target")');
    }
  }

  /** 业务入口每次打开通过当前目录选择模板，不直接操作内部ref。 */
  async function single() {
    await press('预览/打印');
    await press('合成采购单模板', '.el-dropdown-menu__item');
    await ready('document.querySelector(".report-paper-view .paper-cell")?.textContent.includes("合成采购订单")');
  }

  /** 抽屉关闭后原列表筛选、勾选和路由必须保持。 */
  async function closeDrawer() {
    const currentSelection = await evaluate('window.__reportHarness.businessIds');
    await press('关闭', '.el-drawer__footer button');
    await ready('!Array.from(document.querySelectorAll(".el-drawer")).some(element => element.getClientRects().length)');
    assert.equal(await evaluate('window.__reportHarness.businessFilter'), filter);
    assert.deepEqual(await evaluate('window.__reportHarness.businessIds'), currentSelection);
    assert.deepEqual(await evaluate('window.__reportNavigation'), navigation);
  }

  /** PDF错误只关闭文件弹窗，不能误关其后方的业务抽屉。 */
  async function closePdf() {
    await press('关闭', '.el-dialog__footer button');
    await ready('!Array.from(document.querySelectorAll(".el-dialog")).some(element => element.getClientRects().length)');
    assert.equal(await evaluate('window.__reportBlobUrls.size'), 0);
  }

  /** 当前工具栏的批次提交由真实BusinessReportButton实例持有。 */
  async function batch() {
    await click('[aria-label="打开业务打印批次"]');
    await ready('Array.from(document.querySelectorAll("button")).some(button => button.textContent.trim() === "确认并入队" && !button.disabled)');
  }

  /** 真实结果面板仅在已固定批次创建并入队之后展示。 */
  async function result() {
    await ready(
      'document.querySelector(".batch-result-panel .el-table__body tbody tr") && document.querySelector(".batch-result-panel").textContent.includes("合并PDF预览/下载")'
    );
    assert.deepEqual(await evaluate('window.__reportNavigation'), navigation);
  }

  await scenario('真实单张业务按钮：原页抽屉自动预览一次，完整ID与固定版本', async () => {
    const before = business.runs.length;
    await single();
    assert.equal(business.runs.length, before + 1);
    assert.equal(business.runs.at(-1).parameters.orderId, selected[0]);
    assert.equal(business.runs.at(-1).version, fixture.reportVersionId);
    assert.equal(business.runs.at(-1).mode, 'SNAPSHOT');
    assert.deepEqual(await evaluate('window.__reportNavigation'), navigation);
    const visual = await rendered('business-single-drawer');
    assert.equal(visual.drawers.length, 1, '单张截图应保留实际打开的业务抽屉');
    assert.equal(visual.drawers[0].headings.length, 2, '单张抽屉应显示查询条件与运行结果两个flex标题');
  });

  await scenario('单张打印：JSON错误Blob显示权限指引，不创建iframe或二次run', async () => {
    const runs = business.runs.length;
    const jobs = business.jobs.length;
    await press('打印PDF');
    await ready(
      'Array.from(document.querySelectorAll(".el-drawer__body,.report-pdf-preview")).some(element => element.getClientRects().length && element.textContent.includes("权限已变化"))'
    );
    assert.equal(business.jobs.length, jobs + 1);
    assert.equal(business.runs.length, runs);
    assert.equal(await evaluate('document.querySelectorAll(".report-pdf-preview iframe").length'), 0);
    assert.equal(await evaluate('window.__reportBlobUrls.size'), 0);
    assert.equal(await evaluate('document.querySelectorAll(".report-paper-view,.report-result-meta").length'), 0, '撤权后仍保留原报表结果');
    await rendered('single-json-blob-denied');
    await ready('!Array.from(document.querySelectorAll(".el-dialog")).some(element => element.getClientRects().length)');
    await closeDrawer();
  });

  await scenario('关闭单张抽屉停止原PDF任务轮询，并保留筛选勾选', async () => {
    business.faults.queued = true;
    await single();
    await press('打印PDF');
    await ready('Array.from(document.querySelectorAll(".el-drawer")).some(element => element.textContent.includes("QUEUED"))');
    await closeDrawer();
    const reads = host.requests.filter((entry) => entry.path === '/__report_mock/report/runtime/exports').length;
    await evaluate('new Promise(resolve => setTimeout(resolve, 2700))');
    assert.equal(host.requests.filter((entry) => entry.path === '/__report_mock/report/runtime/exports').length, reads);
    business.faults.queued = false;
  });

  await scenario('真实批次确认：展示单号往来单位与完整ID，上移改变提交顺序', async () => {
    await batch();
    const content = await evaluate(
      'Array.from(document.querySelectorAll(".el-drawer__body")).find(element => element.getClientRects().length).textContent'
    );
    assert.match(content, /SYN-PO-001/);
    assert.match(content, /合成供应商乙/);
    await press('上移');
    const rows = await evaluate('Array.from(document.querySelectorAll(".el-drawer .el-table__body tbody tr")).map(row => row.textContent)');
    assert.match(rows[0], /SYN-PO-002/);
    assert.match(rows[0], new RegExp(selected[1]));
    assert.match(rows[1], /SYN-PO-001/);
    assert.deepEqual(await evaluate('window.__reportHarness.businessIds'), selected);
    await rendered('batch-confirm-reordered');
  });

  await scenario('批次确认后原位结果：code合法、版本与参数顺序固定，不导航', async () => {
    const count = business.batches.length;
    await press('确认并入队');
    await result();
    assert.equal(business.batches.length, count + 1);
    const current = business.batches.at(-1);
    assert.match(current.code, /^[A-Z][A-Z0-9_]{0,63}$/);
    assert.deepEqual(
      current.documents.map((document) => document.parameters.orderId),
      [...selected].reverse()
    );
    assert.ok(current.documents.every((document) => document.reportVersionId === fixture.reportVersionId));
    assert.deepEqual(Object.keys(current.documents[0]).sort(), ['parameters', 'reportVersionId']);
    assert.equal(current.items.length, 2);
    await rendered('batch-result-in-place');
  });

  await scenario('批次关闭重开恢复同一batchId，不重复创建或start', async () => {
    const id = business.batches.at(-1).id;
    const writes = host.requests.filter((entry) => entry.method === 'POST' && entry.path.startsWith('/__report_mock/report/batches')).length;
    await closeDrawer();
    await click('[aria-label="打开业务打印批次"]');
    await result();
    assert.match(await evaluate('document.querySelector(".batch-result-panel").textContent'), new RegExp(id));
    assert.equal(host.requests.filter((entry) => entry.method === 'POST' && entry.path.startsWith('/__report_mock/report/batches')).length, writes);
  });

  await scenario('清空或超过50项勾选仍能继续已创建批次，原顺序与内容保持固定', async () => {
    const id = business.batches.at(-1).id;
    const writes = business.submissions.length;
    await closeDrawer();
    await click('[data-test="business-selection"] .el-checkbox:nth-child(1)');
    await click('[data-test="business-selection"] .el-checkbox:nth-child(2)');
    assert.deepEqual(await evaluate('window.__reportHarness.businessIds'), []);
    assert.equal(await evaluate('document.querySelector("[aria-label=打开业务打印批次]").disabled'), false);
    assert.equal(await evaluate('document.querySelector("[aria-label=打开业务打印批次]").textContent.trim()'), '继续原批次');
    await click('[aria-label="打开业务打印批次"]');
    await result();
    assert.match(await evaluate('document.querySelector(".batch-result-panel").textContent'), new RegExp(id));
    await closeDrawer();
    await evaluate('window.__reportHarness.setBusinessIds(Array.from({length: 51}, (value, index) => String(9223372036854775000n + BigInt(index))))');
    assert.equal(await evaluate('document.querySelector("[aria-label=打开业务打印批次]").disabled'), false);
    await click('[aria-label="打开业务打印批次"]');
    await result();
    assert.equal(business.submissions.length, writes);
    assert.equal(business.batches.at(-1).id, id);
    assert.deepEqual(
      business.batches.at(-1).documents.map((document) => document.parameters.orderId),
      [...selected].reverse()
    );
    await closeDrawer();
    await evaluate('window.__reportHarness.setBusinessIds(' + JSON.stringify(selected) + ')');
    await click('[aria-label="打开业务打印批次"]');
    await result();
  });

  await scenario('PDF缓存打开期间逐项刷新为BLOCKED，关闭预览回收URL并隐藏原单号标签', async () => {
    business.faults.pdfError = '';
    await press('合并PDF预览/下载');
    await ready('document.querySelector(".report-pdf-preview iframe")?.src.startsWith("blob:") && window.__reportBlobUrls.size === 1');
    const batch = business.batches.at(-1);
    const original = structuredClone(batch.items[0]);
    const fileReads = host.requests.filter((entry) => entry.path === '/__report_mock/report/batches/' + batch.id + '/pdf').length;
    await rendered('batch-private-pdf-before-block');
    Object.assign(batch.items[0], { state: 'BLOCKED', error: 'SCOPE_CHANGED', jobId: null, canRetry: false });
    /** 文件弹窗覆盖刷新按钮时，以真实按钮事件触发异步重鉴权，不修改组件状态。 */
    await evaluate(
      `Array.from(document.querySelectorAll('.batch-result-panel button')).find(button => button.textContent.trim() === '刷新结果').click()`
    );
    await ready('window.__reportBlobUrls.size === 0 && !document.querySelector(".report-pdf-preview iframe")');
    await ready('!Array.from(document.querySelectorAll(".el-dialog")).some(element => element.getClientRects().length)');
    const row = await evaluate('document.querySelector(".batch-result-panel .el-table__body tbody tr").textContent');
    assert.ok(!row.includes('SYN-PO-002') && !row.includes('合成供应商乙') && !row.includes(original.jobId), '撤权项仍显示原标签或任务标识：' + row);
    assert.equal(host.requests.filter((entry) => entry.path === '/__report_mock/report/batches/' + batch.id + '/pdf').length, fileReads);
    await rendered('batch-blocked-pdf-revoked');
    Object.assign(batch.items[0], original);
    await press('刷新结果');
    await result();
    assert.equal(await evaluate('window.__reportBlobUrls.size'), 0, '恢复访问不能自动重新打开旧文件');
    business.faults.pdfError = 'SCOPE_CHANGED';
  });

  await scenario('合并领取：200 JSON Blob和410错误Blob都显示指引，禁止送入PDF', async () => {
    const count = business.batches.length;
    await press('合并PDF预览/下载');
    await ready('document.querySelector(".report-pdf-preview .el-alert")?.textContent.includes("权限已变化")');
    assert.equal(await evaluate('document.querySelectorAll(".report-pdf-preview iframe").length'), 0);
    business.faults.pdfError = 'ARTIFACT_EXPIRED';
    business.faults.pdfStatus = 410;
    await press('重新读取文件');
    await ready('document.querySelector(".report-pdf-preview .el-alert")?.textContent.includes("已过期")');
    assert.equal(await evaluate('window.__reportBlobUrls.size'), 0);
    assert.equal(business.batches.length, count);
    await rendered('batch-expired-blob');
    await closePdf();
    business.faults.pdfError = 'SCOPE_CHANGED';
    business.faults.pdfStatus = 200;
  });

  await scenario('start失败关闭重开：只补原批次入队，不重复create', async () => {
    await press('新建其他批次');
    business.faults.startFailures = 1;
    const count = business.batches.length;
    const submissions = business.submissions.length;
    await press('确认并入队');
    await ready('Array.from(document.querySelectorAll("button")).some(button => button.textContent.trim() === "重试入队" && !button.disabled)');
    const id = business.batches.at(-1).id;
    await closeDrawer();
    await click('[aria-label="打开业务打印批次"]');
    await press('重试入队');
    await result();
    assert.equal(business.batches.length, count + 1);
    assert.equal(business.submissions.length, submissions + 1);
    assert.equal(business.batches.at(-1).id, id);
    assert.equal(host.requests.filter((entry) => entry.path === '/__report_mock/report/batches/' + id + '/start').length, 2);
  });

  await scenario('创建响应未知后恢复：复用同code、完整内容及原提交顺序', async () => {
    await press('新建其他批次');
    business.faults.createResponseLoss = 1;
    const count = business.batches.length;
    const submissions = business.submissions.length;
    await press('确认并入队');
    await ready('Array.from(document.querySelectorAll("button")).some(button => button.textContent.trim() === "重试原批次" && !button.disabled)');
    const original = business.submissions.at(-1);
    await closeDrawer();
    await click('[data-test="business-selection"] .el-checkbox:nth-child(1)');
    await click('[data-test="business-selection"] .el-checkbox:nth-child(2)');
    assert.deepEqual(await evaluate('window.__reportHarness.businessIds'), []);
    assert.equal(await evaluate('document.querySelector("[aria-label=打开业务打印批次]").disabled'), false);
    await click('[aria-label="打开业务打印批次"]');
    await press('重试原批次');
    await result();
    assert.equal(business.batches.length, count + 1);
    assert.equal(business.submissions.length, submissions + 2);
    assert.deepEqual(business.submissions.at(-1), original);
    assert.equal(business.batches.at(-1).code, original.code);
    await closeDrawer();
  });
}
