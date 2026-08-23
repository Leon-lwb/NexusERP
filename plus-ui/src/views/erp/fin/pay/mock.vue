<template>
  <div class="app-container">
    <el-alert
      title="模拟支付渠道收款回调:发送 PayReceivedMessage → PayReceivedConsumer 自动建 AR 收款单 → GL 过账 → FIFO 自动核销该客户未清发票。解决 pay 模块缺失导致的自动收款断链。"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />

    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>模拟收款回调</template>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
            <el-form-item label="客户" prop="clientId">
              <el-select v-model="form.clientId" filterable placeholder="选择收款客户" style="width: 100%" @change="loadInvoices">
                <el-option v-for="c in clientOptions" :key="c.clientId" :label="`${c.clientCode} ${c.clientName}`" :value="c.clientId" />
              </el-select>
            </el-form-item>
            <el-form-item label="收款金额" prop="amount">
              <el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item label="支付渠道" prop="payType">
              <el-select v-model="form.payType" style="width: 100%">
                <el-option label="支付宝" value="alipay" />
                <el-option label="微信" value="wechat" />
                <el-option label="其他" value="other" />
              </el-select>
            </el-form-item>
            <el-form-item label="支付单号" prop="payOrderNumber">
              <el-input v-model="form.payOrderNumber" placeholder="留空自动生成" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="sending" @click="submitForm">发送收款回调</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            该客户未核销发票(ISSUED)
            <el-tag v-if="openInvoices.length" type="warning" size="small" style="margin-left: 8px"> 未核销合计: {{ fmt(openTotal) }} </el-tag>
          </template>
          <el-table v-loading="loadingInvoices" :data="openInvoices" border size="small" max-height="400">
            <el-table-column label="发票号" align="center" prop="invoiceNo" min-width="140" />
            <el-table-column label="发票日期" align="center" prop="invoiceDate" width="100" />
            <el-table-column label="金额" align="right" width="110">
              <template #default="scope">{{ fmt(scope.row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column label="已核销" align="right" width="100">
              <template #default="scope">{{ fmt(scope.row.verifiedAmount) }}</template>
            </el-table-column>
            <el-table-column label="未核销" align="right" width="100">
              <template #default="scope">{{ fmt(Number(scope.row.totalAmount) - Number(scope.row.verifiedAmount)) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!openInvoices.length" description="该客户无未核销发票" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="FinPayMock">
import { payMockCallback, listOpenInvoices } from '@/api/erp/fin/pay';
import { listClient } from '@/api/erp/bd/client';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const clientOptions = ref<any[]>([]);
const openInvoices = ref<any[]>([]);
const loadingInvoices = ref(false);
const sending = ref(false);
const formRef = ref();

const form = ref({
  clientId: undefined,
  amount: 0,
  payType: 'alipay',
  payOrderNumber: ''
});

const rules = {
  clientId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  amount: [{ required: true, message: '收款金额不能为空', trigger: 'blur' }]
};

const openTotal = computed(() => openInvoices.value.reduce((s, x) => s + Number(x.totalAmount) - Number(x.verifiedAmount), 0));

function fmt(v: number | undefined | null) {
  return Number(v || 0).toFixed(2);
}

function loadClients() {
  listClient({ pageNum: 1, pageSize: 200 } as any).then((response) => {
    clientOptions.value = response.rows || [];
  });
}

function loadInvoices(clientId: number) {
  if (!clientId) return;
  loadingInvoices.value = true;
  listOpenInvoices({ clientId, status: 'ISSUED' } as any)
    .then((response) => {
      openInvoices.value = response.rows || [];
    })
    .finally(() => {
      loadingInvoices.value = false;
    });
}

function submitForm() {
  formRef.value.validate((valid: boolean) => {
    if (!valid) return;
    sending.value = true;
    const data = {
      ...form.value,
      payOrderNumber: form.value.payOrderNumber || `MOCK${Date.now()}`,
      thirdPartyNo: `TP${Date.now()}`
    };
    payMockCallback(data)
      .then(() => {
        proxy.$modal.msgSuccess('收款回调已发送,自动收款/核销将在后台执行');
        setTimeout(() => loadInvoices(form.value.clientId), 1500);
      })
      .finally(() => {
        sending.value = false;
      })
      .catch(() => {});
  });
}

onMounted(() => {
  loadClients();
});
</script>
