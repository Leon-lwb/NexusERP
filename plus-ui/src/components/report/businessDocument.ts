/** 单号和往来单位仅作展示，授权及参数始终使用完整标识。 */
export interface BusinessDocumentRef {
  /** 完整字符串标识。 */
  id: string;
  /** 用户可识别的业务单号。 */
  code: string;
  /** 客户或供应商名称。 */
  counterparty?: string;
}

/** 已丢失精度的数字不能继续提交给业务接口。 */
export function exactBusinessId(value: string | number | undefined): string {
  return typeof value === 'string' ? value.trim() : Number.isSafeInteger(value) ? String(value) : '';
}

/** 按勾选顺序固定展示快照，只有匹配完整ID才采用附加字段。 */
export function captureBusinessDocuments(ids: (string | number)[], references: BusinessDocumentRef[] = []): BusinessDocumentRef[] {
  return ids.map((value) => {
    const id = exactBusinessId(value);
    const reference = references.find((entry) => entry.id === id);
    return { id, code: reference?.code || id, counterparty: reference?.counterparty };
  });
}

/** 已确认不可访问的单据不继续展示原单号、单位或标识。 */
export function blockedBatchItem(item: { state: string; error?: string | null }): boolean {
  return item.state === 'BLOCKED' || /DENIED|SCOPE_CHANGED/.test(item.error ?? '');
}

/** 根据服务端提交序号解析展示数据，缺失或撤权项不返回本地标签。 */
export function batchDocumentAt(
  items: { number: number; state: string; error?: string | null }[],
  documents: BusinessDocumentRef[] | undefined,
  number: number
): BusinessDocumentRef | undefined {
  const item = items.find((entry) => entry.number === number);
  return item && !blockedBatchItem(item) ? documents?.[number] : undefined;
}
