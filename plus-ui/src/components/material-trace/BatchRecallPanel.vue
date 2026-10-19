<template>
  <el-card class="batch-recall mt-3" v-loading="initialLoading">
    <template #header>组件{{ componentIdentityId ? '身份去向' : '批次召回' }} · {{ componentIdentityId || batchId }}</template>
    <template v-if="current">
      <BatchTracePanel v-if="batchId" :batch-id="batchId" :initial="batchFacts" />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="召回范围">{{ current.scope }}</el-descriptions-item>
        <el-descriptions-item label="正式身份页 / 深度">第 {{ index + 1 }} 页 / {{ current.depthReached ?? '未提供' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        :title="boundary"
        :type="
          current.truncated ||
          current.hasMore ||
          currentScope?.truncated ||
          currentScope?.page.hasMore ||
          currentBatch?.truncated ||
          currentBatch?.page.hasMore
            ? 'warning'
            : 'info'
        "
        :closable="false"
        class="my-3"
      />
      <el-alert
        v-for="(gap, position) in current.evidenceGaps || []"
        :key="position"
        :title="traceCell(gap)"
        type="warning"
        :closable="false"
        class="mb-2"
      />
      <el-table :data="current.rows" border max-height="520">
        <el-table-column prop="identityId" label="正式实物身份" min-width="200" />
        <el-table-column label="影响精度" min-width="170">
          <template #default="{ row }"
            ><el-tag :type="recallPrecision(row.scope).type">{{ recallPrecision(row.scope).label }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }"><el-button link type="primary" @click="emit('identity', row.identityId)">查看实物追溯</el-button></template>
        </el-table-column>
      </el-table>
      <p>每页最多 500 件；逐件确认、批次可能影响和工单可能影响分别展示。当前页不代表完整召回范围。</p>
      <el-button :disabled="index === 0 || identityLoading" @click="index--">上一页</el-button>
      <el-button :disabled="!current.hasMore" :loading="identityLoading" @click="next">召回下一页</el-button>
      <el-alert v-if="error" :title="error" type="warning" :closable="false" class="mt-2" />

      <h4 class="mt-5">受影响成品批次 · 可能影响范围</h4>
      <el-alert
        title="这些批次按批次或工单范围列为可能受影响，不表示每件产品已确认使用该组件。请选择批次单独查询当前库存与实际客户去向。"
        type="warning"
        :closable="false"
        class="my-3"
      />
      <template v-if="currentBatch">
        <p>
          受影响批次第 {{ batchIndex + 1 }} 页 ·
          {{ currentBatch.page.hasMore ? '还有后续页，请独立分页核对' : '本页无后续游标，覆盖范围仍以证据与截断提示为准' }}
        </p>
        <el-table :data="currentBatch.page.rows" border max-height="420">
          <el-table-column prop="batchId" label="受影响批次身份" min-width="210" />
          <el-table-column label="影响精度" min-width="180"
            ><template #default="{ row }"
              ><el-tag type="warning">{{ recallPrecision(row.scope || 'UNKNOWN').label }}</el-tag></template
            ></el-table-column
          >
          <el-table-column label="实际批次事实" min-width="270"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="affectedBatchId = row.batchId">查看受影响批次事实/客户去向</el-button></template
            ></el-table-column
          >
        </el-table>
        <el-alert
          v-for="(gap, position) in currentBatch.evidenceGaps"
          :key="position"
          :title="traceCell(gap)"
          type="warning"
          :closable="false"
          class="mt-2"
        />
        <el-button class="mt-3" :disabled="batchIndex === 0 || batchLoading" @click="batchIndex--">受影响批次上一页</el-button>
        <el-button :disabled="!currentBatch.page.hasMore" :loading="batchLoading" @click="nextBatch">受影响批次下一页</el-button>
      </template>
      <el-alert v-else title="服务端未提供受影响批次分页，不能据此判定没有受影响成品批次。" type="warning" :closable="false" />
      <el-alert v-if="batchError" :title="batchError" type="warning" :closable="false" class="mt-2" />
      <BatchTracePanel v-if="affectedBatchId" :batch-id="affectedBatchId" />

      <h4 class="mt-5">工单范围影响 · 未确认逐件消耗</h4>
      <el-alert
        title="此表保留组件批次涉及的工单范围事实。已分配数量与未分配数量用于说明证据覆盖，不能据此认定某个产品 SN 已确认使用该批次。"
        type="warning"
        :closable="false"
        class="my-3"
      />
      <template v-if="currentScope">
        <p>
          工单范围第 {{ scopeIndex + 1 }} 页 ·
          {{ currentScope.page.hasMore ? '还有后续页，请独立分页核对' : '本页无后续游标，覆盖范围以证据缺口与截断提示为准' }}
        </p>
        <el-table :data="currentScope.page.rows" border max-height="420">
          <el-table-column prop="module" label="模块" width="90" />
          <el-table-column label="证据范围" min-width="190"
            ><template #default><el-tag type="warning">工单范围 · 未确认逐件</el-tag></template></el-table-column
          >
          <el-table-column prop="mesWoId" label="MES 工单 ID" min-width="190" />
          <el-table-column prop="consumptionId" label="消耗记录 ID" min-width="190" />
          <el-table-column prop="componentMaterialId" label="组件物料 ID" min-width="190" />
          <el-table-column prop="consumedQuantity" label="工单消耗数量" min-width="180" />
          <el-table-column prop="allocatedQuantity" label="已分配数量" min-width="180" />
          <el-table-column prop="unallocatedQuantity" label="未分配数量" min-width="180" />
        </el-table>
        <el-alert
          v-for="(gap, position) in currentScope.evidenceGaps"
          :key="position"
          :title="traceCell(gap)"
          type="warning"
          :closable="false"
          class="mt-2"
        />
        <el-button class="mt-3" :disabled="scopeIndex === 0 || scopeLoading" @click="scopeIndex--">工单范围上一页</el-button>
        <el-button :disabled="!currentScope.page.hasMore" :loading="scopeLoading" @click="nextScope">工单范围下一页</el-button>
      </template>
      <el-alert v-else title="服务端未提供工单范围分页，不能据此判定该范围没有影响。" type="warning" :closable="false" />
      <el-alert v-if="scopeError" :title="scopeError" type="warning" :closable="false" class="mt-2" />
    </template>
    <el-alert v-else-if="error" :title="error" type="warning" :closable="false" class="mt-2" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { recallBatch, whereUsedIdentity } from '@/api/erp/wm/trace';
import BatchTracePanel from './BatchTracePanel.vue';
import type {
  BatchRecallScopePage,
  BatchRecallBatchPage,
  ComponentImpactResult,
  BatchRecallPageQuery,
  BatchTraceResult
} from '@/api/erp/wm/trace/types';
import { pageCursor, recallBoundary, recallPrecision, traceCell } from './tracePaging';

const props = defineProps<{ batchId?: string; componentIdentityId?: string }>();
const emit = defineEmits<{ identity: [identityId: string] }>();
const pages = ref<ComponentImpactResult[]>([]);
const batchFacts = ref<BatchTraceResult>();
const index = ref(0);
const scopePages = ref<ReturnType<typeof scopeSnapshot>[]>([]);
const scopeIndex = ref(0);
const batchPages = ref<ReturnType<typeof batchSnapshot>[]>([]);
const batchIndex = ref(0);
const batchLoading = ref(false);
const batchError = ref('');
const affectedBatchId = ref('');
const currentBatch = computed(() => batchPages.value[batchIndex.value]);
const initialLoading = ref(false);
const identityLoading = ref(false);
const scopeLoading = ref(false);
const error = ref('');
const scopeError = ref('');
const current = computed(() => pages.value[index.value]);
const currentScope = computed(() => scopePages.value[scopeIndex.value]);
const boundary = computed(() =>
  recallBoundary({
    ...current.value,
    scopeImpacts: currentScope.value?.page,
    batchImpacts: currentBatch.value?.page,
    truncated: current.value?.truncated || currentScope.value?.truncated || currentBatch.value?.truncated,
    truncatedByDepth: current.value?.truncatedByDepth || currentScope.value?.truncatedByDepth || currentBatch.value?.truncatedByDepth,
    truncatedByCapacity: current.value?.truncatedByCapacity || currentScope.value?.truncatedByCapacity || currentBatch.value?.truncatedByCapacity
  })
);
let generation = 0;
let identityRequests: string[] = ['0'];
let scopeRequests: (string | undefined)[] = [undefined];
let batchRequests: (string | undefined)[] = [undefined];
function query(page?: BatchRecallPageQuery) {
  return props.componentIdentityId ? whereUsedIdentity(props.componentIdentityId, page) : recallBatch(props.batchId!, page);
}
function matchesRoot(result: ComponentImpactResult) {
  return props.componentIdentityId
    ? 'componentIdentityId' in result && result.componentIdentityId === props.componentIdentityId
    : 'batchId' in result && result.batchId === props.batchId;
}

/** 两个分页分别缓存；换批次后旧请求的迟到结果均不能覆盖新查询。 */
watch(
  () => [props.batchId, props.componentIdentityId] as const,
  async ([batchId, identityId]) => {
    const request = ++generation;
    pages.value = [];
    batchFacts.value = undefined;
    scopePages.value = [];
    batchPages.value = [];
    batchIndex.value = 0;
    batchRequests = [undefined];
    batchLoading.value = false;
    batchError.value = '';
    affectedBatchId.value = '';
    index.value = 0;
    scopeIndex.value = 0;
    identityRequests = ['0'];
    scopeRequests = [undefined];
    error.value = '';
    scopeError.value = '';
    identityLoading.value = false;
    scopeLoading.value = false;
    initialLoading.value = false;
    if (!batchId && !identityId) return;
    if (batchId && identityId) {
      error.value = '请选择一个明确的组件批次或组件身份';
      return;
    }
    initialLoading.value = true;
    try {
      const response = await query();
      if (request !== generation) return;
      if (!matchesRoot(response.data)) throw new Error('组件影响响应身份不一致');
      pages.value = [response.data];
      if (batchId && 'parts' in response.data && Array.isArray(response.data.parts))
        batchFacts.value = { batchId, scope: 'BATCH', parts: response.data.parts };
      if (response.data.scopeImpacts) scopePages.value = [scopeSnapshot(response.data, response.data.scopeImpacts)];
      if (response.data.batchImpacts) batchPages.value = [batchSnapshot(response.data, response.data.batchImpacts)];
    } catch (cause) {
      if (request === generation) error.value = cause instanceof Error ? cause.message : '召回查询失败';
    } finally {
      if (request === generation) initialLoading.value = false;
    }
  },
  { immediate: true }
);

/** 正式身份翻页只更新正式身份结果；保留正在显示的工单范围页。 */
async function next() {
  if (!current.value || identityLoading.value) return;
  error.value = '';
  const request = generation;
  try {
    if (pages.value[index.value + 1]) {
      index.value++;
      return;
    }
    const cursor = pageCursor(current.value);
    if (cursor === undefined) return;
    if (identityRequests.includes(cursor)) throw new Error('召回游标未推进，请重新查询');
    identityLoading.value = true;
    const response = await query({
      afterIdentity: cursor,
      scopeCursor: scopeRequests[scopeIndex.value],
      batchCursor: batchRequests[batchIndex.value],
      depth: 10,
      limit: 500
    });
    if (request !== generation) return;
    if (!matchesRoot(response.data)) throw new Error('组件影响响应身份不一致');
    identityRequests.push(cursor);
    pages.value.push(response.data);
    index.value++;
  } catch (cause) {
    if (request === generation) error.value = cause instanceof Error ? cause.message : '召回分页失败';
  } finally {
    if (request === generation) identityLoading.value = false;
  }
}

/** 工单页自己的证据和截断必须一起缓存，不能被身份页的完整状态掩盖。 */
function scopeSnapshot(response: ComponentImpactResult, page: BatchRecallScopePage) {
  return {
    page,
    evidenceGaps: response.evidenceGaps || [],
    truncated: response.truncated,
    truncatedByDepth: response.truncatedByDepth,
    truncatedByCapacity: response.truncatedByCapacity
  };
}

/** 工单范围使用独立scopeCursor，响应的正式身份部分不得覆盖另一组分页。 */
async function nextScope() {
  if (!currentScope.value || scopeLoading.value) return;
  scopeError.value = '';
  const request = generation;
  try {
    if (scopePages.value[scopeIndex.value + 1]) {
      scopeIndex.value++;
      return;
    }
    const cursor = pageCursor(currentScope.value.page);
    if (cursor === undefined) return;
    if (scopeRequests.includes(cursor)) throw new Error('工单范围游标未推进，请重新查询');
    scopeLoading.value = true;
    const response = await query({
      afterIdentity: identityRequests[index.value],
      scopeCursor: cursor,
      batchCursor: batchRequests[batchIndex.value],
      depth: 10,
      limit: 500
    });
    if (request !== generation) return;
    if (!matchesRoot(response.data)) throw new Error('组件影响响应身份不一致');
    if (!response.data.scopeImpacts) throw new Error('响应未提供工单范围分页，已保留当前页');
    scopeRequests.push(cursor);
    scopePages.value.push(scopeSnapshot(response.data, response.data.scopeImpacts));
    scopeIndex.value++;
  } catch (cause) {
    if (request === generation) scopeError.value = cause instanceof Error ? cause.message : '工单范围分页失败';
  } finally {
    if (request === generation) scopeLoading.value = false;
  }
}

/** 受影响批次使用第三个独立游标，列表页不会预取所有批次库存或客户明细。 */
function batchSnapshot(response: ComponentImpactResult, page: BatchRecallBatchPage) {
  return { ...scopeSnapshot(response, { rows: [], hasMore: page.hasMore }), page };
}
async function nextBatch() {
  if (!currentBatch.value || batchLoading.value) return;
  batchError.value = '';
  const request = generation;
  try {
    if (batchPages.value[batchIndex.value + 1]) {
      batchIndex.value++;
      return;
    }
    const cursor = pageCursor(currentBatch.value.page);
    if (cursor === undefined) return;
    if (batchRequests.includes(cursor)) throw new Error('受影响批次游标未推进，请重新查询');
    batchLoading.value = true;
    const response = await query({
      afterIdentity: identityRequests[index.value],
      scopeCursor: scopeRequests[scopeIndex.value],
      batchCursor: cursor,
      depth: 10,
      limit: 500
    });
    if (request !== generation) return;
    if (!matchesRoot(response.data)) throw new Error('组件影响响应身份不一致');
    if (!response.data.batchImpacts) throw new Error('响应未提供受影响批次分页，已保留当前页');
    batchRequests.push(cursor);
    batchPages.value.push(batchSnapshot(response.data, response.data.batchImpacts));
    batchIndex.value++;
  } catch (cause) {
    if (request === generation) batchError.value = cause instanceof Error ? cause.message : '受影响批次分页失败';
  } finally {
    if (request === generation) batchLoading.value = false;
  }
}
</script>
