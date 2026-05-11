<template>
  <div class="app-container">
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
          <span>{{ proxy.parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="300" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:area:edit']">修改</el-button>
          <el-button link type="primary" icon="Plus" @click="handleAdd(scope.row)" v-hasPermi="['system:area:add']">新增</el-button>
          <el-button link type="primary" icon="View" @click="handleExpand(scope.row)" v-hasPermi="['system:area:list']">查询</el-button>
          <el-button v-if="scope.row.parentAreaId != 0" link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:area:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="open" :title="title" width="600px" append-to-body>
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

<script setup lang="ts" name="Area">
import { ref, reactive, toRefs, getCurrentInstance } from 'vue'
import { listArea, listAreaExcludeChild, getArea, delArea, addArea, updateArea } from "@/api/system/area"

const { proxy } = getCurrentInstance()
const { sys_area_level } = toRefs<any>(proxy.useDict("sys_area_level"))

const areaList = ref([])
const open = ref(false)
const loading = ref(true)
const title = ref("")
const areaOptions = ref([])
const expandedRowKeys = ref([])
const areaRef = ref()

const data = reactive({
  form: {},
  queryParams: {
    areaName: undefined,
    layered: 1
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

function getList() {
  loading.value = true
  listArea(queryParams.value).then(response => {
    areaList.value = []
    if (response.data && Array.isArray(response.data)) {
      const filteredData = response.data.filter(item => item !== null && item !== undefined)
      const dataWithHasChildren = filteredData.map(item => ({
        ...item,
        hasChildren: item.layered < 4
      }))
      areaList.value = dataWithHasChildren
    }
    loading.value = false
    expandedRowKeys.value = []
  }).catch(() => {
    proxy.$modal.msgError('获取数据失败')
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

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

function handleAdd(row) {
  reset()
  listArea(queryParams.value).then(response => {
    if (response.data && Array.isArray(response.data)) {
      const filteredData = response.data.filter(item => item !== null && item !== undefined)
      const dataWithHasChildren = filteredData.map(item => ({
        ...item,
        hasChildren: item.layered < 4
      }))
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

function handleExpand(row) {
  const query = {
    parentAreaId: row.areaId,
    layered: row.layered + 1
  }

  listArea(query).then(response => {
    if (!response.data || response.data.length === 0) {
      proxy.$modal.msgWarning("该行政区划下无子节点！")
    } else {
      const childrenData = response.data.map(item => {
        const { children, ...rest } = item
        return { ...rest, hasChildren: item.layered < 4 }
      })

      const updateAreaList = (list) => {
        return list.map(item => {
          if (item.areaId === row.areaId) {
            return { ...item, children: childrenData, hasChildren: childrenData.length > 0 }
          }
          if (item.children && item.children.length > 0) {
            return { ...item, children: updateAreaList(item.children) }
          }
          return item
        })
      }

      areaList.value = updateAreaList(areaList.value)
      if (!expandedRowKeys.value.includes(row.areaId)) {
        expandedRowKeys.value = [...expandedRowKeys.value, row.areaId]
      }
    }
  }).catch(() => {
    proxy.$modal.msgError('展开失败')
  })
}

function handleUpdate(row) {
  reset()
  listAreaExcludeChild(row.areaId).then(response => {
    if (response.data && Array.isArray(response.data)) {
      const filteredData = response.data.filter(item => item !== null && item !== undefined)
      const dataWithHasChildren = filteredData.map(item => ({
        ...item,
        hasChildren: item.layered < 4
      }))
      areaOptions.value = []
      const data = { areaId: 0, areaName: '顶级节点', children: [] }
      data.children = dataWithHasChildren
      areaOptions.value.push(data)
    }
  })
  getArea(row.pkid).then(response => {
    form.value = response.data
    if (form.value.layered !== undefined && form.value.layered !== null) {
      form.value.layered = parseInt(form.value.layered)
    }
    open.value = true
    title.value = "修改行政区划"
  })
}

function submitForm() {
  proxy.$refs["areaRef"].validate(valid => {
    if (valid) {
      if (form.value.pkid != undefined) {
        updateArea(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addArea(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

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
