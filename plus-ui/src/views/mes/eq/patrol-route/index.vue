<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="路线名称" prop="routeName">
            <el-input v-model="queryParams.routeName" placeholder="请输入" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100%">
              <el-option v-for="d in mes_patrol_route_status" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:eq:patrol:route:add']">新增路线</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="路线名称" prop="routeName" min-width="120" />
      <el-table-column label="描述" prop="description" min-width="120" show-overflow-tooltip />
      <el-table-column label="频率" prop="frequency" align="center" width="80" />
      <el-table-column label="状态" prop="status" align="center" width="80">
        <template #default="s"><dict-tag :options="mes_patrol_route_status" :value="s.row.status" /></template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="160" :show-overflow-tooltip="false">
        <template #default="s">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(s.row)" v-hasPermi="['mes:eq:patrol:route:edit']">编辑</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(s.row)" v-hasPermi="['mes:eq:patrol:route:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改巡检路线对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="formRules">
        <el-form-item label="路线名称" prop="routeName">
          <el-input v-model="form.routeName" placeholder="路线名称" clearable />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="设备ID列表">
          <el-input v-model="form.equipmentIds" placeholder="JSON数组,如 [1,2,3]" clearable />
        </el-form-item>
        <el-form-item label="检查项">
          <el-input v-model="form.checkItems" placeholder='JSON数组,如 ["温度","油位"]' type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="频率">
          <el-select v-model="form.frequency" style="width: 100%">
            <el-option label="每日" value="DAILY" />
            <el-option label="每周" value="WEEKLY" />
            <el-option label="每月" value="MONTHLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="d in mes_patrol_route_status" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts" name="MesEqPatrolRoute">
import { listPatrolRoute, getPatrolRoute, addPatrolRoute, updatePatrolRoute, delPatrolRoute } from '@/api/mes/eq/patrol';
import type { EqPatrolRouteVO, EqPatrolRouteQuery } from '@/api/mes/eq/patrol';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_patrol_route_status } = toRefs<any>(proxy?.useDict('mes_patrol_route_status'));

const formRef = ref<FormInstance>();
const formRules = { routeName: [{ required: true, message: '请输入路线名称', trigger: 'blur' }] };

const queryParams = ref<EqPatrolRouteQuery>({ pageNum: 1, pageSize: 10, routeName: undefined, status: undefined });

const {
  loading,
  list: dataList,
  total,
  open,
  title,
  showSearch,
  form,
  getList,
  handleQuery,
  handleAdd,
  handleUpdate,
  handleDelete,
  submitForm,
  cancel
} = useCrud<EqPatrolRouteVO, EqPatrolRouteQuery>({
  apis: { list: listPatrolRoute, get: getPatrolRoute, add: addPatrolRoute, update: updatePatrolRoute, del: delPatrolRoute },
  idField: 'routeId',
  entityName: '巡检路线',
  formRef,
  queryParams,
  initForm: () => ({ routeName: '', description: '', equipmentIds: '', checkItems: '', frequency: 'DAILY', status: 'ACTIVE', remark: '' })
});

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
