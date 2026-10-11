<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="入库单编号" prop="receiveCode">
            <el-input v-model="queryParams.receiveCode" placeholder="请输入入库单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="入库单名称" prop="receiveName">
            <el-input v-model="queryParams.receiveName" placeholder="请输入入库单名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="采购订单编号" prop="purCode">
            <el-input v-model="queryParams.purCode" placeholder="请输入采购订单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="供应商名称" prop="vendorName">
            <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="入库日期" prop="receiveDate">
            <el-date-picker
              clearable
              v-model="queryParams.receiveDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择入库日期"
              style="width: 100%"
            >
            </el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="单据状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择单据状态" clearable style="width: 100%">
              <el-option v-for="dict in wm_material_receive_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
      <el-col :span="1.5"><el-button plain @click="openCertified()" v-hasPermi="['wm:receive:query']">认证收货工作台</el-button></el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:receive:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single || !isDraftOrRejected"
          :title="single ? '请先勾选一条单据' : isDraftOrRejected ? undefined : '仅「草稿」或「已驳回」状态可修改'"
          @click="handleUpdate"
          v-hasPermi="['wm:receive:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="single || !isDraft"
          :title="single ? '请先勾选一条单据' : isDraft ? undefined : '仅「草稿」状态可删除'"
          @click="handleDelete"
          v-hasPermi="['wm:receive:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Upload"
          :disabled="single || !isApproved"
          :title="single ? '请先勾选一条单据' : isApproved ? undefined : '仅「待上架」状态可上架'"
          @click="handleShelf"
          v-hasPermi="['wm:receive:edit']"
          >上架</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Check"
          :disabled="single || !isApproved"
          :title="single ? '请先勾选一条单据' : isApproved ? undefined : '仅「待上架」状态可确认上架'"
          @click="handleConfirmShelf"
          v-hasPermi="['wm:receive:shelf']"
          >确认上架</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Finished"
          :disabled="single || !isShelved"
          :title="single ? '请先勾选一条单据' : isShelved ? undefined : '仅「待入库」状态可入库'"
          @click="handleExecute"
          v-hasPermi="['wm:receive:execute']"
          >入库</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="View"
          :disabled="single"
          :title="single ? '请先勾选一条单据' : undefined"
          @click="handleView"
          v-hasPermi="['wm:receive:query']"
          >查看</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:receive:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="receiveList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="入库单编号" align="center" prop="receiveCode" min-width="140" />
      <el-table-column label="入库单名称" align="center" prop="receiveName" min-width="120" show-overflow-tooltip />
      <el-table-column label="到货通知单编号" align="center" prop="noticeCode" min-width="140" />
      <el-table-column label="采购订单编号" align="center" prop="purCode" min-width="140" />
      <el-table-column label="入库日期" align="center" prop="receiveDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.receiveDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单据状态" align="center" prop="status" min-width="90">
        <template #default="scope">
          <dict-tag :options="wm_material_receive_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <!-- <el-table-column label="上架人" align="center" prop="shelfByName" width="120">
        <template #default="scope">
          <span v-if="scope.row.status === 'SHELVED' || scope.row.status === 'FINISHED'">
            {{ scope.row.shelfByName || '-' }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="上架时间" align="center" prop="shelfTime" width="180">
        <template #default="scope">
          <span v-if="scope.row.status === 'SHELVED' || scope.row.status === 'FINISHED'">
            {{ parseTime(scope.row.shelfTime, '{y}-{m}-{d} {h}:{i}:{s}') }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column> -->
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="230" fixed="right">
        <template #default="scope">
          <el-button link @click="openCertified(scope.row)" v-hasPermi="['wm:receive:query']">认证工作台</el-button>
          <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['wm:receive:query']">查看</el-button>
          <el-dropdown
            v-if="rowMoreActions(scope.row).some((action) => hasPermi(action.perm))"
            trigger="click"
            @command="(command) => handleRowMoreCommand(command, scope.row)"
          >
            <el-button link type="primary"
              >更多<el-icon class="el-icon--right"><ArrowDown /></el-icon
            ></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="action in rowMoreActions(scope.row)"
                  v-show="hasPermi(action.perm)"
                  :key="action.key"
                  :command="action.key"
                  :icon="action.icon"
                  >{{ action.label }}</el-dropdown-item
                >
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <CertifiedWarehouseWorkflowDialog v-model="certifiedOpen" domain="receive" :initial-id="certifiedId" :initial-kind="certifiedKind" />
    <!-- 添加或修改物料入库单对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="receiveRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="入库单编号" prop="receiveCode">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.receiveCode" placeholder="请输入入库单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
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
            <el-form-item label="入库单名称" prop="receiveName">
              <el-input v-model="form.receiveName" placeholder="请输入入库单名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="到货通知单" prop="noticeCode">
              <el-row :gutter="10">
                <el-col :span="18">
                  <el-input v-model="form.noticeCode" placeholder="请选择到货通知单" readonly>
                    <template #append>
                      <el-button v-if="!isViewMode" icon="Search" @click="handleOpenArrivalNoticeDialog" />
                    </template>
                  </el-input>
                </el-col>
              </el-row>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购订单" prop="purCode">
              <el-input v-model="form.purCode" placeholder="请输入采购订单编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供应商" prop="vendorName">
              <el-input v-model="form.vendorName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="入库日期" prop="receiveDate">
              <el-date-picker clearable v-model="form.receiveDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择入库日期">
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
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 到货通知选择对话框 -->
    <el-dialog title="选择到货通知单" v-model="arrivalNoticeDialogVisible" width="800px" append-to-body>
      <el-form :model="arrivalNoticeQueryParams" :inline="true" label-width="100px">
        <el-form-item label="通知单编号">
          <el-input v-model="arrivalNoticeQueryParams.noticeCode" placeholder="请输入通知单编号" clearable @keyup.enter="handleArrivalNoticeQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleArrivalNoticeQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetArrivalNoticeQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="arrivalNoticeLoading" :data="arrivalNoticeList" @row-click="handleArrivalNoticeSelect" show-overflow-tooltip>
        <el-table-column label="通知单编号" align="center" prop="noticeCode" />
        <el-table-column label="通知单名称" align="center" prop="noticeName" />
        <el-table-column label="供应商名称" align="center" prop="vendorName" />
        <el-table-column label="到货日期" align="center" prop="arrivalDate" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.arrivalDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" @click="handleArrivalNoticeSelect(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="arrivalNoticeTotal > 0"
        :total="arrivalNoticeTotal"
        v-model:page="arrivalNoticeQueryParams.pageNum"
        v-model:limit="arrivalNoticeQueryParams.pageSize"
        @pagination="handleArrivalNoticePagination"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Receive">
import { listReceive, getReceive, delReceive, addReceive, updateReceive, execute, confirmShelf, getReceiveProcId } from '@/api/erp/wm/receive';
import type { ReceiveForm, ReceiveVO } from '@/api/erp/wm/receive/types';
import { listArrivalnotice } from '@/api/erp/wm/arrivalnotice';
import { useRouter } from 'vue-router';
import { previewCode } from '@/api/system/autocode/rule';

import { useRoute } from 'vue-router';
import { getToken } from '@/utils/auth';
import { useUserStore } from '@/store/modules/user';
import { createWarehouseLegacyGate } from '../certified/warehouseWorkflow';
import { getCertifiedReceiveContext, getCertifiedReceiveDetail } from '@/api/erp/wm/receive';
import CertifiedWarehouseWorkflowDialog from '../certified/CertifiedWarehouseWorkflowDialog.vue';
import type { WarehouseTarget } from '@/api/erp/wm/arrivalnotice/types';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_material_receive_status } = toRefs<any>(proxy.useDict('wm_material_receive_status'));

const receiveList = ref<ReceiveVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);
const autoGenFlag = ref(false);

const data = reactive({
  form: {} as ReceiveForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    receiveCode: null,
    receiveName: null,
    iqcId: null,
    iqcCode: null,
    noticeId: null,
    noticeCode: null,
    purId: null,
    purCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    receiveDate: null,
    status: null
  },
  rules: {
    receiveCode: [{ required: true, message: '入库单编号不能为空', trigger: 'blur' }],
    receiveName: [{ required: true, message: '入库单名称不能为空', trigger: 'blur' }]
  }
});

const selectedRow = computed(() => {
  if (ids.value.length === 1) {
    return receiveList.value.find((item: any) => item.receiveId === ids.value[0]);
  }
  return null;
});

/** 选中行可修改（草稿 / 已驳回） */
const isDraftOrRejected = computed(
  () => legacyRow(selectedRow.value) && (selectedRow.value?.status === 'DRAFT' || selectedRow.value?.status === 'REJECTED')
);
/** 选中行可删除（仅草稿） */
const isDraft = computed(() => legacyRow(selectedRow.value) && selectedRow.value?.status === 'DRAFT');
/** 选中行可上架 / 确认上架（待上架） */
const isApproved = computed(() => legacyRow(selectedRow.value) && selectedRow.value?.status === 'APPROVED');
/** 选中行可入库（待入库） */
const isShelved = computed(() => selectedRow.value?.status === 'SHELVED');

/** 行内「更多」下拉的操作项定义 */
interface RowAction {
  key: string;
  label: string;
  icon: string;
  perm: string;
}

/** 单据状态 → 行内「更多」下拉操作（行内按钮的唯一真相来源；FINISHED / CANCELED 无可用操作） */
const ROW_MORE_ACTIONS: Record<string, RowAction[]> = {
  DRAFT: [
    { key: 'edit', label: '修改', icon: 'Edit', perm: 'wm:receive:edit' },
    { key: 'delete', label: '删除', icon: 'Delete', perm: 'wm:receive:remove' }
  ],
  REJECTED: [{ key: 'edit', label: '修改', icon: 'Edit', perm: 'wm:receive:edit' }],
  APPROVING: [{ key: 'flow', label: '查看流程', icon: 'View', perm: 'wm:receive:query' }],
  APPROVED: [
    { key: 'shelf', label: '上架', icon: 'Upload', perm: 'wm:receive:edit' },
    { key: 'confirmShelf', label: '确认上架', icon: 'Check', perm: 'wm:receive:shelf' },
    { key: 'flow', label: '查看流程', icon: 'View', perm: 'wm:receive:query' }
  ],
  SHELVED: [{ key: 'execute', label: '入库', icon: 'Finished', perm: 'wm:receive:execute' }]
};

/** 获取某行「更多」下拉的操作列表 */
function rowMoreActions(row: any): RowAction[] {
  return legacyRow(row) ? (ROW_MORE_ACTIONS[row?.status] ?? []) : [];
}

/** 当前用户是否具备指定权限（用于下拉项过滤，避免对 el-dropdown-item 使用 v-hasPermi 指令） */
function hasPermi(permission: string) {
  return proxy.$auth.hasPermi(permission);
}

const { queryParams, form, rules } = toRefs(data);

const warehouseRoute = useRoute();
const warehouseUser = useUserStore();
const protectionRevision = ref(0);
const certifiedOpen = ref(false);
const certifiedId = ref('');
const certifiedKind = ref<WarehouseTarget>('ORDER');
/** 独立服务器保护事实，不用列表 attr1/status 作为认证判断。 */
const legacyGate = createWarehouseLegacyGate({
  domain: 'receive',
  readCredential: getToken,
  readScope: () => ({
    tenantId: warehouseUser.tenantId,
    userId: warehouseUser.userId,
    routeKey: warehouseRoute.fullPath + '|' + ids.value.join(',') + '|' + String(form.value.receiveId ?? '')
  }),
  hasPermission: (p) => proxy.$auth.hasPermi(p),
  context: getCertifiedReceiveContext,
  detail: getCertifiedReceiveDetail,
  changed: () => {
    protectionRevision.value++;
  }
});
function legacyRow(row: any) {
  protectionRevision.value;
  return !!row && legacyGate.known(row.receiveId);
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
  certifiedId.value = row?.receiveId == null ? '' : String(row.receiveId);
  certifiedKind.value = row?.receiveId == null ? 'ORDER' : 'RECEIVE';
  certifiedOpen.value = true;
}
watch(
  () => receiveList.value,
  (rows) => {
    legacyGate.clear();
    for (const row of rows ?? []) void legacyGate.probe(row.receiveId);
  }
);
watch(
  () => [warehouseRoute.fullPath, warehouseUser.userId, warehouseUser.tenantId, getToken()],
  () => legacyGate.clear()
);

async function legacyFormPermit() {
  try {
    return form.value.receiveId == null
      ? await legacyGate.requireNew('wm:receive:add', form.value as any)
      : await legacyGate.requireLegacy([form.value.receiveId], 'wm:receive:edit');
  } catch {
    proxy.$modal.msgWarning('认证或未知来源关闭通用保存');
    return null;
  }
}

/** 查询物料入库单列表 */
function getList() {
  loading.value = true;
  // 表格无 row-key，数据替换后勾选会被清空，同步重置选中态避免工具栏按钮停留在旧的可用状态
  ids.value = [];
  single.value = true;
  listReceive(queryParams.value).then((response: any) => {
    receiveList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    receiveId: null,
    receiveCode: null,
    receiveName: null,
    iqcId: null,
    iqcCode: null,
    noticeId: null,
    noticeCode: null,
    purId: null,
    purCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    receiveDate: null,
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
  };
  proxy.resetForm('receiveRef');
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.receiveId);
  single.value = selection.length != 1;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '添加物料入库单';
}

/** 修改按钮操作 */
async function handleUpdate(row: any) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }

  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    const selectedRow = receiveList.value.find((item: any) => item.receiveId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }

  // 校验状态：只有草稿和已驳回状态才能修改
  if (row.status !== 'DRAFT' && row.status !== 'REJECTED') {
    proxy.$modal.msgWarning('当前状态不允许修改，只有草稿和已驳回状态才能修改！');
    return;
  }

  if (!(await legacyPermit([row.receiveId], 'wm:receive:edit'))) return;
  const receiveId = row.receiveId;
  router.push({
    path: '/erp/wm/receive/form',
    query: { id: receiveId, action: 'edit' }
  });
}

/** 查看按钮操作 */
function handleView(row: any) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }

  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    const selectedRow = receiveList.value.find((item: any) => item.receiveId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }

  const receiveId = row.receiveId;
  router.push({
    path: '/erp/wm/receive/form',
    query: { id: receiveId, action: 'view' }
  });
}

/** 提交按钮 */
async function submitForm() {
  const session = await legacyFormPermit();
  if (!session) return;
  (proxy.$refs['receiveRef'] as any).validate((valid: boolean) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.receiveCode = undefined;
      }
      if (form.value.receiveId != null) {
        updateReceive(form.value, session).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addReceive(form.value, session).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
async function handleDelete(row: any) {
  // 工具栏点击时传入的是 MouseEvent，需兜底取选中行 ID
  if (row instanceof Event) {
    row = undefined;
  }
  // 校验状态：只有草稿状态才能删除
  if (row && row.status !== 'DRAFT') {
    proxy.$modal.msgWarning('当前状态不允许删除，只有草稿状态才能删除！');
    return;
  }
  if (!row) {
    // 批量删除仅允许草稿状态
    if (!ids.value || ids.value.length === 0) {
      proxy.$modal.msgWarning('请先选择要删除的入库单');
      return;
    }
  }
  const selected = row?.receiveId ? [row.receiveId] : [...ids.value];
  const session = await legacyPermit(selected, 'wm:receive:remove');
  if (!session) return;
  const _receiveIds = row?.receiveId || ids.value;
  proxy.$modal
    .confirm('是否确认删除物料入库单编号为"' + _receiveIds + '"的数据项？')
    .then(function () {
      return delReceive(_receiveIds, session);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 上架按钮操作 */
async function handleShelf(row: any) {
  if (row && row.type && row instanceof Event) {
    row = null;
  }

  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行上架');
      return;
    }
    const selectedRow = receiveList.value.find((item: any) => item.receiveId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }

  // 校验状态：只有待上架状态才能执行上架操作
  if (row.status !== 'APPROVED') {
    proxy.$modal.msgWarning('当前状态不允许上架，只有待上架状态才能执行上架操作！');
    return;
  }

  if (!(await legacyPermit([row.receiveId], 'wm:receive:edit'))) return;
  const receiveId = row.receiveId;
  router.push({
    path: '/erp/wm/receive/form',
    query: { id: receiveId, action: 'edit', pageType: 'shelf' }
  });
}

/** 入库按钮操作 */
async function handleExecute() {
  if (ids.value.length !== 1) {
    proxy.$modal.msgWarning('请选择一条数据进行入库');
    return;
  }
  const selectedRow = receiveList.value.find((item: any) => item.receiveId == ids.value[0]);
  if (!selectedRow) {
    proxy.$modal.msgWarning('未找到选中的数据');
    return;
  }

  // 校验状态：只有待入库状态才能执行入库操作
  if (selectedRow.status !== 'SHELVED') {
    proxy.$modal.msgWarning('当前状态不允许入库，只有待入库状态才能执行入库操作！');
    return;
  }

  const session = await legacyPermit([selectedRow.receiveId], 'wm:receive:execute');
  if (!session) return;
  proxy.$modal
    .confirm('是否确认执行物料入库操作？')
    .then(function () {
      return execute(selectedRow.receiveId, {}, session);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('入库成功');
    })
    .catch(() => {});
}

/** 确认上架按钮操作 */
function handleConfirmShelf() {
  if (ids.value.length !== 1) {
    proxy.$modal.msgWarning('请选择一条数据进行确认上架');
    return;
  }
  const selectedRow = receiveList.value.find((item: any) => item.receiveId == ids.value[0]);
  if (!selectedRow) {
    proxy.$modal.msgWarning('未找到选中的数据');
    return;
  }
  handleConfirmShelfRow(selectedRow);
}

/** 行内确认上架按钮操作 */
async function handleConfirmShelfRow(row: any) {
  const session = await legacyPermit([row.receiveId], 'wm:receive:shelf');
  if (!session) return;
  // 校验状态：只有待上架状态才能执行确认上架操作
  if (row.status !== 'APPROVED') {
    proxy.$modal.msgWarning('当前状态不允许确认上架，只有待上架状态才能执行确认上架操作！');
    return;
  }
  proxy.$modal
    .confirm('是否确认上架？确认后单据状态将变为"待入库"。')
    .then(function () {
      return confirmShelf(row.receiveId, session);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('确认上架成功');
    })
    .catch(() => {});
}

/** 行内入库按钮操作 */
async function handleExecuteRow(row: any) {
  const session = await legacyPermit([row.receiveId], 'wm:receive:execute');
  if (!session) return;
  // 校验状态：只有待入库状态才能执行入库操作
  if (row.status !== 'SHELVED') {
    proxy.$modal.msgWarning('当前状态不允许入库，只有待入库状态才能执行入库操作！');
    return;
  }
  proxy.$modal
    .confirm('是否确认执行物料入库操作？')
    .then(function () {
      return execute(row.receiveId, {}, session);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('入库成功');
    })
    .catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download(
    '/erp/wm/receive/export',
    {
      ...queryParams.value
    },
    `receive_${new Date().getTime()}.xlsx`
  );
}

/** 查看流程按钮操作 */
function handleViewFlow(row: any) {
  getReceiveProcId(String(row.receiveId))
    .then((res: any) => {
      if (res.code === 200 && res.data) {
        router.push('/workflow/process/detail/' + res.data);
      } else {
        proxy.$modal.msgWarning('暂无流程信息');
      }
    })
    .catch(() => {
      proxy.$modal.msgWarning('获取流程信息失败');
    });
}

/** 行内「更多」下拉命令分发 */
function handleRowMoreCommand(command: string, row: any) {
  switch (command) {
    case 'edit':
      handleUpdate(row);
      break;
    case 'delete':
      handleDelete(row);
      break;
    case 'shelf':
      handleShelf(row);
      break;
    case 'confirmShelf':
      handleConfirmShelfRow(row);
      break;
    case 'execute':
      handleExecuteRow(row);
      break;
    case 'flow':
      handleViewFlow(row);
      break;
  }
}

// 自动生成编码
function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    previewCode('WM_MATERIAL_RECEIVE_CODE').then((response: any) => {
      form.value.receiveCode = response;
    });
  } else {
    form.value.receiveCode = null;
  }
}

// 到货通知选择相关变量
const arrivalNoticeDialogVisible = ref(false);
const arrivalNoticeList = ref([]);
const arrivalNoticeLoading = ref(false);
const arrivalNoticeTotal = ref(0);

const arrivalNoticeQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  noticeCode: null
});

/** 打开到货通知选择对话框 */
function handleOpenArrivalNoticeDialog() {
  arrivalNoticeDialogVisible.value = true;
  getArrivalNoticeList();
}

/** 查询到货通知列表 */
function getArrivalNoticeList() {
  arrivalNoticeLoading.value = true;
  listArrivalnotice(arrivalNoticeQueryParams.value).then((response: any) => {
    arrivalNoticeList.value = response.rows;
    arrivalNoticeTotal.value = response.total;
    arrivalNoticeLoading.value = false;
  });
}

/** 到货通知搜索按钮操作 */
function handleArrivalNoticeQuery() {
  arrivalNoticeQueryParams.value.pageNum = 1;
  getArrivalNoticeList();
}

/** 重置到货通知查询 */
function resetArrivalNoticeQuery() {
  arrivalNoticeQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    noticeCode: null
  };
  handleArrivalNoticeQuery();
}

/** 选择到货通知 */
function handleArrivalNoticeSelect(row: any) {
  form.value.noticeId = row.noticeId;
  form.value.noticeCode = row.noticeCode;
  form.value.vendorId = row.vendorId;
  form.value.vendorCode = row.vendorCode;
  form.value.vendorName = row.vendorName;
  arrivalNoticeDialogVisible.value = false;
}

/** 处理到货通知分页 */
function handleArrivalNoticePagination(pagination: any) {
  arrivalNoticeQueryParams.value.pageNum = pagination.page;
  arrivalNoticeQueryParams.value.pageSize = pagination.limit;
  getArrivalNoticeList();
}

onActivated(() => {
  getList();
});
</script>
