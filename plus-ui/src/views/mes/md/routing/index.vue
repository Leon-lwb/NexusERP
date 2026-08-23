<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="工艺路线编码" prop="routingCode">
            <el-input v-model="queryParams.routingCode" placeholder="请输入编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工艺路线名称" prop="routingName">
            <el-input v-model="queryParams.routingName" placeholder="请输入名称" clearable @keyup.enter="handleQuery()" />
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
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip>
      <el-table-column label="工艺路线编码" prop="routingCode" />
      <el-table-column label="工艺路线名称" prop="routingName" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料编码" prop="materialCode" />
      <el-table-column label="物料名称" prop="materialName" min-width="120" show-overflow-tooltip />
      <el-table-column label="版本" prop="routingVersion" align="center" />
      <el-table-column label="是否当前版本" prop="isCurrent" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.isCurrent === 1" type="success">是</el-tag>
          <span v-else>否</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" align="center">
        <template #default="scope">
          <dict-tag :options="sys_core_status" :value="scope.row.status" />
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="MesRoutingReadonly">
import { listRouting } from '@/api/erp/bd/routing/index';
import type { RoutingVO, RoutingQuery } from '@/api/erp/bd/routing/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_core_status } = toRefs<any>(proxy.useDict('sys_core_status'));

const queryParams = ref<RoutingQuery>({
  pageNum: 1,
  pageSize: 10,
  routingCode: undefined,
  routingName: undefined
});

// MES 侧工艺路线为 ERP 基础数据的只读镜像（无新增/修改/删除/详情操作），
// get/add/update/del 占位仅为满足 useCrud 的 API 签名，不会被调用
const readonlyApi = () => Promise.resolve({ data: {} as RoutingVO });

const { loading, list, total, showSearch, getList, handleQuery } = useCrud({
  apis: { list: listRouting, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'routingId',
  entityName: '工艺路线',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({})
});

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
