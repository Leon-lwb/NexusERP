<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="日期" prop="calendarDate">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="对象类型" prop="objectType">
            <el-select v-model="queryParams.objectType" placeholder="请选择对象类型" clearable style="width: 100%">
              <el-option v-for="dict in prd_capacity_object_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="对象名称" prop="objectName">
            <el-input v-model="queryParams.objectName" placeholder="请输入对象名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in prd_capacity_calendar_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-tabs v-model="activeView" class="mt10">
      <el-tab-pane label="列表视图" name="list">
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['prd:capacityCalendar:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['prd:capacityCalendar:edit']"
              >修改</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['prd:capacityCalendar:remove']"
              >删除</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <el-button type="info" plain icon="Calendar" @click="handleGenerate" v-hasPermi="['prd:capacityCalendar:add']">生成</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['prd:capacityCalendar:export']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
        </el-row>

        <el-table v-loading="loading" :data="calendarList" @selection-change="handleSelectionChange" show-overflow-tooltip>
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="日期" align="center" prop="calendarDate" />
          <el-table-column label="对象类型" align="center" prop="objectType">
            <template #default="scope">
              <dict-tag :options="prd_capacity_object_type" :value="scope.row.objectType" />
            </template>
          </el-table-column>
          <el-table-column label="对象编码" align="center" prop="objectCode" />
          <el-table-column label="对象名称" align="center" prop="objectName" min-width="140" show-overflow-tooltip />
          <el-table-column label="班次编码" align="center" prop="shiftCode" />
          <el-table-column label="班次名称" align="center" prop="shiftName" min-width="120" show-overflow-tooltip />
          <el-table-column label="可用工时" align="center" prop="availableHours" />
          <el-table-column label="已计划工时" align="center" prop="plannedHours" />
          <el-table-column label="剩余工时" align="center" prop="remainingHours" />
          <el-table-column label="效率(%)" align="center" prop="efficiency" />
          <el-table-column label="状态" align="center" prop="status">
            <template #default="scope">
              <dict-tag :options="prd_capacity_calendar_status" :value="scope.row.status" />
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
            <template #default="scope">
              <el-tooltip content="修改" placement="top">
                <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['prd:capacityCalendar:edit']" />
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['prd:capacityCalendar:remove']" />
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
      </el-tab-pane>
      <el-tab-pane label="日历视图" name="calendar">
        <el-calendar v-model="calendarDate">
          <template #date-cell="{ data }">
            <div class="calendar-cell">
              <div class="calendar-day">{{ data.day.split('-')[2] }}</div>
              <div v-for="item in getCalendarItems(data.day)" :key="item.calendarId" class="calendar-item">
                <el-tag :type="getCapacityTagType(item)" size="small" style="width: 100%; margin: 1px 0">
                  {{ item.shiftName || '' }}: {{ item.remainingHours || 0 }}h/{{ item.availableHours || 0 }}h
                </el-tag>
              </div>
            </div>
          </template>
        </el-calendar>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="calendarRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="日期" prop="calendarDate">
              <el-date-picker v-model="form.calendarDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="对象类型" prop="objectType">
              <el-select v-model="form.objectType" placeholder="请选择对象类型" style="width: 100%">
                <el-option label="车间" value="WORKSHOP" />
                <el-option label="工位" value="WORKSTATION" />
                <el-option label="产线" value="LINE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="对象ID" prop="objectId">
              <el-input v-model="form.objectId" placeholder="请输入对象ID" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="对象编码" prop="objectCode">
              <el-input v-model="form.objectCode" placeholder="请输入对象编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="对象名称" prop="objectName">
              <el-input v-model="form.objectName" placeholder="请输入对象名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班次ID" prop="shiftId">
              <el-input v-model="form.shiftId" placeholder="请输入班次ID" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="可用工时" prop="availableHours">
              <el-input-number v-model="form.availableHours" :precision="2" :min="0" placeholder="请输入可用工时" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="效率(%)" prop="efficiency">
              <el-input-number v-model="form.efficiency" :precision="2" :min="0" :max="100" placeholder="请输入效率" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="dict in prd_capacity_calendar_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="生成产能日历" v-model="generateOpen" width="640px" append-to-body>
      <el-form ref="generateRef" :model="generateForm" :rules="generateRules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="排班计划ID" prop="planId">
              <el-input v-model="generateForm.planId" placeholder="请输入排班计划ID" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="对象类型" prop="objectType">
              <el-select v-model="generateForm.objectType" placeholder="请选择对象类型" style="width: 100%">
                <el-option label="车间" value="WORKSHOP" />
                <el-option label="工位" value="WORKSTATION" />
                <el-option label="产线" value="LINE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="日期范围" prop="dateRange">
              <el-date-picker
                v-model="generateForm.dateRange"
                type="daterange"
                range-separator="-"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="对象ID" prop="objectId">
              <el-input v-model="generateForm.objectId" placeholder="请输入对象ID" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="对象编码" prop="objectCode">
              <el-input v-model="generateForm.objectCode" placeholder="请输入对象编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="对象名称" prop="objectName">
              <el-input v-model="generateForm.objectName" placeholder="请输入对象名称" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitGenerate">确 定</el-button>
        <el-button @click="generateOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="CapacityCalendar" lang="ts">
import {
  listCapacityCalendar,
  getCapacityCalendar,
  addCapacityCalendar,
  updateCapacityCalendar,
  delCapacityCalendar,
  generateFromSchedule
} from '@/api/erp/prd/capacity-calendar';
import { CapacityCalendarVO, CapacityCalendarQuery, CapacityCalendarForm } from '@/api/erp/prd/capacity-calendar/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { prd_capacity_object_type, prd_capacity_calendar_status } = toRefs<any>(
  proxy?.useDict('prd_capacity_object_type', 'prd_capacity_calendar_status')
);

const calendarList = ref<CapacityCalendarVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const open = ref(false);
const generateOpen = ref(false);
const dateRange = ref<string[]>([]);

const queryRef = ref<ElFormInstance>();
const calendarRef = ref<ElFormInstance>();
const generateRef = ref<ElFormInstance>();
const queryParams = ref<CapacityCalendarQuery>({ pageNum: 1, pageSize: 10 });
const form = ref<CapacityCalendarForm>({});

const generateForm = ref<{
  planId?: number;
  dateRange?: string[];
  objectType?: string;
  objectId?: number;
  objectCode?: string;
  objectName?: string;
}>({});

const rules = {
  calendarDate: [{ required: true, message: '日期不能为空', trigger: 'change' }],
  objectType: [{ required: true, message: '对象类型不能为空', trigger: 'change' }],
  objectId: [{ required: true, message: '对象ID不能为空', trigger: 'blur' }],
  availableHours: [{ required: true, message: '可用工时不能为空', trigger: 'blur' }]
};

const generateRules = {
  planId: [{ required: true, message: '排班计划ID不能为空', trigger: 'blur' }],
  dateRange: [{ required: true, message: '日期范围不能为空', trigger: 'change' }],
  objectType: [{ required: true, message: '对象类型不能为空', trigger: 'change' }],
  objectId: [{ required: true, message: '对象ID不能为空', trigger: 'blur' }]
};

const getList = async () => {
  loading.value = true;
  const params = { ...queryParams.value };
  if (dateRange.value?.length === 2) {
    params.calendarDate = dateRange.value.join(',');
  }
  const res = await listCapacityCalendar(params);
  calendarList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
const resetQuery = () => {
  dateRange.value = [];
  queryRef.value?.resetFields();
  handleQuery();
};
const handleSelectionChange = (selection: CapacityCalendarVO[]) => {
  ids.value = selection.map((item) => item.calendarId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
};

const reset = () => {
  form.value = {};
  calendarRef.value?.resetFields();
};

const handleAdd = () => {
  reset();
  open.value = true;
  title.value = '添加产能日历';
};

const handleUpdate = async (row?: CapacityCalendarVO) => {
  reset();
  const calendarId = row?.calendarId || ids.value[0];
  const res = await getCapacityCalendar(calendarId);
  form.value = res.data;
  open.value = true;
  title.value = '修改产能日历';
};

const submitForm = () => {
  calendarRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      if (form.value.calendarId) {
        await updateCapacityCalendar(form.value);
        proxy?.$modal.msgSuccess('修改成功');
      } else {
        await addCapacityCalendar(form.value);
        proxy?.$modal.msgSuccess('新增成功');
      }
      open.value = false;
      getList();
    }
  });
};

const handleDelete = async (row?: CapacityCalendarVO) => {
  const calendarIds = row?.calendarId || ids.value;
  await proxy?.$modal.confirm('是否确认删除编号为"' + calendarIds + '"的数据项？');
  await delCapacityCalendar(calendarIds);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

const handleGenerate = () => {
  generateForm.value = {};
  generateOpen.value = true;
};

const submitGenerate = () => {
  generateRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      const { planId, dateRange: dr, objectType, objectId, objectCode, objectName } = generateForm.value;
      await generateFromSchedule(planId!, dr![0], dr![1], objectType!, objectId!, objectCode!, objectName!);
      proxy?.$modal.msgSuccess('生成成功');
      generateOpen.value = false;
      getList();
    }
  });
};

const cancel = () => {
  open.value = false;
  reset();
};

const activeView = ref('list');
const calendarDate = ref(new Date());
const calendarData = ref<any[]>([]);

const loadCalendarData = async () => {
  const year = calendarDate.value.getFullYear();
  const month = calendarDate.value.getMonth();
  const startDate = `${year}-${String(month + 1).padStart(2, '0')}-01`;
  const endDate = `${year}-${String(month + 1).padStart(2, '0')}-${new Date(year, month + 1, 0).getDate()}`;
  try {
    const res = await listCapacityCalendar({
      calendarDate: startDate,
      objectType: queryParams.value.objectType,
      objectId: queryParams.value.objectId,
      pageNum: 1,
      pageSize: 999
    });
    calendarData.value = res.rows || [];
  } catch {
    calendarData.value = [];
  }
};

const getCalendarItems = (day: string): any[] => {
  return calendarData.value.filter((item: any) => {
    if (!item.calendarDate) return false;
    return item.calendarDate.substring(0, 10) === day;
  });
};

const getCapacityTagType = (item: any): 'success' | 'primary' | 'warning' | 'danger' | 'info' => {
  if (!item.availableHours || item.availableHours === 0) return 'info';
  const ratio = (item.remainingHours || 0) / item.availableHours;
  if (ratio >= 0.8) return 'success';
  if (ratio >= 0.5) return 'primary';
  if (ratio >= 0.2) return 'warning';
  return 'danger';
};

watch(activeView, (val) => {
  if (val === 'calendar') {
    loadCalendarData();
  }
});

watch(calendarDate, () => {
  if (activeView.value === 'calendar') {
    loadCalendarData();
  }
});

const handleExport = () => {
  proxy?.download('erp/prd/capacityCalendar/export', { ...queryParams.value }, `产能日历_${new Date().getTime()}.xlsx`);
};

onMounted(() => {
  getList();
});
</script>

<style scoped lang="scss">
.calendar-cell {
  height: 100%;
  min-height: 60px;
  overflow: hidden;
}
.calendar-day {
  font-weight: bold;
  font-size: 14px;
  margin-bottom: 2px;
}
.calendar-item {
  font-size: 11px;
  line-height: 1.2;
}
</style>
