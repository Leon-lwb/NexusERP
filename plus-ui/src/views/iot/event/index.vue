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
          <el-form-item label="事件编码" prop="eventCode">
            <el-input v-model="queryParams.eventCode" placeholder="请输入事件编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="事件级别" prop="eventLevel">
            <el-select v-model="queryParams.eventLevel" placeholder="请选择级别" clearable style="width: 100%">
              <el-option v-for="dict in iot_event_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="处理状态" prop="processStatus">
            <el-select v-model="queryParams.processStatus" placeholder="请选择处理状态" clearable style="width: 100%">
              <el-option v-for="dict in iot_event_process_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="联动告警" prop="hasAlert">
            <!-- 布尔开关而非业务状态码，无需字典 -->
            <el-select v-model="queryParams.hasAlert" placeholder="请选择" clearable style="width: 100%">
              <el-option label="仅已联动告警" :value="true" />
              <el-option label="不限" :value="false" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="事件时间" prop="eventTimeRange">
            <el-date-picker
              v-model="eventTimeRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
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
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['iot:event:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="eventList" show-overflow-tooltip>
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="设备编码" align="center" prop="deviceCode" />
      <el-table-column label="事件编码" align="center" prop="eventCode" min-width="120" />
      <el-table-column label="事件名称" align="center" prop="eventName" min-width="120" />
      <el-table-column label="级别" align="center" prop="eventLevel">
        <template #default="scope">
          <dict-tag :options="iot_event_level" :value="scope.row.eventLevel" />
        </template>
      </el-table-column>
      <el-table-column label="事件时间" align="center" prop="eventTime">
        <template #default="scope">{{ parseTime(scope.row.eventTime) }}</template>
      </el-table-column>
      <el-table-column label="接收时间" align="center" prop="receiveTime">
        <template #default="scope">{{ parseTime(scope.row.receiveTime) }}</template>
      </el-table-column>
      <el-table-column label="处理状态" align="center" prop="processStatus">
        <template #default="scope">
          <dict-tag :options="iot_event_process_status" :value="scope.row.processStatus" />
        </template>
      </el-table-column>
      <el-table-column label="联动告警" align="center" prop="alertId">
        <template #default="scope">
          <el-button v-if="scope.row.alertId" link type="primary" @click="goToAlert(scope.row)">查看告警</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="来源" align="center" prop="source" />
      <el-table-column label="处理说明" align="center" prop="processRemark" min-width="120" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['iot:event:query']">详情</el-button>
          <el-button link type="primary" icon="Check" @click="handleProcess(scope.row)" v-hasPermi="['iot:event:process']">处理</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 事件详情：载荷 JSON 只读展示 -->
    <el-drawer title="设备事件详情" v-model="detailOpen" size="50%">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="设备编码">{{ detail.deviceCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="事件编码">{{ detail.eventCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="事件名称">{{ detail.eventName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="事件级别">
          <dict-tag :options="iot_event_level" :value="detail.eventLevel" />
        </el-descriptions-item>
        <el-descriptions-item label="事件时间">{{ parseTime(detail.eventTime) }}</el-descriptions-item>
        <el-descriptions-item label="接收时间">{{ parseTime(detail.receiveTime) }}</el-descriptions-item>
        <el-descriptions-item label="处理状态">
          <dict-tag :options="iot_event_process_status" :value="detail.processStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="处理说明">{{ detail.processRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数据来源">{{ detail.source || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源消息ID">{{ detail.sourceMsgId || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="payload-title">事件载荷（只读）</div>
      <el-input v-model="detailPayloadText" type="textarea" readonly :rows="14" resize="vertical" />
    </el-drawer>

    <!-- 标记处理状态 -->
    <el-dialog title="处理设备事件" v-model="processOpen" width="520px" append-to-body>
      <el-form ref="processRef" :model="processForm" :rules="processRules" label-width="100px">
        <el-form-item label="事件编码">
          <el-input v-model="processForm.eventCode" readonly />
        </el-form-item>
        <el-form-item label="处理结果" prop="processStatus">
          <el-radio-group v-model="processForm.processStatus">
            <el-radio v-for="dict in processTargetOptions" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明" prop="remark">
          <el-input v-model="processForm.remark" type="textarea" placeholder="请填写处理说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitLoading" @click="submitProcess">确 定</el-button>
          <el-button @click="processOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="IotEvent">
import { getDeviceEvent, listDeviceEvent, processDeviceEvent } from '@/api/iot/event';
import type { DeviceEventQuery, DeviceEventVO } from '@/api/iot/event/types';
import { useRouter } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { iot_event_level, iot_event_process_status } = toRefs<any>(proxy.useDict('iot_event_level', 'iot_event_process_status'));

const eventTimeRange = ref<string[]>([]);
const detailOpen = ref(false);
const detail = ref<Partial<DeviceEventVO>>({});
const detailPayloadText = ref('');
const processOpen = ref(false);
const submitLoading = ref(false);
const processRef = ref<FormInstance>();

const queryParams = ref<DeviceEventQuery>({
  pageNum: 1,
  pageSize: 10,
  deviceCode: null,
  eventCode: null,
  eventLevel: null,
  processStatus: null,
  hasAlert: null,
  beginTime: null,
  endTime: null
});

const processForm = ref<{ eventId: string | number | null; eventCode: string; processStatus: string; remark: string }>({
  eventId: null,
  eventCode: '',
  processStatus: 'PROCESSED',
  remark: ''
});

const processRules = {
  processStatus: [{ required: true, message: '处理结果不能为空', trigger: 'change' }]
};

/** 可标记的目标状态（PENDING 是入库初态，不作为人工处理结果） */
const processTargetOptions = computed(() => (iot_event_process_status.value || []).filter((d: any) => d.value !== 'PENDING'));

const {
  loading,
  list: eventList,
  total,
  showSearch,
  getList,
  handleQuery,
  handleExport
} = useCrud<DeviceEventVO, DeviceEventQuery>({
  apis: { list: listDeviceEvent },
  idField: 'eventId',
  entityName: '设备事件',
  formRef: processRef,
  queryParams,
  initForm: () => ({}),
  exportUrl: '/iot/event/export',
  exportFileName: 'iot_device_event',
  beforeList: (query) => {
    const [beginTime, endTime] = eventTimeRange.value || [];
    query.beginTime = beginTime;
    query.endTime = endTime;
  }
});

function resetQuery() {
  eventTimeRange.value = [];
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 详情：载荷 JSON 只读展示 */
function handleDetail(row: DeviceEventVO) {
  getDeviceEvent(row.eventId).then((res) => {
    detail.value = res.data || row;
    detailPayloadText.value = prettyJson((res.data || row).payload);
    detailOpen.value = true;
  });
}

function handleProcess(row: DeviceEventVO) {
  processForm.value = { eventId: row.eventId, eventCode: row.eventCode || '', processStatus: 'PROCESSED', remark: '' };
  processOpen.value = true;
}

function submitProcess() {
  processRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    processDeviceEvent({
      eventId: processForm.value.eventId as string,
      processStatus: processForm.value.processStatus,
      remark: processForm.value.remark
    })
      .then(() => {
        proxy.$modal.msgSuccess('处理成功');
        processOpen.value = false;
        getList();
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

/** 跳转告警记录页（告警页支持按 alertId 定位） */
function goToAlert(row: DeviceEventVO) {
  router.push({ path: '/iot/alert', query: { alertId: String(row.alertId) } });
}

/** 载荷 JSON 美化（解析失败时原样展示，避免把非法 JSON 直接抛错） */
function prettyJson(text?: string) {
  if (!text) return '';
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

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
.payload-title {
  margin: 12px 0 6px;
  font-weight: 600;
}
</style>
