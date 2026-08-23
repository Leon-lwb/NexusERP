<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="报表类型" prop="reportType">
            <el-select v-model="queryParams.reportType" placeholder="请选择报表类型" clearable style="width: 100%" @change="handleQuery">
              <el-option v-for="dict in fin_report_type" :key="dict.value" :label="dict.label" :value="dict.value" />
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

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['fin:report:item:add']">新增</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="项目编码" align="center" prop="itemCode" width="160" />
      <el-table-column label="项目名称" align="left" prop="itemName" min-width="180" show-overflow-tooltip />
      <el-table-column label="科目匹配" align="left" prop="accountCodeMatch" min-width="200" show-overflow-tooltip />
      <el-table-column label="取值符号" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.sign === '-1' ? 'warning' : 'success'" size="small">{{ scope.row.sign === '-1' ? '负(-)' : '正(+1)' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="合计行" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.isSummary === '1' ? 'primary' : 'info'" size="small">{{ scope.row.isSummary === '1' ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sortOrder" width="70" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="140" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['fin:report:item:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['fin:report:item:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="open" :title="title" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="报表类型" prop="reportType">
          <el-select v-model="form.reportType" style="width: 100%">
            <el-option v-for="dict in fin_report_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目编码" prop="itemCode">
          <el-input v-model="form.itemCode" placeholder="如 BS_MONETARY" />
        </el-form-item>
        <el-form-item label="项目名称" prop="itemName">
          <el-input v-model="form.itemName" placeholder="如 货币资金" />
        </el-form-item>
        <el-form-item label="科目匹配" prop="accountCodeMatch">
          <el-input v-model="form.accountCodeMatch" placeholder="如 1001,1002 或 2221%(前缀通配)" />
        </el-form-item>
        <el-form-item label="取值符号">
          <el-radio-group v-model="form.sign">
            <el-radio value="1">正(+1)</el-radio>
            <el-radio value="-1">负(-1)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="合计行">
          <el-switch v-model="summaryFlag" active-value="1" inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
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

<script setup lang="ts" name="FinReportItemConfig">
import { deleteItemConfig, listItemConfig, saveItemConfig, ReportItemVO } from '@/api/erp/fin/report';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { fin_report_type } = toRefs<any>(proxy.useDict('fin_report_type'));

const dataList = ref<ReportItemVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const open = ref(false);
const title = ref('');
const formRef = ref();
const summaryFlag = ref('0');

const data = reactive({
  queryParams: { reportType: 'BALANCE' }
});
const { queryParams } = toRefs(data);

const form = ref<Partial<ReportItemVO>>({});
const rules = {
  reportType: [{ required: true, message: '报表类型不能为空', trigger: 'blur' }],
  itemCode: [{ required: true, message: '项目编码不能为空', trigger: 'blur' }],
  itemName: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }]
};

function getList() {
  loading.value = true;
  listItemConfig(queryParams.value.reportType)
    .then((response) => {
      dataList.value = response.data;
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleQuery() {
  getList();
}

function resetQuery() {
  queryParams.value.reportType = 'BALANCE';
  getList();
}
function reset() {
  form.value = {
    reportType: queryParams.value.reportType,
    sign: '1',
    sortOrder: 0,
    status: '0',
    parentItemId: 0
  };
  summaryFlag.value = '0';
}

function handleAdd() {
  reset();
  open.value = true;
  title.value = '新增报表项目';
}

function handleEdit(row: ReportItemVO) {
  form.value = { ...row };
  summaryFlag.value = row.isSummary || '0';
  open.value = true;
  title.value = '修改报表项目';
}

function submitForm() {
  formRef.value.validate((valid: boolean) => {
    if (!valid) return;
    saveItemConfig({ ...form.value, isSummary: summaryFlag.value } as any)
      .then(() => {
        proxy.$modal.msgSuccess(form.value.itemId ? '修改成功' : '新增成功');
        open.value = false;
        getList();
      })
      .catch(() => {});
  });
}

function cancel() {
  open.value = false;
  reset();
}

function handleDelete(row: ReportItemVO) {
  proxy.$modal
    .confirm(`确认删除报表项目【${row.itemName}】?`)
    .then(() => deleteItemConfig([row.itemId]))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功');
      getList();
    })
    .catch(() => {});
}

onMounted(() => {
  getList();
});
</script>
