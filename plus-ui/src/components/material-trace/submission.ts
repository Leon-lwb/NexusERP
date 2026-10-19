import { submissionNotice } from './model';

/** 原业务完成按钮统一按提交状态提示，普通数量模式保留既有文案。 */
export function showTraceSubmission(
  modal: { msgSuccess(message: string): unknown; msgWarning(message: string): unknown } | undefined,
  response: unknown,
  fallback: string
) {
  const notice = submissionNotice(response, fallback);
  if (notice.type === 'warning') modal?.msgWarning(notice.message);
  else modal?.msgSuccess(notice.message);
  return notice;
}
