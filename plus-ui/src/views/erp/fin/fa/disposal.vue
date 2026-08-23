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
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['fa:disposal:add']">处置资产</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="资产编号" align="center" prop="assetNo" width="130" />
      <el-table-column label="资产名称" align="left" prop="assetName" min-width="160" show-overflow-tooltip />
      <el-table-column label="处置类型" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.disposalType === 'SALE' ? 'primary' : 'danger'" size="small">{{
            scope.row.disposalType === 'SALE' ? '出售' : '报废'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处置日期" align="center" prop="disposalDate" width="110" />
      <el-table-column label="处置收入" align="right" width="110">
        <template #default="scope">{{ fmt(scope.row.disposalPrice) }}</template>
      </el-table-column>
      <el-table-column label="处置时净值" align="right" width="110">
        <template #default="scope">{{ fmt(scope.row.netBookValue) }}</template>
      </el-table-column>
      <el-table-column label="处置损益" align="right" width="110">
        <template #default="scope">
          <span :style="{ color: Number(scope.row.gainLoss) >= 0 ? '#67c23a' : '#f56c6c' }">{{ fmt(scope.row.gainLoss) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="凭证号" align="center" prop="postedVoucherNo" min-width="170" />
      <el-table-column label="备注" align="left" prop="remark" min-width="120" show-overflow-tooltip />
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="open" :title="title" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="资产" prop="assetId">
          <el-select v-model="form.assetId" filterable placeholder="请选择使用中的资产" style="width: 100%">
            <el-option v-for="a in assetOptions" :key="a.assetId" :label="`${a.assetNo} ${a.assetName}(净值${fmt(a.netValue)})`" :value="a.assetId" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置类型" prop="disposalType">
          <el-radio-group v-model="form.disposalType">
            <el-radio value="SALE">出售</el-radio>
            <el-radio value="SCRAP">报废</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处置日期" prop="disposalDate">
          <el-date-picker v-model="form.disposalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="处置收入" prop="disposalPrice">
          <el-input-number v-model="form.disposalPrice" :min="0" :precision="2" style="width: 100%" />
          <span class="el-form-item__content-hint">报废可填 0</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="处置原因等" />
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

<script setup lang="ts" name="FinFaDisposal">
import { addDisposal, listAsset, listDisposal, FaDisposalVO } from '@/api/erp/fin/fa';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const formRef = ref<FormInstance>();
const assetOptions = ref<any[]>([]);

// 处置为一次性登记(无修改/删除),get/update 占位仅为满足 useCrud 的 API 签名,不会被调用
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref({ pageNum: 1, pageSize: 10, keyword: '' });

const rules = {
  assetId: [{ required: true, message: '请选择资产', trigger: 'change' }],
  disposalType: [{ required: true, message: '处置类型不能为空', trigger: 'change' }],
  disposalDate: [{ required: true, message: '处置日期不能为空', trigger: 'change' }],
  disposalPrice: [{ required: true, message: '处置收入不能为空', trigger: 'blur' }]
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
  submitForm,
  cancel
} = useCrud<FaDisposalVO, any>({
  apis: { list: listDisposal, get: readonlyApi, add: addDisposal, update: readonlyApi, del: readonlyApi },
  idField: 'disposalId',
  entityName: '资产处置',
  formRef,
  queryParams,
  initForm: () => ({
    disposalType: 'SALE',
    disposalPrice: 0,
    disposalDate: new Date().toISOString().slice(0, 10)
  })
});

function fmt(v: number | undefined | null) {
  return Number(v || 0).toFixed(2);
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function loadAssets() {
  listAsset({ pageNum: 1, pageSize: 200, status: 'IN_USE' } as any).then((response) => {
    assetOptions.value = response.rows || [];
  });
}

getList();
loadAssets();
</script>

<style scoped>
.el-form-item__content-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
