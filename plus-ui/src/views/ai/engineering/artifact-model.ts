import type { DrcStatus, DrcSummary, EngineeringArtifact, EngineeringArtifactType, EngineeringManifest } from '@/api/ai/engineering';

export const MAX_ARTIFACT_BYTES = 100 * 1024 * 1024;
export const MAX_REPORT_ROWS = 10000;
const filenames = new WeakMap<Blob, string>();
/** 保存认证文件响应提供的文件名，不使用正文决定下载行为。 */
export function rememberArtifactFileName(blob: Blob, name: string): void {
  filenames.set(blob, name);
}
/** 只从当前清单筛选指定类型，缺产物返回空集合。 */
export function artifactsOfType(manifest: Pick<EngineeringManifest, 'artifacts'> | undefined, type: EngineeringArtifactType): EngineeringArtifact[] {
  return manifest?.artifacts?.filter((item) => item.type === type) || [];
}
/** 大小校验发生在任何内容请求之前。 */
export function validateArtifactSize(artifact: EngineeringArtifact): void {
  if (!Number.isSafeInteger(artifact.size) || artifact.size < 0 || artifact.size > MAX_ARTIFACT_BYTES)
    throw new Error('产物文件过大或大小无效，单个文件最多 100 MiB');
}
/** 识别伪装为 Blob 的 R 错误信封，403 不回退公共链接。 */
export async function assertArtifactBlob(blob: Blob): Promise<void> {
  if (!(blob instanceof Blob)) throw new Error('产物响应不是有效文件');
  if (blob.size > MAX_ARTIFACT_BYTES) throw new Error('产物文件过大，超过 100 MiB');
  const prefix = await blob.slice(0, 512).text();
  if (!/json/i.test(blob.type) && !prefix.trimStart().startsWith('{')) return;
  if (blob.size > 1024 * 1024) return;
  let body: { code?: unknown; msg?: string; traceId?: string; data?: { traceId?: string } };
  try {
    body = JSON.parse(await blob.text());
  } catch {
    return;
  }
  if (typeof body.code === 'number' && body.code !== 200)
    throw Object.assign(new Error(body.msg || '无权读取产物'), { status: body.code, traceId: body.traceId || body.data?.traceId });
}
/** 通过注入的认证内容读取器下载，支持切换任务时中止。 */
export async function readArtifact(
  artifact: EngineeringArtifact,
  fetcher: (artifactId: string, signal?: AbortSignal) => Promise<Blob>,
  signal?: AbortSignal
): Promise<Blob> {
  validateArtifactSize(artifact);
  signal?.throwIfAborted();
  const blob = await fetcher(artifact.artifactId, signal);
  signal?.throwIfAborted();
  await assertArtifactBlob(blob);
  return blob;
}
/** PNG 预览限制签名、IHDR 和解码像素，避免以 SVG 冒充。 */
export async function validatePng(blob: Blob): Promise<{ width: number; height: number }> {
  const bytes = new Uint8Array(await blob.slice(0, 24).arrayBuffer());
  if (bytes.length < 24 || ![137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82].every((value, index) => bytes[index] === value))
    throw new Error('线路预览不是有效 PNG 文件');
  const view = new DataView(bytes.buffer);
  const width = view.getUint32(16);
  const height = view.getUint32(20);
  if (!width || !height || width * height > 32000000) throw new Error('线路预览像素超过 32M 限制');
  return { width, height };
}
export interface BomRow {
  designator: string | null;
  value: string | null;
  footprint: string | null;
  manufacturerPartNumber: string | null;
  supplierPartNumber: string | null;
  quantity: string | null;
}
export interface BomReport {
  rows: BomRow[];
  truncated: boolean;
}
export interface DrcViolation {
  severity: string;
  message: string;
  rule?: string;
  designator?: string;
  x?: string;
  y?: string;
}
export interface DrcReport extends DrcSummary {
  violations: DrcViolation[];
  truncated: boolean;
}
/** 只接收规范文本或空值，数量不强制转换为浮点数。 */
function textOrNull(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}
/** 解析规范 BOM，保留空值和十进制字符串，限制展示行数。 */
export async function readBom(blob: Blob): Promise<BomReport> {
  await assertArtifactBlob(blob);
  return parseBomReport(JSON.parse(await blob.text()));
}
/** 接收 report 接口的规范对象，不为展示下载、解码原始大文件。 */
export function parseBomReport(parsed: unknown): BomReport {
  const report = parsed as { bom?: unknown } | null;
  const data = (report?.bom ?? parsed) as { rows?: unknown; items?: unknown; truncated?: unknown };
  if (!data || typeof data !== 'object') throw new Error('BOM 报告格式无效');
  const rows = Array.isArray(data) ? data : (data.rows ?? data.items);
  if (!Array.isArray(rows)) throw new Error('BOM 报告格式无效，缺少条目列表');
  return {
    rows: rows.slice(0, MAX_REPORT_ROWS).map((row) => {
      if (!row || typeof row !== 'object') throw new Error('BOM 条目格式无效');
      return {
        designator: textOrNull(row.designator),
        value: textOrNull(row.value),
        footprint: textOrNull(row.footprint),
        manufacturerPartNumber: textOrNull(row.manufacturerPartNumber),
        supplierPartNumber: textOrNull(row.supplierPartNumber),
        quantity: textOrNull(row.quantity)
      };
    }),
    truncated: data.truncated === true || rows.length > MAX_REPORT_ROWS
  };
}
/** DRC 必须明确声明状态，空列表不能推断通过。 */
export async function readDrc(blob: Blob): Promise<DrcReport> {
  await assertArtifactBlob(blob);
  return parseDrcReport(JSON.parse(await blob.text()));
}
/** 状态与明细来自服务端规范摘要，原文仍可独立下载。 */
export function parseDrcReport(parsed: unknown): DrcReport {
  const report = parsed as { drc?: unknown } | null;
  const data = (report?.drc ?? parsed) as {
    status?: DrcStatus;
    violations?: unknown;
    issues?: unknown;
    items?: unknown;
    errorCount?: number;
    warningCount?: number;
    message?: unknown;
    truncated?: unknown;
  };
  if (!data || typeof data !== 'object') throw new Error('DRC 报告格式无效');
  if (!['PASS', 'VIOLATIONS', 'UNAVAILABLE', 'NOT_RUN'].includes(data.status)) throw new Error('DRC 报告缺少有效状态');
  const rows = data.violations ?? data.issues ?? data.items ?? [];
  if (!Array.isArray(rows)) throw new Error('DRC 违规条目格式无效');
  return {
    status: data.status as DrcStatus,
    errorCount: data.errorCount,
    warningCount: data.warningCount,
    message: textOrNull(data.message) || undefined,
    violations: rows.slice(0, MAX_REPORT_ROWS).map((row) => ({
      severity: textOrNull(row.severity) || 'UNKNOWN',
      message: textOrNull(row.message) || '未提供说明',
      rule: textOrNull(row.rule) || undefined,
      designator: textOrNull(row.designator) || undefined,
      x: textOrNull(row.x) || undefined,
      y: textOrNull(row.y) || undefined
    })),
    truncated: data.truncated === true || rows.length > MAX_REPORT_ROWS
  };
}
type BlobUrlApi = Pick<typeof URL, 'createObjectURL' | 'revokeObjectURL'>;
/** 单个可替换预览资源，过期响应不分配 URL，释放操作幂等。 */
export class ArtifactBlobSlot {
  url?: string;
  private generation = 0;
  private controller?: AbortController;
  constructor(private urls: BlobUrlApi = URL) {}
  async load(fetcher: (signal: AbortSignal) => Promise<Blob>): Promise<string | undefined> {
    this.dispose();
    const generation = this.generation;
    const controller = new AbortController();
    this.controller = controller;
    const blob = await fetcher(controller.signal);
    if (controller.signal.aborted || generation !== this.generation) return undefined;
    this.url = this.urls.createObjectURL(blob);
    return this.url;
  }
  dispose(): void {
    this.generation++;
    this.controller?.abort();
    if (this.url) this.urls.revokeObjectURL(this.url);
    this.url = undefined;
  }
}
/** 授权 Blob 下载只使用响应/清单文件名，正文始终作为文件保存。 */
export async function saveArtifactBlob(
  blob: Blob,
  artifact: EngineeringArtifact,
  adapter?: {
    createObjectURL: (blob: Blob) => string;
    revokeObjectURL: (url: string) => void;
    click: (url: string, name: string) => void;
    defer: (fn: () => void) => void;
  }
): Promise<void> {
  await assertArtifactBlob(blob);
  const io = adapter || {
    createObjectURL: (value: Blob) => URL.createObjectURL(value),
    revokeObjectURL: (url: string) => URL.revokeObjectURL(url),
    click: (url: string, name: string) => {
      const link = document.createElement('a');
      link.href = url;
      link.download = name;
      document.body.appendChild(link);
      link.click();
      link.remove();
    },
    defer: (fn: () => void) => {
      setTimeout(fn, 1000);
    }
  };
  const name = (filenames.get(blob) || artifact.fileName || artifact.relativePath.split('/').at(-1) || '工程产物').replace(
    /[\\/\u0000-\u001f]/g,
    '_'
  );
  const url = io.createObjectURL(blob);
  try {
    io.click(url, name);
  } finally {
    io.defer(() => io.revokeObjectURL(url));
  }
}
