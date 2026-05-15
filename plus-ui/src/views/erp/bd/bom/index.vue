<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
                  <el-form-item label="BOM名称" prop="bomName">
                    <el-input
                        v-model="queryParams.bomName"
                        placeholder="请输入BOM名称"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="生效日期" prop="effectiveDate">
                    <el-date-picker clearable
                                    v-model="queryParams.effectiveDate"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择生效日期">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="失效日期" prop="expiryDate">
                    <el-date-picker clearable
                                    v-model="queryParams.expiryDate"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择失效日期">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="是否当前版本" prop="isCurrent">
                    <el-select v-model="queryParams.isCurrent" placeholder="请选择是否当前版本" clearable>
                      <el-option
                          v-for="dict in sys_true_or_false"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="状态" prop="status">
                    <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
                      <el-option
                          v-for="dict in sys_core_status"
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
        <el-button
            type="primary"
            plain
            icon="Plus"
            @click="handleAdd"
            v-hasPermi="['bd:bom:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="single"
            @click="handleUpdate"
            v-hasPermi="['bd:bom:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['bd:bom:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="info"
            plain
            icon="View"
            :disabled="single"
            @click="handleView"
            v-hasPermi="['bd:bom:query']"
        >查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="warning"
            plain
            icon="Download"
            @click="handleExport"
            v-hasPermi="['bd:bom:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="bomList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="序号" type="index" width="50" align="center" prop="bomId" />
              <el-table-column label="BOM版本号" align="center" prop="bomVersion" />
              <el-table-column label="BOM名称" align="center" prop="bomName" />
              <el-table-column label="生效日期" align="center" prop="effectiveDate" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.effectiveDate, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="失效日期" align="center" prop="expiryDate" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.expiryDate, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="是否当前版本" align="center" prop="isCurrent">
                <template #default="scope">
                      <dict-tag :options="sys_true_or_false" :value="scope.row.isCurrent"/>
                </template>
              </el-table-column>
              <el-table-column label="状态" align="center" prop="status">
                <template #default="scope">
                      <dict-tag :options="sys_core_status" :value="scope.row.status"/>
                </template>
              </el-table-column>
              <el-table-column label="来源类型" align="center" prop="sourceType">
                <template #default="scope">
                  <dict-tag :options="prd_material_source_type" :value="scope.row.sourceType" />
                </template>
              </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['bd:bom:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['bd:bom:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
        v-show="total>0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
    />

    <!-- 添加或修改产品BOM对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="bomRef" :model="form" :rules="rules" label-width="120px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="父项物料信息" prop="parentMaterialId">
              <el-row :gutter="10">
                <el-col :span="18">
                  <el-input v-model="form.parentMaterialName" placeholder="请选择父项物料信息" readonly>
                    <template #append>
                      <el-button v-if="!isViewMode" icon="Search" @click="openMaterialSelect" />
                    </template>
                  </el-input>
                </el-col>
                <el-col :span="6">
                  <el-button v-if="form.parentMaterialId && !isViewMode" type="danger" icon="Delete" @click="clearMaterial" />
                </el-col>
              </el-row>
              <el-input v-model="form.parentMaterialId" style="display: none" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="BOM版本号" prop="bomVersion">
              <el-input v-model="form.bomVersion" placeholder="请输入BOM版本号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="BOM名称信息" prop="bomName">
              <el-input v-model="form.bomName" placeholder="请输入BOM名称信息" />
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
                              style="width: 100%">
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
                              style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否当前版本" prop="isCurrent">
              <el-radio-group v-model="form.isCurrent">
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
              <el-radio-group v-model="form.status">
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
          <el-col :span="12">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型" clearable>
                <el-option
                    v-for="dict in prd_material_source_type"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注信息" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注信息" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
    
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

<script setup lang="ts" name="Bom">
import { listBom, getBom, delBom, addBom, updateBom } from "@/api/erp/bd/bom"
import { listMaterial, getMaterial } from "@/api/erp/bd/material"
import { listCategory,getAllChildrenIds } from "@/api/erp/bd/category"
import { useRouter } from 'vue-router'
import { debounce } from 'lodash'

  const router = useRouter();
  const { proxy } = getCurrentInstance()
  const { sys_true_or_false, sys_core_status, bd_material_type, prd_material_source_type } = toRefs<any>(proxy.useDict('sys_true_or_false', 'sys_core_status', 'bd_material_type', 'prd_material_source_type'))

  const bomList = ref([])
  const open = ref(false)
  const loading = ref(true)
  const showSearch = ref(true)
  const ids = ref([])
  const single = ref(true)
  const multiple = ref(true)
  const total = ref(0)
  const title = ref("")
  const isViewMode = ref(false) // 新增：标识是否为查看模式
  
  // 物料选择相关变量
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
  
  const data = reactive({
    form: {},
    queryParams: {
      pageNum: 1,
      pageSize: 10,
                    bomName: null,
                    effectiveDate: null,
                    expiryDate: null,
                    isCurrent: null,
                    status: null,
    },
    rules: {
      parentMaterialId: [
        { required: true, message: "父项物料不能为空", trigger: "change" }
      ],
      bomVersion: [
        { required: true, message: "BOM版本号不能为空", trigger: "blur" }
      ],
      bomName: [
        { required: true, message: "BOM名称不能为空", trigger: "blur" }
      ],
    },
    // 物料查询参数
    materialQueryParams: {
      pageNum: 1,
      pageSize: 10,
      materialCode: null,
      materialName: null,
      categoryId: null,
      categoryIds: []  // 初始化为空数组
    }
  })

  const { queryParams, form, rules, materialQueryParams } = toRefs(data)

  /** 查询产品BOM列表 */
  function getList() {
    loading.value = true
    listBom(queryParams.value).then(response => {
            bomList.value = response.rows
      total.value = response.total
      loading.value = false
    })
  }
  
  /** 查询物料列表 - 用于初始化和搜索 */
  function getMaterialList() {
    materialLoading.value = true
    
    // 构造查询参数
    const params = {}
    
    // 添加基础参数
    if (materialQueryParams.value.pageNum) params.pageNum = materialQueryParams.value.pageNum
    if (materialQueryParams.value.pageSize) params.pageSize = materialQueryParams.value.pageSize
    if (materialQueryParams.value.materialCode) params.materialCode = materialQueryParams.value.materialCode
    if (materialQueryParams.value.materialName) params.materialName = materialQueryParams.value.materialName
    
    // 只显示启用状态的物料 (status = 1)
    params.status = 1

    console.log('发送物料查询参数:', params) // 调试用
    
    listMaterial(params).then(response => {
      materialList.value = response.rows
      materialTotal.value = response.total
      materialLoading.value = false
    }).catch((error) => {
      console.error('获取物料列表失败:', error)
      materialLoading.value = false
      proxy.$modal.msgError("获取物料列表失败")
    })
  }
  
  /** 搜索物料 */
  function handleMaterialQuery() {
    materialQueryParams.value.pageNum = 1
    getMaterialList()
  }
  
  /** 重置物料查询 */
  function resetMaterialQuery() {
    proxy.resetForm("materialQueryRef")
    materialQueryParams.value.materialCode = null
    materialQueryParams.value.materialName = null
    materialQueryParams.value.categoryId = null
    materialQueryParams.value.categoryIds = []  // 重置为数组
    handleMaterialQuery()
  }
  
  /** 打开物料选择对话框 */
  function openMaterialSelect() {
    materialSelectOpen.value = true
    materialDialogTitle.value = "选择物料"
    // 每次打开时重新加载物料列表
    getMaterialList()
  }
  
  /** 选择物料 */
  function selectMaterial(row) {
    form.value.parentMaterialId = row.materialId
    form.value.parentMaterialName = row.materialName
    materialSelectOpen.value = false
  }
  
  /** 清除已选择的物料 */
  function clearMaterial() {
    form.value.parentMaterialId = null
    form.value.parentMaterialName = null
  }
  
  // 取消按钮
  function cancel() {
    open.value = false
    reset()
  }

  // 表单重置
  function reset() {
    form.value = {
                    bomId: null,
                    parentMaterialId: null,
                    parentMaterialName: null,
                    bomVersion: "1.0",
                    bomName: null,
                    effectiveDate: null,
                    expiryDate: null,
                    isCurrent: null,
                    status: null,
                    remark: null,
                    sourceType: null,
                    attr1: null,
                    attr2: null,
                    attr3: null,
                    attr4: null,
                    createBy: null,
                    createTime: null,
                    updateBy: null,
                    updateTime: null
    }
    proxy.resetForm("bomRef")
  }

  /** 搜索按钮操作 */
  function handleQuery() {
    queryParams.value.pageNum = 1
    getList()
  }

  /** 重置按钮操作 */
  function resetQuery() {
    proxy.resetForm("queryRef")
    handleQuery()
  }

  // 多选框选中数据
  function handleSelectionChange(selection) {
    ids.value = selection.map(item => item.bomId)
    single.value = selection.length != 1
    multiple.value = !selection.length
  }

  /** 新增按钮操作 */
  function handleAdd() {
    reset()
    isViewMode.value = false // 设置为编辑模式
    open.value = true
    title.value = "添加产品BOM"
  }

  /** 修改按钮操作 */
  function handleUpdate(row) {
    if (row && row.type && row instanceof Event) {
      // 这是一个事件对象，表示没有传入数据参数
      row = null;
    }
    
    // 如果没有传入row参数，则从选中的数据中获取
    if (!row) {
      if (ids.value.length !== 1) {
        proxy.$modal.msgWarning("请选择一条数据进行修改");
        return;
      }
      // 从列表中找到选中的记录
      const selectedRow = bomList.value.find(item => item.bomId == ids.value[0]);
      if (!selectedRow) {
        proxy.$modal.msgWarning("未找到选中的数据");
        return;
      }
      row = selectedRow;
    }

    const bomId = row.bomId;
    router.push({
      path: '/erp/bd/bom/form',
      query: { id: bomId, action: 'edit' }
    });
  }

  /** 查看按钮操作 */
  function handleView(row) {
    if (row && row.type && row instanceof Event) {
      // 这是一个事件对象，表示没有传入数据参数
      row = null;
    }
    
    // 如果没有传入row参数，则从选中的数据中获取
    if (!row) {
      if (ids.value.length !== 1) {
        proxy.$modal.msgWarning("请选择一条数据进行查看");
        return;
      }
      // 从列表中找到选中的记录
      const selectedRow = bomList.value.find(item => item.bomId == ids.value[0]);
      if (!selectedRow) {
        proxy.$modal.msgWarning("未找到选中的数据");
        return;
      }
      row = selectedRow;
    }

    const bomId = row.bomId;
    router.push({
      path: '/erp/bd/bom/form',
      query: { id: bomId, action: 'view' }
    });
  }

  /** 提交按钮 */
  function submitForm() {
    proxy.$refs["bomRef"].validate(valid => {
      if (valid) {
        if (form.value.bomId != null) {
          updateBom(form.value).then(response => {
            proxy.$modal.msgSuccess("修改成功")
            open.value = false
            getList()
          })
        } else {
          addBom(form.value).then(response => {
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
    const _bomIds = row.bomId || ids.value
    proxy.$modal.confirm('是否确认删除产品BOM编号为"' + _bomIds + '"的数据项？').then(function() {
      return delBom(_bomIds)
    }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    }).catch(() => {})
  }

  /** 导出按钮操作 */
  function handleExport() {
    proxy.download('bd/bom/export', {
      ...queryParams.value
    }, `bom_${new Date().getTime()}.xlsx`)
  }

  // 获取物料分类树
  function getCategoryTree() {
  // 只获取启用状态的分类 (status = 1)
  listCategory({ status: 1 }).then(response => {
    // 处理分类树数据，确保数据结构符合 el-tree 要求
    // 修改处理逻辑，适配AjaxResult返回格式
    const data = response.data || response.rows || []
    const filteredData = Array.isArray(data) ? data.filter(item => item !== null && item !== undefined) : []
    const treeData = proxy.handleTree(filteredData, "categoryId", "parentId");
    categoryTree.value = treeData;
  }).catch(error => {
    console.error('获取物料分类失败:', error)
    proxy.$modal.msgError("获取物料分类失败")
  })
}

  // 处理物料分类节点点击 - 使用getAllChildrenIds接口返回的物料数据
  function handleCategoryNodeClick(data) {
    console.log('点击的分类数据:', data) // 调试用
    
    // 确保传给getAllChildrenIds的是数字类型的ID
    const categoryId = Number(data.id || data.categoryId)
    
    if (isNaN(categoryId) || categoryId <= 0) {
      console.error('无效的分类ID:', categoryId)
      proxy.$modal.msgError("无效的分类ID")
      return
    }
    
    console.log('传递给getAllChildrenIds的参数:', categoryId) // 调试用
    
    // 使用getAllChildrenIds方法获取分类信息和物料数据
    materialLoading.value = true
  getAllChildrenIds(categoryId).then(response => {
    console.log('getAllChildrenIds响应:', response) // 调试用
    
    if (response.code === 200) {
      // 检查响应中是否包含物料数据
      if (response.data && response.data.materials && Array.isArray(response.data.materials)) {
        // 只显示启用状态的物料 (status = 1)
        const enabledMaterials = response.data.materials.filter(material => material.status === 1);
        materialList.value = enabledMaterials
        materialTotal.value = enabledMaterials.length
        materialLoading.value = false
      } else {
        // 如果没有返回物料数据，则构造categoryIds数组并调用listMaterial
        const categoryIds = []
        categoryIds.push(categoryId)
        
        // 添加所有子分类ID
        if (response.data && Array.isArray(response.data) && response.data.length > 0) {
          response.data.forEach(id => {
            const childId = Number(id)
            if (!isNaN(childId) && childId > 0 && !categoryIds.includes(childId)) {
              categoryIds.push(childId)
            }
          })
        }
        
        console.log('构造的categoryIds数组:', categoryIds) // 调试用
        
        // 调用物料列表接口，只获取启用状态的物料
        const params = {
          pageNum: 1,
          pageSize: 9999, // 获取所有数据
          categoryIds: categoryIds,
          status: 1 // 只显示启用状态的物料
        }
        
        listMaterial(params).then(materialResponse => {
          materialList.value = materialResponse.rows
          materialTotal.value = materialResponse.total
          materialLoading.value = false
        }).catch(error => {
          console.error('获取物料列表失败:', error)
          materialLoading.value = false
          proxy.$modal.msgError("获取物料列表失败")
        })
      }
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

  onMounted(() => {
    getList()
    getCategoryTree()  // 确保调用获取分类树的方法
  })

  // 添加组件销毁时的清理逻辑
  onUnmounted(() => {
    // 取消防抖函数的执行
    if (debouncedHandleCategoryFilter && typeof debouncedHandleCategoryFilter.cancel === 'function') {
      debouncedHandleCategoryFilter.cancel()
    }
  })
</script>