<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="物料需求单编号" prop="requirementNo">
            <el-input v-model="queryParams.requirementNo" placeholder="请输入物料需求单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="需求日期" prop="requirementDate">
            <el-date-picker
              clearable
              v-model="queryParams.requirementDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择需求日期"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in prd_requirement_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['prd:requirement:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['prd:requirement:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['prd:requirement:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['prd:requirement:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['prd:requirement:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" :disabled="multiple" @click="handleGenerateWorkOrders" v-hasPermi="['prd:plan:edit']"
          >生成工单</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="requirementList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="物料需求单编号" align="center" prop="requirementNo" />
      <el-table-column label="生产计划编号" align="center" prop="planNo" />
      <el-table-column label="需求日期" align="center" prop="requirementDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.requirementDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="物料种类总数" align="center" prop="totalMaterialTypes" />
      <el-table-column label="需求总量" align="center" prop="totalRequiredQuantity" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="prd_requirement_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="物料编码" align="center" prop="materialCode" />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="120" show-overflow-tooltip />
      <el-table-column label="来源类型" align="center" prop="sourceType">
        <template #default="scope">
          <dict-tag :options="prd_material_source_type" :value="scope.row.sourceType" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['prd:requirement:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['prd:requirement:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="PrdRequirement">
import { listRequirement, delRequirement } from '@/api/erp/prd/requirement';
import { generateWorkOrders } from '@/api/erp/prd/plan';
import { useRouter } from 'vue-router';

const router = useRouter();
const { proxy } = getCurrentInstance();
const { prd_requirement_status, prd_material_source_type } = toRefs<any>(proxy.useDict('prd_requirement_status', 'prd_material_source_type'));

const requirementList = ref([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    requirementNo: null,
    bomId: null,
    requirementDate: null,
    totalMaterialTypes: null,
    totalRequiredQuantity: null,
    status: null,
    materialId: null,
    materialName: null,
    materialSpec: null
  }
});

const { queryParams } = toRefs(data);

function getList() {
  loading.value = true;
  listRequirement(queryParams.value).then((response) => {
    requirementList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.requirementId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

function handleUpdate(row) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selectedRow = requirementList.value.find((item) => item.requirementId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }
  const requirementId = row.requirementId;
  router.push({
    path: '/erp/prd/requirement/form',
    query: { id: requirementId, action: 'edit' }
  });
}

function handleAdd() {
  router.push({
    path: '/erp/prd/requirement/form',
    query: { action: 'add' }
  });
}

function handleView(row) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selectedRow = requirementList.value.find((item) => item.requirementId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }
  const requirementId = row.requirementId;
  router.push({
    path: '/erp/prd/requirement/form',
    query: { id: requirementId, action: 'view' }
  });
}

function handleDelete(row) {
  const _requirementIds = row.requirementId || ids.value;
  proxy.$modal
    .confirm('是否确认删除物料清单编号为"' + _requirementIds + '"的数据项？')
    .then(function () {
      return delRequirement(_requirementIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

function handleExport() {
  proxy.download(
    'erp/prd/requirement/export',
    {
      ...queryParams.value
    },
    `requirement_${new Date().getTime()}.xlsx`
  );
}

function handleGenerateWorkOrders() {
  if (ids.value.length === 0) {
    proxy.$modal.msgWarning('请选择至少一个物料需求单');
    return;
  }
  const selected = requirementList.value.filter((item) => ids.value.includes(item.requirementId));
  const planIds = [...new Set(selected.map((item) => item.planId))];
  if (planIds.length !== 1) {
    proxy.$modal.msgWarning('请选择同一生产计划下的物料需求单');
    return;
  }
  const planId = planIds[0];
  proxy.$modal
    .confirm('确认要为选中的 ' + ids.value.length + ' 个物料需求单生成生产工单？')
    .then(function () {
      return generateWorkOrders(planId, ids.value);
    })
    .then((res) => {
      proxy.$modal.msgSuccess(res.msg || '工单生成成功');
      getList();
    })
    .catch(() => {});
}

onActivated(() => {
  getList();
});
</script>

