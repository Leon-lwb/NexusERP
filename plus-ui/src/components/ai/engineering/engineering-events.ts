import type { EngineeringStage, EngineeringState, EngineeringTask } from '@/api/ai/engineering';
import { engineeringId, ENGINEERING_STATES, ENGINEERING_STAGES } from '@/views/ai/engineering/model';

export interface EngineeringTaskEvent {
  sessionId: string;
  requestId: string;
  taskId: string;
  state: EngineeringState;
  stage?: EngineeringStage;
}
/** 工程事件只读取固定字段，不让工具文字或任意路径进入任务卡片。 */
export function parseEngineeringTaskEvent(detail: unknown): EngineeringTaskEvent | undefined {
  const envelope = detail as { type?: string; content?: string };
  if (envelope?.type !== 'AI_ENGINEERING_TASK' || typeof envelope.content !== 'string') return undefined;
  try {
    const data = JSON.parse(envelope.content);
    const taskId = engineeringId(data.taskId);
    const sessionId = engineeringId(data.sessionId);
    if (typeof data.requestId !== 'string' || !data.requestId || !Object.hasOwn(ENGINEERING_STATES, data.state)) return undefined;
    if (data.stage != null && !Object.hasOwn(ENGINEERING_STAGES, data.stage)) return undefined;
    return { taskId, sessionId, requestId: data.requestId, state: data.state, stage: data.stage ?? undefined };
  } catch {
    return undefined;
  }
}
/** 会话和本次请求必须同时匹配，旧事件不能修改当前卡片。 */
export function acceptEngineeringTaskEvent(
  event: EngineeringTaskEvent | undefined,
  context: { sessionId: string | null; requestId?: string }
): boolean {
  return !!event && !!context.sessionId && event.sessionId === context.sessionId && event.requestId === context.requestId;
}
/** 每条用户工程意图持有一个 UUID；同一意图重发继续使用原键。 */
export class EngineeringChatRequests {
  private requests = new Map<string, { engineeringDeviceId: string; engineeringRequestId: string }>();
  constructor(private uuid: () => string = () => crypto.randomUUID()) {}
  prepare(deviceId: string | undefined, intent: string): { engineeringDeviceId?: string; engineeringRequestId?: string } {
    if (!deviceId) return {};
    const id = engineeringId(deviceId);
    const key = `${id}:${intent}`;
    if (!this.requests.has(key)) this.requests.set(key, { engineeringDeviceId: id, engineeringRequestId: this.uuid() });
    return { ...this.requests.get(key) };
  }
  clear(): void {
    this.requests.clear();
  }
}
/** 历史只从服务端任务重建，按版本去重并隔离会话。 */
export function mergeEngineeringTasks(current: EngineeringTask[], incoming: EngineeringTask[], sessionId: string): EngineeringTask[] {
  const tasks = new Map<string, EngineeringTask>();
  for (const task of [...current, ...incoming]) {
    if (task.sessionId !== sessionId) continue;
    try {
      engineeringId(task.taskId);
    } catch {
      continue;
    }
    const previous = tasks.get(task.taskId);
    if (!previous || engineeringRevision(task.revision) >= engineeringRevision(previous.revision)) tasks.set(task.taskId, task);
  }
  return [...tasks.values()];
}

/** 修订号可能经 long 序列化器变为文本，比较时不使用字典序。 */
function engineeringRevision(value: number | string | undefined): bigint {
  if (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0) return BigInt(value);
  if (typeof value === 'string' && /^\d+$/.test(value)) return BigInt(value);
  return 0n;
}
