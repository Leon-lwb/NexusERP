import { computed, onActivated, onDeactivated, onMounted, onUnmounted, reactive, ref, watch, type Ref } from 'vue';
import { checkPermi } from '@/utils/permission';
import { getEngineeringTask, listEngineeringDevices, listEngineeringTasks, type EngineeringDevice, type EngineeringTask } from '@/api/ai/engineering';
import { ENGINEERING_ENABLED } from '@/views/ai/engineering/engineering-config';
import { engineeringError, shouldPoll } from '@/views/ai/engineering/model';
import { useEngineeringPolling } from '@/views/ai/engineering/useEngineeringPolling';
import {
  acceptEngineeringTaskEvent,
  EngineeringChatRequests,
  mergeEngineeringTasks,
  parseEngineeringTaskEvent,
  type EngineeringTaskEvent
} from './engineering-events';

/** 在会话之外持久化的真实工程任务入口，独立于 AI 文本流生命周期。 */
export function useEngineeringChat(sessionId: Ref<string | null>, visible: Ref<boolean> = ref(true)) {
  const componentActive = ref(true);
  const selectedDeviceId = ref('');
  const devices = ref<EngineeringDevice[]>([]);
  const tasks = ref<EngineeringTask[]>([]);
  const deviceLoading = ref(false);
  const deviceError = ref('');
  const taskError = ref('');
  const activeRequestId = ref('');
  const requests = new EngineeringChatRequests();
  const queued: EngineeringTaskEvent[] = [];
  const enabled = computed(() => ENGINEERING_ENABLED && checkPermi(['ai:engineering:query', 'ai:engineering:task']));
  const canSelect = computed(() => enabled.value && checkPermi(['ai:engineering:device']) && checkPermi(['ai:engineering:task']));
  let deviceController: AbortController | undefined;
  let historyVersion = 0;
  let eventController = new AbortController();
  let pending: { text: string; deviceId: string; intent: string; confirmed: boolean } | undefined;
  /** 设备下拉只列真实已授权设备，能力不足/离线状态原样显示。 */
  async function loadDevices() {
    if (!canSelect.value || !visible.value || !componentActive.value || document.hidden) return;
    deviceController?.abort();
    const controller = new AbortController();
    deviceController = controller;
    deviceLoading.value = true;
    deviceError.value = '';
    try {
      const result = await listEngineeringDevices(controller.signal);
      if (!controller.signal.aborted) {
        devices.value = result.data || [];
        if (!devices.value.some((device) => device.deviceId === selectedDeviceId.value)) selectedDeviceId.value = '';
      }
    } catch (cause) {
      if (!controller.signal.aborted) {
        devices.value = [];
        selectedDeviceId.value = '';
        deviceError.value = engineeringError(cause).message;
      }
    } finally {
      if (!controller.signal.aborted) deviceLoading.value = false;
    }
  }
  /** 设备只由用户主动选择，失去授权或列表已变化时清空选择。 */
  function selectDevice(deviceId: string) {
    selectedDeviceId.value = devices.value.some((device) => device.deviceId === deviceId && device.status !== 'REVOKED') ? deviceId : '';
  }
  /** 同一条未确认请求可重发，成功后的下一条消息拥有新请求标识。 */
  function prepare(text: string): { engineeringDeviceId?: string; engineeringRequestId?: string } {
    if (!canSelect.value || !selectedDeviceId.value) {
      activeRequestId.value = '';
      return {};
    }
    if (!pending || pending.confirmed || pending.text !== text || pending.deviceId !== selectedDeviceId.value)
      pending = { text, deviceId: selectedDeviceId.value, intent: crypto.randomUUID(), confirmed: false };
    const fields = requests.prepare(selectedDeviceId.value, pending.intent);
    activeRequestId.value = fields.engineeringRequestId || '';
    return fields;
  }
  /** 仅将 HTTP 回执标为已确认，不把它解释成工程创建成功。 */
  function sent() {
    if (pending) pending.confirmed = true;
  }
  /** 历史恢复包含所有服务端分页并按 taskId 去重。 */
  async function loadTasks(signal: AbortSignal): Promise<boolean> {
    if (!enabled.value || !visible.value || !componentActive.value || !sessionId.value || !checkPermi(['ai:engineering:query'])) return false;
    const version = historyVersion;
    const session = sessionId.value;
    const rows: EngineeringTask[] = [];
    let pageNum = 1;
    let total = 0;
    do {
      const result = await listEngineeringTasks({ sessionId: session, pageNum, pageSize: 100 }, signal);
      signal.throwIfAborted();
      rows.push(...(result.rows || []));
      total = result.total || 0;
      pageNum++;
      if (!result.rows?.length) break;
    } while (rows.length < total);
    if (version !== historyVersion || session !== sessionId.value || !visible.value) return false;
    tasks.value = mergeEngineeringTasks([], rows, session);
    taskError.value = '';
    return tasks.value.some((task) => shouldPoll(task.state));
  }
  const { refresh, stop } = useEngineeringPolling(loadTasks, (cause) => {
    taskError.value = engineeringError(cause).message;
  });
  /** 类型化事件也要重新读任务，状态与归属最终由服务端确认。 */
  async function applyEvent(event: EngineeringTaskEvent) {
    if (!acceptEngineeringTaskEvent(event, { sessionId: sessionId.value, requestId: activeRequestId.value })) return;
    const version = historyVersion;
    const controller = eventController;
    try {
      const result = await getEngineeringTask(event.taskId, controller.signal);
      if (
        !controller.signal.aborted &&
        version === historyVersion &&
        acceptEngineeringTaskEvent(event, { sessionId: sessionId.value, requestId: activeRequestId.value }) &&
        result.data.taskId === event.taskId
      ) {
        tasks.value = mergeEngineeringTasks(tasks.value, [result.data], sessionId.value || '');
        await refresh();
      }
    } catch (cause) {
      if (!controller.signal.aborted && version === historyVersion) taskError.value = engineeringError(cause).message;
    }
  }
  /** 新会话 ID 尚未回执时暂存事件，绑定真实会话后才允许查询。 */
  function onEngineeringEvent(event: Event) {
    if (!enabled.value || !visible.value || !componentActive.value || document.hidden) return;
    const detail = (event as CustomEvent).detail;
    if (detail?.type === 'AI_DONE' || detail?.type === 'AI_ERROR') {
      if (sessionId.value) void refresh();
      return;
    }
    const typed = parseEngineeringTaskEvent((event as CustomEvent).detail);
    if (!typed || typed.requestId !== activeRequestId.value) return;
    if (!sessionId.value) {
      if (queued.length < 32) queued.push(typed);
      return;
    }
    void applyEvent(typed);
  }
  watch(sessionId, (session, previous) => {
    historyVersion++;
    eventController.abort();
    eventController = new AbortController();
    tasks.value = [];
    taskError.value = '';
    if (previous && previous !== session) {
      activeRequestId.value = '';
      pending = undefined;
      requests.clear();
      queued.length = 0;
    }
    if (!session) {
      activeRequestId.value = '';
      pending = undefined;
      requests.clear();
      queued.length = 0;
      stop();
      return;
    }
    void refresh();
    for (const event of queued.splice(0)) void applyEvent(event);
  });
  watch(visible, (shown) => {
    if (shown) {
      void loadDevices();
      void refresh();
    } else {
      stop();
      eventController.abort();
      eventController = new AbortController();
      deviceController?.abort();
    }
  });
  /** 隐藏或离开页面时中止额外事件读取，返回后由持久化任务恢复。 */
  function suspendRequests() {
    eventController.abort();
    eventController = new AbortController();
    deviceController?.abort();
  }
  function visibilityChanged() {
    if (document.hidden) suspendRequests();
  }
  onActivated(() => {
    componentActive.value = true;
  });
  onDeactivated(() => {
    componentActive.value = false;
    suspendRequests();
  });
  onMounted(() => {
    document.addEventListener('visibilitychange', visibilityChanged);
    window.addEventListener('ai-sse-message', onEngineeringEvent);
    void loadDevices();
  });
  onUnmounted(() => {
    historyVersion++;
    stop();
    eventController.abort();
    deviceController?.abort();
    queued.length = 0;
    window.removeEventListener('ai-sse-message', onEngineeringEvent);
    document.removeEventListener('visibilitychange', visibilityChanged);
  });
  return reactive({
    enabled,
    canSelect,
    selectedDeviceId,
    selectDevice,
    devices,
    tasks,
    deviceLoading,
    deviceError,
    taskError,
    activeRequestId,
    loadDevices,
    refresh,
    prepare,
    sent
  });
}
export type EngineeringChatController = ReturnType<typeof useEngineeringChat>;
