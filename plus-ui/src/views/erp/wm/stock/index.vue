<template>
  <div class="app-container">
    <el-row :gutter="20">
      <!-- 左侧物料分类树 -->
      <el-col :span="5" class="left-tree">
        <div class="tree-header">
          <!-- 增加分类树搜索框 -->
          <el-input
            v-model="categoryFilterText"
            placeholder="请输入分类名称"
            clearable
            prefix-icon="Search"
            class="category-search-input"
            aria-label="分类搜索"
            @input="debouncedHandleCategoryFilter"
          />
        </div>
        <el-tree
          ref="categoryTreeRef"
          :data="categoryTree"
          node-key="id"
          :props="defaultProps"
          :expand-on-click-node="false"
          highlight-current
          :filter-node-method="filterCategoryNode"
          @node-click="handleNodeClick"
        >
          <template #default="{ node }">
            <span class="custom-tree-node">
              <span>{{ node.label }}</span>
            </span>
          </template>
        </el-tree>
      </el-col>

      <!-- 右侧库存列表 -->
      <el-col :span="19">
        <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
          <el-form-item label="产品物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入产品物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="产品物料名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入产品物料名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="入库批次号" prop="batchCode">
            <el-input v-model="queryParams.batchCode" placeholder="请输入入库批次号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="仓库名称" prop="warehouseName">
            <el-input v-model="queryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="库区名称" prop="areaName">
            <el-input v-model="queryParams.areaName" placeholder="请输入库区名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="库位名称" prop="locationName">
            <el-input v-model="queryParams.locationName" placeholder="请输入库位名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="供应商名称" prop="vendorName">
            <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="生产工单编号" prop="woNo">
            <el-input v-model="queryParams.woNo" placeholder="请输入生产工单编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="入库时间" prop="recptDate">
            <el-date-picker clearable v-model="queryParams.recptDate" type="date" value-format="YYYY-MM-DD"
              placeholder="请选择入库时间">
            </el-date-picker>
          </el-form-item>
          <el-form-item label="库存有效期" prop="expireDate">
            <el-date-picker clearable v-model="queryParams.expireDate" type="date" value-format="YYYY-MM-DD"
              placeholder="请选择库存有效期">
            </el-date-picker>
          </el-form-item>
          <el-form-item label="冻结标志" prop="frozenFlag">
            <el-select v-model="queryParams.frozenFlag" placeholder="请选择冻结标志" clearable>
              <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:stock:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
              v-hasPermi="['wm:stock:edit']">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
              v-hasPermi="['wm:stock:remove']">删除</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
              v-hasPermi="['wm:stock:query']">查看</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport"
              v-hasPermi="['wm:stock:export']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <el-table v-loading="loading" :data="stockList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="产品物料编码" align="center" prop="materialCode" />
          <el-table-column label="产品物料名称" align="center" prop="materialName" />
          <el-table-column label="规格型号" align="center" prop="materialSpec" />
          <el-table-column label="单位名称" align="center" prop="baseUomName" />
          <el-table-column label="入库批次号" align="center" prop="batchCode" />
          <el-table-column label="仓库编码" align="center" prop="warehouseCode" />
          <el-table-column label="仓库名称" align="center" prop="warehouseName" />
          <el-table-column label="库区编码" align="center" prop="areaCode" />
          <el-table-column label="库区名称" align="center" prop="areaName" />
          <el-table-column label="库位编码" align="center" prop="locationCode" />
          <el-table-column label="库位名称" align="center" prop="locationName" />
          <el-table-column label="供应商编号" align="center" prop="vendorCode" />
          <el-table-column label="供应商名称" align="center" prop="vendorName" />
          <el-table-column label="供应商简称" align="center" prop="vendorNick" />
          <el-table-column label="在库数量" align="center" prop="stockQuantity" />
          <el-table-column label="生产工单ID" align="center" prop="woId" />
          <el-table-column label="生产工单编号" align="center" prop="woNo" />
          <el-table-column label="生产日期" align="center" prop="productionDate" width="180">
            <template #default="scope">
              <span>{{ parseTime(scope.row.productionDate, '{y}-{m}-{d}') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="入库时间" align="center" prop="recptDate" width="180">
            <template #default="scope">
              <span>{{ parseTime(scope.row.recptDate, '{y}-{m}-{d}') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库存有效期" align="center" prop="expireDate" width="180">
            <template #default="scope">
              <span>{{ parseTime(scope.row.expireDate, '{y}-{m}-{d}') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="冻结标志" align="center" prop="frozenFlag">
            <template #default="scope">
              <dict-tag :options="sys_true_or_false" :value="scope.row.frozenFlag" />
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
                v-hasPermi="['wm:stock:edit']">修改</el-button>
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
                v-hasPermi="['wm:stock:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
          @pagination="getList" />

        <!-- 添加或修改库存清单对话框 -->
        <el-dialog :title="title" v-model="open" width="960px" append-to-body>
          <el-form ref="stockRef" :model="form" :rules="rules" label-width="80px" :disabled="isViewMode">
            <el-form-item label="物料类型ID" prop="categoryId">
              <el-input v-model="form.categoryId" placeholder="请输入物料类型ID" />
            </el-form-item>
            <el-form-item label="产品物料ID" prop="materialId">
              <el-input v-model="form.materialId" placeholder="请输入产品物料ID" />
            </el-form-item>
            <el-form-item label="产品物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请输入产品物料编码" />
            </el-form-item>
            <el-form-item label="产品物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入产品物料名称" />
            </el-form-item>
            <el-form-item label="规格型号" prop="materialSpec">
              <el-input v-model="form.materialSpec" type="textarea" placeholder="请输入内容" />
            </el-form-item>
            <el-form-item label="单位id" prop="baseUomId">
              <el-input v-model="form.baseUomId" placeholder="请输入单位id" />
            </el-form-item>
            <el-form-item label="单位名称" prop="baseUomName">
              <el-input v-model="form.baseUomName" placeholder="请输入单位名称" />
            </el-form-item>
            <el-form-item label="批次ID" prop="batchId">
              <el-input v-model="form.batchId" placeholder="请输入批次ID" />
            </el-form-item>
            <el-form-item label="入库批次号" prop="batchCode">
              <el-input v-model="form.batchCode" placeholder="请输入入库批次号" />
            </el-form-item>
            <el-form-item label="仓库ID" prop="warehouseId">
              <el-input v-model="form.warehouseId" placeholder="请输入仓库ID" />
            </el-form-item>
            <el-form-item label="仓库编码" prop="warehouseCode">
              <el-input v-model="form.warehouseCode" placeholder="请输入仓库编码" />
            </el-form-item>
            <el-form-item label="仓库名称" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" />
            </el-form-item>
            <el-form-item label="库区ID" prop="areaId">
              <el-input v-model="form.areaId" placeholder="请输入库区ID" />
            </el-form-item>
            <el-form-item label="库区编码" prop="areaCode">
              <el-input v-model="form.areaCode" placeholder="请输入库区编码" />
            </el-form-item>
            <el-form-item label="库区名称" prop="areaName">
              <el-input v-model="form.areaName" placeholder="请输入库区名称" />
            </el-form-item>
            <el-form-item label="库位ID" prop="locationId">
              <el-input v-model="form.locationId" placeholder="请输入库位ID" />
            </el-form-item>
            <el-form-item label="库位编码" prop="locationCode">
              <el-input v-model="form.locationCode" placeholder="请输入库位编码" />
            </el-form-item>
            <el-form-item label="库位名称" prop="locationName">
              <el-input v-model="form.locationName" placeholder="请输入库位名称" />
            </el-form-item>
            <el-form-item label="供应商ID" prop="vendorId">
              <el-input v-model="form.vendorId" placeholder="请输入供应商ID" />
            </el-form-item>
            <el-form-item label="供应商编号" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编号" />
            </el-form-item>
            <el-form-item label="供应商名称" prop="vendorName">
              <el-input v-model="form.vendorName" placeholder="请输入供应商名称" />
            </el-form-item>
            <el-form-item label="供应商简称" prop="vendorNick">
              <el-input v-model="form.vendorNick" placeholder="请输入供应商简称" />
            </el-form-item>
            <el-form-item label="在库数量" prop="stockQuantity">
              <el-input v-model="form.stockQuantity" placeholder="请输入在库数量" />
            </el-form-item>
            <el-form-item label="生产工单ID" prop="woId">
              <el-input v-model="form.woId" placeholder="请输入生产工单ID" />
            </el-form-item>
            <el-form-item label="生产工单编号" prop="woNo">
              <el-input v-model="form.woNo" placeholder="请输入生产工单编号" />
            </el-form-item>
            <el-form-item label="生产日期" prop="productionDate">
              <el-date-picker clearable v-model="form.productionDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择生产日期">
              </el-date-picker>
            </el-form-item>
            <el-form-item label="入库时间" prop="recptDate">
              <el-date-picker clearable v-model="form.recptDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择入库时间">
              </el-date-picker>
            </el-form-item>
            <el-form-item label="库存有效期" prop="expireDate">
              <el-date-picker clearable v-model="form.expireDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择库存有效期">
              </el-date-picker>
            </el-form-item>
            <el-form-item label="冻结标志" prop="frozenFlag">
              <el-radio-group v-model="form.frozenFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
          <template #footer>
            <div class="dialog-footer">
              <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
              <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
            </div>
          </template>
        </el-dialog>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="Stock">
import { listStock, getStock, delStock, addStock, updateStock, getCategoryStocks } from "@/api/erp/wm/stock"
import { listCategory, getAllChildrenIds } from "@/api/erp/bd/category"
import { debounce } from 'lodash'

const { proxy } = getCurrentInstance() as any
const { sys_true_or_false } = toRefs<any>(proxy.useDict('sys_true_or_false'))

const stockList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false)
const categoryTree = ref([])
const categoryTreeRef = ref()
const categoryFilterText = ref('')

const defaultProps = {
  children: 'children',
  label: 'categoryName'
}

// 创建防抖函数
const debouncedHandleCategoryFilter = debounce(function(val: string) {
  handleCategoryFilter(val)
}, 300)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    categoryId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    baseUomId: null,
    baseUomName: null,
    batchId: null,
    batchCode: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    stockQuantity: null,
    woId: null,
    woNo: null,
    productionDate: null,
    recptDate: null,
    expireDate: null,
    frozenFlag: null,
  },
  rules: {
    materialId: [
      { required: true, message: "产品物料ID不能为空", trigger: "blur" }
    ],
    warehouseId: [
      { required: true, message: "仓库ID不能为空", trigger: "blur" }
    ],
    frozenFlag: [
      { required: true, message: "冻结标志不能为空", trigger: "change" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询库存清单列表 */
function getList() {
  loading.value = true
  listStock(queryParams.value).then((response: any) => {
    stockList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 查询物料分类树 */
function getCategoryTree() {
  listCategory().then((response: any) => {
    // 处理分类树数据，确保数据结构符合 el-tree 要求
    const data = response.data || response.rows || []
    const filteredData = Array.isArray(data) ? data.filter((item: any) => item !== null && item !== undefined) : []
    const treeData = proxy.handleTree(filteredData, "categoryId", "parentId");
    categoryTree.value = treeData;
  })
}

// 添加分类树过滤方法
function filterCategoryNode(value: string, data: any) {
  if (!value) return true;
  return data.categoryName && data.categoryName.includes(value);
}

// 添加处理分类过滤输入
function handleCategoryFilter(val: string) {
  categoryTreeRef.value.filter(val)
}

/** 树节点点击事件 */
function handleNodeClick(data: any) {
  // 传递 data.categoryId 而不是整个 data 对象
  getAllChildrenIds(data.categoryId).then((response: any) => {
    if (response.code === 200) {
      // 提取所有分类的ID
      const categoryIds = [data.categoryId];

      if (response.data && Array.isArray(response.data)) {
        response.data.forEach((id: any) => {
          const childId = Number(id)
          if (!isNaN(childId) && childId > 0 && !categoryIds.includes(childId)) {
            categoryIds.push(childId)
          }
        })
      }

      // 使用新接口获取分类及其子分类的库存数据
      loading.value = true;
      getCategoryStocks(categoryIds).then((res: any) => {
        if (res.code === 200) {
          stockList.value = res.data.stocks || [];
          total.value = stockList.value.length;
        } else {
          stockList.value = [];
          total.value = 0;
          proxy.$modal.msgError("获取库存数据失败");
        }
        loading.value = false;
      }).catch((err: any) => {
        stockList.value = [];
        total.value = 0;
        loading.value = false;
        proxy.$modal.msgError("请求出现异常");
        console.error(err);
      });
    } else {
      proxy.$modal.msgError("获取分类信息失败");
    }
  }).catch((error: any) => {
    proxy.$modal.msgError("请求出现异常");
    console.error(error);
  });
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    stockId: null,
    categoryId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    baseUomId: null,
    baseUomName: null,
    batchId: null,
    batchCode: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    stockQuantity: null,
    woId: null,
    woNo: null,
    productionDate: null,
    recptDate: null,
    expireDate: null,
    frozenFlag: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  proxy.resetForm("stockRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  // 重置分类相关查询参数
  queryParams.value.categoryId = null;
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.stockId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false
  open.value = true
  title.value = "添加库存清单"
}

/** 修改按钮操作 */
function handleUpdate(row: any) {
  reset()
  isViewMode.value = false
  const _stockId = row.stockId || ids.value
  getStock(_stockId).then((response: any) => {
    form.value = response.data
    open.value = true
    title.value = "修改库存清单"
  })
}

/** 查看按钮操作 */
function handleView(row: any) {
  reset()
  isViewMode.value = true
  const _stockId = row.stockId || ids.value
  getStock(_stockId).then((response: any) => {
    form.value = response.data
    open.value = true
    title.value = "查看库存清单"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["stockRef"].validate((valid: boolean) => {
    if (valid) {
      if (form.value.stockId != null) {
        updateStock(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addStock(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row: any) {
  const _stockIds = row.stockId || ids.value
  proxy.$modal.confirm('是否确认删除库存清单编号为"' + _stockIds + '"的数据项？').then(function () {
    return delStock(_stockIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('/erp/wm/stock/export', {
    ...queryParams.value
  }, `stock_${new Date().getTime()}.xlsx`)
}

// 添加组件销毁时的清理逻辑
onUnmounted(() => {
  // 取消防抖函数的执行
  if (debouncedHandleCategoryFilter) {
    debouncedHandleCategoryFilter.cancel()
  }
})

getList()
getCategoryTree()
</script>

<style scoped>
.left-tree {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
  height: calc(100vh - 200px);
  overflow-y: auto;
}

.tree-header {
  margin-bottom: 15px;
  text-align: center;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}

.category-search-input {
  margin-bottom: 10px;
}
</style>
