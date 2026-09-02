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

      <el-form ref="returnRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-alert
          v-if="!isViewMode"
          type="info"
          show-icon
          :closable="false"
          title="退料单保存后状态为待核销，由仓管员在 PDA 扫码逐行核销实退数量并执行退料过账（报废退料不回库存仅留痕）。"
          class="mb8"
        />
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="退料类型" prop="returnType">
              <el-select v-model="form.returnType" placeholder="请选择退料类型" style="width: 100%">
                <el-option v-for="(label, value) in RETURN_TYPE_LABELS" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请选择仓库" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="Search" @click="openWarehouseSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退料日期" prop="returnDate">
              <el-date-picker
                clearable
                v-model="form.returnDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择退料日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="来源单号" prop="sourceDoc">
              <el-input v-model="form.sourceDoc" placeholder="生产工单号等(可空)" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="center">退料行信息</el-divider>
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
          <el-table-column label="物料编码" prop="materialCode" min-width="160">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请选择物料" v-if="!isViewMode" readonly>
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect(scope.$index)" />
                </template>
              </el-input>
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" min-width="140">
            <template #default="scope">
              <span>{{ scope.row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="规格型号" prop="materialSpec" min-width="110">
            <template #default="scope">
              <span>{{ scope.row.materialSpec }}</span>
            </template>
          </el-table-column>
          <el-table-column label="计划数量" prop="quantity" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.quantity" placeholder="数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.quantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已核销数量" prop="actualQty" width="100" v-if="isViewMode">
            <template #default="scope">
              <span>{{ scope.row.actualQty ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单位" prop="baseUomName" width="70">
            <template #default="scope">
              <span>{{ scope.row.baseUomName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="批次" prop="batchCode" width="120">
            <template #default="scope">
              <el-input v-model="scope.row.batchCode" placeholder="批次(可空)" v-if="!isViewMode" />
              <span v-else>{{ scope.row.batchCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="目标库位" prop="locationCode" width="130">
            <template #default="scope">
              <el-input v-model="scope.row.locationCode" placeholder="目标库位(可空)" v-if="!isViewMode" />
              <span v-else>{{ scope.row.locationCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" min-width="110">
            <template #default="scope">
              <el-input v-model="scope.row.remark" placeholder="备注(可空)" v-if="!isViewMode" />
              <span v-else>{{ scope.row.remark }}</span>
            </template>
          </el-table-column>
        </el-table>
        <el-alert
          v-if="!isViewMode"
          type="warning"
          show-icon
          :closable="false"
          title="目标库位为退料过账回库存的入库库位；不良退料建议指向不良品库区库位。"
          class="mt8"
        />
      </el-form>
    </el-card>

    <el-dialog title="选择仓库" v-model="warehouseSelectOpen" width="800px" append-to-body>
      <el-form :model="warehouseQueryParams" :inline="true">
        <el-form-item label="仓库编码">
          <el-input v-model="warehouseQueryParams.warehouseCode" placeholder="请输入仓库编码" clearable @keyup.enter="getWarehouseList" />
        </el-form-item>
        <el-form-item label="仓库名称">
          <el-input v-model="warehouseQueryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="getWarehouseList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getWarehouseList">搜索</el-button>
          <el-button icon="Refresh" @click="resetWarehouseQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="warehouseLoading" :data="warehouseList" highlight-current-row @current-change="handleWarehouseSelect">
        <el-table-column label="仓库编码" align="center" prop="warehouseCode" />
        <el-table-column label="仓库名称" align="center" prop="warehouseName" />
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="handleWarehouseSelect(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="warehouseTotal > 0"
        :total="warehouseTotal"
        v-model:page="warehouseQueryParams.pageNum"
        v-model:limit="warehouseQueryParams.pageSize"
        @pagination="getWarehouseList"
      />
    </el-dialog>

    <MaterialSelectDialog ref="materialSelectDialogRef" title="选择物料" @select="handleMaterialSelected" />
  </div>
</template>

<script setup lang="ts" name="MaterialReturnForm">
import { getReturn, addReturn } from '@/api/erp/wm/return';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { getMeasure } from '@/api/erp/bd/measure';
import { useRoute, useRouter } from 'vue-router';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { RETURN_TYPE_LABELS } from './constants';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const router = useRouter();

const isViewMode = ref(false);
const title = ref('');
const form = ref<any>({});
const lineList = ref<any[]>([]);
const checkedLines = ref<any[]>([]);

const materialSelectDialogRef = ref<any>(null);
const currentMaterialRowIndex = ref(-1);

const warehouseSelectOpen = ref(false);
const warehouseList = ref([]);
const warehouseLoading = ref(false);
const warehouseTotal = ref(0);
const warehouseQueryParams = ref({ pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null });

const rules = {
  returnType: [{ required: true, message: '退料类型不能为空', trigger: 'change' }],
  warehouseName: [{ required: true, message: '仓库不能为空', trigger: 'change' }]
};

onMounted(() => {
  const id = route.query.id;
  const action = route.query.action as string;
  isViewMode.value = action === 'view';
  if (action === 'add') {
    title.value = '新增退料单';
    reset();
  } else if (action === 'view') {
    title.value = '查看退料单';
    if (id) getInfo(id);
  }
});

function getInfo(id: any) {
  getReturn(id).then((response: any) => {
    form.value = response.data;
    lineList.value = response.data.lines || [];
  });
}

function reset() {
  form.value = {
    returnId: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    returnType: null,
    sourceDoc: null,
    returnDate: null,
    remark: null
  };
  lineList.value = [];
}

function submitForm() {
  (proxy.$refs['returnRef'] as any).validate((valid: boolean) => {
    if (!valid) return;
    if (!lineList.value.length || lineList.value.some((l: any) => !l.materialId)) {
      proxy.$modal.msgWarning('请至少添加一行并选择物料');
      return;
    }
    if (lineList.value.some((l: any) => !l.quantity || Number(l.quantity) <= 0)) {
      proxy.$modal.msgWarning('退料行数量必须大于 0');
      return;
    }
    form.value.lines = lineList.value;
    addReturn(form.value).then(() => {
      proxy.$modal.msgSuccess('新增成功，请在 PDA 上核销执行');
      router.push('/erp/inventory/materialReturn');
    });
  });
}

function cancel() {
  proxy.$tab.closeOpenPage({ path: '/erp/inventory/materialReturn' });
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
    baseUomId: '',
    baseUomName: '',
    quantity: null,
    batchCode: '',
    locationCode: '',
    remark: ''
  });
}

function handleDeleteLine() {
  if (checkedLines.value.length == 0) {
    proxy.$modal.msgError('请先选择要删除的行');
    return;
  }
  lineList.value = lineList.value.filter((item: any) => checkedLines.value.indexOf(item.index) == -1);
}

function handleLineSelectionChange(selection: any[]) {
  checkedLines.value = selection.map((item: any) => item.index);
}

function openMaterialSelect(rowIndex: number) {
  currentMaterialRowIndex.value = rowIndex;
  materialSelectDialogRef.value?.open();
}

function handleMaterialSelected(row: any) {
  const idx = currentMaterialRowIndex.value;
  if (idx >= 0 && lineList.value[idx]) {
    lineList.value[idx].materialId = row.materialId;
    lineList.value[idx].materialCode = row.materialCode;
    lineList.value[idx].materialName = row.materialName;
    lineList.value[idx].materialSpec = row.materialSpec;
    if (row.baseUomId) {
      lineList.value[idx].baseUomId = row.baseUomId;
      getMeasure(row.baseUomId).then((r: any) => {
        if (idx < lineList.value.length) lineList.value[idx].baseUomName = r.data.uomName;
      });
    }
  }
  currentMaterialRowIndex.value = -1;
}

function openWarehouseSelect() {
  warehouseSelectOpen.value = true;
  getWarehouseList();
}

function getWarehouseList() {
  warehouseLoading.value = true;
  listWarehouse(warehouseQueryParams.value).then((response: any) => {
    warehouseList.value = response.rows;
    warehouseTotal.value = response.total;
    warehouseLoading.value = false;
  });
}

function resetWarehouseQuery() {
  warehouseQueryParams.value = { pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null };
  getWarehouseList();
}

function handleWarehouseSelect(row: any) {
  if (row) {
    form.value.warehouseId = row.warehouseId;
    form.value.warehouseCode = row.warehouseCode;
    form.value.warehouseName = row.warehouseName;
  }
  warehouseSelectOpen.value = false;
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
.mt8 {
  margin-top: 8px;
}
</style>
