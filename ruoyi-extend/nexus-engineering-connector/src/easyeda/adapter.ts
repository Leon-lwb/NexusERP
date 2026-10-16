import { setTimeout as delay } from 'node:timers/promises';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { join, dirname, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import type { ConnectorConfig, TaskLease, LocalArtifact } from '../types.js';
import { taskDirectory, extractZip, zipProject, containedPath, ensureDirectory } from '../workspace.js';
import { Journal } from '../journal.js';
import { validateParameters, TEMPLATE_ID } from './capabilities.js';
import { EasyedaClient } from './mcp-client.js';
import { collectArtifacts, validateModelReferences } from '../artifacts/manifest.js';
/** 编辑器执行结果包含真实产物和诊断；任何结果都不构成生产发布。 */
export interface AdapterResult {
  artifacts: LocalArtifact[];
  drc?: any;
  diagnostic?: any;
  errors: string[];
  projectPath?: string;
}
/** 受控适配器：客户端版本不支持原生MCP时使用固定CLI动作，无任意脚本入口。 */
export class EasyedaAdapter {
  private client: EasyedaClient;
  constructor(
    private config: Pick<ConnectorConfig, 'workspaceRoot' | 'executable'>,
    client?: EasyedaClient
  ) {
    this.client = client ?? new EasyedaClient(config.executable);
  }
  /** 执行白名单任务，副作用前持久化日志；未完成步骤不会自动重放。 */
  async execute(task: TaskLease, signal?: AbortSignal, onStage: (stage: string) => Promise<void> = async () => {}): Promise<AdapterResult> {
    const actions = ['CHECK_CONNECTION', 'OPEN_PROJECT', 'CREATE_PCB_DRAFT', 'EXPORT_ARTIFACTS', 'RUN_DRC', 'SUBMIT_DRAFT'];
    if (!actions.includes(task.action)) throw new Error('不允许的工程动作');
    signal?.throwIfAborted();
    if (task.action === 'CHECK_CONNECTION')
      return { artifacts: [], diagnostic: { ...(await this.client.doctor(signal)), transportMode: 'CLI_COMPAT' }, errors: [] };
    const dir = await taskDirectory(this.config.workspaceRoot, task.taskId, task.attempt),
      journal = new Journal(dir);
    await journal.load();
    const project = join(dir, 'project');
    let output = join(project, 'exports');
    let projectPath: string;
    if (task.action === 'CREATE_PCB_DRAFT') {
      if (task.templateId !== TEMPLATE_ID || task.templateVersion !== '1.0.0') throw new Error('TEMPLATE_UNAVAILABLE');
      const templateDir = fileURLToPath(new URL('../../templates/temperature-humidity-demo/', import.meta.url));
      const manifest = JSON.parse(await readFile(join(templateDir, 'manifest.json'), 'utf8'));
      const zip = await readFile(join(templateDir, 'source.zip'));
      if (createHash('sha256').update(zip).digest('hex') !== manifest.sha256 || task.templateSha256 !== manifest.sha256)
        throw new Error('模板摘要不匹配');
      const parameters = validateParameters(task.parameters);
      if (journal.step('prepare') !== 'COMPLETED') {
        await journal.start('prepare');
        await mkdir(project, { recursive: true });
        await extractZip(zip, project);
        await journal.finish('prepare', {});
      }
      projectPath = await containedPath(project, manifest.projectFile);
      await onStage('GENERATING');
      signal?.throwIfAborted();
      if (journal.step('generate') !== 'COMPLETED') {
        if (journal.step('open') !== 'COMPLETED') {
          await journal.start('open');
          await journal.finish('open', { sessionId: await this.client.open(projectPath, signal) });
        }
        const sessionId = journal.result('open').sessionId;
        let ready = false;
        for (let i = 0; i < 30; i++) {
          signal?.throwIfAborted();
          try {
            ready = (await this.client.invoke(sessionId, 'inspect', {}, signal)).ready === true;
          } catch {
            /* 新窗口载入期间只重试无副作用的检查。 */
          }
          if (ready) break;
          await delay(1000, undefined, { signal });
        }
        if (!ready) throw new Error('EDITOR_NOT_READY');
        await journal.start('generate');
        const result = await this.client.invoke(sessionId, 'create-draft', parameters, signal);
        await journal.finish('generate', { sessionId, result });
      }
    } else {
      if (!/^\d{1,20}$/.test(task.parentTaskId ?? '')) throw new Error('缺少父任务引用');
      const parentRef = await containedPath(this.config.workspaceRoot, `references/${task.parentTaskId}.json`);
      const record = JSON.parse(await readFile(parentRef, 'utf8'));
      const rel = relative(this.config.workspaceRoot, record.projectPath);
      if (rel.startsWith('..') || rel.startsWith(sep)) throw new Error('工程路径逃逸');
      projectPath = await containedPath(this.config.workspaceRoot, rel.split(sep).join('/'));
      output = join(dirname(projectPath), 'exports', `${task.taskId}-${task.attempt}`);
      if (journal.step('open') !== 'COMPLETED') {
        await journal.start('open');
        await journal.finish('open', { sessionId: await this.client.open(projectPath, signal) });
      }
    }
    const sessionId = (journal.result('generate') ?? journal.result('open')).sessionId;
    const refs = await ensureDirectory(this.config.workspaceRoot, 'references');
    const refPath = await containedPath(refs, task.taskId + '.json');
    await writeFile(refPath, JSON.stringify({ projectPath, sessionId }));
    if (task.action === 'OPEN_PROJECT') return { artifacts: [], errors: [], projectPath, diagnostic: { opened: true, transportMode: 'CLI_COMPAT' } };
    output = await ensureDirectory(this.config.workspaceRoot, relative(this.config.workspaceRoot, output).split(sep).join('/'));
    const errors: string[] = [];
    let drc: any;
    const exports = task.action === 'RUN_DRC' ? ['drc'] : ['drc', 'bom', 'png', 'obj', 'netlist'];
    await ensureDirectory(output, 'preview');
    await ensureDirectory(output, 'reports');
    await ensureDirectory(output, 'model');
    for (const kind of exports) {
      signal?.throwIfAborted();
      await onStage(kind === 'drc' ? 'CHECKING_DRC' : 'EXPORTING');
      signal?.throwIfAborted();
      const step = 'export-' + kind;
      if (journal.step(step) === 'COMPLETED') {
        if (kind === 'drc') drc = journal.result(step).value;
        continue;
      }
      try {
        await journal.start(step);
        const path =
          kind === 'png' ? join(output, 'preview/top.png') : kind === 'obj' ? join(output, 'model.zip') : join(output, 'reports', kind + '.net');
        await containedPath(this.config.workspaceRoot, relative(this.config.workspaceRoot, path).split(sep).join('/'));
        signal?.throwIfAborted();
        const result = await this.client.invoke(sessionId, 'export-artifacts', { kind, output: path.replaceAll('\\', '/') }, signal);
        signal?.throwIfAborted();
        if (kind === 'bom' || kind === 'drc') {
          await writeFile(join(output, 'reports', kind + '.json'), JSON.stringify(result, null, 2));
          if (kind === 'drc') drc = result;
        }
        if (kind === 'obj') await extractZip(await readFile(path), join(output, 'model'));
        await journal.finish(step, { value: kind === 'drc' ? result : undefined });
      } catch (e) {
        signal?.throwIfAborted();
        errors.push(`${kind}:${(e as NodeJS.ErrnoException).code ?? 'EXPORT_FAILED'}`);
        if (kind === 'drc') drc = { status: 'UNAVAILABLE', errorCount: 0, warningCount: 0, items: [] };
      }
    }
    if (task.action !== 'RUN_DRC') {
      signal?.throwIfAborted();
      const sourceDest = await containedPath(output, 'source.zip');
      await writeFile(sourceDest, await zipProject(dirname(projectPath)));
    }
    const artifacts = await collectArtifacts(output);
    errors.push(...(await validateModelReferences(artifacts, { allowMissingMaterials: true })));
    return { artifacts, drc, errors, projectPath };
  }
}
