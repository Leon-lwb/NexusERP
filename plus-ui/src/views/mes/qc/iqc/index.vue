<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="IQC编号" prop="iqcNo">
            <el-input v-model="queryParams.iqcNo" placeholder="请输入IQC编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in mes_qc_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="handleOpenAdd" v-hasPermi="['mes:qc:iqc:add']">新增IQC</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip>
      <el-table-column label="IQC编号" prop="iqcNo" min-width="140" />
      <el-table-column label="物料编码" prop="materialCode" min-width="140">
        <template #default="scope">{{ scope.row.materialCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="物料名称" prop="materialName" min-width="120" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.materialName || '-' }}</template>
      </el-table-column>
      <el-table-column label="供应商" prop="vendorName" min-width="120" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.vendorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="检验数量" prop="quantity" align="right" min-width="90" />
      <el-table-column label="状态" prop="status" align="center" min-width="80">
        <template #default="scope">
          <dict-tag :options="mes_qc_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="检验结果" prop="inspectResult" align="center" min-width="90">
        <template #default="scope">
          <dict-tag :options="mes_qc_result" :value="scope.row.inspectResult" />
        </template>
      </el-table-column>
      <el-table-column label="检验员" prop="inspectorName" min-width="90">
        <template #default="scope">{{ scope.row.inspectorName || '-' }}</template>
      </el-table-column>
      <el-table-column label="检验时间" prop="inspectTime" width="160">
        <template #default="scope">{{ scope.row.inspectTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="150" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleOpenDetail(scope.row)" v-hasPermi="['mes:qc:iqc:query']">详情</el-button>
          <el-button
            link
            type="primary"
            icon="Check"
            @click="handleOpenInspect(scope.row)"
            v-if="scope.row.status !== 'COMPLETED'"
            v-hasPermi="['mes:qc:iqc:inspect']"
            >检验</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增IQC对话框 -->
    <el-dialog v-model="addDialog.visible" title="新增IQC检验单" width="500px" append-to-body>
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="收货单">
          <el-input v-model="addReceiveCode" placeholder="可选, 选择ERP收货单" readonly clearable @clear="addForm.receiveId = undefined">
            <template #append>
              <el-button icon="Search" @click="openReceiveSelect" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="物料" required>
          <el-input v-model="addForm.materialCode" placeholder="请选择物料" readonly>
            <template #append>
              <el-button icon="Search" @click="openMaterialSelect" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="供应商名称">
          <el-input v-model="addForm.vendorName" placeholder="供应商名称(可选)" clearable />
        </el-form-item>
        <el-form-item label="检验数量">
          <el-input-number v-model="addForm.quantity" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="抽样数量">
          <el-input-number v-model="addForm.sampleQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确认新增</el-button>
      </template>
    </el-dialog>

    <!-- 检验对话框: 有明细走自动判定, 无明细回退人工判定 -->
    <el-dialog v-model="inspectDialog.visible" :title="'IQC检验 - ' + inspectForm.iqcNo" width="860px" append-to-body>
      <el-tabs v-model="inspectDialog.activeTab">
        <!-- 检验项目(自动判定) -->
        <el-tab-pane label="检验项目" name="details">
          <el-alert
            v-if="inspectEntries.length === 0"
            title="本检验单无检验明细(未匹配到启用的检验模板)"
            description="请在 质量管理 → 检验标准模板 中为该物料维护并启用模板后重建检验单, 或切换到'人工判定'页签直接录入结果。"
            type="info"
            :closable="false"
            style="margin-bottom: 12px"
          />
          <el-table v-else :data="inspectEntries" border size="small">
            <el-table-column label="项目" min-width="140">
              <template #default="scope"> {{ scope.row.itemCode }} {{ scope.row.itemName }} </template>
            </el-table-column>
            <el-table-column label="标准" prop="standardDesc" min-width="120" />
            <el-table-column label="样本量" prop="sampleQty" width="70" align="center" />
            <el-table-column label="关键度" width="80" align="center">
              <template #default="scope">
                <el-tag v-if="scope.row.criticality === 'CRITICAL'" type="danger" size="small">关键</el-tag>
                <el-tag v-else-if="scope.row.criticality === 'MINOR'" type="info" size="small">次要</el-tag>
                <el-tag v-else type="warning" size="small">重要</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="实测值" width="200">
              <template #default="scope">
                <el-input
                  v-if="scope.row.dataType !== 'QUALITATIVE'"
                  v-model="scope.row.measuredValue"
                  placeholder="数值, 多值逗号分隔"
                  size="small"
                />
                <el-select v-else v-model="scope.row.measuredValue" placeholder="选择" size="small">
                  <el-option label="OK" value="OK" />
                  <el-option label="NG" value="NG" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="判定" width="80" align="center">
              <template #default="scope">
                <el-tag v-if="scope.row.judgeResult === 'PASS'" type="success" size="small">PASS</el-tag>
                <el-tag v-else-if="scope.row.judgeResult === 'FAIL'" type="danger" size="small">FAIL</el-tag>
                <el-tag v-else type="info" size="small">待判</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-form label-width="100px" style="margin-top: 12px">
            <el-form-item label="不良品信息">
              <el-input v-model="inspectForm.defectInfo" type="textarea" :rows="2" placeholder='不合格时填写, 如: [{"type":"划痕","qty":2}]' />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="inspectForm.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <!-- 人工判定(兼容无模板检验单) -->
        <el-tab-pane label="人工判定" name="manual">
          <el-form :model="inspectForm" label-width="100px">
            <el-form-item label="检验结果" required>
              <el-radio-group v-model="inspectForm.inspectResult">
                <el-radio v-for="dict in inspectResultOptions" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="抽样数量">
              <el-input-number v-model="inspectForm.sampleQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="不良品信息">
              <el-input v-model="inspectForm.defectInfo" type="textarea" :rows="3" placeholder='不良品信息JSON, 如: [{"type":"划痕","qty":2}]' />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="inspectForm.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="inspectDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitInspect">提交判定</el-button>
      </template>
    </el-dialog>

    <!-- 检验单详情对话框(只读) -->
    <el-dialog v-model="detailDialog.visible" :title="'IQC详情 - ' + (detailData.iqcNo || '')" width="860px" append-to-body>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="IQC编号">{{ detailData.iqcNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="mes_qc_status" :value="detailData.status" />
        </el-descriptions-item>
        <el-descriptions-item label="检验结果">
          <dict-tag :options="mes_qc_result" :value="detailData.inspectResult" />
        </el-descriptions-item>
        <el-descriptions-item label="物料编码">{{ detailData.materialCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物料名称" :span="2">{{ detailData.materialName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailData.vendorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="检验数量">{{ detailData.quantity ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="抽样数量">{{ detailData.sampleQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="检验员">{{ detailData.inspectorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="检验时间">{{ detailData.inspectTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="不良品信息">{{ detailData.defectInfo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detailData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">检验明细</el-divider>
      <el-alert v-if="detailEntries.length === 0" title="无检验明细(该检验单未匹配启用的检验模板)" type="info" :closable="false" />
      <el-table v-else :data="detailEntries" border size="small">
        <el-table-column label="项目" min-width="140">
          <template #default="scope">{{ scope.row.itemCode }} {{ scope.row.itemName }}</template>
        </el-table-column>
        <el-table-column label="标准" prop="standardDesc" min-width="120" />
        <el-table-column label="样本量" prop="sampleQty" width="70" align="center" />
        <el-table-column label="关键度" width="80" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.criticality === 'CRITICAL'" type="danger" size="small">关键</el-tag>
            <el-tag v-else-if="scope.row.criticality === 'MINOR'" type="info" size="small">次要</el-tag>
            <el-tag v-else type="warning" size="small">重要</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实测值" prop="measuredValue" min-width="120" />
        <el-table-column label="判定" width="80" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.judgeResult === 'PASS'" type="success" size="small">PASS</el-tag>
            <el-tag v-else-if="scope.row.judgeResult === 'FAIL'" type="danger" size="small">FAIL</el-tag>
            <el-tag v-else type="info" size="small">待判</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="detailDialog.visible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 收货单选择对话框 -->
    <el-dialog v-model="receiveDialog.visible" title="选择收货单" width="960px" append-to-body>
      <el-form :model="receiveDialog.query" :inline="true">
        <el-form-item label="收货单号">
          <el-input v-model="receiveDialog.query.receiveCode" clearable @keyup.enter="queryReceives" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryReceives">搜索</el-button>
          <el-button icon="Refresh" @click="resetReceiveQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="receiveDialog.list" height="400" @row-dblclick="selectReceive">
        <el-table-column label="收货单号" prop="receiveCode" min-width="160" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectReceive(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="receiveDialog.total > 0"
        :total="receiveDialog.total"
        v-model:page="receiveDialog.query.pageNum"
        v-model:limit="receiveDialog.query.pageSize"
        @pagination="queryReceives"
      />
    </el-dialog>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />
  </div>
</template>

<script setup lang="ts" name="MesQcIqc">
import { listIqc, addIqc, inspectIqc, getIqc, getIqcDetails, inspectIqcWithDetails } from '@/api/mes/qc/iqc';
import type { QcIqcVO, QcIqcQuery, QcIqcForm, QcInspectDetailVO } from '@/api/mes/qc/iqc';
import { listReceive } from '@/api/erp/wm/receive';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_qc_status, mes_qc_result } = toRefs<any>(proxy?.useDict('mes_qc_status', 'mes_qc_result'));
// 检验结果可选项(排除 PENDING)
const inspectResultOptions = computed(() => (mes_qc_result.value as any[]).filter((d) => d.value !== 'PENDING'));

// 检验单的新增/检验为独立业务弹窗(非标准 CRUD 表单)，列表骨架走 useCrud，以下占位 API 不会被调用
const iqcFormRef = ref<ElFormInstance>();
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref<QcIqcQuery>({
  pageNum: 1,
  pageSize: 10,
  iqcNo: undefined,
  status: undefined
});

const { loading, list, total, showSearch, getList, handleQuery } = useCrud<QcIqcVO, QcIqcQuery>({
  apis: { list: listIqc, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'iqcId',
  entityName: 'IQC检验单',
  formRef: iqcFormRef,
  queryParams,
  initForm: () => ({})
});

const addDialog = reactive({ visible: false });
const addForm = reactive<QcIqcForm & { vendorId?: number }>({
  receiveId: undefined,
  materialId: undefined as unknown as number,
  materialCode: '',
  materialName: '',
  vendorName: '',
  quantity: undefined,
  sampleQty: undefined,
  remark: ''
});
// 选择器展示字段
const addReceiveCode = ref('');
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();
const receiveDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, receiveCode: '' as string }
});

const inspectDialog = reactive({ visible: false, activeTab: 'details' });
const inspectForm = reactive<{ iqcId: number; iqcNo: string; inspectResult: string; defectInfo?: string; sampleQty?: number; remark?: string }>({
  iqcId: 0,
  iqcNo: '',
  inspectResult: 'QUALIFIED',
  defectInfo: '',
  sampleQty: undefined,
  remark: ''
});
/** 检验单明细(编辑副本, 录入实测值后提交自动判定) */
const inspectEntries = ref<QcInspectDetailVO[]>([]);

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

const handleOpenAdd = () => {
  Object.assign(addForm, {
    receiveId: undefined,
    materialId: undefined as unknown as number,
    materialCode: '',
    materialName: '',
    vendorName: '',
    quantity: undefined,
    sampleQty: undefined,
    remark: ''
  });
  addReceiveCode.value = '';
  addDialog.visible = true;
};

const submitAdd = async () => {
  if (!addForm.materialId) {
    proxy?.$modal.msgWarning('请选择物料');
    return;
  }
  await addIqc(addForm);
  proxy?.$modal.msgSuccess('新增成功');
  addDialog.visible = false;
  getList();
};

const handleOpenInspect = async (row: QcIqcVO) => {
  inspectForm.iqcId = row.iqcId;
  inspectForm.iqcNo = row.iqcNo;
  inspectForm.inspectResult = 'QUALIFIED';
  inspectForm.defectInfo = '';
  inspectForm.sampleQty = row.sampleQty;
  inspectForm.remark = '';
  inspectEntries.value = [];
  inspectDialog.activeTab = 'details';
  inspectDialog.visible = true;
  // 拉取检验明细(有模板匹配才有; 失败不阻断, 回退人工判定)
  try {
    const res = await getIqcDetails(row.iqcId);
    inspectEntries.value = res.data || [];
    if (inspectEntries.value.length === 0) {
      inspectDialog.activeTab = 'manual';
    }
  } catch {
    inspectDialog.activeTab = 'manual';
  }
};

const submitInspect = async () => {
  if (inspectDialog.activeTab === 'details' && inspectEntries.value.length > 0) {
    // 自动判定: 校验实测值全部录入
    for (const entry of inspectEntries.value) {
      if (!entry.measuredValue) {
        proxy?.$modal.msgError(`项目 ${entry.itemCode} ${entry.itemName} 未录入实测值`);
        return;
      }
    }
    await inspectIqcWithDetails(inspectForm.iqcId, {
      details: inspectEntries.value.map((e) => ({
        detailId: e.detailId,
        measuredValue: e.measuredValue as string,
        sampleQty: e.sampleQty,
        remark: e.remark
      })),
      defectInfo: inspectForm.defectInfo,
      remark: inspectForm.remark
    });
    proxy?.$modal.msgSuccess('判定完成');
  } else {
    await inspectIqc(inspectForm.iqcId, inspectForm.inspectResult, inspectForm.defectInfo, inspectForm.sampleQty, inspectForm.remark);
    proxy?.$modal.msgSuccess('检验成功');
  }
  inspectDialog.visible = false;
  getList();
};

// ============ 详情查看(只读) ============
const detailDialog = reactive({ visible: false });
const detailData = ref<QcIqcVO>({} as QcIqcVO);
const detailEntries = ref<QcInspectDetailVO[]>([]);

const handleOpenDetail = async (row: QcIqcVO) => {
  detailData.value = row;
  detailEntries.value = [];
  detailDialog.visible = true;
  try {
    const [infoRes, detailRes] = await Promise.all([getIqc(row.iqcId), getIqcDetails(row.iqcId)]);
    if (infoRes.data) detailData.value = infoRes.data;
    detailEntries.value = detailRes.data || [];
  } catch {
    detailEntries.value = [];
  }
};

// ============ 收货单选择 ============
const openReceiveSelect = () => {
  receiveDialog.visible = true;
  queryReceives();
};
const queryReceives = () => {
  listReceive(receiveDialog.query as any).then((res: any) => {
    receiveDialog.list = res.rows || [];
    receiveDialog.total = res.total || 0;
  });
};
const resetReceiveQuery = () => {
  receiveDialog.query = { pageNum: 1, pageSize: 10, receiveCode: '' };
  queryReceives();
};
const selectReceive = (row: any) => {
  addForm.receiveId = row.receiveId;
  addReceiveCode.value = row.receiveCode;
  receiveDialog.visible = false;
};

// ============ 物料选择 ============
const openMaterialSelect = () => {
  materialSelectRef.value?.open();
};
const handleMaterialSelect = (row: any) => {
  addForm.materialId = row.materialId;
  addForm.materialCode = row.materialCode;
  addForm.materialName = row.materialName;
};

getList();
</script>
