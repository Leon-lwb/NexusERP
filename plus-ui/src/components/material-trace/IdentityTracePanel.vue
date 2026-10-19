<template>
  <div class="identity-trace mt-3">
    <el-descriptions :column="2" border>
      <el-descriptions-item label="正式实物身份">{{ view.identityId }}</el-descriptions-item>
      <el-descriptions-item label="授权模块">{{ view.parts.map((part) => part.module).join(' / ') || '当前无可见模块' }}</el-descriptions-item>
    </el-descriptions>
    <TraceEvidence :result="view" class="mt-3" />
    <el-alert
      v-if="view.truncated"
      title="当前统一响应含截断；请逐模块、逐分节翻页。未展示的模块不能据此推定没有业务记录。"
      type="warning"
      :closable="false"
      class="mb-3"
    />
    <el-card v-for="part in view.parts" :key="part.module" class="mb-3">
      <template #header>{{ part.module }} · {{ part.canonicalSnCode || part.identityId }} · {{ part.scope }}</template>
      <p>模块本地身份：{{ part.localSnId || '未提供' }}；正式身份：{{ part.identityId }}</p>
      <p v-if="!part.canonicalSnCode">正式 SN：未提供正式登记证据，不以本地原码推定关联。</p>
      <p v-if="part.localSnCode && part.localSnCode !== part.canonicalSnCode">{{ part.module }} 本地原码：{{ part.localSnCode }}（仅供展示）</p>
      <el-alert v-for="(gap, index) in part.evidenceGaps || []" :key="index" :title="traceCell(gap)" type="warning" :closable="false" class="mb-2" />
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
          <div class="mt-2">
            <span>第 {{ stateFor(part.module, section).index + 1 }} 页 · 每页最多 500 条</span>
            <el-button
              class="ml-3"
              :disabled="stateFor(part.module, section).index === 0 || stateFor(part.module, section).pending"
              @click="previous(part.module, section)"
              >上一页</el-button
            >
            <el-button :disabled="!page.hasMore" :loading="stateFor(part.module, section).pending" @click="next(part.module, section)"
              >{{ part.module }} {{ section }} 下一页</el-button
            >
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { traceIdentity } from '@/api/erp/wm/trace';
import type { IdentityTraceResult, TraceModule, TraceSectionName, TraceSectionPage } from '@/api/erp/wm/trace/types';
import { mergeIdentitySection, pageCursor, replaceIdentityPage, traceCell } from './tracePaging';
import TraceEvidence from './TraceEvidence.vue';
import TraceSectionTable from './TraceSectionTable.vue';

const props = defineProps<{ result: IdentityTraceResult }>();
const view = ref<IdentityTraceResult>(props.result);
interface SectionState {
  pages: TraceSectionPage[];
  index: number;
  pending: boolean;
  error: string;
  cursors: string[];
}
const states = ref<Record<string, SectionState>>({});
let generation = 0;

/** 新身份响应重置所有页缓存，迟到分页请求不得混入另一实物。 */
watch(
  () => props.result,
  (result) => {
    ++generation;
    view.value = result;
    const initialized: Record<string, SectionState> = {};
    for (const part of result.parts)
      for (const [section, page] of Object.entries(part.sections)) {
        initialized[`${part.module}/${section}`] = { pages: [page], index: 0, pending: false, error: '', cursors: [] };
      }
    states.value = initialized;
  },
  { immediate: true }
);

/** 分节状态以模块和节名共同定位，不按数组第一条推定来源。 */
function stateFor(module: TraceModule, section: string): SectionState {
  return states.value[`${module}/${section}`];
}

/** IPQC 明确是工单范围，不显示为单件检验证明。 */
function sectionLabel(section: string): string {
  return (
    (
      {
        STOCK: '库存',
        MOVEMENTS: '库存流水',
        ALIASES: '外部别名',
        CONSUMPTION: '实际组件消耗',
        TEST: '测试记录',
        IPQC_WORK_ORDER: '工单范围 IPQC',
        MANIFEST: '最终完工清单'
      } as Record<string, string>
    )[section] || section
  );
}

/** 返回已缓存的上一页，不重复请求或拼接为完整结果。 */
function previous(module: TraceModule, section: TraceSectionName) {
  const state = stateFor(module, section);
  if (!state || state.pending || state.index === 0) return;
  state.index--;
  view.value = replaceIdentityPage(view.value, module, section, state.pages[state.index]);
}

/** 下一页只能使用当前分节返回的游标，错误页不会覆盖其它模块。 */
async function next(module: TraceModule, section: TraceSectionName) {
  const state = stateFor(module, section);
  if (!state || state.pending) return;
  const originalGeneration = generation;
  state.error = '';
  try {
    if (state.pages[state.index + 1]) {
      state.index++;
      view.value = replaceIdentityPage(view.value, module, section, state.pages[state.index]);
      return;
    }
    const cursor = pageCursor(state.pages[state.index]);
    if (cursor === undefined) return;
    if (state.cursors.includes(cursor)) throw new Error('服务端分页游标未推进，请重新查询');
    state.pending = true;
    const response = await traceIdentity(view.value.identityId, { module, section, cursor, limit: 500 });
    if (generation !== originalGeneration) return;
    const merged = mergeIdentitySection(view.value, { module, section }, response.data);
    const page = merged.parts.find((part) => part.module === module)!.sections[section]!;
    state.cursors.push(cursor);
    state.pages.push(page);
    state.index++;
    view.value = merged;
  } catch (cause) {
    if (generation === originalGeneration) state.error = cause instanceof Error ? cause.message : '分节分页读取失败';
  } finally {
    if (generation === originalGeneration) state.pending = false;
  }
}
</script>
