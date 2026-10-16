import type { EngineeringArtifact, EngineeringManifest } from '@/api/ai/engineering';
import { validateArtifactSize, assertArtifactBlob, validatePng } from '@/views/ai/engineering/artifact-model';
import { assertModelTextBudget, modelTextLines } from './model-text';

/** 引用只允许当前清单内的相对路径，拒绝所有协议、编码逃逸和平台绝对路径。 */
export function safeModelPath(path: string): string {
  if (!path || /[\\:%?#\u0000-\u001f]/.test(path) || path.startsWith('/') || path.split('/').includes('..'))
    throw new Error('模型引用路径无效，禁止外部或上级目录资源');
  const normalized = path
    .split('/')
    .filter((part) => part !== '.' && part !== '')
    .join('/');
  if (!normalized) throw new Error('模型引用路径为空');
  return normalized;
}
/** 提取与 MTLLoader 相同的纹理参数，额外校验剩余资源路径。 */
function texturePath(value: string): string {
  const items = value.trim().split(/\s+/);
  for (const [option, count] of [
    ['-bm', 2],
    ['-mm', 3],
    ['-s', 4],
    ['-o', 4]
  ] as const) {
    const index = items.indexOf(option);
    if (index >= 0) items.splice(index, count);
  }
  return safeModelPath(items.join(' '));
}
/** 当前任务、当前模型组独立持有的受控纹理资源池。 */
export class EngineeringModelResources {
  private paths = new Map<string, EngineeringArtifact>();
  private urls = new Map<string, string>();
  private aliases = new Map<string, string>();
  private controller = new AbortController();
  private disposed = false;
  private uniformMaterial = false;
  /** 本组材质不可用；与未声明材质区分，不能误报材质成功。 */
  materialMissing = false;
  constructor(
    private manifest: EngineeringManifest,
    private obj: EngineeringArtifact,
    private fetcher: (artifact: EngineeringArtifact, signal: AbortSignal) => Promise<Blob>,
    private urlApi: Pick<typeof URL, 'createObjectURL' | 'revokeObjectURL'> = URL
  ) {
    if (!obj.groupId || (obj.taskId && obj.taskId !== manifest.taskId)) throw new Error('模型与当前任务或产物组不匹配');
    const group = manifest.modelGroups?.find((item) => item.groupId === obj.groupId);
    this.uniformMaterial = group?.uniformMaterial === true;
    this.materialMissing = group?.status === 'DEGRADED' || !!group?.missingPaths?.length;
    for (const artifact of manifest.artifacts) {
      if (artifact.groupId !== obj.groupId || (artifact.taskId && artifact.taskId !== manifest.taskId)) continue;
      const path = safeModelPath(artifact.relativePath);
      if (this.paths.has(path)) throw new Error('模型清单存在重复路径');
      this.paths.set(path, artifact);
    }
    if (this.paths.get(safeModelPath(obj.relativePath))?.artifactId !== obj.artifactId) throw new Error('模型不在当前任务清单内');
  }
  /** 所有引用先验证路径，缺失降级也不能绕过外部资源校验。 */
  private referencePath(reference: string, fromPath: string): string {
    const relative = safeModelPath(reference);
    const source = safeModelPath(fromPath);
    const directory = source.includes('/') ? source.slice(0, source.lastIndexOf('/') + 1) : '';
    return directory + relative;
  }
  /** 从声明文件所在目录解析引用，绝不回退为外部 URL。 */
  resolveReference(reference: string, fromPath: string): EngineeringArtifact {
    const artifact = this.paths.get(this.referencePath(reference, fromPath));
    if (!artifact) throw new Error(`模型引用未命中当前任务产物组清单：${reference}`);
    return artifact;
  }
  /** 安全但缺失的组内 MTL 降级为统一材质，仍完整校验所有 OBJ 引用。 */
  materialArtifacts(objText: string): EngineeringArtifact[] {
    const artifacts = new Map<string, EngineeringArtifact>();
    for (const line of modelTextLines(objText)) {
      const match = /^\s*mtllib\s+(.+?)\s*$/.exec(line);
      if (!match) continue;
      const refs = match[1].trim().split(/\s+/);
      for (const ref of refs) {
        const artifact = this.paths.get(this.referencePath(ref, this.obj.relativePath));
        if (!artifact || artifact.type !== 'PCB_MODEL_MTL') {
          this.materialMissing = true;
          continue;
        }
        artifacts.set(artifact.relativePath, artifact);
      }
    }
    return this.uniformMaterial || this.materialMissing ? [] : [...artifacts.values()];
  }
  /** 完整预检全部引用后才请求纹理，重复引用复用同一 Blob URL。 */
  async prepare(objText: string, materialText?: string | Record<string, string>): Promise<string | undefined> {
    const materials = this.materialArtifacts(objText);
    if (!materialText || !materials.length) return undefined;
    if (typeof materialText === 'string' && materials.length !== 1) throw new Error('模型材质清单数量不匹配');
    const textures = new Map<string, EngineeringArtifact>();
    const rewritten: string[] = [];
    for (const material of materials) {
      const text = typeof materialText === 'string' ? materialText : materialText[material.relativePath];
      if (typeof text !== 'string') throw new Error('缺少清单中的材质内容');
      assertModelTextBudget(text.length, 'MTL');
      const lines = [...modelTextLines(text)].map((line) => {
        const match = /^\s*(map_\w+|bump|disp|norm|refl)\s+(.+?)\s*$/i.exec(line);
        if (!match) return line;
        const reference = texturePath(match[2]);
        const artifact = this.resolveReference(reference, material.relativePath);
        if (artifact.type !== 'PCB_MODEL_TEXTURE') throw new Error('纹理引用类型与清单不符');
        validateArtifactSize(artifact);
        textures.set(artifact.relativePath, artifact);
        this.aliases.set(reference, artifact.relativePath);
        return line.replace(reference, artifact.relativePath);
      });
      rewritten.push(lines.join('\n'));
    }
    for (const [path, artifact] of textures) {
      this.controller.signal.throwIfAborted();
      if (this.urls.has(path)) continue;
      const blob = await this.fetcher(artifact, this.controller.signal);
      this.controller.signal.throwIfAborted();
      await assertArtifactBlob(blob);
      await validateModelTexture(blob);
      this.controller.signal.throwIfAborted();
      this.urls.set(path, this.urlApi.createObjectURL(blob));
    }
    return rewritten.join('\n');
  }
  /** Three.js 的兜底加载被关闭，仅认可当前池内已经生成的 URL。 */
  urlModifier = (url: string): string => {
    if (this.disposed) throw new Error('模型资源已释放');
    if ([...this.urls.values()].includes(url)) return url;
    const path = safeModelPath(url);
    const blob = this.urls.get(path) || this.urls.get(this.aliases.get(path) || '');
    if (!blob) throw new Error('纹理不在当前任务的已授权清单内');
    return blob;
  };
  /** 中止在途请求并幂等回收所有资源 URL。 */
  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.controller.abort();
    for (const url of this.urls.values()) this.urlApi.revokeObjectURL(url);
    this.urls.clear();
    this.aliases.clear();
  }
}

/** 纹理只接收有实际栅格签名的图片，SVG/HTML 不进入浏览器加载器。 */
async function validateModelTexture(blob: Blob): Promise<void> {
  const bytes = new Uint8Array(await blob.slice(0, 16).arrayBuffer());
  const png = bytes[0] === 137 && bytes[1] === 80 && bytes[2] === 78 && bytes[3] === 71;
  const jpeg = bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
  const webp = String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF' && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP';
  if (!png && !jpeg && !webp) throw new Error('模型纹理仅支持有效的 PNG、JPEG 或 WebP 栅格图片');
  if (png) await validatePng(blob);
  if (typeof createImageBitmap === 'function') {
    let bitmap: ImageBitmap | undefined;
    try {
      bitmap = await createImageBitmap(blob);
      if (!bitmap.width || !bitmap.height || bitmap.width * bitmap.height > 32000000) throw new Error('纹理图片像素超过 32M 限制');
    } finally {
      bitmap?.close();
    }
  }
}
