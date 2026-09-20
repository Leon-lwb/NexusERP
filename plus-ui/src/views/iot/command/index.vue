<template>
  <div class="app-container">
    <!-- 搜索栏：按列表页标准 §2（el-row/el-col 网格，每行 4 项，label-width 100px） -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="设备" prop="deviceId">
            <el-select v-model="queryParams.deviceId" placeholder="请选择设备" clearable filterable style="width: 100%">
              <el-option v-for="item in deviceOptions" :key="item.deviceId" :label="deviceLabel(item)" :value="item.deviceId" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="指令状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in iot_command_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <!-- 搜索项 2 个 → 2 % 4 = 2，按钮补当前行第 4 格（offset=6） -->
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
        <el-button type="primary" plain icon="Promotion" @click="openSend()" v-hasPermi="['iot:command:send']">下发指令</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="commandList" show-overflow-tooltip>
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="设备编码" align="center" prop="deviceCode" />
      <el-table-column label="指令类型" align="center" prop="commandType">
        <template #default="scope">
          <dict-tag :options="iot_command_type" :value="scope.row.commandType" />
        </template>
      </el-table-column>
      <el-table-column label="下发Topic" align="center" prop="topic" min-width="140" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="iot_command_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="下发时间" align="center" prop="sentTime">
        <template #default="scope">{{ parseTime(scope.row.sentTime) }}</template>
      </el-table-column>
      <el-table-column label="回执时间" align="center" prop="ackTime">
        <template #default="scope">{{ parseTime(scope.row.ackTime) }}</template>
      </el-table-column>
      <el-table-column label="重发次数" align="center" prop="retryCount" />
      <el-table-column label="下发人" align="center" prop="operatorName" />
      <el-table-column label="结果说明" align="center" prop="resultMessage" min-width="120" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['iot:command:query']">详情</el-button>
          <el-button
            link
            type="warning"
            icon="RefreshRight"
            :disabled="scope.row.status === 'SUCCESS'"
            @click="handleResend(scope.row)"
            v-hasPermi="['iot:command:resend']"
            >重发</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 下发指令 -->
    <el-dialog title="下发指令" v-model="sendOpen" width="680px" append-to-body>
      <el-form ref="sendRef" :model="sendForm" :rules="sendRules" label-width="110px">
        <el-form-item label="目标设备" prop="deviceId">
          <el-select v-model="sendForm.deviceId" placeholder="请选择设备" filterable style="width: 100%">
            <el-option v-for="item in deviceOptions" :key="item.deviceId" :label="deviceLabel(item)" :value="item.deviceId" />
          </el-select>
        </el-form-item>
        <el-form-item label="指令类型" prop="commandType">
          <el-radio-group v-model="sendForm.commandType">
            <el-radio value="PROPERTY_SET">属性设置 (property/set)</el-radio>
            <el-radio value="SERVICE_INVOKE">服务调用 (event/set)</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="sendForm.commandType === 'PROPERTY_SET'">
          <el-form-item v-for="(row, index) in paramRows" :key="index" :label="index === 0 ? '属性设置' : ''">
            <div class="param-row">
              <el-input v-model="row.key" placeholder="属性编码，如 temperature" />
              <el-input v-model="row.value" placeholder="期望值" />
              <el-button link type="danger" icon="Delete" @click="removeParamRow(index)" />
            </div>
          </el-form-item>
          <el-form-item>
            <el-button link type="primary" icon="Plus" @click="addParamRow()">添加属性</el-button>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="服务名" prop="serviceName">
            <el-input v-model="sendForm.serviceName" placeholder="如 reboot / calibrate / otaUpgrade" />
          </el-form-item>
          <el-form-item label="服务参数" prop="paramsJson">
            <el-input v-model="sendForm.paramsJson" type="textarea" :rows="5" placeholder='JSON 对象，如 {"delay": 5}；留空表示无参数' />
          </el-form-item>
        </template>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="sendForm.remark" type="textarea" placeholder="写入指令留痕的结果说明前缀（可选）" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="指令先留痕后下发：设备禁用/注销时后端拒绝下发；真实执行结果依赖设备回执（property/set_reply）。"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitLoading" @click="submitSend">确 定</el-button>
          <el-button @click="sendOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 指令详情 / 回执 -->
    <el-drawer title="指令详情" v-model="detailOpen" size="50%">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="设备编码">{{ detail.deviceCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="指令类型">
          <dict-tag :options="iot_command_type" :value="detail.commandType" />
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="iot_command_status" :value="detail.status" />
        </el-descriptions-item>
        <el-descriptions-item label="下发Topic">{{ detail.topic || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下发时间">{{ parseTime(detail.sentTime) }}</el-descriptions-item>
        <el-descriptions-item label="回执时间">{{ parseTime(detail.ackTime) }}</el-descriptions-item>
        <el-descriptions-item label="重发次数">{{ detail.retryCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="下发人">{{ detail.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果说明">{{ detail.resultMessage || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="payload-title">下发载荷（只读）</div>
      <el-input v-model="detailPayloadText" type="textarea" readonly :rows="8" resize="vertical" />
      <div class="payload-title">回执载荷（只读）</div>
      <el-input v-model="detailAckText" type="textarea" readonly :rows="8" resize="vertical" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts" name="IotCommand">
import { getDeviceCommand, listDeviceCommand, resendDeviceCommand, sendPropertySet, sendServiceInvoke } from '@/api/iot/command';
import type { DeviceCommandQuery, DeviceCommandVO } from '@/api/iot/command/types';
import { listDevice } from '@/api/iot/device';
import type { DeviceVO } from '@/api/iot/device/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { iot_command_type, iot_command_status } = toRefs<any>(proxy.useDict('iot_command_type', 'iot_command_status'));

const sendRef = ref<FormInstance>();
const sendOpen = ref(false);
const submitLoading = ref(false);
const detailOpen = ref(false);
const detail = ref<Partial<DeviceCommandVO>>({});
const detailPayloadText = ref('');
const detailAckText = ref('');
const deviceOptions = ref<DeviceVO[]>([]);
const paramRows = ref<Array<{ key: string; value: string }>>([{ key: '', value: '' }]);

const queryParams = ref<DeviceCommandQuery>({
  pageNum: 1,
  pageSize: 10,
  deviceId: null,
  status: null
});

const sendForm = ref<{
  deviceId: string | number | null;
  commandType: string;
  serviceName: string;
  paramsJson: string;
  remark: string;
}>({
  deviceId: null,
  commandType: 'PROPERTY_SET',
  serviceName: '',
  paramsJson: '',
  remark: ''
});

const sendRules = {
  deviceId: [{ required: true, message: '目标设备不能为空', trigger: 'change' }],
  commandType: [{ required: true, message: '指令类型不能为空', trigger: 'change' }],
  serviceName: [{ required: true, message: '服务名不能为空', trigger: 'blur' }]
};

const {
  loading,
  list: commandList,
  total,
  showSearch,
  getList,
  handleQuery
} = useCrud<DeviceCommandVO, DeviceCommandQuery>({
  apis: { list: listDeviceCommand },
  idField: 'commandId',
  entityName: '设备指令',
  formRef: sendRef,
  queryParams,
  initForm: () => ({})
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function deviceLabel(item: DeviceVO) {
  return `${item.deviceCode} - ${item.deviceName}`;
}

/** 设备下拉：一次性加载（IoT 设备量级较小），供筛选与下发复用 */
function loadDeviceOptions() {
  listDevice({ pageNum: 1, pageSize: 500 })
    .then((res) => {
      deviceOptions.value = res.rows || [];
    })
    .catch(() => {
      deviceOptions.value = [];
    });
}

function addParamRow() {
  paramRows.value.push({ key: '', value: '' });
}

function removeParamRow(index: number) {
  if (paramRows.value.length <= 1) {
    paramRows.value = [{ key: '', value: '' }];
    return;
  }
  paramRows.value.splice(index, 1);
}

function openSend() {
  sendForm.value = { deviceId: null, commandType: 'PROPERTY_SET', serviceName: '', paramsJson: '', remark: '' };
  paramRows.value = [{ key: '', value: '' }];
  sendOpen.value = true;
}

function submitSend() {
  sendRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    const base = {
      deviceId: sendForm.value.deviceId as string,
      commandType: sendForm.value.commandType,
      remark: sendForm.value.remark
    };
    let request;
    if (sendForm.value.commandType === 'PROPERTY_SET') {
      const params: Record<string, any> = {};
      paramRows.value.forEach((row) => {
        if (row.key) params[row.key] = row.value;
      });
      if (Object.keys(params).length === 0) {
        proxy.$modal.msgWarning('请至少填写一个属性编码');
        return;
      }
      request = sendPropertySet({ ...base, params });
    } else {
      let params: Record<string, any> = {};
      if (sendForm.value.paramsJson) {
        try {
          params = JSON.parse(sendForm.value.paramsJson);
        } catch {
          proxy.$modal.msgError('服务参数不是合法 JSON');
          return;
        }
      }
      request = sendServiceInvoke({ ...base, serviceName: sendForm.value.serviceName, params });
    }
    submitLoading.value = true;
    request
      .then((res: any) => {
        const status = res?.data?.status;
        proxy.$modal.msgSuccess(status === 'FAILED' ? '指令已登记但下发失败，请查看结果说明' : '指令已下发，等待设备回执');
        sendOpen.value = false;
        getList();
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

function handleDetail(row: DeviceCommandVO) {
  getDeviceCommand(row.commandId).then((res) => {
    const data = res.data || row;
    detail.value = data;
    detailPayloadText.value = prettyJson(data.payload);
    detailAckText.value = prettyJson(data.ackPayload);
    detailOpen.value = true;
  });
}

function handleResend(row: DeviceCommandVO) {
  proxy.$modal
    .confirm(`是否确认重发指令「${row.commandId}」？`)
    .then(() => resendDeviceCommand(row.commandId))
    .then(() => {
      proxy.$modal.msgSuccess('重发成功');
      getList();
    })
    .catch(() => {});
}

/** 载荷 JSON 美化（解析失败时原样展示） */
function prettyJson(text?: string) {
  if (!text) return '';
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

loadDeviceOptions();
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
.param-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.payload-title {
  margin: 12px 0 6px;
  font-weight: 600;
}
</style>
