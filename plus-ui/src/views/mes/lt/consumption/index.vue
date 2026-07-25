<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
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
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:lt:consumption:add']">新增消耗记录</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="MagicStick" @click="handlePick" v-hasPermi="['mes:lt:lot:fefo']">FEFO 领料</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="!ids.length" @click="handleDelete" v-hasPermi="['mes:lt:consumption:remove']"
            >删除</el-button
          >
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="工单编号" prop="woNo" min-width="140" />
        <el-table-column label="批次编码" prop="lotCode" min-width="140" />
        <el-table-column label="物料编码" prop="materialCode" min-width="120" />
        <el-table-column label="消耗数量" prop="consumedQty" align="right" width="100" />
        <el-table-column label="消耗时间" prop="consumedTime" width="160" />
        <el-table-column label="操作员" prop="operatorName" width="100" />
        <el-table-column label="备注" prop="remark" show-overflow-tooltip />
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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
          <el-input-number v-model="form.consumedQty" :min="0" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="操作员">
          <el-input v-model="form.operatorName" placeholder="请选择操作员" readonly>
            <template #append>
              <el-button icon="Search" @click="openUserDialog" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="dialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- FEFO 领料对话框 -->
    <el-dialog title="FEFO 批次领料" v-model="pickDialog.visible" width="860px" append-to-body>
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
          <el-input-number v-model="pickDialog.requiredQty" :min="0" :precision="4" controls-position="right" />
        </el-form-item>
        <el-form-item label="SN">
          <el-input v-model="pickDialog.snCode" placeholder="可空,选择SN" readonly style="width: 180px">
            <template #append>
              <el-button icon="Search" @click="openSnDialog" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadPickFefo">查询推荐</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="pickDialog.list" v-loading="pickDialog.loading" show-overflow-tooltip>
        <el-table-column label="批次编码" prop="lotCode" min-width="140" />
        <el-table-column label="物料" prop="materialName" min-width="120" />
        <el-table-column label="剩余数量" prop="remainingQty" align="right" width="100" />
        <el-table-column label="有效期" prop="expiryDate" width="110" />
        <el-table-column label="推荐顺序" type="index" width="80" align="center" />
        <el-table-column label="本次领料数量" width="160" align="center">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.pickQty"
              :min="0"
              :precision="4"
              :max="scope.row.remainingQty"
              controls-position="right"
              size="small"
              style="width: 140px"
            />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button type="primary" :disabled="!pickDialog.list.length" @click="submitPick">确认领料</el-button>
        <el-button @click="pickDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 工单选择对话框 -->
    <el-dialog v-model="woDialog.visible" title="选择工单" width="950px" append-to-body>
      <el-form :model="woDialog.query" :inline="true">
        <el-form-item label="工单编号">
          <el-input v-model="woDialog.query.woNo" clearable @keyup.enter="queryWorkOrders" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryWorkOrders">搜索</el-button>
          <el-button icon="Refresh" @click="resetWoQuery">重置</el-button>
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
    <el-dialog v-model="lotDialog.visible" title="选择批次" width="900px" append-to-body>
      <el-form :model="lotDialog.query" :inline="true">
        <el-form-item label="批次编码">
          <el-input v-model="lotDialog.query.lotCode" clearable @keyup.enter="queryLots" />
        </el-form-item>
        <el-form-item v-if="lotSelectTarget === 'form' && form.materialId">
          <el-checkbox v-model="lotDialog.filterByMaterial" @change="queryLots">仅显示当前物料批次</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryLots">搜索</el-button>
          <el-button icon="Refresh" @click="resetLotQuery">重置</el-button>
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
    <el-dialog v-model="snDialog.visible" title="选择SN" width="900px" append-to-body>
      <el-form :model="snDialog.query" :inline="true">
        <el-form-item label="SN编码">
          <el-input v-model="snDialog.query.snCode" clearable @keyup.enter="querySns" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="querySns">搜索</el-button>
          <el-button icon="Refresh" @click="resetSnQuery">重置</el-button>
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
          <el-input v-model="userDialog.query.userName" clearable @keyup.enter="queryUsers" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userDialog.query.phonenumber" clearable @keyup.enter="queryUsers" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryUsers">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery">重置</el-button>
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
import { listConsumption, delConsumption, addConsumption, recommendForWo, confirmPick } from '@/api/mes/lt/consumption';
import type { LtWoConsumptionVO, LtWoConsumptionForm, LtWoConsumptionQuery, LtWoPickBO } from '@/api/mes/lt/consumption';
import { listLot } from '@/api/mes/lt/lot';
import type { LtLotVO } from '@/api/mes/lt/lot';
import { listWorkOrder } from '@/api/mes/pro/workorder';
import { listSn } from '@/api/mes/pro/sn';
import { listUser } from '@/api/system/user';
import MaterialSelectDialog from '@/components/MaterialSelectDialog';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_lot_status, mes_sn_status, prd_wo_status } = toRefs<any>(proxy?.useDict('mes_lot_status', 'mes_sn_status', 'prd_wo_status'));
const dataList = ref<LtWoConsumptionVO[]>([]);
const total = ref(0);
const loading = ref(true);
const ids = ref<number[]>([]);
const queryParams = reactive<LtWoConsumptionQuery>({ pageNum: 1, pageSize: 10 } as any);
const queryWoNo = ref('');
const queryLotCode = ref('');
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();
const dialog = reactive({ visible: false, title: '' });
const form = reactive<LtWoConsumptionForm>({ woId: 0, lotId: 0, materialId: 0, consumedQty: 0 } as LtWoConsumptionForm);
const pickDialog = reactive({
  visible: false,
  woId: undefined as number | undefined,
  woNo: '' as string,
  materialId: undefined as number | undefined,
  materialCode: '' as string,
  requiredQty: undefined as number | undefined,
  snId: undefined as number | undefined,
  snCode: '' as string,
  list: [] as (LtLotVO & { pickQty?: number })[],
  loading: false
});
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();
const materialSelectTarget = ref<'form' | 'pick'>('form');
const woSelectTarget = ref<'query' | 'form' | 'pick'>('form');
const lotSelectTarget = ref<'query' | 'form'>('form');
const snDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, snCode: '' as string }
});
const woDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, woNo: '' as string }
});
const lotDialog = reactive({
  visible: false,
  list: [] as LtLotVO[],
  total: 0,
  filterByMaterial: true,
  query: { pageNum: 1, pageSize: 10, lotCode: '' as string }
});
const userDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, userName: '' as string, phonenumber: '' as string }
});
const rules: ElFormRules = {
  woId: [{ required: true, message: '工单ID不能为空', trigger: 'blur' }],
  lotId: [{ required: true, message: '批次ID不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '物料ID不能为空', trigger: 'blur' }],
  consumedQty: [{ required: true, message: '消耗数量不能为空', trigger: 'blur' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listConsumption(queryParams);
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
  queryWoNo.value = '';
  queryLotCode.value = '';
  queryParams.woId = undefined;
  queryParams.lotId = undefined;
  handleQuery();
};
const handleSelectionChange = (selection: LtWoConsumptionVO[]) => {
  ids.value = selection.map((i) => i.consumptionId);
};

const handleAdd = () => {
  Object.assign(form, {
    consumptionId: undefined,
    woId: 0,
    woNo: '',
    lotId: 0,
    lotCode: '',
    materialId: 0,
    materialCode: '',
    materialName: '',
    consumedQty: 0,
    operatorId: undefined,
    operatorName: '',
    remark: ''
  });
  formRef.value?.resetFields();
  dialog.title = '新增批次消耗';
  dialog.visible = true;
};

const submitForm = () => {
  formRef.value?.validate(async (valid) => {
    if (!valid) return;
    await addConsumption(form);
    proxy!.$modal.msgSuccess('新增成功,已联动扣减批次数量');
    dialog.visible = false;
    getList();
  });
};

const handleDelete = async () => {
  await proxy!.$modal.confirm('确认删除选中消耗记录?(不回滚已扣减数量)');
  await delConsumption(ids.value);
  proxy!.$modal.msgSuccess('删除成功');
  getList();
};

// FEFO 领料: 两步(查询推荐 -> 确认领料)
const handlePick = () => {
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
    proxy!.$modal.msgWarning('请选择工单和物料');
    return;
  }
  pickDialog.loading = true;
  try {
    const res = await recommendForWo(pickDialog.woId, pickDialog.materialId, pickDialog.requiredQty);
    pickDialog.list = ((res.data || []) as LtLotVO[]).map((l) => ({ ...l, pickQty: 0 }));
    proxy!.$modal.msgSuccess(`推荐 ${pickDialog.list.length} 个批次`);
  } finally {
    pickDialog.loading = false;
  }
};

const submitPick = async () => {
  const picks = pickDialog.list.filter((l) => l.pickQty && l.pickQty > 0).map((l) => ({ lotId: l.lotId, consumedQty: l.pickQty! }));
  if (!picks.length) {
    proxy!.$modal.msgWarning('请填写至少一条领料数量');
    return;
  }
  const payload: LtWoPickBO = {
    woId: pickDialog.woId!,
    materialId: pickDialog.materialId!,
    snId: pickDialog.snId,
    picks
  };
  await confirmPick(payload);
  proxy!.$modal.msgSuccess('领料成功,已联动扣减批次数量');
  pickDialog.visible = false;
  getList();
};

// ============ 工单选择 ============
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
    queryParams.woId = woId;
    queryWoNo.value = row.woNo;
  } else {
    form.woId = woId;
    form.woNo = row.woNo;
    formRef.value?.validateField('woId');
  }
  woDialog.visible = false;
};

// ============ 批次选择 ============
const openLotDialog = (target: 'query' | 'form' = 'form') => {
  lotSelectTarget.value = target;
  lotDialog.filterByMaterial = target === 'form' && !!form.materialId;
  lotDialog.visible = true;
  queryLots();
};
const queryLots = () => {
  const params: any = { ...lotDialog.query };
  if (lotDialog.filterByMaterial && form.materialId) {
    params.materialId = form.materialId;
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
    queryParams.lotId = row.lotId;
    queryLotCode.value = row.lotCode;
  } else {
    form.lotId = row.lotId;
    form.lotCode = row.lotCode;
    // 若物料为空,顺带带出物料(批次冗余字段)
    if (!form.materialId && row.materialId) {
      form.materialId = row.materialId;
      form.materialCode = row.materialCode;
      form.materialName = row.materialName;
      formRef.value?.validateField('materialId');
    }
    formRef.value?.validateField('lotId');
  }
  lotDialog.visible = false;
};

// ============ 物料选择 ============
const openMaterialSelect = (target: 'form' | 'pick') => {
  materialSelectTarget.value = target;
  materialSelectRef.value?.open();
};
const handleMaterialSelect = (row: any) => {
  if (materialSelectTarget.value === 'pick') {
    pickDialog.materialId = row.materialId;
    pickDialog.materialCode = row.materialCode;
  } else {
    form.materialId = row.materialId;
    form.materialCode = row.materialCode;
    form.materialName = row.materialName;
    formRef.value?.validateField('materialId');
  }
};

// ============ 操作员选择 ============
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
  form.operatorId = row.userId;
  form.operatorName = row.nickName || row.userName;
  userDialog.visible = false;
};

// ============ SN 选择 ============
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
  pickDialog.snId = row.snId;
  pickDialog.snCode = row.snCode;
  snDialog.visible = false;
};

onMounted(() => getList());
</script>
