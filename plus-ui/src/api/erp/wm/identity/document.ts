import request from '@/utils/request';
import type { AxiosRequestConfig } from 'axios';
import type { TraceRow } from '@/components/material-trace/model';
import { createCommandSession, identityText, materialPolicy, postingBody, prepareTraceDocument } from '@/components/material-trace/model';

const commands = createCommandSession();
const draftCommands = new WeakMap<object, ReturnType<typeof createCommandSession>>();

/** 保存原业务单据时保留源单头和版本，歧义或未完成的解析阻断保存。 */
export function traceSave<T extends TraceRow>(data: T): T & { commandId: string } {
  const inspect = (value: any): void => {
    if (!value || typeof value !== 'object') return;
    if (value._traceResolving || value._traceError) throw new Error(value._traceError || '条码解析尚未完成');
    for (const child of Object.values(value)) if (child && typeof child === 'object') inspect(child);
  };
  inspect(data);
  if (!draftCommands.has(data)) draftCommands.set(data, createCommandSession());
  const commandId = data._traceCommandId || draftCommands.get(data)!.forPayload(data);
  return prepareTraceDocument(data, commandId);
}

/** 原执行端点的请求体支持实物明细；列表操作必须重新读取详情，不使用过期列表行。 */
export async function executeTraceDocument(options: {
  url: string;
  detailUrl: string;
  linesKey?: string;
  quantityKey: string;
  data?: TraceRow;
  method?: 'post' | 'put';
  send?: (config: AxiosRequestConfig) => Promise<any>;
}) {
  const header: TraceRow = options.data ?? (await request({ url: options.detailUrl, method: 'get' })).data;
  const lines: TraceRow[] = options.linesKey ? header[options.linesKey] || [] : [header];
  const rows = lines.flatMap((line) =>
    line.detailList?.length ? line.detailList : line.wmMaterialReceiveLineDetailList?.length ? line.wmMaterialReceiveLineDetailList : [line]
  );
  for (const row of rows) {
    if (!row.policy && row.materialId) {
      const response = await request({ url: `/erp/bd/material/${encodeURIComponent(identityText(row.materialId)!)}`, method: 'get' });
      row.policy = materialPolicy(response.data);
    }
  }
  const body = postingBody(header, rows, options.quantityKey, header._traceCommandId || commands.forPayload({ action: options.url, header }));
  return (options.send || request)({ url: options.url, method: options.method || 'put', data: body, headers: { repeatSubmit: false } });
}
