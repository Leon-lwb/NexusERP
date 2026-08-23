<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="priceListRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="价格表名称" prop="listName">
              <el-input v-model="form.listName" placeholder="请输入价格表名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="价格表类型" prop="listType">
              <el-select v-model="form.listType" placeholder="请选择价格表类型">
                <el-option v-for="dict in so_price_list_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户名称" prop="clientName">
              <el-input v-model="form.clientName" placeholder="请输入客户名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="生效日期" prop="effectiveDateFrom">
              <el-date-picker
                clearable
                v-model="form.effectiveDateFrom"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择生效日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="失效日期" prop="effectiveDateTo">
              <el-date-picker
                clearable
                v-model="form.effectiveDateTo"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择失效日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态">
                <el-option v-for="dict in so_price_list_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="币别" prop="currency">
              <el-input v-model="form.currency" placeholder="请输入币别" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="center">价格表明细</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="lineList" :row-class-name="rowLineIndex" @selection-change="handleLineSelectionChange">
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="物料编码" prop="materialCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请输入物料编码" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="请输入物料名称" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="规格型号" prop="materialSpec" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.materialSpec" placeholder="请输入规格型号" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialSpec }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="120">
            <template #default="scope">
              <el-input-number v-model="scope.row.unitPrice" :precision="2" :min="0" placeholder="单价" v-if="!isViewMode" style="width: 100%" />
              <span v-else>{{ scope.row.unitPrice }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最小数量" prop="minQty" width="120">
            <template #default="scope">
              <el-input-number v-model="scope.row.minQty" :min="0" placeholder="最小数量" v-if="!isViewMode" style="width: 100%" />
              <span v-else>{{ scope.row.minQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最大数量" prop="maxQty" width="120">
            <template #default="scope">
              <el-input-number v-model="scope.row.maxQty" :min="0" placeholder="最大数量" v-if="!isViewMode" style="width: 100%" />
              <span v-else>{{ scope.row.maxQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="折扣率(%)" prop="discountRate" width="120">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.discountRate"
                :precision="2"
                :min="0"
                :max="100"
                placeholder="折扣率"
                v-if="!isViewMode"
                style="width: 100%"
              />
              <span v-else>{{ scope.row.discountRate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="80" v-if="!isViewMode">
            <template #default="scope">
              <el-button link type="danger" icon="Delete" @click="handleDeleteSingleLine(scope.$index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="PriceListForm">
import { getPriceList, addPriceList, updatePriceList } from '@/api/erp/so/price-list';
import { useRoute, useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as any;
const { so_price_list_type, so_price_list_status } = toRefs<any>(proxy.useDict('so_price_list_type', 'so_price_list_status'));
const route = useRoute();
const router = useRouter();

const form = ref<any>({});
const isViewMode = ref(false);
const title = ref('');
const lineList = ref<any[]>([]);
const checkedLines = ref<any[]>([]);

const rules = {
  listName: [{ required: true, message: '价格表名称不能为空', trigger: 'blur' }],
  listType: [{ required: true, message: '请选择价格表类型', trigger: 'change' }]
};

onMounted(() => {
  const id = route.query.id;
  const action = route.query.action as string;
  isViewMode.value = action === 'view';
  if (action === 'add') {
    title.value = '新增价格表';
    reset();
  } else if (action === 'edit') {
    title.value = '修改价格表';
    if (id) getInfo(id);
  } else if (action === 'view') {
    title.value = '查看价格表';
    if (id) getInfo(id);
  }
});

function getInfo(id: any) {
  getPriceList(id).then((response: any) => {
    form.value = response.data;
    lineList.value = response.data.soPriceListLineList || [];
  });
}

function reset() {
  form.value = {
    priceListId: null,
    listName: null,
    listType: null,
    clientId: null,
    clientName: null,
    effectiveDateFrom: null,
    effectiveDateTo: null,
    status: 'DRAFT',
    currency: 'CNY',
    remark: null
  };
  lineList.value = [];
  proxy.resetForm('priceListRef');
}

function submitForm() {
  proxy.$refs['priceListRef'].validate((valid: boolean) => {
    if (valid) {
      form.value.soPriceListLineList = lineList.value;
      if (form.value.priceListId != null) {
        updatePriceList(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          router.push('/erp/so/price-list');
        });
      } else {
        addPriceList(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          router.push('/erp/so/price-list');
        });
      }
    }
  });
}

function cancel() {
  proxy.$tab.closeOpenPage({ path: '/erp/so/price-list' });
}

function rowLineIndex({ row, rowIndex }: any): string {
  row.index = rowIndex + 1;
  return '';
}

function handleAddLine() {
  lineList.value.push({
    materialId: null,
    materialCode: '',
    materialName: '',
    materialSpec: '',
    unitPrice: null,
    minQty: null,
    maxQty: null,
    discountRate: null,
    effectiveDateFrom: null,
    effectiveDateTo: null
  });
}

function handleDeleteLine() {
  if (checkedLines.value.length == 0) {
    proxy.$modal.msgError('请先选择要删除的行');
    return;
  }
  lineList.value = lineList.value.filter((item: any) => checkedLines.value.indexOf(item.index) == -1);
}

function handleDeleteSingleLine(index: number) {
  lineList.value.splice(index, 1);
}

function handleLineSelectionChange(selection: any[]) {
  checkedLines.value = selection.map((item: any) => item.index);
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.mb8 {
  margin-bottom: 8px;
}
:deep(.el-divider__text) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}
:deep(.el-form-item) {
  margin-bottom: 18px;
}
:deep(.el-form-item__label) {
  font-weight: 500;
}
</style>
