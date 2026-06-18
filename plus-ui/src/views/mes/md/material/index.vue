<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
        <el-form-item label="物料编码" prop="materialCode">
          <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="物料名称" prop="materialName">
          <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="物料编码" prop="materialCode" width="160" />
        <el-table-column label="物料名称" prop="materialName" show-overflow-tooltip />
        <el-table-column label="规格型号" prop="materialSpec" show-overflow-tooltip />
        <el-table-column label="物料类型" prop="materialType" width="100" />
        <el-table-column label="计量单位" prop="unitName" width="100" />
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'">
              {{ scope.row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesMaterialReadonly">
import { listMaterial } from '@/api/erp/bd/material/index';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<any[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<any>({
  pageNum: 1,
  pageSize: 10,
  materialCode: undefined,
  materialName: undefined
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listMaterial(queryParams.value);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};

onMounted(() => {
  getList();
});
</script>
