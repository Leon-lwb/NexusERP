<template>
  <div class="app-container">
    <el-alert type="info" :closable="false" style="margin-bottom: 12px">
      <template #title>
        推演口径: 余额 = 当前可用库存(已扣预留) + 累计在途到货 − 累计毛需求(MRP未发料) − 安全库存; 首次余额 &lt; 0 的日期即最早缺料日。结果缓存 30
        分钟, 可点"重新推演"强制刷新。
      </template>
    </el-alert>

    <el-row :gutter="16" class="mb8">
      <el-col :span="6">
        <el-select v-model="days" style="width: 160px" @change="getList">
          <el-option label="未来 14 天" :value="14" />
          <el-option label="未来 30 天" :value="30" />
          <el-option label="未来 60 天" :value="60" />
          <el-option label="未来 90 天" :value="90" />
        </el-select>
      </el-col>
      <el-col :span="4">
        <el-switch v-model="onlyRisk" active-text="只看有风险" @change="getList" />
      </el-col>
      <el-col :span="14" style="text-align: right">
        <el-button icon="Refresh" @click="handleRefresh" v-hasPermi="['prd:shortage:query']">重新推演</el-button>
        <el-button type="primary" icon="MagicStick" @click="handleDeliveryDecision" v-hasPermi="['ai:decision:run']">交付风险分析</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="filteredList" show-overflow-tooltip :row-class-name="rowClassName">
      <el-table-column label="物料编码" prop="materialCode" min-width="120" />
      <el-table-column label="物料名称" prop="materialName" min-width="140" />
      <el-table-column label="可用库存" prop="stockQty" align="right" width="100" />
      <el-table-column label="安全库存" prop="safetyStock" align="right" width="100" />
      <el-table-column label="期内需求" prop="totalDemand" align="right" width="100" />
      <el-table-column label="在途量" prop="onOrderQty" align="right" width="100" />
      <el-table-column label="最早缺料日" width="120" align="center">
        <template #default="scope">
          <span v-if="scope.row.earliestShortageDate" :class="dateClass(scope.row)">{{ scope.row.earliestShortageDate }}</span>
          <span v-else style="color: var(--el-color-success)">无风险</span>
        </template>
      </el-table-column>
      <el-table-column label="剩余天数" width="100" align="center">
        <template #default="scope">
          <template v-if="scope.row.daysUntilShortage != null">
            <el-tag v-if="scope.row.daysUntilShortage < 0" type="danger" size="small">已缺料</el-tag>
            <span v-else :class="dateClass(scope.row)">{{ scope.row.daysUntilShortage }} 天</span>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="最大缺口" prop="maxGap" align="right" width="100">
        <template #default="scope">
          <span :style="Number(scope.row.maxGap) > 0 ? 'color: var(--el-color-danger); font-weight: bold' : ''">{{ scope.row.maxGap ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="缺口天数" prop="gapDays" align="center" width="80" />
      <el-table-column label="建议动作" min-width="200">
        <template #default="scope">
          <el-tag v-if="scope.row.suggestion === 'URGE'" type="warning" size="small">催货</el-tag>
          <el-tag v-else-if="scope.row.suggestion === 'PURCHASE'" type="danger" size="small">提前采购</el-tag>
          <el-tag v-else-if="scope.row.suggestion === 'ADJUST_SAFETY'" type="info" size="small">调安全库存</el-tag>
          <span style="margin-left: 8px">{{ scope.row.suggestionText }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="190" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Promotion" @click="handleUrge(scope.row)" v-if="scope.row.hasRisk" v-hasPermi="['prd:shortage:query']"
            >去催货</el-button
          >
          <el-button link type="primary" icon="MagicStick" @click="handleAiAnalyze" v-hasPermi="['prd:shortage:query']">AI 文字解读</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- AI 助手抽屉(缺料风险分析入口) -->
    <AiChatDrawer ref="aiChatRef" />
  </div>
</template>

<script setup lang="ts" name="ErpPrdShortage">
import { predictShortage, predictShortageRefresh } from '@/api/erp/prd/shortage';
import type { PrdShortageRiskVO } from '@/api/erp/prd/shortage';
import AiChatDrawer from '@/components/ai/AiChatDrawer.vue';
import { runDeliveryRisk } from '@/api/ai/decision';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(false);
const days = ref(30);
const onlyRisk = ref(true);
const list = ref<PrdShortageRiskVO[]>([]);
const aiChatRef = ref<InstanceType<typeof AiChatDrawer>>();

const filteredList = computed(() => (onlyRisk.value ? list.value.filter((r) => r.hasRisk) : list.value));

/** 行底色: 红=7天内缺料, 橙=30天内 */
function rowClassName({ row }: { row: PrdShortageRiskVO }): string {
  if (!row.hasRisk || row.daysUntilShortage == null) return '';
  if (row.daysUntilShortage < 7) return 'risk-row-danger';
  if (row.daysUntilShortage < 30) return 'risk-row-warning';
  return '';
}

function dateClass(row: PrdShortageRiskVO): string {
  if (row.daysUntilShortage == null) return '';
  if (row.daysUntilShortage < 7) return 'text-danger';
  if (row.daysUntilShortage < 30) return 'text-warning';
  return '';
}

async function getList() {
  loading.value = true;
  try {
    const res = await predictShortage(days.value);
    list.value = res.data || [];
  } finally {
    loading.value = false;
  }
}

/** 强制刷新推演(跳过缓存) */
async function handleRefresh() {
  loading.value = true;
  try {
    const res = await predictShortageRefresh(days.value);
    list.value = res.data || [];
    proxy?.$modal.msgSuccess('重新推演完成');
  } finally {
    loading.value = false;
  }
}

/** 去催货: 跳采购订单页(带物料编码搜索) */
function handleUrge(row: PrdShortageRiskVO) {
  const route = router.resolve({ path: '/puExec/order', query: { materialCode: row.materialCode } });
  if (route.href) {
    window.open(route.href, '_self');
  }
}

async function handleDeliveryDecision() {
  try {
    const res = await runDeliveryRisk({ bizType: 'SHORTAGE_HORIZON', bizId: undefined, horizonDays: days.value, triggerType: 'MANUAL' });
    await router.push({ path: '/ai/decision/detail', query: { runId: String(res.data.runId) } });
  } catch {
    proxy?.$modal.msgError('交付风险分析未能启动，请检查权限或稍后重试');
  }
}

/** AI 分析: 唤起全局 AI 助手预填分析指令(Agent 调缺料风险工具作答) */
function handleAiAnalyze() {
  const prompt =
    `请基于缺料风险预测工具(erp_shortage_risk)分析未来 ${days.value} 天的物料短缺情况：` +
    `1) 哪些物料最早缺料、缺口多大；2) 在途采购能否覆盖、是否需要催货；` +
    `3) 结合采购提前期给出建议(提前下达采购/催货/调整安全库存)。`;
  aiChatRef.value?.open(prompt);
}

const router = useRouter();

onMounted(() => {
  getList();
});
</script>

<style scoped>
:deep(.risk-row-danger) {
  background-color: var(--el-color-danger-light-9);
}
:deep(.risk-row-warning) {
  background-color: var(--el-color-warning-light-9);
}
.text-danger {
  color: var(--el-color-danger);
  font-weight: bold;
}
.text-warning {
  color: var(--el-color-warning);
  font-weight: bold;
}
</style>
