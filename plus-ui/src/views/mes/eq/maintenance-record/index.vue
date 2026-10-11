<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="保养类型" prop="maintenanceType">
            <el-select v-model="queryParams.maintenanceType" placeholder="请选择类型" clearable style="width: 100%">
              <el-option v-for="d in mes_maintenance_type" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="结果" prop="result">
            <el-select v-model="queryParams.result" placeholder="请选择结果" clearable style="width: 100%">
              <el-option v-for="d in mes_maintenance_result" :key="d.value" :label="d.label" :value="d.value" />
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
        <el-button type="success" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:eq:maintenance:complete']">手动保养</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['mes:eq:maintenance:record:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="设备名称" prop="equipmentName" min-width="120" show-overflow-tooltip />
      <el-table-column label="保养类型" prop="maintenanceType" align="center" min-width="90">
        <template #default="scope">
          <dict-tag v-if="scope.row.maintenanceType" :options="mes_maintenance_type" :value="scope.row.maintenanceType" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="执行人" prop="executorName" min-width="100">
        <template #default="scope">{{ scope.row.executorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="执行时间" prop="executeTime" width="160" />
      <el-table-column label="保养内容" prop="content" min-width="160" show-overflow-tooltip />
      <el-table-column label="结果" prop="result" align="center" min-width="80">
        <template #default="scope">
          <dict-tag :options="mes_maintenance_result" :value="scope.row.result" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="100" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:eq:maintenance:record:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 手动保养对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="设备" prop="equipmentId">
          <equipment-select v-model="form.equipmentId" />
        </el-form-item>
        <el-form-item label="保养类型" prop="maintenanceType">
          <el-select v-model="form.maintenanceType" placeholder="请选择类型" style="width: 100%">
            <el-option v-for="d in mes_maintenance_type" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="保养内容">
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="保养执行情况" />
        </el-form-item>
        <el-form-item label="保养结果">
          <el-select v-model="form.result" style="width: 100%">
            <el-option v-for="d in mes_maintenance_result" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesEqMaintenanceRecord">
import { listMaintenanceRecord, addMaintenanceRecord, delMaintenanceRecord } from '@/api/mes/eq/maintenance-record';
import type { EqMaintenanceRecordVO, EqMaintenanceRecordQuery } from '@/api/mes/eq/maintenance-record';
import EquipmentSelect from '@/components/EquipmentSelect/index.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_maintenance_type, mes_maintenance_result } = toRefs<any>(proxy?.useDict('mes_maintenance_type', 'mes_maintenance_result'));

const formRef = ref<FormInstance>();
const rules = {
  equipmentId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  maintenanceType: [{ required: true, message: '请选择保养类型', trigger: 'change' }]
};

// 只读占位 API(手动保养无详情/修改场景)
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref<EqMaintenanceRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  maintenanceType: undefined,
  result: undefined
});

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
  handleDelete,
  handleExport,
  submitForm,
  cancel
} = useCrud<EqMaintenanceRecordVO, EqMaintenanceRecordQuery>({
  apis: {
    list: listMaintenanceRecord,
    // 保养记录仅支持手动新增与删除，详情/修改占位不会被调用
    get: readonlyApi,
    add: addMaintenanceRecord,
    update: readonlyApi,
    del: (ids: any) => delMaintenanceRecord(Array.isArray(ids) ? ids : [ids])
  },
  idField: 'recordId',
  entityName: '保养记录',
  formRef,
  queryParams,
  initForm: () => ({ equipmentId: undefined as unknown as number, maintenanceType: '', content: '', result: 'NORMAL', remark: '' }),
  exportUrl: '/mes/eq/maintenance-record/export',
  exportFileName: 'maintenance_record'
});

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
