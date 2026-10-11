<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery()" />
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
      <el-table-column label="物料编码" prop="materialCode" min-width="140" />
      <el-table-column label="物料名称" prop="materialName" min-width="120" show-overflow-tooltip />
      <el-table-column label="规格型号" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料类型" prop="materialType" min-width="90" />
      <el-table-column label="计量单位" prop="unitName" min-width="80" />
      <!-- 只读镜像 ERP 物料, status 0=正常/1=停用, 复用 RuoYi 内置 sys_normal_disable -->
      <el-table-column label="状态" prop="status" align="center" min-width="80">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="MesMaterialReadonly">
import { listMaterial } from '@/api/erp/bd/material/index';
import type { MaterialVO, MaterialQuery } from '@/api/erp/bd/material/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));

const queryParams = ref<MaterialQuery>({
  pageNum: 1,
  pageSize: 10,
  materialCode: undefined,
  materialName: undefined
});

// MES 侧物料为 ERP 基础数据的只读镜像（无新增/修改/删除/详情操作），
// get/add/update/del 占位仅为满足 useCrud 的 API 签名，不会被调用
const readonlyApi = () => Promise.resolve({ data: {} as MaterialVO });

const { loading, list, total, showSearch, getList, handleQuery } = useCrud({
  apis: { list: listMaterial, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'materialId',
  entityName: '物料',
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
