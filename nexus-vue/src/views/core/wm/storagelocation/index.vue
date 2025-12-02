<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="库位编码" prop="locationCode">
        <el-input v-model="queryParams.locationCode" placeholder="请输入库位编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库位名称" prop="locationName">
        <el-input v-model="queryParams.locationName" placeholder="请输入库位名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="库区ID" prop="areaId">
        <el-input v-model="queryParams.areaId" placeholder="请输入库区ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="面积" prop="area">
        <el-input v-model="queryParams.area" placeholder="请输入面积" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="最大载重量" prop="maxLoa">
        <el-input v-model="queryParams.maxLoa" placeholder="请输入最大载重量" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="是否允许产品混放" prop="productMixing">
        <el-select v-model="queryParams.productMixing" placeholder="请选择是否允许产品混放" clearable>
          <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否允许批次混放" prop="batchMixing">
        <el-select v-model="queryParams.batchMixing" placeholder="请选择是否允许批次混放" clearable>
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
      <el-form-item label="库位类型" prop="locationType">
        <el-select v-model="queryParams.locationType" placeholder="请选择库位类型" clearable>
          <el-option v-for="dict in wm_location_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="库位状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择库位状态" clearable>
          <el-option v-for="dict in wm_location_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="最后入库时间" prop="lastInboundTime">
        <el-date-picker clearable v-model="queryParams.lastInboundTime" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择最后入库时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="最后出库时间" prop="lastOutboundTime">
        <el-date-picker clearable v-model="queryParams.lastOutboundTime" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择最后出库时间">
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd"
          v-hasPermi="['wm:storagelocation:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['wm:storagelocation:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['wm:storagelocation:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['wm:storagelocation:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['wm:storagelocation:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="storagelocationList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="库位ID" align="center" prop="locationId" />
      <el-table-column label="库位编码" align="center" prop="locationCode" />
      <el-table-column label="库位名称" align="center" prop="locationName" />
      <el-table-column label="面积" align="center" prop="area" />
      <el-table-column label="最大载重量" align="center" prop="maxLoa" />
      <el-table-column label="是否允许产品混放" align="center" prop="productMixing">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.productMixing" />
        </template>
      </el-table-column>
      <el-table-column label="是否允许批次混放" align="center" prop="batchMixing">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.batchMixing" />
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
      <el-table-column label="库位类型" align="center" prop="locationType">
        <template #default="scope">
          <dict-tag :options="wm_location_type" :value="scope.row.locationType" />
        </template>
      </el-table-column>
      <el-table-column label="库位状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_location_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="占用率(0-100)" align="center" prop="occupancyRate" />
      <el-table-column label="最后入库时间" align="center" prop="lastInboundTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.lastInboundTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最后出库时间" align="center" prop="lastOutboundTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.lastOutboundTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['wm:storagelocation:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['wm:storagelocation:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改库位对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="storagelocationRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="库位编码" prop="locationCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.locationCode" placeholder="请输入库位编码" :disabled="autoGenFlag || isViewMode"
                  style="flex: 1;" />
                <el-switch v-model="autoGenFlag" inline-prompt active-color="#13ce66" active-text="自动"
                  inactive-text="自动生成" @change="handleAutoGenChange" :disabled="isViewMode" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位名称" prop="locationName">
              <el-input v-model="form.locationName" placeholder="请输入库位名称" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="库区ID" prop="areaId">
              <el-input v-model="form.areaId" placeholder="请输入库区ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积" prop="area">
              <el-input v-model="form.area" placeholder="请输入面积" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最大载重量" prop="maxLoa">
              <el-input v-model="form.maxLoa" placeholder="请输入最大载重量" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否允许产品混放" prop="productMixing">
              <el-radio-group v-model="form.productMixing">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否允许批次混放" prop="batchMixing">
              <el-radio-group v-model="form.batchMixing">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用" prop="enableFlag">
              <el-radio-group v-model="form.enableFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否冻结" prop="frozenFlag">
              <el-radio-group v-model="form.frozenFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位类型" prop="locationType">
              <el-select v-model="form.locationType" placeholder="请选择库位类型">
                <el-option v-for="dict in wm_location_type" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
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
        
        <!-- 隐藏部分表单字段 -->
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="库位位置X" prop="positionX">
              <el-input v-model="form.positionX" placeholder="请输入库位位置X" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库位位置y" prop="positionY">
              <el-input v-model="form.positionY" placeholder="请输入库位位置y" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库位位置z" prop="positionZ">
              <el-input v-model="form.positionZ" placeholder="请输入库位位置z" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="所属货架ID" prop="rackId">
              <el-input v-model="form.rackId" placeholder="请输入所属货架ID" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="层数(从下往上)" prop="layer">
              <el-input v-model="form.layer" placeholder="请输入层数(从下往上)" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="列数(从左往右)" prop="col">
              <el-input v-model="form.col" placeholder="请输入列数(从左往右)" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="行数(从前到后)" prop="rowNum">
              <el-input v-model="form.rowNum" placeholder="请输入行数(从前到后)" />
            </el-form-item>
          </el-col>
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
        </el-row>
        
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="3D坐标Z(米)" prop="posZ">
              <el-input v-model="form.posZ" placeholder="请输入3D坐标Z(米)" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库位宽度(米)" prop="width">
              <el-input v-model="form.width" placeholder="请输入库位宽度(米)" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库位高度(米)" prop="height">
              <el-input v-model="form.height" placeholder="请输入库位高度(米)" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="库位深度(米)" prop="locationDepth">
              <el-input v-model="form.locationDepth" placeholder="请输入库位深度(米)" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="X轴旋转角度" prop="rotationX">
              <el-input v-model="form.rotationX" placeholder="请输入X轴旋转角度" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Y轴旋转角度" prop="rotationY">
              <el-input v-model="form.rotationY" placeholder="请输入Y轴旋转角度" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="Z轴旋转角度" prop="rotationZ">
              <el-input v-model="form.rotationZ" placeholder="请输入Z轴旋转角度" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="空置时颜色" prop="colorEmpty">
              <el-input v-model="form.colorEmpty" placeholder="请输入空置时颜色" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="占用时颜色" prop="colorOccupied">
              <el-input v-model="form.colorOccupied" placeholder="请输入占用时颜色" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="锁定时颜色" prop="colorLocked">
              <el-input v-model="form.colorLocked" placeholder="请输入锁定时颜色" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预警时颜色" prop="colorWarning">
              <el-input v-model="form.colorWarning" placeholder="请输入预警时颜色" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="透明度" prop="opacity">
              <el-input v-model="form.opacity" placeholder="请输入透明度" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="是否显示边框" prop="showBorder">
              <el-radio-group v-model="form.showBorder">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="边框颜色" prop="borderColor">
              <el-input v-model="form.borderColor" placeholder="请输入边框颜色" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="边框宽度" prop="borderWidth">
              <el-input v-model="form.borderWidth" placeholder="请输入边框宽度" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="是否显示标签" prop="showLabel">
              <el-input v-model="form.showLabel" placeholder="请输入是否显示标签" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="置: TOP-顶部, FRONT-前面标签位" prop="labelPosition">
              <el-input v-model="form.labelPosition" placeholder="请输入置: TOP-顶部, FRONT-前面标签位" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="标签大小" prop="labelSize">
              <el-input v-model="form.labelSize" placeholder="请输入标签大小" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="库位状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in wm_location_status" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="占用率(0-100)" prop="occupancyRate">
              <el-input v-model="form.occupancyRate" placeholder="请输入占用率(0-100)" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最后入库时间" prop="lastInboundTime">
              <el-date-picker clearable v-model="form.lastInboundTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择最后入库时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" style="display: none;">
          <el-col :span="8">
            <el-form-item label="最后出库时间" prop="lastOutboundTime">
              <el-date-picker clearable v-model="form.lastOutboundTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择最后出库时间">
              </el-date-picker>
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

<script setup name="Storagelocation">
import { listStoragelocation, getStoragelocation, delStoragelocation, addStoragelocation, updateStoragelocation } from "@/api/core/wm/storagelocation"
import { useRoute } from 'vue-router'
import { genCode } from "@/api/system/autocode/rule"

const { proxy } = getCurrentInstance()
const { sys_true_or_false, wm_location_type, wm_location_status } = proxy.useDict('sys_true_or_false', 'wm_location_type', 'wm_location_status')

const storagelocationList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false) // 新增：标识是否为查看模式
const autoGenFlag = ref(false) // 新增：自动生成标识

const route = useRoute()

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    locationCode: null,
    locationName: null,
    areaId: null,
    area: null,
    maxLoa: null,
    productMixing: null,
    batchMixing: null,
    enableFlag: null,
    frozenFlag: null,
    locationType: null,
    status: null,
    lastInboundTime: null,
    lastOutboundTime: null,
  },
  rules: {
    locationCode: [
      { required: true, message: "库位编码不能为空", trigger: "blur" }
    ],
    locationName: [
      { required: true, message: "库位名称不能为空", trigger: "blur" }
    ],
    areaId: [
      { required: true, message: "库区ID不能为空", trigger: "blur" }
    ],
    productMixing: [
      { required: true, message: "是否允许产品混放不能为空", trigger: "change" }
    ],
    batchMixing: [
      { required: true, message: "是否允许批次混放不能为空", trigger: "change" }
    ],
    enableFlag: [
      { required: true, message: "是否启用不能为空", trigger: "change" }
    ],
    frozenFlag: [
      { required: true, message: "是否冻结不能为空", trigger: "change" }
    ],
    rowNum: [
      { required: true, message: "行数不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询库位列表 */
function getList() {
  loading.value = true
  // 如果路由中有areaId参数，则使用该参数进行查询
  if (route.query.areaId) {
    queryParams.value.areaId = route.query.areaId
  }
  listStoragelocation(queryParams.value).then(response => {
    storagelocationList.value = response.rows
    total.value = response.total
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
    locationId: null,
    locationCode: null,
    locationName: null,
    areaId: null,
    area: null,
    maxLoa: null,
    positionX: null,
    positionY: null,
    positionZ: null,
    productMixing: null,
    batchMixing: null,
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
    locationType: null,
    rackId: null,
    layer: null,
    col: null,
    rowNum: null,
    posX: null,
    posY: null,
    posZ: null,
    width: null,
    height: null,
    locationDepth: null,
    rotationX: null,
    rotationY: null,
    rotationZ: null,
    colorEmpty: null,
    colorOccupied: null,
    colorLocked: null,
    colorWarning: null,
    opacity: null,
    showBorder: null,
    borderColor: null,
    borderWidth: null,
    showLabel: null,
    labelPosition: null,
    labelSize: null,
    status: null,
    occupancyRate: null,
    lastInboundTime: null,
    lastOutboundTime: null,
    configJson: null
  }
  proxy.resetForm("storagelocationRef")
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
  ids.value = selection.map(item => item.locationId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  // 如果路由中有areaId参数，则自动填充到表单中
  if (route.query.areaId) {
    form.value.areaId = route.query.areaId
  }
  open.value = true
  title.value = "添加库位"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  isViewMode.value = false // 设置为编辑模式
  const _locationId = row.locationId || ids.value
  getStoragelocation(_locationId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改库位"
  })
}

/** 查看按钮操作 */
function handleView(row) {
  reset()
  isViewMode.value = true // 设置为查看模式
  const _locationId = row.locationId || ids.value
  getStoragelocation(_locationId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看库位"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["storagelocationRef"].validate(valid => {
    if (valid) {
      if (form.value.locationId != null) {
        updateStoragelocation(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addStoragelocation(form.value).then(response => {
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
  const _locationIds = row.locationId || ids.value
  proxy.$modal.confirm('是否确认删除库位编号为"' + _locationIds + '"的数据项？').then(function () {
    return delStoragelocation(_locationIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('wm/storagelocation/export', {
    ...queryParams.value
  }, `storagelocation_${new Date().getTime()}.xlsx`)
}

// 自动生成编码
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    genCode('WM_LOCATION_CODE').then(response => {
      form.value.locationCode = response;
    });
  } else {
    form.value.locationCode = null;
  }
}

getList()
</script>