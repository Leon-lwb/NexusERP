<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="通知单编号" prop="noticeCode">
            <el-input v-model="queryParams.noticeCode" placeholder="请输入通知单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="通知单名称" prop="noticeName">
            <el-input v-model="queryParams.noticeName" placeholder="请输入通知单名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="采购订单编号" prop="purOrderCode">
            <el-input v-model="queryParams.purOrderCode" placeholder="请输入采购订单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="供应商名称" prop="vendorName">
            <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="到货日期" prop="arrivalDate">
            <el-date-picker
              clearable
              v-model="queryParams.arrivalDate"
              type="date"
              value-format="YYYY-MM-DD"
              format="yyyy-MM-dd"
              placeholder="请选择到货日期"
              style="width: 100%"
            >
            </el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="单据状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择单据状态" clearable style="width: 100%">
              <el-option v-for="dict in wm_notice_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button plain @click="openCertified()" v-hasPermi="['wm:arrivalnotice:query']">认证到货工作台</el-button></el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['wm:arrivalnotice:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single || !ids.every((id) => legacyGate.known(id))"
          @click="handleUpdate()"
          v-hasPermi="['wm:arrivalnotice:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple || !ids.every((id) => legacyGate.known(id))"
          @click="handleDelete()"
          v-hasPermi="['wm:arrivalnotice:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="View"
          :disabled="single || !ids.every((id) => legacyGate.known(id))"
          @click="handleView()"
          v-hasPermi="['wm:arrivalnotice:query']"
          >查看</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Promotion"
          :disabled="single || !ids.every((id) => legacyGate.known(id))"
          @click="handleSubmit()"
          v-hasPermi="['wm:arrivalnotice:submit']"
          >提交审批</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['wm:arrivalnotice:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="arrivalnoticeList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="通知单编号" align="center" prop="noticeCode" />
      <el-table-column label="通知单名称" align="center" prop="noticeName" min-width="120" show-overflow-tooltip />
      <el-table-column label="采购订单编号" align="center" prop="purOrderCode" />
      <el-table-column label="供应商编码" align="center" prop="vendorCode" />
      <el-table-column label="供应商名称" align="center" prop="vendorName" min-width="120" show-overflow-tooltip />
      <el-table-column label="供应商简称" align="center" prop="vendorNick" />
      <el-table-column label="到货日期" align="center" prop="arrivalDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.arrivalDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="联系人" align="center" prop="contact" />
      <el-table-column label="联系方式" align="center" prop="phone" />
      <el-table-column label="单据状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_notice_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link @click="openCertified(scope.row)" v-hasPermi="['wm:arrivalnotice:query']">认证工作台</el-button>
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            :disabled="!legacyRow(scope.row)"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['wm:arrivalnotice:edit']"
            >修改</el-button
          >
          <el-button
            link
            type="success"
            icon="Promotion"
            :disabled="!legacyRow(scope.row)"
            @click="handleSubmitFlowRow(scope.row)"
            v-hasPermi="['wm:arrivalnotice:submit']"
            v-if="scope.row.status === 'DRAFT'"
            >提交审批</el-button
          >
          <el-button
            link
            type="info"
            icon="Connection"
            @click="onViewFlowRow(scope.row)"
            v-hasPermi="['wm:arrivalnotice:query']"
            v-if="scope.row.status === 'APPROVING' || scope.row.status === 'APPROVED'"
            >查看流程</el-button
          >
          <el-button
            link
            type="primary"
            icon="Delete"
            :disabled="!legacyRow(scope.row)"
            @click="handleDelete(scope.row)"
            v-hasPermi="['wm:arrivalnotice:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <CertifiedWarehouseWorkflowDialog v-model="certifiedOpen" domain="arrivalnotice" :initial-id="certifiedId" :initial-kind="certifiedKind" />
    <!-- 添加或修改到货通知对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="arrivalnoticeRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="通知单编号" prop="noticeCode">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.noticeCode" placeholder="请输入通知单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                <el-switch
                  v-model="autoGenFlag"
                  inline-prompt
                  active-color="#13ce66"
                  active-text="自动"
                  inactive-text="自动生成"
                  @change="handleAutoGenChange"
                  :disabled="isViewMode"
                />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="通知单名称" prop="noticeName">
              <el-input v-model="form.noticeName" placeholder="请输入通知单名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="采购订单编号" prop="purOrderCode">
              <el-input v-model="form.purOrderCode" placeholder="请输入采购订单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="供应商" prop="vendorId">
              <el-row :gutter="10">
                <el-col :span="18">
                  <el-input v-model="form.vendorName" placeholder="请选择供应商" readonly>
                    <template #append>
                      <el-button v-if="!isViewMode" icon="Search" @click="handleOpenVendorDialog()" />
                    </template>
                  </el-input>
                </el-col>
                <el-col :span="6">
                  <el-button v-if="form.vendorId && !isViewMode" type="danger" icon="Delete" @click="clearVendor()" />
                </el-col>
              </el-row>
              <el-input v-model="form.vendorId" style="display: none" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系人" prop="contact">
              <el-input v-model="form.contact" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系方式" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系方式" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="到货日期" prop="arrivalDate">
              <el-date-picker
                clearable
                v-model="form.arrivalDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择到货日期"
                style="width: 100%"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm()" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel()">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 供应商选择对话框 -->
    <el-dialog title="选择供应商" v-model="vendorDialogVisible" width="800px" append-to-body>
      <el-form :model="vendorQueryParams" :inline="true" label-width="100px">
        <el-form-item label="供应商名称">
          <el-input v-model="vendorQueryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleVendorQuery" />
        </el-form-item>
        <el-form-item label="供应商编码">
          <el-input v-model="vendorQueryParams.vendorCode" placeholder="请输入供应商编码" clearable @keyup.enter="handleVendorQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleVendorQuery()">搜索</el-button>
          <el-button icon="Refresh" @click="resetVendorQuery()">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="vendorLoading" :data="vendorList" @row-click="handleVendorSelect" show-overflow-tooltip>
        <el-table-column label="供应商编码" align="center" prop="vendorCode" />
        <el-table-column label="供应商名称" align="center" prop="vendorName" />
        <el-table-column label="联系人" align="center" prop="contactPerson" />
        <el-table-column label="联系电话" align="center" prop="phone" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" @click="handleVendorSelect(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="vendorTotal > 0"
        :total="vendorTotal"
        v-model:page="vendorQueryParams.pageNum"
        v-model:limit="vendorQueryParams.pageSize"
        @pagination="handleVendorPagination"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Arrivalnotice">
import {
  listArrivalnotice,
  getArrivalnotice,
  delArrivalnotice,
  addArrivalnotice,
  updateArrivalnotice,
  submitArrivalnotice,
  getArrivalnoticeProcId
} from '@/api/erp/wm/arrivalnotice';
import { previewCode } from '@/api/system/autocode/rule';
import { listVendor } from '@/api/erp/bd/vendor';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import { useRouter } from 'vue-router';

import { useRoute } from 'vue-router';
import { getToken } from '@/utils/auth';
import { useUserStore } from '@/store/modules/user';
import { createWarehouseLegacyGate } from '../certified/warehouseWorkflow';
import { getCertifiedArrivalContext, getCertifiedArrivalDetail } from '@/api/erp/wm/arrivalnotice';
import CertifiedWarehouseWorkflowDialog from '../certified/CertifiedWarehouseWorkflowDialog.vue';
import type { WarehouseTarget } from '@/api/erp/wm/arrivalnotice/types';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_notice_status } = toRefs<any>(proxy.useDict('wm_notice_status'));

const arrivalnoticeRef = ref<FormInstance>();
/** 通知单编号自动生成开关：开启时由后端按 WM_ARRIVAL_NOTICE_CODE 规则生成 */
const autoGenFlag = ref(false);

// 供应商选择相关变量
const vendorDialogVisible = ref(false);
const vendorList = ref([]);
const vendorLoading = ref(false);
const vendorTotal = ref(0);

const vendorQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  vendorName: null,
  vendorCode: null
});

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  noticeCode: null,
  noticeName: null,
  purOrderCode: null,
  vendorName: null,
  arrivalDate: null,
  status: null
});

const rules = {
  noticeCode: [{ required: true, message: '通知单编号不能为空', trigger: 'blur' }],
  noticeName: [{ required: true, message: '通知单名称不能为空', trigger: 'blur' }]
};

// 列表骨架 + 新增弹窗（新增走 dialog；修改/查看为路由表单页 form.vue，本地 router.push 覆盖）
const {
  loading,
  list: arrivalnoticeList,
  total,
  open,
  isViewMode,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleAdd,
  cancel,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: { list: listArrivalnotice, get: getArrivalnotice, add: addArrivalnotice, update: updateArrivalnotice, del: delArrivalnotice },
  idField: 'noticeId',
  entityName: '到货通知',
  formRef: arrivalnoticeRef,
  queryParams,
  initForm: () => ({
    noticeId: null,
    noticeCode: null,
    noticeName: null,
    purOrderCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    arrivalDate: null,
    contact: null,
    phone: null,
    status: null,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }),
  exportUrl: '/erp/wm/arrivalnotice/export',
  exportFileName: 'arrivalnotice',
  onReset: () => {
    autoGenFlag.value = false;
  },
  beforeSubmit: () => {
    // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
    if (autoGenFlag.value) {
      form.value.noticeCode = undefined;
    }
  }
});

/** 通用新增仍是明确无来源人工普通单，认证创建只能从独立工作台进行。 */
async function submitForm() {
  try {
    const session = await legacyGate.requireNew('wm:arrivalnotice:add', form.value as any);
    arrivalnoticeRef.value?.validate(async (valid) => {
      if (!valid) return;
      try {
        session.assertCurrent();
        if (autoGenFlag.value) form.value.noticeCode = undefined;
        await addArrivalnotice({ ...form.value }, session);
        session.assertCurrent();
        open.value = false;
        getList();
      } catch {
        proxy.$modal.msgWarning('普通通知保存条件未确认，禁止重发未知结果');
      }
    });
  } catch {
    proxy.$modal.msgWarning('认证或未知来源关闭通用新增，请使用认证工作台');
  }
}
async function handleDelete(row?: any) {
  const selected = row?.noticeId ? [row.noticeId] : [...ids.value];
  const session = await legacyPermit(selected, 'wm:arrivalnotice:remove');
  if (!session) return;
  try {
    await proxy.$modal.confirm('确认仅删除明确普通到货通知？');
    session.assertCurrent();
    await delArrivalnotice(selected, session);
    session.assertCurrent();
    getList();
  } catch {
    /* 不自动重发 */
  }
}

const warehouseRoute = useRoute();
const warehouseUser = useUserStore();
const protectionRevision = ref(0);
const certifiedOpen = ref(false);
const certifiedId = ref('');
const certifiedKind = ref<WarehouseTarget>('ORDER');
/** 独立服务器保护事实，不用列表 attr1/status 作为认证判断。 */
const legacyGate = createWarehouseLegacyGate({
  domain: 'arrivalnotice',
  readCredential: getToken,
  readScope: () => ({
    tenantId: warehouseUser.tenantId,
    userId: warehouseUser.userId,
    routeKey: warehouseRoute.fullPath + '|' + ids.value.join(',') + '|' + String(form.value.noticeId ?? '')
  }),
  hasPermission: (p) => proxy.$auth.hasPermi(p),
  context: getCertifiedArrivalContext,
  detail: getCertifiedArrivalDetail,
  changed: () => {
    protectionRevision.value++;
  }
});
function legacyRow(row: any) {
  protectionRevision.value;
  return !!row && legacyGate.known(row.noticeId);
}
async function legacyPermit(values: unknown[], permission: string) {
  try {
    return await legacyGate.requireLegacy(values, permission);
  } catch {
    proxy.$modal.msgWarning('认证或保护事实未知：旧通用入口已关闭，请只读核对或使用认证工作台');
    return null;
  }
}
function openCertified(row?: any) {
  certifiedId.value = row?.noticeId == null ? '' : String(row.noticeId);
  certifiedKind.value = row?.noticeId == null ? 'ORDER' : 'NOTICE';
  certifiedOpen.value = true;
}
watch(
  () => arrivalnoticeList.value,
  (rows) => {
    legacyGate.clear();
    for (const row of rows ?? []) void legacyGate.probe(row.noticeId);
  }
);
watch(
  () => [warehouseRoute.fullPath, warehouseUser.userId, warehouseUser.tenantId, getToken()],
  () => legacyGate.clear()
);

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 修改按钮操作（跳转路由表单页） */
async function handleUpdate(row?: any) {
  if (row instanceof Event) row = undefined;
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selectedRow = arrivalnoticeList.value.find((item: any) => item.noticeId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }

  if (!(await legacyPermit([row.noticeId], 'wm:arrivalnotice:edit'))) return;
  const noticeId = row.noticeId;
  router.push({
    path: '/erp/wm/arrivalnotice/form',
    query: { id: noticeId, action: 'edit' }
  });
}

/** 查看按钮操作（跳转路由表单页） */
function handleView(row?: any) {
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selectedRow = arrivalnoticeList.value.find((item: any) => item.noticeId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }

  const noticeId = row.noticeId;
  router.push({
    path: '/erp/wm/arrivalnotice/form',
    query: { id: noticeId, action: 'view' }
  });
}

/** 顶部提交审批按钮 */
function handleSubmit() {
  if (ids.value.length !== 1) {
    proxy.$modal.msgWarning('请选择一条数据进行提交审批');
    return;
  }
  const selectedRow = arrivalnoticeList.value.find((item: any) => item.noticeId == ids.value[0]);
  if (!selectedRow) {
    proxy.$modal.msgWarning('未找到选中的数据');
    return;
  }
  handleSubmitFlowRow(selectedRow);
}

/** 列表行提交审批 */
async function handleSubmitFlowRow(row: any) {
  const session = await legacyPermit([row.noticeId], 'wm:arrivalnotice:submit');
  if (!session) return;
  if (row.status !== 'DRAFT') {
    proxy.$modal.msgWarning('只有草稿状态的单据才能提交审批');
    return;
  }
  if (!row.selectFlowId) {
    proxy.$modal.msgWarning('该单据未选择审批流程，请先编辑选择流程并保存');
    return;
  }
  proxy.$modal
    .confirm('确认提交到货通知【' + row.noticeCode + '】审批？')
    .then(() => {
      submitArrivalnotice(String(row.noticeId), String(row.selectFlowId), session).then(() => {
        proxy.$modal.msgSuccess('提交审批成功');
        getList();
      });
    })
    .catch(() => {});
}

/** 列表行查看流程 */
function onViewFlowRow(row: any) {
  if (!row.noticeId) return;
  getArrivalnoticeProcId(String(row.noticeId)).then((res: any) => {
    const procInsId = res.data;
    if (!procInsId) {
      proxy.$modal.msgWarning('暂无流程信息');
      return;
    }
    router.push({
      path: '/workflow/process/detail/' + procInsId,
      query: { processed: 'false' }
    });
  });
}

// 自动生成编码
function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    previewCode('WM_ARRIVAL_NOTICE_CODE').then((response: any) => {
      form.value.noticeCode = response;
    });
  } else {
    form.value.noticeCode = null;
  }
}

/** 打开供应商选择对话框 */
function openVendorDialog() {
  vendorDialogVisible.value = true;
  getVendorList();
}

/** 处理打开供应商选择对话框事件 */
function handleOpenVendorDialog() {
  openVendorDialog();
}

/** 查询供应商列表 */
function getVendorList() {
  vendorLoading.value = true;
  listVendor(vendorQueryParams.value).then((response: any) => {
    vendorList.value = response.rows;
    vendorTotal.value = response.total;
    vendorLoading.value = false;
  });
}

/** 供应商搜索按钮操作 */
function handleVendorQuery() {
  vendorQueryParams.value.pageNum = 1;
  getVendorList();
}

/** 重置供应商查询 */
function resetVendorQuery() {
  vendorQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    vendorName: null,
    vendorCode: null
  };
  handleVendorQuery();
}

/** 选择供应商 */
function handleVendorSelect(row: any) {
  form.value.vendorId = row.vendorId;
  form.value.vendorCode = row.vendorCode;
  form.value.vendorName = row.vendorName;
  form.value.contact = row.contactPerson;
  form.value.phone = row.phone;
  vendorDialogVisible.value = false;
}

/** 清除已选择的供应商 */
function clearVendor() {
  form.value.vendorId = null;
  form.value.vendorCode = null;
  form.value.vendorName = null;
  form.value.contact = null;
  form.value.phone = null;
}

/** 处理供应商分页 */
function handleVendorPagination(pagination: any) {
  vendorQueryParams.value.pageNum = pagination.page;
  vendorQueryParams.value.pageSize = pagination.limit;
  getVendorList();
}

onActivated(() => {
  getList();
});
</script>
