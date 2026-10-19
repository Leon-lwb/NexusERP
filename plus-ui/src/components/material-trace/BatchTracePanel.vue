<template>
  <el-card class="batch-facts my-3" v-loading="loading">
    <template #header>批次自身事实 · {{ batchId }}</template>
    <el-alert
      title="来源仓库仅表示批次来源；当前仓库和库存以 STOCK 分节为准，客户去向以实际 MOVEMENTS 流水为准。"
      type="info"
      :closable="false"
      class="mb-3"
    />
    <template v-if="view">
      <p>授权模块：{{ view.parts.map((part) => part.module).join(' / ') || '当前无可见模块' }}</p>
      <TraceEvidence :result="view" />
      <el-alert
        v-if="view.truncated"
        title="批次自身事实响应已截断，请分别读取已授权模块的后续分节页。"
        type="warning"
        :closable="false"
        class="my-2"
      />
      <el-card v-for="part in view.parts" :key="part.module" class="mt-3">
        <template #header>{{ part.module }} · 批次 {{ part.batchId }} · {{ part.scope }}</template>
        <el-alert
          v-for="(gap, position) in part.evidenceGaps || []"
          :key="position"
          :title="traceCell(gap)"
          type="warning"
          :closable="false"
          class="mb-2"
        />
        <el-collapse>
          <el-collapse-item v-for="(page, section) in part.sections" :key="section" :name="section">
            <template #title
              >{{ sectionLabel(section) }} · 当前页 {{ page.rows.length }} 条 · {{ page.hasMore ? '还有后续页' : '当前页无后续游标' }}</template
            >
            <TraceSectionTable :page="page" />
            <el-alert
              v-if="stateFor(part.module, section).error"
              :title="stateFor(part.module, section).error"
              type="warning"
              :closable="false"
              class="mt-2"
            />
            <p>第 {{ stateFor(part.module, section).index + 1 }} 页 · 每页最多 500 条</p>
            <el-button
              :disabled="stateFor(part.module, section).index === 0 || stateFor(part.module, section).pending"
              @click="previous(part.module, section)"
              >{{ part.module }} {{ section }} 批次上一页</el-button
            >
            <el-button :disabled="!page.hasMore" :loading="stateFor(part.module, section).pending" @click="next(part.module, section)"
              >{{ part.module }} {{ section }} 批次下一页</el-button
            >
          </el-collapse-item>
        </el-collapse>
      </el-card>
    </template>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
  </el-card>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { traceBatch } from '@/api/erp/wm/trace';
import type { BatchTraceResult, BatchTraceSectionName, TraceModule, TraceSectionPage } from '@/api/erp/wm/trace/types';
import { mergeBatchSection, pageCursor, replaceBatchPage, traceCell } from './tracePaging';
import TraceEvidence from './TraceEvidence.vue';
import TraceSectionTable from './TraceSectionTable.vue';

const props = defineProps<{ batchId: string; initial?: BatchTraceResult }>();
const view = ref<BatchTraceResult>();
const loading = ref(false);
const error = ref('');
interface State {
  pages: TraceSectionPage[];
  index: number;
  pending: boolean;
  error: string;
  cursors: string[];
}
const states = ref<Record<string, State>>({});
let generation = 0;

/** 初次沿用召回返回的授权parts，缺少时读取独立批次接口；迟到结果不跨批次绑定。 */
watch(
  () => [props.batchId, props.initial] as const,
  async ([batchId, initial]) => {
    const request = ++generation;
    view.value = undefined;
    states.value = {};
    error.value = '';
    loading.value = false;
    if (!batchId) return;
    loading.value = true;
    try {
      const result = initial || (await traceBatch(batchId)).data;
      if (request !== generation) return;
      if (result.batchId !== batchId || !Array.isArray(result.parts)) throw new Error('服务端未返回同一批次的自身事实分节');
      const initialized: Record<string, State> = {};
      const modules = new Set<string>();
      for (const part of result.parts) {
        if (part.batchId !== batchId || modules.has(part.module)) throw new Error('批次模块身份缺失或重复，不能自动选择');
        modules.add(part.module);
        for (const [section, page] of Object.entries(part.sections))
          initialized[`${part.module}/${section}`] = { pages: [page], index: 0, pending: false, error: '', cursors: [] };
      }
      view.value = result;
      states.value = initialized;
    } catch (cause) {
      if (request === generation) error.value = cause instanceof Error ? cause.message : '批次自身事实读取失败';
    } finally {
      if (request === generation) loading.value = false;
    }
  },
  { immediate: true }
);
onBeforeUnmount(() => {
  generation++;
});

function stateFor(module: TraceModule, section: string): State {
  return states.value[`${module}/${section}`];
}
function sectionLabel(section: string): string {
  return (
    (
      { STOCK: '当前库存与当前仓库', MOVEMENTS: '实际流转与客户去向', SOURCE: '批次来源（仓库仅代表来源）', LOTS: 'MES 实际产品批次' } as Record<
        string,
        string
      >
    )[section] || section
  );
}
/** 仅恢复该模块该分节的缓存，其它批次事实和召回页不受影响。 */
function previous(module: TraceModule, section: BatchTraceSectionName) {
  const state = stateFor(module, section);
  if (!view.value || !state || state.index === 0 || state.pending) return;
  state.index--;
  view.value = replaceBatchPage(view.value, module, section, state.pages[state.index]);
}
/** 请求只使用初次已授权parts中的module+section和该页原游标，费用等字段沿用服务端裁剪。 */
async function next(module: TraceModule, section: BatchTraceSectionName) {
  const state = stateFor(module, section);
  if (!view.value || !state || state.pending) return;
  const request = generation;
  state.error = '';
  try {
    if (state.pages[state.index + 1]) {
      state.index++;
      view.value = replaceBatchPage(view.value, module, section, state.pages[state.index]);
      return;
    }
    const cursor = pageCursor(state.pages[state.index]);
    if (cursor === undefined) return;
    if (state.cursors.includes(cursor)) throw new Error('批次分节游标未推进，请重新查询');
    state.pending = true;
    const response = await traceBatch(props.batchId, { module, section, cursor, limit: 500 });
    if (request !== generation) return;
    const merged = mergeBatchSection(view.value, { module, section }, response.data);
    const page = merged.parts.find((part) => part.module === module)!.sections[section]!;
    state.cursors.push(cursor);
    state.pages.push(page);
    state.index++;
    view.value = merged;
  } catch (cause) {
    if (request === generation) state.error = cause instanceof Error ? cause.message : '批次分节分页失败';
  } finally {
    if (request === generation) state.pending = false;
  }
}
</script>
