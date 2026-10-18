<template>
  <ReportPage
    :title="'发布历史' + (definition?.name ? ' · ' + definition.name : '')"
    description="查看每次发布的固定版本，按需回滚。"
    icon="i-ep-clock"
    class="report-history"
  >
    <template #actions><el-button icon="Back" @click="router.push('/report/center')">返回报表中心</el-button></template>
    <el-card shadow="never">
      <el-alert v-if="loadFailed" title="目录载入失败，请刷新重试。" type="error" show-icon :closable="false" class="mb-4" /><el-alert
        title="每个发布版本独立保存。回滚后，已有运行继续使用创建时的版本。"
        type="info"
        :closable="false"
        class="mb-4"
      /><el-table v-loading="loading" :data="versions" empty-text="此报表还没有发布版本" border
        ><el-table-column label="版本" width="100"
          ><template #default="scope">v{{ scope.row.number }}</template></el-table-column
        ><el-table-column prop="createdAt" label="发布时间" :formatter="formatReportTimeCell" min-width="210" /><el-table-column
          prop="engine"
          label="引擎版本"
          width="150"
        /><el-table-column label="校验值" min-width="160"
          ><template #default="scope">{{ scope.row.checksum.slice(0, 16) }}…</template></el-table-column
        ><el-table-column label="操作" width="200"
          ><template #default="scope"
            ><el-tag v-if="definition?.publishedVersion === scope.row.id" type="success">当前版本</el-tag
            ><el-button
              v-else
              v-hasPermi="['report:definition:publish']"
              link
              type="primary"
              :disabled="!!rollingBack"
              :loading="rollingBack === scope.row.id"
              @click="rollback(scope.row)"
              >回滚至此版本</el-button
            ></template
          ></el-table-column
        ></el-table
      ></el-card
    >
  </ReportPage>
</template>
<script setup lang="ts">
/** 区分资源目录载入失败与当前账号暂无可见资源。 */
const loadFailed = ref(false);
import ReportPage from '@/components/report/ReportPage.vue';
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, watch, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listReports, listVersions, rollbackReport } from '@/api/report';
import type { ReportVersion, ReportDefinition } from '@/types/report';
import { RequestGate } from '@/views/report/model';
import { formatReportTimeCell } from '@/views/report/time';
defineOptions({ name: 'ReportHistory' });
/**
 * 保存当前页面或测试的route状态。
 */
const route = useRoute();
/**
 * 保存当前页面或测试的router状态。
 */
const router = useRouter();
/**
 * 保存当前页面或测试的versions状态。
 */
const versions = ref<ReportVersion[]>([]);
/**
 * 保存当前页面或测试的definition状态。
 */
const definition = ref<ReportDefinition>();
/**
 * 保存当前页面或测试的loading状态。
 */
const loading = ref(false);
/**
 * 保存当前页面或测试的rollingBack状态。
 */
const rollingBack = ref('');
/**
 * 保存当前页面或测试的gate状态。
 */
const gate = new RequestGate();
/** 处理当前报表的 `load` 操作。 */
async function load() {
  const id = String(route.query.id || '');
  const ticket = gate.begin();
  versions.value = [];
  definition.value = undefined;
  rollingBack.value = '';
  loading.value = false;
  if (!id) return;
  loading.value = true;
  loadFailed.value = false;
  try {
    const [a, b] = await Promise.all([listReports(), listVersions(id)]);
    if (gate.current(ticket)) {
      definition.value = a.data.find((r) => r.id === id);
      versions.value = b.data;
    }
  } catch {
    if (gate.current(ticket)) {
      loadFailed.value = true;
      versions.value = [];
    }
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 处理当前报表的 `rollback` 操作。 */
async function rollback(version: ReportVersion) {
  if (!definition.value || rollingBack.value) return;
  const target = definition.value;
  const ticket = gate.begin();
  rollingBack.value = version.id;
  try {
    await ElMessageBox.confirm('将当前发布指针切换到 v' + version.number + '？', '回滚版本');
    if (!gate.current(ticket)) return;
    await rollbackReport(target.id, version.id, target.revision);
    if (!gate.current(ticket)) return;
    ElMessage.success('已回滚');
    await load();
  } catch {
  } finally {
    if (gate.current(ticket)) rollingBack.value = '';
  }
}
watch(() => route.query.id, load, { immediate: true, flush: 'sync' });
onUnmounted(() => gate.invalidate());
</script>
