<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button type="success" @click="submitApprovingForm" v-if="!isViewMode && form.status === 'PREPARE'">审核</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="receiveRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="入库单编号" prop="receiveCode">
              <el-input v-model="form.receiveCode" placeholder="请输入入库单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="入库单名称" prop="receiveName">
              <el-input v-model="form.receiveName" placeholder="请输入入库单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="来料检验单编号" prop="iqcCode">
              <el-input v-model="form.iqcCode" placeholder="请输入来料检验单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="到货通知单编号" prop="noticeCode">
              <el-input v-model="form.noticeCode" placeholder="请输入到货通知单编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="采购订单编号" prop="purCode">
              <el-input v-model="form.purCode" placeholder="请输入采购订单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="供应商名称" prop="vendorName">
              <el-input v-model="form.vendorName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="供应商简称" prop="vendorNick">
              <el-input v-model="form.vendorNick" placeholder="请输入供应商简称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="入库日期" prop="receiveDate">
              <el-date-picker clearable v-model="form.receiveDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择入库日期" style="width: 100%">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="单据状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in wm_material_receive_status" :key="dict.value"
                  :label="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 子表部分开始 -->
        <el-divider content-position="center">物料入库单行信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddWmMaterialReceiveLine"
              v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteWmMaterialReceiveLine"
              v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="wmMaterialReceiveLineList" :row-class-name="rowWmMaterialReceiveLineIndex"
          @selection-change="handleWmMaterialReceiveLineSelectionChange" ref="wmMaterialReceiveLine">
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode" />
          <el-table-column type="expand" v-if="form.status !== 'PREPARE'">
            <template #default="props">
              <el-table :data="props.row.detailList" border style="width: 100%; margin-left: 50px;">
                <el-table-column label="明细ID" prop="detailId" />
                <el-table-column label="产品物料编码" prop="materialCode" />
                <el-table-column label="产品物料名称" prop="materialName" />
                <el-table-column label="规格型号" prop="materialSpec" />
                <el-table-column label="单位" prop="baseUomName" />
                <el-table-column label="入库数量" prop="quantity" />
                <el-table-column label="批次号" prop="batchCode" />
                <el-table-column label="仓库名称" prop="warehouseName" />
                <el-table-column label="库区名称" prop="areaName" />
                <el-table-column label="库位名称" prop="locationName" />
              </el-table>
            </template>
          </el-table-column>
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="来源单据行ID" prop="sourceDocLineId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.sourceDocLineId" placeholder="请输入来源单据行ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.sourceDocLineId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="产品物料ID" prop="materialId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialId" placeholder="请输入产品物料ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="产品物料编码" prop="materialCode" width="150">
            <template #default="scope">
              <el-input 
                v-model="scope.row.materialCode" 
                placeholder="请输入产品物料编码" 
                v-if="!isViewMode" 
                readonly>
                <template #append>
                  <el-button icon="Search" @click="openMaterialSelect(scope.$index)" />
                </template>
              </el-input>
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="产品物料名称" prop="materialName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="请输入产品物料名称" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单位" prop="baseUomId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.baseUomId" placeholder="请输入单位" v-if="!isViewMode" />
              <span v-else>{{ scope.row.baseUomId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单位名称" prop="baseUomName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.baseUomName" placeholder="请输入单位名称" v-if="!isViewMode" />
              <span v-else>{{ scope.row.baseUomName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="入库数量" prop="quantityRecived" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.quantityRecived" placeholder="请输入入库数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.quantityRecived }}</span>
            </template>
          </el-table-column>
          <el-table-column label="批次ID" prop="batchId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.batchId" placeholder="请输入批次ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.batchId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="批次号" prop="batchCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.batchCode" placeholder="请输入批次号" v-if="!isViewMode" />
              <span v-else>{{ scope.row.batchCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="仓库ID" prop="warehouseId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.warehouseId" placeholder="请输入仓库ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.warehouseId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="仓库编码" prop="warehouseCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.warehouseCode" placeholder="请输入仓库编码" v-if="!isViewMode" />
              <span v-else>{{ scope.row.warehouseCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="仓库名称" prop="warehouseName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.warehouseName" placeholder="请输入仓库名称" v-if="!isViewMode" />
              <span v-else>{{ scope.row.warehouseName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库区ID" prop="areaId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.areaId" placeholder="请输入库区ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.areaId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库区编码" prop="areaCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.areaCode" placeholder="请输入库区编码" v-if="!isViewMode" />
              <span v-else>{{ scope.row.areaCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库区名称" prop="areaName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.areaName" placeholder="请输入库区名称" v-if="!isViewMode" />
              <span v-else>{{ scope.row.areaName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库位ID" prop="locationId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.locationId" placeholder="请输入库位ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.locationId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库位编码" prop="locationCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.locationCode" placeholder="请输入库位编码" v-if="!isViewMode" />
              <span v-else>{{ scope.row.locationCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="库位名称" prop="locationName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.locationName" placeholder="请输入库位名称" v-if="!isViewMode" />
              <span v-else>{{ scope.row.locationName }}</span>
            </template>
          </el-table-column>
          <!-- 上架操作列，只在通过上架按钮进入页面时显示 -->
          <el-table-column label="操作" align="center" width="100" v-if="pageType === 'shelf' && !isViewMode">
            <template #default="scope">
              <el-button link type="primary" @click="handleShelf(scope.row)">上架</el-button>
            </template>
          </el-table-column>
        </el-table>
        <!-- 子表部分结束 -->
      </el-form>
    </el-card>
    
    <!-- 上架弹窗 -->
    <el-dialog :title="shelfTitle" v-model="shelfOpen" width="960px" append-to-body>
      <el-form ref="shelfRef" :model="shelfForm" :rules="shelfRules" label-width="100px" :disabled="isShelfViewMode">
        <el-form-item label="行ID" prop="lineId">
          <el-input v-model="shelfForm.lineId" placeholder="请输入行ID" />
        </el-form-item>
        <el-form-item label="入库单ID" prop="receiveId">
          <el-input v-model="shelfForm.receiveId" placeholder="请输入入库单ID" />
        </el-form-item>
        <el-form-item label="产品物料ID" prop="materialId">
          <el-input v-model="shelfForm.materialId" placeholder="请输入产品物料ID" />
        </el-form-item>
        <el-form-item label="产品物料编码" prop="materialCode">
          <el-input v-model="shelfForm.materialCode" placeholder="请输入产品物料编码" />
        </el-form-item>
        <el-form-item label="产品物料名称" prop="materialName">
          <el-input v-model="shelfForm.materialName" placeholder="请输入产品物料名称" />
        </el-form-item>
        <el-form-item label="规格型号" prop="materialSpec">
          <el-input v-model="shelfForm.materialSpec" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="单位" prop="baseUomId">
          <el-input v-model="shelfForm.baseUomId" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="单位名称" prop="baseUomName">
          <el-input v-model="shelfForm.baseUomName" placeholder="请输入单位名称" />
        </el-form-item>
        <el-form-item label="入库数量" prop="quantity">
          <el-input v-model="shelfForm.quantity" placeholder="请输入入库数量" />
        </el-form-item>
        <el-form-item label="批次ID" prop="batchId">
          <el-input v-model="shelfForm.batchId" placeholder="请输入批次ID" />
        </el-form-item>
        <el-form-item label="批次号" prop="batchCode">
          <el-input v-model="shelfForm.batchCode" placeholder="请输入批次号" />
        </el-form-item>
        <el-form-item label="仓库ID" prop="warehouseId">
          <el-input v-model="shelfForm.warehouseId" placeholder="请输入仓库ID" />
        </el-form-item>
        <el-form-item label="仓库编码" prop="warehouseCode">
          <el-input v-model="shelfForm.warehouseCode" placeholder="请输入仓库编码" />
        </el-form-item>
        <el-form-item label="仓库名称" prop="warehouseName">
          <el-input v-model="shelfForm.warehouseName" placeholder="请输入仓库名称" />
        </el-form-item>
        <el-form-item label="库区ID" prop="areaId">
          <el-input v-model="shelfForm.areaId" placeholder="请输入库区ID" />
        </el-form-item>
        <el-form-item label="库区编码" prop="areaCode">
          <el-input v-model="shelfForm.areaCode" placeholder="请输入库区编码" />
        </el-form-item>
        <el-form-item label="库区名称" prop="areaName">
          <el-input v-model="shelfForm.areaName" placeholder="请输入库区名称" />
        </el-form-item>
        <el-form-item label="库位ID" prop="locationId">
          <el-input v-model="shelfForm.locationId" placeholder="请输入库位ID" />
        </el-form-item>
        <el-form-item label="库位编码" prop="locationCode">
          <el-input v-model="shelfForm.locationCode" placeholder="请输入库位编码" />
        </el-form-item>
        <el-form-item label="库位名称" prop="locationName">
          <el-input v-model="shelfForm.locationName" placeholder="请输入库位名称" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="shelfForm.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitShelfForm" v-if="!isShelfViewMode">确 定</el-button>
          <el-button @click="cancelShelf">{{ isShelfViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
    
    <!-- 到货通知单选择对话框 -->
    <el-dialog :title="arrivalNoticeDialogTitle" v-model="arrivalNoticeSelectOpen" width="1200px" append-to-body>
      <el-row :gutter="20">
        <el-col :span="24">
          <el-table 
            v-loading="arrivalNoticeLoading" 
            :data="arrivalNoticeLineList" 
            @row-dblclick="selectArrivalNoticeLine"
            highlight-current-row
            style="width: 100%"
            height="400"
          >
            <el-table-column type="index" width="50" align="center" />
            <el-table-column label="产品物料编码" align="center" prop="materialCode" />
            <el-table-column label="产品物料名称" align="center" prop="materialName" />
            <el-table-column label="规格型号" align="center" prop="materialSpec" />
            <el-table-column label="单位" align="center" prop="baseUomName" />
            <el-table-column label="到货数量" align="center" prop="quantityArrival" />
            <el-table-column label="合格数量" align="center" prop="quantityQuanlified" />
            <el-table-column label="操作" align="center" width="100">
              <template #default="scope">
                <el-button link type="primary" @click="selectArrivalNoticeLine(scope.row)">选择</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="arrivalNoticeSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ReceiveForm">
import { getReceive, addReceive, updateReceive } from "@/api/core/wm/receive"
import { getArrivalnotice } from "@/api/core/wm/arrivalnotice"
import { addDetail, updateDetail, listDetail } from "@/api/core/wm/detail"

const { proxy } = getCurrentInstance()
const { wm_material_receive_status } = proxy.useDict('wm_material_receive_status')
const route = useRoute()
const router = useRouter()

const wmMaterialReceiveLineList = ref([])
const checkedWmMaterialReceiveLine = ref([])

// 上架弹窗相关变量
const shelfOpen = ref(false)
const shelfForm = ref({})
const shelfTitle = ref("")
const isShelfViewMode = ref(false)

// 明细数据显示相关
const expandedRows = ref([])

// 到货通知单选择相关变量
const arrivalNoticeSelectOpen = ref(false)
const arrivalNoticeLineList = ref([])
const arrivalNoticeLoading = ref(false)
const currentMaterialRowIndex = ref(-1)
const arrivalNoticeDialogTitle = ref("选择到货通知单物料信息")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    receiveCode: null,
    receiveName: null,
    iqcId: null,
    iqcCode: null,
    noticeId: null,
    noticeCode: null,
    purId: null,
    purCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    receiveDate: null,
    status: null,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  },
  shelfRules: {
    materialId: [
      { required: true, message: "产品物料ID不能为空", trigger: "blur" }
    ],
    quantity: [
      { required: true, message: "入库数量不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, shelfRules } = toRefs(data)

const isViewMode = ref(false)
const title = ref("")
const pageType = ref("") // 新增：用于区分页面类型

onMounted(() => {
  const id = route.query.id
  const action = route.query.action
  pageType.value = route.query.pageType || "" // 获取页面类型参数
  // 设置查看模式
  isViewMode.value = action === 'view'
  console.log('id:', id);

  if (id) {
    initPage(id)
  }
})

function initPage(id) {
  const action = route.query.action

  if (action === 'view') {
    // 查看模式
    isViewMode.value = true
    title.value = "查看物料入库单"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'edit') {
    // 编辑模式
    isViewMode.value = false
    // 根据路由参数确定标题
    const pageType = route.query.pageType
    title.value = pageType === 'shelf' ? "采购单上架" : "修改物料入库单"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'add') {
    // 新增模式
    isViewMode.value = false
    title.value = "新增物料入库单"
    reset()
  }
}

// 获取详细信息
function getInfo(id) {
  getReceive(id).then(response => {
    form.value = response.data
    wmMaterialReceiveLineList.value = response.data.wmMaterialReceiveLineList
    
    // 获取每个行项目的明细数据
    wmMaterialReceiveLineList.value.forEach((line, index) => {
      // 为每一行初始化明细列表
      line.detailList = []
      
      // 如果有行ID，则获取对应的明细数据
      if (line.lineId) {
        loadDetailList(line.lineId, index)
      }
    })
  })
}

// 加载明细数据
function loadDetailList(lineId, index) {
  const queryParams = { lineId: lineId }
  listDetail(queryParams).then(response => {
    // 将获取到的明细数据赋值给对应的行
    wmMaterialReceiveLineList.value[index].detailList = response.rows
  }).catch(error => {
    console.error("加载明细数据失败:", error)
  })
}

// 表单重置
function reset() {
  form.value = {
    receiveId: null,
    receiveCode: null,
    receiveName: null,
    iqcId: null,
    iqcCode: null,
    noticeId: null,
    noticeCode: null,
    purId: null,
    purCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    warehouseId: null,
    warehouseCode: null,
    warehouseName: null,
    areaId: null,
    areaCode: null,
    areaName: null,
    locationId: null,
    locationCode: null,
    locationName: null,
    receiveDate: null,
    status: null,
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
  wmMaterialReceiveLineList.value = []
  proxy.resetForm("receiveRef")
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["receiveRef"].validate(valid => {
    if (valid) {
      form.value.wmMaterialReceiveLineList = wmMaterialReceiveLineList.value
      if (form.value.receiveId != null) {
        updateReceive(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          router.push('/core/wm/receive')
        })
      } else {
        addReceive(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          router.push('/core/wm/receive')
        })
      }
    }
  })
}

// 审核按钮提交表单
function submitApprovingForm() {
  proxy.$refs["receiveRef"].validate(valid => {
    if (valid) {
      // 设置状态为审核中
      form.value.status = 'APPROVING'
      form.value.wmMaterialReceiveLineList = wmMaterialReceiveLineList.value
      if (form.value.receiveId != null) {
        updateReceive(form.value).then(response => {
          proxy.$modal.msgSuccess("审核提交成功")
          router.push('/core/wm/receive')
        })
      } else {
        addReceive(form.value).then(response => {
          proxy.$modal.msgSuccess("审核提交成功")
          router.push('/core/wm/receive')
        })
      }
    }
  })
}

// 取消按钮
function cancel() {
  const obj = { path: '/core/wm/receive' }
  proxy.$tab.closeOpenPage(obj)
}

/** 物料入库单行序号 */
function rowWmMaterialReceiveLineIndex({ row, rowIndex }) {
  row.index = rowIndex + 1
}

/** 物料入库单行添加按钮操作 */
function handleAddWmMaterialReceiveLine() {
  let obj = {}
  obj.sourceDocLineId = ""
  obj.materialId = ""
  obj.materialCode = ""
  obj.materialName = ""
  obj.materialSpec = ""
  obj.baseUomId = ""
  obj.baseUomName = ""
  obj.quantityRecived = ""
  obj.batchId = ""
  obj.batchCode = ""
  obj.warehouseId = ""
  obj.warehouseCode = ""
  obj.warehouseName = ""
  obj.areaId = ""
  obj.areaCode = ""
  obj.areaName = ""
  obj.locationId = ""
  obj.locationCode = ""
  obj.locationName = ""
  obj.detailList = [] // 初始化明细列表
  wmMaterialReceiveLineList.value.push(obj)
}

/** 物料入库单行删除按钮操作 */
function handleDeleteWmMaterialReceiveLine() {
  if (checkedWmMaterialReceiveLine.value.length == 0) {
    proxy.$modal.msgError("请先选择要删除的物料入库单行数据")
  } else {
    const wmMaterialReceiveLines = wmMaterialReceiveLineList.value
    const checkedWmMaterialReceiveLines = checkedWmMaterialReceiveLine.value
    wmMaterialReceiveLineList.value = wmMaterialReceiveLines.filter(function (item) {
      return checkedWmMaterialReceiveLines.indexOf(item.index) == -1
    })
  }
}

/** 复选框选中数据 */
function handleWmMaterialReceiveLineSelectionChange(selection) {
  checkedWmMaterialReceiveLine.value = selection.map(item => item.index)
}

/** 获取字典标签 */
function getDictLabel(dictOptions, value) {
  const dict = dictOptions.find(item => item.value === value)
  return dict ? dict.label : value
}

/**
 * 打开到货通知单物料选择对话框
 */
function openMaterialSelect(rowIndex) {
  // 记录当前行索引
  currentMaterialRowIndex.value = rowIndex
  
  // 检查是否有到货通知单ID
  if (!form.value.noticeId) {
    proxy.$modal.msgWarning("请先选择到货通知单")
    return
  }
  
  // 加载到货通知单的子表信息
  loadArrivalNoticeLines(form.value.noticeId)
  
  // 打开对话框
  arrivalNoticeSelectOpen.value = true
}

/**
 * 加载到货通知单子表信息
 */
function loadArrivalNoticeLines(noticeId) {
  arrivalNoticeLoading.value = true
  getArrivalnotice(noticeId).then(response => {
    arrivalNoticeLineList.value = response.data.wmArrivalNoticeLineList || []
    arrivalNoticeLoading.value = false
  }).catch(error => {
    arrivalNoticeLoading.value = false
    proxy.$modal.msgError("加载到货通知单信息失败")
    console.error(error)
  })
}

/**
 * 选择到货通知单行信息
 */
function selectArrivalNoticeLine(row) {
  // 检查行索引是否有效
  if (currentMaterialRowIndex.value < 0 || currentMaterialRowIndex.value >= wmMaterialReceiveLineList.value.length) {
    proxy.$modal.msgError("选择行索引无效")
    return
  }
  
  // 将选中的到货通知单行信息赋值给当前入库单行
  const currentRow = wmMaterialReceiveLineList.value[currentMaterialRowIndex.value]
  currentRow.materialId = row.materialId
  currentRow.materialCode = row.materialCode
  currentRow.materialName = row.materialName
  currentRow.materialSpec = row.materialSpec
  currentRow.baseUomId = row.baseUomId
  currentRow.baseUomName = row.baseUomName
  currentRow.quantityRecived = row.quantityArrival
  currentRow.sourceDocLineId = row.lineId
  
  // 关闭对话框
  arrivalNoticeSelectOpen.value = false
  currentMaterialRowIndex.value = -1
}

/** 上架按钮操作 */
function handleShelf(row) {
  // 打开上架弹窗
  resetShelfForm();
  isShelfViewMode.value = false;
  
  // 填充从主表传递过来的数据
  shelfForm.value.lineId = row.lineId;
  shelfForm.value.materialId = row.materialId;
  shelfForm.value.materialCode = row.materialCode;
  shelfForm.value.materialName = row.materialName;
  shelfForm.value.baseUomId = row.baseUomId;
  shelfForm.value.baseUomName = row.baseUomName;
  shelfForm.value.quantity = row.quantityRecived;
  shelfForm.value.receiveId = form.value.receiveId;
  
  shelfOpen.value = true;
  shelfTitle.value = "添加物料入库单明细";
}

/** 重置上架表单 */
function resetShelfForm() {
  shelfForm.value = {
    detailId: null,
    lineId: null,
    receiveId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    baseUomId: null,
    baseUomName: null,
    quantity: null,
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
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  };
  proxy.resetForm("shelfRef");
}

/** 取消上架 */
function cancelShelf() {
  shelfOpen.value = false;
  resetShelfForm();
}

/** 提交上架表单 */
function submitShelfForm() {
  proxy.$refs["shelfRef"].validate(valid => {
    if (valid) {
      if (shelfForm.value.detailId != null) {
        updateDetail(shelfForm.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          shelfOpen.value = false;
          // 更新表格中的明细数据
          refreshDetailList();
        });
      } else {
        addDetail(shelfForm.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          shelfOpen.value = false;
          // 更新表格中的明细数据
          refreshDetailList();
        });
      }
    }
  });
}

// 刷新明细列表
function refreshDetailList() {
  // 重新加载当前行的明细数据
  const lineId = shelfForm.value.lineId;
  const rowIndex = wmMaterialReceiveLineList.value.findIndex(line => line.lineId === lineId);
  if (rowIndex >= 0) {
    loadDetailList(lineId, rowIndex);
  }
}

</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>