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

      <el-form ref="handoverRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-alert
          v-if="!isViewMode"
          type="info"
          show-icon
          :closable="false"
          title="交接单保存后状态为待签收，由产线员工在 PDA 扫码逐行核销并签收；签收过账后物料经杂项入库落入行指定的线边库位。"
          class="mb8"
        />
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="工单号" prop="woNo">
              <el-input v-model="form.woNo" placeholder="请输入工单号" />
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
          <el-col :span="8" v-if="isViewMode">
            <el-form-item label="状态" prop="status">
              <el-tag :type="HANDOVER_STATUS_TAG[form.status] ?? 'info'">
                {{ HANDOVER_STATUS_LABELS[form.status] || form.status }}
              </el-tag>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20" v-if="isViewMode && form.signName">
          <el-col :span="8">
            <el-form-item label="签收人">
              <span>{{ form.signName }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="签收时间">
              <span>{{ form.signTime || '-' }}</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="center">交接行信息</el-divider>
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
          <el-table-column label="线边库位" prop="toLocationCode" width="140">
            <template #default="scope">
              <el-input v-model="scope.row.toLocationCode" placeholder="线边库位(必填)" v-if="!isViewMode" />
              <span v-else>{{ scope.row.toLocationCode }}</span>
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
          title="线边库位必填且必须真实存在：签收过账按其归属仓库入库；仓库仅为建单信息，实际入账以行库位归属为准。"
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

<script setup lang="ts" name="HandoverForm">
import { getHandover, addHandover } from '@/api/erp/wm/handover';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { getMeasure } from '@/api/erp/bd/measure';
import { useRoute, useRouter } from 'vue-router';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { HANDOVER_STATUS_LABELS, HANDOVER_STATUS_TAG } from './constants';

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
  warehouseName: [{ required: true, message: '仓库不能为空', trigger: 'change' }]
};

onMounted(() => {
  const id = route.query.id;
  const action = route.query.action as string;
  isViewMode.value = action === 'view';
  if (action === 'add') {
    title.value = '新增上料交接单';
    reset();
  } else if (action === 'view') {
    title.value = '查看上料交接单';
    if (id) getInfo(id);
  }
});

function getInfo(id: any) {
  getHandover(id).then((response: any) => {
    form.value = response.data;
    lineList.value = response.data.lines || [];
  });
}

function reset() {
  form.value = {
    handoverId: null,
    woId: null,
    woNo: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    remark: null
  };
  lineList.value = [];
}

function submitForm() {
  (proxy.$refs['handoverRef'] as any).validate((valid: boolean) => {
    if (!valid) return;
    if (!lineList.value.length || lineList.value.some((l: any) => !l.materialId)) {
      proxy.$modal.msgWarning('请至少添加一行并选择物料');
      return;
    }
    if (lineList.value.some((l: any) => !l.quantity || Number(l.quantity) <= 0)) {
      proxy.$modal.msgWarning('交接行数量必须大于 0');
      return;
    }
    if (lineList.value.some((l: any) => !l.toLocationCode)) {
      proxy.$modal.msgWarning('交接行线边库位不能为空');
      return;
    }
    form.value.lines = lineList.value;
    addHandover(form.value).then(() => {
      proxy.$modal.msgSuccess('新增成功，请在 PDA 上核销签收');
      router.push('/erp/inventory/materialHandover');
    });
  });
}

function cancel() {
  proxy.$tab.closeOpenPage({ path: '/erp/inventory/materialHandover' });
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
    toLocationCode: '',
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
