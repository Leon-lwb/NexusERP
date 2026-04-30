<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="仓库编码" prop="warehouseCode">
        <el-input v-model="queryParams.warehouseCode" placeholder="请输入仓库编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库名称" prop="warehouseName">
        <el-input v-model="queryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="位置" prop="location">
        <el-input v-model="queryParams.location" placeholder="请输入位置" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="面积" prop="area">
        <el-input v-model="queryParams.area" placeholder="请输入面积" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="负责人" prop="manager">
        <el-input v-model="queryParams.manager" placeholder="请输入负责人" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="仓库状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择仓库状态" clearable>
          <el-option v-for="dict in wm_warehouse_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:warehouse:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['wm:warehouse:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['wm:warehouse:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['wm:warehouse:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['wm:warehouse:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="warehouseList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" width="50" align="center" prop="warehouseId" />
      <el-table-column label="仓库编码" align="center" prop="warehouseCode" />
      <el-table-column label="仓库名称" align="center" prop="warehouseName" />
      <el-table-column label="位置" align="center" prop="location" />
      <el-table-column label="面积" align="center" prop="area" />
      <el-table-column label="负责人" align="center" prop="manager" />
      <el-table-column label="仓库状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_warehouse_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="220px">
        <template #default="scope">
          <el-button size="mini" type="text" icon="box" @click="handleStoragearea(scope.row.warehouseId)"
            v-hasPermi="['wm:storagearea:edit', 'wm:storagearea:list']">库区</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['wm:warehouse:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['wm:warehouse:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改仓库设置对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="warehouseRef" :model="form" :rules="rules" label-width="80px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="仓库编码" prop="warehouseCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.warehouseCode" placeholder="请输入仓库编码" :disabled="autoGenFlag || isViewMode"
                  style="flex: 1;" />
                <el-switch v-model="autoGenFlag" inline-prompt active-color="#13ce66" active-text="自动"
                  inactive-text="自动生成" @change="handleAutoGenChange" :disabled="isViewMode" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库名称" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="省份" prop="provinceAreaId">
              <el-select v-model="form.provinceAreaId" placeholder="请选择省份" :disabled="isViewMode"
                @change="handleProvinceChange" style="width: 100%">
                <el-option v-for="item in provinceOptions" :key="item.areaId" :label="item.areaName"
                  :value="item.areaId">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="城市" prop="cityAreaId">
              <el-select v-model="form.cityAreaId" placeholder="请选择城市" :disabled="isViewMode || !cityOptions.length"
                @change="handleCityChange" style="width: 100%">
                <el-option v-for="item in cityOptions" :key="item.areaId" :label="item.areaName" :value="item.areaId">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="区县" prop="districtAreaId">
              <el-select v-model="form.districtAreaId" placeholder="请选择区县"
                :disabled="isViewMode || !districtOptions.length" @change="handleDistrictChange" style="width: 100%">
                <el-option v-for="item in districtOptions" :key="item.areaId" :label="item.areaName"
                  :value="item.areaId">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="街道" prop="streetId">
              <el-select v-model="form.streetId" placeholder="请选择街道" :disabled="isViewMode || !streetOptions.length"
                style="width: 100%" @change="handleStreetChange">
                <el-option v-for="item in streetOptions" :key="item.areaId" :label="item.areaName" :value="item.areaId">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="经度" prop="addressLng">
              <el-input v-model="form.addressLng" placeholder="请输入地址经度" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度" prop="addressLat">
              <el-input v-model="form.addressLat" placeholder="请输入地址纬度" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="位置" prop="location">
              <el-input v-model="form.location" type="textarea" placeholder="请输入位置" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="面积" prop="area">
              <el-input-number v-model="form.area" placeholder="请输入面积" :precision="2" :step="1"
                controls-position="right" style="width: 100%">
                <template #append>㎡</template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择仓库状态" style="width: 100%">
                <el-option v-for="dict in wm_warehouse_status" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="负责人" prop="manager">
              <el-input v-model="form.manager" placeholder="请选择负责人" readonly>
                <template #append>
                  <el-button icon="User" @click="openUserSelect" :disabled="isViewMode"></el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :rows="3" />
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

    <!-- 用户选择对话框 -->
    <el-dialog title="选择负责人" v-model="userVisible" width="800px" append-to-body>
      <el-form :model="userQueryParams" ref="userQueryRef" :inline="true">
        <el-form-item label="用户名称" prop="userName">
          <el-input v-model="userQueryParams.userName" placeholder="请输入用户名称" clearable @keyup.enter="getUserList" />
        </el-form-item>
        <el-form-item label="手机号码" prop="phonenumber">
          <el-input v-model="userQueryParams.phonenumber" placeholder="请输入手机号码" clearable @keyup.enter="getUserList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getUserList">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="userLoading" :data="userList" @row-dblclick="selectUser">
        <el-table-column label="用户编号" align="center" prop="userId" />
        <el-table-column label="用户名称" align="center" prop="userName" />
        <el-table-column label="用户昵称" align="center" prop="nickName" />
        <el-table-column label="手机号码" align="center" prop="phonenumber" />
        <el-table-column label="部门" align="center" prop="dept.deptName" />
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectUser(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="userList.length > 0" :total="userTotal" v-model:page="userQueryParams.pageNum"
        v-model:limit="userQueryParams.pageSize" @pagination="getUserList" />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="userVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Warehouse">
import { listWarehouse, getWarehouse, delWarehouse, addWarehouse, updateWarehouse } from "@/api/erp/wm/warehouse"
import { genCode } from "@/api/system/autocode/rule"
import { listUser } from "@/api/system/user"
import { getProvinces, getNextLevel } from "@/api/system/area"

const { proxy } = getCurrentInstance() as any
const { wm_warehouse_status } = proxy.useDict('wm_warehouse_status')

const warehouseList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false)
const autoGenFlag = ref(false)

const userList = ref([])
const userLoading = ref(false)
const userVisible = ref(false)
const userTotal = ref(0)
const userQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  userName: undefined,
  phonenumber: undefined
})

const provinceOptions = ref([])
const cityOptions = ref([])
const districtOptions = ref([])
const streetOptions = ref([])

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    warehouseCode: null,
    warehouseName: null,
    location: null,
    area: null,
    manager: null,
    status: null,
  },
  rules: {
    warehouseCode: [
      { required: true, message: "仓库编码不能为空", trigger: "blur" }
    ],
    warehouseName: [
      { required: true, message: "仓库名称不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listWarehouse(queryParams.value).then((response: any) => {
    warehouseList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    location: null,
    area: null,
    manager: null,
    managerId: null,
    remark: null,
    status: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    provinceAreaId: null,
    cityAreaId: null,
    districtAreaId: null,
    streetId: null,
    addressLat: null,
    addressLng: null
  }
  proxy.resetForm("warehouseRef")
  cityOptions.value = []
  districtOptions.value = []
  streetOptions.value = []
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.warehouseId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  isViewMode.value = false
  loadProvinceOptions()
  open.value = true
  title.value = "添加仓库设置"
}

function handleUpdate(row: any) {
  reset()
  isViewMode.value = false
  const _warehouseId = row.warehouseId || ids.value
  getWarehouse(_warehouseId).then((response: any) => {
    form.value = response.data
    loadProvinceOptions().then(() => {
      if (form.value.provinceAreaId) {
        loadCityOptions(form.value.provinceAreaId).then(() => {
          if (form.value.cityAreaId) {
            loadDistrictOptions(form.value.cityAreaId).then(() => {
              if (form.value.streetId && form.value.districtAreaId) {
                loadStreetOptions(form.value.districtAreaId)
              }
            })
          }
        })
      }
    })
    open.value = true
    title.value = "修改仓库设置"
  })
}

function handleView(row: any) {
  reset()
  isViewMode.value = true
  const _warehouseId = row.warehouseId || ids.value
  getWarehouse(_warehouseId).then((response: any) => {
    form.value = response.data
    loadProvinceOptions().then(() => {
      if (form.value.provinceAreaId) {
        loadCityOptions(form.value.provinceAreaId).then(() => {
          if (form.value.cityAreaId) {
            loadDistrictOptions(form.value.cityAreaId).then(() => {
              if (form.value.streetId && form.value.districtAreaId) {
                loadStreetOptions(form.value.districtAreaId)
              }
            })
          }
        })
      }
    })
    open.value = true
    title.value = "查看仓库设置"
  })
}

function submitForm() {
  proxy.$refs["warehouseRef"].validate((valid: boolean) => {
    if (valid) {
      if (form.value.warehouseId != null) {
        updateWarehouse(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addWarehouse(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row: any) {
  const _warehouseIds = row.warehouseId || ids.value
  proxy.$modal.confirm('是否确认删除仓库设置编号为"' + _warehouseIds + '"的数据项？').then(function () {
    return delWarehouse(_warehouseIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function handleExport() {
  proxy.download('/erp/wm/warehouse/export', {
    ...queryParams.value
  }, `warehouse_${new Date().getTime()}.xlsx`)
}

function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    genCode('WAREHOUSE_CODE').then((response: any) => {
      form.value.warehouseCode = response;
    });
  } else {
    form.value.warehouseCode = null;
  }
}

function getUserList() {
  userLoading.value = true
  listUser(userQueryParams).then((response: any) => {
    userList.value = response.rows
    userTotal.value = response.total
    userLoading.value = false
  })
}

function openUserSelect() {
  userVisible.value = true
  getUserList()
}

function selectUser(row: any) {
  form.value.manager = row.userName
  form.value.managerId = row.userId
  userVisible.value = false
}

function resetUserQuery() {
  userQueryParams.userName = undefined
  userQueryParams.phonenumber = undefined
  getUserList()
}

function loadProvinceOptions() {
  return getProvinces().then((response: any) => {
    provinceOptions.value = response.data
  })
}

function handleProvinceChange(value: any) {
  form.value.cityAreaId = null
  form.value.districtAreaId = null
  form.value.streetId = null
  cityOptions.value = []
  districtOptions.value = []
  streetOptions.value = []

  if (value) {
    loadCityOptions(value)
  }

  nextTick(() => {
    updateAddressField()
  })
}

function loadCityOptions(parentId: any) {
  return getNextLevel(parentId).then((response: any) => {
    cityOptions.value = response.data
  })
}

function handleCityChange(value: any) {
  form.value.districtAreaId = null
  form.value.streetId = null
  districtOptions.value = []
  streetOptions.value = []

  if (value) {
    loadDistrictOptions(value)
  }

  nextTick(() => {
    updateAddressField()
  })
}

function handleDistrictChange(value: any) {
  form.value.streetId = null
  streetOptions.value = []

  if (value) {
    loadStreetOptions(value)
  }

  nextTick(() => {
    updateAddressField()
  })
}

function handleStreetChange(value: any) {
  updateAddressField()
}

function loadDistrictOptions(parentId: any) {
  return getNextLevel(parentId).then((response: any) => {
    districtOptions.value = response.data
  })
}

function loadStreetOptions(parentId: any) {
  return getNextLevel(parentId).then((response: any) => {
    streetOptions.value = response.data
  })
}

function updateAddressField() {
  const provinceName = provinceOptions.value.find((p: any) => p.areaId === form.value.provinceAreaId)?.areaName || ''
  const cityName = cityOptions.value.find((c: any) => c.areaId === form.value.cityAreaId)?.areaName || ''
  const districtName = districtOptions.value.find((d: any) => d.areaId === form.value.districtAreaId)?.areaName || ''
  const streetName = streetOptions.value.find((s: any) => s.areaId === form.value.streetId)?.areaName || ''

  const addressParts = [provinceName, cityName, districtName, streetName].filter(part => part !== '')
  form.value.location = addressParts.join('')
}

function handleStoragearea(warehouseId: number) {
  proxy.$router.push({ path: '/erp/wm/storagearea/index', query: { warehouseId: warehouseId || 0 } })
}

getList()
</script>
