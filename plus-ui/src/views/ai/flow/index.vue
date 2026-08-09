<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { AiFlow } from '@/api/ai/flow';
import { deleteFlow, listFlow, offlineFlow, publishFlow, runFlow } from '@/api/ai/flow';

/**
 * AI 流程列表页
 * <p>流程 CRUD + 发布/下线 + 运行 + 进入设计器/历史 + Webhook 信息</p>
 */

const router = useRouter();
const loading = ref(false);
const rows = ref<AiFlow[]>([]);
const total = ref(0);
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  flowName: '',
  status: ''
});

/** Webhook 信息对话框 */
const webhookVisible = ref(false);
const webhookRow = ref<AiFlow | null>(null);
const webhookUrl = computed(() => {
  if (!webhookRow.value) return '';
  return `${location.origin}/ai/flow/webhook/${webhookRow.value.flowKey}`;
});

/** 显示 Webhook 信息 */
function showWebhook(row: AiFlow) {
  webhookRow.value = row;
  webhookVisible.value = true;
}

/** 复制到剪贴板 */
async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制');
  } catch {
    ElMessage.warning('复制失败，请手动选择复制');
  }
}

/** 状态标签映射 */
const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  OFFLINE: { label: '已下线', type: 'warning' }
};

async function loadList() {
  loading.value = true;
  try {
    const res = await listFlow(query);
    rows.value = res.rows ?? [];
    total.value = res.total ?? 0;
  } finally {
    loading.value = false;
  }
}

/** 新建流程（进入设计器） */
function createFlow() {
  router.push('/ai/flow/designer');
}

/** 编辑流程 */
function editFlow(row: AiFlow) {
  router.push({ path: '/ai/flow/designer', query: { id: row.id } });
}

/** 发布 */
async function publish(row: AiFlow) {
  try {
    await publishFlow(row.id);
    ElMessage.success('发布成功');
    loadList();
  } catch (e: any) {
    ElMessage.error('发布失败: ' + (e?.message ?? e));
  }
}

/** 下线 */
async function offline(row: AiFlow) {
  try {
    await offlineFlow(row.id);
    ElMessage.success('已下线');
    loadList();
  } catch (e: any) {
    ElMessage.error('下线失败: ' + (e?.message ?? e));
  }
}

/** 删除 */
async function remove(row: AiFlow) {
  try {
    await ElMessageBox.confirm(`确认删除流程「${row.flowName}」？`, '提示', { type: 'warning' });
    await deleteFlow([row.id]);
    ElMessage.success('删除成功');
    loadList();
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e?.message ?? e));
    }
  }
}

/** 运行 */
async function run(row: AiFlow) {
  try {
    const res = await runFlow(row.id);
    ElMessage.success('流程已启动，实例ID: ' + res.data);
  } catch (e: any) {
    ElMessage.error('运行失败: ' + (e?.message ?? e));
  }
}

/** 运行历史 */
function history(row: AiFlow) {
  router.push({ path: '/ai/flow/monitor', query: { flowId: row.id } });
}

onMounted(loadList);
</script>

<template>
  <div class="app-container">
    <el-form :model="query" inline>
      <el-form-item label="流程名称">
        <el-input v-model="query.flowName" placeholder="输入流程名称" clearable @keyup.enter="loadList" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList">搜索</el-button>
        <el-button type="success" @click="createFlow">新建流程</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column label="ID" prop="id" width="180" show-overflow-tooltip />
      <el-table-column label="流程名称" prop="flowName" min-width="150" show-overflow-tooltip />
      <el-table-column label="流程标识" prop="flowKey" min-width="160" show-overflow-tooltip />
      <el-table-column label="版本" prop="version" width="70" align="center" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="(statusMap[row.status]?.type as any) ?? 'info'">
            {{ statusMap[row.status]?.label ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="触发方式" width="100" align="center">
        <template #default="{ row }">
          {{ row.triggerType === 'SCHEDULE' ? '定时' : '手动' }}
        </template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updateTime" width="170" />
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="editFlow(row)">设计</el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="success" @click="publish(row)"> 发布 </el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="offline(row)"> 下线 </el-button>
          <el-button link type="success" @click="run(row)">运行</el-button>
          <el-button link type="primary" @click="history(row)">历史</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link type="info" @click="showWebhook(row)">Webhook</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Webhook 信息对话框 -->
    <el-dialog v-model="webhookVisible" title="Webhook 触发" width="520px">
      <template v-if="webhookRow">
        <el-alert
          title="外部系统可通过 POST 此 URL 触发流程（无需登录）。流程配置了 token 时需带 x-flow-token 请求头。"
          type="info"
          :closable="false"
          style="margin-bottom: 12px"
        />
        <el-form label-width="80px">
          <el-form-item label="Webhook URL">
            <div class="webhook-url">
              <code>{{ webhookUrl }}</code>
              <el-button link type="primary" @click="copyText(webhookUrl)">复制</el-button>
            </div>
          </el-form-item>
          <el-form-item label="Token 校验">
            <span v-if="webhookRow.webhookToken">已配置（请求头 x-flow-token: {{ webhookRow.webhookToken }}）</span>
            <span v-else>未配置（flowKey 本身即令牌）</span>
          </el-form-item>
          <el-form-item label="调用示例">
            <pre class="webhook-example">
curl -X POST {{ webhookUrl }} \
  -H "Content-Type: application/json"{{ webhookRow.webhookToken ? '\n  -H "x-flow-token: ' + webhookRow.webhookToken + '"' : '' }} \
  -d '{"name":"张三","amount":500}'</pre
            >
          </el-form-item>
        </el-form>
      </template>
    </el-dialog>

    <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="loadList" />
  </div>
</template>

<style scoped>
.webhook-url {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.webhook-url code {
  flex: 1;
  font-size: 12px;
  word-break: break-all;
  background: #f5f7fa;
  padding: 6px 8px;
  border-radius: 4px;
}

.webhook-example {
  font-size: 11px;
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  width: 100%;
  overflow-x: auto;
  white-space: pre-wrap;
}
</style>
