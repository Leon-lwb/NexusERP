<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { AiFlow } from '@/api/ai/flow';
import { deleteFlow, listFlow, offlineFlow, publishFlow, runFlow } from '@/api/ai/flow';

/**
 * AI 流程列表页
 * <p>流程 CRUD + 发布/下线 + 运行 + 进入设计器/历史</p>
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
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="editFlow(row)">设计</el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="success" @click="publish(row)"> 发布 </el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="offline(row)"> 下线 </el-button>
          <el-button link type="success" @click="run(row)">运行</el-button>
          <el-button link type="primary" @click="history(row)">历史</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="loadList" />
  </div>
</template>
