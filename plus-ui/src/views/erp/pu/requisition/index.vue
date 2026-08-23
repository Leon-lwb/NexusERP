<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="申请单号" prop="requisitionNo">
            <el-input v-model="queryParams.requisitionNo" placeholder="请输入申请单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="申请名称" prop="requisitionName">
            <el-input v-model="queryParams.requisitionName" placeholder="请输入申请名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in pu_purchase_requisition_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['pu:requisition:add']"> 新增 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['pu:requisition:edit']"> 修改 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['pu:requisition:remove']">
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['pu:requisition:query']"> 查看 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['pu:requisition:export']"> 导出 </el-button>
      </el-col>
      <!--
      <el-col :span="1.5">
        <el-button type="success" plain icon="Promotion" :disabled="single" @click="handleSubmit" v-hasPermi="['pu:requisition:edit']">
          提交
        </el-button>
      </el-col>
      -->
      <!--
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Check" :disabled="single" @click="handleApprove" v-hasPermi="['pu:requisition:edit']"> 审批 </el-button>
      </el-col>
      -->
      <el-col :span="1.5">
        <el-button type="success" plain icon="Position" :disabled="single" @click="handleConvert" v-hasPermi="['pu:requisition:edit']">
          转单
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleGenerateFromLowStock" v-hasPermi="['pu:requisition:add']">
          低库存生成
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="requisitionList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="申请单号" prop="requisitionNo" />
      <el-table-column label="申请名称" prop="requisitionName" min-width="120" show-overflow-tooltip />
      <el-table-column label="申请日期" prop="requisitionDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.requisitionDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="需求日期" prop="requiredDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.requiredDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status">
        <template #default="scope">
          <dict-tag :options="pu_purchase_requisition_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
    </el-table>

    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="PurchaseRequisition">
// 导入采购请购单相关的API函数
import { listPurchaseRequisition, delPurchaseRequisition, convertPurchaseRequisition, generateFromLowStock } from '@/api/erp/pu/requisition';

// 获取当前实例并获取字典数据
const { proxy } = getCurrentInstance() as any;
const { pu_purchase_requisition_status } = toRefs<any>(proxy.useDict('pu_purchase_requisition_status'));

// 定义响应式数据
const requisitionList = ref([]); // 采购请购单列表
const loading = ref(true); // 加载状态
const showSearch = ref(true); // 显示搜索区域
const ids = ref([]); // 选中的ID数组
const single = ref(true); // 单选状态
const multiple = ref(true); // 多选状态
const total = ref(0); // 总记录数

// 查询参数
const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    requisitionNo: null,
    requisitionName: null,
    status: null
  }
});
const { queryParams } = toRefs(data);

/**
 * 获取采购请购单列表
 */
function getList() {
  loading.value = true;
  listPurchaseRequisition(queryParams.value).then((res: any) => {
    requisitionList.value = res.rows;
    total.value = res.total;
    loading.value = false;
  });
}

/**
 * 搜索按钮操作
 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/**
 * 重置按钮操作
 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/**
 * 多选框选中数据
 */
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((i: any) => i.requisitionId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/**
 * 新增按钮操作
 */
function handleAdd() {
  proxy.$router.push({ path: '/erp/pu/requisition/form', query: { action: 'add' } });
}

/**
 * 修改按钮操作
 */
function handleUpdate() {
  proxy.$router.push({ path: '/erp/pu/requisition/form', query: { id: ids.value[0], action: 'edit' } });
}

/**
 * 查看按钮操作
 */
function handleView() {
  proxy.$router.push({ path: '/erp/pu/requisition/form', query: { id: ids.value[0], action: 'view' } });
}

/**
 * 删除按钮操作
 */
function handleDelete() {
  proxy.$modal
    .confirm('是否确认删除采购申请?')
    .then(() => delPurchaseRequisition(ids.value))
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    });
}

/**
 * 导出按钮操作
 */
function handleExport() {
  proxy.download('/erp/pu/requisition/export', { ...queryParams.value }, `pu_requisition_${Date.now()}.xlsx`);
}

/**
 * 转单按钮操作 - 将请购单转换为采购订单
 */
function handleConvert() {
  convertPurchaseRequisition(ids.value[0]).then(() => {
    proxy.$modal.msgSuccess('转单成功');
    getList();
  });
}

/**
 * 低库存生成按钮操作 - 扫描安全库存自动生成采购申请
 */
function handleGenerateFromLowStock() {
  proxy.$modal
    .confirm('是否确认从低库存自动生成采购申请？')
    .then(() => generateFromLowStock())
    .then((res: any) => {
      const count = res.data ? res.data.length : 0;
      if (count > 0) {
        proxy.$modal.msgSuccess('成功生成 ' + count + ' 条采购申请');
      } else {
        proxy.$modal.msgWarning('未发现低库存物料，无需生成采购申请');
      }
      getList();
    });
}

onActivated(() => {
  getList();
});
</script>
