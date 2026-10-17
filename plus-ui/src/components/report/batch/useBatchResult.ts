import { ref, computed, watch, onUnmounted, type Ref } from 'vue';
import { retryExport, listExportJobs } from '@/api/report';
import { batchItems, startBatch, batchPdf, type BatchItem } from '@/api/report/batch';
import { readReportFailure } from '@/api/report/errors';
import type { ReportResource } from '@/api/report/query';
import type { ExportJob } from '@/types/report';
import { useUserStore } from '@/store/modules/user';
import { checkPermi } from '@/utils/permission';
import { RequestGate } from '@/views/report/model';

/** 批次逐项结果复用现有任务状态，不重新查询业务或创建批次。 */
export function useBatchResult(batches: Ref<ReportResource[]>, active: Ref<boolean>, creating: Ref<boolean> = ref(false)) {
  const userStore = useUserStore();
  const selected = ref('');
  const items = ref<(BatchItem & Partial<Pick<ExportJob, 'phase' | 'processedRows' | 'totalRows'>>)[]>([]);
  const detailLoading = ref(false);
  const detailFailed = ref(false);
  const detailError = ref('');
  const writeBusy = ref(false);
  const pdfOpen = ref(false);
  const now = ref(Date.now());
  const canList = computed(() => checkPermi(['report:batch:list']));
  const canInspect = computed(() => active.value && canList.value && checkPermi(['report:runtime:preview']));
  const canExport = computed(() => checkPermi(['report:runtime:export']));
  const canStart = computed(() => canList.value && canExport.value && checkPermi(['report:batch:manage']) && checkPermi(['report:runtime:preview']));
  const isOwner = computed(
    () => !!userStore.userId && String(batches.value.find((entry) => entry.id === selected.value)?.owner ?? '') === String(userStore.userId)
  );
  const canReadPdf = computed(() => canInspect.value && canExport.value && isOwner.value);
  const expired = computed(() => items.value.some((item) => !!item.expires && Date.parse(item.expires) <= now.value));
  const allSucceeded = computed(
    () =>
      items.value.length > 0 &&
      items.value.every((item) => item.state === 'SUCCEEDED' && !!item.jobId && (!item.expires || Date.parse(item.expires) > now.value))
  );
  const busy = computed(() => creating.value || writeBusy.value);
  const canDeliverPdf = computed(() => canReadPdf.value && allSucceeded.value);
  const pdfSourceId = computed(() =>
    JSON.stringify([selected.value, ...items.value.map((item) => [item.number, item.jobId, item.state, item.expires])])
  );
  const gate = new RequestGate();
  const writeGate = new RequestGate();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let expiryTimer: ReturnType<typeof setTimeout> | undefined;

  /** 暂停当前批次的观察，关闭抽屉不改变后端任务。 */
  function pause() {
    if (timer) clearTimeout(timer);
    timer = undefined;
  }
  /** 到期立即关闭文件入口，最终领取仍由服务端判定。 */
  function scheduleExpiry() {
    if (expiryTimer) clearTimeout(expiryTimer);
    expiryTimer = undefined;
    const future = items.value.map((item) => Date.parse(item.expires ?? '')).filter((time) => Number.isFinite(time) && time > now.value);
    if (!active.value || !future.length) return;
    expiryTimer = setTimeout(
      () => {
        now.value = Date.now();
        if (expired.value) pdfOpen.value = false;
        scheduleExpiry();
      },
      Math.min(Math.max(1, Math.min(...future) - Date.now()), 2147483647)
    );
  }
  /** 安全刷新各项状态，权限失败清除原结果。 */
  async function refreshItems(id: string, quiet = false) {
    if (!canInspect.value || selected.value !== id || (quiet && detailLoading.value)) return;
    pause();
    const ticket = gate.begin();
    detailLoading.value = true;
    try {
      const [response, jobs] = await Promise.all([
        batchItems(id),
        canExport.value && isOwner.value ? listExportJobs() : Promise.resolve({ data: [] })
      ]);
      if (!gate.current(ticket) || selected.value !== id || !active.value) return;
      items.value = response.data.map((item) => {
        const job = jobs.data.find((entry) => entry.id === item.jobId);
        return { ...item, phase: job?.phase, processedRows: job?.processedRows, totalRows: job?.totalRows };
      });
      now.value = Date.now();
      detailFailed.value = false;
      detailError.value = '';
      scheduleExpiry();
      if (items.value.some((item) => ['QUEUED', 'RUNNING', 'CANCEL_REQUESTED'].includes(item.state)))
        timer = setTimeout(() => {
          void refreshItems(id, true);
        }, 2500);
    } catch (error) {
      const failure = await readReportFailure(error);
      if (gate.current(ticket)) {
        items.value = [];
        detailFailed.value = true;
        detailError.value = failure.message;
        pdfOpen.value = false;
        scheduleExpiry();
      }
    } finally {
      if (gate.current(ticket)) detailLoading.value = false;
    }
  }
  /** 切换批次先清空文件和旧状态，迟到响应无法越过围栏。 */
  async function open(id: string) {
    if (!canInspect.value) return;
    pause();
    gate.invalidate();
    writeGate.invalidate();
    writeBusy.value = false;
    pdfOpen.value = false;
    selected.value = id;
    items.value = [];
    detailFailed.value = false;
    detailError.value = '';
    scheduleExpiry();
    await refreshItems(id);
  }
  /** 没有原任务的可恢复项只允许补入队。 */
  function needsStart(item: BatchItem) {
    return !item.jobId && ['PENDING', 'FAILED'].includes(item.state) && !['SCOPE_DENIED', 'SCOPE_CHANGED'].includes(item.error ?? '');
  }
  /** 重试入队复用原批次，调用前重新核对当前所有者。 */
  async function start(id: string) {
    if (!active.value || busy.value || !canStart.value || selected.value !== id || !isOwner.value) return;
    const ticket = writeGate.begin();
    writeBusy.value = true;
    try {
      await startBatch(id);
      if (writeGate.current(ticket) && selected.value === id) await refreshItems(id);
    } catch (error) {
      const failure = await readReportFailure(error);
      if (writeGate.current(ticket)) detailError.value = failure.message;
    } finally {
      if (writeGate.current(ticket)) writeBusy.value = false;
    }
  }
  /** 使用显示的原任务轮次恢复，不更换快照或过期时间。 */
  async function retryItem(item: BatchItem) {
    if (!active.value || busy.value || !isOwner.value) return;
    if (needsStart(item)) {
      await start(selected.value);
      return;
    }
    if (
      !item.jobId ||
      !item.canRetry ||
      !canExport.value ||
      !checkPermi(['report:runtime:preview']) ||
      !Number.isInteger(item.retryRound) ||
      (item.expires && Date.parse(item.expires) <= Date.now())
    ) {
      detailError.value = '原任务不可恢复，请明确创建新批次';
      return;
    }
    const ticket = writeGate.begin();
    const id = selected.value;
    writeBusy.value = true;
    try {
      await retryExport(item.jobId, item.retryRound);
      if (writeGate.current(ticket) && selected.value === id) await refreshItems(id);
    } catch (error) {
      const failure = await readReportFailure(error);
      if (writeGate.current(ticket)) detailError.value = failure.message;
    } finally {
      if (writeGate.current(ticket)) writeBusy.value = false;
    }
  }
  /** 合并只领取现有全部成功文件，服务端复核权限、顺序和有效期。 */
  async function loadPdf(): Promise<Blob> {
    now.value = Date.now();
    if (!canDeliverPdf.value) throw new Error('批次尚不可领取，请核对失败、权限及有效期');
    return batchPdf(selected.value);
  }
  /** 立即停止本地轮询和写请求后续动作，清除旧文件入口。 */
  function stop() {
    pause();
    gate.invalidate();
    writeGate.invalidate();
    if (expiryTimer) clearTimeout(expiryTimer);
    expiryTimer = undefined;
    detailLoading.value = false;
    writeBusy.value = false;
    pdfOpen.value = false;
    items.value = [];
  }
  watch(
    active,
    (value) => {
      if (!value) stop();
      else if (selected.value) void refreshItems(selected.value);
    },
    { flush: 'sync' }
  );
  watch(
    canDeliverPdf,
    (value) => {
      if (!value) pdfOpen.value = false;
    },
    { flush: 'sync' }
  );
  watch(
    canInspect,
    (allowed) => {
      if (!allowed) {
        stop();
        if (!canList.value || !checkPermi(['report:runtime:preview'])) selected.value = '';
      }
    },
    { flush: 'sync' }
  );
  watch(
    canList,
    (value) => {
      if (!value) {
        stop();
        selected.value = '';
      }
    },
    { flush: 'sync' }
  );
  watch(
    () => [userStore.userId, userStore.tenantId],
    () => {
      stop();
      selected.value = '';
    },
    { flush: 'sync' }
  );
  onUnmounted(stop);
  return {
    selected,
    items,
    detailLoading,
    detailFailed,
    detailError,
    writeBusy,
    pdfOpen,
    canList,
    canExport,
    canStart,
    isOwner,
    canReadPdf,
    canDeliverPdf,
    pdfSourceId,
    allSucceeded,
    expired,
    busy,
    pause,
    refreshItems,
    open,
    needsStart,
    start,
    retryItem,
    loadPdf,
    stop
  };
}
