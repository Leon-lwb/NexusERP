<template>
  <div class="p-4">
    <el-card v-loading="loading"
      ><template #header
        ><div class="flex justify-between"><strong>我的报表交付</strong><el-button @click="load">刷新</el-button></div></template
      ><el-alert
        title="每份文件按你自己的当前权限生成和领取。订阅或报告停用、账户或权限变化、过期都会阻止领取。"
        type="info"
        :closable="false"
      /><el-table :data="deliveries" class="mt-4"
        ><el-table-column prop="subscriptionId" label="订阅" /><el-table-column prop="state" label="状态" /><el-table-column
          prop="notification"
          label="通知状态"
        /><el-table-column prop="expires" label="到期时刻" /><el-table-column prop="error" label="结果码" /><el-table-column label="领取"
          ><template #default="scope"
            ><el-button
              type="primary"
              link
              :disabled="!scope.row.jobId || !['READY', 'CLAIMED'].includes(scope.row.state) || busy"
              @click="download(scope.row)"
              >鉴权领取文件</el-button
            ></template
          ></el-table-column
        ></el-table
      ></el-card
    >
  </div>
</template>
<script setup lang="ts">
/** 私有交付页面不接收外链、匿名令牌或生成者文件。 */
import { ref, onMounted, onUnmounted } from 'vue';
import { saveAs } from 'file-saver';
import { listReportDeliveries, claimReportDelivery, type ReportDelivery } from '@/api/report/subscription';
import { downloadExport } from '@/api/report';
import { RequestGate } from '@/views/report/model';
/**
 * 保存当前页面或测试的deliveries、loading、busy状态。
 */
const deliveries = ref<ReportDelivery[]>([]),
  loading = ref(false),
  busy = ref(false);
/**
 * 保存当前页面或测试的gate、downloadGate状态。
 */
const gate = new RequestGate(),
  downloadGate = new RequestGate();
/** 当前用户权限变化后刷新目录会隐藏失效入口。 */
async function load() {
  const ticket = gate.begin();
  loading.value = true;
  try {
    const response = await listReportDeliveries();
    if (gate.current(ticket)) deliveries.value = response.data;
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 领取和下载分别实时鉴权，迟到下载不能触发新页面保存。 */
async function download(delivery: ReportDelivery) {
  if (busy.value) return;
  const ticket = downloadGate.begin();
  busy.value = true;
  try {
    const claimed = await claimReportDelivery(delivery.id);
    if (!downloadGate.current(ticket)) return;
    const file = await downloadExport(claimed.data);
    if (downloadGate.current(ticket)) {
      const extension = file.type.includes('pdf') ? 'pdf' : 'xlsx';
      saveAs(file, '订阅报表-' + delivery.id + '.' + extension);
      await load();
    }
  } finally {
    if (downloadGate.current(ticket)) busy.value = false;
  }
}
onMounted(load);
onUnmounted(() => {
  gate.invalidate();
  downloadGate.invalidate();
});
</script>
