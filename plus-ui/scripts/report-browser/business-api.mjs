import { fixture } from './api.mjs';
import { createHash } from 'node:crypto';

/** 用原生字符串生成一页有效合成PDF，只验证浏览器Blob生命周期。 */
function syntheticPdf() {
  const stream = 'BT /F1 12 Tf 20 100 Td (SYNTHETIC UI ONLY) Tj ET\n';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    '<< /Length ' + Buffer.byteLength(stream) + ' >>\nstream\n' + stream + 'endstream'
  ];
  let text = '%PDF-1.4\n';
  const offsets = [];
  for (const [index, object] of objects.entries()) {
    offsets.push(Buffer.byteLength(text));
    text += index + 1 + ' 0 obj\n' + object + '\nendobj\n';
  }
  const xref = Buffer.byteLength(text);
  text += 'xref\n0 6\n0000000000 65535 f \n' + offsets.map((offset) => String(offset).padStart(10, '0') + ' 00000 n \n').join('');
  return Buffer.from(text + 'trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n' + xref + '\n%%EOF\n');
}

/** 为真实业务入口提供严格合成契约，状态仅存于本次Node进程内存。 */
export function businessMock(validId) {
  const runs = [],
    batches = [],
    jobs = [],
    submissions = [];
  const faults = { createResponseLoss: 0, startFailures: 0, queued: false, pdfError: 'SCOPE_CHANGED', pdfStatus: 200 };
  const style = { fontSize: 10, bold: false, align: 'LEFT', color: '#111111', background: '#ffffff' };
  const columns = [{ field: 'orderId', label: '订单标识', width: 523.28, scale: 0, expression: null, style }];
  const dsl = {
    schemaVersion: 2,
    code: fixture.reportCode,
    title: '合成采购单模板',
    kind: 'DOCUMENT',
    datasetCode: 'SYNTHETIC_DATASET',
    datasetVersionId: fixture.datasetVersionId,
    parameters: [{ name: 'orderId', label: '采购订单', type: 'ID', required: true }],
    summaries: [],
    repeatHeader: true,
    columns,
    headerRows: [],
    footerRows: [],
    page: { width: 595.28, height: 841.89, marginLeft: 36, marginRight: 36, marginTop: 36, marginBottom: 36, rowHeight: 24 },
    print: { orientation: 'PORTRAIT', groupBy: [], repeatFooter: false, media: [] }
  };
  const definition = {
    id: fixture.reportId,
    code: dsl.code,
    name: dsl.title,
    kind: dsl.kind,
    catalogId: null,
    json: JSON.stringify(dsl),
    revision: 1,
    publishedVersion: fixture.reportVersionId,
    status: 'PUBLISHED',
    owner: fixture.userId,
    updatedAt: '2026-10-17T00:00:00Z'
  };
  const versions = [version(fixture.reportVersionId, 1)];

  /** 发布快照只存不可变字符串，后续保存草稿不能改写旧版本内容。 */
  function version(id, number) {
    return Object.freeze({
      id,
      reportId: definition.id,
      number,
      schema: 2,
      engine: '1.0.0',
      json: definition.json,
      checksum: createHash('sha256').update(definition.json).digest('hex'),
      datasetVersion: fixture.datasetVersionId,
      createdAt: '2026-10-17T00:00:00Z'
    });
  }

  /** 运行目录和预览始终读取当前发布快照，不能泄漏未发布的草稿。 */
  function published() {
    const current = versions.find((value) => value.id === definition.publishedVersion);
    return { versionId: current.id, number: current.number, dsl: JSON.parse(current.json) };
  }
  const descriptor = {
    code: dsl.datasetCode,
    name: '合成采购数据集',
    version: 1,
    permission: 'report:runtime:preview',
    document: true,
    fields: [{ name: 'orderId', label: '订单编码', type: 'ID', permission: null, aggregationKeys: [], aggregatable: false }],
    parameters: dsl.parameters,
    defaultColumns: ['orderId']
  };
  const dataset = {
    id: '9223372036854775788',
    code: dsl.datasetCode,
    name: descriptor.name,
    provider: dsl.datasetCode,
    publishedVersion: fixture.datasetVersionId,
    status: 'ACTIVE',
    owner: fixture.userId
  };

  /** 固定ID序列始终以字符串返回，避免JS数值截断。 */
  function nextId(offset, size) {
    return String(9223372036854775700n - BigInt(offset + size));
  }

  /** 读取有界JSON请求并记录合成参数，供场景核对幂等内容。 */
  async function body(request, entry) {
    const chunks = [];
    let size = 0;
    for await (const chunk of request) {
      size += chunk.length;
      if (size > 65536) throw Error('业务合成请求超过64KiB');
      chunks.push(chunk);
    }
    entry.body = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
    return entry.body;
  }

  /** 导出与批次项使用同一固定快照，不从界面重算单据内容。 */
  function job(runId, reportVersionId = runs.find((value) => value.id === runId)?.reportVersionId) {
    const value = {
      id: nextId(100, jobs.length),
      runId,
      reportVersionId,
      format: 'PDF',
      state: faults.queued ? 'QUEUED' : 'SUCCEEDED',
      phase: faults.queued ? 'QUEUED' : 'COMPLETED',
      processedRows: 1,
      totalRows: 1,
      attempts: 1,
      bytes: 128,
      expires: '2099-12-31T23:59:59Z',
      error: null,
      retryRound: 0,
      canRetry: false
    };
    jobs.push(value);
    return value;
  }

  /** 返回故意携带JSON的Blob；它只能作为UI错误证据，绝不冒充PDF生成验收。 */
  function file(reply, response, entry, versionIds) {
    if (faults.pdfError)
      reply(faults.pdfStatus, { code: faults.pdfStatus === 200 ? 403 : faults.pdfStatus, msg: '合成文件领取错误', errorCode: faults.pdfError });
    else {
      const pdf = syntheticPdf();
      entry.status = 200;
      entry.pdf = { synthetic: true, contentType: 'application/pdf', bytes: pdf.length, versionIds };
      response.writeHead(200, { 'Content-Type': 'application/pdf', 'Cache-Control': 'no-store, private' });
      response.end(pdf);
    }
  }

  /** 只处理列明的业务路由，其他请求仍由外层严格Mock判失败。 */
  async function handle({ request, response, route, permissions, reply, entry }) {
    const url = new URL(request.url, 'http://127.0.0.1');
    const method = request.method;
    /** 权限拒绝与正常响应使用相同HTTP状态和错误信封。 */
    const denied = (required) => {
      if (required.every((permission) => permissions.includes(permission))) return false;
      reply(403, { code: 403, msg: '合成业务资源无权访问', errorCode: 'SCOPE_DENIED' });
      return true;
    };
    if (route === '/report/datasets' && method === 'GET') {
      if (!denied(['report:dataset:list'])) reply(200, { code: 200, data: [{ dataset, descriptor, canManage: false }] });
      return true;
    }
    if (route === '/report/catalogs' && method === 'GET') {
      if (!denied(['report:definition:list'])) reply(200, { code: 200, data: [] });
      return true;
    }
    if (route === '/report/datasets/versions/' + fixture.datasetVersionId && method === 'GET') {
      if (!denied(['report:dataset:list']))
        reply(200, {
          code: 200,
          data: { id: fixture.datasetVersionId, datasetId: dataset.id, code: dsl.datasetCode, number: 1, current: true, descriptor }
        });
      return true;
    }
    if (route === '/report/definitions/' + fixture.reportId && method === 'GET') {
      if (!denied(['report:definition:edit'])) reply(200, { code: 200, data: { definition, dsl: JSON.parse(definition.json) } });
      return true;
    }
    if (route === '/report/definitions/' + fixture.reportId && method === 'PUT') {
      if (denied(['report:definition:edit'])) return true;
      const input = await body(request, entry);
      if (!Number.isSafeInteger(input.revision) || input.revision !== definition.revision)
        reply(409, { code: 409, msg: '草稿修订已变化', errorCode: 'REVISION_CONFLICT' });
      else if (
        Object.keys(input).sort().join(',') !== 'dsl,revision' ||
        !input.dsl ||
        input.dsl.code !== definition.code ||
        input.dsl.kind !== 'DOCUMENT' ||
        input.dsl.datasetCode !== dsl.datasetCode ||
        !validId(input.dsl.datasetVersionId) ||
        input.dsl.datasetVersionId !== fixture.datasetVersionId ||
        typeof input.dsl.title !== 'string' ||
        !input.dsl.title.trim()
      )
        reply(400, { code: 400, msg: '保存的code或数据集固定版本无效', errorCode: 'INVALID_REQUEST' });
      else {
        definition.json = JSON.stringify(input.dsl);
        definition.name = input.dsl.title;
        definition.revision++;
        reply(200, { code: 200, data: definition });
      }
      return true;
    }
    if (route === '/report/definitions/' + fixture.reportId + '/publish' && method === 'POST') {
      if (denied(['report:definition:edit', 'report:definition:publish'])) return true;
      const input = await body(request, entry);
      if (!Number.isSafeInteger(input.revision) || input.revision !== definition.revision)
        reply(409, { code: 409, msg: '发布修订已变化', errorCode: 'REVISION_CONFLICT' });
      else if (Object.keys(input).join(',') !== 'revision') reply(400, { code: 400, msg: '发布只接受预期revision' });
      else {
        const value = version(String(BigInt(fixture.nextReportVersionId) - BigInt(versions.length - 1)), versions.length + 1);
        versions.push(value);
        definition.publishedVersion = value.id;
        definition.status = 'PUBLISHED';
        definition.revision++;
        entry.publishedVersionId = value.id;
        reply(200, { code: 200, data: value });
      }
      return true;
    }
    if (route === '/report/definitions/validate' && method === 'POST') {
      if (denied(['report:runtime:preview'])) return true;
      const input = await body(request, entry);
      if (!input.dsl || !/^[A-Z][A-Z0-9_]{0,63}$/.test(input.dsl.code) || input.dsl.datasetVersionId !== fixture.datasetVersionId)
        reply(400, { code: 400, msg: '合成定义不符合固定契约' });
      else reply(200, { code: 200, data: { valid: true } });
      return true;
    }
    if (route === '/report/runtime/templates' && method === 'GET') {
      if (denied(['report:runtime:preview'])) return true;
      if (url.searchParams.get('datasetCode') !== 'SYNTHETIC_DATASET') reply(400, { code: 400, msg: '数据集code无效' });
      else {
        const current = published();
        entry.publishedVersionId = current.versionId;
        reply(200, { code: 200, data: [{ id: fixture.reportId, name: current.dsl.title, kind: 'DOCUMENT', versionId: current.versionId }] });
      }
      return true;
    }
    if (route === '/report/definitions/' + fixture.reportId + '/published' && method === 'GET') {
      if (!denied(['report:runtime:preview'])) reply(200, { code: 200, data: published() });
      return true;
    }
    if (route === '/report/lookups/' + fixture.datasetVersionId && method === 'GET') {
      if (!denied(['report:runtime:preview'])) reply(200, { code: 200, data: [] });
      return true;
    }
    if (route === '/report/runtime/runs' && method === 'POST') {
      if (denied(['report:runtime:preview'])) return true;
      const input = await body(request, entry);
      if (
        !validId(input.reportId) ||
        input.reportId !== fixture.reportId ||
        !['LIVE', 'SNAPSHOT'].includes(input.mode) ||
        !input.parameters ||
        Object.keys(input.parameters).length !== 1 ||
        !validId(input.parameters.orderId) ||
        !validId(input.expectedVersionId)
      )
        reply(400, { code: 400, msg: '必须保留完整字符串ID和固定版本', errorCode: 'INVALID_REQUEST' });
      else if (input.expectedVersionId !== definition.publishedVersion)
        reply(409, { code: 409, msg: '发布版本已变化', errorCode: 'REPORT_VERSION_CHANGED' });
      else {
        const value = {
          id: nextId(0, runs.length),
          reportId: fixture.reportId,
          version: definition.publishedVersion,
          reportVersionId: definition.publishedVersion,
          parameters: input.parameters,
          mode: input.mode,
          state: 'SUCCEEDED',
          rows: 1,
          dataTime: '2026-10-17T00:00:00Z',
          expires: '2099-12-31T23:59:59Z',
          error: null
        };
        runs.push(value);
        reply(200, { code: 200, data: value });
      }
      return true;
    }
    const runRoute = /^\/report\/runtime\/runs\/([^/]+)(?:\/(page|layout|exports))?$/.exec(route);
    if (runRoute) {
      if (denied([runRoute[2] === 'exports' ? 'report:runtime:export' : 'report:runtime:preview'])) return true;
      const run = runs.find((value) => value.id === runRoute[1]);
      if (!run) {
        reply(404, { code: 404, msg: '固定运行不存在' });
        return true;
      }
      if (runRoute[2] === 'exports' && method === 'POST') {
        const input = await body(request, entry);
        if (input.format !== 'PDF' || typeof input.requestKey !== 'string' || !/^[a-zA-Z0-9_-]{16,128}$/.test(input.requestKey))
          reply(400, { code: 400, msg: '无效导出标识' });
        else {
          const original = jobs.find((value) => value.runId === run.id && value.requestKey === input.requestKey);
          const value = original || Object.assign(job(run.id), { requestKey: input.requestKey });
          reply(200, { code: 200, data: value });
        }
      } else if (method !== 'GET') return false;
      else if (!runRoute[2]) reply(200, { code: 200, data: run });
      else if (runRoute[2] === 'page')
        reply(200, {
          code: 200,
          data: {
            columns,
            rows: [{ orderId: { type: 'ID', value: run.parameters.orderId } }],
            total: 1,
            dataTime: run.dataTime,
            mode: run.mode,
            summaries: []
          }
        });
      else if (runRoute[2] === 'layout')
        reply(200, {
          code: 200,
          data: {
            schemaVersion: 2,
            rowCount: 1,
            fontName: 'synthetic',
            columns,
            summaries: [],
            pages: [
              {
                number: 1,
                width: 595.28,
                height: 841.89,
                media: [],
                cells: [
                  {
                    band: 'DETAIL',
                    rowIndex: 0,
                    column: 0,
                    colSpan: 1,
                    rowSpan: 1,
                    x: 36,
                    y: 36,
                    width: 523.28,
                    height: 32,
                    lines: [
                      JSON.parse(versions.find((value) => value.id === run.reportVersionId).json).title + ' · 合成采购订单 ' + run.parameters.orderId
                    ],
                    style,
                    value: null,
                    scale: 0
                  }
                ]
              }
            ]
          }
        });
      else return false;
      return true;
    }
    if (route === '/report/runtime/exports' && method === 'GET') {
      if (!denied(['report:runtime:export'])) reply(200, { code: 200, data: jobs });
      return true;
    }
    const exportFile = /^\/report\/runtime\/exports\/([^/]+)\/file$/.exec(route);
    if (exportFile && method === 'GET') {
      if (denied(['report:runtime:export'])) return true;
      if (!jobs.some((value) => value.id === exportFile[1] && value.state === 'SUCCEEDED')) reply(404, { code: 404, msg: '文件未就绪' });
      else file(reply, response, entry, [jobs.find((value) => value.id === exportFile[1]).reportVersionId]);
      return true;
    }
    if (route === '/report/batches' && method === 'POST') {
      if (denied(['report:runtime:preview', 'report:runtime:export', 'report:batch:manage', 'report:batch:list'])) return true;
      const input = await body(request, entry);
      submissions.push(structuredClone(input));
      if (
        typeof input.code !== 'string' ||
        !/^[A-Z][A-Z0-9_]{0,63}$/.test(input.code) ||
        typeof input.title !== 'string' ||
        !input.title.trim() ||
        !Array.isArray(input.documents) ||
        !input.documents.length ||
        input.documents.length > 50 ||
        input.documents.some(
          (document) =>
            !versions.some((value) => value.id === document.reportVersionId) ||
            !validId(document.reportVersionId) ||
            !document.parameters ||
            Object.keys(document.parameters).length !== 1 ||
            !validId(document.parameters.orderId)
        )
      ) {
        reply(400, { code: 400, msg: '批次code、固定版本或完整参数无效', errorCode: 'INVALID_REQUEST' });
        return true;
      }
      let value = batches.find((batch) => batch.code === input.code);
      if (value && (value.title !== input.title || JSON.stringify(value.documents) !== JSON.stringify(input.documents))) {
        reply(409, { code: 409, msg: '幂等编码内容不一致' });
        return true;
      }
      if (!value) {
        value = {
          id: nextId(200, batches.length),
          tenant: fixture.tenant,
          owner: fixture.userId,
          code: input.code,
          title: input.title,
          documents: input.documents,
          json: JSON.stringify(input.documents),
          revision: 1,
          publishedVersion: null,
          status: 'DRAFT',
          items: []
        };
        batches.push(value);
      }
      if (faults.createResponseLoss > 0) {
        faults.createResponseLoss--;
        reply(503, { code: 503, msg: '合成创建响应丢失' });
      } else reply(200, { code: 200, data: value });
      return true;
    }
    const batchRoute = /^\/report\/batches\/([^/]+)\/(start|items|pdf)$/.exec(route);
    if (batchRoute) {
      if (
        denied(
          batchRoute[2] === 'start'
            ? ['report:batch:list', 'report:batch:manage', 'report:runtime:export', 'report:runtime:preview']
            : batchRoute[2] === 'pdf'
              ? ['report:batch:list', 'report:runtime:export', 'report:runtime:preview']
              : ['report:batch:list']
        )
      )
        return true;
      const batch = batches.find((value) => value.id === batchRoute[1]);
      if (!batch) {
        reply(404, { code: 404, msg: '批次不可访问' });
        return true;
      }
      if (batchRoute[2] === 'start' && method === 'POST') {
        if (faults.startFailures > 0) {
          faults.startFailures--;
          reply(503, { code: 503, msg: '合成入队失败' });
          return true;
        }
        if (!batch.items.length)
          batch.items = batch.documents.map((document, number) => {
            const task = job(batch.id, document.reportVersionId);
            return {
              number,
              reportVersionId: document.reportVersionId,
              jobId: task.id,
              state: task.state,
              error: null,
              canRetry: false,
              retryRound: 0,
              expires: task.expires
            };
          });
        reply(200, { code: 200, data: null });
      } else if (method !== 'GET') return false;
      else if (batchRoute[2] === 'items') reply(200, { code: 200, data: batch.items });
      else if (batchRoute[2] === 'pdf' && batch.items.length && batch.items.every((value) => value.state === 'SUCCEEDED'))
        file(
          reply,
          response,
          entry,
          batch.documents.map((document) => document.reportVersionId)
        );
      else {
        reply(409, { code: 409, msg: '批次未全部完成' });
      }
      return true;
    }
    return false;
  }
  return { handle, runs, batches, jobs, submissions, faults, definition, versions };
}
