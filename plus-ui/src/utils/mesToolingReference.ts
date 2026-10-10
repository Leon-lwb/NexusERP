/** MES 工装引用的最小展示字段。 */
export interface MesToolingReference {
  toolingId: string | number;
  toolingCode: string;
  toolingName: string;
}

/** 以字符串 ID 为键，避免雪花 ID 在不同响应类型之间匹配失败。 */
export type MesToolingReferenceMap = Record<string, Pick<MesToolingReference, 'toolingCode' | 'toolingName'>>;

/**
 * 构建工装引用映射。
 *
 * @param rows 工装台账列表
 * @returns 可供计划、保养记录和使用记录复用的引用映射
 */
export function buildToolingReferenceMap(rows: MesToolingReference[]): MesToolingReferenceMap {
  return rows.reduce<MesToolingReferenceMap>((result, row) => {
    if (row?.toolingId == null || !row.toolingCode || !row.toolingName) return result;
    result[String(row.toolingId)] = { toolingCode: row.toolingCode, toolingName: row.toolingName };
    return result;
  }, {});
}

/**
 * 格式化工装引用；映射未加载或主数据缺失时保留 ID 便于追溯。
 *
 * @param references 工装引用映射
 * @param toolingId 工装 ID
 * @returns “工装编码 · 工装名称”或可追溯兜底文本
 */
export function formatToolingReference(references: MesToolingReferenceMap, toolingId: string | number | bigint | null | undefined): string {
  const key = toolingId == null ? '' : String(toolingId);
  const reference = references[key];
  return reference ? `${reference.toolingCode} · ${reference.toolingName}` : `工装 ${key || '-'}`;
}
