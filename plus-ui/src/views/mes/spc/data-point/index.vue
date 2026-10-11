<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="是否OOC" prop="isOoc">
            <el-select v-model="queryParams.isOoc" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="dict in mes_yes_no" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
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
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip>
      <el-table-column label="样本组号" prop="sampleGroup" align="right" min-width="80" />
      <el-table-column label="值" prop="value" align="right" min-width="80" />
      <el-table-column label="是否OOC" prop="isOoc" align="center" min-width="80">
        <template #default="scope">
          <dict-tag :options="mes_yes_no" :value="scope.row.isOoc" />
        </template>
      </el-table-column>
      <el-table-column label="违规规则" prop="oocRule" min-width="120" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.oocRule || '-' }}</template>
      </el-table-column>
      <el-table-column label="采集时间" prop="collectTime" width="160">
        <template #default="scope">{{ scope.row.collectTime || '-' }}</template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="MesSpcDataPoint">
import { listDataPoint } from '@/api/mes/spc/data-point';
import type { SpcDataPointQuery } from '@/api/mes/spc/data-point';
import type { SpcDataPointVO } from '@/api/mes/spc/control-chart';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_yes_no } = toRefs<any>(proxy?.useDict('mes_yes_no'));

// 数据点为只读页面（无新增/修改/删除入口），以下占位仅为满足 useCrud 的 API 签名，不会被调用
const dataPointFormRef = ref<FormInstance>();
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref<SpcDataPointQuery>({
  pageNum: 1,
  pageSize: 10,
  isOoc: undefined
});

const { loading, list, total, showSearch, getList, handleQuery } = useCrud<SpcDataPointVO, SpcDataPointQuery>({
  apis: { list: listDataPoint, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'dataId',
  entityName: 'SPC数据点',
  formRef: dataPointFormRef,
  queryParams,
  initForm: () => ({})
});

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
