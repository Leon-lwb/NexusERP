import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

/** 只通过真实页面读取既有报表资源与样例，时间值仅在内存比较，证据只保留步骤结果。 */
export async function runTimeScenario({ cdp, actions, ledger }) {
  const { evaluate, command } = cdp;
  const { wait, click, press, fill, menu, settled, step } = actions;
  const run = process.env.REPORT_LIVE_TIME_RUN || 'lK4f8u';
  assert.ok(/^[A-Za-z0-9]{6}$/.test(run), '时间复验必须指定已有证据运行');
  const evidence = JSON.parse(
    await readFile(new URL('../../node_modules/.tmp/report-live-run-' + run + '-evidence/verification.json', import.meta.url), 'utf8')
  );
  assert.ok(evidence.live && !evidence.synthetic && /^LIVE_RPT_\d{14}$/.test(evidence.resourcePrefix), '时间复验只能使用本线程既有真实资源');
  const prefix = evidence.resourcePrefix;
  ledger.resourcePrefix = prefix;
  const firstRequest = ledger.requests.length;
  const pending = new Map();
  const captures = new Set();
  const responses = new Map();
  let active = true;
  let errors = 0;
  cdp.on('Network.responseReceived', ({ requestId, response }) => {
    if (!active || response.status !== 200) return;
    const path = new URL(response.url).pathname.replace(/^\/dev-api/, '');
    if (
      /^\/report\/(?:definitions(?:\/\d+\/versions)?|audit|runtime\/exports|subscriptions(?:\/\d+\/(?:schedule|executions))?|deliveries|batches(?:\/\d+\/items)?|datasets\/\d+\/sample)$/.test(
        path
      )
    ) {
      pending.set(requestId, path);
    }
  });
  cdp.on('Network.loadingFinished', ({ requestId }) => {
    const path = pending.get(requestId);
    if (!active || !path) return;
    pending.delete(requestId);
    const capture = (async () => {
      try {
        const body = await command('Network.getResponseBody', { requestId });
        const text = body.base64Encoded ? Buffer.from(body.body, 'base64').toString('utf8') : body.body;
        assert.ok(text.length < 1024 * 1024, '时间响应超过采集上限');
        const envelope = JSON.parse(text);
        assert.ok(envelope.code === 200, '时间复验必须获得真实成功响应');
        responses.set(path, envelope.data);
      } catch {
        errors++;
      }
    })();
    captures.add(capture);
    void capture.finally(() => captures.delete(capture));
  });

  /** 在实际网络和DOM稳定后核对显示，避免以空表或旧响应作为通过。 */
  async function drain() {
    await settled();
    await Promise.all([...captures]);
    assert.ok(errors === 0, '时间响应采集失败');
  }

  /** 元数据按业务时区核对到秒，日期本身不跨天，不记录具体时间。 */
  function expected(value) {
    if (value === null || value === undefined || value === '') return '—';
    if (/^\d{4}-\d{2}-\d{2}(?: \d{2}:\d{2}:\d{2})?$/.test(value)) return value;
    const date = new Date(value);
    assert.ok(Number.isFinite(date.getTime()), '真实接口时间必须可解析');
    return new Intl.DateTimeFormat('sv-SE', {
      timeZone: 'Asia/Shanghai',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23'
    }).format(date);
  }

  /** 用实际表头选择目标列，比较完整行数和文本，任何原始ISO或时间戳直出均失败。 */
  async function tableTimes(selector, header, values, tableIndex = 0) {
    assert.ok(values.length > 0, '时间核对必须存在非零真实记录');
    const actual = await evaluate(`(() => {
      const table = document.querySelectorAll(${JSON.stringify(selector)})[${tableIndex}];
      const index = Array.from(table.querySelectorAll('.el-table__header th')).findIndex(cell => cell.textContent.trim() === ${JSON.stringify(header)});
      if (index < 0) throw Error('时间列表缺少对应表头');
      return Array.from(table.querySelectorAll('.el-table__body .el-table__row')).map(row => row.querySelectorAll('td')[index]?.textContent.trim());
    })()`);
    assert.ok(JSON.stringify(actual) === JSON.stringify(values.map(expected)), '页面时间必须与真实响应的业务时区格式一致');
  }

  /** 仅点击已存在行中的读取按钮，不调用页面内部方法或直接发业务请求。 */
  async function rowButton(selector, code, label) {
    await evaluate(`(() => {
      const row = Array.from(document.querySelectorAll(${JSON.stringify(selector)})).find(row => row.textContent.includes(${JSON.stringify(code)}));
      const button = row && Array.from(row.querySelectorAll('button')).find(button => button.textContent.trim() === ${JSON.stringify(label)} && !button.disabled);
      if (!button) throw Error('既有资源的读取入口不存在');
      document.querySelector('[data-time-action]')?.removeAttribute('data-time-action');
      button.dataset.timeAction = 'current';
    })()`);
    await click('[data-time-action]');
  }

  try {
    await step('时间显示：报表中心列表对应真实更新时间', async () => {
      await menu('报表管理', '报表中心');
      await press('列表', '.center-results .el-radio-button');
      await click('[aria-label="刷新报表列表"]');
      await drain();
      await tableTimes(
        '.center-results .el-table',
        '更新时间',
        responses.get('/report/definitions').map((row) => row.updatedAt)
      );
    });
    await step('时间显示：卡片更新时间与列表一致', async () => {
      await press('卡片', '.center-results .el-radio-button');
      await wait('document.querySelector(".report-resource-card__meta")');
      const actual = await evaluate(
        'Array.from(document.querySelectorAll(".report-resource-card__meta")).map(meta => meta.querySelectorAll("span")[1].textContent.trim())'
      );
      assert.ok(
        JSON.stringify(actual) === JSON.stringify(responses.get('/report/definitions').map((row) => expected(row.updatedAt))),
        '卡片时间必须使用同一业务格式'
      );
      await press('列表', '.center-results .el-radio-button');
    });
    await step('时间显示：审计时间兼容真实序列化类型', async () => {
      await press('操作审计');
      await wait('document.querySelector(".el-drawer.open .el-table")');
      await drain();
      await tableTimes(
        '.el-drawer.open .el-table',
        '时间',
        responses.get('/report/audit').map((row) => row.create_time)
      );
      await click('.el-drawer.open .el-drawer__close-btn');
      await settled();
    });
    await step('时间显示：既有模板发布历史', async () => {
      await fill('input[placeholder="搜索报表名称或编码"]', prefix + '_TABLE');
      await rowButton('.center-results .el-table__row', prefix + '_TABLE', '更多');
      await press('发布历史', '.el-dropdown-menu__item');
      await wait('document.querySelector(".report-history .el-table__row")');
      await drain();
      const versions = [...responses].find(([path]) => path.endsWith('/versions'))?.[1];
      await tableTimes(
        '.report-history .el-table',
        '发布时间',
        versions.map((row) => row.createdAt)
      );
    });
    await step('时间显示：导出中心到期时间', async () => {
      await menu('导出中心', '导出中心');
      await press('刷新任务', '.report-exports button');
      await drain();
      await tableTimes(
        '.report-exports .el-table',
        '到期时间',
        responses.get('/report/runtime/exports').map((row) => row.expires)
      );
    });
    await step('时间显示：既有订阅执行时刻和下一计划时刻', async () => {
      await menu('报表订阅', '报表订阅');
      await rowButton('.report-subscription .el-table__row', prefix + '_SUB', '执行记录');
      await wait('document.querySelectorAll(".report-subscription .el-table").length === 2');
      await drain();
      const rows = [...responses].find(([path]) => path.endsWith('/executions'))?.[1];
      await tableTimes(
        '.report-subscription .el-table',
        '固定时刻',
        rows.map((row) => row.scheduledAt),
        1
      );
      const schedule = [...responses].find(([path]) => path.endsWith('/schedule'))?.[1];
      assert.ok(schedule, '既有订阅应存在计划记录');
      assert.ok(
        await evaluate(
          `document.querySelector('.report-subscription').textContent.includes(${JSON.stringify('下一执行时刻 ' + expected(schedule.nextAt))})`
        ),
        '下一计划时刻必须统一格式'
      );
    });
    await step('时间显示：私有交付到期时间', async () => {
      await menu('我的报表交付', '我的报表交付');
      await press('刷新交付', '.report-delivery button');
      await drain();
      await tableTimes(
        '.report-delivery .el-table',
        '到期时刻',
        responses.get('/report/deliveries').map((row) => row.expires)
      );
    });
    await step('时间显示：既有批次逐项有效期', async () => {
      await menu('批量单据', '批量单据');
      await rowButton('.report-batch .el-table__row', prefix.replace('LIVE_RPT_', 'LIVE_RPT 真实操作 ') + ' 四类批次', '查看逐项结果');
      await wait('document.querySelector(".batch-result-panel .el-table__row")');
      await drain();
      const rows = [...responses].find(([path]) => path.endsWith('/items'))?.[1];
      const text = await evaluate('document.querySelector(".batch-result-panel .el-table").textContent');
      const times = rows.filter((row) => row.expires).map((row) => expected(row.expires));
      assert.ok(times.length > 0 && times.every((time) => text.includes(time)), '批次有效期必须使用业务时间格式');
      assert.ok(!/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(text), '批次不应显示ISO时间原文');
    });
    for (const code of ['MES_WORK_ORDER_TRACKING', 'MES_IQC_RECORDS']) {
      await step('时间显示：真实数据集样例 ' + code, async () => {
        await menu('数据集目录', '数据集目录');
        await rowButton('.report-dataset .el-table__row', code, '字段与样例');
        await press('数据样例', '.el-tabs__item');
        await press('查询样例', '.el-drawer.open button');
        await wait('document.querySelector(".el-drawer.open .el-tab-pane[id$=sample] .el-table")');
        await drain();
        const sample = [...responses].filter(([path]) => path.endsWith('/sample')).at(-1)?.[1];
        const values = sample.rows.flatMap((row) =>
          Object.values(row).filter((value) => ['DATE', 'DATETIME'].includes(value.type) && value.value !== null)
        );
        if (!values.length) {
          await click('.el-drawer.open .el-drawer__close-btn');
          await settled();
          return { blocked: '当前已授权样例没有非空时间字段，未补造业务数据' };
        }
        const text = await evaluate('document.querySelector(".el-drawer.open .el-tab-pane[id$=sample] .el-table").textContent');
        assert.ok(
          values.every((value) => text.includes(value.type === 'DATE' ? value.value : expected(value.value))),
          '样例时间必须格式化，日期保留原日'
        );
        assert.ok(!/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(text), '样例不应显示ISO时间原文');
        await click('.el-drawer.open .el-drawer__close-btn');
        await settled();
      });
    }
    await step('时间复验：仅读取既有资源和只读样例', async () => {
      await drain();
      assert.ok(
        ledger.requests
          .slice(firstRequest)
          .every((request) => request.method === 'GET' || (request.method === 'POST' && /\/datasets\/:id\/sample$/.test(request.path))),
        '时间复验不得创建、发布或变更资源'
      );
    });
    return ledger;
  } finally {
    active = false;
    await Promise.all([...captures]);
    pending.clear();
    responses.clear();
  }
}
