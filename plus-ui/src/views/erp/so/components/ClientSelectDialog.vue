<template>
  <el-dialog title="选择客户" v-model="visible" width="720px" append-to-body destroy-on-close>
    <!-- 搜索表单 -->
    <el-form :inline="true" :model="queryParams" class="query-form">
      <el-form-item label="客户编码">
        <el-input v-model="queryParams.clientCode" placeholder="请输入客户编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="客户名称">
        <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 客户列表 -->
    <el-table v-loading="loading" :data="clientList" highlight-current-row @current-change="handleCurrentChange" max-height="400">
      <el-table-column label="客户编码" prop="clientCode" width="140" />
      <el-table-column label="客户名称" prop="clientName" min-width="200" />
      <el-table-column label="联系人" prop="contact1" width="120" />
      <el-table-column label="联系电话" prop="contact1Tel" width="140" />
    </el-table>

    <!-- 分页 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 操作按钮 -->
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :disabled="!selectedClient" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="ClientSelectDialog">
import { listClient } from '@/api/erp/bd/client';
import type { ClientVO } from '@/api/erp/bd/client/types';

const emit = defineEmits(['confirm']);

/** 对话框可见状态 */
const visible = ref(false);
/** 加载状态 */
const loading = ref(false);
/** 客户列表 */
const clientList = ref<ClientVO[]>([]);
/** 总数 */
const total = ref(0);
/** 当前选中的客户 */
const selectedClient = ref<ClientVO | null>(null);

/** 查询参数 */
const queryParams = ref({
  clientCode: '',
  clientName: '',
  pageNum: 1,
  pageSize: 10
});

/** 打开对话框 */
function open() {
  visible.value = true;
  selectedClient.value = null;
  resetQuery();
}

/** 查询客户列表 */
function getList() {
  loading.value = true;
  listClient(queryParams.value)
    .then((response: any) => {
      clientList.value = response.rows || [];
      total.value = response.total || 0;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置 */
function resetQuery() {
  queryParams.value = {
    clientCode: '',
    clientName: '',
    pageNum: 1,
    pageSize: 10
  };
  getList();
}

/** 选中行变化 */
function handleCurrentChange(row: ClientVO | null) {
  selectedClient.value = row;
}

/** 确认选择 */
function handleConfirm() {
  if (selectedClient.value) {
    emit('confirm', selectedClient.value);
    visible.value = false;
  }
}

/** 取消 */
function handleCancel() {
  visible.value = false;
  selectedClient.value = null;
}

defineExpose({ open });
</script>
