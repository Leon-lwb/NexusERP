<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="行政区划名称" prop="areaName">
        <el-input
          v-model="queryParams.areaName"
          placeholder="请输入行政区划名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
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
          v-hasPermi="['system:area:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="Sort"
          @click="toggleExpandAll"
        >展开/折叠</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table
      v-loading="loading"
      :data="areaList"
      row-key="areaId"
      :default-expanded-keys="expandedRowKeys"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
    >
      <el-table-column prop="areaName" label="行政区划名称" width="260"></el-table-column>
      <el-table-column prop="areaId" label="行政区划代码" width="200"></el-table-column>
      <el-table-column prop="layered" label="层级" width="100">
        <template #default="scope">
          <dict-tag :options="sys_area_level" :value="scope.row.layered" />
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="100"></el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="200">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:area:edit']">修改</el-button>
          <el-button link type="primary" icon="Plus" @click="handleAdd(scope.row)" v-hasPermi="['system:area:add']">新增</el-button>
          <el-button v-if="scope.row.parentAreaId != 0" link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:area:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加或修改行政区划对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="areaRef" :model="form" :rules="rules" label-width="80px">
        <el-row>
          <el-col :span="24" v-if="form.parentAreaId !== 0">
            <el-form-item label="上级地区" prop="parentAreaId">
              <el-tree-select
                v-model="form.parentAreaId"
                :data="areaOptions"
                :props="{ value: 'areaId', label: 'areaName', children: 'children' }"
                value-key="areaId"
                placeholder="选择上级地区"
                check-strictly
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地区名称" prop="areaName">
              <el-input v-model="form.areaName" placeholder="请输入地区名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地区代码" prop="areaId">
              <el-input v-model="form.areaId" placeholder="请输入地区代码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="sort">
              <el-input-number v-model="form.sort" controls-position="right" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="层级" prop="layered">
              <el-select v-model="form.layered" placeholder="请选择层级" clearable>
                <el-option
                  v-for="dict in sys_area_level"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度" prop="lat">
              <el-input v-model="form.lat" placeholder="请输入纬度" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经度" prop="lng">
              <el-input v-model="form.lng" placeholder="请输入经度" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址" prop="address">
              <el-input v-model="form.address" placeholder="请输入地址" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Area">
import { listArea, listAreaExcludeChild, getArea, delArea, addArea, updateArea } from "@/api/system/area"

const { proxy } = getCurrentInstance()
const { sys_area_level } = proxy.useDict("sys_area_level")

const areaList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const title = ref("")
const areaOptions = ref([])
const isExpandAll = ref(false)
const expandedRowKeys = ref([])

const data = reactive({
  form: {},
  queryParams: {
    areaName: undefined
  },
  rules: {
    parentAreaId: [{ required: true, message: "上级地区不能为空", trigger: "blur" }],
    areaName: [{ required: true, message: "地区名称不能为空", trigger: "blur" }],
    areaId: [{ required: true, message: "地区代码不能为空", trigger: "blur" }],
    sort: [{ required: true, message: "显示排序不能为空", trigger: "blur" }],
    layered: [{ required: true, message: "层级不能为空", trigger: "blur" }]
  },
})

const { queryParams, form, rules } = toRefs(data)

/** 查询行政区划列表 */
function getList() {
  loading.value = true
  listArea(queryParams.value).then(response => {
    areaList.value = []
    // 检查并过滤空值
    if (response.data && Array.isArray(response.data)) {
      const filteredData = response.data.filter(item => item !== null && item !== undefined);
      // 为每个节点设置 hasChildren 属性
      const dataWithHasChildren = filteredData.map(item => {
        return {
          ...item,
          // 根据是否有子节点或层级小于4来判断是否还有子节点
          hasChildren: item.layered < 4
        }
      });
      areaList.value = dataWithHasChildren;
    }
    loading.value = false;
    
    // 如果默认是展开状态，则展开所有节点
    if (isExpandAll.value) {
      expandAllRows(areaList.value);
    } else {
      expandedRowKeys.value = [];
    }
  }).catch(error => {
    console.error('Error fetching area data:', error);
    proxy.$modal.msgError('获取数据失败');
    loading.value = false;
  });
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    pkid: undefined,
    areaId: undefined,
    parentAreaId: undefined,
    areaName: undefined,
    lat: undefined,
    lng: undefined,
    layered: undefined,
    sort: 0,
    address: undefined
  }
  proxy.resetForm("areaRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  getList()
}

/** 新增按钮操作 */
function handleAdd(row) {
  reset()
  listArea(queryParams.value).then(response => {
    // 检查并过滤空值
    if (response.data && Array.isArray(response.data)) {
      const filteredData = response.data.filter(item => item !== null && item !== undefined);
      // 为每个节点设置 hasChildren 属性
      const dataWithHasChildren = filteredData.map(item => {
        return {
          ...item,
          // 根据是否有子节点或层级小于4来判断是否还有子节点
          hasChildren: item.layered < 4
        }
      });
      areaOptions.value = []
      const data = { areaId: 0, areaName: '顶级节点', children: [] }
      data.children = dataWithHasChildren
      areaOptions.value.push(data)
    }
  })
  if (row != undefined) {
    form.value.parentAreaId = row.areaId
  } else {
    form.value.parentAreaId = 0
  }
  open.value = true
  title.value = "添加行政区划"
}

/** 展开/折叠操作 */
function toggleExpandAll() {
  isExpandAll.value = !isExpandAll.value
  // 使用递归展开或折叠所有节点
  if (isExpandAll.value) {
    // 展开所有节点
    expandAllRows(areaList.value)
  } else {
    // 折叠所有节点
    expandedRowKeys.value = []
  }
}

/** 递归展开所有行 */
function expandAllRows(rows) {
  const keys = []
  const traverse = (nodes) => {
    nodes.forEach(node => {
      keys.push(node.areaId)
      if (node.children && node.children.length > 0) {
        traverse(node.children)
      }
    })
  }
  traverse(rows)
  expandedRowKeys.value = keys
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  // 查询排除当前节点及其子节点的区域数据，用于选择上级地区
  listAreaExcludeChild(row.areaId).then(response => {
    // 检查并过滤空值
    if (response.data && Array.isArray(response.data)) {
      const filteredData = response.data.filter(item => item !== null && item !== undefined);
      // 为每个节点设置 hasChildren 属性
      const dataWithHasChildren = filteredData.map(item => {
        return {
          ...item,
          // 根据是否有子节点或层级小于4来判断是否还有子节点
          hasChildren: item.layered < 4
        }
      });
      areaOptions.value = []
      const data = { areaId: 0, areaName: '顶级节点', children: [] }
      data.children = dataWithHasChildren
      areaOptions.value.push(data)
    }
  })
  getArea(row.pkid).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改行政区划"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["areaRef"].validate(valid => {
    if (valid) {
      if (form.value.pkid != undefined) {
        updateArea(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addArea(form.value).then(response => {
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
  proxy.$modal.confirm('是否确认删除名称为"' + row.areaName + '"的数据项?').then(function() {
    return delArea(row.pkid)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getList()
</script>