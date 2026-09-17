<template>
  <!--
    PDF 盖章定位组件（P2b-1）。

    坐标契约见 `@/api/system/sign/document` 的 SealPlacement 注释：五个比例均为**归一化 0..1、原点左上**，
    pageIndex **0 基**。PDF 用户空间的 y 翻转（y = pageHeight - yRatio*pageHeight - h）是服务端
    `PdfStampGeometry` 的职责，本组件不做任何 PDF 空间换算 —— 落位直接以百分比写入页面盒子的
    left/top/width/height，因此容器缩放、缩放倍率变化都不会让印章跑位。

    用法：
      <PdfSealPlacer ref="placerRef" :doc-id="docId" biz-type="SO_CONTRACT" v-model:placements="placements"
                     @success="onSigned" />
  -->
  <div class="pdf-seal-placer">
    <div class="pdf-seal-placer__viewer">
      <div class="pdf-seal-placer__toolbar">
        <span class="pdf-seal-placer__hint">点击页面放置印章；拖动可移动，右下角手柄缩放，右上角手柄旋转</span>
        <div class="pdf-seal-placer__zoom">
          <el-button size="small" icon="ZoomOut" :disabled="zoom <= MIN_ZOOM" @click="changeZoom(-ZOOM_STEP)" />
          <span class="pdf-seal-placer__zoom-text">{{ Math.round(zoom * 100) }}%</span>
          <el-button size="small" icon="ZoomIn" :disabled="zoom >= MAX_ZOOM" @click="changeZoom(ZOOM_STEP)" />
          <el-button size="small" icon="FullScreen" @click="resetZoom">适应宽度</el-button>
          <el-button size="small" icon="Refresh" @click="loadPdf">重新加载</el-button>
        </div>
      </div>

      <div ref="stageRef" v-loading="pdfLoading" element-loading-text="正在加载 PDF 正文…" class="pdf-seal-placer__stage">
        <!-- 未指定文档：给明确空态，不留白板 -->
        <el-empty v-if="!hasDocument" :image-size="70" description="尚未指定待签文档正文" />
        <!-- 加载失败（非 PDF / 已加密 / 无权限 / 正文缺失）：给出原因与重试，不留白板 -->
        <el-alert v-else-if="pdfError" class="pdf-seal-placer__error" type="error" show-icon :closable="false" :title="pdfError">
          <div class="pdf-seal-placer__error-body">
            <span>请确认该文档已上传 PDF 正文，且当前账号有查看正文的权限。</span>
            <el-button link type="primary" @click="loadPdf">重新加载</el-button>
          </div>
        </el-alert>
        <div v-else ref="pagesRef" class="pdf-seal-placer__pages">
          <div v-for="meta in pages" :key="meta.pageIndex" class="pdf-page" :style="pageStyle(meta)">
            <canvas :ref="(el) => setCanvasRef(el, meta.pageIndex)" class="pdf-page__canvas" />
            <!-- 覆盖层负责「点击放置」；印章元素上的事件一律 stop，避免放置冲突 -->
            <div class="pdf-page__overlay" @click="handlePageClick($event, meta)">
              <div
                v-for="(pl, index) in pagePlacements(meta.pageIndex)"
                :key="`${meta.pageIndex}-${index}`"
                class="seal-placement"
                :style="placementStyle(pl)"
                @pointerdown.stop="startDrag($event, pl, 'move')"
                @click.stop
              >
                <img
                  v-if="sealPreview(pl.sealId)"
                  class="seal-placement__img"
                  :src="sealPreview(pl.sealId)"
                  :alt="sealName(pl.sealId)"
                  draggable="false"
                />
                <!-- 无预览图（P2a-1 前生成的印章）时退化为名称牌，与 SealPicker 的降级一致；**绝不请求印章原图**（无该接口） -->
                <div v-else class="seal-placement__fallback">{{ sealName(pl.sealId) }}</div>
                <span
                  class="seal-placement__handle seal-placement__handle--rotate"
                  title="拖动旋转"
                  @pointerdown.stop="startDrag($event, pl, 'rotate')"
                />
                <span
                  class="seal-placement__handle seal-placement__handle--resize"
                  title="拖动缩放"
                  @pointerdown.stop="startDrag($event, pl, 'resize')"
                />
                <span class="seal-placement__del" title="删除该印章" @pointerdown.stop @click.stop="removePlacement(pl)">×</span>
              </div>
            </div>
            <div class="pdf-page__no">第 {{ meta.pageIndex + 1 }} 页</div>
          </div>
        </div>
      </div>
    </div>

    <div class="pdf-seal-placer__panel">
      <el-card shadow="never">
        <template #header><span class="pdf-seal-placer__panel-title">签署印章</span></template>
        <SealPicker ref="sealPickerRef" v-model="sealId" :biz-type="bizType" />
      </el-card>

      <el-card shadow="never" class="mt3">
        <template #header>
          <span class="pdf-seal-placer__panel-title">印章落位（{{ placements.length }}）</span>
        </template>
        <el-empty v-if="!placements.length" :image-size="50" description="尚未放置印章" />
        <div v-for="(pl, index) in placements" :key="`${pl.pageIndex}-${index}`" class="placement-row">
          <span class="placement-row__index">{{ index + 1 }}</span>
          <span class="placement-row__page">第 {{ pl.pageIndex + 1 }} 页</span>
          <span class="placement-row__name">{{ sealName(pl.sealId) }}</span>
          <span class="flex-1" />
          <el-button link size="small" icon="RefreshLeft" title="逆时针 15°" @click="rotate(pl, 15)" />
          <el-button link size="small" icon="RefreshRight" title="顺时针 15°" @click="rotate(pl, -15)" />
          <el-button link type="danger" size="small" icon="Delete" @click="removePlacement(pl)" />
        </div>
      </el-card>

      <el-card shadow="never" class="mt3">
        <template #header><span class="pdf-seal-placer__panel-title">提交签署</span></template>
        <el-form label-width="80px" @submit.prevent>
          <el-form-item label="签署含义" required>
            <el-input v-model="signMeaning" maxlength="128" show-word-limit placeholder="请输入签署含义" />
          </el-form-item>
        </el-form>
        <!-- 签章入口：与 work/detail.vue 的签章入口同构 —— 先校验落位与签署含义，再走二级认证 -->
        <el-button type="primary" icon="CircleCheck" class="w-full" :loading="submitting" :disabled="disabled" @click="handleSign"
          >提交签署</el-button
        >
        <div class="pdf-seal-placer__tip">提交前需二次身份验证（登录密码）；签署行为将永久记录（签署人 / 时间 / IP / 哈希链）。</div>
      </el-card>
    </div>

    <SignAuthDialog ref="authRef" v-model="authVisible" @confirmed="handleSignConfirmed" />
  </div>
</template>

<script setup lang="ts">
import { GlobalWorkerOptions, getDocument } from 'pdfjs-dist';
import type { PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
// Vite 下 pdfjs 的 worker 必须显式指定：`?url` 让 Vite 把 worker 作为独立资源产出并返回其地址。
// 不设置 workerSrc 时 pdfjs 会退回主线程「假 worker」（大文件卡死界面），或按相对路径 404。
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import SealPicker from '@/components/sign/SealPicker.vue';
import SignAuthDialog from '@/components/sign/SignAuthDialog.vue';
import { listAvailableSeal, type AvailableSealVO } from '@/api/system/sign/seal';
import { fetchDocumentBytes, fetchOssBytes, signDocument, type SealPlacement } from '@/api/system/sign/document';

GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

/** 渲染尺寸下限/缩放区间（zoom 是在「适应宽度」基础上的倍率） */
const MIN_ZOOM = 0.3;
const MAX_ZOOM = 3;
const ZOOM_STEP = 0.2;
/** 页面留白（左右各一份，用于算适应宽度） */
const PAGE_GUTTER = 12;
/** 印章默认边长（PDF 点）≈ 40mm —— 中国公章标准直径；页宽不足时按页宽收缩 */
const DEFAULT_SEAL_PT = 113;
/** 印章最小边长（PDF 点）≈ 8.5mm */
const MIN_SEAL_PT = 24;
/** 旋转吸附步长（度），避免手动摆出 3.7° 这种没法复现的角度 */
const ROTATE_SNAP = 15;

const props = withDefaults(
  defineProps<{
    /** 待签文档ID（es_sign_document.doc_id）；签署接口以它为主键 */
    docId: string | number;
    /** 业务场景（后端 SignBizType，如 SO_CONTRACT / PU_CONTRACT），用于拉取当前用户可用印章 */
    bizType: string;
    /** 直接指定要预览的正文 OSS ID；不传则按 docId 解析（已签署优先取签署后正文） */
    ossId?: string | number;
    /** 最多可放置的印章数（后端本身不限制，这里只做界面保护） */
    maxPlacements?: number;
    /** 只读（查看已签署文档时禁用放置与提交） */
    disabled?: boolean;
  }>(),
  { ossId: undefined, maxPlacements: 20, disabled: false }
);

/** 当前落位（归一化比例，可直接作为 DocSignForm.placements 提交） */
const placements = defineModel<SealPlacement[]>({ default: () => [] });

const emit = defineEmits<{
  /** 签署成功（后端已返回新签名ID），后续跳转/刷新由父组件决定 */
  (e: 'success', signatureId: string | number): void;
  /** 签署失败（弹窗保持打开以便改正后重试） */
  (e: 'error', error: Error): void;
}>();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

/** 页面自然尺寸（scale=1 的 viewport，1 单位 = 1 PDF 点）；比例换算一律以它为准 */
interface PageMeta {
  pageIndex: number;
  width: number;
  height: number;
}

const stageRef = ref<HTMLElement>();
const pagesRef = ref<HTMLElement>();
const pages = ref<PageMeta[]>([]);
const pdfLoading = ref(false);
const pdfError = ref('');
/** 适应宽度时「1 PDF 点 = ? px」；zoom 是在它之上的倍率 */
const baseScale = ref(1);
const zoom = ref(1);
/** 实际渲染比例（px / PDF 点） */
const scale = computed(() => baseScale.value * zoom.value);

/**
 * pdfjs 的文档 / 加载任务 / 渲染任务**不能放进响应式对象**（会被 Proxy 包裹导致内部私有字段失效），
 * 故用普通变量持有；卸载时按 pdfjs 要求显式 destroy，避免 worker 与内存泄漏。
 */
let loadingTask: PDFDocumentLoadingTask | null = null;
let pdfDoc: PDFDocumentProxy | null = null;
const renderTasks = new Map<number, RenderTask>();
const canvasEls = new Map<number, HTMLCanvasElement>();
/** 渲染轮次：缩放/重载会发起新一轮，旧轮次在 await 之后自行退出 */
let renderToken = 0;
let resizeObserver: ResizeObserver | null = null;

const hasDocument = computed(() => props.docId !== undefined && props.docId !== null && props.docId !== '');

/** 拖动/缩放/旋转的临时状态（普通变量，不参与渲染） */
interface DragState {
  mode: 'move' | 'resize' | 'rotate';
  /** 被拖动的落位在 placements 中的下标（拖动中数组顺序不变，用它定位；不要用对象引用做比较） */
  index: number;
  startX: number;
  startY: number;
  origin: SealPlacement;
  page: PageMeta;
  /** 旋转用：屏幕上的旋转中心与按下时的角度（度，逆时针为正） */
  centerX: number;
  centerY: number;
  startAngle: number;
}
let drag: DragState | null = null;

// ===== 印章元数据（用于在页面上画预览图 / 名称，与 SealPicker 的降级口径一致） =====

const sealPickerRef = ref<InstanceType<typeof SealPicker>>();
const sealId = ref<string | undefined>();
const sealMap = ref<Record<string, AvailableSealVO>>({});

/**
 * 拉取可选印章元数据：SealPicker 只暴露选择结果（不暴露列表），
 * 而页面上画印章需要 previewUrl/名称，故这里按同一 bizType 再取一次（同一接口，参数一致）。
 * 仅仅展示预览图；印章原图没有任何对外接口，此处也不会去取。
 */
const loadSeals = async () => {
  const { data } = await listAvailableSeal(props.bizType);
  sealMap.value = Object.fromEntries((data ?? []).map((seal) => [String(seal.sealId), seal]));
};

watch(() => props.bizType, loadSeals, { immediate: true });

const sealMeta = (sealValue: string | number) => sealMap.value[String(sealValue)];
const sealPreview = (sealValue: string | number) => sealMeta(sealValue)?.previewUrl ?? '';
const sealName = (sealValue: string | number) => sealMeta(sealValue)?.sealName ?? `印章 ${sealValue}`;

// ===== PDF 加载与渲染 =====

const describePdfError = (error: any): string => {
  const name = error?.name;
  if (name === 'InvalidPDFException') return '该文件不是有效的 PDF（或已损坏），无法预览';
  if (name === 'PasswordException') return '该 PDF 已加密，需要密码才能打开';
  if (name === 'MissingPDFException') return '正文文件不存在或已被删除';
  return error?.message || 'PDF 正文加载失败';
};

/** 释放当前文档与渲染任务（重复调用安全） */
const destroyPdf = async () => {
  renderTasks.forEach((task) => task.cancel());
  renderTasks.clear();
  canvasEls.clear();
  const doc = pdfDoc;
  const task = loadingTask;
  pdfDoc = null;
  loadingTask = null;
  try {
    // 文档加载成功后 destroy 文档即可（会连带销毁 worker transport）；加载中途失败则销毁任务
    if (doc) await doc.destroy();
    else if (task) await task.destroy();
  } catch (error) {
    console.warn('释放 PDF 资源失败（忽略）:', error);
  }
};

/** 渲染全部页面（顺序渲染，避免大文件并发占满内存；取消/过期轮次静默退出） */
const renderAll = async () => {
  const doc = pdfDoc;
  if (!doc) return;
  const token = ++renderToken;
  const dpr = window.devicePixelRatio || 1;
  for (const meta of pages.value) {
    if (token !== renderToken) return;
    const canvas = canvasEls.get(meta.pageIndex);
    if (!canvas) continue;
    renderTasks.get(meta.pageIndex)?.cancel();
    try {
      const page = await doc.getPage(meta.pageIndex + 1);
      if (token !== renderToken) return;
      const viewport = page.getViewport({ scale: scale.value });
      const context = canvas.getContext('2d');
      if (!context) continue;
      // HiDPI：物理像素按 dpr 放大，CSS 尺寸仍为逻辑像素，缩放时印章与正文一起缩放
      canvas.width = Math.max(1, Math.floor(viewport.width * dpr));
      canvas.height = Math.max(1, Math.floor(viewport.height * dpr));
      canvas.style.width = `${viewport.width}px`;
      canvas.style.height = `${viewport.height}px`;
      const task = page.render({ canvasContext: context, viewport, transform: dpr === 1 ? undefined : [dpr, 0, 0, dpr, 0, 0] });
      renderTasks.set(meta.pageIndex, task);
      await task.promise;
    } catch (error: any) {
      // 缩放/重载/卸载触发的取消是预期路径
      if (error?.name !== 'RenderingCancelledException') console.error('PDF 页面渲染失败:', error);
    }
  }
};

/** 按容器宽度重算「适应宽度」比例；宽度变化超过阈值才重渲染 */
const measure = async (force = false) => {
  const width = stageRef.value?.clientWidth ?? 0;
  if (!width || !pages.value.length) return;
  const maxPageWidth = Math.max(...pages.value.map((page) => page.width));
  const next = Math.max((width - PAGE_GUTTER * 2) / maxPageWidth, 0.05);
  if (!force && Math.abs(next - baseScale.value) < 0.001) return;
  baseScale.value = next;
  await nextTick();
  await renderAll();
};

const loadPdf = async () => {
  await destroyPdf();
  pdfError.value = '';
  pages.value = [];
  if (!hasDocument.value) {
    pdfLoading.value = false;
    return;
  }
  pdfLoading.value = true;
  try {
    // 正文取流走同源鉴权端点（先取 ossId 再取字节），避免直接把跨域 OSS 地址丢给 pdfjs worker
    const bytes = props.ossId ? await fetchOssBytes(props.ossId) : await fetchDocumentBytes(props.docId);
    const task = getDocument({ data: new Uint8Array(bytes) });
    loadingTask = task;
    const doc = await task.promise;
    pdfDoc = doc;
    const metas: PageMeta[] = [];
    for (let pageNo = 1; pageNo <= doc.numPages; pageNo++) {
      // pdfjs 页码 1 基，后端 pageIndex 0 基 —— 这里就换算好，落位里只出现 0 基序号
      const page = await doc.getPage(pageNo);
      const viewport = page.getViewport({ scale: 1 });
      metas.push({ pageIndex: pageNo - 1, width: viewport.width, height: viewport.height });
    }
    pages.value = metas;
    await nextTick();
    await measure(true);
  } catch (error: any) {
    console.error('PDF 正文加载失败:', error);
    pdfError.value = describePdfError(error);
  } finally {
    pdfLoading.value = false;
  }
};

watch([() => props.docId, () => props.ossId], () => loadPdf());

onMounted(() => {
  resizeObserver = new ResizeObserver(() => measure());
  if (stageRef.value) resizeObserver.observe(stageRef.value);
  loadPdf();
});

onBeforeUnmount(async () => {
  stopDrag();
  resizeObserver?.disconnect();
  resizeObserver = null;
  await destroyPdf();
});

const setCanvasRef = (el: any, pageIndex: number) => {
  if (el instanceof HTMLCanvasElement) canvasEls.set(pageIndex, el);
  else canvasEls.delete(pageIndex);
};

// ===== 缩放 =====

const pageStyle = (meta: PageMeta) => ({
  width: `${meta.width * scale.value}px`,
  height: `${meta.height * scale.value}px`
});

const changeZoom = (delta: number) => {
  zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round((zoom.value + delta) * 100) / 100));
  renderAll();
};

const resetZoom = () => {
  zoom.value = 1;
  renderAll();
};

// ===== 落位：比例 ⇄ 屏幕坐标 =====

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max);
/** 归一化比例留 6 位小数：float32 精度足够，避免 JSON 里出现 0.30000000000000004 这类噪声 */
const round6 = (value: number) => Math.round(value * 1e6) / 1e6;
/** 角度归一化到 (-180, 180]，避免转圈后角度无限增大 */
const normalizeAngle = (deg: number) => {
  const wrapped = ((deg % 360) + 360) % 360;
  return wrapped > 180 ? wrapped - 360 : wrapped;
};

const pageMetaOf = (pageIndex: number) => pages.value.find((page) => page.pageIndex === pageIndex);
const pagePlacements = (pageIndex: number) => placements.value.filter((pl) => pl.pageIndex === pageIndex);

/** 落位样式：直接用百分比表达归一化比例 —— 容器/页面尺寸怎么变，印章都在同一相对位置 */
const placementStyle = (pl: SealPlacement) => ({
  left: `${pl.xRatio * 100}%`,
  top: `${pl.yRatio * 100}%`,
  width: `${pl.wRatio * 100}%`,
  height: `${pl.hRatio * 100}%`,
  // 后端 rotation 正值为「屏幕上逆时针」，CSS rotate 正值是顺时针，故取负号
  transform: pl.rotation ? `rotate(${-pl.rotation}deg)` : undefined
});

/** 默认印章比例：以自然尺寸（PDF 点）算，页窄时按页宽收缩，并保持预览图的宽高比 */
const defaultRatios = (meta: PageMeta, sealValue: string | number) => {
  const meta0 = sealMeta(sealValue);
  const aspect = meta0?.imgWidth && meta0?.imgHeight ? meta0.imgHeight / meta0.imgWidth : 1;
  const widthPt = Math.min(DEFAULT_SEAL_PT, meta.width * 0.3);
  const heightPt = widthPt * aspect;
  return { wRatio: widthPt / meta.width, hRatio: heightPt / meta.height };
};

/** 点击页面空白处放置印章（以点击点为中心） */
const handlePageClick = (event: MouseEvent, meta: PageMeta) => {
  if (props.disabled) return;
  if (!sealId.value) {
    proxy?.$modal.msgWarning('请先选择要使用的印章！');
    return;
  }
  if (placements.value.length >= props.maxPlacements) {
    proxy?.$modal.msgWarning(`最多只能放置 ${props.maxPlacements} 处印章`);
    return;
  }
  const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
  // 屏幕像素 → 归一化比例：渲染盒子 = 自然尺寸 × scale，比例与当前缩放/容器宽度无关
  const centerX = (event.clientX - box.left) / box.width;
  const centerY = (event.clientY - box.top) / box.height;
  const { wRatio, hRatio } = defaultRatios(meta, sealId.value);
  placements.value = [
    ...placements.value,
    {
      pageIndex: meta.pageIndex,
      xRatio: round6(clamp(centerX - wRatio / 2, 0, 1 - wRatio)),
      yRatio: round6(clamp(centerY - hRatio / 2, 0, 1 - hRatio)),
      wRatio: round6(wRatio),
      hRatio: round6(hRatio),
      rotation: 0,
      sealId: sealId.value
    }
  ];
};

const startDrag = (event: PointerEvent, pl: SealPlacement, mode: DragState['mode']) => {
  if (props.disabled) return;
  const meta = pageMetaOf(pl.pageIndex);
  const index = placements.value.indexOf(pl);
  if (!meta || index < 0) return;
  event.preventDefault();
  let centerX = 0;
  let centerY = 0;
  let startAngle = 0;
  if (mode === 'rotate') {
    const host = (event.currentTarget as HTMLElement)?.closest('.seal-placement') as HTMLElement | null;
    const rect = host?.getBoundingClientRect();
    if (!rect) return;
    centerX = rect.left + rect.width / 2;
    centerY = rect.top + rect.height / 2;
    // 屏幕角度（度）：y 轴向下，取负号使「逆时针为正」与后端 rotation 同向
    startAngle = -((Math.atan2(event.clientY - centerY, event.clientX - centerX) * 180) / Math.PI);
  }
  drag = {
    mode,
    index,
    startX: event.clientX,
    startY: event.clientY,
    origin: { ...pl },
    page: meta,
    centerX,
    centerY,
    startAngle
  };
  window.addEventListener('pointermove', onPointerMove);
  window.addEventListener('pointerup', stopDrag);
  window.addEventListener('pointercancel', stopDrag);
};

const onPointerMove = (event: PointerEvent) => {
  const state = drag;
  if (!state) return;
  const { origin, page } = state;
  const list = placements.value.slice();
  if (!list[state.index]) return;
  const next: SealPlacement = { ...origin };

  if (state.mode === 'rotate') {
    const angle = -((Math.atan2(event.clientY - state.centerY, event.clientX - state.centerX) * 180) / Math.PI);
    next.rotation = normalizeAngle(Math.round((origin.rotation + angle - state.startAngle) / ROTATE_SNAP) * ROTATE_SNAP);
  } else {
    // 屏幕位移 → PDF 点（除以 scale）→ 归一化比例（除以页面自然尺寸）；与容器缩放解耦
    const dxRatio = (event.clientX - state.startX) / (scale.value * page.width);
    const dyRatio = (event.clientY - state.startY) / (scale.value * page.height);
    if (state.mode === 'move') {
      next.xRatio = round6(clamp(origin.xRatio + dxRatio, 0, 1 - origin.wRatio));
      next.yRatio = round6(clamp(origin.yRatio + dyRatio, 0, 1 - origin.hRatio));
    } else {
      const minWRatio = MIN_SEAL_PT / page.width;
      const wRatio = clamp(origin.wRatio + dxRatio, minWRatio, 1 - origin.xRatio);
      // 等比缩放：按原点宽高比推出新高度，保证不与页边相撞
      const hRatio = Math.min(wRatio * (origin.hRatio / origin.wRatio), 1 - origin.yRatio);
      next.wRatio = round6(wRatio);
      next.hRatio = round6(hRatio);
    }
  }
  list[state.index] = next;
  placements.value = list;
};

function stopDrag() {
  drag = null;
  window.removeEventListener('pointermove', onPointerMove);
  window.removeEventListener('pointerup', stopDrag);
  window.removeEventListener('pointercancel', stopDrag);
}

const removePlacement = (pl: SealPlacement) => {
  if (props.disabled) return;
  placements.value = placements.value.filter((item) => item !== pl);
};

/** 列表里的旋转按钮：正值逆时针（与后端 rotation 同向），RefreshLeft=逆时针 / RefreshRight=顺时针 */
const rotate = (pl: SealPlacement, delta: number) => {
  if (props.disabled) return;
  placements.value = placements.value.map((item) => (item === pl ? { ...item, rotation: normalizeAngle(item.rotation + delta) } : item));
};

// ===== 提交签署 =====

const signMeaning = ref('');
const authVisible = ref(false);
const authRef = ref<InstanceType<typeof SignAuthDialog>>();
const submitting = ref(false);

/** 只取契约字段，避免把界面态带进请求体 */
const buildPayload = (): SealPlacement[] =>
  placements.value.map((pl) => ({
    pageIndex: pl.pageIndex,
    xRatio: round6(pl.xRatio),
    yRatio: round6(pl.yRatio),
    wRatio: round6(pl.wRatio),
    hRatio: round6(pl.hRatio),
    rotation: pl.rotation,
    sealId: pl.sealId
  }));

const handleSign = () => {
  if (!placements.value.length) {
    proxy?.$modal.msgError('请先在 PDF 页面上放置印章！');
    return;
  }
  if (!signMeaning.value?.trim()) {
    proxy?.$modal.msgError('请输入签署含义！');
    return;
  }
  authVisible.value = true;
};

/**
 * 二级认证通过后调签章接口。
 * 与 `views/workflow/work/detail.vue#handleSignConfirmed` 同一套做法：认证弹窗由父级控制
 * 提交中状态，后端拒签（密码错误 / 无授权 / 印章不可用）时保持弹窗打开以便改正后重试，
 * 成功后才关闭；凭证不落任何持久化位置（无 Pinia / 无 localStorage / 不进 URL）。
 */
const handleSignConfirmed = async (auth: { authMethod: string; credential: string }) => {
  if (!props.docId) {
    proxy?.$modal.msgError('缺少待签文档ID，无法签署！');
    return;
  }
  const payload = buildPayload();
  authRef.value?.setLoading(true);
  submitting.value = true;
  try {
    const res = await signDocument(props.docId, {
      placements: payload,
      signatureMeaning: signMeaning.value.trim(),
      authMethod: auth.authMethod,
      credential: auth.credential
    });
    authVisible.value = false;
    emit('success', res.data);
  } catch (error: any) {
    console.error('文档签章失败:', error);
    authRef.value?.setLoading(false);
    proxy?.$modal.msgError(error?.message || '签署失败');
    emit('error', error instanceof Error ? error : new Error(String(error?.message || error)));
  } finally {
    submitting.value = false;
  }
};

defineExpose({
  /** 授权配置变更后刷新可选印章（同时刷新页面上的印章预览元数据） */
  reload: () => {
    sealPickerRef.value?.reload();
    loadSeals();
  },
  /** 重新加载正文（签署成功、正文换版后调用） */
  reloadPdf: loadPdf,
  /** 当前落位（含归一化比例，可直接作为 DocSignForm.placements 提交） */
  getPlacements: buildPayload,
  /** 清空全部落位 */
  clearPlacements: () => {
    placements.value = [];
  }
});
</script>

<style scoped>
.pdf-seal-placer {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.pdf-seal-placer__viewer {
  flex: 1;
  min-width: 0;
}
.pdf-seal-placer__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.pdf-seal-placer__hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.pdf-seal-placer__zoom {
  display: flex;
  gap: 6px;
  align-items: center;
}
.pdf-seal-placer__zoom-text {
  min-width: 44px;
  text-align: center;
  font-size: 13px;
}
.pdf-seal-placer__stage {
  position: relative;
  min-height: 320px;
  max-height: 72vh;
  padding: 12px;
  overflow: auto;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
}
.pdf-seal-placer__pages {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}
.pdf-seal-placer__error-body {
  display: flex;
  gap: 8px;
  align-items: center;
}
.pdf-seal-placer__panel {
  width: 330px;
  flex: none;
}
.pdf-seal-placer__panel-title {
  font-weight: 600;
}
.pdf-seal-placer__tip {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.pdf-page {
  position: relative;
  background: #fff;
  box-shadow: 0 1px 6px rgb(0 0 0 / 16%);
}
.pdf-page__canvas {
  display: block;
}
.pdf-page__overlay {
  position: absolute;
  inset: 0;
  cursor: crosshair;
}
.pdf-page__no {
  position: absolute;
  right: 6px;
  bottom: 4px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  pointer-events: none;
}
.seal-placement {
  position: absolute;
  box-sizing: border-box;
  cursor: move;
  user-select: none;
  outline: 1px dashed var(--el-color-primary);
  outline-offset: 2px;
}
.seal-placement__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
  -webkit-user-drag: none;
}
.seal-placement__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 2px;
  overflow: hidden;
  color: var(--el-color-danger);
  font-size: 12px;
  text-align: center;
  border: 2px solid var(--el-color-danger);
  border-radius: 50%;
  pointer-events: none;
}
.seal-placement__handle {
  position: absolute;
  width: 12px;
  height: 12px;
  background: var(--el-color-primary);
  border: 1px solid #fff;
  border-radius: 50%;
}
.seal-placement__handle--rotate {
  top: -18px;
  right: -6px;
  cursor: grab;
}
.seal-placement__handle--resize {
  right: -6px;
  bottom: -6px;
  border-radius: 2px;
  cursor: nwse-resize;
}
.seal-placement__del {
  position: absolute;
  top: -18px;
  left: -6px;
  width: 14px;
  height: 14px;
  color: #fff;
  font-size: 12px;
  line-height: 12px;
  text-align: center;
  background: var(--el-color-danger);
  border-radius: 50%;
  cursor: pointer;
}
.placement-row {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 2px 0;
  font-size: 13px;
}
.placement-row__index {
  width: 18px;
  color: var(--el-text-color-secondary);
}
.placement-row__name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
