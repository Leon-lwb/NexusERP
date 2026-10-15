<template>
  <ReportPage title="报表订阅" description="设置固定报表、接收人和执行计划，跟踪每次交付。" icon="i-ep-alarm-clock" class="report-subscription">
    <template #actions
      ><el-button :loading="loading" icon="Refresh" @click="load">刷新</el-button
      ><el-button v-hasPermi="['report:subscription:manage']" :disabled="busy" type="primary" icon="Plus" @click="newSubscription"
        >新建订阅</el-button
      ></template
    >
    <el-card shadow="never" v-loading="loading">
      <el-alert v-if="loadFailed" title="目录载入失败，请刷新重试。" type="error" show-icon :closable="false" class="mb-4" /><el-alert
        :title="
          '自动调度：' +
          (capabilities.schedulingEnabled ? '已启用' : '未启用，可使用手动运行') +
          '；真实站内通知：' +
          (capabilities.notificationsEnabled ? '已启用' : '关闭')
        "
        type="info"
        :closable="false"
      />
      <el-form v-if="model" :disabled="busy" label-width="120px" class="mt-4 report-editor-form"
        ><el-form-item label="编码与标题"
          ><el-input v-model="code" :disabled="!!resource" placeholder="订阅编码" /><el-input
            v-model="title"
            class="ml-3"
            placeholder="名称" /></el-form-item
        ><el-form-item label="固定报告"
          ><el-select :model-value="model.reportVersionId" class="w-full" @change="(value) => chooseReport(String(value))"
            ><el-option
              v-for="report in reports"
              :key="report.id"
              :value="report.publishedVersion!"
              :label="report.name + ' / ' + report.publishedVersion" /><el-option
              v-if="model.reportVersionId && !reports.some((report) => report.publishedVersion === model.reportVersionId)"
              :value="model.reportVersionId"
              :label="'历史固定版本 / ' + model.reportVersionId" /></el-select></el-form-item
        ><el-form-item label="交付格式"
          ><el-select v-model="model.format"
            ><el-option label="PDF" value="PDF" /><el-option label="数据XLSX" value="XLSX_DATA" /><el-option
              label="样式XLSX"
              value="XLSX_LAYOUT" /></el-select></el-form-item
        ><el-form-item label="接收人ID"><el-input v-model="recipientText" placeholder="以逗号分隔完整字符串ID，最多20人" /></el-form-item
        ><el-form-item label="cron表达式"
          ><el-input v-model="model.cron" placeholder="秒 分 时 日 月 周" /><span class="ml-3">每次间隔至少五分钟</span></el-form-item
        ><el-form-item label="显式时区"
          ><el-select v-model="model.timezone" filterable allow-create
            ><el-option v-for="zone in zones" :key="zone" :value="zone" :label="zone" /></el-select></el-form-item
        ><el-form-item label="夏令时策略"><span>缺失时刻跳过；重复时刻使用较早偏移执行一次</span></el-form-item
        ><el-form-item label="补跑窗口"
          ><el-input-number v-model="model.catchupSeconds" :min="0" :max="86400" :step="3600" /><span class="ml-3"
            >秒；相对日期固定在原计划时刻</span
          ></el-form-item
        ><el-form-item label="站内摘要通知"
          ><el-switch v-model="model.notifyOnReady" :disabled="!capabilities.notificationsEnabled" /><span class="ml-3"
            >只提醒登录后领取，不发送文件或业务明细</span
          ></el-form-item
        >
        <el-form-item v-for="parameter in sourceParameters" :key="parameter.name" :label="parameter.label + ' / ' + parameter.type"
          ><el-select :model-value="model.parameterRules[parameter.name]?.kind || 'DEFAULT'" @change="(value) => rule(parameter.name, String(value))"
            ><el-option label="固定报告默认值" value="DEFAULT" /><el-option label="固定值" value="FIXED" /><el-option
              v-if="parameter.type === 'DATE'"
              label="按计划时区的相对日期"
              value="DATE_RELATIVE" /><el-option v-if="parameter.type === 'ID'" label="接收人自身ID" value="RECIPIENT_ID" /></el-select
          ><el-select
            v-if="model.parameterRules[parameter.name]?.kind === 'DATE_RELATIVE'"
            v-model="model.parameterRules[parameter.name].value"
            class="ml-3"
            ><el-option v-for="value in dates" :key="value" :value="value" :label="value" /></el-select
          ><el-input
            v-else-if="model.parameterRules[parameter.name]?.kind === 'FIXED'"
            v-model="model.parameterRules[parameter.name].value"
            placeholder="精确字符串值"
            class="ml-3" /></el-form-item
        ><el-form-item
          ><el-button type="primary" :loading="busy" @click="save">保存草稿</el-button
          ><el-button v-hasPermi="['report:subscription:publish']" :disabled="!resource || dirty" @click="publish"
            >发布固定订阅</el-button
          ></el-form-item
        >
      </el-form>
      <div class="report-section-heading mt-5">
        <div>
          <h2>订阅目录</h2>
          <p class="report-section-description">共 {{ subscriptions.length }} 个可见订阅，执行记录按接收人分别展示。</p>
        </div>
        <el-tag v-if="model && dirty" type="warning" effect="plain">草稿尚未保存</el-tag>
      </div>
      <el-table :data="subscriptions" empty-text="暂无订阅，点击新建订阅设置交付计划" class="mt-4"
        ><el-table-column prop="title" label="名称" /><el-table-column prop="code" label="编码" /><el-table-column label="状态" width="120"
          ><template #default="scope"><ReportStatus :status="scope.row.status" /></template></el-table-column
        ><el-table-column label="操作" min-width="290"
          ><template #default="scope"
            ><el-button v-hasPermi="['report:acl:manage']" link :disabled="busy" @click="aclId = scope.row.id">资源授权</el-button
            ><el-button v-hasPermi="['report:subscription:manage']" link @click="edit(scope.row.id)">编辑</el-button
            ><el-button link @click="results(scope.row.id)">执行记录</el-button
            ><el-button
              v-hasPermi="['report:subscription:manage']"
              link
              :disabled="scope.row.status !== 'PUBLISHED' || busy"
              @click="run(scope.row.id)"
              >手动运行</el-button
            ><el-button v-hasPermi="['report:subscription:publish']" link :disabled="busy" @click="state(scope.row)">{{
              scope.row.status === 'OFFLINE' ? '恢复' : '停用'
            }}</el-button></template
          ></el-table-column
        ></el-table
      ><el-alert
        v-if="schedule"
        :title="'下一执行时刻 ' + schedule.nextAt + '；' + (schedule.enabled ? '可调度' : '已停用') + (schedule.error ? '；' + schedule.error : '')"
        :type="schedule.enabled ? 'info' : 'warning'"
        :closable="false"
        class="mt-3"
      />
      <div v-if="selected" class="report-section-heading mt-5">
        <h2>执行记录</h2>
        <span class="report-secondary">{{ selected }}</span>
      </div>
      <el-table v-if="selected" :data="executions" empty-text="当前订阅还没有执行记录"
        ><el-table-column prop="recipient" label="接收人" /><el-table-column prop="scheduledAt" label="固定时刻" /><el-table-column
          prop="eventType"
          label="触发类型"
        /><el-table-column label="状态" width="120"
          ><template #default="scope"><ReportStatus :status="scope.row.state" /></template></el-table-column
        ><el-table-column prop="error" label="结果码" /><el-table-column label="操作"
          ><template #default="scope"
            ><el-button v-hasPermi="['report:subscription:manage']" link :disabled="busy" @click="retry(scope.row.id)">重试</el-button
            ><el-button v-hasPermi="['report:subscription:manage']" link :disabled="busy" @click="cancel(scope.row.id)">取消</el-button></template
          ></el-table-column
        ></el-table
      ></el-card
    >
  </ReportPage>
  <AclDialog
    :open="!!aclId"
    resource-type="SUBSCRIPTION"
    :resource-id="aclId"
    @update:open="
      (value) => {
        if (!value) aclId = '';
      }
    "
  />
</template>
<script setup lang="ts">
import { createRequestKey } from '@/utils/requestKey';
/** 区分资源目录载入失败与当前账号暂无可见资源。 */
const loadFailed = ref(false);
import ReportPage from '@/components/report/ReportPage.vue';
import ReportStatus from '@/components/report/ReportStatus.vue';
import AclDialog from '@/components/report/AclDialog.vue';
/** 当前弹窗绑定的资源ID，关闭或切换时由弹窗请求围栏保护。 */
const aclId = ref('');
/** 原生订阅设计器，版本、精确参数和执行记录分开管理。 */
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listReports } from '@/api/report';
import {
  listSubscriptions,
  subscriptionDetail,
  createSubscription,
  saveSubscription,
  publishSubscription,
  runSubscription,
  subscriptionState,
  subscriptionExecutions,
  subscriptionSchedule,
  retrySubscriptionExecution,
  cancelSubscriptionExecution,
  subscriptionCapabilities,
  subscriptionSource,
  type SubscriptionDefinition,
  type SubscriptionExecution,
  type SubscriptionSchedule
} from '@/api/report/subscription';
import type { ReportDefinition, ReportParameter } from '@/types/report';
import type { ReportResource } from '@/api/report/query';
import { RequestGate } from '@/views/report/model';
/**
 * 保存当前页面或测试的reports、subscriptions、resource、model、sourceParameters、capabilities、executions、schedule、selected、loading、busy、code、title、recipientText、baseline状态。
 */
const reports = ref<ReportDefinition[]>([]),
  subscriptions = ref<ReportResource[]>([]),
  resource = ref<ReportResource>(),
  model = ref<SubscriptionDefinition>(),
  sourceParameters = ref<ReportParameter[]>([]),
  capabilities = ref({ schedulingEnabled: false, notificationsEnabled: false, minSeconds: 300, maxRecipients: 20 }),
  executions = ref<SubscriptionExecution[]>([]),
  schedule = ref<SubscriptionSchedule | null>(null),
  selected = ref(''),
  loading = ref(false),
  busy = ref(false),
  code = ref(''),
  title = ref(''),
  recipientText = ref(''),
  baseline = ref('');
/**
 * 保存当前页面或测试的gate、editorGate、resultGate、switchGate状态。
 */
const gate = new RequestGate(),
  editorGate = new RequestGate(),
  resultGate = new RequestGate(),
  switchGate = new RequestGate();
/**
 * 保存当前页面或测试的zones、dates状态。
 */
const zones = ['Asia/Shanghai', 'UTC', 'Europe/Berlin', 'America/New_York'],
  dates = ['TODAY', 'YESTERDAY', 'MONTH_START', 'PREVIOUS_MONTH_START', 'PREVIOUS_MONTH_END'];
/**
 * 保存当前页面或测试的keys状态。
 */
const keys = new Map<string, string>();
/**
 * 保存当前页面或测试的dirty状态。
 */
const dirty = computed(
  () =>
    !!model.value &&
    JSON.stringify({ code: code.value, title: title.value, recipientText: recipientText.value, model: model.value }) !== baseline.value
);
/** 刷新目录及实际开关，报告目录只在需要编辑时获取。 */
async function load() {
  const ticket = gate.begin();
  loading.value = true;
  loadFailed.value = false;
  try {
    const [a, b] = await Promise.all([listSubscriptions(), subscriptionCapabilities()]);
    if (gate.current(ticket)) {
      subscriptions.value = a.data;
      capabilities.value = b.data;
    }
  } catch {
    if (gate.current(ticket)) {
      loadFailed.value = true;
      subscriptions.value = [];
    }
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 会话切换前保护未保存设计。 */
async function maySwitch() {
  const ticket = switchGate.begin();
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('有未保存的订阅设计，确认切换？', '切换订阅');
    } catch {
      return false;
    }
  }
  return switchGate.current(ticket);
}
/** 新建默认关闭真实通知的草稿。 */
async function newSubscription() {
  if (!(await maySwitch())) return;
  const ticket = editorGate.begin();
  resource.value = undefined;
  code.value = title.value = recipientText.value = '';
  model.value = {
    formatVersion: 1,
    reportVersionId: '',
    format: 'PDF',
    parameterRules: {},
    recipients: [],
    cron: '0 0 9 * * *',
    timezone: 'Asia/Shanghai',
    dstPolicy: 'EARLIER_OFFSET',
    catchupSeconds: 3600,
    notifyOnReady: false
  };
  sourceParameters.value = [];
  baseline.value = JSON.stringify({ code: code.value, title: title.value, recipientText: recipientText.value, model: model.value });
  const response = await listReports();
  if (editorGate.current(ticket)) reports.value = response.data.filter((report) => report.status === 'PUBLISHED');
}
/** 固定报告选择不会隐式保留不匹配参数规则。 */
async function chooseReport(id: string) {
  if (!model.value) return;
  const ticket = editorGate.begin();
  busy.value = true;
  model.value.reportVersionId = id;
  model.value.parameterRules = {};
  sourceParameters.value = [];
  try {
    const response = await subscriptionSource(id);
    if (editorGate.current(ticket)) sourceParameters.value = response.data.parameters;
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 编辑读取历史固定契约，报告升级必须再次明确选择和发布。 */
async function edit(id: string) {
  if (!(await maySwitch())) return;
  const ticket = editorGate.begin();
  busy.value = true;
  try {
    const [a, b] = await Promise.all([subscriptionDetail(id), listReports()]);
    const parsed = JSON.parse(a.data.json) as SubscriptionDefinition;
    const source = await subscriptionSource(parsed.reportVersionId);
    if (!editorGate.current(ticket)) return;
    resource.value = a.data;
    code.value = a.data.code;
    title.value = a.data.title;
    model.value = parsed;
    recipientText.value = parsed.recipients.join(',');
    reports.value = b.data.filter((report) => report.status === 'PUBLISHED');
    sourceParameters.value = source.data.parameters;
    baseline.value = JSON.stringify({ code: code.value, title: title.value, recipientText: recipientText.value, model: model.value });
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 参数规则只使用固定值、白名单日期或接收人ID。 */
function rule(name: string, kind: string) {
  if (!model.value) return;
  if (kind === 'DEFAULT') delete model.value.parameterRules[name];
  else
    model.value.parameterRules[name] = {
      kind: kind as 'FIXED' | 'DATE_RELATIVE' | 'RECIPIENT_ID',
      value: kind === 'DATE_RELATIVE' ? 'TODAY' : kind === 'FIXED' ? '' : null
    };
}
/** 接收人ID全程保持字符串，不经Number转换。 */
function recipients() {
  const values = recipientText.value.split(/[，,\s]+/).filter(Boolean);
  if (!values.length || values.length > 20 || new Set(values).size !== values.length || values.some((value) => !/^\d{1,19}$/.test(value)))
    throw new Error('接收人应为1–20个不重复完整ID');
  return values;
}
/** 保存当前设计，旧会话回包不替换新草稿。 */
async function save() {
  if (!model.value || busy.value) return;
  let input;
  try {
    input = JSON.parse(JSON.stringify(model.value)) as SubscriptionDefinition;
    input.recipients = recipients();
  } catch (error) {
    ElMessage.warning((error as Error).message);
    return;
  }
  const ticket = editorGate.begin();
  const sentTitle = title.value;
  busy.value = true;
  try {
    const response = resource.value
      ? await saveSubscription(resource.value, sentTitle, input)
      : await createSubscription(code.value, sentTitle, input);
    if (editorGate.current(ticket)) {
      model.value = input;
      resource.value = response.data;
      baseline.value = JSON.stringify({ code: code.value, title: title.value, recipientText: recipientText.value, model: model.value });
      ElMessage.success('订阅草稿已保存');
      await load();
    }
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 发布已保存的revision，保留旧执行和时刻。 */
async function publish() {
  if (!resource.value || dirty.value || busy.value) return;
  const ticket = editorGate.begin();
  busy.value = true;
  try {
    await publishSubscription(resource.value);
    const response = await subscriptionDetail(resource.value.id);
    if (editorGate.current(ticket)) {
      resource.value = response.data;
      await load();
      ElMessage.success('固定订阅已发布');
    }
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 查询状态时旧结果不能覆盖新订阅。 */
async function results(id: string) {
  const ticket = resultGate.begin();
  selected.value = id;
  executions.value = [];
  schedule.value = null;
  try {
    const [a, b] = await Promise.all([subscriptionExecutions(id), subscriptionSchedule(id)]);
    if (resultGate.current(ticket)) {
      executions.value = a.data;
      schedule.value = b.data;
    }
  } catch {}
}
/** 重复点击保留同一请求键，成功后才开始新的手动时刻。 */
async function run(id: string) {
  if (busy.value) return;
  const ticket = editorGate.begin();
  busy.value = true;
  try {
    let key = keys.get(id);
    if (!key) {
      try {
        key = createRequestKey();
      } catch {
        ElMessage.error('当前浏览器缺少安全随机能力，无法创建订阅执行请求，请更新浏览器后重试');
        return;
      }
      keys.set(id, key);
    }
    await runSubscription(id, key);
    if (editorGate.current(ticket)) {
      keys.delete(id);
      await results(id);
    }
  } finally {
    if (editorGate.current(ticket)) busy.value = false;
  }
}
/** 停用会同时影响交付入口及通用导出中心的订阅文件。 */
async function state(row: ReportResource) {
  try {
    await ElMessageBox.confirm('变更订阅状态会影响后续调度和文件领取，确认变更？', '订阅状态');
  } catch {
    return;
  }
  busy.value = true;
  try {
    await subscriptionState(row, row.status === 'OFFLINE' ? 'PUBLISHED' : 'OFFLINE');
    await load();
    await results(row.id);
  } finally {
    busy.value = false;
  }
}
/** 重试使用已有执行身份和固定日期。 */
async function retry(id: string) {
  if (busy.value) return;
  busy.value = true;
  try {
    await retrySubscriptionExecution(id);
    await results(selected.value);
  } finally {
    busy.value = false;
  }
}
/** 取消特定接收人的执行。 */
async function cancel(id: string) {
  if (busy.value) return;
  busy.value = true;
  try {
    await cancelSubscriptionExecution(id);
    await results(selected.value);
  } finally {
    busy.value = false;
  }
}
onBeforeRouteLeave(async () => !dirty.value || (await maySwitch()));
onMounted(load);
onUnmounted(() => {
  gate.invalidate();
  editorGate.invalidate();
  resultGate.invalidate();
  switchGate.invalidate();
});
</script>
