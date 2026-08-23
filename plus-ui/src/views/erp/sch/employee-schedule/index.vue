<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="人员姓名" prop="workerName">
            <el-input v-model="queryParams.workerName" placeholder="请输入人员姓名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="班组名称" prop="teamName">
            <el-input v-model="queryParams.teamName" placeholder="请输入班组名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="班次名称" prop="shiftName">
            <el-input v-model="queryParams.shiftName" placeholder="请输入班次名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="排班日期" prop="scheduledDate">
            <el-date-picker
              clearable
              v-model="queryParams.scheduledDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择排班日期"
              style="width: 100%"
            >
            </el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="排班状态" prop="scheduleStatus">
            <el-select v-model="queryParams.scheduleStatus" placeholder="请选择排班状态" clearable style="width: 100%">
              <el-option v-for="dict in sch_schedule_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sch:employeeSchedule:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['sch:employeeSchedule:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['sch:employeeSchedule:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['sch:employeeSchedule:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['sch:employeeSchedule:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5" v-if="planOptions.length > 0">
        <el-select v-model="generatePlanId" placeholder="选择排班计划(空=全部)" clearable filterable style="width: 200px">
          <el-option v-for="p in planOptions" :key="p.planId" :label="p.planCode + ' ' + p.planName" :value="p.planId" />
        </el-select>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="MagicStick" :loading="generating" @click="handleGenerate" v-hasPermi="['sch:employeeSchedule:add']"
          >生成排班</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="人员姓名" align="center" prop="workerName" min-width="120" show-overflow-tooltip />
      <el-table-column label="班组名称" align="center" prop="teamName" min-width="120" show-overflow-tooltip />
      <el-table-column label="班次名称" align="center" prop="shiftName" min-width="120" show-overflow-tooltip />
      <el-table-column label="排班日期" align="center" prop="scheduledDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.scheduledDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="排班开始时间" align="center" prop="startTime">
        <template #default="scope">
          <span>{{ parseTime(scope.row.startTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="排班结束时间" align="center" prop="endTime">
        <template #default="scope">
          <span>{{ parseTime(scope.row.endTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="排班状态" align="center" prop="scheduleStatus">
        <template #default="scope">
          <dict-tag :options="sch_schedule_status" :value="scope.row.scheduleStatus" />
        </template>
      </el-table-column>
      <el-table-column label="是否工作日" align="center" prop="isWorkingDay">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isWorkingDay" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sch:employeeSchedule:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sch:employeeSchedule:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="employeeScheduleRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="排班计划ID" prop="planId">
              <el-input v-model="form.planId" placeholder="请输入排班计划ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="人员ID" prop="workerId">
              <el-input v-model="form.workerId" placeholder="请输入人员ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="人员姓名" prop="workerName">
              <el-input v-model="form.workerName" placeholder="请输入人员姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班组ID" prop="teamId">
              <el-input v-model="form.teamId" placeholder="请输入班组ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="班组名称" prop="teamName">
              <el-input v-model="form.teamName" placeholder="请输入班组名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班次ID" prop="shiftId">
              <el-input v-model="form.shiftId" placeholder="请输入班次ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="班次名称" prop="shiftName">
              <el-input v-model="form.shiftName" placeholder="请输入班次名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排班日期" prop="scheduledDate">
              <el-date-picker
                clearable
                v-model="form.scheduledDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择排班日期"
                style="width: 100%"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="排班开始时间" prop="startTime">
              <el-date-picker
                clearable
                v-model="form.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择排班开始时间"
                style="width: 100%"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排班结束时间" prop="endTime">
              <el-date-picker
                clearable
                v-model="form.endTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择排班结束时间"
                style="width: 100%"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="实际班次ID" prop="actualShiftId">
              <el-input v-model="form.actualShiftId" placeholder="请输入实际班次ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排班状态" prop="scheduleStatus">
              <el-select v-model="form.scheduleStatus" placeholder="请选择排班状态" style="width: 100%">
                <el-option v-for="dict in sch_schedule_status" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="是否工作日" prop="isWorkingDay">
              <el-select v-model="form.isWorkingDay" placeholder="请选择是否工作日" style="width: 100%">
                <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="EmployeeSchedule">
import {
  listEmployeeSchedule,
  getEmployeeSchedule,
  delEmployeeSchedule,
  addEmployeeSchedule,
  updateEmployeeSchedule,
  generateEmployeeSchedule
} from '@/api/erp/sch/employee-schedule';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { listPlan } from '@/api/erp/sch/plan';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_true_or_false, sch_schedule_status } = toRefs(proxy.useDict('sys_true_or_false', 'sch_schedule_status'));
/** 计划下拉选项(生成排班用) */
const planOptions = ref<any[]>([]);
const generatePlanId = ref<string | number | undefined>(undefined);
const generating = ref(false);

function loadPlanOptions() {
  listPlan({ pageNum: 1, pageSize: 100 }).then((response) => {
    planOptions.value = response.rows || [];
  });
}

/** 按计划生成排班(按班组排班模板展开成员; 未选计划则处理全部已发布计划) */
function handleGenerate() {
  proxy.$modal
    .confirm(
      generatePlanId.value
        ? '确认按所选计划重新生成排班明细？该计划现有明细将被覆盖。'
        : '未选择计划将重新生成全部已发布计划的排班明细(覆盖现有), 确认继续？'
    )
    .then(() => {
      generating.value = true;
      return generateEmployeeSchedule(generatePlanId.value);
    })
    .then((res: any) => {
      proxy.$modal.msgSuccess('排班明细生成成功');
      getList();
    })
    .catch(() => {})
    .finally(() => {
      generating.value = false;
    });
}

const employeeScheduleRef = ref<FormInstance>();

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  workerName: null,
  teamName: null,
  shiftName: null,
  scheduledDate: null,
  scheduleStatus: null
});

const rules = {
  planId: [{ required: true, message: '排班计划ID不能为空', trigger: 'blur' }],
  workerId: [{ required: true, message: '人员ID不能为空', trigger: 'blur' }],
  shiftId: [{ required: true, message: '班次ID不能为空', trigger: 'blur' }],
  scheduledDate: [{ required: true, message: '排班日期不能为空', trigger: 'blur' }]
};

const {
  loading,
  list,
  total,
  open,
  isViewMode,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate,
  handleView: rawHandleView
} = useCrud({
  apis: { list: listEmployeeSchedule, get: getEmployeeSchedule, add: addEmployeeSchedule, update: updateEmployeeSchedule, del: delEmployeeSchedule },
  idField: 'scheduleId',
  entityName: '排班记录',
  formRef: employeeScheduleRef,
  queryParams,
  initForm: () => ({
    scheduleId: null,
    planId: null,
    workerId: null,
    workerName: null,
    teamId: null,
    teamName: null,
    shiftId: null,
    shiftName: null,
    scheduledDate: null,
    startTime: null,
    endTime: null,
    actualShiftId: null,
    scheduleStatus: null,
    isWorkingDay: null,
    remark: null
  }),
  exportUrl: '/erp/sch/employeeSchedule/export',
  exportFileName: 'employee-schedule'
});

onMounted(() => {
  loadPlanOptions();
});

function handleAdd() {
  rawHandleAdd();
}
function handleUpdate(row?: any) {
  return rawHandleUpdate(row);
}
function handleView(row?: any) {
  return rawHandleView(row);
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
