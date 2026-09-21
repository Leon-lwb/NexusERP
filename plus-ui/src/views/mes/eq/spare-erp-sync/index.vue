<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="同步状态" prop="syncStatus">
            <el-select v-model="queryParams.syncStatus" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="s in SYNC_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Refresh" :loading="loading" @click="getList()">刷新</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="pagedList" show-overflow-tooltip>
      <el-table-column label="备件编码" prop="spareCode" width="120">
        <template #default="scope">{{ scope.row.spareCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="业务幂等键" min-width="210" :show-overflow-tooltip="false">
        <template #default="scope">
          <span class="biz-key" :title="scope.row.bizKey">{{ scope.row.bizKey || '-' }}</span>
          <el-button
            v-if="scope.row.bizKey"
            link
            type="primary"
            icon="DocumentCopy"
            v-copyText="scope.row.bizKey"
            v-copyText:callback="onCopySuccess"
          />
        </template>
      </el-table-column>
      <el-table-column label="变动类型" align="center" width="100">
        <template #default="scope">{{ txnTypeLabel(scope.row.transactionType) }}</template>
      </el-table-column>
      <el-table-column label="数量" prop="quantity" align="right" width="100">
        <template #default="scope">{{ scope.row.quantity ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="同步状态" align="center" width="100">
        <template #default="scope">
          <el-tag :type="syncTagType(scope.row.syncStatus)" size="small">{{ syncStatusLabel(scope.row.syncStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="重投次数" prop="retryCount" align="center" width="90">
        <template #default="scope">{{ scope.row.retryCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="最近投递时间" prop="lastPublishTime" width="160">
        <template #default="scope">{{ scope.row.lastPublishTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="ERP过账后可用量" prop="erpAvailableAfter" align="right" width="150">
        <template #default="scope">{{ scope.row.erpAvailableAfter ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="失败原因" prop="lastError" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          <span :class="{ 'error-text': !!scope.row.lastError }">{{ scope.row.lastError || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="90" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="RefreshRight" @click="handleRetry(scope.row)" v-hasPermi="['mes:eq:spare:stock']">重投</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" />
  </div>
</template>

<script setup lang="ts" name="MesEqSpareErpSync">
import { erpSyncList, retryErpSync } from '@/api/mes/eq/spare-part';
import type { EqSpareErpSyncVO } from '@/api/mes/eq/spare-part';
import type { FormInstance } from 'element-plus';

/**
 * 备件→ERP 过账台账（MES-EQ-04「失败可见」的人工兜底入口）
 *
 * <p>台账为 R&lt;List&gt; 一次性返回（后端无分页参数），这里按本地分页切片展示。
 * 同步状态/变动类型用本地文案映射，不依赖字典同步（字典未种子时也能正常显示）。</p>
 */
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const queryRef = ref<FormInstance>();
const showSearch = ref(true);
const loading = ref(false);
const dataList = ref<EqSpareErpSyncVO[]>([]);

const queryParams = ref<{ pageNum: number; pageSize: number; syncStatus?: string }>({
  pageNum: 1,
  pageSize: 10,
  syncStatus: undefined
});

const SYNC_STATUS_OPTIONS = [
  { value: 'PENDING', label: '待投递' },
  { value: 'SENT', label: '已投递' },
  { value: 'SYNCED', label: '已同步' },
  { value: 'FAILED', label: '失败' }
];

const SYNC_STATUS_LABELS: Record<string, string> = {
  PENDING: '待投递',
  SENT: '已投递',
  SYNCED: '已同步',
  FAILED: '失败'
};

const TXN_TYPE_LABELS: Record<string, string> = {
  IN: '入库',
  OUT: '出库',
  REPAIR_USE: '维修领用',
  RETURN: '退库'
};

const total = computed(() => dataList.value.length);
const pagedList = computed(() => {
  const start = (queryParams.value.pageNum - 1) * queryParams.value.pageSize;
  return dataList.value.slice(start, start + queryParams.value.pageSize);
});

const syncStatusLabel = (status?: string) => (status ? SYNC_STATUS_LABELS[status] || status : '-');
const syncTagType = (status?: string) => {
  switch (status) {
    case 'SYNCED':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'SENT':
      return 'primary';
    default:
      return 'warning';
  }
};
const txnTypeLabel = (type?: string) => (type ? TXN_TYPE_LABELS[type] || type : '-');
const onCopySuccess = () => proxy?.$modal.msgSuccess('复制成功');

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await erpSyncList(queryParams.value.syncStatus);
    dataList.value = res?.data || [];
    // 过滤后行数变少时回退页码，避免停留在空白页
    const maxPage = Math.max(1, Math.ceil(dataList.value.length / queryParams.value.pageSize));
    if (queryParams.value.pageNum > maxPage) queryParams.value.pageNum = maxPage;
  } catch (e) {
    console.error('备件 ERP 过账台账加载失败', e);
    dataList.value = [];
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  return getList();
};

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

/** 重投：后端返回 false 表示仍未确认 ERP 落账 */
const handleRetry = async (row: EqSpareErpSyncVO) => {
  try {
    await proxy?.$modal.confirm(`确认重投备件「${row.spareCode || row.spareId}」的 ERP 过账（幂等键 ${row.bizKey || '-'}）？`);
  } catch {
    return;
  }
  try {
    const res: any = await retryErpSync(row.syncId);
    if (res?.data === true) {
      proxy?.$modal.msgSuccess('已确认 ERP 落账');
    } else {
      proxy?.$modal.msgWarning('仍未确认 ERP 落账，请检查失败原因');
    }
    getList();
  } catch (e: any) {
    // 接口已关闭统一错误 toast，这里原样展示后端信息
    proxy?.$modal.msgError(e?.message || '重投失败');
  }
};

getList();
</script>

<style scoped>
.biz-key {
  display: inline-block;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.error-text {
  color: #f56c6c;
}
</style>
