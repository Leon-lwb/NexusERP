<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="工单" prop="woId">
            <el-input v-model="queryWoNo" placeholder="请选择工单" readonly>
              <template #append>
                <el-button icon="Search" @click="openWoSelect('query')" />
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="批次" prop="lotId">
            <el-input v-model="queryLotCode" placeholder="请选择批次" readonly>
              <template #append>
                <el-button icon="Search" @click="openLotDialog('query')" />
              </template>
            </el-input>
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
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:lt:consumption:add']">按产品 SN 记录消耗</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="MagicStick" @click="handlePick()" v-hasPermi="['mes:lt:lot:fefo']">FEFO 领料</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-alert
      title="历史工单级消耗不能证明某一产品 SN 的实际用料。新增消耗需选择正式产品身份并逐批填写数量。"
      type="info"
      :closable="false"
      class="mb-3"
    />
    <el-table v-loading="loading" :data="list" show-overflow-tooltip @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工单编号" prop="woNo" min-width="140" />
      <el-table-column label="批次编码" prop="lotCode" min-width="140" />
      <el-table-column label="物料编码" prop="materialCode" min-width="120" />
      <el-table-column label="产品正式身份" min-width="180"
        ><template #default="{ row }">{{ row.productIdentityId || '历史工单范围' }}</template></el-table-column
      >
      <el-table-column label="消耗数量" prop="consumedQty" align="right" width="100" />
      <el-table-column label="消耗时间" prop="consumedTime" width="160" />
      <el-table-column label="操作员" prop="operatorName" width="100" />
      <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加消耗记录对话框(工单/批次/物料/操作员选择) -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="consumptionRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="工单" prop="woId">
          <el-input v-model="form.woNo" placeholder="请选择工单" readonly>
            <template #append>
              <el-button icon="Search" @click="openWoSelect('form')" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="批次" prop="lotId">
          <el-input v-model="form.lotCode" placeholder="请选择批次" readonly>
            <template #append>
              <el-button icon="Search" @click="openLotDialog('form')" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="物料" prop="materialId">
          <el-input v-model="form.materialCode" placeholder="请选择物料" readonly>
            <template #append>
              <el-button icon="Search" @click="openMaterialSelect('form')" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="消耗数量" prop="consumedQty">
          <el-input v-model="form.consumedQty" placeholder="精确十进制数量" />
        </el-form-item>
        <el-form-item label="操作员">
          <el-input v-model="form.operatorName" placeholder="请选择操作员" readonly>
            <template #append>
              <el-button icon="Search" @click="openUserDialog()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <!-- FEFO 领料对话框(两步: 查询推荐 -> 确认领料, 特殊功能本地保留) -->
    <el-dialog title="FEFO 批次领料" v-model="pickDialog.visible" width="800px" append-to-body>
      <el-form :inline="true" label-width="90px" class="mb8">
        <el-form-item label="工单">
          <el-input v-model="pickDialog.woNo" placeholder="请选择工单" readonly style="width: 180px">
            <template #append>
              <el-button icon="Search" @click="openWoSelect('pick')" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="物料">
          <el-input v-model="pickDialog.materialCode" placeholder="请选择物料" readonly style="width: 180px">
            <template #append>
              <el-button icon="Search" @click="openMaterialSelect('pick')" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="需求数量">
          <el-input v-model="pickDialog.requiredQty" placeholder="十进制字符串" />
        </el-form-item>
        <el-form-item label="SN">
          <el-input v-model="pickDialog.snCode" placeholder="可空,选择SN" readonly style="width: 180px">
            <template #append>
              <el-button icon="Search" @click="openSnDialog()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadPickFefo()">查询推荐</el-button>
        </el-form-item>
      </el-form>
      <CodeResolver
        ref="productResolver"
        scene="MES_COMPONENT_CONSUMPTION"
        :context="{ sourceDocType: 'MES_WORK_ORDER', sourceDocId: identityText(pickDialog.woId) }"
        @resolved="selectProductIdentity"
        @state="productResolveState = $event"
      />
      <p>产品正式身份：{{ productIdentityId || '尚未选择' }}；MES 本地 SN：{{ pickDialog.snId || '未映射' }}；{{ pickDialog.snCode }}</p>
      <el-alert
        v-if="pickPolicy"
        :title="`${pickPolicy.allowMixedLot ? '允许' : '禁止'}同产品 SN 内同组件跨批次消耗 · ${pickPolicy.stage} / ${pickPolicy.version}`"
        type="info"
        :closable="false"
        class="mb-3"
      />
      <el-alert v-if="pickError" :title="pickError" type="warning" :closable="false" class="mb-3" />
      <el-table :data="pickDialog.list" v-loading="pickDialog.loading" show-overflow-tooltip>
        <el-table-column label="批次编码" prop="lotCode" min-width="140" />
        <el-table-column label="物料" prop="materialName" min-width="120" />
        <el-table-column label="剩余数量" prop="remainingQty" align="right" width="100" />
        <el-table-column label="有效期" prop="expiryDate" width="110" />
        <el-table-column label="推荐顺序" type="index" width="80" align="center" />
        <el-table-column label="本次领料数量" width="160" align="center">
          <template #default="scope">
            <el-input v-model="scope.row.pickQty" placeholder="精确数量" size="small" style="width: 140px" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button type="primary" :disabled="!pickDialog.list.length" @click="submitPick()">确认领料</el-button>
        <el-button @click="pickDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 工单选择对话框 -->
    <el-dialog v-model="woDialog.visible" title="选择工单" width="960px" append-to-body>
      <el-form :model="woDialog.query" :inline="true">
        <el-form-item label="工单编号">
          <el-input v-model="woDialog.query.woNo" clearable @keyup.enter="queryWorkOrders()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryWorkOrders()">搜索</el-button>
          <el-button icon="Refresh" @click="resetWoQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="woDialog.list" height="400" @row-dblclick="selectWorkOrder">
        <el-table-column label="工单编号" prop="woNo" min-width="160" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="woStatus" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="prd_wo_status" :value="scope.row.woStatus" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectWorkOrder(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="woDialog.total > 0"
        :total="woDialog.total"
        v-model:page="woDialog.query.pageNum"
        v-model:limit="woDialog.query.pageSize"
        @pagination="queryWorkOrders"
      />
    </el-dialog>

    <!-- 批次选择对话框 -->
    <el-dialog v-model="lotDialog.visible" title="选择批次" width="960px" append-to-body>
      <el-form :model="lotDialog.query" :inline="true">
        <el-form-item label="批次编码">
          <el-input v-model="lotDialog.query.lotCode" clearable @keyup.enter="queryLots()" />
        </el-form-item>
        <el-form-item v-if="lotSelectTarget === 'form' && form.materialId">
          <el-checkbox v-model="lotDialog.filterByMaterial" @change="queryLots()">仅显示当前物料批次</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryLots()">搜索</el-button>
          <el-button icon="Refresh" @click="resetLotQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="lotDialog.list" height="400" @row-dblclick="selectLot">
        <el-table-column label="批次编码" prop="lotCode" min-width="140" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="剩余" prop="remainingQty" align="right" width="90" />
        <el-table-column label="有效期" prop="expiryDate" width="110" />
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="scope">
            <dict-tag :options="mes_lot_status" :value="scope.row.status" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectLot(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="lotDialog.total > 0"
        :total="lotDialog.total"
        v-model:page="lotDialog.query.pageNum"
        v-model:limit="lotDialog.query.pageSize"
        @pagination="queryLots"
      />
    </el-dialog>

    <!-- SN 选择对话框 -->
    <el-dialog v-model="snDialog.visible" title="选择SN" width="960px" append-to-body>
      <el-form :model="snDialog.query" :inline="true">
        <el-form-item label="SN编码">
          <el-input v-model="snDialog.query.snCode" clearable @keyup.enter="querySns()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="querySns()">搜索</el-button>
          <el-button icon="Refresh" @click="resetSnQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="snDialog.list" height="400" @row-dblclick="selectSn">
        <el-table-column label="SN编码" prop="snCode" min-width="160" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="snStatus" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="mes_sn_status" :value="scope.row.snStatus" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectSn(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="snDialog.total > 0"
        :total="snDialog.total"
        v-model:page="snDialog.query.pageNum"
        v-model:limit="snDialog.query.pageSize"
        @pagination="querySns"
      />
    </el-dialog>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />

    <!-- 操作员选择对话框 -->
    <el-dialog v-model="userDialog.visible" title="选择操作员" width="800px" append-to-body>
      <el-form :model="userDialog.query" :inline="true">
        <el-form-item label="用户名">
          <el-input v-model="userDialog.query.userName" clearable @keyup.enter="queryUsers()" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userDialog.query.phonenumber" clearable @keyup.enter="queryUsers()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryUsers()">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="userDialog.list" height="400" @row-dblclick="selectUser">
        <el-table-column label="用户账号" prop="userName" />
        <el-table-column label="用户昵称" prop="nickName" />
        <el-table-column label="手机号" prop="phonenumber" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectUser(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="userDialog.total > 0"
        :total="userDialog.total"
        v-model:page="userDialog.query.pageNum"
        v-model:limit="userDialog.query.pageSize"
        @pagination="queryUsers"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="LtWoConsumption">
import CodeResolver from '@/components/material-trace/CodeResolver.vue';
import { consumptionError, decimalCompare, identityText, materialPolicy, resolvedObject } from '@/components/material-trace/model';
import type { ConsumptionEntry } from '@/components/material-trace/model';
import type { CodeResolution, TrackingPolicy } from '@/api/erp/wm/identity/types';
import { getMaterial } from '@/api/erp/bd/material';

import {
  listConsumption,
  getConsumption,
  addConsumption,
  delConsumption,
  recommendForWo,
  confirmPick,
  listConsumptionByWo
} from '@/api/mes/lt/consumption';
import type { LtWoConsumptionVO, LtWoConsumptionForm, LtWoConsumptionQuery, LtWoPickBO } from '@/api/mes/lt/consumption';
import { listLot } from '@/api/mes/lt/lot';
import type { LtLotVO } from '@/api/mes/lt/lot';
import { listWorkOrder, getWorkOrder } from '@/api/mes/pro/workorder';
import { listSn } from '@/api/mes/pro/sn';
import { listUser } from '@/api/system/user';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_lot_status, mes_sn_status, prd_wo_status } = toRefs<any>(proxy?.useDict('mes_lot_status', 'mes_sn_status', 'prd_wo_status'));

const consumptionRef = ref<FormInstance>();
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();
// 查询条件展示字段(按工单/批次选择器回填)
const queryWoNo = ref('');
const queryLotCode = ref('');

const queryParams = ref<LtWoConsumptionQuery>({
  pageNum: 1,
  pageSize: 10,
  woId: undefined,
  lotId: undefined
});

const rules: ElFormRules = {
  woId: [{ required: true, message: '工单ID不能为空', trigger: 'blur' }],
  lotId: [{ required: true, message: '批次ID不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '物料ID不能为空', trigger: 'blur' }],
  consumedQty: [{ required: true, message: '消耗数量不能为空', trigger: 'blur' }]
};

// 消耗记录仅新增/删除(无修改接口)，update 占位仅为满足 useCrud 的 API 签名，不会被调用
const readonlyApi = () => Promise.resolve();

const {
  loading,
  list,
  total,
  open,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd
} = useCrud({
  apis: { list: listConsumption, get: getConsumption, add: addConsumption, update: readonlyApi, del: delConsumption },
  idField: 'consumptionId',
  entityName: '批次消耗',
  formRef: consumptionRef,
  queryParams,
  initForm: () =>
    ({
      consumptionId: undefined,
      woId: undefined,
      woNo: '',
      lotId: undefined,
      lotCode: '',
      materialId: undefined,
      materialCode: '',
      materialName: '',
      consumedQty: undefined,
      operatorId: undefined,
      operatorName: '',
      remark: ''
    }) as LtWoConsumptionForm
});

function handleAdd() {
  handlePick();
}

function resetQuery() {
  queryWoNo.value = '';
  queryLotCode.value = '';
  queryParams.value.woId = undefined;
  queryParams.value.lotId = undefined;
  handleQuery();
}

/** 删除(不回滚已扣减批次数量) */
const handleDelete = async () => {
  await proxy?.$modal.confirm('确认删除选中消耗记录?(不回滚已扣减数量)');
  await delConsumption(ids.value);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

// ==================== FEFO 领料(两步: 查询推荐 -> 确认领料) ====================
const pickDialog = reactive({
  visible: false,
  woId: undefined as number | undefined,
  woNo: '' as string,
  materialId: undefined as number | undefined,
  materialCode: '' as string,
  requiredQty: undefined as string | undefined,
  snId: undefined as string | number | undefined,
  snCode: '' as string,
  list: [] as (LtLotVO & { pickQty?: string })[],
  loading: false
});

const handlePick = () => {
  pickError.value = '';
  productIdentityId.value = '';
  pickSourceHeader.value = {};
  priorConsumptions.value = [];
  productResolver.value?.discard();
  pickDialog.visible = true;
  pickDialog.list = [];
  pickDialog.woId = undefined;
  pickDialog.woNo = '';
  pickDialog.materialId = undefined;
  pickDialog.materialCode = '';
  pickDialog.requiredQty = undefined;
  pickDialog.snId = undefined;
  pickDialog.snCode = '';
};

const loadPickFefo = async () => {
  if (!pickDialog.woId || !pickDialog.materialId) {
    proxy?.$modal.msgWarning('请选择工单和物料');
    return;
  }
  pickDialog.loading = true;
  try {
    const res = await recommendForWo(pickDialog.woId, pickDialog.materialId, pickDialog.requiredQty);
    pickDialog.list = ((res.data || []) as LtLotVO[]).map((l) => ({ ...l, pickQty: '' }));
    proxy?.$modal.msgSuccess(`推荐 ${pickDialog.list.length} 个批次`);
  } finally {
    pickDialog.loading = false;
  }
};

/** 每次确认只针对一个产品实物，可对多个组件批次分别填写精确数量。 */
const submitPick = async () => {
  pickError.value = '';
  try {
    if (productResolveState.value.pending || productResolveState.value.error)
      throw new Error(productResolveState.value.error || '产品 SN 解析尚未完成');
    if (!productIdentityId.value || !pickDialog.snId || !pickPolicy.value) throw new Error('请选择完整正式产品 SN 并加载组件策略');
    if (typeof pickSourceHeader.value.detailVersion !== 'string' || !pickSourceHeader.value.detailVersion)
      throw new Error('工单缺少服务端详情版本，请重新选择工单');
    const selected = pickDialog.list.filter((lot) => lot.pickQty?.trim());
    const entries: ConsumptionEntry[] = selected.map((lot) => ({
      productIdentityId: productIdentityId.value,
      componentMaterialId: identityText(pickDialog.materialId)!,
      lotId: identityText(lot.lotId)!,
      quantity: lot.pickQty!
    }));
    const error = consumptionError(entries, pickPolicy.value.allowMixedLot, priorConsumptions.value);
    if (error) throw new Error(error);
    for (const lot of selected) {
      if (identityText(lot.materialId) !== identityText(pickDialog.materialId)) throw new Error('批次物料与当前组件不一致');
      if (lot.remainingQty == null || decimalCompare(lot.pickQty!, String(lot.remainingQty)) > 0)
        throw new Error('消耗数量超过批次余额或余额未知，请刷新推荐');
    }
    const payload: LtWoPickBO = {
      woId: identityText(pickDialog.woId)!,
      materialId: identityText(pickDialog.materialId)!,
      snId: identityText(pickDialog.snId),
      productIdentityId: productIdentityId.value,
      identityId: productIdentityId.value,
      detailVersion: pickSourceHeader.value.detailVersion,
      sourceHeader: pickSourceHeader.value.sourceHeader,
      picks: selected.map((lot) => ({ lotId: identityText(lot.lotId)!, consumedQty: lot.pickQty! }))
    };
    await confirmPick(payload);
    proxy?.$modal.msgSuccess('消耗已提交，以服务端返回的实物分录为准');
    pickDialog.visible = false;
    getList();
  } catch (cause) {
    pickError.value = cause instanceof Error ? cause.message : '消耗提交失败';
  }
};

const productResolver = ref<InstanceType<typeof CodeResolver>>();
const productResolveState = ref({ pending: false, error: '' });
const productIdentityId = ref('');
const pickPolicy = ref<TrackingPolicy>();
const pickError = ref('');
const pickSourceHeader = ref<Record<string, any>>({});
const priorConsumptions = ref<ConsumptionEntry[]>([]);

/** 正式身份与 MES 本地身份分别保存，缺少映射时不能静默降级成工单级消耗。 */
function selectProductIdentity(resolution: CodeResolution) {
  try {
    const object = resolvedObject(resolution);
    if (object.objectType !== 'SN' || !object.identityId || !object.localSnId) throw new Error('产品 SN 缺少正式身份或 MES 映射');
    productIdentityId.value = object.identityId;
    pickDialog.snId = object.localSnId;
    pickDialog.snCode = object.canonicalSnCode || object.snCode || '';
    pickError.value = '';
  } catch (cause) {
    productIdentityId.value = '';
    pickDialog.snId = undefined;
    pickError.value = cause instanceof Error ? cause.message : '产品身份不完整';
  }
}

/** 工单详情和历史消耗只读取事实，不用工单级旧记录补造产品身份。 */
watch(
  () => pickDialog.woId,
  async (woId) => {
    pickSourceHeader.value = {};
    priorConsumptions.value = [];
    productIdentityId.value = '';
    pickDialog.snId = undefined;
    if (!woId) return;
    try {
      const [detail, history] = await Promise.all([getWorkOrder(woId), listConsumptionByWo(woId)]);
      if (woId !== pickDialog.woId) return;
      pickSourceHeader.value = detail.data;
      priorConsumptions.value = (history.data || [])
        .filter((entry) => entry.productIdentityId && (entry.componentMaterialId || entry.materialId))
        .map((entry) => ({
          productIdentityId: entry.productIdentityId!,
          componentMaterialId: identityText(entry.componentMaterialId || entry.materialId)!,
          lotId: identityText(entry.lotId),
          quantity: String(entry.quantity ?? entry.consumedQty)
        }));
    } catch (cause) {
      pickError.value = cause instanceof Error ? cause.message : '工单证据读取失败';
    }
  }
);

/** 组件混批策略来自精确物料详情，切换组件立即清空旧候选。 */
watch(
  () => pickDialog.materialId,
  async (materialId) => {
    pickDialog.list = [];
    pickPolicy.value = undefined;
    if (!materialId) return;
    try {
      const response = await getMaterial(materialId);
      if (materialId === pickDialog.materialId) pickPolicy.value = materialPolicy(response.data);
    } catch (cause) {
      pickError.value = cause instanceof Error ? cause.message : '组件策略读取失败';
    }
  }
);

// ==================== 工单选择 ====================
const woSelectTarget = ref<'query' | 'form' | 'pick'>('form');
const woDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, woNo: '' as string }
});
const openWoSelect = (target: 'query' | 'form' | 'pick') => {
  woSelectTarget.value = target;
  woDialog.visible = true;
  queryWorkOrders();
};
const queryWorkOrders = () => {
  listWorkOrder(woDialog.query as any).then((res: any) => {
    woDialog.list = res.rows || [];
    woDialog.total = res.total || 0;
  });
};
const resetWoQuery = () => {
  woDialog.query = { pageNum: 1, pageSize: 10, woNo: '' };
  queryWorkOrders();
};
const selectWorkOrder = (row: any) => {
  const woId = row.mesWoId;
  if (woSelectTarget.value === 'pick') {
    pickDialog.woId = woId;
    pickDialog.woNo = row.woNo;
  } else if (woSelectTarget.value === 'query') {
    queryParams.value.woId = woId;
    queryWoNo.value = row.woNo;
  } else {
    form.value.woId = woId;
    form.value.woNo = row.woNo;
    consumptionRef.value?.validateField('woId');
  }
  woDialog.visible = false;
};

// ==================== 批次选择 ====================
const lotSelectTarget = ref<'query' | 'form'>('form');
const lotDialog = reactive({
  visible: false,
  list: [] as LtLotVO[],
  total: 0,
  filterByMaterial: true,
  query: { pageNum: 1, pageSize: 10, lotCode: '' as string }
});
const openLotDialog = (target: 'query' | 'form' = 'form') => {
  lotSelectTarget.value = target;
  lotDialog.filterByMaterial = target === 'form' && !!form.value.materialId;
  lotDialog.visible = true;
  queryLots();
};
const queryLots = () => {
  const params: any = { ...lotDialog.query };
  if (lotDialog.filterByMaterial && form.value.materialId) {
    params.materialId = form.value.materialId;
  }
  listLot(params).then((res: any) => {
    lotDialog.list = res.rows || [];
    lotDialog.total = res.total || 0;
  });
};
const resetLotQuery = () => {
  lotDialog.query = { pageNum: 1, pageSize: 10, lotCode: '' };
  queryLots();
};
const selectLot = (row: LtLotVO) => {
  if (lotSelectTarget.value === 'query') {
    queryParams.value.lotId = row.lotId;
    queryLotCode.value = row.lotCode;
  } else {
    form.value.lotId = row.lotId;
    form.value.lotCode = row.lotCode;
    // 若物料为空,顺带带出物料(批次冗余字段)
    if (!form.value.materialId && row.materialId) {
      form.value.materialId = row.materialId;
      form.value.materialCode = row.materialCode;
      form.value.materialName = row.materialName;
      consumptionRef.value?.validateField('materialId');
    }
    consumptionRef.value?.validateField('lotId');
  }
  lotDialog.visible = false;
};

// ==================== 物料选择 ====================
const materialSelectTarget = ref<'form' | 'pick'>('form');
const openMaterialSelect = (target: 'form' | 'pick') => {
  materialSelectTarget.value = target;
  materialSelectRef.value?.open();
};
const handleMaterialSelect = (row: any) => {
  if (materialSelectTarget.value === 'pick') {
    pickDialog.materialId = row.materialId;
    pickDialog.materialCode = row.materialCode;
  } else {
    form.value.materialId = row.materialId;
    form.value.materialCode = row.materialCode;
    form.value.materialName = row.materialName;
    consumptionRef.value?.validateField('materialId');
  }
};

// ==================== 操作员选择 ====================
const userDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, userName: '' as string, phonenumber: '' as string }
});
const openUserDialog = () => {
  userDialog.visible = true;
  queryUsers();
};
const queryUsers = () => {
  listUser(userDialog.query as any).then((res: any) => {
    userDialog.list = res.rows || [];
    userDialog.total = res.total || 0;
  });
};
const resetUserQuery = () => {
  userDialog.query = { pageNum: 1, pageSize: 10, userName: '', phonenumber: '' };
  queryUsers();
};
const selectUser = (row: any) => {
  form.value.operatorId = row.userId;
  form.value.operatorName = row.nickName || row.userName;
  userDialog.visible = false;
};

// ==================== SN 选择 ====================
const snDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, snCode: '' as string }
});
const openSnDialog = () => {
  snDialog.visible = true;
  querySns();
};
const querySns = () => {
  listSn(snDialog.query as any).then((res: any) => {
    snDialog.list = res.rows || [];
    snDialog.total = res.total || 0;
  });
};
const resetSnQuery = () => {
  snDialog.query = { pageNum: 1, pageSize: 10, snCode: '' };
  querySns();
};
const selectSn = (row: any) => {
  productResolver.value?.resolve(row.snCode);
  snDialog.visible = false;
};

getList();
</script>
