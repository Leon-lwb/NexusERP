<template>
  <div class="trace-evidence mb-3">
    <el-alert :title="traceScopeText(result)" type="info" :closable="false" show-icon />
    <el-alert v-if="!gaps.length" title="响应未提供历史缺口明细，不能据此认定历史实物链完整。" type="warning" :closable="false" class="mt-2" />
    <el-table v-else :data="gaps" size="small" class="mt-2" border>
      <el-table-column label="历史缺口 / 证据限制"
        ><template #default="{ row }">{{ typeof row === 'string' ? row : JSON.stringify(row) }}</template></el-table-column
      >
    </el-table>
    <el-table v-if="consumptions.length" :data="consumptions" size="small" class="mt-2" border>
      <el-table-column prop="productIdentityId" label="产品正式身份" min-width="180" />
      <el-table-column prop="componentMaterialId" label="组件物料" min-width="150" />
      <el-table-column prop="componentIdentityId" label="组件实物" min-width="180" />
      <el-table-column prop="lotId" label="MES 批次" min-width="150" />
      <el-table-column prop="erpBatchId" label="ERP 批次" min-width="150" />
      <el-table-column prop="quantity" label="实际消耗数量" min-width="140" />
      <el-table-column prop="entryType" label="分录方向" width="100" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { traceScopeText } from './model';
import type { TraceRow } from './model';
const props = defineProps<{ result: TraceRow }>();
const gaps = computed(() => props.result.evidenceGaps || props.result.legacyGaps || props.result.gaps || []);
const consumptions = computed(() => props.result.consumptions || props.result.componentConsumptions || []);
</script>
