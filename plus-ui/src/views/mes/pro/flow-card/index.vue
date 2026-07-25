<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="流转卡编号" prop="cardNo">
              <el-input v-model="queryParams.cardNo" placeholder="请输入流转卡编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="cardStatus">
              <el-select v-model="queryParams.cardStatus" placeholder="请选择状态" clearable style="width: 100%">
                <el-option v-for="dict in mes_flow_card_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" :offset="6" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-alert v-if="woSummary" :closable="false" show-icon type="info" class="mb-2">
        <template #title>
          工单 <b>{{ woSummary.woNo }}</b> 计划数量 <b>{{ woSummary.plannedQty }}</b
          >，已建流转卡计划数量 <b>{{ woSummary.usedQty }}</b
          >，剩余可建
          <b :style="{ color: woSummary.remainingQty <= 0 ? '#f56c6c' : '#67c23a' }">{{ woSummary.remainingQty }}</b>
        </template>
      </el-alert>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="流转卡编号" prop="cardNo" />
        <el-table-column label="工单编号" prop="woNo" />
        <el-table-column label="产品名称" prop="materialName" min-width="120" show-overflow-tooltip />
        <el-table-column label="计划数量" prop="plannedQty" align="right" />
        <el-table-column label="完成数量" prop="completedQty" align="right" />
        <el-table-column label="状态" prop="cardStatus" align="center">
          <template #default="scope">
            <dict-tag :options="mes_flow_card_status" :value="scope.row.cardStatus" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
            <el-button
              link
              type="primary"
              icon="Promotion"
              @click="handleRelease(scope.row)"
              v-if="scope.row.cardStatus === 'CREATED'"
              v-hasPermi="['mes:pro:flowcard:release']"
              >下达</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="MesFlowCard">
// 注: mesWoId 保留在 queryParams(供路由 query 预过滤), 但不暴露为搜索项(内部 ID)
import { listFlowCard, listFlowCardByWoId, releaseFlowCard } from '@/api/mes/pro/flowcardApi';
import type { ProFlowCardVO, ProFlowCardQuery } from '@/api/mes/pro/flowcard';
import { getWorkOrder } from '@/api/mes/pro/workorder';
import { checkPermi } from '@/utils/permission';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_flow_card_status } = toRefs<any>(proxy?.useDict('mes_flow_card_status'));
const router = useRouter();
const route = useRoute();

const dataList = ref<ProFlowCardVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
/** 当前过滤工单的数量汇总(仅按工单过滤时展示,用于核对剩余可建数量) */
interface WoSummary {
  woNo: string;
  plannedQty: number;
  usedQty: number;
  remainingQty: number;
}
const woSummary = ref<WoSummary | null>(null);

const queryParams = ref<ProFlowCardQuery>({
  pageNum: 1,
  pageSize: 10,
  cardNo: undefined,
  mesWoId: undefined,
  cardStatus: undefined
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listFlowCard(queryParams.value);
    dataList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
  // 按工单过滤时同步刷新数量汇总(剩余可建数量)
  if (queryParams.value.mesWoId) {
    loadWoSummary(queryParams.value.mesWoId);
  } else {
    woSummary.value = null;
  }
};

/** 加载工单计划数量及已建流转卡数量汇总(用于核对剩余可建数量) */
const loadWoSummary = async (mesWoId: string | number) => {
  // 无工单查询权限时跳过(避免 403 错误提示),仅展示流转卡列表本身
  if (!checkPermi(['mes:pro:work:query'])) {
    woSummary.value = null;
    return;
  }
  try {
    const [woRes, cardsRes] = await Promise.all([getWorkOrder(mesWoId), listFlowCardByWoId(mesWoId)]);
    const wo = woRes?.data;
    const cards = cardsRes?.data || [];
    const planned = Number(wo?.plannedQty || 0);
    const used = cards.reduce((sum, c) => sum + Number(c.plannedQty || 0), 0);
    woSummary.value = {
      woNo: wo?.woNo || '',
      plannedQty: planned,
      usedQty: used,
      remainingQty: Math.max(0, planned - used)
    };
  } catch (e) {
    woSummary.value = null;
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

const handleDetail = (row: ProFlowCardVO) => {
  router.push(`/mes/pro/flow-card/detail/${row.cardId}`);
};

const handleRelease = async (row: ProFlowCardVO) => {
  await proxy?.$modal.confirm('确认下达流转卡"' + row.cardNo + '"？');
  await releaseFlowCard(row.cardId);
  proxy?.$modal.msgSuccess('下达成功');
  getList();
};

onMounted(() => {
  if (route.query.mesWoId) {
    queryParams.value.mesWoId = route.query.mesWoId as any;
  }
  getList();
});
</script>
