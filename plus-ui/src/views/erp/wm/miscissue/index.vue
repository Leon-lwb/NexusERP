<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="出库单编号" prop="issueCode">
            <el-input v-model="queryParams.issueCode" placeholder="请输入出库单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in wm_misc_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['wm:miscissue:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['wm:miscissue:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['wm:miscissue:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['wm:miscissue:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:miscissue:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="issueList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="出库单编号" align="center" prop="issueCode" min-width="140" />
      <el-table-column label="出库类型" align="center" prop="issueType" min-width="90">
        <template #default="scope">
          <dict-tag :options="wm_misc_issue_type" :value="scope.row.issueType" />
        </template>
      </el-table-column>
      <el-table-column label="仓库" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="出库日期" align="center" prop="issueDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.issueDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" min-width="90">
        <template #default="scope">
          <dict-tag :options="wm_misc_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:miscissue:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:miscissue:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="MiscIssue">
import { listMiscIssue, getMiscIssue, addMiscIssue, updateMiscIssue, delMiscIssue } from '@/api/erp/wm/miscissue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { useRouter } from 'vue-router';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_misc_status, wm_misc_issue_type } = toRefs<any>(proxy.useDict('wm_misc_status', 'wm_misc_issue_type'));

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  issueCode: null,
  status: null
});

// 列表骨架（新增/修改/查看为路由表单页 form.vue，保留本地 router.push 跳转逻辑；
// get/add/update 仅满足 useCrud 签名传入，列表页不会调用）
const {
  loading,
  list: issueList,
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
  apis: { list: listMiscIssue, get: getMiscIssue, add: addMiscIssue, update: updateMiscIssue, del: delMiscIssue },
  idField: 'issueId',
  entityName: '出库单',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/wm/miscissue/export',
  exportFileName: 'miscissue'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作（跳转路由表单页） */
function handleAdd() {
  router.push({ path: '/erp/wm/miscissue/form', query: { action: 'add' } });
}

/** 修改按钮操作（跳转路由表单页） */
function handleUpdate(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selected = issueList.value.find((item: any) => item.issueId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({ path: '/erp/wm/miscissue/form', query: { id: row.issueId, action: 'edit' } });
}

/** 查看按钮操作（跳转路由表单页） */
function handleView(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selected = issueList.value.find((item: any) => item.issueId == ids.value[0]);
    if (!selected) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selected;
  }
  router.push({ path: '/erp/wm/miscissue/form', query: { id: row.issueId, action: 'view' } });
}

onActivated(() => {
  getList();
});
</script>
