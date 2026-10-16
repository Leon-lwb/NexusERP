import { readFile, writeFile, rename } from 'node:fs/promises';
import { join } from 'node:path';
/** 原子任务日志，记录副作用开始/完成及恢复材料；不保存租约或设备Token。 */
export class Journal {
  private value: Record<string, { state: 'STARTED' | 'COMPLETED'; result?: any }> = {};
  constructor(private directory: string) {}
  async load() {
    try {
      this.value = JSON.parse(await readFile(join(this.directory, 'journal.json'), 'utf8'));
    } catch (e) {
      if ((e as NodeJS.ErrnoException).code !== 'ENOENT') throw e;
    }
  }
  private async save() {
    const p = join(this.directory, 'journal.json');
    await writeFile(p + '.tmp', JSON.stringify(this.value));
    await rename(p + '.tmp', p);
  }
  /** 取得某步骤的持久化状态。 */
  step(name: string) {
    return this.value[name]?.state;
  }
  /** 取得已记录的工程会话、产物或摘要。 */
  result(name: string) {
    return this.value[name]?.result;
  }
  /** 副作用前写入开始；重启见到未完成项时不允许重放。 */
  async start(name: string) {
    if (this.value[name]?.state === 'STARTED') throw new Error('EXECUTION_UNCERTAIN');
    if (this.value[name]?.state === 'COMPLETED') return;
    this.value[name] = { state: 'STARTED' };
    await this.save();
  }
  /** 副作用成功后持久化回执。 */
  async finish(name: string, result: any) {
    this.value[name] = { state: 'COMPLETED', result };
    await this.save();
  }
}
