<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="工序编码" prop="processCode">
            <el-input v-model="queryParams.processCode" placeholder="请输入工序编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工序名称" prop="processName">
            <el-input v-model="queryParams.processName" placeholder="请输入工序名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工序类别" prop="processCategory">
            <el-select v-model="queryParams.processCategory" placeholder="请选择工序类别" clearable style="width: 100%">
              <el-option v-for="dict in bd_process_category" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否关键工序" prop="isKeyProcess">
            <el-select v-model="queryParams.isKeyProcess" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['bd:process:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['bd:process:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['bd:process:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['bd:process:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 技术债：关键工序/委外/质检列使用硬编码 el-tag，缺对应字典，待补字典后改 dict-tag -->
    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工序编码" align="center" prop="processCode" />
      <el-table-column label="工序名称" align="center" prop="processName" min-width="120" show-overflow-tooltip />
      <el-table-column label="工序类别" align="center" prop="processCategory">
        <template #default="scope">
          <dict-tag :options="bd_process_category" :value="scope.row.processCategory" />
        </template>
      </el-table-column>
      <el-table-column label="工作中心" align="center" prop="workstationName" min-width="120" show-overflow-tooltip />
      <el-table-column label="标准工时(h/件)" align="center" prop="standardHours" />
      <el-table-column label="准备工时(h)" align="center" prop="setupHours" />
      <el-table-column label="人工数" align="center" prop="laborCount" />
      <el-table-column label="机台数" align="center" prop="machineCount" />
      <el-table-column label="关键工序" align="center" prop="isKeyProcess">
        <template #default="scope">
          <el-tag v-if="scope.row.isKeyProcess === 1" type="danger" size="small">是</el-tag>
          <el-tag v-else type="info" size="small">否</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="委外" align="center" prop="isOutsource">
        <template #default="scope">
          <el-tag v-if="scope.row.isOutsource === 1" type="warning" size="small">是</el-tag>
          <el-tag v-else type="info" size="small">否</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质检" align="center" prop="qualityRequired">
        <template #default="scope">
          <el-tag v-if="scope.row.qualityRequired === 1" type="success" size="small">是</el-tag>
          <el-tag v-else type="info" size="small">否</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_core_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['bd:process:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['bd:process:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改工序对话框 -->
    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
      <el-form ref="processRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工序编码" prop="processCode">
              <el-input v-model="form.processCode" placeholder="请输入工序编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序名称" prop="processName">
              <el-input v-model="form.processName" placeholder="请输入工序名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工序类别" prop="processCategory">
              <el-select v-model="form.processCategory" placeholder="请选择工序类别" clearable style="width: 100%">
                <el-option v-for="dict in bd_process_category" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认工作中心" prop="workstationId">
              <el-select
                v-model="form.workstationId"
                placeholder="请选择工作中心"
                clearable
                filterable
                style="width: 100%"
                @change="handleWorkstationChange"
              >
                <el-option
                  v-for="ws in workstationList"
                  :key="ws.workstationId"
                  :label="ws.workstationCode + ' - ' + ws.workstationName"
                  :value="ws.workstationId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="标准工时(h/件)" prop="standardHours">
              <el-input-number v-model="form.standardHours" :min="0" :precision="4" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="准备工时(h)" prop="setupHours">
              <el-input-number v-model="form.setupHours" :min="0" :precision="4" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="标准人工数" prop="laborCount">
              <el-input-number v-model="form.laborCount" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标准机台数" prop="machineCount">
              <el-input-number v-model="form.machineCount" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="关键工序" prop="isKeyProcess">
              <el-switch v-model="form.isKeyProcess" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="委外工序" prop="isOutsource">
              <el-switch v-model="form.isOutsource" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="需要质检" prop="qualityRequired">
              <el-switch v-model="form.qualityRequired" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_core_status" :key="dict.value" :label="parseInt(dict.value)">
                  {{ dict.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="工序描述" prop="processDesc">
              <el-input v-model="form.processDesc" type="textarea" :rows="3" placeholder="请输入工序描述" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Process">
import { listProcess, getProcess, addProcess, updateProcess, delProcess } from '@/api/erp/bd/process';
import { listWorkstation } from '@/api/erp/bd/workstation';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as any;
const { sys_core_status, sys_true_or_false, bd_process_category } = toRefs<any>(
  proxy.useDict('sys_core_status', 'sys_true_or_false', 'bd_process_category')
);

const processRef = ref<FormInstance>();
const workstationList = ref<any[]>([]);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  processCode: undefined,
  processName: undefined,
  processCategory: undefined,
  isKeyProcess: undefined,
  status: undefined
});

const rules = {
  processCode: [{ required: true, message: '工序编码不能为空', trigger: 'blur' }],
  processName: [{ required: true, message: '工序名称不能为空', trigger: 'blur' }]
};

const {
  loading, list, total, open, isViewMode, title, ids, single, multiple, showSearch, form,
  getList, handleQuery, handleDelete, handleExport, handleSelectionChange, submitForm, cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate,
  handleView: rawHandleView
} = useCrud({
  apis: { list: listProcess, get: getProcess, add: addProcess, update: updateProcess, del: delProcess },
  idField: 'processId',
  entityName: '工序',
  formRef: processRef,
  queryParams,
  initForm: () => ({
    processId: undefined,
    processCode: undefined,
    processName: undefined,
    processDesc: undefined,
    processCategory: undefined,
    workstationId: undefined,
    workstationCode: undefined,
    workstationName: undefined,
    standardHours: undefined,
    setupHours: undefined,
    laborCount: undefined,
    machineCount: undefined,
    isKeyProcess: 0,
    isOutsource: 0,
    qualityRequired: 0,
    status: 1,
    remark: undefined
  }),
  exportUrl: '/erp/bd/process/export',
  exportFileName: 'process',
  onReset: () => { getWorkstationList(); }
});

/** 工作中心列表 */
function getWorkstationList() {
  listWorkstation({ status: 1 } as any).then((response: any) => {
    workstationList.value = response.rows || [];
  });
}

/** 工作中心选择变更 */
function handleWorkstationChange(val: number) {
  if (val) {
    const ws = workstationList.value.find((item: any) => item.workstationId === val);
    if (ws) {
      form.value.workstationCode = ws.workstationCode;
      form.value.workstationName = ws.workstationName;
    }
  } else {
    form.value.workstationCode = undefined;
    form.value.workstationName = undefined;
  }
}

function handleAdd() {
  rawHandleAdd(() => { getWorkstationList(); });
}
function handleUpdate(row?: any) {
  return rawHandleUpdate(row, () => { getWorkstationList(); });
}
function handleView(row?: any) {
  return rawHandleView(row, () => { getWorkstationList(); });
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

getList();
</script>

