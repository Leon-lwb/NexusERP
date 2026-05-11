<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="库区编码" prop="areaCode">
        <el-input v-model="queryParams.areaCode" placeholder="请输入库区编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库区名称" prop="areaName">
        <el-input v-model="queryParams.areaName" placeholder="请输入库区名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库位管理" prop="locationFlag">
        <el-select v-model="queryParams.locationFlag" placeholder="请选择是否开启库位管理" clearable>
          <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否启用" prop="enableFlag">
        <el-select v-model="queryParams.enableFlag" placeholder="请选择是否启用" clearable>
          <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否冻结" prop="frozenFlag">
        <el-select v-model="queryParams.frozenFlag" placeholder="请选择是否冻结" clearable>
          <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="布局类型" prop="layoutType">
        <el-select v-model="queryParams.layoutType" placeholder="请选择布局类型" clearable>
          <el-option v-for="dict in wm_layout_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd"
          v-hasPermi="['wm:storagearea:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['wm:storagearea:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['wm:storagearea:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['wm:storagearea:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['wm:storagearea:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="storageareaList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="库区ID" align="center" prop="areaId" />
      <el-table-column label="库区编码" align="center" prop="areaCode" />
      <el-table-column label="库区名称" align="center" prop="areaName" />
      <el-table-column label="面积(㎡)" align="center" prop="area" />
      <el-table-column label="开启库位管理" align="center" prop="locationFlag">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.locationFlag" />
        </template>
      </el-table-column>
      <el-table-column label="是否启用" align="center" prop="enableFlag">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.enableFlag" />
        </template>
      </el-table-column>
      <el-table-column label="是否冻结" align="center" prop="frozenFlag">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.frozenFlag" />
        </template>
      </el-table-column>
      <el-table-column label="布局类型" align="center" prop="layoutType">
        <template #default="scope">
          <dict-tag :options="wm_layout_type" :value="scope.row.layoutType" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button size="mini" type="text" icon="box" @click="handleStoragelocation(scope.row.areaId)"
            v-hasPermi="['wm:storagelocation:edit', 'wm:storagelocation:list']">库位</el-button>
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:storagearea:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:storagearea:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改库区对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="storageareaRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="库区编码" prop="areaCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.areaCode" placeholder="请输入库区编码" :disabled="autoGenFlag || isViewMode"
                  style="flex: 1;" />
                <el-switch v-model="autoGenFlag" inline-prompt active-color="#13ce66" active-text="自动"
                  inactive-text="自动生成" @change="handleAutoGenChange" :disabled="isViewMode" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库区名称" prop="areaName">
              <el-input v-model="form.areaName" placeholder="请输入库区名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="布局类型" prop="layoutType">
              <el-select v-model="form.layoutType" placeholder="请选择布局类型">
                <el-option v-for="dict in wm_layout_type" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积(㎡)" prop="area">
              <el-input v-model="form.area" placeholder="请输入面积(㎡)" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="库位管理" prop="locationFlag">
              <el-radio-group v-model="form.locationFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{
                  dict.label
                  }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="是否启用" prop="enableFlag">
              <el-radio-group v-model="form.enableFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{
                  dict.label
                  }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="是否冻结" prop="frozenFlag">
              <el-radio-group v-model="form.frozenFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{
                  dict.label
                  }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="3D坐标X(米)" prop="posX">
              <el-input v-model="form.posX" placeholder="请输入3D坐标X(米)" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="3D坐标Y(米)" prop="posY">
              <el-input v-model="form.posY" placeholder="请输入3D坐标Y(米)" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="3D坐标Z(米)" prop="posZ">
              <el-input v-model="form.posZ" placeholder="请输入3D坐标Z(米)" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="库区宽度(米)" prop="areaWidth">
              <el-input v-model="form.areaWidth" placeholder="请输入库区宽度(米)" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库区长度(米)" prop="areaLength">
              <el-input v-model="form.areaLength" placeholder="请输入库区长度(米)" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库区高度(米)" prop="areaHeight">
              <el-input v-model="form.areaHeight" placeholder="请输入库区高度(米)" />
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
  </div>
</template>

<script setup lang="ts" name="Storagearea">
import { listStoragearea, getStoragearea, delStoragearea, addStoragearea, updateStoragearea } from "@/api/erp/wm/storagearea"
import { useRoute } from 'vue-router'
import { genCode } from "@/api/system/autocode/rule"

const { proxy } = getCurrentInstance() as any
const { sys_true_or_false, wm_layout_type } = toRefs<any>(proxy.useDict('sys_true_or_false', 'wm_layout_type'))

const storageareaList = ref([])
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

const route = useRoute()

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    areaCode: null,
    areaName: null,
    warehouseId: null,
    area: null,
    locationFlag: null,
    enableFlag: null,
    frozenFlag: null,
    layoutType: null,
  },
  rules: {
    areaCode: [
      { required: true, message: "库区编码不能为空", trigger: "blur" }
    ],
    areaName: [
      { required: true, message: "库区名称不能为空", trigger: "blur" }
    ],
    warehouseId: [
      { required: true, message: "仓库ID不能为空", trigger: "blur" }
    ],
    locationFlag: [
      { required: true, message: "是否开启库位管理不能为空", trigger: "change" }
    ],
    enableFlag: [
      { required: true, message: "是否启用不能为空", trigger: "change" }
    ],
    frozenFlag: [
      { required: true, message: "是否冻结不能为空", trigger: "change" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  if ((route.query as any).warehouseId) {
    queryParams.value.warehouseId = (route.query as any).warehouseId
  }
  listStoragearea(queryParams.value).then((response: any) => {
    storageareaList.value = response.rows
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
    areaId: null,
    areaCode: null,
    areaName: null,
    warehouseId: null,
    area: null,
    locationFlag: null,
    enableFlag: null,
    frozenFlag: null,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    layoutType: null,
    posX: null,
    posY: null,
    posZ: null,
    areaWidth: null,
    areaLength: null,
    areaHeight: null,
    rotationX: null,
    rotationY: null,
    rotationZ: null,
    color: null,
    opacity: null,
    modelType: null,
    modelUrl: null,
    showWalls: null,
    showLabel: null,
    gridVisible: null,
    configJson: null
  }
  proxy.resetForm("storageareaRef")
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
  ids.value = selection.map((item: any) => item.areaId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  isViewMode.value = false
  if ((route.query as any).warehouseId) {
    form.value.warehouseId = (route.query as any).warehouseId
  }
  open.value = true
  title.value = "添加库区"
}

function handleUpdate(row: any) {
  reset()
  isViewMode.value = false
  const _areaId = row.areaId || ids.value
  getStoragearea(_areaId).then((response: any) => {
    form.value = response.data
    open.value = true
    title.value = "修改库区"
  })
}

function handleView(row: any) {
  reset()
  isViewMode.value = true
  const _areaId = row.areaId || ids.value
  getStoragearea(_areaId).then((response: any) => {
    form.value = response.data
    open.value = true
    title.value = "查看库区"
  })
}

function submitForm() {
  proxy.$refs["storageareaRef"].validate((valid: boolean) => {
    if (valid) {
      if (form.value.areaId != null) {
        updateStoragearea(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addStoragearea(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row: any) {
  const _areaIds = row.areaId || ids.value
  proxy.$modal.confirm('是否确认删除库区编号为"' + _areaIds + '"的数据项？').then(function () {
    return delStoragearea(_areaIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function handleExport() {
  proxy.download('/erp/wm/storagearea/export', {
    ...queryParams.value
  }, `storagearea_${new Date().getTime()}.xlsx`)
}

function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    genCode('WM_AREA_CODE').then((response: any) => {
      form.value.areaCode = response;
    });
  } else {
    form.value.areaCode = null;
  }
}

function handleStoragelocation(areaId: number) {
  proxy.$router.push({ path: '/erp/wm/storagelocation/index', query: { areaId: areaId || 0 } })
}

getList()
</script>
