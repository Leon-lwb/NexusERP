<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch">
        <el-form-item label="工艺路线编码" prop="routingCode">
          <el-input v-model="queryParams.routingCode" placeholder="请输入编码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="工艺路线名称" prop="routingName">
          <el-input v-model="queryParams.routingName" placeholder="请输入名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="dataList">
        <el-table-column label="工艺路线编码" prop="routingCode" width="160" />
        <el-table-column label="工艺路线名称" prop="routingName" show-overflow-tooltip />
        <el-table-column label="物料编码" prop="materialCode" width="140" />
        <el-table-column label="物料名称" prop="materialName" show-overflow-tooltip />
        <el-table-column label="版本" prop="version" width="80" align="center" />
        <el-table-column label="是否当前版本" prop="isCurrent" width="120" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isCurrent === 1" type="success">是</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
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

<script setup lang="ts" name="MesRoutingReadonly">
import { listRouting } from '@/api/erp/bd/routing/index';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<any[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = ref<any>({
  pageNum: 1,
  pageSize: 10,
  routingCode: undefined,
  routingName: undefined
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listRouting(queryParams.value);
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
