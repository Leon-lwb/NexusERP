<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="工单编号" prop="woNo">
            <el-input v-model="queryParams.woNo" placeholder="请输入工单编号" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="产品名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入产品名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="woStatus">
            <el-select v-model="queryParams.woStatus" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in prd_wo_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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

    <!-- 操作栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:pro:work:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:pro:work:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="workOrderList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工单编号" prop="woNo" />
      <el-table-column label="产品编码" prop="materialCode" />
      <el-table-column label="产品名称" prop="materialName" min-width="120" show-overflow-tooltip />
      <!-- FE-MES-02：计划数量带计量单位（后端 ProWorkOrderVo.unitName），不再是无单位裸数字 -->
      <el-table-column label="计划数量" prop="plannedQty" align="right">
        <template #default="scope">{{ fmtQty(scope.row.plannedQty) }} {{ scope.row.unitName || '' }}</template>
      </el-table-column>
      <!-- FE-MES-02：完工进度（已完工/报废），此前列表完全看不到完工情况 -->
      <el-table-column label="已完工" prop="completedQty" align="right">
        <template #default="scope">
          {{ fmtQty(scope.row.completedQty) }}
          <span v-if="progressOf(scope.row) !== '-'" class="text-gray-400">（{{ progressOf(scope.row) }}）</span>
        </template>
      </el-table-column>
      <el-table-column label="报废" prop="scrapQty" align="right">
        <template #default="scope">{{ fmtQty(scope.row.scrapQty) }}</template>
      </el-table-column>
      <el-table-column label="单位" prop="unitName" align="center" />
      <el-table-column label="优先级" prop="priority" align="center">
        <template #default="scope">{{ scope.row.priority ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="来源" prop="sourceType" align="center">
        <template #default="scope">
          <dict-tag :options="mes_wo_source_type" :value="scope.row.sourceType" />
        </template>
      </el-table-column>
      <el-table-column label="车间" prop="workshopName" min-width="120" show-overflow-tooltip />
      <!-- FE-MES-02：日期统一走 fmtDate（与 mes/eq/oee、mes/eq/repair/statistics 同型），
           直接输出后端 Date 序列化串会带时间戳且列宽不定 -->
      <el-table-column label="计划开工" prop="planStartDate" align="center">
        <template #default="scope">{{ fmtDate(scope.row.planStartDate) }}</template>
      </el-table-column>
      <el-table-column label="计划完工" prop="planEndDate" align="center">
        <template #default="scope">{{ fmtDate(scope.row.planEndDate) }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="woStatus" align="center">
        <template #default="scope">
          <dict-tag :options="prd_wo_status" :value="scope.row.woStatus" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-if="scope.row.woStatus === 'CREATED'"
            v-hasPermi="['mes:pro:work:edit']"
            >修改</el-button
          >
          <el-button
            link
            type="primary"
            icon="Promotion"
            @click="handleRelease(scope.row)"
            v-if="scope.row.woStatus === 'CREATED'"
            v-hasPermi="['mes:pro:work:edit']"
            >下达</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="MesWorkOrder">
import { listWorkOrder, getWorkOrder, addWorkOrder, updateWorkOrder, delWorkOrder, releaseWorkOrder } from '@/api/mes/pro/workorder';
import type { ProWorkOrderVO, ProWorkOrderQuery } from '@/api/mes/pro/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { prd_wo_status, mes_wo_source_type } = toRefs<any>(proxy.useDict('prd_wo_status', 'mes_wo_source_type'));
const router = useRouter();

const queryParams = ref<ProWorkOrderQuery>({
  pageNum: 1,
  pageSize: 10,
  woNo: undefined,
  materialName: undefined,
  woStatus: undefined
});

// 列表骨架（新增/修改/详情为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: workOrderList,
  total,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange
} = useCrud({
  apis: { list: listWorkOrder, get: getWorkOrder, add: addWorkOrder, update: updateWorkOrder, del: delWorkOrder },
  idField: 'mesWoId',
  entityName: '工单',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({})
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 详情按钮操作 */
function handleDetail(row: ProWorkOrderVO) {
  router.push(`/mes/pro/workorder/detail/${row.mesWoId}`);
}

/** 新增按钮操作 */
function handleAdd() {
  router.push({ path: '/mes/pro/workorder/form', query: { action: 'add' } });
}

/** 修改按钮操作 */
function handleUpdate(row: ProWorkOrderVO) {
  router.push({ path: '/mes/pro/workorder/form', query: { id: row.mesWoId, action: 'edit' } });
}

/** 下达工单 */
async function handleRelease(row: ProWorkOrderVO) {
  await proxy.$modal.confirm('确认下达工单"' + row.woNo + '"？');
  await releaseWorkOrder(row.mesWoId);
  proxy.$modal.msgSuccess('下达成功');
  getList();
}

/** 数量千分位格式化（FE-MES-02） */
const fmtQty = (v?: number | string | null) => (v === null || v === undefined || v === '' ? '-' : Number(v).toLocaleString('en-US'));

/** 完工进度（已完工 / 计划数量） */
const progressOf = (row: ProWorkOrderVO) => {
  const planned = Number(row.plannedQty || 0);
  if (!planned) return '-';
  return Math.round((Number(row.completedQty || 0) / planned) * 100) + '%';
};

/** 日期格式化：截取到日（与 mes/eq/oee、mes/eq/repair/statistics 同型实现） */
const fmtDate = (s?: string) => (s ? String(s).substring(0, 10) : '-');

onActivated(() => {
  getList();
});
</script>
