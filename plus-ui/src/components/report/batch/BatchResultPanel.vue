<template>
  <section class="batch-result-panel">
    <el-card v-if="selected && canList" v-loading="detailLoading" shadow="never" class="report-panel">
      <div class="report-section-heading">
        <div>
          <h2>逐项结果</h2>
          <p class="report-section-description">当前批次：{{ selected }}；显示顺序即提交顺序。</p>
        </div>
        <div>
          <el-button @click="refreshItems(selected)">刷新结果</el-button>
          <el-button v-if="isOwner && canStart && items.some(needsStart)" :loading="writeBusy" @click="start(selected)">补充入队</el-button>
          <el-button v-if="canReadPdf && allSucceeded" type="primary" @click="pdfOpen = true">合并PDF预览/下载</el-button>
        </div>
      </div>
      <el-alert
        v-if="detailFailed || detailError"
        :title="detailError || '逐项结果读取失败，请刷新重试。'"
        type="error"
        :closable="false"
        class="mb-4"
      />
      <el-alert v-if="expired" title="部分单据文件已过期，请创建新批次。" type="warning" :closable="false" class="mb-4" />
      <el-button v-if="expired" class="mb-4" @click="emit('new-batch')">新建批次</el-button>
      <el-table :data="items" empty-text="当前批次暂无结果">
        <el-table-column label="序号" width="70"
          ><template #default="scope">{{ scope.row.number + 1 }}</template></el-table-column
        >
        <el-table-column label="单据" min-width="180"
          ><template #default="scope"
            ><span :title="documentAt(scope.row.number)?.id">{{ documentAt(scope.row.number)?.code || '单据 ' + (scope.row.number + 1) }}</span>
            <div>{{ documentAt(scope.row.number)?.counterparty }}</div></template
          ></el-table-column
        >
        <el-table-column prop="reportVersionId" label="固定版本" min-width="180" />
        <el-table-column label="进度" min-width="180">
          <template #default="scope">
            <ReportStatus :status="scope.row.state" />
            <div v-if="scope.row.phase">
              {{ phaseLabels[scope.row.phase] || scope.row.phase }} {{ scope.row.processedRows ?? 0
              }}<span v-if="scope.row.totalRows != null"> / {{ scope.row.totalRows }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="错误/有效期" min-width="230">
          <template #default="scope">
            <div v-if="scope.row.error">{{ jobError(scope.row.error) }}（{{ scope.row.error }}）</div>
            <div v-if="scope.row.expires">{{ scope.row.expires }}</div>
            <span v-if="['FAILED', 'CANCELLED', 'EXPIRED'].includes(scope.row.state) && scope.row.jobId && !scope.row.canRetry"
              >原任务不可恢复，请新建批次</span
            >
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="180">
          <template #default="scope">
            <el-button v-if="canReadPdf && scope.row.canRetry" link :disabled="busy" @click="retryItem(scope.row)"
              >重试原任务（{{ scope.row.retryRound }}）</el-button
            >
            <el-button v-if="isOwner && canStart && needsStart(scope.row)" link :disabled="busy" @click="retryItem(scope.row)">补充入队</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <ReportPdfPreview
      v-model:open="pdfOpen"
      :source-id="pdfSourceId"
      :load="loadPdf"
      :filename="`nexus-batch-${selected}.pdf`"
      :can-download="canDeliverPdf"
    />
  </section>
</template>
<script setup lang="ts">
import { toRefs, type UnwrapNestedRefs } from 'vue';
import type { useBatchResult } from './useBatchResult';
import type { BusinessDocumentRef } from '../businessDocument';
import { batchDocumentAt } from '../businessDocument';
import { jobError } from '@/views/report/model';
import ReportStatus from '../ReportStatus.vue';
import ReportPdfPreview from '../ReportPdfPreview.vue';
/** 逐项结果由批次页和业务抽屉共用，展示信息不参与授权。 */
const props = defineProps<{ state: UnwrapNestedRefs<ReturnType<typeof useBatchResult>>; documents?: BusinessDocumentRef[] }>();
const emit = defineEmits<{ 'new-batch': [] }>();
const {
  selected,
  items,
  detailLoading,
  detailFailed,
  detailError,
  writeBusy,
  pdfOpen,
  canList,
  canDeliverPdf,
  pdfSourceId,
  canStart,
  isOwner,
  canReadPdf,
  allSucceeded,
  expired,
  busy,
  refreshItems,
  needsStart,
  start,
  retryItem,
  loadPdf
} = toRefs(props.state);
const phaseLabels: Record<string, string> = {
  QUEUED: '排队中',
  PREPARING: '准备中',
  SNAPSHOT: '固定快照',
  LAYOUT: '固定排版',
  WRITING: '写入文件',
  COMPLETED: '完成'
};
/** 仅按原提交序号匹配本地单据展示字段。 */
function documentAt(number: number) {
  return batchDocumentAt(props.state.items, props.documents, number);
}
</script>
<style scoped>
.batch-result-panel {
  min-width: 0;
}
</style>
