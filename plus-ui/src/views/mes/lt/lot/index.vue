<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="批次编码" prop="lotCode">
              <el-input v-model="queryParams.lotCode" placeholder="请输入批次编码" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="dict in mes_lot_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="QC状态" prop="qcStatus">
              <el-select v-model="queryParams.qcStatus" placeholder="请选择QC状态" clearable style="width: 100%">
                <el-option v-for="dict in mes_lot_qc_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:lt:lot:add']">新增批次</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="MagicStick" @click="handleFefo" v-hasPermi="['mes:lt:lot:fefo']">FEFO 推荐</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="!ids.length" @click="handleDelete" v-hasPermi="['mes:lt:lot:remove']"
            >删除</el-button
          >
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="批次编码" prop="lotCode" min-width="140" />
        <el-table-column label="物料" prop="materialName" min-width="140" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="供应商批次" prop="supplierLot" width="120" />
        <el-table-column label="批次数量" prop="quantity" align="right" width="100" />
        <el-table-column label="已消耗" prop="consumedQty" align="right" width="90" />
        <el-table-column label="剩余" prop="remainingQty" align="right" width="90">
          <template #default="scope">
            <span :class="scope.row.remainingQty <= 0 ? 'text-danger' : ''">{{ scope.row.remainingQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="生产日期" prop="producedDate" width="110" />
        <el-table-column label="有效期" prop="expiryDate" width="110">
          <template #default="scope">
            <span :class="isExpiringSoon(scope.row.expiryDate) ? 'text-warning' : ''">{{ scope.row.expiryDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" align="center" width="90">
          <template #default="scope">
            <dict-tag :options="mes_lot_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="QC" prop="qcStatus" align="center" width="80">
          <template #default="scope">
            <dict-tag :options="mes_lot_qc_status" :value="scope.row.qcStatus" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="280" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleTrace(scope.row)" v-hasPermi="['mes:lt:lot:trace']">追溯</el-button>
            <el-button
              link
              type="primary"
              icon="Edit"
              @click="handleEdit(scope.row)"
              v-if="canEdit(scope.row.status)"
              v-hasPermi="['mes:lt:lot:edit']"
              >编辑</el-button
            >
            <el-button
              link
              type="warning"
              icon="Lock"
              @click="handleQuarantine(scope.row)"
              v-if="canQuarantine(scope.row.status)"
              v-hasPermi="['mes:lt:lot:quarantine']"
              >隔离</el-button
            >
            <el-button
              link
              type="success"
              icon="Unlock"
              @click="handleRelease(scope.row)"
              v-if="scope.row.status === 'QUARANTINED'"
              v-hasPermi="['mes:lt:lot:release']"
              >放行</el-button
            >
            <el-button
              link
              type="danger"
              icon="Close"
              @click="handleScrap(scope.row)"
              v-if="scope.row.status === 'QUARANTINED'"
              v-hasPermi="['mes:lt:lot:scrap']"
              >报废</el-button
            >
            <el-dropdown @command="(cmd) => handleCommand(cmd, scope.row)" v-if="scope.row.qcStatus === 'PENDING'">
              <el-button link type="primary" icon="DArrowRight">QC</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="PASS">合格</el-dropdown-item>
                  <el-dropdown-item command="FAIL">不合格</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="640px" append-to-body>
      <el-form ref="lotFormRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="批次编码" prop="lotCode">
              <div style="display: flex; align-items: center; gap: 10px; width: 100%">
                <el-input v-model="form.lotCode" :disabled="autoGenFlag" placeholder="留空自动生成" style="flex: 1" />
                <el-switch v-model="autoGenFlag" active-text="生成" @change="handleAutoGenChange" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料" prop="materialId">
              <el-input v-model="form.materialCode" placeholder="请选择物料" readonly>
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect('form')" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料名称">
              <el-input v-model="form.materialName" placeholder="选择物料后自动带出" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input v-model="form.supplierName" placeholder="请选择供应商" readonly>
                <template #append>
                  <el-button icon="Search" @click="openVendorDialog" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供应商批次"><el-input v-model="form.supplierLot" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="0" :precision="4" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="生产日期">
              <el-date-picker v-model="form.producedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期">
              <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="dialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- FEFO 推荐对话框 -->
    <el-dialog title="FEFO 批次推荐" v-model="fefoDialog.visible" width="720px" append-to-body>
      <el-form :inline="true" label-width="100px">
        <el-form-item label="物料">
          <el-input v-model="fefoDialog.materialCode" placeholder="请选择物料" readonly style="width: 200px">
            <template #append>
              <el-button icon="Search" @click="openMaterialSelect('fefo')" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="需求数量">
          <el-input-number v-model="fefoDialog.requiredQty" :min="0" :precision="4" controls-position="right" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadFefo">查询推荐</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="fefoDialog.list" v-loading="fefoDialog.loading" show-overflow-tooltip>
        <el-table-column label="批次编码" prop="lotCode" min-width="140" />
        <el-table-column label="物料" prop="materialName" min-width="120" />
        <el-table-column label="剩余数量" prop="remainingQty" align="right" width="100" />
        <el-table-column label="生产日期" prop="producedDate" width="110" />
        <el-table-column label="有效期" prop="expiryDate" width="110">
          <template #default="scope">
            <el-tag :type="isExpiringSoon(scope.row.expiryDate) ? 'warning' : 'success'" size="small">
              {{ scope.row.expiryDate }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="推荐顺序" type="index" width="80" align="center" />
      </el-table>
    </el-dialog>

    <!-- 追溯对话框 -->
    <el-dialog title="批次追溯" v-model="traceDialog.visible" width="900px" append-to-body>
      <div v-if="traceDialog.data">
        <el-descriptions :column="3" border title="批次信息">
          <el-descriptions-item label="批次编码">{{ traceDialog.data.lot?.lotCode }}</el-descriptions-item>
          <el-descriptions-item label="物料">{{ traceDialog.data.lot?.materialCode }} {{ traceDialog.data.lot?.materialName }}</el-descriptions-item>
          <el-descriptions-item label="状态"><dict-tag :options="mes_lot_status" :value="traceDialog.data.lot?.status" /></el-descriptions-item>
          <el-descriptions-item label="批次数量">{{ traceDialog.data.lot?.quantity }}</el-descriptions-item>
          <el-descriptions-item label="已消耗">{{ traceDialog.data.lot?.consumedQty }}</el-descriptions-item>
          <el-descriptions-item label="剩余">{{ traceDialog.data.lot?.remainingQty }}</el-descriptions-item>
          <el-descriptions-item label="生产日期">{{ traceDialog.data.lot?.producedDate }}</el-descriptions-item>
          <el-descriptions-item label="有效期">{{ traceDialog.data.lot?.expiryDate }}</el-descriptions-item>
          <el-descriptions-item label="QC状态"
            ><dict-tag :options="mes_lot_qc_status" :value="traceDialog.data.lot?.qcStatus"
          /></el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="traceDialog.activeTab" class="mt8">
          <el-tab-pane label="工单消耗" name="consumptions">
            <el-table :data="traceDialog.data.consumptions" size="small">
              <el-table-column label="工单ID" prop="woId" width="100" />
              <el-table-column label="工单编号" prop="woNo" />
              <el-table-column label="消耗数量" prop="consumedQty" align="right" />
              <el-table-column label="消耗时间" prop="consumedTime" />
              <el-table-column label="操作员" prop="operatorName" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="SN 绑定" name="bindings">
            <el-table :data="traceDialog.data.bindings" size="small">
              <el-table-column label="SN ID" prop="snId" width="100" />
              <el-table-column label="SN编码" prop="snCode" />
              <el-table-column label="绑定时间" prop="boundTime" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />

    <!-- 供应商选择对话框 -->
    <el-dialog v-model="vendorDialog.visible" title="选择供应商" width="900px" append-to-body>
      <el-form :model="vendorDialog.query" :inline="true">
        <el-form-item label="供应商编码">
          <el-input v-model="vendorDialog.query.vendorCode" clearable @keyup.enter="queryVendors" />
        </el-form-item>
        <el-form-item label="供应商名称">
          <el-input v-model="vendorDialog.query.vendorName" clearable @keyup.enter="queryVendors" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryVendors">搜索</el-button>
          <el-button icon="Refresh" @click="resetVendorQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="vendorDialog.list" height="400" @row-dblclick="selectVendor">
        <el-table-column label="供应商编码" prop="vendorCode" width="160" />
        <el-table-column label="供应商名称" prop="vendorName" min-width="180" show-overflow-tooltip />
        <el-table-column label="联系人" prop="contactPerson" width="120" />
        <el-table-column label="电话" prop="phone" width="140" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectVendor(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="vendorDialog.total > 0"
        :total="vendorDialog.total"
        v-model:page="vendorDialog.query.pageNum"
        v-model:limit="vendorDialog.query.pageSize"
        @pagination="queryVendors"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="LtLot">
import {
  listLot,
  getLot,
  addLot,
  updateLot,
  delLot,
  quarantineLot,
  scrapLot,
  releaseLot,
  updateLotQcStatus,
  recommendFefo,
  traceLot
} from '@/api/mes/lt/lot';
import type { LtLotVO, LtLotForm, LtLotQuery, LtLotTraceResult } from '@/api/mes/lt/lot';
import { previewCode } from '@/api/system/autocode/rule';
import { listVendor } from '@/api/erp/bd/vendor';
import MaterialSelectDialog from '@/components/MaterialSelectDialog';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_lot_status, mes_lot_qc_status } = toRefs<any>(proxy?.useDict('mes_lot_status', 'mes_lot_qc_status'));
const dataList = ref<LtLotVO[]>([]);
const total = ref(0);
const loading = ref(true);
const ids = ref<number[]>([]);
const queryParams = reactive<LtLotQuery>({ pageNum: 1, pageSize: 10 } as any);
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();
const lotFormRef = ref<ElFormInstance>();
const dialog = reactive({ visible: false, title: '' });
const fefoDialog = reactive({
  visible: false,
  materialId: undefined as number | undefined,
  materialCode: '' as string,
  requiredQty: undefined as number | undefined,
  list: [] as LtLotVO[],
  loading: false
});
const traceDialog = reactive({ visible: false, data: null as LtLotTraceResult | null, activeTab: 'consumptions' });
const form = reactive<LtLotForm>({ materialId: 0, quantity: 0 } as LtLotForm);
const autoGenFlag = ref(false);
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();
const materialSelectTarget = ref<'form' | 'fefo'>('form');
const vendorDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: {
    pageNum: 1,
    pageSize: 10,
    vendorCode: '' as string,
    vendorName: '' as string
  }
});

const rules: ElFormRules = {
  materialId: [{ required: true, message: '物料ID不能为空', trigger: 'blur' }],
  quantity: [{ required: true, message: '批次数量不能为空', trigger: 'blur' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listLot(queryParams);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};
const resetQuery = () => {
  proxy!.resetForm('queryFormRef');
  handleQuery();
};
const handleSelectionChange = (selection: LtLotVO[]) => {
  ids.value = selection.map((i) => i.lotId);
};

const resetForm = () => {
  autoGenFlag.value = false;
  Object.assign(form, {
    lotId: undefined,
    lotCode: '',
    materialId: 0,
    materialCode: '',
    materialName: '',
    supplierId: undefined,
    supplierName: '',
    supplierLot: '',
    quantity: 0,
    producedDate: '',
    expiryDate: '',
    remark: ''
  });
  lotFormRef.value?.resetFields();
};

const handleAdd = () => {
  resetForm();
  dialog.title = '新增批次';
  dialog.visible = true;
};
const handleEdit = async (row: LtLotVO) => {
  resetForm();
  const res = await getLot(row.lotId);
  Object.assign(form, res.data);
  dialog.title = '编辑批次';
  dialog.visible = true;
};

const submitForm = () => {
  lotFormRef.value?.validate(async (valid) => {
    if (!valid) return;
    // 自动生成开关打开时清空编码，由后端 genSerialCode 落库生成(避免预览占号)
    if (!form.lotId && autoGenFlag.value) {
      form.lotCode = undefined;
    }
    if (form.lotId) {
      await updateLot(form);
      proxy!.$modal.msgSuccess('修改成功');
    } else {
      await addLot(form);
      proxy!.$modal.msgSuccess('新增成功');
    }
    dialog.visible = false;
    getList();
  });
};

const handleDelete = async () => {
  await proxy!.$modal.confirm('确认删除选中批次?仅 IN_STOCK/SCRAPPED 状态可删');
  await delLot(ids.value);
  proxy!.$modal.msgSuccess('删除成功');
  getList();
};

const handleQuarantine = async (row: LtLotVO) => {
  const remark = await proxy!.$modal.prompt('请输入隔离原因', '批次隔离', { inputType: 'textarea' });
  await quarantineLot(row.lotId, remark || '');
  proxy!.$modal.msgSuccess('隔离成功');
  getList();
};
const handleScrap = async (row: LtLotVO) => {
  const remark = await proxy!.$modal.prompt('请输入报废原因', '批次报废', { inputType: 'textarea' });
  await scrapLot(row.lotId, remark || '');
  proxy!.$modal.msgSuccess('报废成功');
  getList();
};
const handleRelease = async (row: LtLotVO) => {
  await proxy!.$modal.confirm(`确认放行批次 ${row.lotCode}? 将解除隔离恢复在库状态`);
  await releaseLot(row.lotId);
  proxy!.$modal.msgSuccess('放行成功');
  getList();
};
const handleCommand = async (cmd: string, row: LtLotVO) => {
  await updateLotQcStatus(row.lotId, cmd);
  proxy!.$modal.msgSuccess('QC状态更新成功');
  getList();
};

const handleFefo = () => {
  fefoDialog.visible = true;
  fefoDialog.list = [];
  fefoDialog.materialId = undefined;
  fefoDialog.materialCode = '';
  fefoDialog.requiredQty = undefined;
};
const loadFefo = async () => {
  if (!fefoDialog.materialId) {
    proxy!.$modal.msgWarning('请选择物料');
    return;
  }
  fefoDialog.loading = true;
  try {
    const res = await recommendFefo(fefoDialog.materialId, fefoDialog.requiredQty);
    fefoDialog.list = res.data;
    proxy!.$modal.msgSuccess(`推荐 ${fefoDialog.list.length} 个批次`);
  } finally {
    fefoDialog.loading = false;
  }
};

const handleTrace = async (row: LtLotVO) => {
  const res = await traceLot(row.lotId);
  traceDialog.data = res.data;
  traceDialog.activeTab = 'consumptions';
  traceDialog.visible = true;
};

const canEdit = (s: string) => ['IN_STOCK', 'IN_USE'].includes(s);
const canQuarantine = (s: string) => ['IN_STOCK', 'IN_USE'].includes(s);
const isExpiringSoon = (expiryDate?: string) => {
  if (!expiryDate) return false;
  const days = (new Date(expiryDate).getTime() - Date.now()) / 86400000;
  return days <= 30 && days >= 0;
};

// 编码自动生成开关: ON 走 preview 预览(不消耗流水号), 提交时清空由后端 genSerialCode 落库
const handleAutoGenChange = (val: boolean) => {
  if (val) {
    previewCode('MES_LOT_CODE').then((response) => {
      form.lotCode = response as unknown as string;
    });
  } else {
    form.lotCode = '';
  }
};

const openMaterialSelect = (target: 'form' | 'fefo') => {
  materialSelectTarget.value = target;
  materialSelectRef.value?.open();
};

const handleMaterialSelect = (row: any) => {
  if (materialSelectTarget.value === 'fefo') {
    fefoDialog.materialId = row.materialId;
    fefoDialog.materialCode = row.materialCode;
  } else {
    form.materialId = row.materialId;
    form.materialCode = row.materialCode;
    form.materialName = row.materialName;
    lotFormRef.value?.validateField('materialId');
  }
};

const openVendorDialog = () => {
  vendorDialog.visible = true;
  queryVendors();
};

const queryVendors = () => {
  listVendor(vendorDialog.query as any).then((res: any) => {
    vendorDialog.list = res.rows || [];
    vendorDialog.total = res.total || 0;
  });
};

const resetVendorQuery = () => {
  vendorDialog.query = { pageNum: 1, pageSize: 10, vendorCode: '', vendorName: '' };
  queryVendors();
};

const selectVendor = (row: any) => {
  form.supplierId = row.vendorId;
  form.supplierName = row.vendorName;
  vendorDialog.visible = false;
};

onMounted(() => getList());
</script>

<style scoped>
.text-danger {
  color: var(--el-color-danger);
  font-weight: bold;
}
.text-warning {
  color: var(--el-color-warning);
  font-weight: bold;
}
</style>
