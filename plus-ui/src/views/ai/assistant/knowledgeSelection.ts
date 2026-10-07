/** 只承接授权列表的明确知识库；选库本身不表示检索已经命中。 */
export interface ChatKnowledgeChoice {
  id: string;
  label: string;
}

/** 实体ID不可由失真的JS数字猜回；兼容安全范围内的历史小ID。 */
export function normalizeChatKnowledgeId(value: unknown): string | undefined {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value > 0 ? String(value) : undefined;
  }
  if (typeof value !== 'string' || !/^[1-9]\d{0,18}$/.test(value)) return undefined;
  return BigInt(value) <= 9223372036854775807n ? value : undefined;
}

/** 仅显示启用且索引就绪的授权结果，不把未知状态或重复身份自动当可用。 */
export function chatKnowledgeChoices(rows: unknown): ChatKnowledgeChoice[] {
  if (!Array.isArray(rows)) return [];
  const choices = new Map<string, ChatKnowledgeChoice>();
  const duplicate = new Set<string>();
  const seenIds = new Set<string>();
  for (const raw of rows) {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) continue;
    const row = raw as Record<string, unknown>;
    const id = normalizeChatKnowledgeId(row.id);
    if (!id) continue;
    // 身份去重先于状态和名称筛选，避免同一身份因记录顺序不同被误当作可用。
    if (seenIds.has(id)) duplicate.add(id);
    seenIds.add(id);
    if (row.status !== '0' || row.vectorIndexStatus !== 'READY') continue;
    if (typeof row.knowledgeName !== 'string' || !row.knowledgeName.trim()) continue;
    choices.set(id, { id, label: row.knowledgeName });
  }
  return [...choices.values()].filter((choice) => !duplicate.has(choice.id));
}

/** 知识库模式必须明确选择当前可用项；普通聊天和Agent绝不夹带知识库范围。 */
export function prepareChatKnowledgeRequest(
  mode: string,
  selectedId: unknown,
  choices: ChatKnowledgeChoice[],
  state: { loading: boolean; error: string; canList: boolean }
): { knowledgeId?: string; error?: string } {
  if (mode !== 'rag') return {};
  if (!state.canList) return { error: '当前账号没有知识库列表权限，请联系管理员授权' };
  if (state.loading) return { error: '知识库列表正在加载，请稍后发送' };
  if (state.error) return { error: '知识库加载失败，请重试后再发送' };
  const knowledgeId = normalizeChatKnowledgeId(selectedId);
  if (!knowledgeId || !choices.some((choice) => choice.id === knowledgeId)) {
    return { error: '请先选择一个启用且索引就绪的知识库' };
  }
  return { knowledgeId };
}
