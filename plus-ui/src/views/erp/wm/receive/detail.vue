<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="行ID" prop="lineId">
        <el-input v-model="queryParams.lineId" placeholder="请输入行ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="入库单ID" prop="receiveId">
        <el-input v-model="queryParams.receiveId" placeholder="请输入入库单ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="产品物料ID" prop="materialId">
        <el-input v-model="queryParams.materialId" placeholder="请输入产品物料ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="产品物料编码" prop="materialCode">
        <el-input v-model="queryParams.materialCode" placeholder="请输入产品物料编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="产品物料名称" prop="materialName">
        <el-input v-model="queryParams.materialName" placeholder="请输入产品物料名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="单位" prop="baseUomId">
        <el-input v-model="queryParams.baseUomId" placeholder="请输入单位" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="单位名称" prop="baseUomName">
        <el-input v-model="queryParams.baseUomName" placeholder="请输入单位名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="入库数量" prop="quantity">
        <el-input v-model="queryParams.quantity" placeholder="请输入入库数量" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="批次ID" prop="batchId">
        <el-input v-model="queryParams.batchId" placeholder="请输入批次ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="批次号" prop="batchCode">
        <el-input v-model="queryParams.batchCode" placeholder="请输入批次号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库ID" prop="warehouseId">
        <el-input v-model="queryParams.warehouseId" placeholder="请输入仓库ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库编码" prop="warehouseCode">
        <el-input v-model="queryParams.warehouseCode" placeholder="请输入仓库编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库名称" prop="warehouseName">
        <el-input v-model="queryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库区ID" prop="areaId">
        <el-input v-model="queryParams.areaId" placeholder="请输入库区ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库区编码" prop="areaCode">
        <el-input v-model="queryParams.areaCode" placeholder="请输入库区编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库区名称" prop="areaName">
        <el-input v-model="queryParams.areaName" placeholder="请输入库区名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库位ID" prop="locationId">
        <el-input v-model="queryParams.locationId" placeholder="请输入库位ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库位编码" prop="locationCode">
        <el-input v-model="queryParams.locationCode" placeholder="请输入库位编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库位名称" prop="locationName">
        <el-input v-model="queryParams.locationName" placeholder="请输入库位名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:detail:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:detail:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:detail:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['wm:detail:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:detail:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="detailList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="明细ID" align="center" prop="detailId" />
      <el-table-column label="行ID" align="center" prop="lineId" />
      <el-table-column label="入库单ID" align="center" prop="receiveId" />
      <el-table-column label="产品物料ID" align="center" prop="materialId" />
      <el-table-column label="产品物料编码" align="center" prop="materialCode" />
      <el-table-column label="产品物料名称" align="center" prop="materialName" />
      <el-table-column label="规格型号" align="center" prop="materialSpec" />
      <el-table-column label="单位" align="center" prop="baseUomId" />
      <el-table-column label="单位名称" align="center" prop="baseUomName" />
      <el-table-column label="入库数量" align="center" prop="quantity" />
      <el-table-column label="批次ID" align="center" prop="batchId" />
      <el-table-column label="批次号" align="center" prop="batchCode" />
      <el-table-column label="仓库ID" align="center" prop="warehouseId" />
      <el-table-column label="仓库编码" align="center" prop="warehouseCode" />
      <el-table-column label="仓库名称" align="center" prop="warehouseName" />
      <el-table-column label="库区ID" align="center" prop="areaId" />
      <el-table-column label="库区编码" align="center" prop="areaCode" />
      <el-table-column label="库区名称" align="center" prop="areaName" />
      <el-table-column label="库位ID" align="center" prop="locationId" />
      <el-table-column label="库位编码" align="center" prop="locationCode" />
      <el-table-column label="库位名称" align="center" prop="locationName" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:detail:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:detail:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改物料入库单明细对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="detailRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode || !ordinaryParent">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="行ID" prop="lineId">
              <el-input v-model="form.lineId" placeholder="请输入行ID" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="入库单ID" prop="receiveId">
              <el-input v-model="form.receiveId" placeholder="请输入入库单ID" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="产品物料ID" prop="materialId">
              <el-input v-model="form.materialId" placeholder="请输入产品物料ID" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="产品物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请输入产品物料编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="产品物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入产品物料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单位" prop="baseUomId">
              <el-input v-model="form.baseUomId" placeholder="请输入单位" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="单位名称" prop="baseUomName">
              <el-input v-model="form.baseUomName" placeholder="请输入单位名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="入库数量" prop="quantity">
              <el-input v-model="form.quantity" placeholder="请输入入库数量" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="批次ID" prop="batchId">
              <el-input v-model="form.batchId" placeholder="请输入批次ID" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="批次号" prop="batchCode">
              <el-input v-model="form.batchCode" placeholder="请输入批次号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库ID" prop="warehouseId">
              <el-input v-model="form.warehouseId" placeholder="请输入仓库ID" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库编码" prop="warehouseCode">
              <el-input v-model="form.warehouseCode" placeholder="请输入仓库编码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="仓库名称" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库区ID" prop="areaId">
              <el-input v-model="form.areaId" placeholder="请输入库区ID" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库区编码" prop="areaCode">
              <el-input v-model="form.areaCode" placeholder="请输入库区编码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="库区名称" prop="areaName">
              <el-input v-model="form.areaName" placeholder="请输入库区名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库位ID" prop="locationId">
              <el-input v-model="form.locationId" placeholder="请输入库位ID" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库位编码" prop="locationCode">
              <el-input v-model="form.locationCode" placeholder="请输入库位编码" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="库位名称" prop="locationName">
              <el-input v-model="form.locationName" placeholder="请输入库位名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="规格型号" prop="materialSpec">
              <el-input v-model="form.materialSpec" type="textarea" placeholder="请输入内容" />
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
          <el-button type="primary" @click="submitForm" v-if="!isViewMode && ordinaryParent">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Detail">
import { getToken } from '@/utils/auth';
import request from '@/utils/request';
import { normalizeBusinessId } from '@/utils/certified-business';
import type { CertifiedBusinessSession } from '@/utils/certified-business';
import { useUserStore } from '@/store/modules/user';
import { createWarehouseLegacyGate } from '../certified/warehouseWorkflow';
import { getCertifiedReceiveContext, getCertifiedReceiveDetail } from '@/api/erp/wm/receive';

import { listDetail, getDetail, delDetail } from '@/api/erp/wm/detail';
import type { DetailForm, DetailVO } from '@/api/erp/wm/detail/types';
import { useRoute } from 'vue-router';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();

const detailList = ref<DetailVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);

const data = reactive({
  form: {} as DetailForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    lineId: null,
    receiveId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    baseUomId: null,
    baseUomName: null,
    quantity: null,
    batchId: null,
    batchCode: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null
  },
  rules: {
    materialId: [{ required: true, message: '产品物料ID不能为空', trigger: 'blur' }],
    quantity: [{ required: true, message: '入库数量不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

const warehouseUser = useUserStore();
const protectionRevision = ref(0);
const nativeParentId = ref<string | null>(null);
const nativeDetailId = ref<string | null>(null);
let detailIdentityRevision = 0;
const legacyGate = createWarehouseLegacyGate({
  domain: 'receive',
  readCredential: getToken,
  readScope: () => ({
    tenantId: warehouseUser.tenantId,
    userId: warehouseUser.userId,
    routeKey: JSON.stringify([
      route.fullPath,
      nativeParentId.value,
      nativeDetailId.value,
      form.value.receiveId ?? null,
      form.value.detailId ?? null,
      form.value.lineId ?? null,
      ids.value,
      detailIdentityRevision
    ])
  }),
  hasPermission: (p) => proxy.$auth.hasPermi(p),
  context: getCertifiedReceiveContext,
  detail: getCertifiedReceiveDetail,
  changed: () => {
    protectionRevision.value++;
  }
});
// 身份改动同步推进代次；A→B→A 也不能复活已暂停的 validate/派发句柄。
watch(
  () => [nativeParentId.value, nativeDetailId.value, form.value.receiveId, form.value.detailId, form.value.lineId, ids.value.join(',')],
  () => {
    detailIdentityRevision++;
  },
  { flush: 'sync' }
);
const ordinaryParent = computed(() => {
  protectionRevision.value;
  return nativeParentId.value != null && legacyGate.known(nativeParentId.value);
});
/** 从原生 detail 重新解析父链，不能相信表单或 URL 指定的可变父 ID。 */
async function guardDetail(permission: string, detailId?: unknown) {
  try {
    const read = legacyGate.readSession();
    let parent: unknown;
    let lineId: string | null = null;
    if (detailId != null) {
      const response: any = await getDetail(detailId as any);
      read.assertCurrent();
      if (String(response.data?.detailId) !== String(detailId)) throw new Error('明细身份不一致');
      parent = response.data?.receiveId;
      lineId = normalizeBusinessId(response.data?.lineId);
    } else parent = nativeParentId.value ?? route.query.receiveId;
    const session = await legacyGate.requireLegacy([parent], permission);
    session.assertCurrent();
    return { session, parent: normalizeBusinessId(parent), lineId };
  } catch {
    proxy.$modal.msgWarning('收货父归属为认证或保护事实未知，明细旧写入口关闭');
    return null;
  }
}
watch(
  () => [route.fullPath, warehouseUser.userId, warehouseUser.tenantId, getToken()],
  () => {
    legacyGate.clear();
    nativeDetailId.value = null;
    nativeParentId.value = null;
  }
);

onMounted(() => {
  // 检查是否有传入的参数，如果有则直接打开新增对话框
  const action = route.query.action;
  if (action === 'add') {
    handleAddFromReceive();
  } else {
    getList();
  }
});

/** 查询物料入库单明细列表 */
function getList() {
  loading.value = true;
  listDetail(queryParams.value).then((response: any) => {
    detailList.value = response.rows;
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
    detailId: null,
    lineId: null,
    receiveId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    baseUomId: null,
    baseUomName: null,
    quantity: null,
    batchId: null,
    batchCode: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
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
  proxy.resetForm('detailRef');
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
  ids.value = selection.map((item: any) => item.detailId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
async function handleAdd() {
  const guarded = await guardDetail('wm:detail:add');
  if (!guarded) return;
  nativeParentId.value = guarded.parent;
  nativeDetailId.value = null;
  reset();
  form.value.receiveId = nativeParentId.value;
  isViewMode.value = false;
  open.value = true;
  title.value = '添加物料入库单明细';
}

/** 新增按钮操作 - 从收货单跳转过来 */
async function handleAddFromReceive() {
  const guarded = await guardDetail('wm:detail:add');
  if (!guarded) return;
  nativeParentId.value = guarded.parent;
  nativeDetailId.value = null;
  reset();
  isViewMode.value = false;

  // 从路由参数中获取值并填入表单（路由参数均为字符串）
  const query = route.query as Record<string, string>;
  form.value.receiveId = nativeParentId.value;
  form.value.lineId = query.lineId;
  form.value.materialId = query.materialId;
  form.value.materialCode = query.materialCode;
  form.value.materialName = query.materialName;
  form.value.baseUomId = query.baseUomId;
  form.value.baseUomName = query.baseUomName;
  form.value.quantity = query.quantity;

  open.value = true;
  title.value = '添加物料入库单明细';
}

/** 修改按钮操作 */
async function handleUpdate(row: any) {
  const guarded = await guardDetail('wm:detail:edit', row?.detailId ?? ids.value[0]);
  if (!guarded) return;
  nativeParentId.value = guarded.parent;
  nativeDetailId.value = String(row?.detailId ?? ids.value[0]);
  reset();
  isViewMode.value = false;
  const _detailId = row.detailId || ids.value;
  const detailReadSession = legacyGate.readSession();
  getDetail(_detailId).then((response: any) => {
    detailReadSession.assertCurrent();
    form.value = response.data;
    open.value = true;
    title.value = '修改物料入库单明细';
  });
}

/** 查看按钮操作 */
function handleView(row: any) {
  reset();
  isViewMode.value = true;
  const _detailId = row.detailId || ids.value;
  getDetail(_detailId).then((response: any) => {
    form.value = response.data;
    open.value = true;
    title.value = '查看物料入库单明细';
  });
}

/** 可变表单身份单独核对，ID 不经 Number 截断；新增 detail/line 可保持 NULL。 */
function detailIdentity(body: DetailForm): string {
  return JSON.stringify([body.receiveId, body.detailId, body.lineId].map((value) => (value == null ? null : normalizeBusinessId(value))));
}
/** 保留原 detail URL/method/DTO，仅本页补固定会话的实际 Axios dispatch 边界。 */
function saveDetail(body: DetailForm, session: CertifiedBusinessSession) {
  session.assertCurrent();
  const transforms = request.defaults.transformRequest;
  return request({
    url: '/erp/wm/detail',
    method: body.detailId != null ? 'put' : 'post',
    data: body,
    headers: { isToken: false, Authorization: 'Bearer ' + session.token },
    transformRequest: [
      ...(Array.isArray(transforms) ? transforms : transforms ? [transforms] : []),
      (data: unknown) => {
        session.assertCurrent();
        return data;
      }
    ]
  });
}
/** 提交按钮：完整 payload 在等待守卫/validate 前复制，父/明细/行不能跨 scope 切换。 */
async function submitForm() {
  try {
    const body: DetailForm = Object.freeze(JSON.parse(JSON.stringify(form.value)));
    const identity = detailIdentity(body);
    const revision = detailIdentityRevision;
    const parent = nativeParentId.value;
    const detail = nativeDetailId.value;
    if (parent == null || normalizeBusinessId(body.receiveId) !== normalizeBusinessId(parent)) return;
    if ((body.detailId == null ? null : normalizeBusinessId(body.detailId)) !== detail) return;
    const guarded = await guardDetail(body.detailId != null ? 'wm:detail:edit' : 'wm:detail:add', detail ?? undefined);
    if (!guarded || normalizeBusinessId(body.receiveId) !== guarded.parent) return;
    if (body.detailId != null && normalizeBusinessId(body.lineId) !== guarded.lineId) return;
    // 只包装已查询 A 的同一会话；不重新捕获当前 token/父 ID 以迁移到 B。
    const session: CertifiedBusinessSession = Object.freeze({
      token: guarded.session.token,
      assertCurrent: () => {
        guarded.session.assertCurrent();
        if (
          detailIdentityRevision !== revision ||
          nativeParentId.value !== parent ||
          nativeDetailId.value !== detail ||
          detailIdentity(form.value) !== identity
        ) {
          throw new Error('明细归属已变化');
        }
      }
    });
    session.assertCurrent();
    (proxy.$refs['detailRef'] as any).validate((valid: boolean) => {
      if (!valid) return;
      try {
        session.assertCurrent();
        saveDetail(body, session)
          .then(() => {
            session.assertCurrent();
            proxy.$modal.msgSuccess(body.detailId != null ? '修改成功' : '新增成功');
            open.value = false;
            getList();
          })
          .catch(() => proxy.$modal.msgWarning('明细保存未确认，请核对原生状态；不会自动重发'));
      } catch {
        proxy.$modal.msgWarning('明细身份或页面会话已变化，请重新读取后确认');
      }
    });
  } catch {
    proxy.$modal.msgWarning('明细身份或页面会话已变化，请重新读取后确认');
  }
}

/** 删除按钮操作 */
async function handleDelete(row: any) {
  const selected = row?.detailId != null ? [row.detailId] : [...ids.value];
  if (!selected.length) return;
  for (const value of selected) if (!(await guardDetail('wm:detail:remove', value))) return;
  const session = legacyGate.readSession();
  const _detailIds = row.detailId || ids.value;
  proxy.$modal
    .confirm('是否确认删除物料入库单明细编号为"' + _detailIds + '"的数据项？')
    .then(function () {
      session.assertCurrent();
      return delDetail(_detailIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download(
    '/erp/wm/detail/export',
    {
      ...queryParams.value
    },
    `detail_${new Date().getTime()}.xlsx`
  );
}

getList();
</script>
