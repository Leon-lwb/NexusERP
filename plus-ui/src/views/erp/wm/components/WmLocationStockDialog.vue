<template>
  <el-dialog v-model="visible" :title="title" width="960px" append-to-body destroy-on-close @close="handleClose">
    <el-table v-loading="loading" :data="rows" show-overflow-tooltip stripe>
      <el-table-column label="序号" type="index" width="55" align="center" />
      <el-table-column label="物料编码" align="center" prop="materialCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="120" show-overflow-tooltip />
      <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="单位" align="center" prop="baseUomName" width="80" />
      <el-table-column label="批次号" align="center" prop="batchCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="在库数量" align="center" prop="stockQuantity" width="100" />
      <el-table-column label="冻结" align="center" prop="frozenFlag" width="80">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.frozenFlag" />
        </template>
      </el-table-column>
      <el-table-column label="入库时间" align="center" prop="recptDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.recptDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="有效期" align="center" prop="expireDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.expireDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="pageNum" v-model:limit="pageSize" @pagination="load" />
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="WmLocationStockDialog">
import { listStock } from '@/api/erp/wm/stock';
import type { StockVO } from '@/api/erp/wm/stock/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_true_or_false } = toRefs<any>(proxy.useDict('sys_true_or_false'));

const props = defineProps<{
  modelValue: boolean;
  locationId?: number | string;
  locationName?: string;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
});

const loading = ref(false);
const rows = ref<StockVO[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);

const title = computed(() => `${props.locationName || '库位'} 库存明细`);

/** 加载库位库存明细（分页） */
const load = () => {
  if (!props.locationId) {
    rows.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  // locationId 雪花ID字符串透传，Number() 会截断
  listStock({ locationId: String(props.locationId), pageNum: pageNum.value, pageSize: pageSize.value })
    .then((response: any) => {
      rows.value = response.rows || [];
      total.value = response.total || 0;
    })
    .catch(() => {
      // 无 wm:stock:list 权限或查询失败：清空并友好提示，不阻塞 3D 浏览
      rows.value = [];
      total.value = 0;
      proxy.$modal.msgError('加载库存明细失败，可能无库存查看权限');
    })
    .finally(() => {
      loading.value = false;
    });
};

// 打开或切换库位时从第一页加载；分页时 pageNum 已由 v-model 更新，直接 load
watch(
  () => [props.modelValue, props.locationId],
  ([mv]) => {
    if (mv) {
      pageNum.value = 1;
      load();
    }
  }
);

const handleClose = () => {
  rows.value = [];
  total.value = 0;
};
</script>
