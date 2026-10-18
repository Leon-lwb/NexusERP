import assert from 'node:assert/strict';
import { mkdir, readFile, readdir, realpath, stat, writeFile } from 'node:fs/promises';
import { basename, isAbsolute, join, relative, resolve } from 'node:path';
import { getDocument, OPS } from 'pdfjs-dist/legacy/build/pdf.mjs';
import { assertCancelledExecution, eligibleExecutionIndices } from './browser.mjs';

/** 真实页面操作只创建独立测试资源，不读取业务行到运行记录。 */
export async function runScenarios({ cdp, actions, ledger, downloadDirectory, suite = 'all' }) {
  const { evaluate, command } = cdp;
  const { wait, click, press, fill, field, choose, menu, settled, step } = actions;
  const suffix = new Date().toISOString().replace(/\D/g, '').slice(0, 14);
  const prefix = 'LIVE_RPT_' + suffix;
  const title = 'LIVE_RPT 真实操作 ' + suffix;
  const extended = suite === 'all' && process.env.REPORT_LIVE_EXTENDED !== '0';
  ledger.extended = extended;
  let sourceTitle = ['extensions', 'finish'].includes(suite) ? '供应商基础台账 /' : title + ' /';
  let subscriptionId;
  let singlePdfText = '';
  let mergedDownloadGuid;
  ledger.resourcePrefix = prefix;
  const identity = await step('当前身份：从已有页面请求确认自身ID', async () => {
    if (typeof ledger.selfUserId !== 'string' || !/^[0-9]+$/.test(ledger.selfUserId))
      return { blocked: '已有页面请求未提供可确认的当前用户ID，停止创建和授权资源' };
  });
  if (identity.status !== 'PASS') return ledger;
  const selfUserId = ledger.selfUserId;
  if (suite === 'time') {
    const { runTimeScenario } = await import('./time-scenario.mjs');
    return runTimeScenario({ cdp, actions, ledger });
  }
  if (suite === 'pdf') {
    await menu('报表管理', '报表中心');
    await step('真实PDF失败诊断', async () => {
      await fill('input[placeholder="搜索报表名称或编码"]', 'BASE_ERP_VENDOR_CATALOG');
      await press('运行', '.center-results button');
      await wait('document.querySelector(".run-toolbar")');
      await press('固定快照', '.el-radio-button');
      await press('运行报表');
      await wait('document.querySelector(".viewer-results .el-table") && !document.querySelector(".viewer-results .el-loading-mask")');
      await press('创建导出');
      await press('PDF 打印文件', '.el-dropdown-menu__item');
      await settled();
      await menu('导出中心', '导出中心');
      await wait(
        'document.querySelector(".report-exports .el-table__row")?.textContent.includes("失败") || document.querySelector(".report-exports .el-table__row")?.textContent.includes("完成")',
        30000
      );
      return { blocked: '真实PDF诊断场景，只记录任务结束，非通过验收' };
    });
    return ledger;
  }
  if (suite === 'exports') {
    await menu('导出中心', '导出中心');
    await step('导出队列状态诊断', async () => {
      process.stdout.write(
        JSON.stringify(
          await evaluate(
            `(async()=>{const api=await import('/src/api/report/index.ts');return (await api.listExportJobs()).data.slice(0,5).map(job=>({format:job.format,state:job.state,error:job.error}));})()`
          )
        ) + '\n'
      );
      process.stdout.write(
        JSON.stringify(
          await evaluate(
            `Array.from(document.querySelectorAll('.report-exports .el-table__row')).slice(0,5).map(row => ({format:row.querySelectorAll('.cell')[1]?.textContent.trim(),state:row.querySelectorAll('.cell')[2]?.textContent.trim(),error:row.querySelectorAll('.cell')[5]?.textContent.trim(),progress:row.querySelectorAll('.cell')[6]?.textContent.trim()}))`
          )
        ) + '\n'
      );
    });
    return ledger;
  }
  const selectors = {
    center: '.center-results .el-table__row',
    dataset: '.report-dataset .report-page__content > .el-card .el-table__row'
  };

  /** 检查页面错误，不把接口失败或空结果提示当成成功。 */
  async function clean() {
    await settled();
    const failures = await evaluate(`Array.from(document.querySelectorAll('.report-page .el-alert--error,.el-message--error'))
      .filter(element => element.getClientRects().length).map(element => element.textContent.trim())`);
    if (failures.some((message) => message.includes('数据正在处理') || message.includes('重复提交'))) {
      process.stdout.write('失败类型：只读操作触发重复提交提示\n');
    }
    assert.deepEqual(failures, [], '页面出现错误提示');
  }

  /** 仅按元数据编码定位列表，不打印业务对象标签和行数据。 */
  async function rowButton(selector, text, button) {
    await evaluate(`(() => {
      const row = Array.from(document.querySelectorAll(${JSON.stringify(selector)})).find(element => element.textContent.includes(${JSON.stringify(text)}));
      const target = row && Array.from(row.querySelectorAll('button')).find(element => element.textContent.trim() === ${JSON.stringify(button)} && !element.disabled);
      if (!target) throw Error('列表操作尚不可用');
      document.querySelector('[data-live-row-action]')?.removeAttribute('data-live-row-action');
      target.dataset.liveRowAction = 'current';
      return true;
    })()`);
    await click('[data-live-row-action]');
  }

  /** 确认框只能在场景明确触发后点击。 */
  async function confirm() {
    await press('确定', '.el-message-box button');
    await wait('!Array.from(document.querySelectorAll(".el-message-box")).some(element => element.getClientRects().length)');
  }

  /** 文件必须经过实际DOM上传控件，不能直接调用业务接口。 */
  async function upload(selector, path) {
    await wait(`!!document.querySelector(${JSON.stringify(selector)})?.matches('input[type="file"]:enabled')`);
    const { root } = await command('DOM.getDocument');
    const { nodeId } = await command('DOM.querySelector', { nodeId: root.nodeId, selector });
    assert.ok(nodeId, '文件控件应存在');
    await command('DOM.setFileInputFiles', { nodeId, files: [path] });
  }

  async function beforeDownload() {
    const files = new Map(
      await Promise.all(
        (await readdir(downloadDirectory)).map(async (name) => {
          const metadata = await stat(join(downloadDirectory, name), { bigint: true });
          return [name, [metadata.mtimeNs, metadata.ctimeNs, metadata.size].join(':')];
        })
      )
    );
    return { cursor: ledger.downloads.length, files };
  }

  /** 等待本次浏览器下载完成，只保留签名、文件类型及尺寸。 */
  async function downloaded(before, extension) {
    assert.ok(Number.isInteger(before.cursor) && before.cursor >= 0 && before.files instanceof Map, '下载前必须记录事件游标及文件版本');
    const root = await realpath(downloadDirectory);
    const start = Date.now();
    while (Date.now() - start < 45000) {
      ledger.signal?.throwIfAborted();
      const current = ledger.downloads.slice(before.cursor).filter((download) => download.filename.endsWith(extension));
      assert.ok(!current.some((download) => download.state === 'canceled'), '本次真实浏览器下载不能被取消');
      const found = current.find((download) => download.state === 'completed');
      if (found) {
        assert.equal(basename(found.filename), found.filename, '下载文件名不能包含目录');
        try {
          const path = await realpath(resolve(found.filePath || join(root, found.filename)));
          const contained = relative(root, path);
          assert.ok(contained && !contained.startsWith('..') && !isAbsolute(contained), '下载文件必须位于本次自有目录');
          const metadata = await stat(path, { bigint: true });
          const revision = [metadata.mtimeNs, metadata.ctimeNs, metadata.size].join(':');
          if (before.files.get(basename(path)) !== revision) {
            const bytes = await readFile(path);
            assert.ok(bytes.length > 0, '下载文件不能为空');
            assert.equal(bytes.length, found.receivedBytes, '实际文件必须匹配本次完成事件的字节数');
            return { path, bytes, guid: found.guid };
          }
        } catch (error) {
          if (error.code !== 'ENOENT') throw error;
        }
      }
      await new Promise((done) => setTimeout(done, 120));
    }
    throw Error('真实文件下载未完成：' + extension);
  }

  /** 授权回归只操作本次资源，授权主体限定当前用户。 */
  async function grantCycle() {
    await wait(
      'Array.from(document.querySelectorAll(".el-dialog button")).some(element => element.textContent.trim() === "保存授权" && !element.disabled)'
    );
    await press('添加授权', '.el-dialog button');
    await choose('.el-dialog .el-table__row:last-child .el-select', '用户');
    await fill('.el-dialog .el-table__row:last-child input[placeholder*=主体]', selfUserId);
    await press('保存授权', '.el-dialog button');
    await wait('!Array.from(document.querySelectorAll(".el-dialog")).some(element => element.getClientRects().length)');
  }

  async function grantAndRemove(open) {
    await open();
    await grantCycle();
    await open();
    await wait(
      'Array.from(document.querySelectorAll(".el-dialog button")).some(element => element.textContent.trim() === "移除" && !element.disabled)'
    );
    await press('移除', '.el-dialog .el-table__row:last-child button');
    await press('保存授权', '.el-dialog button');
    await wait('!Array.from(document.querySelectorAll(".el-dialog")).some(element => element.getClientRects().length)');
    await clean();
  }

  await mkdir(downloadDirectory, { recursive: true });
  if (suite === 'readonly') {
    const ownedPrefix = async (run) => {
      assert.ok(/^[A-Za-z0-9]{6}$/.test(run), '只读补测必须指定已有证据运行');
      const evidence = JSON.parse(
        await readFile(new URL('../../node_modules/.tmp/report-live-run-' + run + '-evidence/verification.json', import.meta.url), 'utf8')
      );
      assert.ok(evidence.live && !evidence.synthetic && /^LIVE_RPT_\d{14}$/.test(evidence.resourcePrefix), '只读补测必须使用本线程既有真实资源');
      return evidence.resourcePrefix;
    };
    const cancelledPrefix = await ownedPrefix(process.env.REPORT_LIVE_CANCEL_RUN || 'KIEdMS');
    const stylePrefix = await ownedPrefix(process.env.REPORT_LIVE_STYLE_RUN || 'l9Gc7S');
    ledger.resourcePrefix = cancelledPrefix;
    let cancelled;
    const selectCancelled = async () => {
      await menu('报表订阅', '报表订阅');
      assert.ok(
        await evaluate(
          `Array.from(document.querySelectorAll('.report-subscription .el-table__row')).filter(row => row.textContent.includes(${JSON.stringify(cancelledPrefix + '_SUB')})).length === 1`
        ),
        '必须唯一定位本线程既有取消订阅'
      );
      await rowButton('.report-subscription .el-table__row', cancelledPrefix + '_SUB', '执行记录');
      await clean();
      const id = await evaluate(
        `Array.from(document.querySelectorAll('.report-subscription .report-section-heading')).find(element => element.querySelector('h2')?.textContent.trim() === '执行记录')?.querySelector('.report-secondary')?.textContent.trim()`
      );
      assert.ok(typeof id === 'string' && /^[0-9]+$/.test(id), '必须确认实际完整字符串订阅ID');
      const rows = ledger.executionRows.get(id);
      assert.ok(Array.isArray(rows) && rows.length === 1, '本次既有订阅必须只有原始的一条执行');
      assert.ok(rows[0].recipient === selfUserId, '既有执行必须属于实际当前自身接收人');
      assert.ok(rows[0].state === 'CANCELLED', '既有执行必须仍为取消终态');
      subscriptionId = id;
      return rows[0];
    };
    await step('只读取消记录：核对既有完整订阅、执行和自身接收人', async () => {
      cancelled = await selectCancelled();
      assert.ok(cancelled.job !== null, '既有取消执行必须关联历史子作业');
    });
    const cancelledButtons = async () => {
      assertCancelledExecution(ledger, subscriptionId, cancelled.id, selfUserId);
      const cursor = ledger.requests.length;
      for (const label of ['取消', '重试']) {
        const point = await evaluate(`(() => {
          const rows = document.querySelectorAll('.report-subscription .el-table')[1]?.querySelectorAll('.el-table__row');
          if (rows?.length !== 1) throw Error('取消执行DOM必须与真实响应一致');
          const row = rows[0];
          if (row.querySelector('.cell')?.textContent.trim() !== ${JSON.stringify(selfUserId)} || row.querySelector('.report-status')?.textContent.trim() !== '已取消') throw Error('页面必须匹配自身取消终态');
          const button = Array.from(row.querySelectorAll('button')).find(element => element.textContent.trim() === ${JSON.stringify(label)});
          if (!button?.disabled || !button.matches(':disabled')) throw Error('取消终态的两个按钮必须原生禁用');
          button.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' });
          const rect = button.getBoundingClientRect();
          if (!rect.width || !rect.height) throw Error('禁用按钮必须真实可见');
          return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
        })()`);
        await command('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point });
        await command('Input.dispatchMouseEvent', { type: 'mousePressed', ...point, button: 'left', clickCount: 1 });
        await command('Input.dispatchMouseEvent', { type: 'mouseReleased', ...point, button: 'left', clickCount: 1 });
      }
      await clean();
      assert.ok(!ledger.requests.slice(cursor).some((request) => request.method !== 'GET'), '取消终态点击两个按钮必须不发送变更请求');
    };
    await step('只读取消记录：原生禁用按钮及实际点击零POST', cancelledButtons);
    await step('只读取消记录：普通导出无下载或重试入口', async () => {
      await menu('导出中心', '导出中心');
      const previous = ledger.exportRows;
      await press('刷新任务', '.report-exports button');
      await clean();
      assert.ok(Array.isArray(ledger.exportRows) && ledger.exportRows !== previous, '必须取得新的普通导出响应');
      assertCancelledExecution(ledger, subscriptionId, cancelled.id, selfUserId, true);
      assert.ok(
        await evaluate(
          `Array.from(document.querySelectorAll('.report-exports .el-table__row')).filter(row => row.querySelector('.cell')?.textContent.trim() === ${JSON.stringify(cancelled.job)}).every(row => !Array.from(row.querySelectorAll('button')).some(button => ['下载', '重试原任务'].includes(button.textContent.trim()) && button.getClientRects().length && !button.disabled))`
        ),
        '历史取消子作业不得提供可用下载或重试入口'
      );
    });
    await step('只读取消记录：交付映射取消、隐藏任务及禁用领取', async () => {
      await menu('我的报表交付', '我的报表交付');
      const previous = ledger.deliveryRows;
      await press('刷新交付', '.report-delivery button');
      await clean();
      assert.ok(Array.isArray(ledger.deliveryRows) && ledger.deliveryRows !== previous, '必须取得新的真实交付响应');
      const rows = ledger.deliveryRows.filter((row) => row.subscriptionId === subscriptionId);
      assert.ok(rows.length === 1, '既有自身执行必须仍能定位唯一交付记录');
      assert.ok(rows[0].state === 'CANCELLED', '历史取消执行的交付必须映射为取消');
      assert.ok(rows[0].jobId === null, '历史取消交付必须隐藏底层子作业ID');
      assert.ok(
        await evaluate(
          `Array.from(document.querySelectorAll('.report-delivery .el-table__row')).filter(row => row.querySelector('.cell')?.textContent.trim() === ${JSON.stringify(subscriptionId)}).every(row => row.querySelector('.report-status')?.textContent.trim() === '已取消' && Array.from(row.querySelectorAll('button')).filter(button => button.textContent.trim() === '鉴权领取文件').every(button => button.disabled && button.matches(':disabled')))`
        ),
        '取消交付页面必须保持取消且领取按钮不可操作'
      );
    });
    await step('只读取消记录：跨入口刷新保持原执行和子作业终态', async () => {
      const previousId = subscriptionId;
      const current = await selectCancelled();
      assert.ok(
        subscriptionId === previousId && current.id === cancelled.id && current.job === cancelled.job,
        '跨入口刷新必须保持原完整订阅、执行和子作业ID'
      );
      await cancelledButtons();
    });
    let reportId, styleJob;
    await step('只读样式格式：定位既有模板并复用原成功运行', async () => {
      await menu('报表管理', '报表中心');
      await fill('input[placeholder="搜索报表名称或编码"]', stylePrefix + '_TABLE');
      assert.ok(
        await evaluate(
          `Array.from(document.querySelectorAll('.center-results .el-table__row')).filter(row => row.textContent.includes(${JSON.stringify(stylePrefix + '_TABLE')})).length === 1`
        ),
        '必须唯一定位本线程既有样式模板'
      );
      await rowButton('.center-results .el-table__row', stylePrefix + '_TABLE', '运行');
      await wait('!!document.querySelector(".run-toolbar")');
      reportId = await evaluate('new URL(location.href).searchParams.get("id")');
      assert.ok(typeof reportId === 'string' && /^[0-9]+$/.test(reportId), '必须保留既有模板的完整字符串ID');
      await menu('导出中心', '导出中心');
      await press('刷新任务', '.report-exports button');
      await clean();
      styleJob = ledger.exportRows.find((job) => job.format === 'XLSX_LAYOUT');
      assert.ok(styleJob?.state === 'SUCCEEDED', '既有样式任务必须已成功');
      assert.ok(typeof styleJob.runId === 'string' && /^[0-9]+$/.test(styleJob.runId), '既有样式任务必须提供完整原运行ID');
      const origin = await evaluate('location.origin');
      await command('Page.navigate', {
        url: origin + '/report/viewer?id=' + encodeURIComponent(reportId) + '&runId=' + encodeURIComponent(styleJob.runId)
      });
      await wait('!!document.querySelector(".viewer-results .el-table")', 30000);
      await clean();
      const original = ledger.observedRuns.get(styleJob.runId);
      assert.ok(original?.id === styleJob.runId, '界面必须实际读取原运行响应');
      assert.ok(original.reportId === reportId, '样式子作业必须属于本线程既有模板');
      assert.ok(original.state === 'SUCCEEDED', '原样式运行必须仍已完成');
    });
    await step('只读样式格式：实际领取原文件及本次下载完成证据', async () => {
      await menu('导出中心', '导出中心');
      await press('刷新任务', '.report-exports button');
      await clean();
      assert.ok(
        ledger.exportRows.some((job) => job.id === styleJob.id && job.format === 'XLSX_LAYOUT' && job.state === 'SUCCEEDED'),
        '必须仍领取同一原有成功样式任务'
      );
      const before = await beforeDownload();
      await rowButton('.report-exports .el-table__row', styleJob.id, '下载');
      const file = await downloaded(before, '.xlsx');
      assert.ok(file.bytes.subarray(0, 2).toString() === 'PK', '实际样式文件必须具有XLSX签名');
      if (styleJob.bytes !== null) assert.ok(BigInt(file.bytes.length) === BigInt(styleJob.bytes), '文件必须与原有样式任务尺寸一致');
      ledger.files.push({ format: 'XLSX', bytes: file.bytes.length });
      await clean();
    });
    await step('只读补测：报表请求全为GET且只领取一个既有文件', async () => {
      assert.ok(
        ledger.requests.every((request) => request.method === 'GET'),
        '有限复验不得创建资源、任务或发送取消、重试、领取POST'
      );
      assert.ok(ledger.downloads.length === 1 && ledger.downloads[0].state === 'completed', '有限复验必须仅完成一次既有样式下载');
    });
    await step('只读补测：合法失败重试缺少真实可恢复前提', async () => {
      assert.ok(eligibleExecutionIndices(ledger, subscriptionId, selfUserId, 'retry').length === 0, '取消终态不能作为合法失败恢复前提');
      return { blocked: '本次自身执行没有可重试的可恢复失败、阻止或等待重试状态' };
    });
    return ledger;
  }
  if (!['extensions', 'finish'].includes(suite)) {
    await menu('报表管理', '报表中心');
    await step('中心：刷新、搜索、清空及卡片/列表', async () => {
      await press('刷新', '.report-center button');
      await settled();
      await fill('input[placeholder="搜索报表名称或编码"]', prefix + '_NO_MATCH');
      await wait('document.querySelector(".center-results").textContent.includes("没有找到匹配")');
      await press('清空筛选');
      await press('卡片', '.el-radio-button');
      await wait('document.querySelector(".report-resource-card")');
      await press('列表', '.el-radio-button');
      await wait('document.querySelector(".center-results .el-table__row")');
      await clean();
    });
    await step('中心：目录创建、选择及恢复全部目录', async () => {
      await click('[aria-label="新建报表目录"]');
      await fill('.el-message-box input', prefix + '_CATALOG');
      await confirm();
      await wait(
        `Array.from(document.querySelectorAll('.catalog-button')).some(element => element.textContent.includes(${JSON.stringify(prefix + '_CATALOG')}))`
      );
      await evaluate(
        `Array.from(document.querySelectorAll('.catalog-button')).find(element => element.textContent.includes(${JSON.stringify(prefix + '_CATALOG')})).dataset.liveCatalog='current'`
      );
      await click('[data-live-catalog]');
      await click('.catalog-button');
      await clean();
    });
    await step('中心：基础报表初始化及操作审计', async () => {
      await press('创建基础报表');
      await wait('!document.querySelector(".report-center button.is-loading")');
      await press('操作审计');
      await wait('document.querySelector(".el-drawer.open .el-table")');
      await click('.el-drawer.open .el-drawer__close-btn');
      await clean();
    });

    if (suite === 'all') {
      await menu('数据集目录', '数据集目录');
      await step('数据集：provider目录及重复登记', async () => {
        await press('注册数据集');
        await wait('document.querySelector(".el-dialog .el-table__row")');
        await rowButton('.el-dialog .el-table__row', 'ERP_VENDOR_CATALOG', '注册');
        await settled();
        await click('.el-dialog__headerbtn');
        await clean();
      });
      for (const code of [
        'ERP_VENDOR_CATALOG',
        'ERP_CLIENT_CATALOG',
        'ERP_MATERIAL_CATALOG',
        'ERP_STOCK_BALANCE',
        'ERP_STOCK_MOVEMENT',
        'ERP_PURCHASE_EXECUTION',
        'ERP_PURCHASE_ORDER_LINES',
        'ERP_SALES_EXECUTION',
        'ERP_SALES_ORDER_LINES',
        'MES_WORK_ORDER_TRACKING',
        'MES_IQC_RECORDS'
      ]) {
        await step('数据集：' + code + '字段契约和真实样例', async () => {
          await rowButton(selectors.dataset, code, '字段与样例');
          await wait('document.querySelector(".el-drawer.open .el-tabs")');
          await press('数据样例', '.el-tabs__item');
          await press('查询样例', '.el-drawer.open button');
          await wait('!document.querySelector(".el-drawer.open button.is-loading")');
          await clean();
          await wait('document.querySelector(".el-drawer.open .el-tab-pane[id$=sample] .el-table")');
          await click('.el-drawer.open .el-drawer__close-btn');
        });
      }
    }

    await menu('报表管理', '报表中心');
    await step('中心：复制为独立草稿', async () => {
      await rowButton(selectors.center, '供应商基础台账', '更多');
      await press('复制为草稿', '.el-dropdown-menu__item');
      await fill('.el-message-box input', prefix + '_TABLE');
      await confirm();
      await wait('document.querySelector("input[placeholder=报表标题]")');
      await clean();
    });
    await step('设计器：编辑、撤销、重做和保存', async () => {
      await fill('input[placeholder=报表标题]', title);
      await press('撤销', '.designer-toolbar button');
      await press('重做', '.designer-toolbar button');
      await press('保存草稿', '.designer-toolbar button');
      await wait('document.querySelector(".report-designer .el-tag--success") && !document.querySelector(".designer-toolbar .is-loading")');
      await clean();
    });
    await step('设计器：校验、预览、刷新和关闭', async () => {
      await press('更多', '.designer-toolbar button');
      await press('校验定义', '.el-dropdown-menu__item');
      await settled();
      await press('数据预览', '.designer-toolbar button');
      await wait('document.querySelector(".designer-preview .el-table") || document.querySelector(".designer-preview .report-paper-view")');
      await press('刷新预览', '.designer-preview button');
      await wait('!document.querySelector(".designer-preview .is-loading")');
      await clean();
      await press('关闭预览');
      await press('专注编辑');
      await press('退出专注编辑');
    });
    let packagePath;
    await step('设计器：下载定义和模板包', async () => {
      const before = await beforeDownload();
      await press('更多', '.designer-toolbar button');
      await press('下载定义', '.el-dropdown-menu__item');
      const definition = await downloaded(before, '.json');
      assert.equal(JSON.parse(definition.bytes.toString()).code, prefix + '_TABLE');
      await press('更多', '.designer-toolbar button');
      const beforePackage = await beforeDownload();
      await press('导出模板包', '.el-dropdown-menu__item');
      const pack = await downloaded(beforePackage, '.reportpkg');
      packagePath = pack.path;
      assert.ok(JSON.parse(pack.bytes.toString()), '模板包必须是实际JSON');
      await clean();
    });
    await step('设计器：首次发布和新修订发布', async () => {
      await press('保存并发布');
      await wait('!document.querySelector(".designer-toolbar .is-loading")');
      await fill('input[placeholder=报表标题]', title + ' V2');
      await press('保存并发布');
      await wait('!document.querySelector(".designer-toolbar .is-loading")');
      await clean();
    });

    await menu('报表管理', '报表中心');
    await fill('input[placeholder="搜索报表名称或编码"]', prefix + '_TABLE');
    await step('历史：查看固定版本及回滚测试模板', async () => {
      await rowButton(selectors.center, prefix + '_TABLE', '更多');
      await press('发布历史', '.el-dropdown-menu__item');
      await wait('document.querySelectorAll(".report-history .el-table__row").length >= 2');
      await press('回滚至此版本', '.report-history button');
      await confirm();
      await clean();
      await press('返回报表中心');
      await wait('document.querySelector(".report-center")');
    });
    await step('中心：测试模板授权保存与移除', async () => {
      await rowButton(selectors.center, prefix + '_TABLE', '更多');
      await press('资源授权', '.el-dropdown-menu__item');
      await grantCycle();
      await rowButton(selectors.center, prefix + '_TABLE', '更多');
      await press('资源授权', '.el-dropdown-menu__item');
      await wait('document.querySelector("input[placeholder*=主体]")');
      await press('移除', '.el-dialog button');
      await press('保存授权', '.el-dialog button');
      await wait('!Array.from(document.querySelectorAll(".el-dialog")).some(element => element.getClientRects().length)');
      await clean();
    });
    await step('运行：实时分页、排序及固定快照', async () => {
      await rowButton(selectors.center, prefix + '_TABLE', '运行');
      await wait('document.querySelector(".viewer-parameters .run-toolbar")');
      await press('运行报表');
      await wait('document.querySelector(".viewer-results .el-table") && !document.querySelector(".viewer-results .el-loading-mask")');
      await clean();
      await click('.viewer-results .el-table th .caret-wrapper');
      await settled();
      await choose('.report-pagination .el-select', '50');
      await settled();
      if (await evaluate('!!document.querySelector(".report-pagination .btn-next:not(:disabled)")')) {
        await click('.report-pagination .btn-next');
        await settled();
        await click('.report-pagination .btn-prev');
        await settled();
      }
      await press('固定快照', '.el-radio-button');
      await press('运行报表');
      await wait('!document.querySelector(".run-toolbar .is-loading") && !document.querySelector(".viewer-results .el-loading-mask")');
      await clean();
    });
    for (const [index, format] of ['PDF 打印文件', '明细 Excel', '样式 Excel'].entries()) {
      await step('导出：创建并领取' + format, async () => {
        if (index) {
          await menu('报表管理', '报表中心');
          await fill('input[placeholder="搜索报表名称或编码"]', prefix + '_TABLE');
          await rowButton(selectors.center, prefix + '_TABLE', '运行');
          await wait('document.querySelector(".run-toolbar")');
          await press('固定快照', '.el-radio-button');
          await press('运行报表');
          await wait('document.querySelector(".viewer-results .el-table") && !document.querySelector(".viewer-results .el-loading-mask")');
        }
        await press('创建导出');
        await press(format, '.el-dropdown-menu__item');
        await wait('!document.querySelector(".viewer-export-actions .is-loading")');
        await clean();
        await menu('导出中心', '导出中心');
        await press('刷新任务');
        await wait('document.querySelector(".report-exports .el-table__row")?.textContent.includes("完成")', 90000);
        await evaluate('document.querySelector(".report-exports .el-table__row").dataset.liveDownload="current"');
        const before = await beforeDownload();
        await press('下载', '[data-live-download] button');
        const file = await downloaded(before, format.includes('PDF') ? '.pdf' : '.xlsx');
        let pages;
        if (format.includes('PDF')) {
          assert.equal(file.bytes.subarray(0, 5).toString(), '%PDF-');
          const pdf = await getDocument({ data: new Uint8Array(file.bytes), useSystemFonts: false }).promise;
          pages = pdf.numPages;
          assert.ok(pages > 0, 'PDF应能实际解析页面');
          await pdf.destroy();
        } else assert.equal(file.bytes.subarray(0, 2).toString(), 'PK');
        ledger.files ??= [];
        ledger.files.push({ format: format.includes('PDF') ? 'PDF' : 'XLSX', bytes: file.bytes.length, pages });
        await evaluate('document.querySelector("[data-live-download]").removeAttribute("data-live-download")');
        await clean();
      });
    }

    await menu('报表管理', '报表中心');
    await step('模板包：选择实际下载文件并导入为独立草稿', async () => {
      assert.ok(packagePath, '导出模板包必须先成功');
      await press('导入模板包');
      const dialog = await wait(`(() => {
        const dialogs = Array.from(document.querySelectorAll('.el-dialog')).filter(element =>
          element.getClientRects().length && element.querySelector('.el-dialog__title')?.textContent.trim() === '导入模板包'
        );
        if (dialogs.length !== 1) return null;
        dialogs[0].setAttribute('data-live-package-import', '');
        return '[data-live-package-import]';
      })()`);
      const fileInput = dialog + ' input[type="file"][aria-label="选择报表模板包"]';
      await upload(fileInput, packagePath);
      await wait(`document.querySelector(${JSON.stringify(fileInput)})?.files?.length === 1`);
      await choose(await field('目标契约', '.el-select'), '供应商基础台账');
      const importedCode = prefix + '_IMPORT';
      const importedTitle = title + ' 导入';
      await fill(await field('新编码'), importedCode);
      await fill(await field('名称'), importedTitle);
      const requestStart = ledger.requests.length;
      await press('导入草稿', dialog + ' button');
      const importedId = await wait(`(() => {
        const route = new URL(location.href);
        const id = route.searchParams.get('id');
        const designer = Array.from(document.querySelectorAll('.app-main .report-designer')).find(element => element.getClientRects().length);
        if (route.pathname !== '/report/designer' || !/^[0-9]+$/.test(id || '') || !designer) return null;
        if (designer.querySelector('input[placeholder="报表编码，如 STOCK_BALANCE"]')?.value !== ${JSON.stringify(importedCode)}) return null;
        if (designer.querySelector('input[placeholder="报表标题"]')?.value !== ${JSON.stringify(importedTitle)}) return null;
        if (!designer.querySelector('.report-page__header .el-tag--success')) return null;
        return id;
      })()`);
      assert.ok(typeof importedId === 'string' && /^[0-9]+$/.test(importedId), '导入后的路由必须保留完整字符串ID');
      await clean();
      const imports = ledger.requests
        .slice(requestStart)
        .filter((request) => request.path === '/report/templates/import' && request.method === 'POST');
      assert.equal(imports.length, 1, '导入必须经过一次真实页面请求');
      assert.equal(imports[0].status, 200, '导入请求必须HTTP成功');
      assert.equal(imports[0].code, 200, '导入请求必须业务成功');
    });
    await menu('报表管理', '报表中心');
    await fill('input[placeholder="搜索报表名称或编码"]', prefix + '_IMPORT');
    await step('中心：仅归档本次导入草稿', async () => {
      await rowButton(selectors.center, prefix + '_IMPORT', '更多');
      await press('归档报表', '.el-dropdown-menu__item');
      await confirm();
      await clean();
    });
    await fill('input[placeholder="搜索报表名称或编码"]', prefix + '_TABLE');
    await step('中心：测试模板下线后重新发布', async () => {
      await rowButton(selectors.center, prefix + '_TABLE', '更多');
      await press('下线报表', '.el-dropdown-menu__item');
      await confirm();
      await wait('document.querySelector(".center-results .el-table__row")?.textContent.includes("已下线")');
      await rowButton(selectors.center, prefix + '_TABLE', '更多');
      await press('发布草稿', '.el-dropdown-menu__item');
      await confirm();
      await wait('document.querySelector(".center-results .el-table__row")?.textContent.includes("已发布")');
      const ownTitle = await evaluate(
        `Array.from(document.querySelectorAll(${JSON.stringify(selectors.center)})).find(row => row.textContent.includes(${JSON.stringify(prefix + '_TABLE')}))?.querySelector('.center-report-name strong')?.textContent.trim()`
      );
      assert.ok(typeof ownTitle === 'string' && ownTitle.startsWith(title), '来源报告必须为本次列表模板');
      sourceTitle = ownTitle + ' /';
      await clean();
    });
  }
  await menu('受控查询', '受控查询');
  await step('受控查询：字段、筛选、保存和发布数据集', async () => {
    await press('新建查询');
    await fill(await field('编码'), prefix + '_QUERY');
    await fill(await field('标题'), title + ' 查询');
    await choose(await field('主实体', '.el-select'), '库存');
    await press('添加字段');
    await press('添加筛选');
    await press('删除', '.report-query .el-table:last-child button');
    await press('保存草稿');
    await wait(
      'Array.from(document.querySelectorAll(".report-query button")).some(element => element.textContent.trim() === "发布数据集" && !element.disabled)'
    );
    await press('发布数据集');
    await wait('document.querySelector(".report-query .el-table__row")?.textContent.includes("已发布")');
    await clean();
  });
  await step('受控查询：真实预览或明确只读源配置阻止', async () => {
    if (process.env.REPORT_LIVE_QUERY_SOURCE_DISABLED === '1')
      return { blocked: '此前已确认QUERY_SOURCE_DISABLED，本次按指示不重复预览，独立SELECT数据源仍未启用' };
    const start = ledger.requests.length;
    await press('样例预览');
    await wait(
      'Array.from(document.querySelectorAll(".el-message")).some(element => element.textContent.includes("独立SELECT")) || document.querySelectorAll(".report-query .el-table").length > 3'
    );
    const denied = ledger.requests.slice(start).find((request) => request.errorCode === 'QUERY_SOURCE_DISABLED');
    if (denied) {
      assert.ok(
        await evaluate(
          'Array.from(document.querySelectorAll(".el-message")).some(element => element.textContent.includes("请配置并启用独立SELECT报表数据源"))'
        )
      );
      await wait('!document.querySelector(".el-message--error")');
      return { blocked: '本地未启用独立SELECT报表数据源，未借用业务写连接' };
    }
    await clean();
  });

  if (extended) {
    await step('查询：本次发布状态和自身授权保存与移除', async () => {
      assert.equal(await evaluate(`document.querySelector('.report-query input:disabled')?.value`), prefix + '_QUERY', '查询操作必须限定本次资源');
      await wait(
        `Array.from(document.querySelectorAll('.report-query .el-table__row')).some(row => row.textContent.includes(${JSON.stringify(title + ' 查询')}) && row.querySelector('.report-status')?.textContent.trim() === '已发布')`
      );
      await grantAndRemove(() => press('资源授权', '.report-query button'));
      await press('刷新目录', '.report-query button');
      await clean();
    });
    await step('数据集：本次查询产物授权、停用和恢复启用', async () => {
      await menu('数据集目录', '数据集目录');
      const ownDataset = title + ' 查询';
      await wait(
        `Array.from(document.querySelectorAll(${JSON.stringify(selectors.dataset)})).some(row => row.textContent.includes(${JSON.stringify(ownDataset)}))`
      );
      await grantAndRemove(() => rowButton(selectors.dataset, ownDataset, '授权'));
      for (const [button, state] of [
        ['停用', '停用'],
        ['启用', '启用']
      ]) {
        const requestStart = ledger.requests.length;
        await rowButton(selectors.dataset, ownDataset, button);
        await confirm();
        await settled();
        const mutation = ledger.requests
          .slice(requestStart)
          .find((request) => request.path === '/report/datasets/:id/state' && request.method === 'PUT');
        assert.equal(mutation?.status, 200, '数据集状态操作必须HTTP成功');
        assert.equal(mutation?.code, 200, '数据集状态操作必须业务成功');
        await wait(
          `Array.from(document.querySelectorAll(${JSON.stringify(selectors.dataset)})).some(row => row.textContent.includes(${JSON.stringify(ownDataset)}) && row.querySelector('.el-tag')?.textContent.trim() === ${JSON.stringify(state)})`
        );
        await clean();
      }
      await rowButton(selectors.dataset, ownDataset, '字段与样例');
      await wait('document.querySelector(".el-drawer.open .el-tabs")');
      await press('字段契约', '.el-drawer.open .el-tabs__item');
      await wait('document.querySelector(".el-drawer.open .el-table__row")');
      await click('.el-drawer.open .el-drawer__close-btn');
    });
    await step('数据集：从本次发布契约创建自有报表草稿', async () => {
      await rowButton(selectors.dataset, title + ' 查询', '创建报表');
      await wait(
        `location.pathname === '/report/designer' && /^[0-9]+$/.test(new URL(location.href).searchParams.get('dataset') || '') && !!document.querySelector('.report-designer input[placeholder="报表标题"]')`
      );
      await fill('input[placeholder="报表编码，如 STOCK_BALANCE"]', prefix + '_QUERY_TABLE');
      await fill('input[placeholder=报表标题]', title + ' 查询草稿');
      await press('保存草稿', '.designer-toolbar button');
      await wait(
        `location.pathname === '/report/designer' && /^[0-9]+$/.test(new URL(location.href).searchParams.get('id') || '') && document.querySelector('.report-designer .report-page__header .el-tag--success') && !document.querySelector('.designer-toolbar .is-loading')`
      );
      await clean();
    });
  }

  await menu('交叉报表', '交叉报表设计');
  await step('交叉：新建、保存、发布、运行和同快照钻取', async () => {
    await fill('input[placeholder=报表编码]', prefix + '_PIVOT');
    await fill('input[placeholder=标题]', title + ' 交叉');
    await choose(await field('固定数据集', '.el-select'), '库存余额');
    await choose(await field('行维度', '.el-select'), '物料编码');
    await click('.report-pivot h1');
    await press('保存草稿');
    await wait(
      'Array.from(document.querySelectorAll(".report-pivot button")).some(element => element.textContent.trim() === "发布固定版本" && !element.disabled)'
    );
    await press('发布固定版本');
    await wait(
      'Array.from(document.querySelectorAll(".report-pivot button")).some(element => element.textContent.trim() === "运行" && !element.disabled)'
    );
    await press('运行', '.report-pivot button');
    await wait('document.querySelector(".run-toolbar")');
    await press('运行报表');
    await wait('document.querySelector(".pivot-table tbody button")');
    await click('.pivot-table tbody button');
    await wait(
      'Array.from(document.querySelectorAll(".el-dialog")).some(element => element.getClientRects().length && element.querySelector(".el-table"))'
    );
    await clean();
    await click('.el-dialog__headerbtn');
  });

  await menu('私有打印图片', '私有打印图片');
  await step('图片：PNG上传、授权、下线和恢复', async () => {
    const pngPath = join(downloadDirectory, 'live-synthetic-pixel.png');
    await writeFile(pngPath, Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64'));
    await fill('input[placeholder="输入唯一编码"]', prefix + '_IMAGE');
    await fill('input[placeholder="例如：公司标识"]', title + ' 图片');
    await upload('.report-assets input[type=file]', pngPath);
    await press('上传固定版本');
    await wait(
      `Array.from(document.querySelectorAll('.report-assets .el-table__row')).some(element => element.textContent.includes(${JSON.stringify(prefix + '_IMAGE')}))`
    );
    await rowButton('.report-assets .el-table__row', prefix + '_IMAGE', '资源授权');
    await grantCycle();
    await rowButton('.report-assets .el-table__row', prefix + '_IMAGE', '下线');
    await confirm();
    await wait(
      `Array.from(document.querySelectorAll('.report-assets .el-table__row')).some(element => element.textContent.includes(${JSON.stringify(prefix + '_IMAGE')}) && element.textContent.includes('恢复'))`
    );
    await rowButton('.report-assets .el-table__row', prefix + '_IMAGE', '恢复');
    await confirm();
    await clean();
  });

  await menu('报表管理', '报表中心');
  await fill('input[placeholder="搜索报表名称或编码"]', 'BASE_ERP_PURCHASE_ORDER_DOCUMENT');
  const documentTitle = title + ' 媒体单据';
  await step('单据设计：从采购模板复制并选择自己的纸张', async () => {
    await rowButton(selectors.center, 'BASE_ERP_PURCHASE_ORDER_DOCUMENT', '更多');
    await press('复制为草稿', '.el-dropdown-menu__item');
    await fill('.el-message-box input', prefix + '_DOC');
    await confirm();
    await wait('document.querySelector("input[placeholder=报表标题]")');
    await fill('input[placeholder=报表标题]', documentTitle);
    await press('纸张与分页', '.el-tabs__item');
    await click(await field('纸张预设', '.el-select'));
    await press('自定义', '.el-select-dropdown__item');
    await fill(await field('纸张宽度（mm）'), '240');
    await click('.report-designer h1');
    await press('撤销', '.designer-toolbar button');
    await press('重做', '.designer-toolbar button');
    await press('设计画布', '.el-tabs__item');
    await clean();
  });
  await step('单据设计：固定行矩形合并、拆分及列边界拖动', async () => {
    await press('添加页眉行');
    await press('拆分选区');
    const before = await evaluate('document.querySelectorAll("[data-band=headerRows] .fixed-cell").length');
    await evaluate(`(() => {
      const cells = Array.from(document.querySelectorAll('[data-band=headerRows] .fixed-cell'));
      const last = Math.max(...cells.map(element => Number(element.style.gridRowStart)));
      const row = cells.filter(element => Number(element.style.gridRowStart) === last);
      if (row.length < 2) throw Error('新页眉行至少应包含两个固定格');
      row[0].dataset.liveCell='anchor'; row[1].dataset.liveCell='extent'; return true;
    })()`);
    await click('[data-live-cell=anchor]');
    const point = await evaluate(
      `(() => { const rect=document.querySelector('[data-live-cell=extent]').getBoundingClientRect();return {x:rect.left+rect.width/2,y:rect.top+rect.height/2}; })()`
    );
    for (const type of ['mousePressed', 'mouseReleased'])
      await command('Input.dispatchMouseEvent', { type, ...point, button: 'left', clickCount: 1, modifiers: 8 });
    await press('加粗选区');
    await press('居中', '.grid-toolbar button');
    await press('合并选区');
    await wait(`document.querySelectorAll('[data-band=headerRows] .fixed-cell').length === ${before - 1}`);
    await press('拆分选区');
    await wait(`document.querySelectorAll('[data-band=headerRows] .fixed-cell').length === ${before}`);
    await evaluate('document.querySelector(".column-resizer").scrollIntoView({block:"center"})');
    const boundary = await evaluate(
      `(() => { const rect=document.querySelector('.column-resizer').getBoundingClientRect();return {x:rect.left+rect.width/2,y:rect.top+rect.height/2}; })()`
    );
    await command('Input.dispatchMouseEvent', { type: 'mousePressed', ...boundary, button: 'left', buttons: 1, clickCount: 1 });
    await command('Input.dispatchMouseEvent', { type: 'mouseMoved', x: boundary.x + 12, y: boundary.y, button: 'left', buttons: 1 });
    await command('Input.dispatchMouseEvent', { type: 'mouseReleased', x: boundary.x + 12, y: boundary.y, button: 'left', clickCount: 1 });
    await press('撤销', '.designer-toolbar button');
    await clean();
  });
  await step('单据设计：通过真实字段栏加入币种可见明细列', async () => {
    assert.equal(
      await evaluate(`document.querySelector('input[placeholder="报表编码，如 STOCK_BALANCE"]')?.value`),
      prefix + '_DOC',
      '币种列只能加入本次自有单据模板'
    );
    const before = await evaluate('document.querySelectorAll(".paper-editor .detail-column").length');
    const currency = await evaluate(
      'Array.from(document.querySelectorAll(".paper-editor .detail-column")).some(element => element.textContent.trim() === "币种")'
    );
    if (!currency) {
      await click('.field-palette button[aria-label="添加字段：币种"]');
      await wait(
        `document.querySelectorAll('.paper-editor .detail-column').length === ${before + 1} && Array.from(document.querySelectorAll('.paper-editor .detail-column')).some(element => element.textContent.trim() === '币种')`
      );
    }
    await press('适配纸张宽度', '.field-palette button');
    await clean();
  });
  await step('单据设计：真实私有图片、QR和Code128及发布', async () => {
    await press('添加 QR');
    await fill(await field('横坐标（mm）'), '20');
    await fill(await field('纵坐标（mm）'), '200');
    await press('添加 Code128');
    await fill(await field('横坐标（mm）'), '65');
    await fill(await field('纵坐标（mm）'), '200');
    await press('添加私有图片');
    await fill('input[placeholder="搜索图片名称或编码"]', title + ' 图片');
    await wait('document.querySelector(".asset-choice:not(:disabled) img")');
    await click('.asset-choice:not(:disabled)');
    await wait('document.querySelectorAll(".paper-media").length === 3');
    await fill(await field('横坐标（mm）'), '150');
    await fill(await field('纵坐标（mm）'), '200');
    await press('保存并发布');
    await wait('!document.querySelector(".designer-toolbar .is-loading")');
    await clean();
  });

  await menu('报表管理', '报表中心');
  if (suite !== 'finish') {
    await fill('input[placeholder="搜索报表名称或编码"]', prefix + '_DOC');
    await step('单张打印：选择真实业务单据、预览及领取当前运行PDF', async () => {
      await rowButton(selectors.center, prefix + '_DOC', '运行');
      await wait('document.querySelector(".viewer-parameters .report-lookup .el-select")');
      await choose('.viewer-parameters .report-lookup .el-select', '');
      ledger.expectedLabels.clear();
      ledger.currencyValues.clear();
      ledger.layoutCurrencyValues.clear();
      ledger.captureDictionary = true;
      try {
        await press('运行报表');
        await wait('document.querySelector(".viewer-results .report-paper-view") && !document.querySelector(".viewer-results .el-loading-mask")');
        await settled();
      } finally {
        ledger.captureDictionary = false;
      }
      await press('打印PDF');
      await wait('document.querySelector(".report-pdf-preview iframe")?.src.startsWith("blob:")', 90000);
      const before = await beforeDownload();
      await press('下载PDF', '.el-dialog button');
      const file = await downloaded(before, '.pdf');
      const pdf = await getDocument({ data: new Uint8Array(file.bytes), useSystemFonts: false }).promise;
      assert.ok(pdf.numPages > 0);
      const page = await pdf.getPage(1);
      const operators = await page.getOperatorList();
      assert.ok(
        operators.fnArray.filter((operator) => [OPS.paintImageXObject, OPS.paintInlineImageXObject].includes(operator)).length >= 3,
        'PDF应包含图片和两种条码栅格'
      );
      const text = await page.getTextContent();
      const pageTexts = [text.items.map((item) => (typeof item.str === 'string' ? item.str : '')).join('')];
      for (let pageNumber = 2; pageNumber <= pdf.numPages; pageNumber++) {
        const content = await (await pdf.getPage(pageNumber)).getTextContent();
        pageTexts.push(content.items.map((item) => (typeof item.str === 'string' ? item.str : '')).join(''));
      }
      singlePdfText = pageTexts.join('').replace(/\s+/g, '');
      assert.ok(
        text.items.some((item) => typeof item.str === 'string' && item.str.includes('LIVE_RPT')),
        'PDF包含当前固定模板标题'
      );
      ledger.files.push({ format: 'PDF', pages: pdf.numPages, bytes: file.bytes.length });
      await pdf.destroy();
      await press('系统打印', '.el-dialog button');
      await press('关闭', '.el-dialog button');
      await clean();
    });

    await step('字典：真实采购单据预览与PDF显示名称、原码保持', async () => {
      const labels = [...ledger.expectedLabels];
      if (!labels.length) return { blocked: '当前真实单据未命中字典显示名称，不伪造业务数据' };
      ledger.dictionary.rawPreserved = [...ledger.currencyValues].filter(
        ([raw, label]) => ledger.layoutCurrencyValues.has(raw) && ledger.layoutCurrencyValues.get(raw) === label
      ).length;
      assert.equal(ledger.dictionary.rawPreserved, ledger.currencyValues.size, '排版响应必须保留页面响应中的原始币种编码和显示名称');
      ledger.dictionary.previewMatches = await evaluate(`(() => {
        const text = document.querySelector('.viewer-results .report-paper-view')?.textContent.replace(/\\s+/g, '') || '';
        return ${JSON.stringify(labels)}.filter(label => text.includes(label.replace(/\\s+/g, ''))).length;
      })()`);
      ledger.dictionary.pdfMatches = labels.filter((label) => singlePdfText.includes(label.replace(/\s+/g, ''))).length;
      assert.equal(ledger.dictionary.previewMatches, labels.length, '真实预览必须显示响应中的币种字典名称');
      assert.equal(ledger.dictionary.pdfMatches, labels.length, '实际下载PDF必须显示同一币种字典名称');
    });

    await menu('批量单据', '批量单据');
    await step('批次：四类单据固定版本、参数及顺序调整', async () => {
      await fill('input[placeholder="例如：本日出库单打印"]', title + ' 四类批次');
      const templates = [documentTitle, '销售订单打印', '送货单打印', '采购收货单打印'];
      for (const [index, template] of templates.entries()) {
        if (index) await press('增加单据');
        await evaluate(`document.querySelectorAll('.batch-document')[${index}].dataset.liveDocument='current'`);
        await choose('[data-live-document] .batch-document__form > .el-select', template);
        await wait('document.querySelector("[data-live-document] .report-lookup .el-select")');
        await choose('[data-live-document] .report-lookup .el-select', '');
        await evaluate('document.querySelector("[data-live-document]").removeAttribute("data-live-document")');
      }
      await evaluate('document.querySelectorAll(".batch-document")[3].dataset.liveMove="current"');
      await press('上移', '[data-live-move] button');
      await press('下移', '[data-live-move] button');
      await evaluate('document.querySelector("[data-live-move]").removeAttribute("data-live-move")');
      assert.equal(await evaluate('document.querySelectorAll(".batch-document").length'), 4);
      await clean();
    });
    await step('批次：真实入队、逐项完成及合并PDF领取', async () => {
      await press('创建并入队');
      await wait('document.querySelector(".batch-result-panel .el-table__row")');
      await press('刷新结果');
      const deadline = Date.now() + 90000;
      while (
        !(await evaluate(
          'Array.from(document.querySelectorAll(".batch-result-panel button")).some(element => element.textContent.trim() === "合并PDF预览/下载" && !element.disabled)'
        ))
      ) {
        assert.ok(Date.now() < deadline, '批次逐项完成超时');
        await new Promise((resolve) => setTimeout(resolve, 1000));
        await press('刷新结果');
        await settled();
        if (
          await evaluate(
            'Array.from(document.querySelectorAll(".batch-result-panel button")).some(element => element.textContent.trim() === "补充入队" && !element.disabled)'
          )
        ) {
          await press('补充入队');
          await settled();
        }
      }
      await press('合并PDF预览/下载');
      await wait('document.querySelector(".report-pdf-preview iframe")?.src.startsWith("blob:")', 90000);
      const before = await beforeDownload();
      await press('下载PDF', '.el-dialog button');
      const file = await downloaded(before, '.pdf');
      const pdf = await getDocument({ data: new Uint8Array(file.bytes), useSystemFonts: false }).promise;
      assert.ok(pdf.numPages >= 4, '四份单据必须分别起页');
      mergedDownloadGuid = file.guid;
      ledger.files.push({ format: 'PDF', pages: pdf.numPages, bytes: file.bytes.length, documents: 4 });
      await pdf.destroy();
      await press('关闭', '.el-dialog button');
      await clean();
    });
  }
  if (extended) {
    await step('批次：离开页面后恢复本次记录、授权并再次领取合并PDF', async () => {
      const batchId = await evaluate('document.querySelector(".batch-result-panel .report-section-description")?.textContent.match(/[0-9]+/)?.[0]');
      assert.ok(typeof batchId === 'string' && /^[0-9]+$/.test(batchId), '本次批次ID必须是完整字符串');
      await press('新建其他批次', '.report-batch button');
      await wait(
        'document.querySelectorAll(".batch-document").length === 4 && !document.querySelector("input[placeholder=例如：本日出库单打印]")?.disabled && Array.from(document.querySelectorAll(".report-batch button")).some(button => button.textContent.trim() === "创建并入队" && !button.disabled)'
      );
      await menu('报表管理', '报表中心');
      await menu('批量单据', '批量单据');
      await rowButton('.report-batch .el-table__row', title + ' 四类批次', '查看逐项结果');
      await wait(
        `document.querySelector('.batch-result-panel .report-section-description')?.textContent.includes(${JSON.stringify(batchId)}) && document.querySelectorAll('.batch-result-panel .el-table__row').length === 4`
      );
      await grantAndRemove(() => rowButton('.report-batch .el-table__row', title + ' 四类批次', '资源授权'));
      await press('合并PDF预览/下载', '.batch-result-panel button');
      await wait('document.querySelector(".report-pdf-preview iframe")?.src.startsWith("blob:")', 90000);
      const before = await beforeDownload();
      await press('下载PDF', '.el-dialog button');
      const file = await downloaded(before, '.pdf');
      assert.ok(mergedDownloadGuid && file.guid !== mergedDownloadGuid, '恢复后必须产生新的真实下载GUID');
      const pdf = await getDocument({ data: new Uint8Array(file.bytes), useSystemFonts: false }).promise;
      assert.ok(pdf.numPages >= 4, '恢复后的合并PDF仍必须包含全部四份单据');
      ledger.files.push({ format: 'PDF', pages: pdf.numPages, bytes: file.bytes.length });
      await pdf.destroy();
      await press('关闭', '.el-dialog button');
      await clean();
    });
    await step('批次：失败或待入队项恢复（缺少状态前提则blocked）', async () => {
      const retry = await evaluate(
        `Array.from(document.querySelectorAll('.batch-result-panel button')).find(button => button.getClientRects().length && !button.disabled && /^(重试原任务|补充入队)/.test(button.textContent.trim()))?.textContent.trim()`
      );
      if (!retry) return { blocked: '本次批次没有可恢复的失败或待入队项' };
      await press(retry, '.batch-result-panel button');
      await settled();
      await press('刷新结果', '.batch-result-panel button');
      await wait(
        'Array.from(document.querySelectorAll(".batch-result-panel .report-status")).length === 4 && Array.from(document.querySelectorAll(".batch-result-panel .report-status")).every(element => element.textContent.trim() === "已完成")',
        90000
      );
      await clean();
    });
  }
  await menu('个人仪表盘', '个人仪表盘');
  await step('仪表盘：真实组件保存、发布、运行、刷新和收藏', async () => {
    await press('新建仪表盘');
    await fill('input[placeholder=仪表盘编码]', prefix + '_DASH');
    await fill('input[placeholder=标题]', title + ' 仪表盘');
    await choose('.report-dashboard .el-card .el-card .el-select', '明细表');
    await evaluate('document.querySelectorAll(".report-dashboard .el-select")[1].dataset.liveReport = "current"');
    await choose('[data-live-report]', sourceTitle);
    await press('保存草稿');
    await wait(
      'Array.from(document.querySelectorAll(".report-dashboard button")).some(element => element.textContent.trim() === "发布固定版本" && !element.disabled)'
    );
    await press('发布固定版本');
    await settled();
    await press('退出设计');
    await wait('!document.querySelector("input[placeholder=仪表盘编码]")');
    await press(title + ' 仪表盘');
    await wait('document.querySelector(".dashboard-grid .el-table")');
    await press('刷新组件');
    await wait('!document.querySelector(".dashboard-runtime-toolbar .is-loading")');
    await press('收藏');
    await press('取消收藏');
    await clean();
  });

  if (extended) {
    await step('仪表盘：编辑并发布指标、柱状、折线、饼图和交叉组件', async () => {
      await press('编辑', '.dashboard-picker button');
      await wait(`document.querySelector('input[placeholder=仪表盘编码]')?.value === ${JSON.stringify(prefix + '_DASH')}`);
      await settled();
      for (const [type, label] of [
        ['METRIC', '指标卡'],
        ['BAR', '柱状图'],
        ['LINE', '折线图'],
        ['PIE', '饼图'],
        ['PIVOT', '交叉表']
      ]) {
        await press('添加组件', '.report-dashboard button');
        await evaluate(
          `document.querySelectorAll('.report-dashboard .report-editor-form > .el-card').item(document.querySelectorAll('.report-dashboard .report-editor-form > .el-card').length - 1).setAttribute('data-live-widget', '')`
        );
        await fill('[data-live-widget] input[placeholder=组件标题]', prefix + '_' + type);
        await choose('[data-live-widget] .el-select', label);
        await evaluate(`document.querySelectorAll('[data-live-widget] .el-select')[1].setAttribute('data-live-widget-source', '')`);
        await choose('[data-live-widget] [data-live-widget-source]', type === 'PIVOT' ? title + ' 交叉 /' : sourceTitle);
        await settled();
        if (['BAR', 'LINE', 'PIE'].includes(type)) {
          await evaluate(`document.querySelectorAll('[data-live-widget] .el-select')[2].setAttribute('data-live-widget-category', '')`);
          await choose('[data-live-widget] [data-live-widget-category]', '供应商分类');
        }
        await evaluate(`document.querySelector('[data-live-widget]').removeAttribute('data-live-widget')`);
      }
      assert.equal(
        await evaluate('document.querySelectorAll(".report-dashboard .report-editor-form > .el-card").length'),
        6,
        '六类组件都必须真实加入草稿'
      );
      await press('保存草稿', '.report-dashboard button');
      await wait(
        'Array.from(document.querySelectorAll(".report-dashboard button")).some(button => button.textContent.trim() === "发布固定版本" && !button.disabled)'
      );
      await press('发布固定版本', '.report-dashboard button');
      await settled();
      await press('退出设计', '.report-dashboard button');
      await wait(
        'document.querySelectorAll(".dashboard-grid .dashboard-widget").length === 6 && document.querySelectorAll(".dashboard-grid .widget-chart .chart canvas").length === 3 && !!document.querySelector(".dashboard-grid .metric-value") && !!document.querySelector(".dashboard-grid .pivot-inline table")',
        30000
      );
      await clean();
      await click('.dashboard-runtime-toolbar .el-switch');
      await wait('document.querySelector(".dashboard-runtime-toolbar .el-switch")?.classList.contains("is-checked")');
    });
    await step('仪表盘：自身资源授权、目录刷新和放弃未保存编辑', async () => {
      await grantAndRemove(() => press('资源授权', '.report-dashboard button'));
      await press('刷新目录', '.report-dashboard button');
      await settled();
      await press('编辑', '.dashboard-picker button');
      await wait(`document.querySelector('input[placeholder=仪表盘编码]')?.value === ${JSON.stringify(prefix + '_DASH')}`);
      await settled();
      await fill('input[placeholder=标题]', title + ' 仪表盘 未保存');
      await press('退出设计', '.report-dashboard button');
      await confirm();
      await wait('!document.querySelector(".report-dashboard input[placeholder=仪表盘编码]")');
      await press('编辑', '.dashboard-picker button');
      await wait(`document.querySelector('.report-dashboard input[placeholder=标题]')?.value === ${JSON.stringify(title + ' 仪表盘')}`);
      await press('退出设计', '.report-dashboard button');
      await clean();
    });
  }

  await menu('报表订阅', '报表订阅');
  const subscriptionPreflight = await step('订阅：自动调度和真实通知关闭前提', async () => {
    const closed = await evaluate(
      `Array.from(document.querySelectorAll('.report-subscription .el-alert')).some(element => element.textContent.includes('自动调度：未启用，可使用手动运行') && element.textContent.includes('真实站内通知：关闭'))`
    );
    if (!closed) return { blocked: '调度和真实通知必须关闭，未创建订阅' };
  });
  if (subscriptionPreflight.status !== 'PASS') return ledger;
  await step('订阅：自身接收人保存、发布及手动执行', async () => {
    await press('新建订阅');
    await fill('input[placeholder=订阅编码]', prefix + '_SUB');
    await fill('input[placeholder=名称]', title + ' 自身交付');
    await choose(await field('固定报告', '.el-select'), sourceTitle);
    await choose(await field('交付格式', '.el-select'), '数据XLSX');
    await fill('input[placeholder*=以逗号]', selfUserId);
    assert.ok(
      await evaluate(
        '!!document.querySelector(".report-subscription .el-switch") && !document.querySelector(".report-subscription .el-switch").classList.contains("is-checked")'
      ),
      '自身订阅不得开启真实通知'
    );
    await press('保存草稿');
    await wait(
      'Array.from(document.querySelectorAll(".report-subscription button")).some(element => element.textContent.trim() === "发布固定订阅" && !element.disabled)'
    );
    await press('发布固定订阅');
    await wait(
      `Array.from(document.querySelectorAll('.report-subscription .el-table__row')).some(element => element.textContent.includes(${JSON.stringify(prefix + '_SUB')}) && Array.from(element.querySelectorAll('button')).some(button => button.textContent.trim() === '手动运行' && !button.disabled))`
    );
    await rowButton('.report-subscription .el-table__row', prefix + '_SUB', '手动运行');
    await settled();
    await rowButton('.report-subscription .el-table__row', prefix + '_SUB', '执行记录');
    subscriptionId = await evaluate(
      `Array.from(document.querySelectorAll('.report-subscription .report-section-heading')).find(element => element.querySelector('h2')?.textContent.trim() === '执行记录')?.querySelector('.report-secondary')?.textContent.trim()`
    );
    assert.ok(typeof subscriptionId === 'string' && /^\d+$/.test(subscriptionId), '执行记录应定位当前测试订阅');
    await clean();
  });
  await menu('我的报表交付', '我的报表交付');
  await step('交付：刷新及鉴权领取实际文件', async () => {
    const deadline = Date.now() + 90000;
    const ready = `Array.from(document.querySelectorAll('.report-delivery .el-table__row')).some(row => row.textContent.includes(${JSON.stringify(subscriptionId)}) && Array.from(row.querySelectorAll('button')).some(button => button.textContent.trim() === '鉴权领取文件' && !button.disabled))`;
    while (!(await evaluate(ready))) {
      assert.ok(Date.now() < deadline, '本次测试订阅的交付未就绪');
      await press('刷新交付');
      await settled();
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
    const before = await beforeDownload();
    await rowButton('.report-delivery .el-table__row', subscriptionId, '鉴权领取文件');
    const file = await downloaded(before, '.xlsx');
    assert.equal(file.bytes.subarray(0, 2).toString(), 'PK');
    ledger.files.push({ format: 'XLSX', bytes: file.bytes.length });
    await clean();
  });

  if (extended) {
    await step('订阅：自身资源编辑、授权及停用恢复', async () => {
      await menu('报表订阅', '报表订阅');
      await rowButton('.report-subscription .el-table__row', prefix + '_SUB', '编辑');
      await wait(`document.querySelector('input[placeholder=订阅编码]')?.value === ${JSON.stringify(prefix + '_SUB')}`);
      assert.equal(await evaluate('document.querySelector("input[placeholder*=以逗号]")?.value'), selfUserId, '接收人必须仍为本次自身ID');
      await grantAndRemove(() => rowButton('.report-subscription .el-table__row', prefix + '_SUB', '资源授权'));
      for (const [button, state] of [
        ['停用', '已下线'],
        ['恢复', '已发布']
      ]) {
        await rowButton('.report-subscription .el-table__row', prefix + '_SUB', button);
        await confirm();
        await wait(
          `Array.from(document.querySelectorAll('.report-subscription .el-table__row')).some(row => row.textContent.includes(${JSON.stringify(prefix + '_SUB')}) && row.querySelector('.report-status')?.textContent.trim() === ${JSON.stringify(state)})`
        );
        await clean();
      }
      await menu('导出中心', '导出中心');
      await clean();
      await menu('报表订阅', '报表订阅');
      await rowButton('.report-subscription .el-table__row', prefix + '_SUB', '执行记录');
      await settled();
      assert.equal(
        await evaluate(
          `Array.from(document.querySelectorAll('.report-subscription .report-section-heading')).find(element => element.querySelector('h2')?.textContent.trim() === '执行记录')?.querySelector('.report-secondary')?.textContent.trim()`
        ),
        subscriptionId,
        '状态操作后必须仍定位本次订阅'
      );
    });

    let cancelledExecutionId = null;
    const markExecution = async (indices, states) => {
      const observed = ledger.executionRows.get(subscriptionId);
      assert.ok(Array.isArray(observed), '必须已观察本次订阅执行列表');
      return evaluate(`(() => {
        for (const row of document.querySelectorAll('[data-live-execution]')) row.removeAttribute('data-live-execution');
        const table = document.querySelectorAll('.report-subscription .el-table')[1];
        const rows = Array.from(table?.querySelectorAll('.el-table__row') || []);
        if (!table || rows.length !== ${observed.length}) throw Error('执行记录页面必须与本次真实响应行数一致');
        const index = ${JSON.stringify(indices)}.find(index =>
          rows[index]?.querySelector('.cell')?.textContent.trim() === ${JSON.stringify(selfUserId)} &&
          ${JSON.stringify(states)}.includes(rows[index]?.querySelector('.report-status')?.textContent.trim())
        );
        if (index === undefined) return -1;
        rows[index].setAttribute('data-live-execution', '');
        return index;
      })()`);
    };
    const execution = async (states, operation) => {
      await settled();
      const indices = eligibleExecutionIndices(ledger, subscriptionId, selfUserId, operation);
      const index = await markExecution(indices, states);
      assert.ok(!indices.length || index >= 0, '可操作执行必须与页面自身接收人及状态一致');
      return index < 0 ? null : ledger.executionRows.get(subscriptionId)[index];
    };

    await step('订阅：自身未完成执行取消及终态按钮禁用（缺少状态前提则blocked）', async () => {
      const current = await execution(['待处理', '排队中', 'PREPARING', '等待重试', '已阻止'], 'cancel');
      if (!current) return { blocked: '本次自身执行没有可取消的未完成状态' };
      const requestStart = ledger.requests.length;
      await press('取消', '[data-live-execution] button');
      await settled();
      const cancellations = ledger.requests
        .slice(requestStart)
        .filter((request) => request.path === '/report/subscriptions/executions/:id/cancel' && request.method === 'POST');
      assert.ok(cancellations.length === 1 && cancellations[0].status === 200 && cancellations[0].code === 200, '取消必须经过一次真实页面请求并成功');
      await rowButton('.report-subscription .el-table__row', prefix + '_SUB', '执行记录');
      await settled();
      const cancelled = assertCancelledExecution(ledger, subscriptionId, current.id, selfUserId);
      const index = ledger.executionRows.get(subscriptionId).indexOf(cancelled);
      assert.ok((await markExecution([index], ['已取消'])) === index, '页面必须显示选定执行的取消终态');
      const terminalRequestStart = ledger.requests.length;
      for (const label of ['取消', '重试']) {
        await evaluate(`(() => {
          const row = document.querySelector('[data-live-execution]');
          const button = Array.from(row?.querySelectorAll('button') || []).find(element => element.textContent.trim() === ${JSON.stringify(label)});
          if (!button || !button.disabled || !button.matches(':disabled')) throw Error('取消终态的取消和重试按钮必须原生禁用');
          button.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' });
        })()`);
        const point = await wait(`(() => {
          const row = document.querySelector('[data-live-execution]');
          const button = Array.from(row?.querySelectorAll('button') || []).find(element => element.textContent.trim() === ${JSON.stringify(label)});
          if (row?.querySelector('.report-status')?.textContent.trim() !== '已取消' || !button?.disabled || !button.matches(':disabled')) throw Error('取消终态按钮状态必须保持禁用');
          if (!button.getClientRects().length) return null;
          const rect = button.getBoundingClientRect();
          if (!rect.width || !rect.height) return null;
          const point = { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
          const hit = document.elementFromPoint(point.x, point.y);
          return hit && (hit === button || button.contains(hit) || hit === button.parentElement) ? point : null;
        })()`);
        await command('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point });
        await command('Input.dispatchMouseEvent', { type: 'mousePressed', ...point, button: 'left', clickCount: 1 });
        await command('Input.dispatchMouseEvent', { type: 'mouseReleased', ...point, button: 'left', clickCount: 1 });
      }
      await settled();
      assert.ok(
        !ledger.requests
          .slice(terminalRequestStart)
          .some((request) => /^\/report\/subscriptions\/executions\/:id\/(cancel|retry)$/.test(request.path) && request.method === 'POST'),
        '取消终态点击取消和重试均不得发起请求'
      );
      await rowButton('.report-subscription .el-table__row', prefix + '_SUB', '执行记录');
      await settled();
      assertCancelledExecution(ledger, subscriptionId, current.id, selfUserId);
      await clean();
      cancelledExecutionId = current.id;
    });
    await step('订阅：取消子作业后普通导出禁重试及交付刷新不复活（缺少前提则blocked）', async () => {
      if (!cancelledExecutionId) return { blocked: '本次缺少已取消且已创建子作业的自身执行' };
      const current = assertCancelledExecution(ledger, subscriptionId, cancelledExecutionId, selfUserId);
      if (current.job === null) return { blocked: '本次缺少已取消且已创建子作业的自身执行' };
      assert.ok(ledger.executionRows.get(subscriptionId).length === 1, '本次单接收人单次运行必须只有一条执行');
      const requestStart = ledger.requests.length;
      await menu('导出中心', '导出中心');
      const previousExports = ledger.exportRows;
      await press('刷新任务', '.report-exports button');
      await clean();
      assert.ok(Array.isArray(ledger.exportRows) && ledger.exportRows !== previousExports, '取消后普通导出必须取得新的真实响应');
      assertCancelledExecution(ledger, subscriptionId, current.id, selfUserId, true);
      const exportIndex = ledger.exportRows.findIndex((row) => row.id === current.job);
      assert.ok(
        await evaluate(`(() => {
        const rows = Array.from(document.querySelectorAll('.report-exports .el-table__row'));
        if (rows.length !== ${ledger.exportRows.length}) throw Error('普通导出页面必须与新响应行数一致');
        if (${exportIndex} < 0) return !rows.some(row => row.querySelector('.cell')?.textContent.trim() === ${JSON.stringify(current.job)});
        const row = rows[${exportIndex}];
        if (row?.querySelector('.cell')?.textContent.trim() !== ${JSON.stringify(current.job)}) throw Error('普通导出必须定位本次完整子作业ID');
        return Array.from(row.querySelectorAll('button')).filter(button => ['下载', '重试原任务'].includes(button.textContent.trim())).every(button => !button.getClientRects().length || button.disabled);
      })()`),
        '普通导出不得提供可用的已取消子作业下载或重试入口'
      );
      await menu('我的报表交付', '我的报表交付');
      const previousDeliveries = ledger.deliveryRows;
      await press('刷新交付', '.report-delivery button');
      await clean();
      assert.ok(Array.isArray(ledger.deliveryRows) && ledger.deliveryRows !== previousDeliveries, '取消后交付目录必须取得新的真实响应');
      const deliveryIndices = ledger.deliveryRows.flatMap((row, index) => (row.subscriptionId === subscriptionId ? [index] : []));
      assert.ok(
        deliveryIndices.length === 1 &&
          deliveryIndices.every((index) => ledger.deliveryRows[index].state === 'CANCELLED' && ledger.deliveryRows[index].jobId === null),
        '已取消自身执行的交付必须映射取消并隐藏子作业'
      );
      assert.ok(
        await evaluate(`(() => {
        const rows = Array.from(document.querySelectorAll('.report-delivery .el-table__row'));
        if (rows.length !== ${ledger.deliveryRows.length}) throw Error('交付页面必须与新响应行数一致');
        return ${JSON.stringify(deliveryIndices)}.every(index => {
          const row = rows[index];
          if (row?.querySelector('.cell')?.textContent.trim() !== ${JSON.stringify(subscriptionId)}) throw Error('交付页面必须定位本次完整订阅ID');
          const claim = Array.from(row.querySelectorAll('button')).find(button => button.textContent.trim() === '鉴权领取文件');
          return !!claim && claim.disabled && claim.matches(':disabled');
        });
      })()`),
        '取消后的自身交付不得提供可用领取入口'
      );
      await menu('报表订阅', '报表订阅');
      await rowButton('.report-subscription .el-table__row', prefix + '_SUB', '执行记录');
      await clean();
      const final = assertCancelledExecution(ledger, subscriptionId, current.id, selfUserId, true);
      assert.ok(final.job === current.job, '取消终态刷新后必须保持原子作业ID');
      const index = ledger.executionRows.get(subscriptionId).indexOf(final);
      assert.ok((await markExecution([index], ['已取消'])) === index, '普通导出和交付刷新后页面必须仍显示同一取消终态');
      assert.ok(
        await evaluate(`['取消', '重试'].every(label => {
        const button = Array.from(document.querySelectorAll('[data-live-execution] button')).find(element => element.textContent.trim() === label);
        return !!button && button.disabled && button.matches(':disabled');
      })`),
        '跨入口刷新后取消终态的两个按钮必须仍原生禁用'
      );
      assert.ok(
        !ledger.requests
          .slice(requestStart)
          .some(
            (request) =>
              request.method === 'POST' &&
              /^\/report\/(?:runtime\/exports|subscriptions\/executions|deliveries)\/:id\/(?:retry|cancel|claim)$/.test(request.path)
          ),
        '取消后的跨入口检查不得发起重试、取消或领取请求'
      );
    });
    await step('订阅：自身可恢复FAILED/BLOCKED/RETRY_WAIT执行重试（缺少状态前提则blocked）', async () => {
      const current = await execution(['失败', '已阻止', '等待重试'], 'retry');
      if (!current) return { blocked: '本次自身执行没有可重试的可恢复失败、阻止或等待重试状态' };
      const requestStart = ledger.requests.length;
      await press('重试', '[data-live-execution] button');
      await settled();
      const retries = ledger.requests
        .slice(requestStart)
        .filter((request) => request.path === '/report/subscriptions/executions/:id/retry' && request.method === 'POST');
      assert.ok(retries.length === 1 && retries[0].status === 200 && retries[0].code === 200, '重试必须经过一次真实页面请求并成功');
      const resumed = ledger.executionRows.get(subscriptionId)?.find((row) => row.id === current.id && row.recipient === selfUserId);
      assert.ok(resumed && resumed.state !== 'CANCELLED', '重试后必须仍保留本次自身的完整执行ID且不能进入取消终态');
      if (current.job !== null) assert.ok(resumed.job === current.job, '已有任务重试必须保留原任务ID');
      await clean();
    });
  }

  return ledger;
}
