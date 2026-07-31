<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px" class="list-search">
      <el-form-item label="类别名称">
        <el-input v-model="queryParams.categoryName" placeholder="请输入类别名称" clearable style="width: 180px" @keyup.enter="getList" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="getList">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
      <el-form-item style="float: right">
        <el-button type="primary" icon="Plus" @click="handleAdd">新增</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="dataList" border>
      <el-table-column label="类别编码" align="center" prop="categoryCode" width="140" />
      <el-table-column label="类别名称" align="left" prop="categoryName" min-width="140" />
      <el-table-column label="折旧方法" align="center" width="130">
        <template #default="scope">{{ methodLabel(scope.row.depreciationMethod) }}</template>
      </el-table-column>
      <el-table-column label="使用年限(月)" align="center" prop="usefulLife" width="110" />
      <el-table-column label="残值率" align="center" width="90">
        <template #default="scope">{{ Number(scope.row.salvageValueRate || 0) * 100 }}%</template>
      </el-table-column>
      <el-table-column label="费用科目" align="center" prop="debitAccountName" min-width="120" />
      <el-table-column label="累计折旧科目" align="center" prop="creditAccountName" min-width="120" />
      <el-table-column label="状态" align="center" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="small">{{ scope.row.status === '0' ? '正常' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="140" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="open" :title="form.categoryId ? '修改资产类别' : '新增资产类别'" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="类别编码">
          <el-input v-model="form.categoryCode" placeholder="如 FA_CAT_MACHINE" />
        </el-form-item>
        <el-form-item label="类别名称" prop="categoryName">
          <el-input v-model="form.categoryName" placeholder="如 机器设备" />
        </el-form-item>
        <el-form-item label="折旧方法" prop="depreciationMethod">
          <el-select v-model="form.depreciationMethod" style="width: 100%">
            <el-option v-for="dict in fa_depreciation_method" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="使用年限(月)" prop="usefulLife">
          <el-input-number v-model="form.usefulLife" :min="1" :max="600" style="width: 100%" />
        </el-form-item>
        <el-form-item label="净残值率(%)">
          <el-input-number v-model="salvageRate" :min="0" :max="100" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="费用科目" prop="debitAccountId">
          <el-select v-model="form.debitAccountId" placeholder="留空默认管理费用" filterable clearable style="width: 100%">
            <el-option v-for="a in accountOptions" :key="a.accountId" :label="`${a.accountCode} ${a.accountName}`" :value="a.accountId" />
          </el-select>
        </el-form-item>
        <el-form-item label="累计折旧科目" prop="creditAccountId">
          <el-select v-model="form.creditAccountId" placeholder="留空默认累计折旧1602" filterable clearable style="width: 100%">
            <el-option v-for="a in accountOptions" :key="a.accountId" :label="`${a.accountCode} ${a.accountName}`" :value="a.accountId" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置损失科目" prop="disposalLossAccountId">
          <el-select v-model="form.disposalLossAccountId" placeholder="留空默认营业外支出6711" filterable clearable style="width: 100%">
            <el-option v-for="a in accountOptions" :key="a.accountId" :label="`${a.accountCode} ${a.accountName}`" :value="a.accountId" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置收益科目" prop="disposalGainAccountId">
          <el-select v-model="form.disposalGainAccountId" placeholder="留空默认营业外收入6301" filterable clearable style="width: 100%">
            <el-option v-for="a in accountOptions" :key="a.accountId" :label="`${a.accountCode} ${a.accountName}`" :value="a.accountId" />
          </el-select>
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

<script setup lang="ts" name="FinFaCategory">
import { addCategory, delCategory, listCategory, updateCategory, FaAssetCategoryVO } from '@/api/erp/fin/fa';
import { leafAccounts } from '@/api/erp/fin/gl/account';
import { AccountVO } from '@/api/erp/fin/gl/account/types';

const { proxy } = getCurrentInstance() as any;
const { fa_depreciation_method } = toRefs<any>(proxy.useDict('fa_depreciation_method'));

const dataList = ref<FaAssetCategoryVO[]>([]);
const accountOptions = ref<any[]>([]);
const loading = ref(false);
const total = ref(0);
const open = ref(false);
const formRef = ref();
const salvageRate = ref(5);

const queryParams = ref({ pageNum: 1, pageSize: 10, categoryName: '' });
const form = ref<Partial<FaAssetCategoryVO>>({});
const rules = {
  categoryName: [{ required: true, message: '类别名称不能为空', trigger: 'blur' }],
  depreciationMethod: [{ required: true, message: '折旧方法不能为空', trigger: 'change' }],
  usefulLife: [{ required: true, message: '使用年限不能为空', trigger: 'blur' }]
};

function methodLabel(method: string) {
  return ({ STRAIGHT_LINE: '直线法', DOUBLE_DECLINING: '双倍余额递减法', SUM_OF_YEARS: '年数总和法', MANUAL: '手工' } as any)[method] || method;
}

function getList() {
  loading.value = true;
  listCategory(queryParams.value)
    .then((response) => {
      dataList.value = response.rows;
      total.value = response.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function resetQuery() {
  queryParams.value = { pageNum: 1, pageSize: 10, categoryName: '' };
  getList();
}

function loadAccounts() {
  leafAccounts().then((response) => {
    accountOptions.value = response.data || [];
  });
}

function reset() {
  form.value = { depreciationMethod: 'STRAIGHT_LINE', usefulLife: 120, status: '0', sortOrder: 0 };
  salvageRate.value = 5;
}

function handleAdd() {
  reset();
  open.value = true;
}

function handleEdit(row: FaAssetCategoryVO) {
  form.value = { ...row };
  salvageRate.value = Number(row.salvageValueRate || 0) * 100;
  open.value = true;
}

function submitForm() {
  formRef.value.validate((valid: boolean) => {
    if (!valid) return;
    const data = { ...form.value, salvageValueRate: Number((salvageRate.value || 0) / 100) } as any;
    const action = data.categoryId ? updateCategory(data) : addCategory(data);
    action
      .then(() => {
        proxy.$modal.msgSuccess('保存成功');
        open.value = false;
        getList();
      })
      .catch(() => {});
  });
}

function handleDelete(row: FaAssetCategoryVO) {
  proxy.$modal
    .confirm(`确认删除类别【${row.categoryName}】?`)
    .then(() => delCategory([row.categoryId]))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功');
      getList();
    })
    .catch(() => {});
}

onMounted(() => {
  getList();
  loadAccounts();
});
</script>
