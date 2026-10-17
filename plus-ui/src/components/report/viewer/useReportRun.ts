import { computed, ref, watch, onUnmounted, onActivated, onDeactivated, toValue, type MaybeRefOrGetter } from 'vue';
import { publishedReport, createRun, runStatus, runPage, runLayout } from '@/api/report';
import { readReportFailure } from '@/api/report/errors';
import { useUserStore } from '@/store/modules/user';
import { checkPermi } from '@/utils/permission';
import { parameterInput, requiredParameterMessage, requiresSnapshot as snapshotRequired, RequestGate } from '@/views/report/model';
import type { PublishedReport, ReportRun, PageData, RenderPlan } from '@/types/report';

/** 运行上下文由页面或抽屉提供，不依赖路由对象。 */
export interface ReportRunSource {
  /** 模板资源。 */
  reportId: string;
  /** 用户确认的不可变版本。 */
  expectedVersionId?: string;
  /** 仅采纳模板声明的参数。 */
  parameters?: Record<string, unknown>;
  /** 单据入口可自动预览一次。 */
  autoPreview?: boolean;
  /** 重新鉴权后复用的既有运行。 */
  runId?: string;
  /** 隐藏抽屉不再发起请求。 */
  active?: boolean;
}

/** 管理固定版本取数和分页，所有迟到响应须通过请求围栏。 */
export function useReportRun(source: MaybeRefOrGetter<ReportRunSource>) {
  const userStore = useUserStore();
  const published = ref<PublishedReport>();
  const parameters = ref<Record<string, string>>({});
  const mode = ref('LIVE');
  const run = ref<ReportRun>();
  const data = ref<PageData>();
  const plan = ref<RenderPlan>();
  const loading = ref(false);
  const loadFailed = ref(false);
  const loadError = ref('');
  const runFailed = ref('');
  const pageLoading = ref(false);
  const running = ref(false);
  const page = ref(1);
  const size = ref(50);
  const suspended = ref(false);
  const active = computed(() => !suspended.value && toValue(source).active !== false && checkPermi(['report:runtime:preview']));
  const requiresSnapshot = computed(() => !!published.value && snapshotRequired(published.value.dsl));
  const gate = new RequestGate();
  const pageGate = new RequestGate();
  const autoAttempts = new Set<string>();
  let names: string[] | undefined;
  let signature = '';
  let sort: string | undefined;
  let descending = false;

  /** 清除当前结果，打印逻辑会随运行标识变化同步失效。 */
  function invalidate() {
    gate.invalidate();
    pageGate.invalidate();
    run.value = undefined;
    data.value = undefined;
    plan.value = undefined;
    runFailed.value = '';
    running.value = false;
    pageLoading.value = false;
  }

  /** 无关页面参数不改变当前运行身份。 */
  function contextSignature() {
    const input = toValue(source);
    const bindings = names
      ? names.map((name) => [name, input.parameters?.[name] ?? ''])
      : Object.entries(input.parameters ?? {}).sort(([first], [second]) => first.localeCompare(second));
    return JSON.stringify([input.reportId, input.expectedVersionId ?? '', input.runId ?? '', !!input.autoPreview, ...bindings]);
  }

  /** 当前请求仍有效时读取结果及服务端排版，不计算业务数据。 */
  async function result(ticket: number, current: ReportRun) {
    const response = await runPage(current.id, 1, size.value);
    if (!gate.current(ticket) || !active.value) return;
    data.value = response.data;
    if (published.value?.dsl.kind === 'DOCUMENT') {
      const layout = await runLayout(current.id);
      if (gate.current(ticket) && active.value) plan.value = layout.data;
    }
  }

  /** 以界面确认的版本创建运行，参数变化立即使旧结果失效。 */
  async function execute() {
    if (!active.value || !published.value || running.value || !checkPermi(['report:runtime:preview'])) return;
    const invalid = requiredParameterMessage(published.value.effectiveParameters ?? published.value.dsl.parameters, parameters.value);
    if (invalid) {
      invalidate();
      runFailed.value = invalid;
      return;
    }
    invalidate();
    const ticket = gate.begin();
    const versionId = published.value.versionId;
    running.value = true;
    page.value = 1;
    sort = undefined;
    descending = false;
    try {
      const response = await createRun(toValue(source).reportId, mode.value, parameterInput(parameters.value), versionId);
      if (!gate.current(ticket) || !active.value) return;
      if (response.data.version && response.data.version !== versionId) {
        runFailed.value = '模板发布版本已变化，请重新选择模板后运行。';
        return;
      }
      run.value = response.data;
      await result(ticket, response.data);
    } catch (error) {
      const failure = await readReportFailure(error);
      if (gate.current(ticket)) {
        run.value = undefined;
        data.value = undefined;
        plan.value = undefined;
        runFailed.value = failure.action === 'NONE' ? '运行未完成，请检查查询条件并核对当前状态后重新运行。' : failure.message;
      }
    } finally {
      if (gate.current(ticket)) running.value = false;
    }
  }

  /** 加载模板并按声明参数绑定上下文，已有run只读恢复。 */
  async function load() {
    invalidate();
    names = undefined;
    published.value = undefined;
    parameters.value = {};
    loadFailed.value = false;
    loadError.value = '';
    signature = contextSignature();
    loading.value = false;
    const input = toValue(source);
    if (!active.value || !input.reportId || !checkPermi(['report:runtime:preview'])) return;
    const ticket = gate.begin();
    loading.value = true;
    try {
      const response = await publishedReport(input.reportId);
      if (!gate.current(ticket) || !active.value) return;
      if (input.expectedVersionId && response.data.versionId !== input.expectedVersionId) {
        loadFailed.value = true;
        loadError.value = '模板发布版本已变化，请返回业务入口重新选择。';
        return;
      }
      published.value = response.data;
      names = (response.data.effectiveParameters ?? response.data.dsl.parameters).map((parameter) => parameter.name);
      for (const name of names) {
        const value = toValue(source).parameters?.[name];
        if (typeof value === 'string' && value.length <= 200) parameters.value[name] = value;
      }
      mode.value = requiresSnapshot.value ? 'SNAPSHOT' : 'LIVE';
      signature = contextSignature();
      if (input.runId) {
        const existing = await runStatus(input.runId);
        if (!gate.current(ticket) || !active.value) return;
        if (existing.data.reportId !== input.reportId || existing.data.version !== response.data.versionId) {
          runFailed.value = '原运行与当前模板版本不符，请重新选择模板；不会自动重新取数。';
          return;
        }
        if (existing.data.expires && Date.parse(existing.data.expires) <= Date.now()) {
          runFailed.value = '原固定快照已过期，请明确重新运行。';
          return;
        }
        run.value = existing.data;
        mode.value = existing.data.mode;
        await result(ticket, existing.data);
      }
    } catch (error) {
      const failure = await readReportFailure(error);
      if (gate.current(ticket)) {
        published.value = undefined;
        run.value = undefined;
        data.value = undefined;
        plan.value = undefined;
        loadFailed.value = true;
        loadError.value = failure.message;
      }
    } finally {
      if (gate.current(ticket)) loading.value = false;
    }
    if (gate.current(ticket) && published.value?.dsl.kind === 'DOCUMENT' && input.autoPreview && !input.runId && !autoAttempts.has(signature)) {
      autoAttempts.add(signature);
      await execute();
    }
  }

  /** 分页只读取当前运行，失败后移除旧结果。 */
  async function loadPage() {
    if (!active.value || !run.value) return;
    const ticket = pageGate.begin();
    const id = run.value.id;
    pageLoading.value = true;
    try {
      const response = await runPage(id, page.value, size.value, sort, descending);
      if (pageGate.current(ticket) && run.value?.id === id) data.value = response.data;
    } catch (error) {
      const failure = await readReportFailure(error);
      if (pageGate.current(ticket)) {
        invalidate();
        runFailed.value = failure.action === 'NONE' ? '结果页面载入失败，请重新运行报表。' : failure.message;
      }
    } finally {
      if (pageGate.current(ticket)) pageLoading.value = false;
    }
  }

  /** 页大小变化从第一页开始。 */
  function sizeChanged() {
    page.value = 1;
    void loadPage();
  }
  /** 服务端确认范围失效时清除业务结果与模板参数，保留明确错误指引。 */
  function accessDenied(message: string) {
    invalidate();
    published.value = undefined;
    parameters.value = {};
    loading.value = false;
    loadFailed.value = true;
    loadError.value = message;
  }
  /** 排序仅适用于当前服务端分页。 */
  function sortChanged(field: string | undefined, desc: boolean) {
    sort = field;
    descending = desc;
    page.value = 1;
    void loadPage();
  }
  /** 隐藏、切换和卸载同步停止本地动作。 */
  function stop() {
    signature = '';
    autoAttempts.clear();
    invalidate();
    published.value = undefined;
    parameters.value = {};
    loading.value = false;
  }

  watch(
    () => [toValue(source), active.value],
    () => {
      if (!active.value) stop();
      else if (contextSignature() !== signature) void load();
    },
    { immediate: true, deep: true, flush: 'sync' }
  );
  watch(
    parameters,
    () => {
      if (!loading.value) invalidate();
    },
    { deep: true, flush: 'sync' }
  );
  watch(
    mode,
    () => {
      if (!loading.value) invalidate();
    },
    { flush: 'sync' }
  );
  watch(
    () => [userStore.userId, userStore.tenantId],
    () => {
      stop();
      autoAttempts.clear();
      if (active.value) void load();
    },
    { flush: 'sync' }
  );
  onDeactivated(() => {
    suspended.value = true;
  });
  onActivated(() => {
    suspended.value = false;
  });
  onUnmounted(() => {
    suspended.value = true;
    stop();
  });
  return {
    published,
    parameters,
    mode,
    run,
    data,
    plan,
    loading,
    loadFailed,
    loadError,
    runFailed,
    pageLoading,
    running,
    page,
    size,
    active,
    requiresSnapshot,
    accessDenied,
    invalidate,
    load,
    execute,
    loadPage,
    sizeChanged,
    sortChanged
  };
}
