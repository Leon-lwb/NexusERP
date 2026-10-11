<template>
  <div class="app-container">
    <!-- 汇总卡片区 -->
    <el-row :gutter="12" class="mb-2">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">注册设备</div>
            <div class="stat-value primary">{{ stats.total ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">在线</div>
            <div class="stat-value success">{{ stats.onlineCount ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">离线(心跳超3分钟)</div>
            <div class="stat-value danger">{{ stats.offlineCount ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button icon="Refresh" @click="getList()">刷新</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="设备编码" prop="deviceCode" min-width="140" />
      <el-table-column label="在线状态" align="center" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.online ? 'success' : 'danger'" size="small">
            {{ scope.row.online ? '在线' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="绑定工位" prop="stationCode" min-width="180">
        <template #default="scope">{{ scope.row.stationCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="工序编码" prop="processCode" align="center" width="120">
        <template #default="scope">{{ scope.row.processCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="终端版本" prop="appVersion" align="center" width="100">
        <template #default="scope">{{ scope.row.appVersion || '-' }}</template>
      </el-table-column>
      <el-table-column label="当前操作员" align="center" width="120">
        <template #default="scope">
          <span v-if="scope.row.operatorName">{{ scope.row.operatorName }}</span>
          <span v-else-if="scope.row.operatorCode">{{ scope.row.operatorCode }}</span>
          <span v-else class="text-muted">无人上岗</span>
        </template>
      </el-table-column>
      <el-table-column label="最后心跳" prop="lastHeartbeat" width="170">
        <template #default="scope">{{ scope.row.lastHeartbeat || '-' }}</template>
      </el-table-column>
      <el-table-column label="心跳时延" align="center" width="120">
        <template #default="scope">
          <span v-if="scope.row.lastHeartbeat">{{ heartbeatAge(scope.row.lastHeartbeat) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Link" @click="openLinkage(scope.row)">工作中心设备</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 工作中心设备联动弹窗(M3): 工位工序 → bd_process 默认工作中心 → eq_equipment 反查 -->
    <el-dialog v-model="linkageVisible" :title="`工作中心设备 - ${linkageData.deviceCode || ''}`" width="800px" append-to-body>
      <div v-loading="linkageLoading">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="工序">
            {{ linkageData.processCode ? `${linkageData.processCode} ${linkageData.processName || ''}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="工作中心">{{ linkageWorkstationText }}</el-descriptions-item>
          <el-descriptions-item label="所属车间">{{ linkageData.workstation?.workshopName || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="linkageData.message" :title="linkageData.message" type="info" show-icon :closable="false" style="margin-top: 12px" />
        <el-table :data="linkageData.equipments" border size="small" style="margin-top: 12px">
          <el-table-column label="设备编码" prop="equipmentCode" width="130" />
          <el-table-column label="设备名称" prop="equipmentName" min-width="140" show-overflow-tooltip />
          <el-table-column label="类型" align="center" width="100">
            <template #default="scope">
              <dict-tag v-if="scope.row.equipmentType" :options="mes_equipment_type" :value="scope.row.equipmentType" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="90">
            <template #default="scope">
              <dict-tag v-if="scope.row.status" :options="mes_equipment_status" :value="scope.row.status" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="型号" prop="model" min-width="100">
            <template #default="scope">{{ scope.row.model || '-' }}</template>
          </el-table-column>
          <el-table-column label="安装位置" prop="location" min-width="120" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.location || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="StationDevice">
import { listStationDevice, stationDeviceStats, stationDeviceEquipments } from '@/api/mes/station/device';
import type { StationDeviceVO, StationDeviceStats, StationDeviceLinkageVO } from '@/api/mes/station/device';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_equipment_type, mes_equipment_status } = toRefs<any>(proxy?.useDict('mes_equipment_type', 'mes_equipment_status'));

// 工位终端台账为只读列表页（无新增/修改/删除），以下占位仅为满足 useCrud 的 API 签名，不会被调用
const formRef = ref<FormInstance>();
const readonlyApi = () => Promise.resolve({ data: {} as any });

const stats = ref<StationDeviceStats>({ total: 0, onlineCount: 0, offlineCount: 0 });

const queryParams = ref({
  pageNum: 1,
  pageSize: 10
});

const {
  loading,
  list: dataList,
  total,
  showSearch,
  getList
} = useCrud<StationDeviceVO, any>({
  apis: { list: listStationDevice, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: readonlyApi },
  idField: 'deviceId',
  entityName: '工位终端',
  formRef,
  queryParams,
  initForm: () => ({})
});

/** 工作中心设备联动弹窗(M3) */
const linkageVisible = ref(false);
const linkageLoading = ref(false);
const linkageData = ref<StationDeviceLinkageVO>({} as StationDeviceLinkageVO);

const linkageWorkstationText = computed(() => {
  const ws = linkageData.value.workstation;
  const text = [ws?.workstationCode, ws?.workstationName].filter(Boolean).join(' - ');
  return text || '-';
});

const openLinkage = async (row: StationDeviceVO) => {
  linkageVisible.value = true;
  linkageLoading.value = true;
  try {
    const res = await stationDeviceEquipments(row.deviceId);
    linkageData.value = res.data;
  } catch {
    /* 请求拦截器已提示 */
  } finally {
    linkageLoading.value = false;
  }
};

/** 心跳距今时长(离线诊断用) */
const heartbeatAge = (lastHeartbeat: string): string => {
  const ms = Date.now() - new Date(lastHeartbeat.replace(/-/g, '/')).getTime();
  if (Number.isNaN(ms) || ms < 0) return '-';
  if (ms < 60_000) return `${Math.floor(ms / 1000)}秒前`;
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}分钟前`;
  return `${Math.floor(ms / 3_600_000)}小时前`;
};

const getStats = async () => {
  const res = await stationDeviceStats();
  stats.value = res.data;
};

getList();
getStats();
</script>

<style scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px 8px;
}
.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.stat-value {
  font-size: 26px;
  font-weight: 600;
}
.stat-value.primary {
  color: var(--el-color-primary);
}
.stat-value.success {
  color: var(--el-color-success);
}
.stat-value.danger {
  color: var(--el-color-danger);
}
</style>
