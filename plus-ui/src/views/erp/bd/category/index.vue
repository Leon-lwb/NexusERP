<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="分类编码" prop="categoryCode">
        <el-input
          v-model="queryParams.categoryCode"
          placeholder="请输入分类编码"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="分类名称" prop="categoryName">
        <el-input
          v-model="queryParams.categoryName"
          placeholder="请输入分类名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status" style="width: 160px;">
        <el-select v-model="queryParams.status" placeholder="请选择" clearable>
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
          v-hasPermi="['bd:category:add']"
        >新增</el-button>
      </el-col>
       <el-col :span="1.5">
        <el-button
          plain
          icon="Sort"
          @click="toggleExpandAll"
        >{{ expandAll ? '折叠' : '展开' }}</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table
      v-loading="loading"
      :data="categoryList"
      row-key="categoryId"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      @selection-change="handleSelectionChange"
      ref="tableRef"
    >
      <!-- <el-table-column type="selection" width="55" align="center" /> -->
      <el-table-column label="分类名称" align="left" prop="categoryName" />
      <el-table-column label="分类编码" align="center" prop="categoryCode" />
      <el-table-column label="分类描述" align="center" prop="categoryDesc" />
      <el-table-column label="显示排序" align="center" prop="sortOrder" width="100" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :options="sys_core_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="View"
            @click="handleView(scope.row)"
            v-hasPermi="['bd:category:query']"
          >查看</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['bd:category:edit']"
          >修改</el-button>
          <el-button
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['bd:category:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加或修改物料产品分类对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="categoryRef" :model="form" :rules="rules" label-width="80px" :disabled="isViewMode">
        <el-row>
          <el-col :span="12">
            <el-form-item label="父分类" prop="parentId">
              <el-tree-select
                v-model="form.parentId"
                :data="categoryOptions"
                node-key="categoryId"
                :props="{ label: 'categoryName', children: 'children' }"
                placeholder="选择父分类"
                check-strictly
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类编码" prop="categoryCode">
              <el-input v-model="form.categoryCode" placeholder="请输入分类编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="分类名称" prop="categoryName">
              <el-input v-model="form.categoryName" placeholder="请输入分类名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="分类描述" prop="categoryDesc">
          <el-input v-model="form.categoryDesc" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="dict in sys_core_status"
              :key="dict.value"
              :label="parseInt(dict.value)"
            >{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Category">
import { listCategory, getCategory, delCategory, addCategory, updateCategory } from "@/api/erp/bd/category"

const { proxy } = getCurrentInstance()
const { sys_core_status } = toRefs<any>(proxy.useDict('sys_core_status'))

const categoryList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false)
const categoryOptions = ref([])

// 添加展开/折叠相关变量
const expandAll = ref(false)
const tableRef = ref(null)

const data = reactive({
  form: {},
  queryParams: {
    categoryCode: null,
    categoryName: null,
    status: null,
  },
  rules: {
    parentId: [
      { required: true, message: "父分类不能为空", trigger: "blur" }
    ],
    categoryCode: [
      { required: true, message: "分类编码不能为空", trigger: "blur" }
    ],
    categoryName: [
      { required: true, message: "分类名称不能为空", trigger: "blur" }
    ],
    sortOrder: [
      { required: true, message: "显示排序不能为空", trigger: "blur" }
    ],
    status: [
      { required: true, message: "状态不能为空", trigger: "change" }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 转换分类数据结构 */
function normalizer(node) {
  if (node.children && !node.children.length) {
    delete node.children
  }
  return {
    id: node.categoryId,
    label: node.categoryName,
    children: node.children
  }
}

/** 查询分类下拉树结构 */
function getTreeselect() {
  listCategory().then(response => {
    categoryOptions.value = []
    // 修改处理逻辑，适配AjaxResult返回格式
    const data = response.data || response.rows || []
    const filteredData = Array.isArray(data) ? data.filter(item => item !== null && item !== undefined) : []
    const treeData = { categoryId: 0, categoryName: '主类目', children: [] }
    treeData.children = proxy.handleTree(filteredData, "categoryId", "parentId")
    categoryOptions.value.push(treeData)
  })
}

/** 查询物料产品分类列表 */
function getList() {
  loading.value = true
  listCategory(queryParams.value).then(response => {
    // 修改处理逻辑，适配AjaxResult返回格式
    const data = response.data || response.rows || []
    const filteredData = Array.isArray(data) ? data.filter(item => item !== null && item !== undefined) : []
    categoryList.value = proxy.handleTree(filteredData, "categoryId", "parentId")
    loading.value = false
  })
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    categoryId: null,
    parentId: null,
    categoryCode: null,
    categoryName: null,
    categoryDesc: null,
    sortOrder: 0,
    status: "0",
    remark: null
  }
  proxy.resetForm("categoryRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.categoryId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  getTreeselect()
  isViewMode.value = false
  open.value = true
  title.value = "添加物料产品分类"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  getTreeselect()
  isViewMode.value = false
  const _categoryId = row.categoryId || ids.value
  getCategory(_categoryId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改物料产品分类"
  })
}

/** 查看按钮操作 */
function handleView(row) {
  reset()
  isViewMode.value = true
  const _categoryId = row.categoryId || ids.value
  getCategory(_categoryId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看物料产品分类"
  })
}

/** 计算分类层级 */
function calculateLevel(parentId) {
  if (!parentId || parentId === 0) {
    return 1
  }
  
  // 在categoryOptions中查找父节点
  const findLevel = (nodes, id) => {
    for (const node of nodes) {
      if (node.categoryId === id) {
        return 2 // 父节点存在且不是根节点，所以当前节点是第2层
      }
      if (node.children && node.children.length > 0) {
        const childResult = findLevel(node.children, id)
        if (childResult > 0) {
          return childResult + 1
        }
      }
    }
    return 0
  }
  
  const level = findLevel(categoryOptions.value, parentId)
  return level > 0 ? level : 1
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["categoryRef"].validate(valid => {
    if (valid) {
      // 计算并设置level_num
      if (form.value.parentId !== null && form.value.parentId !== undefined) {
        form.value.levelNum = calculateLevel(form.value.parentId)
      } else {
        form.value.levelNum = 1
      }
      
      if (form.value.categoryId != null) {
        updateCategory(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addCategory(form.value).then(response => {
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
  const _categoryIds = row.categoryId || ids.value
  proxy.$modal.confirm('是否确认删除物料产品分类编号为"' + _categoryIds + '"的数据项？').then(function() {
    return delCategory(_categoryIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('bd/category/export', {
    ...queryParams.value
  }, `category_${new Date().getTime()}.xlsx`)
}

/** 展开/折叠操作 */
function toggleExpandAll() {
  expandAll.value = !expandAll.value
  nextTick(() => {
    toggleRowExpansion(categoryList.value, expandAll.value)
  })
}

/** 切换行展开状态 */
function toggleRowExpansion(rows, expanded) {
  if (rows && rows.length > 0) {
    rows.forEach(row => {
      tableRef.value.toggleRowExpansion(row, expanded)
      if (row.children && row.children.length > 0) {
        toggleRowExpansion(row.children, expanded)
      }
    })
  }
}

getList()
</script>