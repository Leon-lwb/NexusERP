<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="NCR编号" prop="ncrCode">
            <el-input v-model="queryParams.ncrCode" placeholder="请输入NCR编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="来源" prop="sourceType">
            <el-select v-model="queryParams.sourceType" placeholder="请选择来源" clearable style="width: 100%">
              <el-option v-for="dict in mes_ncr_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in mes_ncr_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工单编号" prop="woNo">
            <el-input v-model="queryParams.woNo" placeholder="请输入工单编号" clearable @keyup.enter="handleQuery" />
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
        <el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:ncr:order:add']">新增NCR</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:ncr:order:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="NCR编号" prop="ncrCode" min-width="150" />
      <el-table-column label="来源" prop="sourceType" align="center" width="100">
        <template #default="scope"><dict-tag :options="mes_ncr_source_type" :value="scope.row.sourceType" /></template>
      </el-table-column>
      <el-table-column label="物料" prop="materialName" min-width="140" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
      </el-table-column>
      <el-table-column label="工单编号" prop="woNo" width="140" />
      <el-table-column label="缺陷数量" prop="defectQty" align="right" width="100" />
      <el-table-column label="状态" prop="status" align="center" width="100">
        <template #default="scope"><dict-tag :options="mes_ncr_status" :value="scope.row.status" /></template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="170" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="280" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleReview(scope.row)"
            v-if="scope.row.status === 'OPEN'"
            v-hasPermi="['mes:ncr:order:review']"
            >评审</el-button
          >
          <el-button
            link
            type="warning"
            icon="Tools"
            @click="handleDispose(scope.row)"
            v-if="scope.row.status === 'OPEN' || scope.row.status === 'REVIEW'"
            v-hasPermi="['mes:ncr:order:dispose']"
            >处置</el-button
          >
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 'CLOSED'"
            v-hasPermi="['mes:ncr:order:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="orderFormRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="NCR编号" prop="ncrCode">
              <div style="display: flex; align-items: center; gap: 10px; width: 100%">
                <el-input v-model="form.ncrCode" :disabled="autoGenFlag" placeholder="留空自动生成" style="flex: 1" />
                <el-switch v-model="autoGenFlag" active-text="生成" @change="handleAutoGenChange" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源" style="width: 100%">
                <el-option v-for="dict in mes_ncr_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料" prop="materialId">
              <el-input v-model="form.materialCode" placeholder="请选择物料" readonly>
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称">
              <el-input v-model="form.materialName" placeholder="选择物料后自动带出" readonly />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工单编号" prop="woNo">
              <el-input v-model="form.woNo" placeholder="请选择工单" readonly>
                <template #append>
                  <el-button icon="Search" @click="openWoDialog" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="缺陷数量" prop="defectQty">
              <el-input-number v-model="form.defectQty" :min="1" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 处置对话框 -->
    <el-dialog title="NCR处置" v-model="disposeDialog.visible" width="500px" append-to-body>
      <el-form :model="disposeForm" label-width="100px">
        <el-form-item label="处置方式" prop="disposition" required>
          <el-select v-model="disposeForm.disposition" placeholder="请选择处置方式" style="width: 100%">
            <el-option v-for="dict in mes_ncr_disposition" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置备注">
          <el-input v-model="disposeForm.remark" type="textarea" :rows="3" placeholder="请输入处置备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <!-- 处置是授权行为，一律走二级认证（后端也拒绝 LOGIN_ONLY），故此处不再提供不签名的处置入口 -->
        <el-button type="primary" @click="openSignDispose" v-hasPermi="['mes:ncr:order:dispose']">签署并处置</el-button>
        <el-button @click="disposeDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 处置二级认证弹窗（签署并处置专用）：凭证只存弹窗内存，后端拒签时保持打开供改正重试 -->
    <SignAuthDialog ref="signAuthRef" v-model="signAuthVisible" @confirmed="handleSignConfirmed" />

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailDrawer.visible" title="NCR详情" size="800px" append-to-body>
      <template v-if="detailDrawer.data">
        <el-descriptions :column="2" border title="基本信息" class="mt8">
          <el-descriptions-item label="NCR编号">{{ detailDrawer.data.ncrCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <dict-tag :options="mes_ncr_status" :value="detailDrawer.data.status" />
          </el-descriptions-item>
          <el-descriptions-item label="来源">
            <dict-tag :options="mes_ncr_source_type" :value="detailDrawer.data.sourceType" />
          </el-descriptions-item>
          <el-descriptions-item label="物料">{{ detailDrawer.data.materialCode }} {{ detailDrawer.data.materialName }}</el-descriptions-item>
          <el-descriptions-item label="工单编号">{{ detailDrawer.data.woNo }}</el-descriptions-item>
          <el-descriptions-item label="缺陷数量">{{ detailDrawer.data.defectQty }}</el-descriptions-item>
          <el-descriptions-item label="评审人">{{ detailDrawer.data.reviewByName }}</el-descriptions-item>
          <el-descriptions-item label="评审时间">{{ detailDrawer.data.reviewTime }}</el-descriptions-item>
          <el-descriptions-item label="处置方式">
            <dict-tag :options="mes_ncr_disposition" :value="detailDrawer.data.disposition" />
          </el-descriptions-item>
          <el-descriptions-item label="处置人">{{ detailDrawer.data.dispositionByName }}</el-descriptions-item>
          <el-descriptions-item label="处置时间">{{ detailDrawer.data.dispositionTime }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailDrawer.data.remark }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailDrawer.activeTab" class="mt16">
          <!-- 缺陷明细 -->
          <el-tab-pane label="缺陷明细" name="defect">
            <el-table :data="defectList" size="small" v-loading="defectLoading">
              <el-table-column label="缺陷代码" prop="defectCode" width="120" />
              <el-table-column label="缺陷名称" prop="defectName" min-width="140" />
              <el-table-column label="数量" prop="defectQty" align="right" width="80" />
              <el-table-column label="原因分类" prop="defectCause" width="120">
                <template #default="scope"><dict-tag :options="mes_defect_cause" :value="scope.row.defectCause" /></template>
              </el-table-column>
              <el-table-column label="根因描述" prop="rootCauseDesc" min-width="160" show-overflow-tooltip />
            </el-table>
            <el-button type="primary" size="small" icon="Plus" class="mt8" @click="openDefectAdd">补录缺陷</el-button>
          </el-tab-pane>

          <!-- 返工工单 -->
          <el-tab-pane label="返工工单" name="rework">
            <el-table :data="reworkList" size="small" v-loading="reworkLoading">
              <el-table-column label="返工编号" prop="reworkCode" width="150" />
              <el-table-column label="原工单" prop="originalWoNo" width="140" />
              <el-table-column label="返工工单" prop="reworkWoNo" width="140" />
              <el-table-column label="返工数量" prop="reworkQty" align="right" width="90" />
              <el-table-column label="状态" prop="status" width="100">
                <template #default="scope"><dict-tag :options="mes_rework_status" :value="scope.row.status" /></template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="scope">
                  <el-button link type="primary" size="small" @click="viewReworkDetail(scope.row)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- 8D报告 -->
          <el-tab-pane label="8D报告" name="8d">
            <div v-if="reportData">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="D1 团队组成">{{ reportData.d1Team }}</el-descriptions-item>
                <el-descriptions-item label="D2 问题描述">{{ reportData.d2Problem }}</el-descriptions-item>
                <el-descriptions-item label="D3 遏制措施">{{ reportData.d3Interim }}</el-descriptions-item>
                <el-descriptions-item label="D4 根因分析">{{ reportData.d4RootCause }}</el-descriptions-item>
                <el-descriptions-item label="D5 永久措施">{{ reportData.d5Permanent }}</el-descriptions-item>
                <el-descriptions-item label="D6 措施验证">{{ reportData.d6Implement }}</el-descriptions-item>
                <el-descriptions-item label="D7 预防措施">{{ reportData.d7Prevent }}</el-descriptions-item>
                <el-descriptions-item label="D8 关闭总结">{{ reportData.d8Recognize }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <dict-tag :options="mes_8d_status" :value="reportData.status" />
                </el-descriptions-item>
              </el-descriptions>
              <el-button
                type="primary"
                size="small"
                class="mt8"
                @click="edit8dReport"
                v-if="reportData.status !== 'COMPLETED'"
                v-hasPermi="['mes:ncr:8d:edit']"
                >编辑8D报告</el-button
              >
            </div>
            <el-empty v-else description="暂无8D报告" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 补录缺陷对话框 -->
    <el-dialog title="补录缺陷" v-model="defectAddDialog.visible" width="500px" append-to-body>
      <el-form ref="defectFormRef" :model="defectForm" :rules="defectRules" label-width="100px">
        <el-form-item label="缺陷代码" prop="defectCodeId" required>
          <el-select v-model="defectForm.defectCodeId" placeholder="请选择缺陷代码" style="width: 100%" filterable>
            <el-option v-for="item in defectCodeList" :key="item.codeId" :label="item.code + ' - ' + item.name" :value="item.codeId" />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="缺陷数量" prop="defectQty">
              <el-input-number v-model="defectForm.defectQty" :min="1" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原因分类" prop="defectCause">
              <el-select v-model="defectForm.defectCause" placeholder="请选择原因分类" clearable style="width: 100%">
                <el-option v-for="dict in mes_defect_cause" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="根因描述" prop="rootCauseDesc">
          <el-input v-model="defectForm.rootCauseDesc" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="defectForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitDefect">确 定</el-button>
        <el-button @click="defectAddDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 8D编辑对话框 -->
    <el-dialog title="编辑8D报告" v-model="reportEditDialog.visible" width="640px" append-to-body>
      <el-form ref="reportFormRef" :model="reportForm" label-width="100px">
        <el-form-item label="D1 团队组成"><el-input v-model="reportForm.d1Team" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D2 问题描述"><el-input v-model="reportForm.d2Problem" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D3 遏制措施"><el-input v-model="reportForm.d3Interim" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D4 根因分析"><el-input v-model="reportForm.d4RootCause" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D5 永久措施"><el-input v-model="reportForm.d5Permanent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D6 措施验证"><el-input v-model="reportForm.d6Implement" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D7 预防措施"><el-input v-model="reportForm.d7Prevent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D8 关闭总结"><el-input v-model="reportForm.d8Recognize" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="reportForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitReport">保存</el-button>
        <el-button
          type="success"
          @click="submitReportAndComplete"
          v-if="reportForm.reportId && reportData?.status !== 'COMPLETED'"
          v-hasPermi="['mes:ncr:8d:complete']"
          >保存并完成</el-button
        >
        <el-button @click="reportEditDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />

    <!-- 工单选择对话框 -->
    <el-dialog v-model="woDialog.visible" title="选择工单" width="800px" append-to-body>
      <el-form :model="woDialog.query" :inline="true">
        <el-form-item label="工单编号"><el-input v-model="woDialog.query.woNo" clearable @keyup.enter="queryWos" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryWos">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="woDialog.list" height="380" @row-dblclick="selectWo">
        <el-table-column label="工单编号" prop="woNo" width="160" />
        <el-table-column label="产品" prop="productName" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectWo(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="woDialog.total > 0"
        :total="woDialog.total"
        v-model:page="woDialog.query.pageNum"
        v-model:limit="woDialog.query.pageSize"
        @pagination="queryWos"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="NcrOrder">
import { listOrder, getOrder, addOrder, updateOrder, delOrder, reviewOrder, disposeOrderWithSign } from '@/api/mes/ncr/order';
import type { NcrOrderVO, NcrOrderQuery } from '@/api/mes/ncr/order';
import { listDefect, addDefect } from '@/api/mes/ncr/defect';
import type { NcrDefectVO, NcrDefectForm } from '@/api/mes/ncr/defect';
import { listDefectCode } from '@/api/mes/ncr/defect-code';
import { listRework } from '@/api/mes/ncr/rework';
import type { NcrReworkOrderVO } from '@/api/mes/ncr/rework';
import { get8dByNcr, update8d, complete8d } from '@/api/mes/ncr/8d-report';
import type { Ncr8dReportVO, Ncr8dReportForm } from '@/api/mes/ncr/8d-report';
import { previewCode } from '@/api/system/autocode/rule';
import { listWorkOrder } from '@/api/mes/pro/workorder';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import SignAuthDialog from '@/components/sign/SignAuthDialog.vue';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_ncr_status, mes_ncr_source_type, mes_ncr_disposition, mes_defect_cause, mes_rework_status, mes_8d_status } = toRefs<any>(
  proxy?.useDict('mes_ncr_status', 'mes_ncr_source_type', 'mes_ncr_disposition', 'mes_defect_cause', 'mes_rework_status', 'mes_8d_status')
);

const orderFormRef = ref<ElFormInstance>();
const autoGenFlag = ref(false);
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();

const queryParams = ref<NcrOrderQuery>({
  pageNum: 1,
  pageSize: 10,
  ncrCode: undefined,
  sourceType: undefined,
  status: undefined,
  woNo: undefined
});

const rules: ElFormRules = {
  sourceType: [{ required: true, message: '来源不能为空', trigger: 'change' }],
  materialId: [{ required: true, message: '物料不能为空', trigger: 'blur' }],
  defectQty: [{ required: true, message: '缺陷数量不能为空', trigger: 'blur' }]
};

const {
  loading,
  list,
  total,
  open,
  title,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd
} = useCrud({
  apis: { list: listOrder, get: getOrder, add: addOrder, update: updateOrder, del: delOrder },
  idField: 'ncrId',
  entityName: 'NCR',
  formRef: orderFormRef,
  queryParams,
  initForm: () => ({
    ncrId: undefined,
    ncrCode: '',
    sourceType: 'CUSTOMER',
    woId: undefined,
    woNo: '',
    materialId: undefined,
    materialCode: '',
    materialName: '',
    defectQty: 1,
    remark: ''
  }),
  onReset: () => {
    autoGenFlag.value = false;
  },
  beforeSubmit: () => {
    if (!form.value.ncrId && autoGenFlag.value) {
      form.value.ncrCode = undefined;
    }
  }
});

/** 新增（包装避免 @click 事件对象被误传为 extraInit） */
function handleAdd() {
  rawHandleAdd();
}

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

// 处置
const disposeDialog = reactive({ visible: false, ncrId: 0 as number });
const disposeForm = reactive({ disposition: '', remark: '' });

// 详情抽屉
const detailDrawer = reactive({
  visible: false,
  data: null as NcrOrderVO | null,
  activeTab: 'defect'
});

// 缺陷明细(抽屉内)
const defectList = ref<NcrDefectVO[]>([]);
const defectLoading = ref(false);
const defectAddDialog = reactive({ visible: false });
const defectForm = reactive<NcrDefectForm>({ ncrId: 0, defectQty: 1 } as NcrDefectForm);
const defectFormRef = ref<ElFormInstance>();
const defectCodeList = ref<any[]>([]);
const defectRules = {
  defectCodeId: [{ required: true, message: '请选择缺陷代码', trigger: 'change' }],
  defectQty: [{ required: true, message: '请输入缺陷数量', trigger: 'blur' }]
};

// 返工工单(抽屉内)
const reworkList = ref<NcrReworkOrderVO[]>([]);
const reworkLoading = ref(false);

// 8D报告(抽屉内)
const reportData = ref<Ncr8dReportVO | null>(null);
const reportEditDialog = reactive({ visible: false });
const reportForm = reactive<Ncr8dReportForm>({});
const reportFormRef = ref<ElFormInstance>();

// 工单选择
const woDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, woNo: '' }
});

// 评审
const handleReview = async (row: NcrOrderVO) => {
  await proxy!.$modal.confirm(`确认评审 NCR ${row.ncrCode}? 评审通过后进入已评审状态`);
  await reviewOrder(row.ncrId);
  proxy!.$modal.msgSuccess('评审成功');
  getList();
};

// 处置：弹出处置方式对话框，确认时走带签名的接口（处置属于授权行为，不提供免签名路径）
const handleDispose = (row: NcrOrderVO) => {
  disposeDialog.ncrId = row.ncrId;
  disposeForm.disposition = '';
  disposeForm.remark = '';
  disposeDialog.visible = true;
};

const signAuthRef = ref<InstanceType<typeof SignAuthDialog>>();
const signAuthVisible = ref(false);

/** 打开二级认证弹窗前先校验处置方式 */
const openSignDispose = () => {
  if (!disposeForm.disposition) {
    proxy!.$modal.msgWarning('请选择处置方式');
    return;
  }
  signAuthVisible.value = true;
};

/**
 * 二级认证通过后调带签名的处置接口。
 * 失败（如「签署密码错误」）时保持认证弹窗打开，只复位按钮提交中状态让用户改正重试；
 * 此处不再本地 toast —— 后端拒签是 code=500，错误提示由 utils/request.ts 的拦截器统一
 * ElMessage 负责，两边都弹用户会看到两条一模一样的提示（对齐 PdfSealPlacer 的做法）。
 */
const handleSignConfirmed = async (auth: { authMethod: string; credential: string }) => {
  signAuthRef.value?.setLoading(true);
  try {
    await disposeOrderWithSign(disposeDialog.ncrId, disposeForm.disposition, disposeForm.remark, auth.authMethod, auth.credential);
  } catch (error) {
    console.error('签署并处置失败:', error);
    signAuthRef.value?.setLoading(false);
    return;
  }
  proxy!.$modal.msgSuccess('签署并处置成功');
  signAuthVisible.value = false;
  disposeDialog.visible = false;
  getList();
};

// 详情抽屉
const handleDetail = async (row: NcrOrderVO) => {
  const res = await getOrder(row.ncrId);
  detailDrawer.data = res.data;
  detailDrawer.activeTab = 'defect';
  detailDrawer.visible = true;
  loadDefectList(row.ncrId);
  loadReworkList(row.ncrId);
  load8dReport(row.ncrId);
};

// 缺陷明细
const loadDefectList = async (ncrId: number) => {
  defectLoading.value = true;
  try {
    const res = await listDefect(ncrId);
    defectList.value = res.data || [];
  } finally {
    defectLoading.value = false;
  }
};
const openDefectAdd = async () => {
  defectForm.ncrId = detailDrawer.data!.ncrId;
  defectForm.defectCodeId = undefined;
  defectForm.defectQty = 1;
  defectForm.defectCause = '';
  defectForm.rootCauseDesc = '';
  defectForm.remark = '';
  defectAddDialog.visible = true;
  // 加载缺陷代码列表
  const res = await listDefectCode({ pageNum: 1, pageSize: 9999 });
  defectCodeList.value = res.data || [];
};
const submitDefect = () => {
  defectFormRef.value?.validate(async (valid) => {
    if (!valid) return;
    await addDefect(defectForm);
    proxy!.$modal.msgSuccess('补录成功');
    defectAddDialog.visible = false;
    loadDefectList(detailDrawer.data!.ncrId);
  });
};

// 返工工单
const loadReworkList = async (ncrId: number) => {
  reworkLoading.value = true;
  try {
    const res = await listRework({ ncrId, pageNum: 1, pageSize: 99 });
    reworkList.value = res.rows || [];
  } finally {
    reworkLoading.value = false;
  }
};

// 8D报告
const load8dReport = async (ncrId: number) => {
  try {
    const res = await get8dByNcr(ncrId);
    reportData.value = res.data;
  } catch {
    reportData.value = null;
  }
};
const edit8dReport = () => {
  if (reportData.value) {
    Object.assign(reportForm, {
      reportId: reportData.value.reportId,
      ncrId: reportData.value.ncrId,
      d1Team: reportData.value.d1Team || '',
      d2Problem: reportData.value.d2Problem || '',
      d3Interim: reportData.value.d3Interim || '',
      d4RootCause: reportData.value.d4RootCause || '',
      d5Permanent: reportData.value.d5Permanent || '',
      d6Implement: reportData.value.d6Implement || '',
      d7Prevent: reportData.value.d7Prevent || '',
      d8Recognize: reportData.value.d8Recognize || '',
      remark: reportData.value.remark || ''
    });
  } else {
    Object.assign(reportForm, {
      ncrId: detailDrawer.data!.ncrId,
      reportId: undefined,
      d1Team: '',
      d2Problem: '',
      d3Interim: '',
      d4RootCause: '',
      d5Permanent: '',
      d6Implement: '',
      d7Prevent: '',
      d8Recognize: '',
      remark: ''
    });
  }
  reportEditDialog.visible = true;
};
const submitReport = async () => {
  await update8d(reportForm as Ncr8dReportForm);
  proxy!.$modal.msgSuccess('保存成功');
  reportEditDialog.visible = false;
  load8dReport(detailDrawer.data!.ncrId);
};
const submitReportAndComplete = async () => {
  await update8d(reportForm as Ncr8dReportForm);
  await complete8d(reportForm.reportId!);
  proxy!.$modal.msgSuccess('8D报告已完成');
  reportEditDialog.visible = false;
  load8dReport(detailDrawer.data!.ncrId);
};

const viewReworkDetail = (row: NcrReworkOrderVO) => {
  // 跳转工单详情页
  proxy!.$tab.openPage(`/mes/pro/workorder/detail/${row.reworkWoId}`, '工单详情');
};

// 编码自动生成
const handleAutoGenChange = (val: boolean) => {
  if (val) {
    previewCode('MES_NCR_CODE').then((response) => {
      form.value.ncrCode = response as unknown as string;
    });
  } else {
    form.value.ncrCode = '';
  }
};

// 物料选择
const openMaterialSelect = () => {
  materialSelectRef.value?.open();
};
const handleMaterialSelect = (row: any) => {
  form.value.materialId = row.materialId;
  form.value.materialCode = row.materialCode;
  form.value.materialName = row.materialName;
  orderFormRef.value?.validateField('materialId');
};

// 工单选择
const openWoDialog = () => {
  woDialog.visible = true;
  queryWos();
};
const queryWos = () => {
  listWorkOrder(woDialog.query as any).then((res: any) => {
    woDialog.list = res.rows || [];
    woDialog.total = res.total || 0;
  });
};
const selectWo = (row: any) => {
  form.value.woId = row.mesWoId;
  form.value.woNo = row.woNo;
  woDialog.visible = false;
};

getList();
</script>

<style scoped>
.mt8 {
  margin-top: 8px;
}
.mt16 {
  margin-top: 16px;
}
</style>
