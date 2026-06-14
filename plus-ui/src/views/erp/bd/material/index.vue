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

      <!-- 右侧物料列表 -->
      <el-col :span="19">
        <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="来源类型" prop="sourceType">
            <el-select v-model="queryParams.sourceType" placeholder="请选择" clearable style="width: 120px;">
              <el-option v-for="dict in bd_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="批次管理" prop="isBatchManage">
            <el-select v-model="queryParams.isBatchManage" placeholder="请选择" clearable style="width: 120px;">
              <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="序列号管理" prop="isSerialManage" label-width="100px" label-position="left">
            <el-select v-model="queryParams.isSerialManage" placeholder="请选择" clearable style="width: 120px;">
              <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择" clearable  style="width: 120px;">
              <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="质量状态" prop="qualityStatus">
            <el-select 
              v-model="queryParams.qualityStatus" 
              placeholder="请选择" 
              clearable 
              style="width: 120px;"
            >
              <el-option 
                v-for="dict in bd_quality_status" 
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

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['bd:material:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
              v-hasPermi="['bd:material:edit']">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
              v-hasPermi="['bd:material:remove']">删除</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
              v-hasPermi="['bd:material:query']">查看</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport"
              v-hasPermi="['bd:material:export']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <el-table v-loading="loading" :data="materialList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="物料编码" align="center" prop="materialCode" />
          <el-table-column label="物料名称" align="center" prop="materialName" />
          <el-table-column label="规格型号" align="center" prop="materialSpec" />
          <el-table-column label="物料类型" align="center" prop="materialType">
            <template #default="scope">
              <dict-tag :options="bd_material_type" :value="scope.row.materialType" />
            </template>
          </el-table-column>
          <el-table-column label="来源类型" align="center" prop="sourceType">
            <template #default="scope">
              <dict-tag :options="bd_source_type" :value="scope.row.sourceType" />
            </template>
          </el-table-column>
          <el-table-column label="是否批次管理" align="center" prop="isBatchManage">
            <template #default="scope">
              <dict-tag :options="sys_true_or_false" :value="scope.row.isBatchManage" />
            </template>
          </el-table-column>
          <el-table-column label="是否序列号管理" align="center" prop="isSerialManage">
            <template #default="scope">
              <dict-tag :options="sys_true_or_false" :value="scope.row.isSerialManage" />
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" prop="status">
            <template #default="scope">
              <dict-tag :options="sys_true_or_false" :value="scope.row.status" />
            </template>
          </el-table-column>
          <el-table-column label="质量状态" align="center" prop="qualityStatus">
            <template #default="scope">
              <dict-tag :options="bd_quality_status" :value="scope.row.qualityStatus" />
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
                v-hasPermi="['bd:material:edit']">修改</el-button>
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
                v-hasPermi="['bd:material:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
          @pagination="getList" />
      </el-col>
    </el-row>

    <!-- 添加或修改物料产品主对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-form ref="materialRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="物料编码" prop="materialCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input
                  v-model="form.materialCode"
                  placeholder="请输入物料编码"
                  :disabled="autoGenFlag || isViewMode"
                  style="flex: 1;"
                />
                <el-switch
                  v-model="autoGenFlag"
                  active-color="#13ce66"
                  active-text="自动生成"
                  @change="handleAutoGenChange"
                  :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规格型号" prop="materialSpec">
              <el-input v-model="form.materialSpec" type="textarea" placeholder="请输入具体的规格和参数，如尺寸、重量、功率等" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料描述" prop="materialDesc">
              <el-input v-model="form.materialDesc" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料分类" prop="categoryId">
              <el-tree-select
                v-model="form.categoryId"
                :data="categoryTree"
                node-key="categoryId"
                :props="{
                  children: 'children',
                  label: 'categoryName',
                  value: 'categoryId'
                }"
                placeholder="请选择物料分类"
                clearable
                check-strictly
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="基本计量单位" prop="baseUomId">
              <el-select 
                v-model="form.baseUomId" 
                placeholder="请选择基本计量单位" 
                clearable 
                style="width: 100%"
              >
                <el-option
                  v-for="item in uomList"
                  :key="item.uomId"
                  :label="item.uomName"
                  :value="item.uomId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="采购单位" prop="purchaseUomId">
              <el-select 
                v-model="form.purchaseUomId" 
                placeholder="请选择采购单位" 
                clearable 
                style="width: 100%"
              >
                <el-option
                  v-for="item in uomList"
                  :key="item.uomId"
                  :label="item.uomName"
                  :value="item.uomId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存单位" prop="inventoryUomId">
              <el-select 
                v-model="form.inventoryUomId" 
                placeholder="请选择库存单位" 
                clearable 
                style="width: 100%"
              >
                <el-option
                  v-for="item in uomList"
                  :key="item.uomId"
                  :label="item.uomName"
                  :value="item.uomId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="销售单位" prop="saleUomId">
              <el-select 
                v-model="form.saleUomId" 
                placeholder="请选择销售单位" 
                clearable 
                style="width: 100%"
              >
                <el-option
                  v-for="item in uomList"
                  :key="item.uomId"
                  :label="item.uomName"
                  :value="item.uomId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料类型" prop="materialType">
              <el-select v-model="form.materialType" placeholder="请选择物料类型">
                <el-option v-for="dict in bd_material_type" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型">
                <el-option v-for="dict in bd_source_type" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次管理" prop="isBatchManage">
              <el-radio-group v-model="form.isBatchManage">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="序列号管理" prop="isSerialManage">
              <el-radio-group v-model="form.isSerialManage">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安全库存" prop="safetyStock">
              <el-input-number v-model="form.safetyStock" placeholder="请输入安全库存" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最低库存" prop="minStock">
              <el-input-number v-model="form.minStock" placeholder="请输入最低库存" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最高库存" prop="maxStock">
              <el-input-number v-model="form.maxStock" placeholder="请输入最高库存" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="标准成本" prop="standardCost">
              <el-input-number v-model="form.standardCost" placeholder="请输入标准成本" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参考价格" prop="referencePrice">
              <el-input-number v-model="form.referencePrice" placeholder="请输入参考价格" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质量状态" prop="qualityStatus">
              <el-select 
                v-model="form.qualityStatus" 
                placeholder="请选择质量状态"
                clearable
                style="width: 100%"
              >
                <el-option 
                  v-for="dict in bd_quality_status" 
                  :key="dict.value" 
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="保质期(天)" prop="shelfLife">
              <el-input-number v-model="form.shelfLife" placeholder="请输入保质期(天)" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重量(kg)" prop="weight">
              <el-input-number v-model="form.weight" placeholder="请输入重量(kg)" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="体积(m³)" prop="volume">
              <el-input-number v-model="form.volume" placeholder="请输入体积(m³)" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
        </el-tab-pane>
        <el-tab-pane label="供应商" name="vendor" v-if="form.materialId">
          <el-table v-loading="vendorLoading" :data="vendorList" border size="small">
            <el-table-column label="供应商编码" align="center" prop="vendorCode" width="120" />
            <el-table-column label="供应商名称" align="center" prop="vendorName" min-width="150" />
            <el-table-column label="单价" align="center" prop="price" width="100" />
            <el-table-column label="币种" align="center" prop="currency" width="80" />
            <el-table-column label="供货周期(天)" align="center" prop="leadTime" width="110" />
            <el-table-column label="最小订货量" align="center" prop="minOrderQty" width="110" />
            <el-table-column label="优先级" align="center" prop="priority" width="80" />
            <el-table-column label="默认" align="center" prop="isDefault" width="70">
              <template #default="scope">
                <el-tag :type="scope.row.isDefault === 1 ? 'success' : 'info'" size="small">
                  {{ scope.row.isDefault === 1 ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" align="center" prop="status" width="70">
              <template #default="scope">
                <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ scope.row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode && activeTab === 'basic'">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 物料分类管理对话框 -->
    <el-dialog :title="categoryTitle" v-model="categoryOpen" width="500px" append-to-body>
      <el-form ref="categoryRef" :model="categoryForm" :rules="categoryRules" label-width="100px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="categoryForm.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="父级分类" prop="parentId">
          <el-tree-select
            v-model="categoryForm.parentId"
            :data="categoryTree"
            node-key="id"
            :props="defaultProps"
            check-strictly
            placeholder="请选择父级分类"
          />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="categoryForm.sortOrder" controls-position="right" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitCategoryForm">确 定</el-button>
          <el-button @click="cancelCategory">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Material">
import { listMaterial, getMaterial, delMaterial, addMaterial, updateMaterial } from "@/api/erp/bd/material"
import { listCategory, getCategory, addCategory, updateCategory,delCategory,listDeptExcludeChild,getAllChildrenIds } from "@/api/erp/bd/category"
import { listMeasureAll } from "@/api/erp/bd/measure"
import { listMaterialVendor } from "@/api/erp/bd/material-vendor"
import { genCode } from "@/api/system/autocode/rule"
import { debounce } from 'lodash'

const { proxy } = getCurrentInstance()
const { sys_true_or_false, bd_source_type, bd_material_type, bd_quality_status } = toRefs<any>(proxy.useDict('sys_true_or_false', 'bd_source_type', 'bd_material_type', 'bd_quality_status'))

const materialList = ref([])
const open = ref(false)
const categoryOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const categoryTitle = ref("")
const isViewMode = ref(false)
const categoryTree = ref([])
const categoryTreeRef = ref()
const uomList  = ref([])
// 添加分类树过滤相关数据
const categoryFilterText = ref('')
// 供应商Tab相关数据
const activeTab = ref("basic")
const vendorList = ref([])
const vendorLoading = ref(false)

const defaultProps = {
  children: 'children',
  label: 'categoryName'
}

// 创建防抖函数
const debouncedHandleCategoryFilter = debounce(function(val) {
  handleCategoryFilter(val)
}, 300)

const data = reactive({
  form: {},
  categoryForm: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    materialType: null,
    sourceType: null,
    isBatchManage: null,
    isSerialManage: null,
    status: null,
    qualityStatus: null,
    categoryId: null,
    categoryIds: null,
    autoGenFlag: false
  },
  rules: {
    materialCode: [
      { required: true, message: "物料编码不能为空", trigger: "blur" }
    ],
    materialName: [
      { required: true, message: "物料名称不能为空", trigger: "blur" }
    ],
    categoryId: [
      { required: true, message: "物料分类不能为空", trigger: "blur" }
    ],
    materialType: [
      { required: true, message: "物料类型不能为空", trigger: "change" }
    ],
    safetyStock: [
      { required: true, message: "安全库存不能为空", trigger: "blur" }
    ]
  },
  categoryRules: {
    name: [
      { required: true, message: "分类名称不能为空", trigger: "blur" }
    ]
  }
})

const { queryParams, form, rules, categoryForm, categoryRules } = toRefs(data)

/** 查询物料产品主列表 */
function getList() {
  loading.value = true
  listMaterial(queryParams.value).then(response => {
    // 检查响应格式并相应处理
    if (response.data && response.data.materials) {
      // 新的响应格式，直接包含物料数据
      materialList.value = response.data.materials
      total.value = response.data.materials.length
    } else {
      // 原有的响应格式
      materialList.value = response.rows
      total.value = response.total
    }
    loading.value = false
  }).catch(error => {
    loading.value = false
    proxy.$modal.msgError("获取物料数据失败")
    console.error(error)
  })
}
/** 查询物料分类树 */
function getCategoryTree() {
  listCategory().then(response => {
    // 处理分类树数据，确保数据结构符合 el-tree 要求
    // 修改处理逻辑，适配AjaxResult返回格式
    const data = response.data || response.rows || []
    const filteredData = Array.isArray(data) ? data.filter(item => item !== null && item !== undefined) : []
    const treeData = proxy.handleTree(filteredData, "categoryId", "parentId");
    categoryTree.value = treeData;
  })
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 取消分类按钮
function cancelCategory() {
  categoryOpen.value = false
  resetCategory()
}

// 表单重置
function reset() {
  form.value = {
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    materialDesc: null,
    categoryId: null,
    baseUomId: null,
    purchaseUomId: null,
    inventoryUomId: null,
    saleUomId: null,
    materialType: null,
    sourceType: null,
    isBatchManage: 0,
    isSerialManage: 0,
    safetyStock: 0,
    minStock: 0,
    maxStock: 0,
    standardCost: 0,
    referencePrice: 0,
    status: 1,
    qualityStatus: "PENDING",
    shelfLife: 0,
    weight: 0,
    volume: 0,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  proxy.resetForm("materialRef")
  data.autoGenFlag = false
  activeTab.value = "basic"
  vendorList.value = []
}

// 分类表单重置
function resetCategory() {
  categoryForm.value = {
    id: null,
    name: null,
    parentId: null,
    sortOrder: 0
  }
  proxy.resetForm("categoryRef")
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
  queryParams.value.categoryIds = null;
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.materialId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 树节点点击事件 */
function handleNodeClick(data) {
  // 传递 data.categoryId 而不是整个 data 对象
  getAllChildrenIds(data.categoryId).then(response => {
    if (response.code === 200) {
      // 从响应中提取分类ID列表和物料数据
      if (response.data && response.data.categories) {
        // 提取所有分类的ID
        const categoryIds = response.data.categories.map(category => category.categoryId);
        
        // 设置查询参数，支持多个分类ID查询
        queryParams.value.categoryIds = categoryIds;
        // 清除单个分类ID参数，避免冲突
        queryParams.value.categoryId = null;                                                              
        // 如果返回了物料数据，直接使用
        if (response.data.materials) {
          materialList.value = response.data.materials;
          total.value = response.data.materials.length;
        } else {
          // 如果没有返回物料数据，则调用getList获取数据
          handleQuery();
        }
      } else {
        // 保持原有逻辑作为备选方案
        const categoryIds = [data.categoryId];
        if (response.data && Array.isArray(response.data)) {
          categoryIds.push(...response.data);
        }
        
        queryParams.value.categoryIds = categoryIds;
        queryParams.value.categoryId = null;
        handleQuery();
      }
    } else {
      proxy.$modal.msgError("获取分类信息失败");
    }
  }).catch(error => {
    proxy.$modal.msgError("请求出现异常");
    console.error(error);
  });
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false
  activeTab.value = "basic"
  open.value = true
  title.value = "添加物料/产品"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  isViewMode.value = false
  activeTab.value = "basic"
  const _materialId = row.materialId || ids.value
  getMaterial(_materialId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改物料/产品"
    loadVendorList(_materialId)
  })
}

/** 查看按钮操作 */
function handleView(row) {
  reset()
  isViewMode.value = true
  activeTab.value = "basic"
  const _materialId = row.materialId || ids.value
  getMaterial(_materialId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看物料产品主"
    loadVendorList(_materialId)
  })
}

/** 加载物料关联的供应商列表 */
function loadVendorList(materialId) {
  vendorLoading.value = true
  listMaterialVendor({ materialId: materialId, pageNum: 1, pageSize: 100 }).then(response => {
    vendorList.value = response.rows || []
    vendorLoading.value = false
  }).catch(() => {
    vendorLoading.value = false
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["materialRef"].validate(valid => {
    if (valid) {
      if (form.value.materialId != null) {
        updateMaterial(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addMaterial(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _materialIds = row.materialId || ids.value
  proxy.$modal.confirm('是否确认删除物料产品主编号为"' + _materialIds + '"的数据项？').then(function () {
    return delMaterial(_materialIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('bd/material/export', {
    ...queryParams.value
  }, `material_${new Date().getTime()}.xlsx`)
}

/** 提交分类表单 */
function submitCategoryForm() {
  proxy.$refs["categoryRef"].validate(valid => {
    if (valid) {
      if (categoryForm.value.id != null) {
        updateMaterialCategory(categoryForm.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          categoryOpen.value = false
          getCategoryTree()
        })
      } else {
        addMaterialCategory(categoryForm.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          categoryOpen.value = false
          getCategoryTree()
        })
      }
    }
  })
}

// 添加分类树过滤方法
function filterCategoryNode(value, data) {
  if (!value) return true;
  return data.categoryName.includes(value);
}

// 添加处理分类过滤输入
function handleCategoryFilter(val) {
  categoryTreeRef.value.filter(val)
}

//自动生成编码
function handleAutoGenChange(autoGenFlag){
  if(autoGenFlag){
    genCode('MATERIAL_CODE').then(response =>{
      form.value.materialCode = response;
    });
  }else{
    form.value.materialCode = null;
  }
}

/** 查询计量单位列表 */
function getUomList() {
  listMeasureAll().then(response => {
    uomList.value = response.data;
  })
}

// 在 onMounted 之前添加以下代码
watch(() => form.value.safetyStock, (newVal) => {
  if (newVal && newVal !== 0) {
    form.value.minStock = newVal * 1.5;
  }
});

watch(() => form.value.minStock, (newVal) => {
  if (newVal && newVal !== 0) {
    form.value.maxStock = newVal * 3;
  }
});

onMounted(() => {
  getList()
  getCategoryTree()
  getUomList() 
})

// 添加组件销毁时的清理逻辑
onUnmounted(() => {
  // 取消防抖函数的执行
  if (debouncedHandleCategoryFilter) {
    debouncedHandleCategoryFilter.cancel()
  }
})
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