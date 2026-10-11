<template>
  <div class="app-container">
    <!-- 搜索栏：按列表页标准（el-row/el-col 网格，每行 4 项，label-width 100px） -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="设备" prop="deviceId">
            <el-select v-model="queryParams.deviceId" placeholder="全部设备" clearable filterable style="width: 100%">
              <el-option v-for="item in deviceOptions" :key="item.deviceId" :label="deviceLabel(item)" :value="String(item.deviceId)" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="固件" prop="firmwareId">
            <el-select v-model="queryParams.firmwareId" placeholder="全部固件" clearable filterable style="width: 100%">
              <el-option v-for="item in firmwareOptions" :key="item.firmwareId" :label="firmwareLabel(item)" :value="item.firmwareId" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="任务状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in otaStatusOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="批次号" prop="batchNo">
            <el-input v-model="queryParams.batchNo" placeholder="请输入批次号" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <!-- 搜索项 4 个 → 4 % 4 = 0，按钮补新行第 4 格（offset=18） -->
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Promotion" @click="openSingleSend()" v-hasPermi="['iot:ota:create']">单设备下发</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Operation" @click="openBatchSend()" v-hasPermi="['iot:ota:create']">按分组批量下发</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="CircleCheck" @click="openVersionCheck()" v-hasPermi="['iot:ota:versionCheck']">版本核对</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-checkbox v-model="autoRefresh" class="auto-refresh">自动刷新({{ POLL_INTERVAL / 1000 }}s)</el-checkbox>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="taskList" show-overflow-tooltip>
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="设备" align="center" min-width="140">
        <template #default="scope">{{ deviceText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="目标版本" align="center" prop="targetVersion" min-width="90">
        <template #default="scope">{{ scope.row.targetVersion || '-' }}</template>
      </el-table-column>
      <el-table-column label="当前版本" align="center" prop="currentVersion" min-width="90">
        <template #default="scope">{{ scope.row.currentVersion || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" min-width="80">
        <template #default="scope">
          <dict-tag :options="otaStatusOptions" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="进度" align="center" min-width="130">
        <template #default="scope">
          <el-progress :percentage="progressOf(scope.row)" :stroke-width="12" :status="progressStatus(scope.row)" />
        </template>
      </el-table-column>
      <el-table-column label="批次号" align="center" prop="batchNo" min-width="120">
        <template #default="scope">{{ scope.row.batchNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="下发时间" align="center" prop="sentTime" width="160">
        <template #default="scope">{{ scope.row.sentTime ? parseTime(scope.row.sentTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="结束时间" align="center" prop="finishTime" width="160">
        <template #default="scope">{{ scope.row.finishTime ? parseTime(scope.row.finishTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="失败原因" align="center" prop="failReason" min-width="140">
        <template #default="scope">{{ scope.row.failReason || '-' }}</template>
      </el-table-column>
      <el-table-column label="重试次数" align="center" prop="retryCount" min-width="80">
        <template #default="scope">{{ scope.row.retryCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="关联指令" align="center" min-width="110" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button v-if="scope.row.commandId" link type="primary" icon="Link" @click="goToCommand(scope.row)">指令ID</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['iot:ota:query']">详情</el-button>
          <el-button
            link
            type="warning"
            icon="RefreshRight"
            :disabled="!canRetry(scope.row)"
            @click="handleRetry(scope.row)"
            v-hasPermi="['iot:ota:retry']"
            >重试</el-button
          >
          <el-button
            link
            type="danger"
            icon="CircleClose"
            :disabled="!canCancel(scope.row)"
            @click="handleCancel(scope.row)"
            v-hasPermi="['iot:ota:cancel']"
            >取消</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 单设备下发 -->
    <el-dialog title="单设备下发升级" v-model="singleOpen" width="620px" append-to-body>
      <el-form ref="singleRef" :model="singleForm" :rules="singleRules" label-width="100px">
        <el-form-item label="固件" prop="firmwareId">
          <el-select v-model="singleForm.firmwareId" placeholder="请选择已发布固件" filterable style="width: 100%">
            <el-option v-for="item in releasedFirmwareOptions" :key="item.firmwareId" :label="firmwareLabel(item)" :value="item.firmwareId" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标设备" prop="deviceId">
          <el-select v-model="singleForm.deviceId" placeholder="请选择设备（仅列出启用中设备）" filterable style="width: 100%">
            <el-option v-for="item in activeDeviceOptions" :key="item.deviceId" :label="deviceLabel(item)" :value="String(item.deviceId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="singleForm.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="下发前置校验由后端保证：固件必须为已发布、设备必须为启用中、且同设备同固件不允许并存进行中任务；产品型号不匹配会被拒绝。"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitLoading" @click="submitSingle()">确 定</el-button>
          <el-button @click="singleOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 按分组批量下发 -->
    <el-dialog title="按分组批量下发升级" v-model="batchOpen" width="620px" append-to-body>
      <el-form ref="batchRef" :model="batchForm" :rules="batchRules" label-width="100px">
        <el-form-item label="固件" prop="firmwareId">
          <el-select v-model="batchForm.firmwareId" placeholder="请选择已发布固件" filterable style="width: 100%">
            <el-option v-for="item in releasedFirmwareOptions" :key="item.firmwareId" :label="firmwareLabel(item)" :value="item.firmwareId" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标分组" prop="groupId">
          <el-tree-select
            v-model="batchForm.groupId"
            :data="groupTree"
            :props="groupTreeSelectProps"
            placeholder="请选择分组"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="包含子分组">
          <el-switch v-model="batchForm.includeSubGroup" />
          <span class="field-tip">开启后按分组子树逐台建任务</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="batchForm.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          title="批量下发为「部分成功」语义：被禁用/已存在进行中任务的设备会被跳过并逐条返回原因，其余设备照常创建，不会整体回滚。"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitLoading" @click="submitBatch()">确 定</el-button>
          <el-button @click="batchOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 批量下发结果 -->
    <el-dialog title="批量下发结果" v-model="resultOpen" width="680px" append-to-body>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="批次号">{{ batchResult.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成功">{{ batchResult.created ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="跳过">{{ batchResult.skipped ?? 0 }}</el-descriptions-item>
      </el-descriptions>
      <div class="result-summary">
        目标设备 {{ batchResult.total ?? 0 }} 台：成功 {{ batchResult.created ?? 0 }} 台 / 跳过 {{ batchResult.skipped ?? 0 }} 台
      </div>
      <template v-if="batchResult.messages && batchResult.messages.length">
        <div class="result-title">跳过原因（逐条）</div>
        <ul class="result-messages">
          <li v-for="(msg, index) in batchResult.messages" :key="index">{{ msg }}</li>
        </ul>
      </template>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="resultOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 版本一致性核对 -->
    <el-dialog title="版本一致性核对" v-model="versionOpen" width="820px" append-to-body>
      <el-form :model="versionForm" label-width="90px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="固件">
              <el-select v-model="versionForm.firmwareId" placeholder="请选择固件（含已停用）" filterable style="width: 100%">
                <el-option v-for="item in firmwareOptions" :key="item.firmwareId" :label="firmwareLabel(item)" :value="item.firmwareId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仅看落后">
              <el-switch v-model="versionForm.onlyBehind" />
              <el-button type="primary" icon="Search" class="version-search" :loading="versionLoading" @click="loadVersionCheck()">核对</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-table v-loading="versionLoading" :data="versionList" :row-class-name="versionRowClass" show-overflow-tooltip max-height="420">
        <el-table-column label="设备编码" align="center" prop="deviceCode" />
        <el-table-column label="设备名称" align="center" prop="deviceName" min-width="120" />
        <el-table-column label="当前版本" align="center" prop="currentVersion">
          <template #default="scope">{{ scope.row.currentVersion || '未知' }}</template>
        </el-table-column>
        <el-table-column label="目标版本" align="center" prop="targetVersion" />
        <el-table-column label="版本状态" align="center" prop="behind">
          <template #default="scope">
            <el-tag v-if="scope.row.behind" type="danger" size="small">落后</el-tag>
            <el-tag v-else type="warning" size="small">高于/不一致</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="field-tip">仅列出"设备当前版本 ≠ 目标版本"的在用设备；空版本视为落后。</div>
    </el-dialog>

    <!-- 任务详情 -->
    <el-drawer title="升级任务详情" v-model="detailOpen" size="50%">
      <el-descriptions v-loading="detailLoading" :column="1" border>
        <el-descriptions-item label="任务ID">{{ detail.taskId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备">{{ deviceText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="固件ID">{{ detail.firmwareId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标版本">{{ detail.targetVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前版本">{{ detail.currentVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="otaStatusOptions" :value="detail.status" />
        </el-descriptions-item>
        <el-descriptions-item label="进度">{{ progressOf(detail) }}%</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detail.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源分组ID">{{ detail.groupId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联指令">
          <el-button v-if="detail.commandId" link type="primary" icon="Link" @click="goToCommand(detail)">查看指令 {{ detail.commandId }}</el-button>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="下发时间">{{ detail.sentTime ? parseTime(detail.sentTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ detail.startTime ? parseTime(detail.startTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ detail.finishTime ? parseTime(detail.finishTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="失败/取消原因">{{ detail.failReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ detail.retryCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="回执消息ID">{{ detail.reportMsgId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下发人">{{ detail.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="field-tip">
        进度由设备回执驱动（MQTT event/post 的 OTA_PROGRESS/OTA_RESULT，或网关经 POST /iot/ota/report 代报）；后端未提供进度 SSE 推送，本页按
        {{ POLL_INTERVAL / 1000 }} 秒轮询刷新。
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts" name="IotOta">
import { cancelOtaTask, checkOtaVersion, createOtaTask, createOtaTaskBatch, getOtaTask, listOtaTask, retryOtaTask } from '@/api/iot/ota';
import type { OtaBatchResultVO, OtaTaskQuery, OtaTaskVO, OtaVersionCheckVO } from '@/api/iot/ota/types';
import { optionFirmware } from '@/api/iot/firmware';
import type { FirmwareVO } from '@/api/iot/firmware/types';
import { listDevice } from '@/api/iot/device';
import type { DeviceVO } from '@/api/iot/device/types';
import { listDeviceGroup } from '@/api/iot/deviceGroup';
import type { DeviceGroupVO } from '@/api/iot/deviceGroup/types';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { iot_ota_status } = toRefs<any>(proxy.useDict('iot_ota_status'));

/** 字典未同步（sys_dict 种子未执行）时的本地兜底，避免 dict-tag 渲染空白 */
const FALLBACK_OTA_STATUS: DictDataOption[] = [
  { label: '已登记', value: 'PENDING', elTagType: 'info' },
  { label: '已下发', value: 'SENT', elTagType: 'primary' },
  { label: '升级中', value: 'UPGRADING', elTagType: 'warning' },
  { label: '成功', value: 'SUCCESS', elTagType: 'success' },
  { label: '失败', value: 'FAILED', elTagType: 'danger' },
  { label: '超时', value: 'TIMEOUT', elTagType: 'warning' },
  { label: '已取消', value: 'CANCELLED', elTagType: 'info' }
];
const otaStatusOptions = computed<DictDataOption[]>(() => (iot_ota_status.value?.length ? iot_ota_status.value : FALLBACK_OTA_STATUS));

/** 进度轮询间隔（后端未提供 OTA 进度 SSE，见报告 §2） */
const POLL_INTERVAL = 8000;
const autoRefresh = ref(true);
let pollTimer: ReturnType<typeof setInterval> | null = null;

const singleRef = ref<FormInstance>();
const batchRef = ref<FormInstance>();
const singleOpen = ref(false);
const batchOpen = ref(false);
const resultOpen = ref(false);
const versionOpen = ref(false);
const versionLoading = ref(false);
const submitLoading = ref(false);
const detailOpen = ref(false);
const detailLoading = ref(false);
const detail = ref<Partial<OtaTaskVO>>({});

const deviceOptions = ref<DeviceVO[]>([]);
const firmwareOptions = ref<FirmwareVO[]>([]);
const groupTree = ref<DeviceGroupVO[]>([]);
const versionList = ref<OtaVersionCheckVO[]>([]);
const batchResult = ref<OtaBatchResultVO>({});

/** 分组树节点字段映射（el-tree-select 的 TreeOptionProps 未收录 value 字段） */
const groupTreeSelectProps = { value: 'groupId', label: 'groupName', children: 'children' };

const queryParams = ref<OtaTaskQuery>({
  pageNum: 1,
  pageSize: 10,
  deviceId: null,
  firmwareId: null,
  status: null,
  batchNo: null
});

const singleForm = ref<{ firmwareId: string | null; deviceId: string | null; remark: string }>({ firmwareId: null, deviceId: null, remark: '' });
const batchForm = ref<{ firmwareId: string | null; groupId: string | null; includeSubGroup: boolean; remark: string }>({
  firmwareId: null,
  groupId: null,
  includeSubGroup: false,
  remark: ''
});
const versionForm = ref<{ firmwareId: string | null; onlyBehind: boolean }>({ firmwareId: null, onlyBehind: false });

const singleRules = {
  firmwareId: [{ required: true, message: '请选择固件', trigger: 'change' }],
  deviceId: [{ required: true, message: '请选择目标设备', trigger: 'change' }]
};
const batchRules = {
  firmwareId: [{ required: true, message: '请选择固件', trigger: 'change' }],
  groupId: [{ required: true, message: '请选择目标分组', trigger: 'change' }]
};

const {
  loading,
  list: taskList,
  total,
  showSearch,
  getList,
  handleQuery
} = useCrud<OtaTaskVO, OtaTaskQuery>({
  apis: { list: listOtaTask },
  idField: 'taskId',
  entityName: '升级任务',
  formRef: singleRef,
  queryParams,
  initForm: () => ({})
});

/** 仅列出已发布固件（后端只允许对 RELEASED 固件下发） */
const releasedFirmwareOptions = computed(() => firmwareOptions.value.filter((item) => item.status === 'RELEASED'));
/** 仅列出启用中设备（禁用/注销设备后端会拒绝下发） */
const activeDeviceOptions = computed(() => deviceOptions.value.filter((item) => item.status === 'ACTIVE'));

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function deviceLabel(item: DeviceVO) {
  return `${item.deviceCode} - ${item.deviceName}`;
}

function firmwareLabel(item: FirmwareVO) {
  const key = item.productKey ? `${item.productKey}/` : '';
  return `${item.firmwareName}（${key}${item.version}）`;
}

/** 列表无设备名称，只有冗余的 deviceCode，故展示"编码(ID)" */
function deviceText(row: Partial<OtaTaskVO>) {
  if (!row?.deviceId && !row?.deviceCode) return '-';
  return row.deviceCode ? `${row.deviceCode}（${row.deviceId}）` : String(row.deviceId);
}

function progressOf(row?: Partial<OtaTaskVO>) {
  const value = Number(row?.progress ?? 0);
  if (Number.isNaN(value)) return 0;
  return Math.min(100, Math.max(0, value));
}

function progressStatus(row?: Partial<OtaTaskVO>) {
  if (row?.status === 'SUCCESS') return 'success';
  if (row?.status === 'FAILED' || row?.status === 'TIMEOUT') return 'exception';
  return undefined;
}

/** 仅 FAILED/TIMEOUT 可重试（与后端 IotOtaStatus.canRetry 一致） */
function canRetry(row: OtaTaskVO) {
  return row.status === 'FAILED' || row.status === 'TIMEOUT';
}

/** 仅进行中（PENDING/SENT/UPGRADING）可取消（与后端 IotOtaStatus.canCancel 一致） */
function canCancel(row: OtaTaskVO) {
  return row.status === 'PENDING' || row.status === 'SENT' || row.status === 'UPGRADING';
}

/** 跳转设备指令页（携带 commandId 便于人工核对；指令页当前未按该参数过滤，见报告 §2） */
function goToCommand(row: Partial<OtaTaskVO>) {
  if (!row?.commandId) return;
  router.push({ path: '/iot/message/command', query: { commandId: String(row.commandId) } });
}

/** 设备下拉（含非启用设备，供筛选） */
function loadDeviceOptions() {
  listDevice({ pageNum: 1, pageSize: 500 })
    .then((res) => {
      deviceOptions.value = res.rows || [];
    })
    .catch(() => {
      deviceOptions.value = [];
    });
}

/** 固件下拉（全量，供筛选与版本核对；下发仅取已发布） */
function loadFirmwareOptions() {
  optionFirmware()
    .then((res: any) => {
      firmwareOptions.value = (res.data || []) as FirmwareVO[];
    })
    .catch(() => {
      firmwareOptions.value = [];
    });
}

/** 分组树（批量下发） */
function loadGroupOptions() {
  listDeviceGroup()
    .then((res: any) => {
      const flat = (res.data || []) as DeviceGroupVO[];
      groupTree.value = proxy.handleTree(flat, 'groupId', 'parentId') || [];
    })
    .catch(() => {
      groupTree.value = [];
    });
}

function openSingleSend() {
  singleForm.value = { firmwareId: null, deviceId: null, remark: '' };
  singleOpen.value = true;
}

function openBatchSend() {
  batchForm.value = { firmwareId: null, groupId: null, includeSubGroup: false, remark: '' };
  batchOpen.value = true;
}

function openVersionCheck() {
  versionList.value = [];
  versionForm.value = { firmwareId: null, onlyBehind: false };
  versionOpen.value = true;
}

function submitSingle() {
  singleRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    createOtaTask({
      firmwareId: singleForm.value.firmwareId as string,
      deviceId: singleForm.value.deviceId as string,
      remark: singleForm.value.remark
    })
      .then(() => {
        proxy.$modal.msgSuccess('升级任务已下发');
        singleOpen.value = false;
        getList();
      })
      .catch((e: any) => {
        // 拦截器已统一提示后端原因（固件未发布/设备禁用/产品型号不匹配/重复进行中任务等）
        if (e?.message) proxy.$modal.msgError(e.message);
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

function submitBatch() {
  batchRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    createOtaTaskBatch({
      firmwareId: batchForm.value.firmwareId as string,
      groupId: batchForm.value.groupId as string,
      includeSubGroup: batchForm.value.includeSubGroup,
      remark: batchForm.value.remark
    })
      .then((res: any) => {
        batchResult.value = (res.data || {}) as OtaBatchResultVO;
        batchOpen.value = false;
        resultOpen.value = true;
        getList();
      })
      .catch((e: any) => {
        if (e?.message) proxy.$modal.msgError(e.message);
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

function loadVersionCheck() {
  if (!versionForm.value.firmwareId) {
    proxy.$modal.msgWarning('请先选择固件');
    return;
  }
  versionLoading.value = true;
  checkOtaVersion(versionForm.value.firmwareId, versionForm.value.onlyBehind)
    .then((res: any) => {
      versionList.value = (res.data || []) as OtaVersionCheckVO[];
    })
    .catch(() => {
      versionList.value = [];
    })
    .finally(() => {
      versionLoading.value = false;
    });
}

/** 落后设备整行高亮 */
function versionRowClass({ row }: { row: OtaVersionCheckVO }) {
  return row.behind ? 'version-behind-row' : '';
}

function handleRetry(row: OtaTaskVO) {
  proxy.$modal
    .confirm(`确认重试设备「${deviceText(row)}」的升级任务（将重新下发一条新指令）？`, '重试确认')
    .then(() => retryOtaTask(row.taskId))
    .then(() => {
      proxy.$modal.msgSuccess('已重新下发');
      getList();
    })
    .catch((e: any) => {
      if (e === 'cancel' || e === 'close') return;
      if (e?.message) proxy.$modal.msgError(e.message);
    });
}

/** 取消：二次确认 + 原因输入（原因写入任务的失败/取消原因） */
function handleCancel(row: OtaTaskVO) {
  proxy.$modal
    .prompt(`确认取消设备「${deviceText(row)}」的升级任务？请填写取消原因（可选）。`, '取消升级任务', {
      inputType: 'textarea',
      inputPlaceholder: '如：设备已下线，改为人工升级'
    })
    .then((res: any) => {
      cancelOtaTask(row.taskId, res?.value || undefined)
        .then(() => {
          proxy.$modal.msgSuccess('任务已取消');
          getList();
        })
        .catch((e: any) => {
          if (e?.message) proxy.$modal.msgError(e.message);
        });
    })
    .catch(() => {});
}

function handleDetail(row: OtaTaskVO) {
  detail.value = {};
  detailOpen.value = true;
  detailLoading.value = true;
  getOtaTask(row.taskId)
    .then((res: any) => {
      detail.value = (res.data || {}) as OtaTaskVO;
    })
    .catch(() => {
      detail.value = {};
    })
    .finally(() => {
      detailLoading.value = false;
    });
}

/** 启动进度轮询（后端未做 OTA 进度 SSE 推送，先用轮询） */
function startPolling() {
  stopPolling();
  if (!autoRefresh.value) return;
  pollTimer = setInterval(() => {
    getList();
  }, POLL_INTERVAL);
}

/** 清理定时器（避免组件销毁后继续请求；本仓库此前有定时器泄漏被审计点名） */
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

watch(autoRefresh, (value) => {
  if (value) startPolling();
  else stopPolling();
});

onUnmounted(() => {
  stopPolling();
});

loadDeviceOptions();
loadFirmwareOptions();
loadGroupOptions();
getList();
startPolling();
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
.auto-refresh {
  margin-left: 8px;
}
.field-tip {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.result-summary {
  margin-top: 12px;
  font-weight: 600;
}
.result-title {
  margin-top: 12px;
  color: var(--el-text-color-regular);
}
.result-messages {
  margin: 6px 0 0;
  padding-left: 20px;
  color: var(--el-color-danger);
  font-size: 13px;
  line-height: 1.8;
}
.version-search {
  margin-left: 10px;
}
:deep(.version-behind-row) {
  background-color: var(--el-color-danger-light-9);
}
</style>
