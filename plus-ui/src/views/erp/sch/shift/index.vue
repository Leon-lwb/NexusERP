<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="班次编码" prop="shiftCode">
            <el-input v-model="queryParams.shiftCode" placeholder="请输入班次编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="班次名称" prop="shiftName">
            <el-input v-model="queryParams.shiftName" placeholder="请输入班次名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="班次类别" prop="shiftCategory">
            <el-select v-model="queryParams.shiftCategory" placeholder="请选择班次类别" clearable style="width: 100%">
              <el-option v-for="dict in sch_shift_category" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['sch:shift:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['sch:shift:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['sch:shift:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['sch:shift:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['sch:shift:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="shiftList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="班次编码" align="center" prop="shiftCode" />
      <el-table-column label="班次名称" align="center" prop="shiftName" min-width="120" show-overflow-tooltip />
      <el-table-column label="开始时间" align="center" prop="startTime">
        <template #default="scope">
          <span>{{ formatTime(scope.row.startTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束时间" align="center" prop="endTime">
        <template #default="scope">
          <span>{{ formatTime(scope.row.endTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="工时(小时)" align="center" prop="workHours" />
      <el-table-column label="是否跨天" align="center" prop="isCrossDay">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isCrossDay" />
        </template>
      </el-table-column>
      <el-table-column label="班次类别" align="center" prop="shiftCategory">
        <template #default="scope">
          <dict-tag :options="sch_shift_category" :value="scope.row.shiftCategory" />
        </template>
      </el-table-column>
      <el-table-column label="班次颜色" align="center" prop="color">
        <template #default="scope">
          <div class="color-tag" :style="{ backgroundColor: scope.row.color || '#f0f0f0' }">
            <span :style="{ color: getContrastColor(scope.row.color) }">{{ scope.row.color || '-' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sortOrder" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sch:shift:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sch:shift:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="750px" append-to-body>
      <el-form ref="shiftRef" :model="form" :rules="rules" label-width="120px" :disabled="isViewMode">
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="班次编码" prop="shiftCode">
              <el-input v-model="form.shiftCode" placeholder="请输入班次编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班次名称" prop="shiftName">
              <el-input v-model="form.shiftName" placeholder="请输入班次名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-time-picker v-model="form.startTime" value-format="HH:mm:ss" placeholder="请选择开始时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-time-picker v-model="form.endTime" value-format="HH:mm:ss" placeholder="请选择结束时间" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="工时(小时)" prop="workHours">
              <el-input v-model="form.workHours" placeholder="请输入工时" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否跨天" prop="isCrossDay">
              <el-radio-group v-model="form.isCrossDay">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="休息开始时间" prop="restStartTime">
              <el-time-picker v-model="form.restStartTime" value-format="HH:mm:ss" placeholder="请选择休息开始时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="休息结束时间" prop="restEndTime">
              <el-time-picker v-model="form.restEndTime" value-format="HH:mm:ss" placeholder="请选择休息结束时间" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="迟到阈值(分钟)" prop="lateThreshold">
              <el-input v-model="form.lateThreshold" placeholder="请输入迟到阈值" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="早退阈值(分钟)" prop="earlyLeaveThreshold">
              <el-input v-model="form.earlyLeaveThreshold" placeholder="请输入早退阈值" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="班次类别" prop="shiftCategory">
              <el-select v-model="form.shiftCategory" placeholder="请选择班次类别" style="width: 100%">
                <el-option v-for="dict in sch_shift_category" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班次显示颜色" prop="color">
              <el-color-picker v-model="form.color" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input v-model="form.sortOrder" placeholder="请输入排序" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :rows="3" />
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

<script setup lang="ts" name="Shift">
import { listShift, getShift, delShift, addShift, updateShift } from '@/api/erp/sch/shift';

const { proxy } = getCurrentInstance();
const { sys_true_or_false, sch_shift_category } = toRefs(proxy.useDict('sys_true_or_false', 'sch_shift_category'));

const shiftList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);
const shiftRef = ref();

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    shiftCode: null,
    shiftName: null,
    shiftCategory: null
  },
  rules: {
    shiftCode: [{ required: true, message: '班次编码不能为空', trigger: 'blur' }],
    shiftName: [{ required: true, message: '班次名称不能为空', trigger: 'blur' }],
    startTime: [{ required: true, message: '开始时间不能为空', trigger: 'blur' }],
    endTime: [{ required: true, message: '结束时间不能为空', trigger: 'blur' }],
    workHours: [{ required: true, message: '工时不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

function getList() {
  loading.value = true;
  listShift(queryParams.value)
    .then((response) => {
      shiftList.value = response.rows;
      total.value = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
}

function cancel() {
  open.value = false;
  reset();
}

function reset() {
  form.value = {
    shiftId: null,
    shiftCode: null,
    shiftName: null,
    startTime: null,
    endTime: null,
    workHours: null,
    isCrossDay: null,
    restStartTime: null,
    restEndTime: null,
    lateThreshold: null,
    earlyLeaveThreshold: null,
    shiftCategory: null,
    color: null,
    sortOrder: null,
    remark: null
  };
  proxy.resetForm('shiftRef');
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.shiftId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '添加班次';
}

function handleUpdate(row) {
  reset();
  isViewMode.value = false;
  getShift(row.shiftId || ids.value[0]).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '修改班次';
  });
}

function handleView(row) {
  reset();
  isViewMode.value = true;
  getShift(row.shiftId || ids.value[0]).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '查看班次';
  });
}

function handleDelete(row) {
  const shiftIds = row.shiftId || ids.value;
  proxy.$modal
    .confirm('是否确认删除班次编号为"' + shiftIds + '"的数据项？')
    .then(function () {
      return delShift(shiftIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

function handleExport() {
  proxy.download('/erp/sch/shift/export', { ...queryParams.value }, '班次数据.xlsx');
}

function submitForm() {
  proxy.$refs['shiftRef'].validate((valid) => {
    if (valid) {
      if (form.value.shiftId != null) {
        updateShift(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addShift(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

function formatTime(timeStr) {
  if (!timeStr) return '-';
  if (typeof timeStr === 'string' && timeStr.length >= 8) {
    return timeStr.substring(0, 8);
  }
  return timeStr;
}

function getContrastColor(color) {
  if (!color) return '#333333';
  const hex = color.replace('#', '');
  const r = parseInt(hex.substring(0, 2), 16);
  const g = parseInt(hex.substring(2, 4), 16);
  const b = parseInt(hex.substring(4, 6), 16);
  const brightness = (r * 299 + g * 587 + b * 114) / 1000;
  return brightness > 128 ? '#000000' : '#FFFFFF';
}

getList();
</script>

<style scoped>
.color-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  min-width: 60px;
  text-align: center;
  font-size: 12px;
}

.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
