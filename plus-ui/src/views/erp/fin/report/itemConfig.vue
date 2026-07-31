<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px" class="list-search">
      <el-form-item label="报表类型">
        <el-select v-model="reportType" style="width: 180px" @change="getList">
          <el-option v-for="dict in fin_report_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Plus" @click="handleAdd">新增</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="dataList" border>
      <el-table-column label="项目编码" align="center" prop="itemCode" width="160" />
      <el-table-column label="项目名称" align="left" prop="itemName" min-width="180" />
      <el-table-column label="科目匹配" align="left" prop="accountCodeMatch" min-width="200" />
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
      <el-table-column label="操作" align="center" width="140" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="open" :title="form.itemId ? '修改报表项目' : '新增报表项目'" width="560px" append-to-body>
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
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="FinReportItemConfig">
import { deleteItemConfig, listItemConfig, saveItemConfig, ReportItemVO } from '@/api/erp/fin/report';

const { proxy } = getCurrentInstance() as any;
const { fin_report_type } = toRefs<any>(proxy.useDict('fin_report_type'));

const dataList = ref<ReportItemVO[]>([]);
const loading = ref(false);
const reportType = ref('BALANCE');
const open = ref(false);
const formRef = ref();
const summaryFlag = ref('0');

const form = ref<Partial<ReportItemVO>>({});
const rules = {
  reportType: [{ required: true, message: '报表类型不能为空', trigger: 'blur' }],
  itemCode: [{ required: true, message: '项目编码不能为空', trigger: 'blur' }],
  itemName: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }]
};

function getList() {
  loading.value = true;
  listItemConfig(reportType.value)
    .then((response) => {
      dataList.value = response.data;
    })
    .finally(() => {
      loading.value = false;
    });
}

function reset() {
  form.value = {
    reportType: reportType.value,
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
}

function handleEdit(row: ReportItemVO) {
  form.value = { ...row };
  summaryFlag.value = row.isSummary || '0';
  open.value = true;
}

function submitForm() {
  formRef.value.validate((valid: boolean) => {
    if (!valid) return;
    saveItemConfig({ ...form.value, isSummary: summaryFlag.value } as any)
      .then(() => {
        proxy.$modal.msgSuccess('保存成功');
        open.value = false;
        getList();
      })
      .catch(() => {});
  });
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
