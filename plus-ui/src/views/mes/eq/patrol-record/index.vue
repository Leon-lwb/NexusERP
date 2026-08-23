<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="结果" prop="result">
            <el-select v-model="queryParams.result" placeholder="全部" clearable style="width: 100%">
              <el-option v-for="d in mes_patrol_result" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="任务ID" prop="taskId" width="100" />
      <el-table-column label="设备编码" prop="equipmentCode" width="110" />
      <el-table-column label="设备名称" prop="equipmentName" min-width="120" show-overflow-tooltip />
      <el-table-column label="检查项" prop="checkItem" width="120" />
      <el-table-column label="实际值" prop="actualValue" width="100" />
      <el-table-column label="结果" prop="result" align="center" width="80">
        <template #default="s"><dict-tag :options="mes_patrol_result" :value="s.row.result" /></template>
      </el-table-column>
      <el-table-column label="照片" prop="photoUrl" width="90" align="center">
        <template #default="s">
          <el-image v-if="s.row.photoUrl" :src="s.row.photoUrl" :preview-src-list="[s.row.photoUrl]" fit="cover" style="width: 60px; height: 60px" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="执行人" prop="executorName" width="90" />
      <el-table-column label="执行时间" prop="executeTime" width="160" />
      <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>
<script setup lang="ts" name="MesEqPatrolRecord">
import { listPatrolRecord } from '@/api/mes/eq/patrol';
import type { EqPatrolRecordVO, EqPatrolRecordQuery } from '@/api/mes/eq/patrol';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_patrol_result } = toRefs<any>(proxy?.useDict('mes_patrol_result'));

// 巡检记录为只读列表页（无新增/修改/删除），以下占位仅为满足 useCrud 的 API 签名，不会被调用
const formRef = ref<FormInstance>();
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref<EqPatrolRecordQuery>({ pageNum: 1, pageSize: 10, result: undefined });

const {
  loading,
  list: dataList,
  total,
  showSearch,
  getList,
  handleQuery
} = useCrud<EqPatrolRecordVO, EqPatrolRecordQuery>({
  apis: { list: listPatrolRecord, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'recordId',
  entityName: '巡检记录',
  formRef,
  queryParams,
  initForm: () => ({})
});

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
