<template>
  <div class="p-2">
    <!-- 统计卡片区 -->
    <el-row :gutter="12" class="mb-2">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">活跃呼叫</div>
            <div class="stat-value danger">{{ stats.activeCount ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">质量异常</div>
            <div class="stat-value danger">{{ stats.typeCount?.QUALITY ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">物料短缺</div>
            <div class="stat-value warning">{{ stats.typeCount?.MATERIAL ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-label">设备故障</div>
            <div class="stat-value primary">{{ stats.typeCount?.EQUIPMENT ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="安灯编号" prop="andonNo">
              <el-input v-model="queryParams.andonNo" placeholder="请输入安灯编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="呼叫类型" prop="callType">
              <el-select v-model="queryParams.callType" placeholder="请选择类型" clearable style="width: 100%">
                <el-option label="质量异常" value="QUALITY" />
                <el-option label="物料短缺" value="MATERIAL" />
                <el-option label="设备故障" value="EQUIPMENT" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="andonStatus">
              <el-select v-model="queryParams.andonStatus" placeholder="请选择状态" clearable style="width: 100%">
                <el-option label="已呼叫" value="CALLED" />
                <el-option label="已响应" value="RESPONDED" />
                <el-option label="已解决" value="RESOLVED" />
                <el-option label="已取消" value="CANCELLED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Bell" @click="handleOpenCall" v-hasPermi="['mes:pro:andon:call']">发起呼叫</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="安灯编号" prop="andonNo" />
        <el-table-column label="工单编号" prop="woNo">
          <template #default="scope">{{ scope.row.woNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="流转卡" prop="cardNo">
          <template #default="scope">{{ scope.row.cardNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="呼叫类型" prop="callType" align="center">
          <template #default="scope">
            <el-tag :type="callTypeTagType(scope.row.callType)">{{ callTypeLabel(scope.row.callType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="呼叫人" prop="callerName">
          <template #default="scope">{{ scope.row.callerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="来源" prop="source" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.source === 'IoT' ? 'primary' : 'info'" size="small">
              {{ scope.row.source === 'IoT' ? 'IoT' : '手动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="持续(分)" prop="durationMinutes" align="right">
          <template #default="scope">{{ scope.row.durationMinutes ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="呼叫时间" prop="callTime" />
        <el-table-column label="状态" prop="andonStatus" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.andonStatus)">{{ statusLabel(scope.row.andonStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="呼叫原因" prop="callReason" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button
              link
              type="warning"
              icon="Headset"
              @click="handleRespond(scope.row)"
              v-if="scope.row.andonStatus === 'CALLED'"
              v-hasPermi="['mes:pro:andon:respond']"
              >响应</el-button
            >
            <el-button
              link
              type="success"
              icon="CircleCheck"
              @click="handleResolve(scope.row)"
              v-if="scope.row.andonStatus === 'RESPONDED'"
              v-hasPermi="['mes:pro:andon:resolve']"
              >解决</el-button
            >
            <el-button
              link
              type="danger"
              icon="Close"
              @click="handleCancel(scope.row)"
              v-if="scope.row.andonStatus === 'CALLED'"
              v-hasPermi="['mes:pro:andon:cancel']"
              >取消</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 发起呼叫对话框 -->
    <el-dialog v-model="callDialog.visible" title="发起安灯呼叫" width="520px" append-to-body>
      <el-form :model="callForm" label-width="90px">
        <el-form-item label="呼叫类型" required>
          <el-select v-model="callForm.callType" placeholder="请选择类型" style="width: 100%">
            <el-option label="质量异常" value="QUALITY" />
            <el-option label="物料短缺" value="MATERIAL" />
            <el-option label="设备故障" value="EQUIPMENT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单ID">
          <el-input v-model.number="callForm.mesWoId" placeholder="可选, 关联MES工单ID" clearable />
        </el-form-item>
        <el-form-item label="流转卡ID">
          <el-input v-model.number="callForm.cardId" placeholder="可选, 关联流转卡ID" clearable />
        </el-form-item>
        <el-form-item label="呼叫原因">
          <el-input v-model="callForm.callReason" type="textarea" :rows="3" placeholder="请输入呼叫原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="callDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCall">确认呼叫</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesAndon">
// 技术债: 缺 mes_andon_status / mes_andon_call_type 字典, 安灯状态与呼叫类型暂用硬编码 el-tag + 本地 map, 待补字典后改 dict-tag
import { ElNotification } from 'element-plus';
import { listAndon, andonStats, callAndon, respondAndon, resolveAndon, cancelAndon } from '@/api/mes/pro/andon';
import type { ProAndonVO, ProAndonQuery, AndonStats } from '@/api/mes/pro/andon';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<ProAndonVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const stats = ref<AndonStats>({ activeCount: 0, statusCount: {}, typeCount: {} });

const queryParams = ref<ProAndonQuery>({
  pageNum: 1,
  pageSize: 10,
  andonNo: undefined,
  callType: undefined,
  andonStatus: undefined
});

const callDialog = reactive({ visible: false });
const callForm = reactive<{ callType: string; mesWoId?: number; cardId?: number; callReason?: string }>({
  callType: '',
  mesWoId: undefined,
  cardId: undefined,
  callReason: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listAndon(queryParams.value);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const loadStats = async () => {
  try {
    const res = await andonStats();
    stats.value = res.data;
  } catch {
    /* 忽略 */
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};

const handleOpenCall = () => {
  callForm.callType = '';
  callForm.mesWoId = undefined;
  callForm.cardId = undefined;
  callForm.callReason = '';
  callDialog.visible = true;
};

const submitCall = async () => {
  if (!callForm.callType) {
    proxy?.$modal.msgWarning('请选择呼叫类型');
    return;
  }
  await callAndon(callForm.mesWoId, callForm.cardId, undefined, callForm.callType, callForm.callReason);
  proxy?.$modal.msgSuccess('呼叫成功');
  callDialog.visible = false;
  getList();
  loadStats();
};

const handleRespond = async (row: ProAndonVO) => {
  await proxy?.$modal.confirm('确认响应安灯"' + row.andonNo + '"？');
  await respondAndon(row.andonId);
  proxy?.$modal.msgSuccess('响应成功');
  getList();
  loadStats();
};

const handleResolve = async (row: ProAndonVO) => {
  const { value: remark } = await proxy?.$modal.prompt('请输入解决备注(可选):', '解决 - ' + row.andonNo);
  await resolveAndon(row.andonId, remark || undefined);
  proxy?.$modal.msgSuccess('解决成功');
  getList();
  loadStats();
};

const handleCancel = async (row: ProAndonVO) => {
  await proxy?.$modal.confirm('确认取消安灯"' + row.andonNo + '"？');
  await cancelAndon(row.andonId);
  proxy?.$modal.msgSuccess('取消成功');
  getList();
  loadStats();
};

const callTypeLabel = (t: string) => ({ QUALITY: '质量异常', MATERIAL: '物料短缺', EQUIPMENT: '设备故障', OTHER: '其他' })[t] || t;
const callTypeTagType = (t: string) => ({ QUALITY: 'danger', MATERIAL: 'warning', EQUIPMENT: 'primary', OTHER: 'info' })[t] || 'info';
const statusLabel = (s: string) => ({ CALLED: '已呼叫', RESPONDED: '已响应', RESOLVED: '已解决', CANCELLED: '已取消' })[s] || s;
const statusTagType = (s: string) => ({ CALLED: 'danger', RESPONDED: 'warning', RESOLVED: 'success', CANCELLED: 'info' })[s] || 'info';

// SSE 实时刷新: 监听 mes-andon CustomEvent(由 utils/sse.ts 分发)
const handleAndonEvent = (e: Event) => {
  const detail = (e as CustomEvent).detail || {};
  getList();
  loadStats();
  // 新呼叫弹通知, 避免操作员错过
  if (detail.action === 'CALLED' && detail.data) {
    const d = detail.data;
    ElNotification({
      title: '安灯呼叫: ' + callTypeLabel(d.callType),
      message: d.callReason || d.andonNo,
      type: 'warning',
      duration: 6000
    });
  }
};

onMounted(() => {
  getList();
  loadStats();
  window.addEventListener('mes-andon', handleAndonEvent as EventListener);
});

onUnmounted(() => {
  window.removeEventListener('mes-andon', handleAndonEvent as EventListener);
});
</script>

<style scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
}
.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 32px;
  font-weight: 600;
  line-height: 1;
}
.stat-value.danger {
  color: var(--el-color-danger);
}
.stat-value.warning {
  color: var(--el-color-warning);
}
.stat-value.primary {
  color: var(--el-color-primary);
}
</style>

