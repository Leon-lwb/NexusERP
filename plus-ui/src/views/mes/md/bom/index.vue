<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
        <el-form-item label="BOM版本" prop="bomVersion">
          <el-input v-model="queryParams.bomVersion" placeholder="请输入BOM版本" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="BOM名称" prop="bomName">
          <el-input v-model="queryParams.bomName" placeholder="请输入BOM名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="BOM版本" prop="bomVersion" width="120" align="center" />
        <el-table-column label="BOM名称" prop="bomName" show-overflow-tooltip />
        <el-table-column label="父项物料编码" prop="parentMaterialCode" width="160" />
        <el-table-column label="父项物料名称" prop="parentMaterialName" show-overflow-tooltip />
        <el-table-column label="BOM类型" prop="bomType" width="100" align="center" />
        <el-table-column label="是否当前版本" prop="isCurrent" width="120" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isCurrent === 1" type="success">是</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="scope">
            <el-tag :type="Number(scope.row.status) === 1 ? 'success' : 'danger'">
              {{ Number(scope.row.status) === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesBomReadonly">
import { listBom } from '@/api/erp/bd/bom/index';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<any[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<any>({
  pageNum: 1,
  pageSize: 10,
  bomVersion: undefined,
  bomName: undefined
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listBom(queryParams.value);
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
