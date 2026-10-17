import { ref, computed, watch, onUnmounted, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import { enqueueExport, listExportJobs, downloadExport, retryExport } from '@/api/report';
import { readReportFailure, type ReportFailure } from '@/api/report/errors';
import { createRequestKey } from '@/utils/requestKey';
import { useUserStore } from '@/store/modules/user';
import { checkPermi } from '@/utils/permission';
import { exportFormats, jobError, RequestGate } from '@/views/report/model';
import type { ReportRun, PageData, ExportJob, ExportFormat } from '@/types/report';

/** 打印及普通导出共享同一run的幂等键，生命周期独立于页面路由。 */
export function useReportPrint(
  run: Ref<ReportRun | undefined>,
  data: Ref<PageData | undefined>,
  active: Ref<boolean>,
  onAccessDenied?: (message: string) => void
) {
  const userStore = useUserStore();
  const printJob = ref<ExportJob>();
  const printing = ref(false);
  const exporting = ref(false);
  const printError = ref('');
  const pdfOpen = ref(false);
  const printOwner = ref('');
  const ownsPrint = computed(
    () => active.value && printOwner.value === String(userStore.userId) && !!printOwner.value && checkPermi(['report:runtime:export'])
  );
  const availableFormats = computed(() => exportFormats(run.value?.mode || 'LIVE', data.value?.total || 0));
  const formatLabels: Record<ExportFormat, string> = { PDF: 'PDF 打印文件', XLSX_DATA: '明细 Excel', XLSX_LAYOUT: '样式 Excel' };
  const keys = new Map<string, string>();
  const gate = new RequestGate();
  const exportGate = new RequestGate();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let polling = false;

  /** 终止本地文件入口和定时器，后端任务继续按既有队列执行。 */
  function suspendPrint() {
    gate.invalidate();
    exportGate.invalidate();
    if (timer) clearTimeout(timer);
    timer = undefined;
    polling = false;
    printing.value = false;
    exporting.value = false;
    pdfOpen.value = false;
    printJob.value = undefined;
    printOwner.value = '';
    printError.value = '';
  }
  /** 运行或身份切换时才释放幂等键，单独撤销导出权限保留原意图。 */
  function stopPrint() {
    suspendPrint();
    keys.clear();
  }
  /** 明确的依赖撤权同步通知运行方清除模板、数据及布局。 */
  function accessFailure(failure: ReportFailure): boolean {
    if (failure.action !== 'ACCESS') return false;
    suspendPrint();
    onAccessDenied?.(failure.message);
    printError.value = failure.message;
    return true;
  }
  /** 首次导出固定安全随机标识，网络失败继续使用相同标识。 */
  function keyFor(id: string, format: ExportFormat) {
    const signature = id + ':' + format;
    let key = keys.get(signature);
    if (!key) {
      key = createRequestKey();
      keys.set(signature, key);
    }
    return key;
  }
  /** 只观察当前任务，不再次查询业务数据。 */
  function trackPrint() {
    if (timer) clearTimeout(timer);
    timer = undefined;
    const job = printJob.value;
    if (!active.value || !job || !ownsPrint.value) return;
    if (job.state === 'SUCCEEDED') {
      printing.value = false;
      pdfOpen.value = true;
    } else if (['FAILED', 'CANCELLED', 'EXPIRED'].includes(job.state)) {
      printing.value = false;
      printError.value = jobError(job.error) || '原PDF任务不可继续，请核对后创建新运行';
    } else {
      printing.value = true;
      timer = setTimeout(() => {
        void pollPrint();
      }, 2500);
    }
  }
  /** 领取当前固定运行的PDF任务，已有成功文件直接复用。 */
  async function printPdf() {
    if (!active.value || !run.value || run.value.mode !== 'SNAPSHOT' || !checkPermi(['report:runtime:export'])) return;
    if (printJob.value) {
      if (printJob.value.state === 'SUCCEEDED') pdfOpen.value = ownsPrint.value;
      else await pollPrint();
      return;
    }
    if (printing.value) return;
    const origin = run.value.id;
    let key: string;
    try {
      key = keyFor(origin, 'PDF');
    } catch {
      printError.value = '当前浏览器缺少安全随机能力，请更新浏览器后重试';
      return;
    }
    const ticket = gate.begin();
    const owner = String(userStore.userId);
    printing.value = true;
    printError.value = '';
    try {
      const response = await enqueueExport(origin, 'PDF', key);
      if (!gate.current(ticket) || run.value?.id !== origin || !active.value || String(userStore.userId) !== owner) return;
      printJob.value = response.data;
      printOwner.value = owner;
      trackPrint();
    } catch (error) {
      const failure = await readReportFailure(error);
      if (gate.current(ticket)) {
        if (accessFailure(failure)) return;
        printing.value = false;
        printError.value = failure.action === 'NONE' ? 'PDF入队结果未确认，重试将复用同一个请求标识' : failure.message;
      }
    }
  }
  /** 网络读取失败只暂停观察，用户可继续刷新同一任务。 */
  async function pollPrint() {
    if (!active.value || polling || !printJob.value || !run.value || !ownsPrint.value) return;
    if (['SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED'].includes(printJob.value.state)) return;
    const ticket = gate.begin();
    const jobId = printJob.value.id;
    const origin = run.value.id;
    polling = true;
    if (timer) clearTimeout(timer);
    timer = undefined;
    try {
      const response = await listExportJobs();
      if (!gate.current(ticket) || run.value?.id !== origin || !ownsPrint.value) return;
      const job = response.data.find((entry) => entry.id === jobId && entry.runId === origin);
      if (!job) {
        printJob.value = undefined;
        pdfOpen.value = false;
        printing.value = false;
        printError.value = '原PDF任务已不可访问或已过期，请核对权限与状态';
        return;
      }
      printJob.value = job;
      printError.value = '';
      trackPrint();
    } catch (error) {
      const failure = await readReportFailure(error);
      if (gate.current(ticket)) {
        if (accessFailure(failure)) return;
        printing.value = false;
        printError.value = failure.action === 'NONE' ? '任务进度读取失败，请刷新同一任务的进度' : failure.message;
      }
    } finally {
      if (gate.current(ticket)) polling = false;
    }
  }
  /** 手动恢复只重试原任务及其显示轮次，禁止重建快照。 */
  async function retryPrint() {
    if (!active.value || !printJob.value?.canRetry || !ownsPrint.value || printing.value) return;
    const job = printJob.value;
    if (job.expires && Date.parse(job.expires) <= Date.now()) {
      printError.value = '原任务已过期，请重新运行';
      return;
    }
    const ticket = gate.begin();
    printing.value = true;
    try {
      await retryExport(job.id, job.retryRound ?? 0);
      if (!gate.current(ticket)) return;
      printJob.value = { ...job, state: 'QUEUED', canRetry: false };
      printError.value = '';
      trackPrint();
    } catch (error) {
      const failure = await readReportFailure(error);
      if (gate.current(ticket)) {
        if (accessFailure(failure)) return;
        printing.value = false;
        printError.value = failure.message;
      }
    }
  }
  /** 常规导出保持格式约束和相同运行的幂等意图。 */
  async function exportReport(format: ExportFormat) {
    if (!active.value || !run.value || exporting.value || !availableFormats.value.includes(format) || !checkPermi(['report:runtime:export'])) return;
    const origin = run.value.id;
    let key: string;
    try {
      key = keyFor(origin, format);
    } catch {
      printError.value = '当前浏览器缺少安全随机能力，请更新浏览器';
      ElMessage.error(printError.value);
      return;
    }
    const ticket = exportGate.begin();
    exporting.value = true;
    try {
      const response = await enqueueExport(origin, format, key);
      if (!exportGate.current(ticket)) return;
      if (['FAILED', 'CANCELLED', 'EXPIRED'].includes(response.data.state)) keys.delete(origin + ':' + format);
      ElMessage.success('导出任务已创建，可在导出中心查看');
    } catch (error) {
      const failure = await readReportFailure(error);
      if (exportGate.current(ticket) && !accessFailure(failure)) printError.value = failure.message;
    } finally {
      if (exportGate.current(ticket)) exporting.value = false;
    }
  }
  /** 文件交付由服务端再次核对所有者和依赖权限。 */
  async function loadPrintPdf(): Promise<Blob> {
    if (!ownsPrint.value || printJob.value?.state !== 'SUCCEEDED' || printJob.value.runId !== run.value?.id) throw new Error('PDF任务不可领取');
    const ticket = gate.begin();
    try {
      const result = await downloadExport(printJob.value.id);
      if (gate.current(ticket) && result.type.toLowerCase().includes('json')) {
        const failure = await readReportFailure({ response: { data: result } });
        if (gate.current(ticket) && accessFailure(failure)) throw Object.assign(new Error(failure.message), { response: { data: result } });
      }
      return result;
    } catch (error) {
      const failure = await readReportFailure(error);
      if (gate.current(ticket)) accessFailure(failure);
      throw error;
    }
  }
  watch(() => [run.value?.id, active.value, userStore.userId, userStore.tenantId], stopPrint, { flush: 'sync' });
  watch(
    () => checkPermi(['report:runtime:export']),
    (allowed) => {
      if (!allowed) suspendPrint();
    },
    { flush: 'sync' }
  );
  watch(
    ownsPrint,
    (allowed) => {
      if (!allowed) pdfOpen.value = false;
    },
    { flush: 'sync' }
  );
  onUnmounted(stopPrint);
  return {
    printJob,
    printing,
    exporting,
    printError,
    pdfOpen,
    ownsPrint,
    availableFormats,
    formatLabels,
    stopPrint,
    printPdf,
    pollPrint,
    retryPrint,
    exportReport,
    loadPrintPdf
  };
}
