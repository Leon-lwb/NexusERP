<template>
  <div class="p-2">
    <!-- 顶部状态卡 -->
    <el-row :gutter="12" class="mb-2">
      <el-col :span="4">
        <el-card shadow="hover" class="status-card">
          <div class="stat-label">设备总数</div>
          <div class="stat-value">{{ statusSummary.total }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="status-card success-bg">
          <div class="stat-label">正常运行</div>
          <div class="stat-value success">{{ statusSummary.normal }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="status-card danger-bg">
          <div class="stat-label">故障中</div>
          <div class="stat-value danger">{{ statusSummary.fault }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="status-card warning-bg">
          <div class="stat-label">保养中</div>
          <div class="stat-value warning">{{ statusSummary.maintenance }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="status-card info-bg">
          <div class="stat-label">IoT 在线</div>
          <div class="stat-value primary">{{ statusSummary.iotOnline }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="status-card danger-bg">
          <div class="stat-label">活跃维修</div>
          <div class="stat-value danger">{{ activeRepairs.length }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="mb-2">
      <!-- 左侧: 设备状态总览 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>设备状态总览</span>
              <el-button link type="primary" icon="Refresh" @click="loadAll" :loading="refreshing">刷新</el-button>
            </div>
          </template>
          <el-table :data="equipmentList" size="small" max-height="360" show-overflow-tooltip>
            <el-table-column label="设备编码" prop="equipmentCode" width="110" />
            <el-table-column label="设备名称" prop="equipmentName" min-width="120" show-overflow-tooltip />
            <el-table-column label="状态" prop="status" align="center" width="80">
              <template #default="scope">
                <el-tag :type="statusTagType(scope.row.status)" size="small">{{ statusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="IoT" align="center" width="80">
              <template #default="scope">
                <el-tag v-if="scope.row.iotStatus === 'ONLINE'" type="success" size="small">在线</el-tag>
                <el-tag v-else-if="scope.row.iotStatus === 'OFFLINE'" type="info" size="small">离线</el-tag>
                <el-tag v-else-if="scope.row.iotStatus === 'UNBOUND'" type="warning" size="small">未关联</el-tag>
                <el-tag v-else type="danger" size="small">-</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="类型" prop="equipmentType" align="center" width="80">
              <template #default="scope">
                <dict-tag v-if="scope.row.equipmentType" :options="mes_equipment_type" :value="scope.row.equipmentType" size="small" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧: 活跃维修 + 低库存 -->
      <el-col :span="12">
        <el-card shadow="hover" class="mb-2">
          <template #header>
            <div class="card-header">
              <span>活跃维修单</span>
              <el-button link type="warning" icon="WarnTriangleFilled" @click="router.push('/mes/eq/repair')" v-hasPermi="['mes:eq:repair:list']"
                >查看全部</el-button
              >
            </div>
          </template>
          <el-table :data="activeRepairs" size="small" max-height="160" show-overflow-tooltip>
            <el-table-column label="维修单号" prop="repairNo" width="130" />
            <el-table-column label="设备" prop="equipmentName" min-width="100" show-overflow-tooltip />
            <el-table-column label="故障类型" prop="faultType" align="center" width="90">
              <template #default="scope">
                <dict-tag v-if="scope.row.faultType" :options="mes_fault_type" :value="scope.row.faultType" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="状态" prop="status" align="center" width="80">
              <template #default="scope">
                <dict-tag :options="mes_repair_status" :value="scope.row.status" size="small" />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="activeRepairs.length === 0" description="当前无活跃维修" :image-size="40" />
        </el-card>

        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>低库存告警</span>
              <el-button link type="warning" icon="Box" @click="router.push('/mes/eq/spare-part')" v-hasPermi="['mes:eq:spare:list']"
                >备件管理</el-button
              >
            </div>
          </template>
          <el-table :data="lowStockList" size="small" max-height="160" show-overflow-tooltip>
            <el-table-column label="备件编码" prop="spareCode" width="110" />
            <el-table-column label="备件名称" prop="spareName" min-width="120" show-overflow-tooltip />
            <el-table-column label="安全库存" prop="safetyStock" align="right" width="90" />
            <el-table-column label="当前库存" prop="currentStock" align="right" width="90">
              <template #default="scope">
                <el-tag type="danger" size="small">{{ scope.row.currentStock }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="lowStockList.length === 0" description="库存充足" :image-size="40" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="MesEqMonitor">
import { listEquipment, batchIotStatus } from '@/api/mes/eq/equipment';
import type { EqEquipmentVO } from '@/api/mes/eq/equipment';
import { listRepair } from '@/api/mes/eq/repair';
import type { EqRepairVO } from '@/api/mes/eq/repair';
import { listLowStock } from '@/api/mes/eq/spare-part';
import type { EqSparePartVO } from '@/api/mes/eq/spare-part';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_equipment_status, mes_equipment_type, mes_repair_status, mes_fault_type } = toRefs<any>(
  proxy?.useDict('mes_equipment_status', 'mes_equipment_type', 'mes_repair_status', 'mes_fault_type')
);

const refreshing = ref(false);
const equipmentList = ref<(EqEquipmentVO & { iotStatus?: string })[]>([]);
const activeRepairs = ref<EqRepairVO[]>([]);
const lowStockList = ref<EqSparePartVO[]>([]);

const statusSummary = reactive({ total: 0, normal: 0, fault: 0, maintenance: 0, scrapped: 0, iotOnline: 0 });

const statusLabel = (s?: string) =>
  (({ NORMAL: '正常', MAINTENANCE: '保养中', FAULT: '故障', SCRAPPED: '报废' }) as Record<string, string>)[s || ''] || s || '-';
const statusTagType = (s?: string) =>
  (({ NORMAL: 'success', MAINTENANCE: 'warning', FAULT: 'danger', SCRAPPED: 'info' }) as Record<string, string>)[s || ''] || 'info';

const loadAll = async () => {
  refreshing.value = true;
  try {
    // 并行加载设备 + 活跃维修(REPORTED/REPAIRING 分别按状态查, 避免全量取后再客户端过滤导致漏算) + 低库存
    const [eqRes, reportedRes, repairingRes, lowRes] = await Promise.all([
      listEquipment({ pageNum: 1, pageSize: 500 } as any),
      listRepair({ pageNum: 1, pageSize: 100, status: 'REPORTED' } as any),
      listRepair({ pageNum: 1, pageSize: 100, status: 'REPAIRING' } as any),
      listLowStock()
    ]);
    equipmentList.value = eqRes.rows || [];
    activeRepairs.value = [...(reportedRes.rows || []), ...(repairingRes.rows || [])];
    lowStockList.value = lowRes.data || [];

    // IoT 批量状态
    const ids = equipmentList.value.filter((r) => r.iotDeviceId).map((r) => r.equipmentId);
    equipmentList.value.forEach((r) => {
      if (!r.iotDeviceId) r.iotStatus = 'UNBOUND';
    });
    if (ids.length > 0) {
      try {
        const iotRes = await batchIotStatus(ids);
        const map = iotRes.data || {};
        equipmentList.value.forEach((r) => {
          if (r.iotDeviceId) r.iotStatus = map[r.equipmentId] || 'UNKNOWN';
        });
      } catch {
        equipmentList.value.forEach((r) => {
          if (r.iotDeviceId) r.iotStatus = 'UNKNOWN';
        });
      }
    }

    // 汇总
    statusSummary.total = equipmentList.value.length;
    statusSummary.normal = equipmentList.value.filter((r) => r.status === 'NORMAL').length;
    statusSummary.fault = equipmentList.value.filter((r) => r.status === 'FAULT').length;
    statusSummary.maintenance = equipmentList.value.filter((r) => r.status === 'MAINTENANCE').length;
    statusSummary.scrapped = equipmentList.value.filter((r) => r.status === 'SCRAPPED').length;
    statusSummary.iotOnline = equipmentList.value.filter((r) => r.iotStatus === 'ONLINE').length;
  } catch {
    // 请求拦截器已弹出错误提示, 此处吞掉异常避免 30s 轮询产生未处理 rejection 并保证 interval 存活
    // 保留上一次成功加载的数据(refreshing 复位, 列表不清空)
  } finally {
    refreshing.value = false;
  }
};

// 定时刷新(30秒)
let timer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  loadAll();
  timer = setInterval(loadAll, 30000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.status-card {
  text-align: center;
  padding: 4px 0;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1;
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
.stat-value.warning {
  color: var(--el-color-warning);
}
.success-bg {
  background: linear-gradient(135deg, #f0f9eb 0%, #e1f3d8 100%);
}
.danger-bg {
  background: linear-gradient(135deg, #fef0f0 0%, #fde2e2 100%);
}
.warning-bg {
  background: linear-gradient(135deg, #fdf6ec 0%, #faecd8 100%);
}
.info-bg {
  background: linear-gradient(135deg, #f4f4f5 0%, #e9e9eb 100%);
}
</style>
