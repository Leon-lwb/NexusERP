/** 报表状态指引仅描述明确的恢复操作，不自动重发请求。 */
export interface ReportFailure {
  /** 业务错误码，不使用Axios传输错误码代替。 */
  code: string;
  /** 可安全展示的操作说明。 */
  message: string;
  /** 当前状态允许用户选择的恢复方向。 */
  action: 'SELECT_TEMPLATE' | 'NEW_RUN' | 'ACCESS' | 'NONE';
  /** 服务端定位的原提交序号，只接受0至49。 */
  itemNumber?: number;
}

/** 只读取普通响应及小型JSON错误文件，保留原始异常对象。 */
export async function readReportFailure(error: unknown): Promise<ReportFailure> {
  const source = error as { response?: { status?: number; data?: unknown }; errorCode?: string } | undefined;
  let data = source?.response?.data ?? source;
  if (data instanceof Blob) {
    if (data.size > 65536) data = undefined;
    else {
      try {
        data = JSON.parse(await data.text());
      } catch {
        data = undefined;
      }
    }
  }
  const code = typeof (data as { errorCode?: unknown })?.errorCode === 'string' ? (data as { errorCode: string }).errorCode : '';
  const rawMessage = (data as { msg?: unknown })?.msg;
  const located = typeof rawMessage === 'string' ? /item_no=(\d{1,2})(?!\d)/.exec(rawMessage) : undefined;
  const itemNumber = located && Number(located[1]) < 50 ? Number(located[1]) : undefined;
  /** 只拼接受限序号及固定说明，服务端内部消息不直接进入界面。 */
  function failure(message: string, action: ReportFailure['action']): ReportFailure {
    return {
      code,
      message: (itemNumber === undefined ? '' : `第${itemNumber + 1}项：`) + message,
      action,
      ...(itemNumber === undefined ? {} : { itemNumber })
    };
  }
  if (code === 'REPORT_VERSION_CHANGED') return failure('模板发布版本已变化，请重新选择模板后预览。', 'SELECT_TEMPLATE');
  if (
    ['SNAPSHOT_EXPIRED', 'ARTIFACT_EXPIRED', 'EXPORT_EXPIRED', 'RUN_EXPIRED', 'BATCH_ITEM_EXPIRED'].includes(code) ||
    source?.response?.status === 410
  )
    return failure('固定快照或文件已过期，请明确创建新的运行或批次。', 'NEW_RUN');
  if (source?.response?.status === 403 || /DENIED|SCOPE_CHANGED/.test(code))
    return failure('当前单据或依赖权限已变化，请重新选择有权访问的内容。', 'ACCESS');
  if (code === 'REPORT_OFFLINE' || code === 'ASSET_OFFLINE')
    return failure('模板或绑定资源已下线，请重新选择可用模板或固定图片。', 'SELECT_TEMPLATE');
  if (code === 'BATCH_CODE_CONFLICT') return failure('批次编码与已有内容冲突，请先核对原批次，再明确新建其他批次。', 'NEW_RUN');
  if (['BATCH_ITEM_CHANGED', 'BATCH_ITEM_FORMAT', 'BATCH_ITEM_CANCELLED'].includes(code))
    return failure('原固定单据已无法继续交付，请明确创建新批次。', 'NEW_RUN');
  if (code === 'BATCH_ITEM_NOT_READY') return failure('单据尚未完成，请刷新逐项结果；未入队项可补充入队。', 'NONE');
  if (code === 'BATCH_ITEM_FAILED') return failure('单据导出失败，请刷新逐项结果，按状态恢复原任务或新建批次。', 'NONE');
  if (code === 'BATCH_PDF_PAGE_LIMIT') return failure('合并页数超出上限，请减少单据后创建新批次。', 'NONE');
  if (code === 'BATCH_PDF_BYTES_LIMIT') return failure('合并文件大小超出上限，请减少单据或使用单张PDF。', 'NONE');
  if (code === 'BATCH_PDF_TIMEOUT') return failure('合并处理超时，请减少单据后重试。', 'NONE');
  return failure('报表处理未完成，请保留当前操作并核对状态后重试。', 'NONE');
}
