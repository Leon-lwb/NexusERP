<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form :inline="true" label-width="100px" class="query-form">
      <el-form-item label="物料选择">
        <el-select
          v-model="selectedMaterialId"
          placeholder="请选择物料"
          filterable
          remote
          :remote-method="searchMaterial"
          :loading="materialLoading"
          style="width: 300px"
          @change="handleMaterialChange"
        >
          <el-option
            v-for="item in materialOptions"
            :key="item.materialId"
            :label="item.materialCode + ' - ' + item.materialName"
            :value="item.materialId"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="queryPriceComparison" :disabled="!selectedMaterialId">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="warning" icon="Download" @click="handleExport" :disabled="priceList.length === 0">导出</el-button>
      </el-form-item>
    </el-form>

    <!-- 物料信息卡片 -->
    <el-card v-if="selectedMaterial" class="material-info-card" style="margin-bottom: 20px;">
      <template #header>
        <span class="card-title">物料信息</span>
      </template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="物料编码">{{ selectedMaterial.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ selectedMaterial.materialName }}</el-descriptions-item>
        <el-descriptions-item label="规格型号">{{ selectedMaterial.materialSpec }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ selectedMaterial.categoryName }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 价格对比表格 -->
    <el-card v-if="priceList.length > 0">
      <template #header>
        <div class="card-header">
          <span class="card-title">供应商价格对比</span>
          <el-tag type="info">共 {{ priceList.length }} 家供应商</el-tag>
        </div>
      </template>
      <el-table :data="priceList" border stripe>
        <el-table-column label="排名" width="70" align="center" type="index" />
        <el-table-column label="供应商编码" prop="vendorCode" width="120" />
        <el-table-column label="供应商名称" prop="vendorName" min-width="180" />
        <el-table-column label="单价" prop="price" width="120" align="right">
          <template #default="scope">
            <span :class="{ 'price-lowest': scope.$index === 0 }">
              {{ formatPrice(scope.row.price) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="币种" prop="currency" width="80" align="center" />
        <el-table-column label="供货周期(天)" prop="leadTime" width="110" align="center" />
        <el-table-column label="最小订货量" prop="minOrderQty" width="110" align="right">
          <template #default="scope">
            {{ formatPrice(scope.row.minOrderQty) }}
          </template>
        </el-table-column>
        <el-table-column label="默认供应商" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isDefault === 1 ? 'success' : 'info'" size="small">
              {{ scope.row.isDefault === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" prop="priority" width="80" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
              {{ scope.row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 价格统计信息 -->
      <el-row :gutter="20" style="margin-top: 20px;">
        <el-col :span="6">
          <el-statistic title="最低价格" :value="lowestPrice" :precision="4" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="最高价格" :value="highestPrice" :precision="4" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="平均价格" :value="averagePrice" :precision="4" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="价差比例" :value="priceDiffPercent" :precision="2" suffix="%" />
        </el-col>
      </el-row>
    </el-card>

    <!-- 空状态 -->
    <el-empty v-if="selectedMaterialId && priceList.length === 0 && !loading" description="该物料暂无供应商报价信息" />
  </div>
</template>

<script setup lang="ts" name="PriceComparison">
import { listMaterial } from "@/api/erp/bd/material";
import request from '@/utils/request';

const { proxy } = getCurrentInstance();

// 物料选择相关
const selectedMaterialId = ref<number | undefined>(undefined);
const selectedMaterial = ref<any>(null);
const materialOptions = ref<any[]>([]);
const materialLoading = ref(false);

// 价格数据
const priceList = ref<any[]>([]);
const loading = ref(false);

/** 格式化价格 */
function formatPrice(price: number) {
  if (price === null || price === undefined) return '-';
  return price.toFixed(4);
}

/** 最低价格 */
const lowestPrice = computed(() => {
  if (priceList.value.length === 0) return 0;
  return Math.min(...priceList.value.map(item => item.price || 0));
});

/** 最高价格 */
const highestPrice = computed(() => {
  if (priceList.value.length === 0) return 0;
  return Math.max(...priceList.value.map(item => item.price || 0));
});

/** 平均价格 */
const averagePrice = computed(() => {
  if (priceList.value.length === 0) return 0;
  const sum = priceList.value.reduce((acc, item) => acc + (item.price || 0), 0);
  return sum / priceList.value.length;
});

/** 价差比例 */
const priceDiffPercent = computed(() => {
  if (lowestPrice.value === 0) return 0;
  return ((highestPrice.value - lowestPrice.value) / lowestPrice.value) * 100;
});

/** 搜索物料 */
function searchMaterial(query: string) {
  if (query) {
    materialLoading.value = true;
    listMaterial({ materialName: query, pageNum: 1, pageSize: 20 }).then(response => {
      materialOptions.value = response.rows || [];
      materialLoading.value = false;
    });
  } else {
    materialOptions.value = [];
  }
}

/** 物料选择变化 */
function handleMaterialChange(materialId: number) {
  if (materialId) {
    selectedMaterial.value = materialOptions.value.find(item => item.materialId === materialId);
    queryPriceComparison();
  } else {
    selectedMaterial.value = null;
    priceList.value = [];
  }
}

/** 查询价格对比 */
function queryPriceComparison() {
  if (!selectedMaterialId.value) return;
  loading.value = true;
  request({
    url: '/erp/bd/materialVendor/report/priceComparison',
    method: 'get',
    params: { materialId: selectedMaterialId.value }
  }).then((response: any) => {
    priceList.value = response.data || [];
    loading.value = false;
  }).catch(() => {
    loading.value = false;
  });
}

/** 重置查询 */
function resetQuery() {
  selectedMaterialId.value = undefined;
  selectedMaterial.value = null;
  materialOptions.value = [];
  priceList.value = [];
}

/** 导出价格对比数据 */
function handleExport() {
  if (priceList.value.length === 0) {
    proxy.$modal.msgWarning("暂无数据可导出");
    return;
  }
  // 构建导出数据
  const exportData = priceList.value.map((item, index) => ({
    排名: index + 1,
    供应商编码: item.vendorCode,
    供应商名称: item.vendorName,
    单价: item.price,
    币种: item.currency,
    供货周期: item.leadTime,
    最小订货量: item.minOrderQty,
    是否默认: item.isDefault === 1 ? '是' : '否',
    优先级: item.priority
  }));
  proxy.download('erp/bd/materialVendor/export', {
    materialId: selectedMaterialId.value
  }, `price_comparison_${new Date().getTime()}.xlsx`);
}
</script>

<style scoped>
.material-info-card {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-weight: 600;
}
.price-lowest {
  color: #67c23a;
  font-weight: 600;
}
</style>
