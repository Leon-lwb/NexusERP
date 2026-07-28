<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="返工编号" prop="reworkCode">
              <el-input v-model="queryParams.reworkCode" placeholder="请输入返工编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="NCR编号" prop="ncrId">
              <el-input-number v-model="queryParams.ncrId" :min="0" controls-position="right" placeholder="NCR ID" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="dict in mes_rework_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="返工编号" prop="reworkCode" min-width="150" />
        <el-table-column label="NCR编号" prop="ncrId" width="100" />
        <el-table-column label="原工单" prop="originalWoNo" width="140" />
        <el-table-column label="返工工单" prop="reworkWoNo" width="140" />
        <el-table-column label="返工数量" prop="reworkQty" align="right" width="100" />
        <el-table-column label="状态" prop="status" align="center" width="100">
          <template #default="scope"><dict-tag :options="mes_rework_status" :value="scope.row.status" /></template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="120" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="NcrRework">
import { listRework, getRework } from '@/api/mes/ncr/rework';
import type { NcrReworkOrderVO, NcrReworkOrderQuery } from '@/api/mes/ncr/rework';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_rework_status } = toRefs<any>(proxy?.useDict('mes_rework_status'));
const dataList = ref<NcrReworkOrderVO[]>([]);
const total = ref(0);
const loading = ref(true);
const queryParams = reactive<NcrReworkOrderQuery>({ pageNum: 1, pageSize: 10 } as any);
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();

const getList = async () => {
  loading.value = true;
  try {
    const res = await listRework(queryParams);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};
const resetQuery = () => {
  proxy!.resetForm('queryFormRef');
  handleQuery();
};

const handleView = (row: NcrReworkOrderVO) => {
  if (row.reworkWoId) {
    proxy!.$tab.openPage(`/mes/pro/workorder/detail/${row.reworkWoId}`, '工单详情');
  } else {
    proxy!.$modal.msgWarning('返工工单尚未生成');
  }
};

onMounted(() => getList());
</script>
