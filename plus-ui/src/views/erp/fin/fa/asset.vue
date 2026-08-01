<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="资产名称" prop="keyword">
            <el-input v-model="queryParams.keyword" placeholder="请输入资产名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="资产类别" prop="categoryId">
            <el-select v-model="queryParams.categoryId" placeholder="全部" clearable style="width: 100%">
              <el-option v-for="c in categoryOptions" :key="c.categoryId" :label="c.categoryName" :value="c.categoryId" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100%">
              <el-option v-for="dict in fa_asset_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['fa:asset:add']">新增卡片</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="资产编号" align="center" prop="assetNo" width="130" />
      <el-table-column label="资产名称" align="left" prop="assetName" min-width="160" show-overflow-tooltip />
      <el-table-column label="类别" align="center" prop="categoryName" width="110" />
      <el-table-column label="折旧方法" align="center" width="110">
        <template #default="scope">{{ methodLabel(scope.row.methodLabel) }}</template>
      </el-table-column>
      <el-table-column label="原值" align="right" width="120">
        <template #default="scope">{{ fmt(scope.row.originalValue) }}</template>
      </el-table-column>
      <el-table-column label="累计折旧" align="right" width="120">
        <template #default="scope">{{ fmt(scope.row.cumulativeDepreciation) }}</template>
      </el-table-column>
      <el-table-column label="净值" align="right" width="120">
        <template #default="scope">{{ fmt(scope.row.netValue) }}</template>
      </el-table-column>
      <el-table-column label="启用日期" align="center" prop="inUseDate" width="110" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.status)" size="small">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="230" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['fa:asset:edit']">修改</el-button>
          <el-button
            v-if="scope.row.status === 'CWIP'"
            link
            type="warning"
            icon="Position"
            @click="handleActivate(scope.row)"
            v-hasPermi="['fa:asset:activate']"
            >转固</el-button
          >
          <el-button
            v-if="scope.row.status !== 'DISPOSED'"
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['fa:asset:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="open" :title="title" width="620px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="资产名称" prop="assetName">
          <el-input v-model="form.assetName" placeholder="请输入资产名称" />
        </el-form-item>
        <el-form-item label="资产类别" prop="categoryId">
          <el-select v-model="form.categoryId" style="width: 100%" @change="onCategoryChange">
            <el-option
              v-for="c in categoryOptions"
              :key="c.categoryId"
              :label="`${c.categoryName}(${methodLabel(c.depreciationMethod)})`"
              :value="c.categoryId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="原值" prop="originalValue">
          <el-input-number v-model="form.originalValue" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="净残值">
          <el-input-number v-model="form.salvageValue" :min="0" :precision="2" style="width: 100%" />
          <span class="el-form-item__content-hint">留空=原值×类别残值率</span>
        </el-form-item>
        <el-form-item label="使用年限(月)" prop="usefulLife">
          <el-input-number v-model="form.usefulLife" :min="1" :max="600" style="width: 100%" />
        </el-form-item>
        <el-form-item label="折旧方法">
          <el-select v-model="form.depreciationMethod" clearable placeholder="留空随类别" style="width: 100%">
            <el-option v-for="dict in fa_depreciation_method" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="购置日期" prop="acquisitionDate">
          <el-date-picker v-model="form.acquisitionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用日期">
          <el-date-picker v-model="form.inUseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          <span class="el-form-item__content-hint">留空=购置日</span>
        </el-form-item>
        <el-form-item label="存放地点">
          <el-input v-model="form.location" placeholder="如 一号车间" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="IN_USE">使用中</el-radio>
            <el-radio value="CWIP">在建</el-radio>
          </el-radio-group>
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

<script setup lang="ts" name="FinFaAsset">
import { activateAsset, addAsset, delAsset, listAsset, updateAsset, FaAssetVO } from '@/api/erp/fin/fa';
import { listAllCategory } from '@/api/erp/fin/fa';
import { FaAssetCategoryVO } from '@/api/erp/fin/fa';

const { proxy } = getCurrentInstance() as any;
const { fa_asset_status, fa_depreciation_method } = toRefs<any>(proxy.useDict('fa_asset_status', 'fa_depreciation_method'));

const dataList = ref<FaAssetVO[]>([]);
const categoryOptions = ref<FaAssetCategoryVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const open = ref(false);
const title = ref('');
const formRef = ref();

const queryParams = ref({ pageNum: 1, pageSize: 10, keyword: '', categoryId: undefined, status: undefined });
const form = ref<Partial<FaAssetVO>>({});
const rules = {
  assetName: [{ required: true, message: '资产名称不能为空', trigger: 'blur' }],
  categoryId: [{ required: true, message: '资产类别不能为空', trigger: 'change' }],
  originalValue: [{ required: true, message: '原值不能为空', trigger: 'blur' }],
  usefulLife: [{ required: true, message: '使用年限不能为空', trigger: 'blur' }],
  acquisitionDate: [{ required: true, message: '购置日期不能为空', trigger: 'change' }]
};

function methodLabel(method: string) {
  return (
    ({ STRAIGHT_LINE: '直线法', DOUBLE_DECLINING: '双倍余额递减法', SUM_OF_YEARS: '年数总和法', MANUAL: '手工' } as any)[method] || method || '-'
  );
}

function statusLabel(status: string) {
  return ({ IN_USE: '使用中', CWIP: '在建', DISPOSED: '已处置' } as any)[status] || status;
}

function statusType(status: string) {
  return ({ IN_USE: 'success', CWIP: 'warning', DISPOSED: 'info' } as any)[status] || 'info';
}

function fmt(v: number | undefined | null) {
  return Number(v || 0).toFixed(2);
}

function getList() {
  loading.value = true;
  listAsset(queryParams.value)
    .then((response) => {
      dataList.value = response.rows;
      total.value = response.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function loadCategories() {
  listAllCategory().then((response) => {
    categoryOptions.value = response.data || [];
  });
}

/** 类别变化时带出年限/残值 */
function onCategoryChange(categoryId: number) {
  const c = categoryOptions.value.find((x) => x.categoryId === categoryId);
  if (!c) return;
  if (!form.value.assetId && !form.value.usefulLife) {
    form.value.usefulLife = c.usefulLife;
  }
  if (!form.value.salvageValue && form.value.originalValue) {
    form.value.salvageValue = Number((Number(form.value.originalValue) * Number(c.salvageValueRate || 0)).toFixed(2));
  }
}

function reset() {
  form.value = {
    status: 'IN_USE',
    acquisitionDate: new Date().toISOString().slice(0, 10),
    inUseDate: new Date().toISOString().slice(0, 10)
  } as any;
}

function handleAdd() {
  reset();
  open.value = true;
  title.value = '新增资产卡片';
}

function handleEdit(row: FaAssetVO) {
  form.value = { ...row };
  open.value = true;
  title.value = '修改资产卡片';
}

function submitForm() {
  formRef.value.validate((valid: boolean) => {
    if (!valid) return;
    const action = form.value.assetId ? updateAsset(form.value) : addAsset(form.value);
    action
      .then(() => {
        proxy.$modal.msgSuccess(form.value.assetId ? '修改成功' : '新增成功');
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

function handleDelete(row: FaAssetVO) {
  proxy.$modal
    .confirm(`确认删除资产【${row.assetName}】?`)
    .then(() => delAsset([row.assetId]))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功');
      getList();
    })
    .catch(() => {});
}

function handleActivate(row: FaAssetVO) {
  proxy.$modal
    .confirm(`确认将资产【${row.assetName}】转固?`)
    .then(() => activateAsset(row.assetId))
    .then(() => {
      proxy.$modal.msgSuccess('转固成功');
      getList();
    })
    .catch(() => {});
}

onMounted(() => {
  getList();
  loadCategories();
});
</script>

<style scoped>
.el-form-item__content-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
