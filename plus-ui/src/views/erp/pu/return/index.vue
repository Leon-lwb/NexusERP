<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="退货单号" prop="returnNo">
        <el-input 
          v-model="queryParams.returnNo" 
          placeholder="请输入退货单号" 
          clearable 
          @keyup.enter="handleQuery" 
        />
      </el-form-item>
      <el-form-item label="采购订单号" prop="orderNo">
        <el-input 
          v-model="queryParams.orderNo" 
          placeholder="请输入采购订单号" 
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
            v-for="dict in pu_purchase_return_status" 
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
          v-hasPermi="['pu:return:add']"
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
          v-hasPermi="['pu:return:edit']"
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
          v-hasPermi="['pu:return:remove']"
        >
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="warning" 
          plain 
          icon="Download" 
          @click="handleExport" 
          v-hasPermi="['pu:return:export']"
        >
          导出
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="success" 
          plain 
          icon="Check" 
          :disabled="single" 
          @click="handleConfirm" 
          v-hasPermi="['pu:return:edit']"
        >
          确认
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button 
          type="primary" 
          plain 
          icon="Finished" 
          :disabled="single" 
          @click="handleExecute" 
          v-hasPermi="['pu:return:edit']"
        >
          执行
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>
    
    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="returnList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="退货单号" align="center" prop="returnNo" />
      <el-table-column label="采购订单号" align="center" prop="orderNo" />
      <el-table-column label="供应商" align="center" prop="vendorName" />
      <el-table-column label="退货日期" align="center" prop="returnDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.returnDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="pu_purchase_return_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
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

<script setup lang="ts" name="PurchaseReturn">
// 导入采购退货相关的API函数
import { 
  listPurchaseReturn, 
  delPurchaseReturn, 
  confirmPurchaseReturn, 
  executePurchaseReturn 
} from '@/api/erp/pu/return'

// 获取当前实例并获取字典数据
const { proxy } = getCurrentInstance() as any; 
const { pu_purchase_return_status } = toRefs<any>(proxy.useDict('pu_purchase_return_status'))

// 定义响应式数据
const returnList = ref([]);      // 采购退货单列表
const loading = ref(true);       // 加载状态
const showSearch = ref(true);    // 显示搜索区域
const ids = ref([]);             // 选中的ID数组
const single = ref(true);        // 单选状态
const multiple = ref(true);      // 多选状态
const total = ref(0)             // 总记录数

// 查询参数
const data = reactive({ 
  queryParams: { 
    pageNum: 1, 
    pageSize: 10, 
    returnNo: null, 
    orderNo: null, 
    vendorName: null, 
    status: null 
  } 
})
const { queryParams } = toRefs(data)

/**
 * 获取采购退货单列表
 */
function getList(){
  loading.value = true;
  listPurchaseReturn(queryParams.value).then((res: any) => {
    returnList.value = res.rows;
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
  ids.value = selection.map((i: any) => i.returnId);
  single.value = selection.length != 1;
  multiple.value = !selection.length
}

/**
 * 新增按钮操作
 */
function handleAdd(){
  proxy.$router.push({ path: '/erp/pu/return/form', query: { action: 'add' } })
}

/**
 * 修改按钮操作
 */
function handleUpdate(){
  const r = returnList.value.find((i: any) => i.returnId == ids.value[0]);
  if (!r) {
    proxy.$modal.msgWarning('请选择一条数据');
    return
  }
  proxy.$router.push({ 
    path: '/erp/pu/return/form', 
    query: { id: r.returnId, action: 'edit' } 
  })
}

/**
 * 删除按钮操作
 */
function handleDelete(){
  proxy.$modal.confirm('是否确认删除采购退货单?').then(() => 
    delPurchaseReturn(ids.value)
  ).then(() => {
    getList();
    proxy.$modal.msgSuccess('删除成功')
  })
}

/**
 * 确认按钮操作
 */
function handleConfirm(){
  confirmPurchaseReturn(ids.value[0]).then(() => {
    proxy.$modal.msgSuccess('确认成功');
    getList()
  })
}

/**
 * 执行按钮操作
 */
function handleExecute(){
  executePurchaseReturn(ids.value[0]).then(() => {
    proxy.$modal.msgSuccess('执行成功');
    getList()
  })
}

/**
 * 导出按钮操作
 */
function handleExport(){
  proxy.download('/erp/pu/return/export', { ...queryParams.value }, `pu_return_${Date.now()}.xlsx`)
}

// 页面加载时获取数据
getList()
</script>