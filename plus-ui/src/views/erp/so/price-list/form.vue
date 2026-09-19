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

      <el-form ref="priceListRef" :model="form" :rules="rules" label-width="110px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="价格表编码" prop="listCode">
              <!-- 留空则由后端按 PRICE_LIST_CODE 规则自动生成 -->
              <el-input v-model="form.listCode" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="价格表名称" prop="listName">
              <el-input v-model="form.listName" placeholder="请输入价格表名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="价格表类型" prop="listType">
              <el-select v-model="form.listType" placeholder="请选择价格表类型" style="width: 100%">
                <el-option v-for="dict in so_price_list_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="客户" prop="clientId">
              <!-- 基价（BASE）/期间促销（PROMOTION）可不选客户，客户专属（CUSTOMER）必选 -->
              <el-input v-model="form.clientName" placeholder="不选表示不限客户" readonly>
                <template #append>
                  <el-button icon="Search" @click="clientSelectRef?.open()" />
                </template>
                <template #suffix v-if="form.clientId">
                  <el-icon class="cell-clear" title="清除" @click="clearClient"><CircleClose /></el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种" prop="currency">
              <el-select v-model="form.currency" placeholder="请选择币种" style="width: 100%">
                <el-option v-for="dict in so_currency_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级" prop="priority">
              <!-- 多张价格表同时命中时，优先级数值大的先被采用 -->
              <el-input-number v-model="form.priority" :min="0" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="生效日期" prop="validFrom">
              <el-date-picker
                clearable
                v-model="form.validFrom"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="留空表示立即生效"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="失效日期" prop="validTo">
              <el-date-picker
                clearable
                v-model="form.validTo"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="留空表示长期有效"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <!-- 只有 ACTIVE 的价格表会被取价引擎采用 -->
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="dict in so_price_list_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :rows="2" />
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
          <el-col :span="12">
            <span class="line-hint"> 折扣率为「让利比例」：0 表示按单价成交，5 表示让利 5%（实付 95%）；最大数量留空表示不设上限。 </span>
          </el-col>
        </el-row>
        <el-table :data="lineList" @selection-change="handleLineSelectionChange" border>
          <el-table-column type="selection" width="46" align="center" v-if="!isViewMode" />
          <el-table-column label="序号" type="index" width="56" align="center" />
          <el-table-column label="物料" prop="materialName" min-width="240">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="请选择物料" readonly v-if="!isViewMode">
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect(scope.row)" />
                </template>
              </el-input>
              <span v-else>{{ scope.row.materialName || '-' }}</span>
              <div class="line-sub">{{ materialSubText(scope.row) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="130">
            <template #default="scope">
              <el-input-number v-model="scope.row.unitPrice" :precision="4" :min="0" placeholder="单价" v-if="!isViewMode" style="width: 100%" />
              <span v-else>{{ scope.row.unitPrice }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最小数量" prop="minQty" width="130">
            <template #default="scope">
              <el-input-number v-model="scope.row.minQty" :precision="4" :min="0" placeholder="0" v-if="!isViewMode" style="width: 100%" />
              <span v-else>{{ scope.row.minQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最大数量" prop="maxQty" width="130">
            <template #default="scope">
              <el-input-number v-model="scope.row.maxQty" :precision="4" :min="0" placeholder="留空=不限" v-if="!isViewMode" style="width: 100%" />
              <span v-else>{{ scope.row.maxQty ?? '不限' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="折扣率(%)" prop="discountRate" width="130">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.discountRate"
                :precision="2"
                :min="0"
                :max="100"
                placeholder="0=不让利"
                v-if="!isViewMode"
                style="width: 100%"
              />
              <span v-else>{{ scope.row.discountRate ?? 0 }}</span>
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

    <!-- 客户选择对话框 -->
    <ClientSelectDialog ref="clientSelectRef" @confirm="handleClientSelect" />
    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择物料" @select="handleMaterialSelected" />
  </div>
</template>

<script setup lang="ts" name="PriceListForm">
import { getPriceList, addPriceList, updatePriceList } from '@/api/erp/so/price-list';
import { useRoute, useRouter } from 'vue-router';
import ClientSelectDialog from '../components/ClientSelectDialog.vue';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import type { ClientVO } from '@/api/erp/bd/client/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { so_price_list_type, so_price_list_status, so_currency_type } = toRefs<any>(
  proxy.useDict('so_price_list_type', 'so_price_list_status', 'so_currency_type')
);
const route = useRoute();
const router = useRouter();

const form = ref<any>({});
const isViewMode = ref(false);
const title = ref('');
const lineList = ref<any[]>([]);
const checkedLines = ref<any[]>([]);

const clientSelectRef = ref<InstanceType<typeof ClientSelectDialog> | null>(null);
const materialSelectDialogRef = ref<any>(null);
/** 当前正在选择物料的明细行（按行对象引用，避免删行后索引错位） */
const currentMaterialRow = ref<any>(null);

const rules = {
  listName: [{ required: true, message: '价格表名称不能为空', trigger: 'blur' }],
  listType: [{ required: true, message: '请选择价格表类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
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
    listCode: null,
    listName: null,
    listType: 'BASE',
    clientId: null,
    clientName: null,
    currency: 'CNY',
    priority: 0,
    validFrom: null,
    validTo: null,
    // 只有 ACTIVE 才会被取价引擎采用（原实现默认 DRAFT，导致新建价目表永远不生效）
    status: 'ACTIVE',
    remark: null
  };
  lineList.value = [];
  proxy.resetForm('priceListRef');
}

function submitForm() {
  (proxy.$refs['priceListRef'] as any).validate((valid: boolean) => {
    if (!valid) return;
    // 明细校验：每行必须选择物料，否则 materialId 为空、取价引擎按 materialId 匹配永远不命中
    for (let i = 0; i < lineList.value.length; i++) {
      if (!lineList.value[i].materialId) {
        proxy.$modal.msgError(`第 ${i + 1} 行未选择物料`);
        return;
      }
    }
    form.value.soPriceListLineList = lineList.value;
    const request = form.value.priceListId != null ? updatePriceList(form.value) : addPriceList(form.value);
    request
      .then(() => {
        proxy.$modal.msgSuccess(form.value.priceListId != null ? '修改成功' : '新增成功');
        router.push('/erp/so/price-list');
      })
      .catch(() => {
        // 失败提示由 request 拦截器统一给出
      });
  });
}

function cancel() {
  proxy.$tab.closeOpenPage({ path: '/erp/so/price-list' });
}

// ==================== 客户选择 ====================

function handleClientSelect(client: ClientVO) {
  form.value.clientId = client.clientId;
  form.value.clientName = client.clientName;
}

function clearClient() {
  form.value.clientId = null;
  form.value.clientName = null;
}

// ==================== 明细行 ====================

function materialSubText(row: any): string {
  const parts = [row.materialCode, row.materialSpec].filter((v: any) => v != null && v !== '');
  return parts.length ? parts.join(' / ') : '未选择物料';
}

function handleAddLine() {
  lineList.value.push({
    lineId: null,
    materialId: null,
    materialCode: '',
    materialName: '',
    materialSpec: '',
    unitPrice: null,
    minQty: null,
    maxQty: null,
    // 0 = 不让利（与 DDL 默认值一致）
    discountRate: 0
  });
}

function openMaterialSelect(row: any) {
  currentMaterialRow.value = row;
  materialSelectDialogRef.value?.open();
}

function handleMaterialSelected(material: any) {
  const line = currentMaterialRow.value;
  currentMaterialRow.value = null;
  if (!line) return;
  line.materialId = material.materialId;
  line.materialCode = material.materialCode;
  line.materialName = material.materialName;
  line.materialSpec = material.materialSpec;
  // 单价仅在未填写时用物料参考价预填，不覆盖手工定价
  if (!line.unitPrice) {
    line.unitPrice = material.referencePrice ?? null;
  }
}

function handleDeleteLine() {
  if (checkedLines.value.length === 0) {
    proxy.$modal.msgError('请先选择要删除的行');
    return;
  }
  lineList.value = lineList.value.filter((item: any) => !checkedLines.value.includes(item));
  checkedLines.value = [];
}

function handleDeleteSingleLine(index: number) {
  lineList.value.splice(index, 1);
}

function handleLineSelectionChange(selection: any[]) {
  checkedLines.value = selection;
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
.line-sub {
  margin-top: 2px;
  font-size: 12px;
  line-height: 16px;
  color: var(--el-text-color-secondary);
}
.line-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.cell-clear {
  cursor: pointer;
  color: var(--el-text-color-placeholder);
}
.cell-clear:hover {
  color: var(--el-color-danger);
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
