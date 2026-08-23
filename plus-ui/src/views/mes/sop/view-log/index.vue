<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="SOP ID" prop="sopId">
            <el-input v-model="queryParams.sopId" placeholder="请输入SOP ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="查阅人ID" prop="viewerId">
            <el-input v-model="queryParams.viewerId" placeholder="请输入查阅人ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工单ID" prop="woId">
            <el-input v-model="queryParams.woId" placeholder="请输入工单ID" clearable @keyup.enter="handleQuery" />
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
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:sop:viewlog:list']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="SOP名称" prop="sopName" min-width="150" />
      <el-table-column label="版本号" prop="versionNo" align="center" width="90" />
      <el-table-column label="查阅人" prop="viewerName" width="100" />
      <el-table-column label="查阅时间" prop="viewTime" width="160" />
      <el-table-column label="工单编号" prop="woNo" width="130" />
      <el-table-column label="工序" prop="processName" min-width="120" />
      <el-table-column label="电子签名" prop="signatureId" align="center" width="100">
        <template #default="scope">
          <el-tag type="success" v-if="scope.row.signatureId">已签</el-tag>
          <el-tag type="info" v-else>未签</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="IP" prop="ipAddress" width="130" />
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="SopViewLog">
import { listViewLog, delViewLog } from '@/api/mes/sop/view-log';
import type { SopViewLogVO, SopViewLogQuery } from '@/api/mes/sop/view-log';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

// SOP查阅记录为只读页面(无详情/新增/修改)，以下占位仅为满足 useCrud 的 API 签名，不会被调用
const viewLogFormRef = ref<ElFormInstance>();
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref<SopViewLogQuery>({
  pageNum: 1,
  pageSize: 10,
  sopId: undefined,
  viewerId: undefined,
  woId: undefined
});

const { loading, list, total, multiple, showSearch, getList, handleQuery, handleDelete, handleSelectionChange } = useCrud<
  SopViewLogVO,
  SopViewLogQuery
>({
  apis: { list: listViewLog, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: delViewLog },
  idField: 'logId',
  entityName: 'SOP查阅记录',
  formRef: viewLogFormRef,
  queryParams,
  initForm: () => ({})
});

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
