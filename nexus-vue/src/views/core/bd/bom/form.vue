<template>
  <div class="app-container">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <!-- 操作按钮区域 - 放在最上方 -->
      <el-row :gutter="20" class="mb20">
        <el-col :span="12">
          <span class="bom-title">BOM清单</span>
        </el-col>
        <el-col :span="12" class="text-align-right">
          <el-button type="primary" @click="handleSave" v-if="!isViewMode">保存</el-button>
          <el-button @click="handleClose">关闭</el-button>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="父项物料信息" prop="parentMaterialName">
            <el-input v-model="form.parentMaterialName" placeholder="父项物料信息" readonly />
            <el-input v-model="form.parentMaterialId" style="display: none" />
          </el-form-item>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="BOM版本号" prop="bomVersion">
            <el-input v-model="form.bomVersion" placeholder="请输入BOM版本号" :readonly="isViewMode" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="BOM名称信息" prop="bomName">
            <el-input v-model="form.bomName" placeholder="请输入BOM名称信息" :readonly="isViewMode" />
          </el-form-item>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="生效日期" prop="effectiveDate">
            <el-date-picker clearable
                            v-model="form.effectiveDate"
                            type="date"
                            value-format="YYYY-MM-DD"
                            placeholder="请选择生效日期"
                            style="width: 100%"
                            :readonly="isViewMode">
            </el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="失效日期" prop="expiryDate">
            <el-date-picker clearable
                            v-model="form.expiryDate"
                            type="date"
                            value-format="YYYY-MM-DD"
                            placeholder="请选择失效日期"
                            style="width: 100%"
                            :readonly="isViewMode">
            </el-date-picker>
          </el-form-item>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="是否当前版本" prop="isCurrent">
            <el-radio-group v-model="form.isCurrent" :disabled="isViewMode">
              <el-radio
                  v-for="dict in sys_true_or_false"
                  :key="dict.value"
                  :label="parseInt(dict.value)"
              >{{dict.label}}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态信息" prop="status">
            <el-radio-group v-model="form.status" :disabled="isViewMode">
              <el-radio
                  v-for="dict in sys_core_status"
                  :key="dict.value"
                  :label="parseInt(dict.value)"
              >{{dict.label}}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="备注信息" prop="remark">
            <el-input v-model="form.remark" type="textarea" placeholder="请输入备注信息" :readonly="isViewMode" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    
    <!-- BOM子项物料信息表格 -->
    <div class="material-section">
      <div class="section-header">
        <span class="section-title">物料信息</span>
        <el-button type="primary" @click="addMaterialRow" class="add-btn" v-if="!isViewMode">新增物料</el-button>
      </div>
      
      <el-table :data="materialItems" style="width: 100%" class="material-table">
        <el-table-column type="index" width="50" align="center" label="序号" />
        <el-table-column label="子项物料" align="center" prop="childMaterialName" min-width="200">
          <template #default="scope">
            <el-input v-model="scope.row.childMaterialName" placeholder="请选择子项物料" readonly v-show="isViewMode" />
            <el-input v-model="scope.row.childMaterialId" style="display: none" />
            <el-row :gutter="10" v-show="!isViewMode">
              <el-col :span="18">
                <el-input v-model="scope.row.childMaterialName" placeholder="请选择子项物料" readonly>
                  <template #append>
                    <el-button icon="Search" @click="openMaterialSelect(scope.$index)" />
                  </template>
                </el-input>
              </el-col>
              <el-col :span="6">
                <el-button v-if="scope.row.childMaterialId" type="danger" icon="Delete" @click="clearMaterial(scope.$index)" />
              </el-col>
            </el-row>
          </template>
        </el-table-column>
        <el-table-column label="单位父项用量" align="center" prop="quantityPerParent" width="140">
          <template #default="scope">
            <el-input-number 
              v-model="scope.row.quantityPerParent" 
              :min="0" 
              controls-position="right" 
              :disabled="isViewMode"
              :precision="6"
              style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="损耗率(%)" align="center" prop="lossRate" width="140">
          <template #default="scope">
            <el-input-number 
              v-model="scope.row.lossRate" 
              :min="0" 
              :max="100"
              controls-position="right" 
              :disabled="isViewMode"
              :precision="4"
              style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="工序步骤" align="center" prop="operationStep" width="140">
          <template #default="scope">
            <el-input-number 
              v-model="scope.row.operationStep" 
              :min="0" 
              controls-position="right" 
              :disabled="isViewMode"
              style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="提前期(小时)" align="center" prop="leadTime" width="140">
          <template #default="scope">
            <el-input-number 
              v-model="scope.row.leadTime" 
              :min="0" 
              controls-position="right" 
              :disabled="isViewMode"
              style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="生效日期" align="center" prop="effectiveDate" width="140">
          <template #default="scope">
            <el-date-picker
              v-model="scope.row.effectiveDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择生效日期"
              style="width: 100%"
              :readonly="isViewMode" />
          </template>
        </el-table-column>
        <el-table-column label="失效日期" align="center" prop="expiryDate" width="140">
          <template #default="scope">
            <el-date-picker
              v-model="scope.row.expiryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择失效日期"
              style="width: 100%"
              :readonly="isViewMode" />
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status" width="100">
          <template #default="scope">
            <el-select v-model="scope.row.status" placeholder="请选择状态"  disabled style="width: 100%">
              <el-option
                v-for="dict in sys_core_status"
                :key="dict.value"
                :label="dict.label"
                :value="parseInt(dict.value)" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100" v-if="!isViewMode">
          <template #default="scope">
            <el-button link type="danger" @click="removeMaterialRow(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    
    <!-- 物料选择对话框 -->
    <el-dialog :title="materialDialogTitle" v-model="materialSelectOpen" width="1200px" append-to-body>
      <el-row :gutter="20">
        <!-- 左侧物料分类树 -->
        <el-col :span="6">
          <div class="category-tree-container">
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
            <el-tree
              ref="categoryTreeRef"
              class="category-tree"
              :data="categoryTree"
              :props="categoryTreeProps"
              :expand-on-click-node="false"
              :highlight-current="true"
              node-key="id"
              default-expand-all
              :filter-node-method="filterCategoryNode"
              @node-click="handleCategoryNodeClick"
            />
          </div>
        </el-col>
        
        <!-- 右侧物料列表 -->
        <el-col :span="18">
          <el-form :model="materialQueryParams" ref="materialQueryRef" :inline="true" label-width="68px">
            <el-form-item label="物料编码" prop="materialCode">
              <el-input
                v-model="materialQueryParams.materialCode"
                placeholder="请输入物料编码"
                clearable
                @keyup.enter="handleMaterialQuery"
              />
            </el-form-item>
            <el-form-item label="物料名称" prop="materialName">
              <el-input
                v-model="materialQueryParams.materialName"
                placeholder="请输入物料名称"
                clearable
                @keyup.enter="handleMaterialQuery"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleMaterialQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetMaterialQuery">重置</el-button>
            </el-form-item>
          </el-form>
          
          <el-table 
            v-loading="materialLoading" 
            :data="materialList" 
            @row-dblclick="selectMaterial"
            highlight-current-row
            style="width: 100%"
            height="400"
          >
            <el-table-column type="index" width="50" align="center" />
            <el-table-column label="物料编码" align="center" prop="materialCode" />
            <el-table-column label="物料名称" align="center" prop="materialName" />
            <el-table-column label="规格型号" align="center" prop="materialSpec" />
            <el-table-column label="物料类型" align="center" prop="materialType">
              <template #default="scope">
                <dict-tag :options="bd_material_type" :value="scope.row.materialType"/>
              </template>
            </el-table-column>
            <el-table-column label="状态" align="center" prop="status">
              <template #default="scope">
                <dict-tag :options="sys_true_or_false" :value="scope.row.status"/>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="100">
              <template #default="scope">
                <el-button link type="primary" @click="selectMaterial(scope.row)">选择</el-button>
              </template>
            </el-table-column>
          </el-table>
          
          <pagination
            v-show="materialTotal > 0"
            :total="materialTotal"
            v-model:page="materialQueryParams.pageNum"
            v-model:limit="materialQueryParams.pageSize"
            @pagination="getMaterialList"
          />
        </el-col>
      </el-row>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="materialSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="BomForm">
import { getBom, addBom, updateBom, listBomDetails, addBomDetail, updateBomDetail, delBomDetail, saveBomDetailsBatch } from "@/api/core/bd/bom"
import { listMaterial, getMaterial } from "@/api/core/bd/material"
import { listCategory,getAllChildrenIds } from "@/api/core/bd/category"
import { useRouter, useRoute } from 'vue-router'
import { debounce } from 'lodash'

const router = useRouter()
const route = useRoute()
const { proxy } = getCurrentInstance()
const { sys_true_or_false, sys_core_status, bd_material_type } = proxy.useDict('sys_true_or_false', 'sys_core_status', 'bd_material_type')

// 表单相关
const formRef = ref(null)
const form = ref({
  bomId: null,
  parentMaterialId: null,
  parentMaterialName: null,
  bomVersion: "1.0",
  bomName: null,
  effectiveDate: null,
  expiryDate: null,
  isCurrent: null,
  status: null,
  remark: null
})

// 查看模式标识
const isViewMode = ref(false)

// 当前正在选择物料的行索引
const currentMaterialRowIndex = ref(-1)

const rules = ref({
  parentMaterialName: [
    { required: true, message: "父项物料不能为空", trigger: "change" }
  ],
  bomVersion: [
    { required: true, message: "BOM版本号不能为空", trigger: "blur" }
  ],
  bomName: [
    { required: true, message: "BOM名称不能为空", trigger: "blur" }
  ]
})

// 物料子项表格数据
const materialItems = ref([
  // 默认添加一行空数据
  {
    detailId: null,
    childMaterialId: null,
    childMaterialName: '',
    quantityPerParent: 1.000000,
    lossRate: 0.0000,
    operationStep: null,
    leadTime: 0,
    effectiveDate: null,
    expiryDate: null,
    status: 1,
    remark: ''
  }
])

// 物料选择相关
const materialSelectOpen = ref(false)
const materialList = ref([])
const materialLoading = ref(false)
const materialTotal = ref(0)
const materialDialogTitle = ref("选择物料")

// 物料分类相关
const categoryTree = ref([])
const categoryTreeProps = { 
  children: 'children', 
  label: 'categoryName',
  value: 'categoryId'
}
const categoryTreeRef = ref(null)
const categoryFilterText = ref('')

// 创建防抖函数
const debouncedHandleCategoryFilter = debounce(function(val) {
  handleCategoryFilter(val)
}, 300)

const materialQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  materialCode: null,
  materialName: null,
  categoryId: null,
  categoryIds: []  // 初始化为空数组
})

// 页面加载时初始化数据
onMounted(() => {
  const id = route.query.id
  const action = route.query.action
  
  // 设置查看模式
  isViewMode.value = action === 'view'
  
  // 获取物料分类树
  getCategoryTree()
  
  if (id) {
    getBomDetails(id)
  }
})

// 获取BOM详情
function getBomDetails(id) {
  // 获取BOM主表信息
  getBom(id).then(response => {
    console.log('BOM详情数据:', response.data);
    form.value = response.data;
    
    // 如果有 parentMaterialId 但没有 parentMaterialName，则查询物料信息
    if (form.value.parentMaterialId) {
      getMaterialNameById(form.value.parentMaterialId);
    }
    
    // 获取BOM子表信息
    getBomDetailsList(id);
  })
}

// 获取BOM子表数据
function getBomDetailsList(bomId) {
  listBomDetails(bomId).then(response => {
    if (response.rows && response.rows.length > 0) {
      materialItems.value = response.rows.map(item => ({
        detailId: item.detailId || null,
        childMaterialId: item.childMaterialId || null,
        childMaterialName: item.childMaterialName || '',
        quantityPerParent: item.quantityPerParent !== undefined ? item.quantityPerParent : 1.000000,
        lossRate: item.lossRate !== undefined ? item.lossRate : 0.0000,
        operationStep: item.operationStep || null,
        leadTime: item.leadTime !== undefined ? item.leadTime : 0,
        effectiveDate: item.effectiveDate || null,
        expiryDate: item.expiryDate || null,
        status: item.status !== undefined ? item.status : 1,
        remark: item.remark || ''
      }));
    } else {
      // 如果没有子表数据，保持默认的一行空数据
      materialItems.value = [{
        detailId: null,
        childMaterialId: null,
        childMaterialName: '',
        quantityPerParent: 1.000000,
        lossRate: 0.0000,
        operationStep: null,
        leadTime: 0,
        effectiveDate: null,
        expiryDate: null,
        status: 1,
        remark: ''
      }];
    }
  }).catch(() => {
    console.warn('获取BOM明细数据失败');
  })
}

// 新增方法：通过ID获取物料名称
function getMaterialNameById(materialId) {
  getMaterial(materialId).then(response => {
    form.value.parentMaterialName = response.data.materialName;
  }).catch(() => {
    console.warn('获取物料名称失败');
  })
}

// 查询物料列表
function getMaterialList() {
  materialLoading.value = true
  
  const params = {}
  
  // 添加基础参数
  if (materialQueryParams.value.pageNum) params.pageNum = materialQueryParams.value.pageNum
  if (materialQueryParams.value.pageSize) params.pageSize = materialQueryParams.value.pageSize
  if (materialQueryParams.value.materialCode) params.materialCode = materialQueryParams.value.materialCode
  if (materialQueryParams.value.materialName) params.materialName = materialQueryParams.value.materialName
  if (materialQueryParams.value.categoryId) params.categoryId = materialQueryParams.value.categoryId
  
  // 只显示启用状态的物料 (status = 1)
  params.status = 1

  // 处理 categoryIds 参数
  if (materialQueryParams.value.categoryIds && 
      Array.isArray(materialQueryParams.value.categoryIds) && 
      materialQueryParams.value.categoryIds.length > 0) {
    const validCategoryIds = materialQueryParams.value.categoryIds
      .map(id => Number(id))
      .filter(id => !isNaN(id) && id > 0)
    
    if (validCategoryIds.length > 0) {
      params.categoryIds = validCategoryIds
    }
  }
  
  console.log('发送物料查询参数:', params)
  
  listMaterial(params).then(response => {
    // 更宽松的状态过滤，处理字符串和数字类型
    const enabledMaterials = response.rows.filter(material => {
      return String(material.status) === '1' || material.status === 1;
    });
    materialList.value = enabledMaterials
    materialTotal.value = enabledMaterials.length
    materialLoading.value = false
  }).catch((error) => {
    console.error('获取物料列表失败:', error)
    materialLoading.value = false
    proxy.$modal.msgError("获取物料列表失败")
  })
}

// 搜索物料
function handleMaterialQuery() {
  materialQueryParams.value.pageNum = 1
  getMaterialList()
}

// 重置物料查询
function resetMaterialQuery() {
  proxy.resetForm("materialQueryRef")
  materialQueryParams.value.materialCode = null
  materialQueryParams.value.materialName = null
  materialQueryParams.value.categoryId = null
  materialQueryParams.value.categoryIds = []  // 重置为数组
  handleMaterialQuery()
}

// 打开物料选择对话框
function openMaterialSelect(rowIndex) {
  currentMaterialRowIndex.value = rowIndex
  materialSelectOpen.value = true
  materialDialogTitle.value = "选择物料"
  getMaterialList()
}

// 选择物料
function selectMaterial(row) {
  if (currentMaterialRowIndex.value >= 0) {
    materialItems.value[currentMaterialRowIndex.value].childMaterialId = row.materialId
    materialItems.value[currentMaterialRowIndex.value].childMaterialName = row.materialName
  }
  materialSelectOpen.value = false
  currentMaterialRowIndex.value = -1
}

// 清除已选择的物料
function clearMaterial(rowIndex) {
  materialItems.value[rowIndex].childMaterialId = null
  materialItems.value[rowIndex].childMaterialName = ''
}

// 新增物料行
function addMaterialRow() {
  materialItems.value.push({
    detailId: null,
    childMaterialId: null,
    childMaterialName: '',
    quantityPerParent: 1.000000,
    lossRate: 0.0000,
    operationStep: null,
    leadTime: 0,
    effectiveDate: null,
    expiryDate: null,
    status: 1,
    remark: ''
  })
}

// 删除物料行
function removeMaterialRow(index) {
  if (materialItems.value.length <= 1) {
    proxy.$modal.msgWarning("至少保留一行数据")
    return
  }
  materialItems.value.splice(index, 1)
}

// 保存按钮
function handleSave() {
  formRef.value.validate(valid => {
    if (valid) {
      if (form.value.bomId) {
        // 更新BOM主表和子表
        updateBomAndDetails();
      } else {
        // 新增BOM主表和子表
        addBomAndDetails();
      }
    }
  })
}

// 新增BOM主表和子表数据
function addBomAndDetails() {
  // 先保存主表
  addBom(form.value).then(response => {
    const bomId = response.data.bomId; // 获取新创建的BOM ID
    proxy.$modal.msgSuccess("主表新增成功");
    
    // 保存子表数据
    saveBomDetails(bomId).then(() => {
      proxy.$modal.msgSuccess("子表保存成功");
      handleClose();
    }).catch(error => {
      console.error("保存子表失败:", error);
      proxy.$modal.msgError("子表保存失败");
    });
  }).catch(error => {
    console.error("新增主表失败:", error);
    proxy.$modal.msgError("主表新增失败");
  });
}

// 更新BOM主表和子表数据
function updateBomAndDetails() {
  // 先更新主表
  updateBom(form.value).then(response => {
    proxy.$modal.msgSuccess("主表修改成功");
    
    // 保存子表数据
    saveBomDetails(form.value.bomId).then(() => {
      proxy.$modal.msgSuccess("子表保存成功");
      handleClose();
    }).catch(error => {
      console.error("保存子表失败:", error);
      proxy.$modal.msgError("子表保存失败");
    });
  }).catch(error => {
    console.error("更新主表失败:", error);
    proxy.$modal.msgError("主表更新失败");
  });
}

// 保存BOM明细数据的通用方法
function saveBomDetails(bomId) {
  // 过滤掉空行数据并添加 bomId
  const validMaterialItems = materialItems.value
    .filter(item => item.childMaterialId && item.childMaterialName)
    .map(item => ({
      ...item,
      bomId: bomId
    }));
  
  // 如果有有效的明细数据，则使用批量保存接口
  if (validMaterialItems.length > 0) {
    return saveBomDetailsBatch(validMaterialItems);
  }
  
  return Promise.resolve();
}

// 关闭按钮
function handleClose() {
  //router.push('/core/bd/bom')
  const obj = { path: '/core/bd/bom' }
  proxy.$tab.closeOpenPage(obj)
}

// 获取物料分类树
function getCategoryTree() {
  // 确保分类树也只获取启用状态的分类
  listCategory({ status: 1 }).then(response => {
    // 处理分类树数据，确保数据结构符合 el-tree 要求
    // 修改处理逻辑，适配AjaxResult返回格式
    const data = response.data || response.rows || []
    const filteredData = Array.isArray(data) ? data.filter(item => item !== null && item !== undefined) : []
    const treeData = proxy.handleTree(filteredData, "categoryId", "parentId");
    categoryTree.value = treeData;
    
    console.log('分类树数据:', treeData);
  }).catch(error => {
    console.error('获取物料分类失败:', error)
    proxy.$modal.msgError("获取物料分类失败")
  })
}

// 处理物料分类节点点击 - 使用getAllChildrenIds接口返回的物料数据
function handleCategoryNodeClick(data) {
  console.log('点击的分类数据:', data)
  
  const categoryId = Number(data.id || data.categoryId)
  
  if (isNaN(categoryId) || categoryId <= 0) {
    console.error('无效的分类ID:', categoryId)
    proxy.$modal.msgError("无效的分类ID")
    return
  }
  
  materialLoading.value = true
  getAllChildrenIds(categoryId).then(response => {
    console.log('getAllChildrenIds响应:', response)
    
    if (response.code === 200) {
      let allMaterials = [];
      
      // 处理不同可能的数据结构
      if (response.data && Array.isArray(response.data)) {
        // 直接是物料数组
        allMaterials = response.data;
      } else if (response.data && response.data.materials && Array.isArray(response.data.materials)) {
        // 包含在materials字段中
        allMaterials = response.data.materials;
      }
      
      // 过滤启用状态的物料（更宽松的比较）
      const enabledMaterials = allMaterials.filter(material => {
        return String(material.status) === '1' || material.status === 1;
      });
      
      console.log('过滤前物料数量:', allMaterials.length);
      console.log('过滤后物料数量:', enabledMaterials.length);
      
      materialList.value = enabledMaterials
      materialTotal.value = enabledMaterials.length
      materialLoading.value = false
    } else {
      proxy.$modal.msgError("获取分类信息失败: " + response.msg)
      materialLoading.value = false
    }
  }).catch(error => {
    console.error('获取分类信息异常:', error)
    proxy.$modal.msgError("请求出现异常: " + error.message)
    materialLoading.value = false
  })
}


// 添加分类树过滤方法
function filterCategoryNode(value, data) {
  if (!value) return true
  return data.categoryName && data.categoryName.includes(value)
}

// 添加处理分类过滤输入
function handleCategoryFilter(val) {
  if (categoryTreeRef.value) {
    categoryTreeRef.value.filter(val)
  }
}

// 添加组件销毁时的清理逻辑
onUnmounted(() => {
  // 取消防抖函数的执行
  if (debouncedHandleCategoryFilter && typeof debouncedHandleCategoryFilter.cancel === 'function') {
    debouncedHandleCategoryFilter.cancel()
  }
})
</script>

<style scoped>
.mb20 {
  margin-bottom: 20px;
}

.text-align-right {
  text-align: right;
}

.bom-title {
  font-size: 18px;
  font-weight: bold;
  line-height: 32px;
}

.material-section {
  margin-top: 30px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.section-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.add-btn {
  float: right;
}

.material-table {
  margin-top: 10px;
}

:deep(.el-input-number .el-input__inner) {
  text-align: center;
}

.category-tree-container {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 10px;
  height: 500px;
  overflow-y: auto;
}

.category-tree {
  height: 100%;
}

.category-search-input {
  margin-bottom: 10px;
}
</style>