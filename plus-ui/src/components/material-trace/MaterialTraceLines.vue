<template>
  <div class="material-trace-lines">
    <el-divider content-position="left">实物 SN 明细</el-divider>
    <el-collapse>
      <el-collapse-item v-for="(row, index) in rows" :key="String(row.detailId ?? row.lineId ?? index)" :name="index">
        <template #title
          >{{ index + 1 }}. {{ row.materialCode || '待选物料' }} {{ row.materialName }} · 已选 {{ row.snItems?.length || 0 }} 件</template
        >
        <SnSelector
          :row="row"
          :header="header"
          :scene="scene"
          :quantity-key="quantityKey"
          :disabled="disabled"
          @change="Object.assign(row, $event)"
        />
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import type { TraceRow } from './model';
import SnSelector from './SnSelector.vue';
withDefaults(defineProps<{ rows: TraceRow[]; header: TraceRow; scene: string; quantityKey?: string; disabled?: boolean }>(), {
  quantityKey: 'quantity',
  disabled: false
});
</script>
