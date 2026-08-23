<template>
  <div class="app-container">
    <el-form :inline="true" label-width="90px" class="list-search">
      <el-form-item label="会计期间">
        <el-select v-model="periodId" placeholder="请选择期间" filterable style="width: 200px" @change="loadSteps">
          <el-option v-for="p in periodList" :key="p.periodId" :label="p.periodName" :value="p.periodId" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Refresh" :loading="closing" @click="doMonthlyClose">执行月结</el-button>
      </el-form-item>
    </el-form>

    <el-descriptions :title="`期间 ${steps.periodName || ''} 月结向导`" :column="3" border style="margin-bottom: 16px">
      <el-descriptions-item label="期间状态">
        <el-tag :type="periodStatusType" size="small">{{ periodStatusLabel }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="试算校验">
        <el-tag :type="steps.trialChecked ? 'success' : 'info'" size="small">{{ steps.trialChecked ? '已通过' : '未执行' }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="损益结转">
        <el-tag :type="steps.carried ? 'success' : 'info'" size="small">{{
          steps.carried ? `已结转 ${steps.closingVoucherNo || ''}` : '未执行'
        }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="外币重估">
        <el-tag :type="steps.revaluated ? 'success' : 'info'" size="small">{{
          steps.revaluated ? `已重估 ${steps.revaluationVoucherNo || ''}` : '未执行'
        }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="本期凭证数"> {{ steps.voucherCount || 0 }} 张 </el-descriptions-item>
      <el-descriptions-item label="关账">
        <el-tag :type="steps.closed ? 'success' : 'warning'" size="small">{{ steps.closed ? '已关账' : '未关账' }}</el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <el-space wrap style="margin-bottom: 16px">
      <el-button :loading="loading" @click="doTrialCheck">① 试算校验</el-button>
      <el-button :loading="loading" @click="doCarryPnl">② 损益结转</el-button>
      <el-button :loading="loading" @click="doRevaluate">③ 外币重估</el-button>
      <el-button type="warning" :loading="loading" @click="doClose">④ 关账</el-button>
    </el-space>

    <el-table v-loading="loading" :data="steps.logs || []" show-overflow-tooltip>
      <el-table-column label="步骤" align="center" prop="step" width="140" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ scope.row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="消息" align="left" prop="message" min-width="300" />
      <el-table-column label="时间" align="center" prop="createTime" width="170" />
    </el-table>
  </div>
</template>

<script setup lang="ts" name="FinClosingMonthly">
import { listPeriod } from '@/api/erp/fin/gl/period';
import { carryPnl, closingSteps, monthlyClose, revaluate, trialCheck, ClosingStepVO } from '@/api/erp/fin/closing';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const periodList = ref<any[]>([]);
const periodId = ref<number>();
const loading = ref(false);
const closing = ref(false);
const steps = ref<ClosingStepVO>({} as ClosingStepVO);

const periodStatusLabel = computed(() => (({ OPEN: '开放', CLOSED: '已关账', LOCKED: '已锁定' }) as any)[steps.value.periodStatus] || '-');
const periodStatusType = computed(() => (({ OPEN: 'success', CLOSED: 'warning', LOCKED: 'danger' }) as any)[steps.value.periodStatus] || 'info');

function loadPeriods() {
  listPeriod({ pageNum: 1, pageSize: 200 } as any).then((response) => {
    periodList.value = response.rows;
    if (periodList.value.length > 0 && !periodId.value) {
      periodId.value = periodList.value[0].periodId;
      loadSteps();
    }
  });
}

function loadSteps() {
  if (!periodId.value) return;
  loading.value = true;
  closingSteps({ periodId: periodId.value })
    .then((response) => {
      steps.value = response.data;
    })
    .finally(() => {
      loading.value = false;
    });
}

function refresh(msg: string) {
  proxy.$modal.msgSuccess(msg);
  loadSteps();
}

function doTrialCheck() {
  trialCheck({ periodId: periodId.value }).then(() => refresh('试算校验通过'));
}

function doCarryPnl() {
  carryPnl({ periodId: periodId.value }).then(() => refresh('损益结转完成'));
}

function doRevaluate() {
  revaluate({ periodId: periodId.value }).then(() => refresh('外币重估完成'));
}

function doClose() {
  proxy.$modal
    .confirm('确认执行关账?关账后该期间不可再过账。')
    .then(() => monthlyClose({ periodId: periodId.value }))
    .then(() => refresh('月结完成'))
    .catch(() => {});
}

function doMonthlyClose() {
  proxy.$modal
    .confirm('确认整体执行月结(试算校验→损益结转→外币重估→关账)?')
    .then(() => {
      closing.value = true;
      return monthlyClose({ periodId: periodId.value });
    })
    .then(() => {
      proxy.$modal.msgSuccess('月结整体执行完成');
      loadSteps();
    })
    .finally(() => {
      closing.value = false;
    });
}

onMounted(() => {
  loadPeriods();
});
</script>
