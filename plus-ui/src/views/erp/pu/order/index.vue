<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="订单号" prop="orderNo">
        <el-input 
          v-model="queryParams.orderNo" 
          placeholder="请输入订单号" 
          clearable 
          @keyup.enter="handleQuery" 
        />
      </el-form-item>
      <el-form-item label="供应商" prop="vendorName">
        <el-input 
          v-model="queryParams.vendorName" 
          placeholder="请输入供应商" 
          clearable 
          @keyup.enter="handleQuery" 
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option 
            v-for="dict in pu_purchase_order_status" 
            :key="dict.value" 
            :label="dict.label" 
            :value="dict.value" 
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    
    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button 
          type="primary" 
          plain 
          icon="Plus" 
          @click="handleAdd" 
          v-hasPermi="['pu:order:add']"
        >
          新增
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="success" 
          plain 
          icon="Edit" 
          :disabled="single" 
          @click="handleUpdate" 
          v-hasPermi="['pu:order:edit']"
        >
          修改
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="danger" 
          plain 
          icon="Delete" 
          :disabled="multiple" 
          @click="handleDelete" 
          v-hasPermi="['pu:order:remove']"
        >
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="info" 
          plain 
          icon="View" 
          :disabled="single" 
          @click="handleView" 
          v-hasPermi="['pu:order:query']"
        >
          查看
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="warning" 
          plain 
          icon="Download" 
          @click="handleExport" 
          v-hasPermi="['pu:order:export']"
        >
          导出
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="success" 
          plain 
          icon="Promotion" 
          :disabled="single" 
          @click="handleSubmit" 
          v-hasPermi="['pu:order:edit']"
        >
          提交
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="primary" 
          plain 
          icon="Check" 
          :disabled="single" 
          @click="handleApprove" 
          v-hasPermi="['pu:order:edit']"
        >
          审批
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="success" 
          plain 
          icon="Goods" 
          :disabled="single" 
          @click="handleReceive" 
          v-hasPermi="['pu:order:edit']"
        >
          收货联动
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="danger" 
          plain 
          icon="Warning" 
          @click="handleDiffReport" 
          v-hasPermi="['pu:order:list']"
        >
          差异报表
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>
    
    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="orderList" @selection-change="handleSelectionChange" row-key="orderId" :expand-row-keys="expandedRows" @expand-change="handleExpandChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column type="expand">
        <template #default="props">
          <el-table :data="orderLinesMap[props.row.orderId] || []" border size="small" style="margin: 8px 20px;">
            <el-table-column label="物料编码" prop="materialCode" width="120" />
            <el-table-column label="物料名称" prop="materialName" min-width="150" />
            <el-table-column label="规格型号" prop="materialSpec" width="120" />
            <el-table-column label="订单数量" prop="orderQuantity" width="100" />
            <el-table-column label="已收数量" prop="receivedQuantity" width="100" />
            <el-table-column label="收货状态" prop="receiveStatus" width="120">
              <template #default="scope">
                <el-tag :type="getReceiveStatusType(scope.row.receiveStatus)" size="small">
                  {{ getReceiveStatusLabel(scope.row.receiveStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="收货进度" width="150">
              <template #default="scope">
                <el-progress :percentage="calcReceiveProgress(scope.row)" :color="getProgressColor(scope.row)" :stroke-width="10" />
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column label="订单号" prop="orderNo" />
      <el-table-column label="供应商" prop="vendorName" />
      <el-table-column label="订单日期" prop="orderDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.orderDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="预计到货" prop="expectedArrivalDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.expectedArrivalDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status">
        <template #default="scope">
          <dict-tag :options="pu_purchase_order_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" />
    </el-table>
    
    <!-- 分页组件 -->
    <pagination 
      v-show="total > 0" 
      :total="total" 
      v-model:page="queryParams.pageNum" 
      v-model:limit="queryParams.pageSize" 
      @pagination="getList" 
    />
  </div>
</template>

<script setup lang="ts" name="PurchaseOrder">
// 导入采购订单相关的API函数
import { listPurchaseOrder, delPurchaseOrder, submitPurchaseOrder, approvePurchaseOrder, receivePurchaseOrder, getDiffReport, listOrderLines } from '@/api/erp/pu/order'

// 获取当前实例并获取字典数据
const { proxy } = getCurrentInstance() as any; 
const { pu_purchase_order_status } = toRefs<any>(proxy.useDict('pu_purchase_order_status'))

// 定义响应式数据
const orderList = ref([]);     // 采购订单列表
const loading = ref(true);     // 加载状态
const showSearch = ref(true);  // 显示搜索区域
const ids = ref([]);           // 选中的ID数组
const single = ref(true);      // 单选状态
const multiple = ref(true);    // 多选状态
const total = ref(0)           // 总记录数

const orderLinesMap = ref<Record<number, any[]>>({});
const expandedRows = ref<number[]>([]);

const loadOrderLines = async (orderId: number) => {
  if (orderLinesMap.value[orderId]) return;
  const res = await listOrderLines(orderId);
  orderLinesMap.value[orderId] = res.data || [];
};

const handleExpandChange = (row: any, expandedRows: any[]) => {
  if (expandedRows.includes(row)) {
    loadOrderLines(row.orderId);
  }
};

const getReceiveStatusType = (status: string): string => {
  const map: Record<string, string> = {
    'NOT_RECEIVED': 'info',
    'PENDING': 'info',
    'PARTIAL_RECEIVED': 'warning',
    'FULL_RECEIVED': 'success',
    'OVER_RECEIVED': 'danger'
  };
  return map[status] || 'info';
};

const getReceiveStatusLabel = (status: string): string => {
  const map: Record<string, string> = {
    'NOT_RECEIVED': '未收货',
    'PENDING': '待收货',
    'PARTIAL_RECEIVED': '部分到货',
    'FULL_RECEIVED': '全部到货',
    'OVER_RECEIVED': '超收'
  };
  return map[status] || status || '未知';
};

const calcReceiveProgress = (row: any): number => {
  if (!row.orderQuantity || row.orderQuantity === 0) return 0;
  const received = row.receivedQuantity || 0;
  return Math.min(Math.round((received / row.orderQuantity) * 100), 100);
};

const getProgressColor = (row: any): string => {
  const progress = calcReceiveProgress(row);
  if (progress >= 100) return '#67c23a';
  if (progress >= 50) return '#409eff';
  if (progress > 0) return '#e6a23c';
  return '#909399';
};

// 查询参数
const data = reactive({ 
  queryParams: { 
    pageNum: 1, 
    pageSize: 10, 
    orderNo: null, 
    vendorName: null, 
    status: null 
  } 
}); 
const { queryParams } = toRefs(data)

/**
 * 获取采购订单列表
 */
function getList(){
  loading.value = true;
  listPurchaseOrder(queryParams.value).then((res: any) => {
    orderList.value = res.rows;
    total.value = res.total;
    loading.value = false
  })
}

/**
 * 搜索按钮操作
 */
function handleQuery(){
  queryParams.value.pageNum = 1;
  getList()
}

/**
 * 重置按钮操作
 */
function resetQuery(){
  proxy.resetForm('queryRef');
  handleQuery()
}

/**
 * 多选框选中数据
 */
function handleSelectionChange(selection: any[]){
  ids.value = selection.map((i: any) => i.orderId);
  single.value = selection.length != 1;
  multiple.value = !selection.length
}

/**
 * 新增按钮操作
 */
function handleAdd(){
  proxy.$router.push({ path: '/erp/pu/order/form', query: { action: 'add' } })
}

/**
 * 修改按钮操作
 */
function handleUpdate(){
  proxy.$router.push({ path: '/erp/pu/order/form', query: { id: ids.value[0], action: 'edit' } })
}

/**
 * 查看按钮操作
 */
function handleView(){
  proxy.$router.push({ path: '/erp/pu/order/form', query: { id: ids.value[0], action: 'view' } })
}

/**
 * 删除按钮操作
 */
function handleDelete(){
  proxy.$modal.confirm('是否确认删除采购订单?').then(() => 
    delPurchaseOrder(ids.value)
  ).then(() => {
    getList();
    proxy.$modal.msgSuccess('删除成功')
  })
}

/**
 * 导出按钮操作
 */
function handleExport(){
  proxy.download('/erp/pu/order/export', { ...queryParams.value }, `pu_order_${Date.now()}.xlsx`)
}

/**
 * 提交按钮操作
 */
function handleSubmit(){
  submitPurchaseOrder(ids.value[0]).then(() => {
    proxy.$modal.msgSuccess('提交成功');
    getList()
  })
}

/**
 * 审批按钮操作
 */
function handleApprove(){
  approvePurchaseOrder(ids.value[0]).then(() => {
    proxy.$modal.msgSuccess('审批成功');
    getList()
  })
}

/**
 * 收货联动按钮操作
 */
function handleReceive(){
  receivePurchaseOrder(ids.value[0]).then((res: any) => {
    proxy.$modal.msgSuccess('收货联动成功，到货通知单ID: ' + (res.data || ''));
    getList()
  })
}

/**
 * 差异报表按钮操作 - 查询收货差异
 */
function handleDiffReport(){
  getDiffReport().then((res: any) => {
    const diffData = res.data || [];
    if (diffData.length === 0) {
      proxy.$modal.msgSuccess('暂无收货差异记录');
    } else {
      // 构建差异报表内容
      let html = '<div style="max-height:400px;overflow-y:auto;">';
      html += '<table style="width:100%;border-collapse:collapse;">';
      html += '<tr style="background:#f5f7fa;"><th style="padding:8px;border:1px solid #ebeef5;">订单行ID</th><th style="padding:8px;border:1px solid #ebeef5;">物料编码</th><th style="padding:8px;border:1px solid #ebeef5;">订单数量</th><th style="padding:8px;border:1px solid #ebeef5;">已收数量</th><th style="padding:8px;border:1px solid #ebeef5;">状态</th></tr>';
      diffData.forEach((item: any) => {
        const statusMap: Record<string, string> = {
          'PARTIAL_RECEIVED': '<span style="color:#e6a23c;">部分到货</span>',
          'OVER_RECEIVED': '<span style="color:#f56c6c;">超收</span>',
          'SHORT_RECEIVED': '<span style="color:#909399;">短收</span>'
        };
        html += `<tr><td style="padding:8px;border:1px solid #ebeef5;">${item.lineId || '-'}</td>`;
        html += `<td style="padding:8px;border:1px solid #ebeef5;">${item.materialCode || '-'}</td>`;
        html += `<td style="padding:8px;border:1px solid #ebeef5;">${item.orderQuantity || 0}</td>`;
        html += `<td style="padding:8px;border:1px solid #ebeef5;">${item.receivedQuantity || 0}</td>`;
        html += `<td style="padding:8px;border:1px solid #ebeef5;">${statusMap[item.receiveStatus] || item.receiveStatus}</td></tr>`;
      });
      html += '</table></div>';
      proxy.$modal.alert(html, '收货差异报表', { dangerouslyUseHTMLString: true });
    }
  })
}

// 页面加载时获取数据
getList()
</script>