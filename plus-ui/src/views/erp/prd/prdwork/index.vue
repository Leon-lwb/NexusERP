<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="工单编号" prop="woNo">
            <el-input v-model="queryParams.woNo" placeholder="请输入工单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="计划名称" prop="planName">
            <el-input v-model="queryParams.planName" placeholder="请输入计划名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="产品名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入产品名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工单状态" prop="woStatus">
            <el-select v-model="queryParams.woStatus" placeholder="请选择工单状态" clearable style="width: 100%">
              <el-option v-for="dict in prd_wo_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="计划开始日期" prop="planStartDate">
            <el-date-picker
              clearable
              v-model="queryParams.planStartDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择计划开始日期"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['prd:work:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['prd:work:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['prd:work:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['prd:work:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['prd:work:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Check" :disabled="single" @click="handleRelease()" v-hasPermi="['prd:work:edit']">下达</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="VideoPlay" :disabled="single" @click="handleStart()" v-hasPermi="['prd:work:edit']">开始</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Finished" :disabled="single" @click="handleComplete()" v-hasPermi="['prd:work:edit']">完成</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Close" :disabled="single" @click="handleClose()" v-hasPermi="['prd:work:edit']">关闭</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="workList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工单编号" align="center" prop="woNo" />
      <el-table-column label="计划名称" align="center" prop="planName" min-width="120" show-overflow-tooltip />
      <el-table-column label="来源类型" align="center" prop="sourceType">
        <template #default="scope">
          <dict-tag :options="prd_source_type" :value="scope.row.sourceType" />
        </template>
      </el-table-column>
      <el-table-column label="销售订单编号" align="center" prop="sourceNo" />
      <el-table-column label="产品编码" align="center" prop="materialCode" />
      <el-table-column label="产品名称" align="center" prop="materialName" min-width="120" show-overflow-tooltip />
      <el-table-column label="产品规格" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="计划数量" align="center" prop="plannedQty" />
      <el-table-column label="已完成数量" align="center" prop="completedQty" />
      <el-table-column label="单位名称" align="center" prop="unitName" />
      <el-table-column label="计划开始日期" align="center" prop="planStartDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.planStartDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="计划结束日期" align="center" prop="planEndDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.planEndDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="工单状态" align="center" prop="woStatus">
        <template #default="scope">
          <dict-tag :options="prd_wo_status" :value="scope.row.woStatus" />
        </template>
      </el-table-column>
      <el-table-column label="质检状态" align="center" prop="qualityStatus">
        <template #default="scope">
          <dict-tag :options="bd_quality_status" :value="scope.row.qualityStatus" />
        </template>
      </el-table-column>
      <!-- 关联质检单号：后端 PrdWorkOrderVo.qualityCheckNo 已返回，此前前端零命中 -->
      <el-table-column label="质检单号" align="center" prop="qualityCheckNo" min-width="120" />
      <el-table-column label="是否倒冲" align="center" prop="backflushFlag">
        <template #default="scope">
          <dict-tag :options="prd_backflush_flag" :value="scope.row.backflushFlag" />
        </template>
      </el-table-column>
      <el-table-column label="车间名称" align="center" prop="workshopName" min-width="120" show-overflow-tooltip />
      <el-table-column label="生产部门" align="center" prop="deptName" min-width="120" show-overflow-tooltip />
      <el-table-column label="负责人" align="center" prop="responsibleName" />
      <el-table-column label="优先级" align="center" prop="priority">
        <template #default="scope">
          <dict-tag :options="so_order_priority" :value="scope.row.priority" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['prd:work:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['prd:work:remove']">删除</el-button>
          <el-button
            link
            type="primary"
            icon="Finished"
            @click="handleSubmit(scope.row)"
            v-if="scope.row.woStatus === 'CREATED'"
            v-hasPermi="['prd:work:edit']"
            >提交审批</el-button
          >
          <el-button
            link
            type="info"
            icon="View"
            @click="handleViewFlow(scope.row)"
            v-if="scope.row.woStatus === 'APPROVING' || scope.row.woStatus === 'APPROVED'"
            v-hasPermi="['prd:work:query']"
            >流程</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleRelease(scope.row)"
            v-if="scope.row.woStatus === 'APPROVED'"
            v-hasPermi="['prd:work:edit']"
            >下达</el-button
          >
          <el-button
            link
            type="primary"
            icon="VideoPlay"
            @click="handleStart(scope.row)"
            v-if="scope.row.woStatus === 'RELEASED'"
            v-hasPermi="['prd:work:edit']"
            >开始</el-button
          >
          <el-button
            link
            type="success"
            icon="Finished"
            @click="handleComplete(scope.row)"
            v-if="scope.row.woStatus === 'IN_PROGRESS'"
            v-hasPermi="['prd:work:edit']"
            >完成</el-button
          >
          <el-button
            link
            type="danger"
            icon="Close"
            @click="handleClose(scope.row)"
            v-if="scope.row.woStatus !== 'CLOSED' && scope.row.woStatus !== 'COMPLETED'"
            v-hasPermi="['prd:work:edit']"
            >关闭</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="PrdWork">
import {
  listWork,
  getWork,
  addWork,
  updateWork,
  delWork,
  releaseWork,
  startWork,
  completeWork,
  closeWork,
  submitWorkFlow,
  getWorkProcId
} from '@/api/erp/prd/work';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { prd_source_type, so_order_priority, prd_wo_status, prd_backflush_flag, bd_quality_status } = toRefs<any>(
  proxy.useDict('prd_source_type', 'so_order_priority', 'prd_wo_status', 'prd_backflush_flag', 'bd_quality_status')
);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  woNo: null,
  planName: null,
  materialName: null,
  planStartDate: null,
  woStatus: null
});

// 列表骨架（新增/修改/查看为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: workList,
  total,
  ids,
  single,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: { list: listWork, get: getWork, add: addWork, update: updateWork, del: delWork },
  idField: 'woId',
  entityName: '生产工单',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: 'erp/prd/work/export',
  exportFileName: 'work'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 修改按钮操作 */
function handleUpdate(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selected = workList.value.find((item: any) => item.woId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({
    path: '/erp/prd/prdwork/form',
    query: { id: row.woId, action: 'edit' }
  });
}

/** 新增按钮操作 */
function handleAdd() {
  router.push({
    path: '/erp/prd/prdwork/form',
    query: { action: 'add' }
  });
}

/** 查看按钮操作 */
function handleView(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selected = workList.value.find((item: any) => item.woId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({
    path: '/erp/prd/prdwork/form',
    query: { id: row.woId, action: 'view' }
  });
}

/** 解析目标工单：优先取行参数，其次取当前选中行 */
function resolveTargetWoId(row?: any): any {
  const woId = row?.woId || ids.value[0];
  if (!woId) {
    proxy.$modal.msgWarning('请选择一条工单');
    return null;
  }
  return woId;
}

/** 提交审批 */
function handleSubmit(row?: any) {
  const woId = resolveTargetWoId(row);
  if (!woId) return;
  proxy.$modal
    .confirm('确认提交审批工单编号为"' + woId + '"的工单？')
    .then(() => {
      return submitWorkFlow(woId);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('提交审批成功');
    })
    .catch(() => {});
}

/** 查看流程 */
function handleViewFlow(row?: any) {
  const woId = resolveTargetWoId(row);
  if (!woId) return;
  getWorkProcId(woId)
    .then((res: any) => {
      if (res.code === 200 && res.data) {
        router.push('/workflow/process/detail/' + res.data);
      } else {
        proxy.$modal.msgWarning('暂无流程信息');
      }
    })
    .catch(() => {
      proxy.$modal.msgWarning('获取流程信息失败');
    });
}

/** 工单下达 */
function handleRelease(row?: any) {
  const woId = resolveTargetWoId(row);
  if (!woId) return;
  proxy.$modal
    .confirm('确认下达工单编号为"' + woId + '"的工单？')
    .then(() => {
      releaseWork(woId).then(() => {
        proxy.$modal.msgSuccess('下达成功');
        getList();
      });
    })
    .catch(() => {});
}

/** 工单开始 */
function handleStart(row?: any) {
  const woId = resolveTargetWoId(row);
  if (!woId) return;
  proxy.$modal
    .confirm('确认开始工单编号为"' + woId + '"的生产？')
    .then(() => {
      startWork(woId).then(() => {
        proxy.$modal.msgSuccess('开始生产成功');
        getList();
      });
    })
    .catch(() => {});
}

/** 工单完成 */
function handleComplete(row?: any) {
  const woId = resolveTargetWoId(row);
  if (!woId) return;
  proxy.$modal
    .confirm('确认完成工单编号为"' + woId + '"的生产？')
    .then(() => {
      completeWork(woId).then(() => {
        proxy.$modal.msgSuccess('完成成功');
        getList();
      });
    })
    .catch(() => {});
}

/** 工单关闭 */
function handleClose(row?: any) {
  const woId = resolveTargetWoId(row);
  if (!woId) return;
  proxy.$modal
    .confirm('确认关闭工单编号为"' + woId + '"？')
    .then(() => {
      closeWork(woId).then(() => {
        proxy.$modal.msgSuccess('关闭成功');
        getList();
      });
    })
    .catch(() => {});
}

onActivated(() => {
  getList();
});
</script>
