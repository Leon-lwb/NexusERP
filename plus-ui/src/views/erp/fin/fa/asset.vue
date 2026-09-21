<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="资产名称" prop="keyword">
            <el-input v-model="queryParams.keyword" placeholder="请输入资产名称" clearable @keyup.enter="handleQuery()" />
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
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['fin:fa:asset:add']">新增卡片</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="资产编号" align="center" prop="assetNo" width="130" />
      <el-table-column label="资产名称" align="left" prop="assetName" min-width="160" show-overflow-tooltip />
      <el-table-column label="类别" align="center" prop="categoryName" width="110" />
      <!-- FE-ERP-08：折旧方法统一走 fa_depreciation_method 字典，不再使用本地硬编码 methodLabel 映射 -->
      <el-table-column label="折旧方法" align="center">
        <template #default="scope">
          <dict-tag :options="fa_depreciation_method" :value="scope.row.depreciationMethod" />
        </template>
      </el-table-column>
      <!-- FE-ERP-05/11：类别默认折旧方法（后端 FaAssetVo.categoryMethod），此前前端零命中 -->
      <el-table-column label="类别折旧方法" align="center" prop="categoryMethod">
        <template #default="scope">
          <dict-tag :options="fa_depreciation_method" :value="scope.row.categoryMethod" />
        </template>
      </el-table-column>
      <!-- FE-ERP-05：剩余折旧月数（后端 FaAssetVo.remainingMonths），此前前端零命中 -->
      <el-table-column label="剩余月数" align="center" prop="remainingMonths" />
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
      <!-- FE-ERP-08：状态统一走 fa_asset_status 字典（原为本地 statusLabel/statusType 硬编码，与搜索下拉两套来源） -->
      <el-table-column label="状态" align="center">
        <template #default="scope">
          <dict-tag :options="fa_asset_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="230" :show-overflow-tooltip="false">
        <template #default="scope">
          <!-- FE-ERP-13：已处置资产不允许再修改 -->
          <el-button
            v-if="scope.row.status !== 'DISPOSED'"
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['fin:fa:asset:edit']"
            >修改</el-button
          >
          <el-button
            v-if="scope.row.status === 'CWIP'"
            link
            type="warning"
            icon="Position"
            @click="handleActivate(scope.row)"
            v-hasPermi="['fin:fa:asset:activate']"
            >转固</el-button
          >
          <el-button
            v-if="scope.row.status !== 'DISPOSED'"
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['fin:fa:asset:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="open" :title="title" width="640px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="资产名称" prop="assetName">
          <el-input v-model="form.assetName" placeholder="请输入资产名称" />
        </el-form-item>
        <el-form-item label="资产类别" prop="categoryId">
          <el-select v-model="form.categoryId" style="width: 100%" @change="onCategoryChange">
            <el-option
              v-for="c in categoryOptions"
              :key="c.categoryId"
              :label="`${c.categoryName}(${dictLabel(fa_depreciation_method, c.depreciationMethod)})`"
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
          <!-- FE-ERP-14：状态选项改字典驱动（fa_asset_status）；「已处置」不在可选项内 ——
               处置必须走资产处置流程（后端状态机），手工改成 DISPOSED 会绕过处置单据 -->
          <el-radio-group v-model="form.status">
            <el-radio v-for="d in editableStatusOptions" :key="d.value" :value="d.value">{{ d.label }}</el-radio>
          </el-radio-group>
          <span class="el-form-item__content-hint">已处置需走资产处置流程，不在此手工选择</span>
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
import { activateAsset, addAsset, delAsset, getAsset, listAsset, updateAsset, listAllCategory, FaAssetVO, FaAssetCategoryVO } from '@/api/erp/fin/fa';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { fa_asset_status, fa_depreciation_method } = toRefs<any>(proxy.useDict('fa_asset_status', 'fa_depreciation_method'));

const formRef = ref<FormInstance>();
const categoryOptions = ref<FaAssetCategoryVO[]>([]);

/**
 * FE-ERP-14：弹窗内「状态」选项改字典驱动（fa_asset_status）。
 * 排除 DISPOSED —— 资产处置必须走处置流程（后端状态机 + 处置单据），
 * 允许手工选会让卡片绕过处置单据直接进入终态。
 */
const editableStatusOptions = computed(() => (fa_asset_status.value || []).filter((d: any) => d.value !== 'DISPOSED'));

const queryParams = ref({ pageNum: 1, pageSize: 10, keyword: '', categoryId: undefined, status: undefined });

const rules = {
  assetName: [{ required: true, message: '资产名称不能为空', trigger: 'blur' }],
  categoryId: [{ required: true, message: '资产类别不能为空', trigger: 'change' }],
  originalValue: [{ required: true, message: '原值不能为空', trigger: 'blur' }],
  usefulLife: [{ required: true, message: '使用年限不能为空', trigger: 'blur' }],
  acquisitionDate: [{ required: true, message: '购置日期不能为空', trigger: 'change' }]
};

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
  handleUpdate,
  submitForm,
  cancel,
  handleDelete
} = useCrud<FaAssetVO, any>({
  apis: { list: listAsset, get: getAsset, add: addAsset, update: updateAsset, del: delAsset },
  idField: 'assetId',
  entityName: '资产卡片',
  formRef,
  queryParams,
  initForm: () => ({
    status: 'IN_USE',
    acquisitionDate: new Date().toISOString().slice(0, 10),
    inUseDate: new Date().toISOString().slice(0, 10)
  })
});

/**
 * 从 useDict 的字典数组取标签（FE-ERP-08：删除本地 statusLabel/statusType/methodLabel 硬编码映射，
 * 页面所有状态/折旧方法展示统一由字典驱动，运维改字典即全局生效）。
 */
function dictLabel(options: any[], value?: string) {
  return (options || []).find((d: any) => d.value === value)?.label || value || '-';
}

function fmt(v: number | undefined | null) {
  return Number(v || 0).toFixed(2);
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

/** 转固:在建资产投入使用 */
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

getList();
loadCategories();
</script>

<style scoped>
.el-form-item__content-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
