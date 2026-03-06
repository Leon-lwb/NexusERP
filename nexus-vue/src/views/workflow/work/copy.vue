<template>
  <div class="app-container">
    <div class="search" v-show="showSearch">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" label-width="90px">
          <el-form-item label="流程名称" prop="processName">
            <el-input v-model="queryParams.processName" placeholder="请输入流程名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="发起人" prop="originatorName">
            <el-input v-model="queryParams.originatorName" placeholder="请输入发起人" clearable style="width: 200px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    <el-card shadow="never">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['workflow:process:copyExport']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="copyList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="抄送编号" align="center" prop="copyId" />
        <el-table-column label="标题" align="center" prop="title" :show-overflow-tooltip="true" />
        <el-table-column label="流程名称" align="center" prop="processName" :show-overflow-tooltip="true" />
        <el-table-column label="发起人" align="center" prop="originatorName" />
        <el-table-column label="状态" align="center" prop="state" />
        <el-table-column label="创建时间" align="center" prop="createTime">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="详情" placement="top">
              <el-button link type="primary" icon="View" @click="handleDetails(scope.row)" v-hasPermi="['workflow:process:query']"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup name="Copy" lang="ts">
//import { listCopyProcess, updateCcReaded  } from "@/api/workflow/work/process"
import { listCopyProcess, updateCcReaded  } from "@/api/workflow/work/process"
import {ProcessQuery, ProcessVO} from "@/api/workflow/work/types";

import type { ComponentInternalInstance } from "vue";
import type { FormInstance } from "element-plus";

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const copyList = ref<ProcessVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const queryFormRef = ref<FormInstance>();

const queryParams = ref<ProcessQuery>({
  pageNum: 1,
  pageSize: 10,
  processId: "",
  processName: "",
  categoryId: "",
  taskId: "",
  userId: "",
  originatorName: ""
});

/** 查询待办列表 */
const getList = async () => {
  loading.value = true;
  const res = await listCopyProcess(queryParams.value);
  copyList.value = res.rows;
  total.value = res.total;
  loading.value = false;
}
/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
}
/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields();
  handleQuery();
}
// 多选框选中数据
const handleSelectionChange = (selection) => {
  ids.value = selection.map(item => item.copyId)
  single.value = selection.length!==1
  multiple.value = !selection.length
}
/** 流程详情 */
const handleDetails = (row: ProcessVO) => {
console.log(row);
  updateCcReaded({ id: row.copyId }).then(res => {
    if (res.success) {
          console.log(res);
    }
  })
  router.push({
    path: '/workflow/process/detail/' + row.instanceId,
    query: {
      taskId: row.taskId,
      processed: false
    }
  })
}
/** 导出按钮操作 */
const handleExport = () => {
  proxy?.download("workflow/process/copyExport", {
    ...queryParams.value
  }, `copy_process_${new Date().getTime()}.xlsx`);
}

onMounted(() => {
  getList();
});
</script>
