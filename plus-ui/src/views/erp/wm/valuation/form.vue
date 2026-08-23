<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <el-button @click="cancel">返回</el-button>
          </div>
        </div>
      </template>

      <el-form :model="form" label-width="100px" disabled>
        <el-divider content-position="left">物料信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="物料编码">
              <el-input v-model="form.materialCode" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="物料名称">
              <el-input v-model="form.materialName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="规格">
              <el-input v-model="form.specification" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="单位">
              <el-input v-model="form.unitName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库">
              <el-input v-model="form.warehouseName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库位">
              <el-input v-model="form.locationName" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">库存估值</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="库存数量">
              <el-input v-model="form.quantity" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单位成本">
              <el-input v-model="form.unitCost">
                <template #prepend>¥</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库存总金额">
              <el-input v-model="form.totalCost">
                <template #prepend>¥</template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="ValuationForm">
import { listValuation } from '@/api/erp/wm/valuation';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

const title = ref('库存估值详情');

const form = ref<any>({
  materialId: null,
  materialCode: null,
  materialName: null,
  specification: null,
  unitName: null,
  quantity: null,
  unitCost: null,
  totalCost: null,
  warehouseId: null,
  warehouseName: null,
  locationName: null
});

onMounted(() => {
  const materialId = route.query.materialId as any;
  const warehouseId = route.query.warehouseId as any;

  if (materialId) {
    loadDetail(materialId, warehouseId);
  }
});

function loadDetail(materialId: any, warehouseId: any) {
  const params: any = { pageNum: 1, pageSize: 1, materialId: materialId };
  if (warehouseId) {
    params.warehouseId = warehouseId;
  }
  listValuation(params).then((res: any) => {
    if (res.rows && res.rows.length > 0) {
      form.value = res.rows[0];
    }
  });
}

function cancel() {
  router.push('/erp/wmRule/valuation');
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.card-actions {
  display: flex;
  gap: 10px;
}
:deep(.el-divider__text) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}
:deep(.el-form-item) {
  margin-bottom: 18px;
}
:deep(.el-input.is-disabled .el-input__wrapper) {
  background-color: #f5f7fa;
}
</style>
