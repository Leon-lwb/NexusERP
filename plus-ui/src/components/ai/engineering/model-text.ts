/** 服务端允许归档更大的文件；浏览器预览独立限制文本和几何预算。 */
export const MAX_OBJ_PREVIEW_BYTES = 24 * 1024 * 1024;
export const MAX_MTL_PREVIEW_BYTES = 2 * 1024 * 1024;
export const MAX_MODEL_VERTICES = 2000000;
const MAX_MODEL_LINE_CHARS = 65536;

/** 在请求或解码完整文本之前检查声明/实际字节数，超限文件保留下载入口。 */
export function assertModelTextBudget(size: number, kind: 'OBJ' | 'MTL'): void {
  const limit = kind === 'OBJ' ? MAX_OBJ_PREVIEW_BYTES : MAX_MTL_PREVIEW_BYTES;
  if (!Number.isSafeInteger(size) || size < 0 || size > limit)
    throw new Error(`${kind} 文件超过浏览器预览预算（${kind === 'OBJ' ? '24' : '2'} MiB），请下载后在本地查看`);
}
/** 按行扫描有界文本，不对整个 OBJ split 产生第二份大数组。 */
export function* modelTextLines(text: string): Generator<string> {
  let offset = 0;
  while (offset < text.length) {
    let end = text.indexOf('\n', offset);
    if (end < 0) end = text.length;
    if (end - offset > MAX_MODEL_LINE_CHARS) throw new Error('模型文本单行过长，已停止预览');
    yield text.slice(offset, end).trim();
    offset = end + 1;
  }
}
/** 在 Three.js 分配几何之前限制原始顶点、索引展开和单行规模。 */
export function assertSafeObjText(text: string, maxVertices = MAX_MODEL_VERTICES): void {
  assertModelTextBudget(text.length, 'OBJ');
  // OBJLoader 会先拼接续行；本期与服务端一样拒绝，避免按物理行低估分配。
  if (/\\\r?\n/.test(text)) throw new Error('OBJ 暂不支持续行，请下载后在本地查看');
  const declarations = { v: 0, vn: 0, vt: 0 };
  let expanded = 0;
  for (const line of modelTextLines(text)) {
    const declaration = /^(v|vn|vt)\s/.exec(line)?.[1] as keyof typeof declarations | undefined;
    if (declaration && ++declarations[declaration] > maxVertices) throw new Error('OBJ 顶点数量超过预览限制');
    if (/^[flp]\s/.test(line)) {
      // 必须匹配当前 OBJLoader：面按空白分隔，线/点按单个空格分隔且保留空项。
      // 折线直接产生 count 个位置，不能按三角面或线段重复展开计算。
      const count = line
        .slice(1)
        .trim()
        .split(line[0] === 'f' ? /\s+/ : ' ').length;
      expanded += line[0] === 'f' ? Math.max(0, count - 2) * 3 : count;
      if (expanded > maxVertices) throw new Error('OBJ 面展开后的顶点数量超过预览限制');
    }
  }
}
/** 已授权 Blob 先检查实际大小，再读取有限文本；不解析超大完整字符串。 */
export async function readModelText(blob: Blob, kind: 'OBJ' | 'MTL', signal?: AbortSignal): Promise<string> {
  signal?.throwIfAborted();
  assertModelTextBudget(blob.size, kind);
  const text = await blob.text();
  signal?.throwIfAborted();
  if (kind === 'OBJ') assertSafeObjText(text);
  else {
    assertModelTextBudget(text.length, 'MTL');
    for (const _line of modelTextLines(text)) {
      /* 只验证单行预算。 */
    }
  }
  return text;
}
