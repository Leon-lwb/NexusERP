<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="序列号" prop="snCode">
            <el-input v-model="queryParams.snCode" placeholder="请输入序列号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in wm_sn_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="批次号" prop="batchCode">
            <el-input v-model="queryParams.batchCode" placeholder="请输入批次号" clearable @keyup.enter="handleQuery" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['wm:sn:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:sn:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="List" @click="handleBatchGenerate()" v-hasPermi="['wm:sn:add']">批量生成</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="snList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" width="60" />
      <el-table-column label="序列号" align="center" prop="snCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料编码" align="center" prop="materialCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="批次号" align="center" prop="batchCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="仓库" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="库位" align="center" prop="locationCode" min-width="100" />
      <el-table-column label="状态" align="center" prop="status" min-width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.status === 'REGISTERED'" type="info">已登记</el-tag>
          <el-tag v-else-if="scope.row.status === 'RETIRED'" type="warning">已弃用</el-tag>
          <dict-tag v-else :options="wm_sn_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="来源单号" align="center" prop="sourceDocNo" min-width="120" show-overflow-tooltip />
      <el-table-column label="生产日期" align="center" prop="productionDate" width="110" />
      <el-table-column label="有效期至" align="center" prop="expiryDate" width="110" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" @click="handleUpdate(scope.row)" v-hasPermi="['wm:sn:query']">查看</el-button>
          <el-button link type="primary" @click="aliasDialog?.open(scope.row)" v-hasPermi="['wm:sn:edit']">外部别名</el-button>
          <el-button
            v-if="scope.row.status === 'REGISTERED' && scope.row.sourceDocType === 'ERP_MANUAL' && scope.row.sourceDocId == null"
            link
            type="danger"
            @click="retireDialog?.open(scope.row)"
            v-hasPermi="['wm:sn:remove']"
            >弃用</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <SerialRegistration ref="registrationDialog" @registered="getList" />
    <SerialAliasDialog ref="aliasDialog" />
    <SerialRetireDialog ref="retireDialog" @retired="getList" />
  </div>
</template>

<script setup lang="ts" name="WmSn">
import { listSn, getSn } from '@/api/erp/wm/sn';
import SerialRetireDialog from '@/components/material-trace/SerialRetireDialog.vue';
import SerialRegistration from '@/components/material-trace/SerialRegistration.vue';
import SerialAliasDialog from '@/components/material-trace/SerialAliasDialog.vue';
import { WmSnVO, WmSnQuery } from '@/api/erp/wm/sn/types';

import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_sn_status } = toRefs<any>(proxy.useDict('wm_sn_status'));

const router = useRouter();

const queryParams = ref<WmSnQuery>({
  pageNum: 1,
  pageSize: 10,
  snCode: undefined,
  materialCode: undefined,
  materialName: undefined,
  status: undefined,
  batchCode: undefined
});

// 列表骨架（新增/修改为路由表单页，保留本地 router.push 跳转逻辑）
const {
  loading,
  list: snList,
  total,
  ids,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: { list: listSn, get: getSn },
  idField: 'snId',
  entityName: '序列号',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({}),
  exportUrl: '/erp/wm/sn/export',
  exportFileName: '序列号'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  registrationDialog.value?.open();
}

/** 修改按钮操作 */
function handleUpdate(row: WmSnVO) {
  const snId = row.snId || ids.value[0];
  router.push({ path: '/erp/wm/sn/form', query: { snId: snId as string, action: 'view' } });
}

/** 正式发号与别名弹窗不写库存状态。 */
const registrationDialog = ref<InstanceType<typeof SerialRegistration>>();
const aliasDialog = ref<InstanceType<typeof SerialAliasDialog>>();
const retireDialog = ref<InstanceType<typeof SerialRetireDialog>>();
function handleBatchGenerate() {
  registrationDialog.value?.open();
}

onActivated(() => {
  getList();
});
</script>
