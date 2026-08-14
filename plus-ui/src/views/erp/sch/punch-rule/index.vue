<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="queryParams.ruleName" placeholder="请输入规则名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="班次名称" prop="shiftName">
            <el-input v-model="queryParams.shiftName" placeholder="请输入班次名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否启用" prop="isEnabled">
            <el-select v-model="queryParams.isEnabled" placeholder="请选择是否启用" clearable style="width: 100%">
              <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sch:punchRule:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['sch:punchRule:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['sch:punchRule:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['sch:punchRule:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['sch:punchRule:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="规则名称" align="center" prop="ruleName" min-width="120" show-overflow-tooltip />
      <el-table-column label="适用班次" align="center" min-width="150">
        <template #default="scope">
          <span>{{ scope.row.shiftName || '所有班次' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="上班时间" align="center" prop="workStartTime">
        <template #default="scope"
          ><span>{{ scope.row.workStartTime }}</span></template
        >
      </el-table-column>
      <el-table-column label="下班时间" align="center" prop="workEndTime">
        <template #default="scope"
          ><span>{{ scope.row.workEndTime }}</span></template
        >
      </el-table-column>
      <el-table-column label="允许打卡时间" align="center" min-width="200">
        <template #default="scope">
          <div>
            <div>上班: {{ scope.row.allowClockInStart }} - {{ scope.row.allowClockInEnd }}</div>
            <div>下班: {{ scope.row.allowClockOutStart }} - {{ scope.row.allowClockOutEnd }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="打卡地点" align="center" prop="locationAddress" min-width="120" show-overflow-tooltip />
      <el-table-column label="是否允许外勤" align="center" prop="allowOutdoorPunch">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.allowOutdoorPunch" />
        </template>
      </el-table-column>
      <el-table-column label="是否启用" align="center" prop="isEnabled">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isEnabled" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sch:punchRule:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sch:punchRule:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
      <el-form ref="punchRuleRef" :model="form" :rules="rules" label-width="120px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
            </el-form-item>
            <el-form-item label="适用班次ID" prop="shiftId">
              <el-input v-model="form.shiftId" placeholder="请输入适用班次ID" />
            </el-form-item>
            <el-form-item label="班次名称" prop="shiftName">
              <el-input v-model="form.shiftName" placeholder="请输入班次名称" />
            </el-form-item>
            <el-form-item label="上班时间" prop="workStartTime">
              <el-time-picker v-model="form.workStartTime" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="请选择上班时间" clearable />
            </el-form-item>
            <el-form-item label="下班时间" prop="workEndTime">
              <el-time-picker v-model="form.workEndTime" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="请选择下班时间" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="允许上班打卡开始" prop="allowClockInStart">
              <el-time-picker v-model="form.allowClockInStart" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="请选择开始时间" clearable />
            </el-form-item>
            <el-form-item label="允许上班打卡结束" prop="allowClockInEnd">
              <el-time-picker v-model="form.allowClockInEnd" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="请选择结束时间" clearable />
            </el-form-item>
            <el-form-item label="允许下班打卡开始" prop="allowClockOutStart">
              <el-time-picker v-model="form.allowClockOutStart" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="请选择开始时间" clearable />
            </el-form-item>
            <el-form-item label="允许下班打卡结束" prop="allowClockOutEnd">
              <el-time-picker v-model="form.allowClockOutEnd" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="请选择结束时间" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider>打卡设置</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="打卡地点描述" prop="locationAddress">
              <el-input v-model="form.locationAddress" placeholder="请输入打卡地点描述" />
            </el-form-item>
            <el-form-item label="打卡半径" prop="locationRadius">
              <el-input v-model="form.locationRadius" placeholder="请输入打卡半径(米)" type="number" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="打卡中心点纬度" prop="locationLat">
              <el-input v-model="form.locationLat" placeholder="请输入纬度" />
            </el-form-item>
            <el-form-item label="打卡中心点经度" prop="locationLng">
              <el-input v-model="form.locationLng" placeholder="请输入经度" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider>考勤设置</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否允许外勤打卡" prop="allowOutdoorPunch">
              <el-radio-group v-model="form.allowOutdoorPunch">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="外勤打卡是否要求拍照" prop="outdoorRequirePhoto">
              <el-radio-group v-model="form.outdoorRequirePhoto">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="是否允许补卡" prop="allowSupplement">
              <el-radio-group v-model="form.allowSupplement">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="补卡申请截止天数" prop="supplementDeadline">
              <el-input v-model="form.supplementDeadline" placeholder="请输入截止天数" type="number" />
            </el-form-item>
            <el-form-item label="迟到阈值(分钟)" prop="lateThreshold">
              <el-input v-model="form.lateThreshold" placeholder="请输入迟到阈值" type="number" />
            </el-form-item>
            <el-form-item label="早退阈值(分钟)" prop="earlyLeaveThreshold">
              <el-input v-model="form.earlyLeaveThreshold" placeholder="请输入早退阈值" type="number" />
            </el-form-item>
            <el-form-item label="旷工阈值(分钟)" prop="absentThreshold">
              <el-input v-model="form.absentThreshold" placeholder="请输入旷工阈值" type="number" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="是否启用" prop="isEnabled">
          <el-radio-group v-model="form.isEnabled">
            <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注信息" :rows="3" />
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

<script setup lang="ts" name="PunchRule">
import { listPunchRule, getPunchRule, delPunchRule, addPunchRule, updatePunchRule } from '@/api/erp/sch/punch-rule';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as any;
const { sys_true_or_false } = toRefs(proxy.useDict('sys_true_or_false'));

const punchRuleRef = ref<FormInstance>();

const queryParams = ref({
  pageNum: 1, pageSize: 10, ruleName: null, shiftName: null, isEnabled: null
});

const rules = {
  ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
  workStartTime: [{ required: true, message: '上班时间不能为空', trigger: 'blur' }],
  workEndTime: [{ required: true, message: '下班时间不能为空', trigger: 'blur' }],
  allowClockInStart: [{ required: true, message: '允许上班打卡开始时间不能为空', trigger: 'blur' }],
  allowClockInEnd: [{ required: true, message: '允许上班打卡结束时间不能为空', trigger: 'blur' }],
  allowClockOutStart: [{ required: true, message: '允许下班打卡开始时间不能为空', trigger: 'blur' }],
  allowClockOutEnd: [{ required: true, message: '允许下班打卡结束时间不能为空', trigger: 'blur' }]
};

function handleTimeFields(data: any) {
  const timeFields = ['workStartTime', 'workEndTime', 'allowClockInStart', 'allowClockInEnd', 'allowClockOutStart', 'allowClockOutEnd'];
  timeFields.forEach((field) => {
    if (data[field]) {
      if (typeof data[field] === 'string') {
        if (data[field].includes('T')) {
          data[field] = data[field].split('T')[1].substring(0, 8);
        } else if (data[field].includes('-')) {
          data[field] = data[field].split(' ')[1];
        }
      }
    }
  });
  return data;
}

const {
  loading, list, total, open, isViewMode, title, ids, single, multiple, showSearch, form,
  getList: rawGetList, handleQuery, handleDelete, handleExport, handleSelectionChange, submitForm, cancel,
  handleAdd: rawHandleAdd, handleUpdate: rawHandleUpdate, handleView: rawHandleView
} = useCrud({
  apis: { list: listPunchRule, get: getPunchRule, add: addPunchRule, update: updatePunchRule, del: delPunchRule },
  idField: 'ruleId',
  entityName: '打卡规则',
  formRef: punchRuleRef,
  queryParams,
  initForm: () => ({
    ruleId: null, ruleName: null, shiftId: null, shiftName: null,
    workStartTime: null, workEndTime: null, allowClockInStart: null, allowClockInEnd: null,
    allowClockOutStart: null, allowClockOutEnd: null,
    locationLat: null, locationLng: null, locationRadius: null, locationAddress: null,
    allowWifiSsids: null, allowBluetoothDevices: null,
    allowOutdoorPunch: null, outdoorRequirePhoto: null, allowSupplement: null,
    supplementDeadline: null, lateThreshold: null, earlyLeaveThreshold: null, absentThreshold: null,
    isEnabled: null, remark: null
  }),
  exportUrl: '/erp/sch/punchRule/export',
  exportFileName: 'punchRule',
  afterLoad: (data: any) => handleTimeFields(data)
});

function getList() {
  return rawGetList().then(() => {
    list.value = list.value.map((row: any) => handleTimeFields(row));
  });
}

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

