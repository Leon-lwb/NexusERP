<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { AiFlow, FlowTemplate } from '@/api/ai/flow';
import { addFlow, deleteFlow, listFlow, listFlowTemplates, offlineFlow, publishFlow, runFlow } from '@/api/ai/flow';

/**
 * AI 流程列表页
 * <p>流程 CRUD + 发布/下线 + 运行 + 进入设计器/历史 + Webhook 信息 + 从模板创建</p>
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

/** 模板库对话框状态 */
const templateVisible = ref(false);
const templateLoading = ref(false);
const templates = ref<FlowTemplate[]>([]);
const templateCreating = ref<string | null>(null);

/** 打开模板库（首次加载模板列表） */
async function openTemplates() {
  templateVisible.value = true;
  if (templates.value.length) return;
  templateLoading.value = true;
  try {
    const res = await listFlowTemplates();
    templates.value = res.data ?? [];
  } catch (e: any) {
    ElMessage.error('加载模板失败: ' + (e?.message ?? e));
  } finally {
    templateLoading.value = false;
  }
}

/** 从模板创建流程（addFlow → 跳设计器编辑） */
async function createFromTemplate(tpl: FlowTemplate) {
  templateCreating.value = tpl.key;
  try {
    const res = await addFlow({
      flowName: tpl.name,
      configJson: tpl.configJson,
      triggerType: 'MANUAL',
      maxRetry: 1
    });
    const newId = res.data;
    ElMessage.success('已从模板创建流程，可继续编辑后发布');
    templateVisible.value = false;
    router.push({ path: '/ai/flow/designer', query: { id: String(newId) } });
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e?.message ?? e));
  } finally {
    templateCreating.value = null;
  }
}

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
        <el-button plain @click="openTemplates">从模板创建</el-button>
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

    <!-- 模板库对话框（从模板创建） -->
    <el-dialog v-model="templateVisible" title="从模板创建流程" width="720px" v-loading="templateLoading">
      <el-alert
        title="模板为纯本地节点（SQL/条件/通知），不依赖模型配置，创建后可立即测试运行。通知节点需在运行入参中提供 owner（接收用户ID）。"
        type="info"
        :closable="false"
        style="margin-bottom: 12px"
      />
      <div v-if="templates.length" class="template-grid">
        <div v-for="tpl in templates" :key="tpl.key" class="template-card">
          <div class="template-card__header">
            <span class="template-card__name">{{ tpl.name }}</span>
            <el-tag size="small" effect="plain">{{ tpl.category }}</el-tag>
          </div>
          <div class="template-card__desc">{{ tpl.description }}</div>
          <div v-if="tpl.inputExample" class="template-card__input">
            运行入参示例: <code>{{ tpl.inputExample }}</code>
          </div>
          <el-button type="primary" size="small" class="template-card__btn" :loading="templateCreating === tpl.key" @click="createFromTemplate(tpl)">
            使用此模板
          </el-button>
        </div>
      </div>
      <el-empty v-else-if="!templateLoading" description="暂无可用模板" />
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

/* 模板库卡片 */
.template-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.template-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.template-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
}

.template-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.template-card__name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.template-card__desc {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  flex: 1;
}

.template-card__input {
  font-size: 11px;
  color: #909399;
}

.template-card__input code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
}

.template-card__btn {
  align-self: flex-start;
}
</style>
