<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="流程标识" prop="processKey">
        <el-input
          v-model="queryParams.processKey"
          placeholder="请输入流程标识"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="流程名称" prop="processName">
        <el-input
          v-model="queryParams.processName"
          placeholder="请输入流程名称"
          clearable
          size="small"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="流程分类" prop="category">
        <el-select v-model="queryParams.category" clearable placeholder="请选择" size="small">
          <el-option
            v-for="item in categoryOptions"
            :key="item.categoryId"
            :label="item.categoryName"
            :value="item.code">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="small" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="small" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="small"
          v-hasPermi="['workflow:process:startExport']"
          @click="handleExport"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>
    <el-table v-loading="loading" fit :data="processList">
      <el-table-column label="序号" type="index" width="50"></el-table-column>
      <el-table-column label="流程标识" align="center" prop="processKey" :show-overflow-tooltip="true" />
      <el-table-column label="流程名称" align="center" :show-overflow-tooltip="true">
        <template #default="scope">
          <el-button type="primary" @click="handleProcessView(scope.row)">
            <span>{{ scope.row.processName }}</span>
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="流程分类" align="center" prop="categoryName" :formatter="categoryFormat" />
      <el-table-column label="流程版本" align="center">
        <template #default="scope">
          <el-tag size="default" >v{{ scope.row.version }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center">
        <template #default="scope">
          <el-tag type="success" v-if="!scope.row.suspended">激活</el-tag>
          <el-tag type="warning" v-if="scope.row.suspended">挂起</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="部署时间" align="center" prop="deploymentTime" width="180"/>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            type="primary"
            size="default"
            icon="el-icon-video-play"
            @click="handleStart(scope.row)"
            v-hasPermi="['workflow:process:start']"
          >发起</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 流程图 -->
    <el-dialog :title="processView.title" v-model="processView.open" width="70%" append-to-body>
      <process-viewer :key="`designer-${processView.index}`" :xml="processView.xmlData" :style="{height: '400px'}" />
    </el-dialog>
  </div>
</template>

<script setup name="WorkProcess" lang="ts">
import { listProcess, getBpmnXml } from "@/api/workflow/process";
import { listAllCategory } from '@/api/workflow/category'
import ProcessViewer from '@/components/ProcessViewer'
import type { FormInstance } from "element-plus";
import type { ComponentInternalInstance } from "vue";

const router = useRouter()
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const queryForm = ref<FormInstance>();
// 遮罩层
const loading = ref(true)
// 查询参数
const queryParams = ref( {
  pageNum: 1,
  pageSize: 10,
  processKey: undefined,
  processName: undefined,
  category: undefined
})
// 显示搜索条件
const showSearch = ref(true)
// 总条数
const total =  ref(0)
const filterCategoryText = ref('')
const categoryOptions = ref<any>([])
const categoryProps =  ref({
  label: 'categoryName',
  value: 'code'
})
// 流程定义表格数据
const processList =  ref([])
const processView =  ref({
  title: '',
  open: false,
  index: undefined,
  xmlData:""
})

/** 查询流程分类列表 */
const getCategoryList = () => {
    listAllCategory().then(response => categoryOptions.value = response.data)
}
/** 查询流程定义列表 */
const getList = () => {
    loading.value = true
    listProcess(queryParams.value).then(response => {
      processList.value = response.rows
      total.value = response.total
      loading.value = false
    })
}
// 搜索按钮操作
const handleQuery = () => {
    queryParams.value["pageNum"] =  1
    getList();
}
// 重置按钮操作
const resetQuery = () => {
    //resetForm("queryForm");
    queryForm.value.resetFields();
    handleQuery();
}
/** 查看流程图 */
const handleProcessView = (row) => {
    let definitionId = row.definitionId;
    processView.value["title"] = "流程图";
    processView.value["index"] = definitionId;
    // 发送请求，获取xml
    getBpmnXml(definitionId).then(res => {
      processView.value["xmlData"] = res.data;
    })
    processView.value["open"] = true;
}
const handleStart = (row) => {
    router.push({
      path: '/workflow/process/start/' + row.deploymentId,
      query: {
        definitionId: row.definitionId,
      }
    })
}
/** 导出按钮操作 */
const handleExport = () => {
    proxy?.download('workflow/process/startExport', {
      ...queryParams.value
    }, `wf_start_process_${new Date().getTime()}.xlsx`)
}
const categoryFormat = (row, column) => {
    return categoryOptions.value.find(k => k.code === row.category)?.categoryName ?? '';
}

onMounted(() => {
  getCategoryList();
  getList();
})

</script>

<style scoped>

</style>
