<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="消息ID" prop="messageId">
              <el-input v-model="queryParams.messageId" placeholder="请输入消息ID" clearable style="width: 220px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="路由键" prop="routingKey">
              <el-input v-model="queryParams.routingKey" placeholder="请输入路由键" clearable style="width: 220px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 140px">
                <el-option label="待处理" value="PENDING" />
                <el-option label="已重投" value="RESENT" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </transition>

    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <right-toolbar v-model:show-search="showSearch" @query-table="getList" />
        </el-row>
      </template>

      <el-table v-loading="loading" :data="deadLetterList">
        <el-table-column label="消息ID" prop="messageId" width="260" show-overflow-tooltip />
        <el-table-column label="交换机" prop="exchange" min-width="160" show-overflow-tooltip />
        <el-table-column label="路由键" prop="routingKey" min-width="180" show-overflow-tooltip />
        <el-table-column label="消息类型" prop="messageType" min-width="220" show-overflow-tooltip />
        <el-table-column label="死信原因" prop="errorMessage" min-width="120" show-overflow-tooltip />
        <el-table-column label="重投次数" prop="retryCount" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'PENDING' ? 'danger' : 'success'">
              {{ scope.row.status === 'PENDING' ? '待处理' : '已重投' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入死信时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
            <el-button
              v-if="scope.row.status === 'PENDING'"
              link
              type="warning"
              icon="RefreshRight"
              @click="handleRedeliver(scope.row)"
              v-hasPermi="['monitor:dead-letter:redeliver']"
            >重投</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['monitor:dead-letter:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <!-- 消息体详情 -->
    <el-dialog v-model="detailVisible" title="死信消息详情" width="760px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="消息ID" :span="2">{{ detail?.messageId }}</el-descriptions-item>
        <el-descriptions-item label="交换机">{{ detail?.exchange }}</el-descriptions-item>
        <el-descriptions-item label="路由键">{{ detail?.routingKey }}</el-descriptions-item>
        <el-descriptions-item label="消息类型" :span="2">{{ detail?.messageType || '(无类型信息, 历史数据)' }}</el-descriptions-item>
        <el-descriptions-item label="死信原因" :span="2">{{ detail?.errorMessage || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-input v-model="detailBody" type="textarea" :rows="14" readonly class="mt10" />
    </el-dialog>
  </div>
</template>

<script setup name="DeadLetter" lang="ts">
import { listDeadLetter, getDeadLetter, redeliverDeadLetter, delDeadLetter, DeadLetterQuery, DeadLetterVO } from '@/api/monitor/deadletter';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(false);
const showSearch = ref(true);
const deadLetterList = ref<DeadLetterVO[]>([]);
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const detailVisible = ref(false);
const detail = ref<DeadLetterVO>();
const detailBody = ref('');

const queryParams = ref<DeadLetterQuery>({
  pageNum: 1,
  pageSize: 10,
  messageId: '',
  routingKey: '',
  status: ''
});

const getList = async () => {
  loading.value = true;
  try {
    const res = await listDeadLetter(queryParams.value);
    deadLetterList.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryFormRef.value?.resetFields();
  handleQuery();
};

/** 查看消息体详情 */
const handleDetail = async (row: DeadLetterVO) => {
  const res = await getDeadLetter(row.id);
  detail.value = res.data;
  detailBody.value = prettyJson(res.data?.messageBody);
  detailVisible.value = true;
};

/** 人工重投 */
const handleRedeliver = async (row: DeadLetterVO) => {
  await proxy?.$modal.confirm(`确认重投消息「${row.messageId}」到 ${row.exchange} / ${row.routingKey} 吗？`);
  await redeliverDeadLetter(row.id);
  proxy?.$modal.msgSuccess('重投成功');
  getList();
};

/** 删除死信记录 */
const handleDelete = async (row: DeadLetterVO) => {
  await proxy?.$modal.confirm(`确认删除死信记录「${row.messageId}」吗？`);
  await delDeadLetter([row.id]);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

/** 消息体 JSON 美化(非 JSON 原样返回) */
const prettyJson = (body?: string) => {
  if (!body) return '';
  try {
    return JSON.stringify(JSON.parse(body), null, 2);
  } catch {
    return body;
  }
};

onMounted(() => {
  getList();
});
</script>
