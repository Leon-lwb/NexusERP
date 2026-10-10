<template>
  <div class="app-container">
    <el-alert
      class="mb8"
      type="info"
      show-icon
      :closable="false"
      title="已投递（SENT）不等于 ERP 已落账；只有已确认 ERP 落账（SYNCED）可作同步完成依据。重投使用原业务幂等键，不新建库存流水。"
    />
    <el-alert
      v-if="loadError"
      class="mb8"
      type="error"
      show-icon
      :closable="false"
      title="过账台账加载失败，请刷新重试；当前空列表不代表没有过账记录。"
    />
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="同步状态" prop="syncStatus">
            <el-select v-model="queryParams.syncStatus" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="s in SPARE_SYNC_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
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

    <el-table
      v-loading="loading"
      :data="pagedList"
      row-key="syncId"
      show-overflow-tooltip
      :empty-text="loadError ? '台账加载失败，请刷新重试' : '暂无过账记录，或当前状态筛选无结果'"
    >
      <el-table-column label="备件编码" prop="spareCode" width="120">
        <template #default="scope">{{ scope.row.spareCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="MES 流水 ID" prop="logId" min-width="190">
        <template #default="scope">{{ scope.row.logId || '-' }}</template>
      </el-table-column>
      <el-table-column label="来源单据" min-width="185">
        <template #default="scope">
          {{ refTypeLabel(scope.row.refType) }}
          <div v-if="scope.row.refId" class="row-note">{{ scope.row.refId }}</div>
        </template>
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
        <template #default="scope">{{ formatQuantity(scope.row.quantity) }}</template>
      </el-table-column>
      <el-table-column label="同步状态" align="center" width="165">
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
      <el-table-column label="确认时 ERP 可用量" prop="erpAvailableAfter" align="right" width="150">
        <template #default="scope">{{ scope.row.syncStatus === 'SYNCED' ? formatQuantity(scope.row.erpAvailableAfter) : '待落账确认' }}</template>
      </el-table-column>
      <el-table-column label="失败原因" prop="lastError" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          <span :class="{ 'error-text': !!scope.row.lastError }">{{ scope.row.lastError || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="90" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="RefreshRight"
            :loading="retryingIds.includes(scope.row.syncId)"
            :disabled="retryingIds.includes(scope.row.syncId) || scope.row.syncStatus === 'SYNCED'"
            @click="handleRetry(scope.row)"
            v-hasPermi="['mes:eq:spare:stock']"
            >重投</el-button
          >
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
import { formatQuantity, txnTypeLabel, refTypeLabel, syncStatusLabel, syncTagType, SPARE_SYNC_STATUS_OPTIONS } from '@/utils/mesSpareDisplay';

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

const loadError = ref(false);
const retryingIds = ref<string[]>([]);

const total = computed(() => dataList.value.length);
const pagedList = computed(() => {
  const start = (queryParams.value.pageNum - 1) * queryParams.value.pageSize;
  return dataList.value.slice(start, start + queryParams.value.pageSize);
});

const onCopySuccess = () => proxy?.$modal.msgSuccess('复制成功');

const getList = async () => {
  loading.value = true;
  loadError.value = false;
  try {
    const res: any = await erpSyncList(queryParams.value.syncStatus);
    dataList.value = Array.isArray(res?.data) ? res.data : [];
    // 过滤后行数变少时回退页码，避免停留在空白页
    const maxPage = Math.max(1, Math.ceil(dataList.value.length / queryParams.value.pageSize));
    if (queryParams.value.pageNum > maxPage) queryParams.value.pageNum = maxPage;
  } catch (e) {
    console.error('备件 ERP 过账台账加载失败', e);
    loadError.value = true;
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
  if (!row.syncId || row.syncStatus === 'SYNCED' || retryingIds.value.includes(row.syncId)) return;
  retryingIds.value.push(row.syncId);
  try {
    try {
      await proxy?.$modal.confirm(`确认使用原幂等键重投备件「${row.spareCode || row.spareId}」的 ERP 过账（${row.bizKey || '-'}）？`);
    } catch {
      return;
    }
    const res = await retryErpSync(row.syncId);
    if (res?.data === true) proxy?.$modal.msgSuccess('已确认 ERP 落账');
    else proxy?.$modal.msgWarning('仍未确认 ERP 落账，请刷新并核对失败原因，不要另建业务单重发');
    await getList();
  } catch (e: any) {
    // 接口已关闭统一错误 toast，仅在业务调用失败时展示后端信息。
    proxy?.$modal.msgError(e?.message || '重投失败');
  } finally {
    retryingIds.value = retryingIds.value.filter((id) => id !== row.syncId);
  }
};

getList();
</script>

<style scoped>
.row-note {
  color: #909399;
  font-size: 12px;
}
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
