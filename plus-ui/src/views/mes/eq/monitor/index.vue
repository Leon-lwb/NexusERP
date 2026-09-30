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
          <div class="stat-value danger">{{ activeRepairCount }}</div>
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
                <dict-tag :options="mes_equipment_status" :value="scope.row.status" size="small" />
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

      <!-- 右侧：活跃维修与低库存；低库存请购入口保持禁用，保留权限限制，能否开放仍以后端门禁为准。 -->
      <el-col :span="12">
        <el-card shadow="hover" class="mb-2">
          <template #header>
            <div class="card-header">
              <span>活跃维修单</span>
              <el-button
                link
                type="warning"
                icon="WarnTriangleFilled"
                @click="router.push('/mes/eq-maintain/repair')"
                v-hasPermi="['mes:eq:repair:list']"
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
              <div>
                <el-button link type="primary" icon="ShoppingCart" disabled v-hasPermi="['mes:eq:spare:purchase']">一键生成采购申请</el-button>
                <el-button link type="warning" icon="Box" @click="router.push('/mes/eq-spare/spare-part')" v-hasPermi="['mes:eq:spare:list']"
                  >备件管理</el-button
                >
              </div>
            </div>
          </template>
          <el-alert
            title="低库存一键请购已暂停：来源占用、同物料防重校验尚未接入，暂不可生成采购申请；以后端门禁为准。"
            type="warning"
            :closable="false"
            show-icon
            class="mb-2"
          />
          <el-table :data="lowStockList" size="small" max-height="160" show-overflow-tooltip>
            <el-table-column label="备件编码" prop="spareCode" width="110" />
            <el-table-column label="备件名称" prop="spareName" min-width="120" show-overflow-tooltip />
            <el-table-column label="安全库存" prop="safetyStock" align="right" width="90" />
            <el-table-column label="当前库存" prop="currentStock" align="right" width="110">
              <template #default="scope">
                <el-tag type="danger" size="small">{{ scope.row.currentStock }}</el-tag>
                <div v-if="scope.row.lowStockBasis === 'NOT_MAPPED'" class="basis-hint">未映射 ERP 物料，无法出入库</div>
                <div v-else-if="scope.row.lowStockBasis === 'ERP_UNAVAILABLE'" class="basis-hint">ERP 不可达，已降级为 MES 派生缓存口径</div>
              </template>
            </el-table-column>
            <el-table-column label="ERP可用量" prop="erpAvailableStock" align="right" width="100">
              <template #default="scope">
                <span v-if="scope.row.erpAvailableStock === null || scope.row.erpAvailableStock === undefined">-</span>
                <span v-else>{{ scope.row.erpAvailableStock }}</span>
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
import { listEquipment, batchIotStatus, getEquipmentSummary } from '@/api/mes/eq/equipment';
import type { EqEquipmentVO } from '@/api/mes/eq/equipment';
import { listRepair, getRepairSummary } from '@/api/mes/eq/repair';
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
/** 活跃维修计数(来自 summary 全量 COUNT, 不受列表 pageSize 限制) */
const activeRepairCount = ref(0);

const loadAll = async () => {
  refreshing.value = true;
  try {
    /** 统计计数走 summary(全量 COUNT, 不受 pageSize 限制); 明细列表仍按状态分页拉取用于展示 */
    const [eqRes, reportedRes, repairingRes, lowRes, eqSummaryRes, repairSummaryRes] = await Promise.all([
      listEquipment({ pageNum: 1, pageSize: 500 } as any),
      listRepair({ pageNum: 1, pageSize: 100, status: 'REPORTED' } as any),
      listRepair({ pageNum: 1, pageSize: 100, status: 'REPAIRING' } as any),
      listLowStock(),
      getEquipmentSummary(),
      getRepairSummary()
    ]);
    equipmentList.value = eqRes.rows || [];
    activeRepairs.value = [...(reportedRes.rows || []), ...(repairingRes.rows || [])];
    lowStockList.value = lowRes.data || [];

    /** IoT 批量状态 */
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

    /** 设备/维修状态计数用 summary(全量 COUNT, 不受列表 pageSize 限制, 避免 >500 台漏算) */
    const es = eqSummaryRes.data || {};
    statusSummary.total = es.total || 0;
    statusSummary.normal = es.normal || 0;
    statusSummary.fault = es.fault || 0;
    statusSummary.maintenance = es.maintenance || 0;
    statusSummary.scrapped = es.scrapped || 0;
    activeRepairCount.value = (repairSummaryRes.data?.active as number) || 0;
    statusSummary.iotOnline = equipmentList.value.filter((r) => r.iotStatus === 'ONLINE').length;
  } catch {
    /** 请求拦截器已弹出错误提示，此处捕获异常，避免 30 秒轮询产生未处理的 Promise 拒绝错误，并保持定时器运行。 */
    /** 保留上一次成功加载的数据(refreshing 复位, 列表不清空) */
  } finally {
    refreshing.value = false;
  }
};

/** 定时刷新(30秒) */
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
.basis-hint {
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.3;
  white-space: normal;
  text-align: left;
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
