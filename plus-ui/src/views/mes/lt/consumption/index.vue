<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="工单ID" prop="woId">
              <el-input-number v-model="queryParams.woId" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="批次ID" prop="lotId">
              <el-input-number v-model="queryParams.lotId" :min="1" controls-position="right" style="width: 100%" />
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

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="工单ID" prop="woId"
          ><el-input-number v-model="form.woId" :min="1" controls-position="right" style="width: 100%"
        /></el-form-item>
        <el-form-item label="批次ID" prop="lotId"
          ><el-input-number v-model="form.lotId" :min="1" controls-position="right" style="width: 100%"
        /></el-form-item>
        <el-form-item label="物料ID" prop="materialId"
          ><el-input-number v-model="form.materialId" :min="1" controls-position="right" style="width: 100%"
        /></el-form-item>
        <el-form-item label="消耗数量" prop="consumedQty"
          ><el-input-number v-model="form.consumedQty" :min="0" :precision="4" controls-position="right" style="width: 100%"
        /></el-form-item>
        <el-form-item label="操作员ID"
          ><el-input-number v-model="form.operatorId" :min="1" controls-position="right" style="width: 100%"
        /></el-form-item>
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
        <el-form-item label="工单ID">
          <el-input-number v-model="pickDialog.woId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="物料ID">
          <el-input-number v-model="pickDialog.materialId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="需求数量">
          <el-input-number v-model="pickDialog.requiredQty" :min="0" :precision="4" controls-position="right" />
        </el-form-item>
        <el-form-item label="SN ID">
          <el-input-number v-model="pickDialog.snId" :min="1" controls-position="right" placeholder="可空" />
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
  </div>
</template>

<script setup lang="ts" name="LtWoConsumption">
import { listConsumption, delConsumption, addConsumption, recommendForWo, confirmPick } from '@/api/mes/lt/consumption';
import type { LtWoConsumptionVO, LtWoConsumptionForm, LtWoConsumptionQuery, LtWoPickBO } from '@/api/mes/lt/consumption';
import type { LtLotVO } from '@/api/mes/lt/lot';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const dataList = ref<LtWoConsumptionVO[]>([]);
const total = ref(0);
const loading = ref(true);
const ids = ref<number[]>([]);
const queryParams = reactive<LtWoConsumptionQuery>({ pageNum: 1, pageSize: 10 } as any);
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();
const dialog = reactive({ visible: false, title: '' });
const form = reactive<LtWoConsumptionForm>({ woId: 0, lotId: 0, materialId: 0, consumedQty: 0 } as LtWoConsumptionForm);
const pickDialog = reactive({
  visible: false,
  woId: undefined as number | undefined,
  materialId: undefined as number | undefined,
  requiredQty: undefined as number | undefined,
  snId: undefined as number | undefined,
  list: [] as (LtLotVO & { pickQty?: number })[],
  loading: false
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
  proxy!.resetForm('queryFormRef');
  handleQuery();
};
const handleSelectionChange = (selection: LtWoConsumptionVO[]) => {
  ids.value = selection.map((i) => i.consumptionId);
};

const handleAdd = () => {
  Object.assign(form, { consumptionId: undefined, woId: 0, lotId: 0, materialId: 0, consumedQty: 0, operatorId: undefined, remark: '' });
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
  pickDialog.materialId = undefined;
  pickDialog.requiredQty = undefined;
  pickDialog.snId = undefined;
};

const loadPickFefo = async () => {
  if (!pickDialog.woId || !pickDialog.materialId) {
    proxy!.$modal.msgWarning('请填写工单ID和物料ID');
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

onMounted(() => getList());
</script>
