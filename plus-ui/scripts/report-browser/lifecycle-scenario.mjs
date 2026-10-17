import assert from 'node:assert/strict';
import { fixture } from './api.mjs';

/** 同一真实模板经设计器发布后，再由业务单张及多选入口领取合成PDF。 */
export async function runLifecycleScenario(cdp, host, scenario, { click, button, rendered }) {
  const { command, evaluate, ready } = cdp;
  const business = host.business;

  /** PDF必须通过真实文件API成为有效Blob，并在关闭时回收地址。 */
  async function pdf() {
    await ready(
      'document.querySelector(".report-pdf-preview iframe")?.getAttribute("src")?.startsWith("blob:") && window.__reportBlobUrls.size === 1'
    );
    const receipt = await evaluate(`(async () => {
      const url = document.querySelector('.report-pdf-preview iframe').src;
      const blob = window.__reportBlobs.get(url);
      if (!(blob instanceof Blob)) throw Error('实际PDF iframe未绑定当前组件创建的Blob');
      return { type: blob.type, bytes: blob.size, signature: await blob.slice(0, 8).text() };
    })()`);
    assert.equal(receipt.type, 'application/pdf');
    assert.equal(receipt.signature, '%PDF-1.4');
    assert.ok(receipt.bytes > 100);
    return receipt;
  }

  /** 保留业务抽屉，只关闭上层真实PDF弹窗。 */
  async function closePdf() {
    await evaluate(`Array.from(document.querySelectorAll('.el-dialog__footer button')).find(element =>
      element.getClientRects().length && element.textContent.trim() === '关闭').dataset.lifecycleClose = 'pdf'`);
    await click('[data-lifecycle-close="pdf"]');
    await ready(
      '!Array.from(document.querySelectorAll(".el-dialog")).some(element => element.getClientRects().length) && window.__reportBlobUrls.size === 0 && window.__reportBlobs.size === 0'
    );
  }

  /** 关闭抽屉后允许当前列表继续进行下一项业务操作。 */
  async function closeDrawer() {
    await button('关闭');
    await ready('!Array.from(document.querySelectorAll(".el-drawer")).some(element => element.getClientRects().length)');
  }

  await scenario('同模板闭环：真实编辑保存发布、新版本目录、单张和多选合并成功PDF Blob', async () => {
    await command('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false });
    await command('Page.navigate', { url: host.origin });
    await ready('window.__reportHarness');
    await evaluate('window.__reportHarness.startDesigner([...window.__reportHarness.fixture.permissions, "report:definition:publish"])');
    await ready('document.querySelector(".designer-config input[placeholder=报表标题]")');
    const previous = structuredClone(business.versions[0]);
    const revision = business.definition.revision;
    const firstRequest = host.requests.length;
    const title = '合成采购单模板 · 完整链路发布';
    await click('.designer-config input[placeholder="报表标题"]');
    for (const type of ['keyDown', 'keyUp'])
      await command('Input.dispatchKeyEvent', { type, key: 'a', code: 'KeyA', modifiers: 2, windowsVirtualKeyCode: 65 });
    await command('Input.insertText', { text: title });
    await ready('document.querySelector(".report-designer").textContent.includes("未保存")');
    await button('保存并发布');
    await ready(
      `document.querySelector('.report-designer').textContent.includes('已保存 · 修订 ${revision + 2}') && !document.querySelector('.designer-toolbar .is-loading')`
    );
    const writes = host.requests.slice(firstRequest).filter((entry) => entry.method === 'PUT' || entry.path.endsWith('/publish'));
    assert.equal(writes.length, 2, '保存并发布只能保存一次并发布一次');
    assert.equal(writes[0].path, '/__report_mock/report/definitions/' + fixture.reportId);
    assert.equal(writes[0].method, 'PUT');
    assert.deepEqual(Object.keys(writes[0].body).sort(), ['dsl', 'revision']);
    assert.equal(writes[0].body.revision, revision);
    assert.equal(writes[0].body.dsl.title, title);
    assert.equal(writes[0].body.dsl.code, fixture.reportCode);
    assert.equal(writes[0].body.dsl.datasetVersionId, fixture.datasetVersionId);
    assert.equal(writes[1].path, '/__report_mock/report/definitions/' + fixture.reportId + '/publish');
    assert.equal(writes[1].method, 'POST');
    assert.deepEqual(writes[1].body, { revision: revision + 1 });
    assert.ok(writes.every((entry) => entry.status === 200));
    assert.equal(business.versions.length, 2);
    assert.deepEqual(business.versions[0], previous, '新草稿及发布不能改写原不可变版本');
    const version = business.versions.at(-1);
    assert.equal(version.id, fixture.nextReportVersionId);
    assert.notEqual(version.id, fixture.reportVersionId);
    assert.equal(version.reportId, fixture.reportId);
    assert.equal(JSON.parse(version.json).title, title);
    assert.equal(business.definition.publishedVersion, version.id);
    await rendered('lifecycle-designer-published');

    await evaluate('window.__reportHarness.stopDesigner()');
    await evaluate('window.__reportHarness.startBusiness()');
    const navigation = await evaluate('window.__reportNavigation');
    const ids = await evaluate('window.__reportHarness.documents.map(document => document.id)');
    for (const index of [1, 2]) await click(`[data-test="business-selection"] .el-checkbox:nth-child(${index})`);
    assert.deepEqual(await evaluate('window.__reportHarness.businessIds'), []);
    business.faults.pdfError = null;
    business.faults.pdfStatus = 200;
    const runs = business.runs.length;
    const jobs = business.jobs.length;
    await button('预览/打印');
    await ready(
      `Array.from(document.querySelectorAll('.el-dropdown-menu__item')).some(element => element.getClientRects().length && element.textContent.trim() === ${JSON.stringify(title)})`
    );
    await click('.el-dropdown-menu__item');
    await ready(`document.querySelector('.report-paper-view .paper-cell')?.textContent.includes(${JSON.stringify(title)})`);
    assert.equal(business.runs.length, runs + 1);
    assert.equal(business.runs.at(-1).reportId, fixture.reportId);
    assert.equal(business.runs.at(-1).reportVersionId, version.id);
    assert.equal(business.runs.at(-1).parameters.orderId, ids[0]);
    await button('打印PDF');
    const singlePdf = await pdf();
    assert.equal(business.runs.length, runs + 1, '打印必须复用预览固定run');
    assert.equal(business.jobs.length, jobs + 1);
    assert.equal(business.jobs.at(-1).reportVersionId, version.id);
    const singleFile = host.requests.findLast((entry) => entry.path.endsWith('/file') && entry.pdf);
    assert.deepEqual(singleFile.pdf.versionIds, [version.id]);
    await rendered('lifecycle-single-pdf');
    await closePdf();
    await closeDrawer();

    for (const index of [1, 2]) await click(`[data-test="business-selection"] .el-checkbox:nth-child(${index})`);
    assert.deepEqual(await evaluate('window.__reportHarness.businessIds'), ids);
    const batches = business.batches.length;
    await click('[aria-label="打开业务打印批次"]');
    await ready('Array.from(document.querySelectorAll("button")).some(element => element.textContent.trim() === "确认并入队" && !element.disabled)');
    assert.ok(
      host.requests
        .filter((entry) => entry.path === '/__report_mock/report/runtime/templates')
        .slice(-2)
        .every((entry) => entry.publishedVersionId === version.id)
    );
    await button('确认并入队');
    await ready('document.querySelector(".batch-result-panel")?.textContent.includes("合并PDF预览/下载")');
    assert.equal(business.batches.length, batches + 1);
    const batch = business.batches.at(-1);
    assert.deepEqual(
      batch.documents,
      ids.map((orderId) => ({ reportVersionId: version.id, parameters: { orderId } }))
    );
    assert.ok(batch.items.length === 2 && batch.items.every((item) => item.reportVersionId === version.id && item.state === 'SUCCEEDED'));
    await button('合并PDF预览/下载');
    const batchPdf = await pdf();
    const mergedFile = host.requests.findLast((entry) => entry.path === '/__report_mock/report/batches/' + batch.id + '/pdf');
    assert.equal(mergedFile.status, 200);
    assert.deepEqual(mergedFile.pdf.versionIds, [version.id, version.id]);
    await rendered('lifecycle-batch-merged-pdf');
    await closePdf();
    await closeDrawer();
    assert.deepEqual(await evaluate('window.__reportNavigation'), navigation);
    assert.equal(await evaluate('window.__reportBlobUrls.size'), 0);
    host.lifecycle = {
      synthetic: true,
      reportId: fixture.reportId,
      previousVersionId: previous.id,
      versionId: version.id,
      saveCount: 1,
      publishCount: 1,
      saveRevision: writes[0].body.revision,
      publishRevision: writes[1].body.revision,
      finalRevision: business.definition.revision,
      runId: business.runs.at(-1).id,
      batchId: batch.id,
      documents: batch.documents,
      singlePdf,
      batchPdf,
      blobUrlsAfterClose: 0
    };
  });
}
