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
                <el-option label="在库" value="IN_STOCK" />
                <el-option label="使用中" value="IN_USE" />
                <el-option label="已消耗" value="CONSUMED" />
                <el-option label="已隔离" value="QUARANTINED" />
                <el-option label="已报废" value="SCRAPPED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="QC状态" prop="qcStatus">
              <el-select v-model="queryParams.qcStatus" placeholder="请选择QC状态" clearable style="width: 100%">
                <el-option label="待检" value="PENDING" />
                <el-option label="合格" value="PASS" />
                <el-option label="不合格" value="FAIL" />
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
            <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="QC" prop="qcStatus" align="center" width="80">
          <template #default="scope">
            <el-tag :type="qcTagType(scope.row.qcStatus)" size="small">{{ qcLabel(scope.row.qcStatus) }}</el-tag>
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
              <el-input v-model="form.lotCode" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料ID" prop="materialId">
              <el-input-number v-model="form.materialId" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12"
            ><el-form-item label="物料编码"><el-input v-model="form.materialCode" placeholder="冗余字段" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="物料名称"><el-input v-model="form.materialName" placeholder="冗余字段" /></el-form-item
          ></el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12"
            ><el-form-item label="供应商ID"
              ><el-input-number v-model="form.supplierId" :min="1" controls-position="right" style="width: 100%" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="供应商名称"><el-input v-model="form.supplierName" /></el-form-item
          ></el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12"
            ><el-form-item label="供应商批次"><el-input v-model="form.supplierLot" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="批次数量" prop="quantity"
              ><el-input-number v-model="form.quantity" :min="0" :precision="4" controls-position="right" style="width: 100%" /></el-form-item
          ></el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12"
            ><el-form-item label="生产日期"
              ><el-date-picker v-model="form.producedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="有效期"
              ><el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item
          ></el-col>
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
        <el-form-item label="物料ID">
          <el-input-number v-model="fefoDialog.materialId" :min="1" controls-position="right" />
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
          <el-descriptions-item label="状态">{{ statusLabel(traceDialog.data.lot?.status) }}</el-descriptions-item>
          <el-descriptions-item label="批次数量">{{ traceDialog.data.lot?.quantity }}</el-descriptions-item>
          <el-descriptions-item label="已消耗">{{ traceDialog.data.lot?.consumedQty }}</el-descriptions-item>
          <el-descriptions-item label="剩余">{{ traceDialog.data.lot?.remainingQty }}</el-descriptions-item>
          <el-descriptions-item label="生产日期">{{ traceDialog.data.lot?.producedDate }}</el-descriptions-item>
          <el-descriptions-item label="有效期">{{ traceDialog.data.lot?.expiryDate }}</el-descriptions-item>
          <el-descriptions-item label="QC状态">{{ qcLabel(traceDialog.data.lot?.qcStatus) }}</el-descriptions-item>
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

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
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
  requiredQty: undefined as number | undefined,
  list: [] as LtLotVO[],
  loading: false
});
const traceDialog = reactive({ visible: false, data: null as LtLotTraceResult | null, activeTab: 'consumptions' });
const form = reactive<LtLotForm>({ materialId: 0, quantity: 0 } as LtLotForm);

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
};
const loadFefo = async () => {
  if (!fefoDialog.materialId) {
    proxy!.$modal.msgWarning('请输入物料ID');
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

const statusLabel = (s?: string) =>
  ({ IN_STOCK: '在库', IN_USE: '使用中', CONSUMED: '已消耗', QUARANTINED: '已隔离', SCRAPPED: '已报废' })[s || ''] || s;
const statusTagType = (s?: string): any =>
  ({ IN_STOCK: 'success', IN_USE: 'primary', CONSUMED: 'info', QUARANTINED: 'warning', SCRAPPED: 'danger' })[s || ''] || 'info';
const qcLabel = (s?: string) => ({ PENDING: '待检', PASS: '合格', FAIL: '不合格' })[s || ''] || s;
const qcTagType = (s?: string): any => ({ PENDING: 'info', PASS: 'success', FAIL: 'danger' })[s || ''] || 'info';
const canEdit = (s: string) => ['IN_STOCK', 'IN_USE'].includes(s);
const canQuarantine = (s: string) => ['IN_STOCK', 'IN_USE'].includes(s);
const isExpiringSoon = (expiryDate?: string) => {
  if (!expiryDate) return false;
  const days = (new Date(expiryDate).getTime() - Date.now()) / 86400000;
  return days <= 30 && days >= 0;
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
