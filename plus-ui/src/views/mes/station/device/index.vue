<template>
  <div class="p-2">
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

    <el-card>
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button icon="Refresh" @click="getList">刷新</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
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
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="StationDevice">
import { listStationDevice, stationDeviceStats } from '@/api/mes/station/device';
import type { StationDeviceVO, StationDeviceStats } from '@/api/mes/station/device';

const dataList = ref<StationDeviceVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const stats = ref<StationDeviceStats>({ total: 0, onlineCount: 0, offlineCount: 0 });

const queryParams = ref({
  pageNum: 1,
  pageSize: 10
});

/** 心跳距今时长(离线诊断用) */
const heartbeatAge = (lastHeartbeat: string): string => {
  const ms = Date.now() - new Date(lastHeartbeat.replace(/-/g, '/')).getTime();
  if (Number.isNaN(ms) || ms < 0) return '-';
  if (ms < 60_000) return `${Math.floor(ms / 1000)}秒前`;
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}分钟前`;
  return `${Math.floor(ms / 3_600_000)}小时前`;
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listStationDevice(queryParams.value);
    dataList.value = res.rows ?? [];
    total.value = res.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const getStats = async () => {
  const res = await stationDeviceStats();
  stats.value = res.data;
};

onMounted(() => {
  getList();
  getStats();
});
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
