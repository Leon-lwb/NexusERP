<template>
  <div class="app-container">
    <!-- 搜索栏：按列表页标准 §2（el-row/el-col 网格，每行 4 项，label-width 100px） -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="设备编码" prop="deviceCode">
            <el-input v-model="queryParams.deviceCode" placeholder="请输入设备编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="属性编码" prop="propertyCode">
            <el-input v-model="queryParams.propertyCode" placeholder="请输入属性编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="d in ALERT_STATUS_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="告警级别" prop="alertLevel">
            <el-select v-model="queryParams.alertLevel" placeholder="请选择级别" clearable style="width: 100%">
              <el-option v-for="d in ALERT_LEVEL_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="来源" prop="source">
            <el-select v-model="queryParams.source" placeholder="请选择来源" clearable style="width: 100%">
              <el-option v-for="d in ALERT_SOURCE_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="触发时间" prop="triggerTimeRange">
            <el-date-picker
              v-model="triggerTimeRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <!-- 搜索项 6 个 → 6 % 4 = 2，按钮补当前行第 4 格（offset=6） -->
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
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['iot:alert:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="alertList" show-overflow-tooltip>
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="设备编码" align="center" prop="deviceCode" />
      <el-table-column label="属性" align="center" prop="propertyCode" />
      <el-table-column label="类型" align="center" prop="alertType">
        <template #default="scope">{{ typeLabel(scope.row.alertType) }}</template>
      </el-table-column>
      <el-table-column label="级别" align="center" prop="alertLevel">
        <template #default="scope">
          <el-tag :type="levelTagType(scope.row.alertLevel)" size="small">{{ levelLabel(scope.row.alertLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="触发值" align="right" prop="triggerValue" />
      <el-table-column label="阈值" align="right" prop="threshold" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)" size="small">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" align="center" prop="source">
        <template #default="scope">{{ sourceLabel(scope.row.source) }}</template>
      </el-table-column>
      <el-table-column label="触发时间" align="center" prop="triggerTime">
        <template #default="scope">{{ parseTime(scope.row.triggerTime) }}</template>
      </el-table-column>
      <el-table-column label="最近触发" align="center" prop="lastTriggerTime">
        <template #default="scope">{{ parseTime(scope.row.lastTriggerTime) }}</template>
      </el-table-column>
      <el-table-column label="确认时间" align="center" prop="ackTime">
        <template #default="scope">{{ parseTime(scope.row.ackTime) }}</template>
      </el-table-column>
      <el-table-column label="恢复值" align="right" prop="resolveValue" />
      <el-table-column label="恢复时间" align="center" prop="resolveTime">
        <template #default="scope">{{ parseTime(scope.row.resolveTime) }}</template>
      </el-table-column>
      <el-table-column label="告警描述" align="center" prop="alertMessage" min-width="120" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Check"
            @click="handleAck(scope.row)"
            v-if="scope.row.status === 'ACTIVE'"
            v-hasPermi="['iot:alert:ack']"
            >确认</el-button
          >
          <el-button
            link
            type="success"
            icon="Select"
            @click="handleRecover(scope.row)"
            v-if="scope.row.status !== 'RESOLVED'"
            v-hasPermi="['iot:alert:recover']"
            >恢复</el-button
          >
          <el-button link type="info" icon="View" @click="handleNotifyLog(scope.row)" v-hasPermi="['iot:alert:query']">留痕</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 确认告警 -->
    <el-dialog title="确认告警" v-model="ackOpen" width="500px" append-to-body>
      <el-form ref="ackRef" :model="ackForm" :rules="ackRules" label-width="100px">
        <el-form-item label="告警描述">
          <el-input v-model="ackForm.alertMessage" type="textarea" readonly />
        </el-form-item>
        <el-form-item label="确认备注" prop="ackRemark">
          <el-input v-model="ackForm.ackRemark" type="textarea" placeholder="请填写确认说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitLoading" @click="submitAck">确 定</el-button>
          <el-button @click="ackOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 恢复/关闭告警 -->
    <el-dialog title="恢复/关闭告警" v-model="recoverOpen" width="500px" append-to-body>
      <el-form ref="recoverRef" :model="recoverForm" :rules="recoverRules" label-width="100px">
        <el-form-item label="告警描述">
          <el-input v-model="recoverForm.alertMessage" type="textarea" readonly />
        </el-form-item>
        <el-form-item label="恢复值" prop="resolveValue">
          <el-input-number v-model="recoverForm.resolveValue" :precision="4" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="恢复备注" prop="resolveRemark">
          <el-input v-model="recoverForm.resolveRemark" type="textarea" placeholder="请填写恢复说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitLoading" @click="submitRecover">确 定</el-button>
          <el-button @click="recoverOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 通知投递留痕（SSE / MES 安灯 是否发出、发给了谁） -->
    <el-drawer title="告警投递留痕" v-model="logOpen" size="50%">
      <el-table v-loading="logLoading" :data="notifyLogs" show-overflow-tooltip>
        <el-table-column label="渠道" align="center" prop="channel">
          <template #default="scope">{{ channelLabel(scope.row.channel) }}</template>
        </el-table-column>
        <el-table-column label="投递目标" align="center" prop="target" min-width="120" />
        <el-table-column label="结果" align="center" prop="status">
          <template #default="scope">
            <el-tag :type="notifyTagType(scope.row.status)" size="small">{{ notifyLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" align="center" prop="message" min-width="120" />
        <el-table-column label="时间" align="center" prop="createTime">
          <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!logLoading && notifyLogs.length === 0" description="该告警暂无投递留痕" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts" name="IotAlert">
import { listAlert, ackAlert, recoverAlert, notifyLog } from '@/api/iot/alert';
import type { AlertRecordQuery, AlertRecordVO, AlertNotifyLogVO } from '@/api/iot/alert/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

/**
 * 状态 / 级别 / 类型 / 来源 目前后端未落 sys_dict 种子
 * （见 doc/audit/2026-09-20-feat-iot-alert.md §2「字典未做」与 §7.4），
 * 因此按列表页标准 §3.4 的例外条款在本页内定义常量，并在字典补齐后改回 useDict。
 * 取值与 IotAlertStatus / IotAlertConstants / IotAlertRuleBo 的 @Pattern 白名单严格一致。
 */
const ALERT_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '未处理' },
  { value: 'ACKNOWLEDGED', label: '已确认' },
  { value: 'RESOLVED', label: '已恢复' }
];
const ALERT_LEVEL_OPTIONS = [
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' }
];
const ALERT_TYPE_OPTIONS = [
  { value: 'TEMPERATURE', label: '温度' },
  { value: 'VIBRATION', label: '振动' },
  { value: 'PRESSURE', label: '压力' },
  { value: 'OTHER', label: '其他' }
];
const ALERT_SOURCE_OPTIONS = [
  { value: 'RULE', label: '规则命中' },
  { value: 'DEFAULT', label: '默认阈值' },
  { value: 'ANOMALY', label: '异常检测' }
];

const triggerTimeRange = ref<string[]>([]);
const ackOpen = ref(false);
const recoverOpen = ref(false);
const logOpen = ref(false);
const logLoading = ref(false);
const submitLoading = ref(false);
const notifyLogs = ref<AlertNotifyLogVO[]>([]);
const ackRef = ref<FormInstance>();
const recoverRef = ref<FormInstance>();

const queryParams = ref<AlertRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  deviceCode: null,
  propertyCode: null,
  status: 'ACTIVE',
  alertLevel: null,
  source: null,
  beginTime: null,
  endTime: null
});

const ackRules = {};
const recoverRules = {};

const ackForm = ref<any>({ alertId: null, alertMessage: '', ackRemark: '' });
const recoverForm = ref<any>({ alertId: null, alertMessage: '', resolveValue: undefined, resolveRemark: '' });

// 告警记录为「只读列表 + 专用状态流转端点（ack/recover）」，无新增/修改/删除端点，
// 以下占位仅为满足 useCrud 的 API 签名，不会被调用（与 fin/settlement 页同一写法）。
const readonlyApi = () => Promise.resolve({ data: {} as any });

const {
  loading,
  list: alertList,
  total,
  showSearch,
  getList,
  handleQuery,
  handleExport
} = useCrud<AlertRecordVO, AlertRecordQuery>({
  apis: { list: listAlert, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'alertId',
  entityName: '告警记录',
  formRef: ackRef,
  queryParams,
  initForm: () => ({}),
  exportUrl: '/iot/alert/export',
  exportFileName: 'iot_alert_record',
  beforeList: (query) => {
    const [beginTime, endTime] = triggerTimeRange.value || [];
    query.beginTime = beginTime;
    query.endTime = endTime;
  }
});

function resetQuery() {
  triggerTimeRange.value = [];
  proxy.resetForm('queryRef');
  queryParams.value.status = 'ACTIVE';
  handleQuery();
}

function handleAck(row: AlertRecordVO) {
  ackForm.value = { alertId: row.alertId, alertMessage: row.alertMessage, ackRemark: '' };
  ackOpen.value = true;
}

function submitAck() {
  submitLoading.value = true;
  ackAlert({ alertId: ackForm.value.alertId, ackRemark: ackForm.value.ackRemark })
    .then(() => {
      proxy.$modal.msgSuccess('确认成功');
      ackOpen.value = false;
      getList();
    })
    .finally(() => {
      submitLoading.value = false;
    });
}

function handleRecover(row: AlertRecordVO) {
  recoverForm.value = { alertId: row.alertId, alertMessage: row.alertMessage, resolveValue: undefined, resolveRemark: '' };
  recoverOpen.value = true;
}

function submitRecover() {
  submitLoading.value = true;
  recoverAlert({
    alertId: recoverForm.value.alertId,
    resolveValue: recoverForm.value.resolveValue,
    resolveRemark: recoverForm.value.resolveRemark
  })
    .then(() => {
      proxy.$modal.msgSuccess('恢复成功');
      recoverOpen.value = false;
      getList();
    })
    .finally(() => {
      submitLoading.value = false;
    });
}

function handleNotifyLog(row: AlertRecordVO) {
  notifyLogs.value = [];
  logOpen.value = true;
  logLoading.value = true;
  notifyLog(row.alertId)
    .then((res) => {
      notifyLogs.value = res.data || [];
    })
    .finally(() => {
      logLoading.value = false;
    });
}

// ==================== 展示辅助 ====================
const pick = (options: { value: string; label: string }[], v?: string) => options.find((o) => o.value === v)?.label || v || '-';
const statusLabel = (v?: string) => pick(ALERT_STATUS_OPTIONS, v);
const levelLabel = (v?: string) => pick(ALERT_LEVEL_OPTIONS, v);
const typeLabel = (v?: string) => pick(ALERT_TYPE_OPTIONS, v);
const sourceLabel = (v?: string) => pick(ALERT_SOURCE_OPTIONS, v);
const channelLabel = (v?: string) => (v === 'MES_ANDON' ? 'MES安灯' : v === 'SSE' ? 'SSE实时推送' : v || '-');
const notifyLabel = (v?: string) => (v === 'SUCCESS' ? '成功' : v === 'FAILED' ? '失败' : v === 'SKIPPED' ? '已跳过' : v || '-');
const statusTagType = (v?: string) => (v === 'ACTIVE' ? 'danger' : v === 'ACKNOWLEDGED' ? 'warning' : 'success');
const levelTagType = (v?: string) => (v === 'HIGH' ? 'danger' : v === 'MEDIUM' ? 'warning' : 'info');
const notifyTagType = (v?: string) => (v === 'SUCCESS' ? 'success' : v === 'FAILED' ? 'danger' : 'info');

getList();
</script>

<style scoped>
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
