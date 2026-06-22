<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button type="success" @click="handleSubmitFlow" v-if="!isViewMode && form.status === 'DRAFT'">提交审批</el-button>
            <el-button type="info" @click="onViewFlow" v-if="form.status === 'APPROVING' || form.status === 'APPROVED'">查看流程</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="arrivalnoticeRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="通知单编号" prop="noticeCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.noticeCode" placeholder="请输入通知单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1;" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="生成" @change="handleAutoGenChange" :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="通知单名称" prop="noticeName">
              <el-input v-model="form.noticeName" placeholder="请输入通知单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="采购订单编号" prop="purOrderCode">
              <el-input v-model="form.purOrderCode" placeholder="请输入采购订单编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">供应商信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商ID" prop="vendorId">
              <el-input v-model="form.vendorId" placeholder="请输入供应商ID" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商编码" prop="vendorCode">
              <el-input v-model="form.vendorCode" placeholder="请输入供应商编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="供应商名称" prop="vendorName">
              <el-input v-model="form.vendorName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="供应商简称" prop="vendorNick">
              <el-input v-model="form.vendorNick" placeholder="请输入供应商简称" />
            </el-form-item>
          </el-col>
          <el-col :span="8"></el-col>
          <el-col :span="8"></el-col>
        </el-row>
        <el-divider content-position="left">其他信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="到货日期" prop="arrivalDate">
              <el-date-picker clearable v-model="form.arrivalDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择到货日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系人" prop="contact">
              <el-input v-model="form.contact" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系方式" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系方式" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="审批流程" prop="selectFlowId">
              <el-select
                v-model="form.selectFlowId"
                clearable
                filterable
                :loading="loadingFlow"
                :disabled="isViewMode || form.status !== 'DRAFT'"
                placeholder="请选择审批流程"
                style="width: 100%"
              >
                <el-option
                  v-for="f in flowOptions"
                  :key="f.id"
                  :label="f.flowName || f.businessName || ('流程#' + f.id)"
                  :value="f.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单据状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in wm_notice_status" :key="dict.value"
                  :label="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 流程选择警告 -->
        <el-alert
          v-if="!isViewMode && !loadingFlow && flowOptions.length === 0 && (!form.selectFlowId || form.selectFlowId === '')"
          type="warning"
          show-icon
          :closable="false"
          title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 wmArrivalNoticeService。"
          style="margin-bottom: 12px;"
        />

        <!-- 子表部分开始 -->
        <el-divider content-position="center">到货通知单行信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddWmArrivalNoticeLine"
              v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteWmArrivalNoticeLine"
              v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="wmArrivalNoticeLineList" :row-class-name="rowWmArrivalNoticeLineIndex"
          @selection-change="handleWmArrivalNoticeLineSelectionChange" ref="wmArrivalNoticeLine">
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
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
          <el-table-column label="到货数量" prop="quantityArrival" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.quantityArrival" placeholder="请输入到货数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.quantityArrival }}</span>
            </template>
          </el-table-column>
          <el-table-column label="合格数量" prop="quantityQuanlified" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.quantityQuanlified" placeholder="请输入合格数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.quantityQuanlified }}</span>
            </template>
          </el-table-column>
          <el-table-column label="是否来料检验" prop="iqcCheck" width="150">
            <template #default="scope">
              <el-select v-model="scope.row.iqcCheck" placeholder="请选择是否来料检验" v-if="!isViewMode">
                <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
              <span v-else>{{ getDictLabel(sys_true_or_false, scope.row.iqcCheck) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="来料检验单ID" prop="iqcId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.iqcId" placeholder="请输入来料检验单ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.iqcId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="来料检验单编号" prop="iqcCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.iqcCode" placeholder="请输入来料检验单编号" v-if="!isViewMode" />
              <span v-else>{{ scope.row.iqcCode }}</span>
            </template>
          </el-table-column>
        </el-table>
        <!-- 子表部分结束 -->
      </el-form>
    </el-card>

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

<script setup lang="ts" name="ArrivalnoticeForm">
import { getArrivalnotice, addArrivalnotice, updateArrivalnotice, submitArrivalnotice, getArrivalnoticeProcId } from "@/api/erp/wm/arrivalnotice"
import { getProcesss } from "@/api/workflow/process"
import { listMaterial, getMaterial } from "@/api/erp/bd/material"
import { listCategory, getAllChildrenIds } from "@/api/erp/bd/category"
import { getMeasure } from "@/api/erp/bd/measure"
import { debounce } from "lodash-es"
import { useRoute, useRouter } from 'vue-router'
import { previewCode } from "@/api/system/autocode/rule"
import type { ArrivalnoticeForm } from "@/api/erp/wm/arrivalnotice/types"

const { proxy } = getCurrentInstance() as any
const { sys_true_or_false, wm_notice_status, bd_material_type } = toRefs<any>(proxy.useDict('sys_true_or_false', 'wm_notice_status', 'bd_material_type'))
const route = useRoute()
const router = useRouter()

// 添加物料查询引用
const materialQueryRef = ref()

const form = ref<ArrivalnoticeForm>({})
const autoGenFlag = ref(false)
const isViewMode = ref(false)
const title = ref("")

// 添加缺失的响应式变量定义
const wmArrivalNoticeLineList = ref([])
const checkedWmArrivalNoticeLine = ref([])

// 物料选择相关变量
const materialSelectOpen = ref(false)
const materialList = ref([])
const materialLoading = ref(false)
const materialTotal = ref(0)
const currentMaterialRowIndex = ref(-1)
const materialQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  materialCode: null,
  materialName: null,
  categoryId: null
})

// 物料分类相关
const categoryTree = ref([])
const categoryTreeProps = {
  children: 'children',
  label: 'categoryName',
  value: 'categoryId'
}
const categoryFilterText = ref('')
const categoryTreeRef = ref(null)

// 流程相关状态
const loadingFlow = ref(false)
const flowOptions = ref([])

onMounted(() => {
  const id = route.query.id
  const action = route.query.action
  // 设置查看模式
  isViewMode.value = action === 'view'

  // 获取物料分类树
  getCategoryTree()

  if (id) {
    initPage(id)
  }
})

function initPage(id: any) {
  const action = route.query.action

  if (action === 'view') {
    // 查看模式
    isViewMode.value = true
    title.value = "查看到货通知"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'edit') {
    // 编辑模式
    isViewMode.value = false
    title.value = "修改到货通知"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'add') {
    // 新增模式
    isViewMode.value = false
    title.value = "新增到货通知"
    reset()
    loadFlows()
  }
}

// 获取详细信息
function getInfo(id: any) {
  getArrivalnotice(id).then((response: any) => {
    form.value = response.data
    wmArrivalNoticeLineList.value = response.data.wmArrivalNoticeLineList
    loadFlows()
  })
}

// 表单重置
function reset() {
  autoGenFlag.value = false
  form.value = {
    noticeId: null,
    noticeCode: null,
    noticeName: null,
    purOrderCode: null,
    vendorId: null,
    vendorCode: null,
    vendorName: null,
    vendorNick: null,
    arrivalDate: null,
    contact: null,
    phone: null,
    status: null,
    selectFlowId: null,
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
  wmArrivalNoticeLineList.value = []
  proxy.resetForm("arrivalnoticeRef")
}

// 表单校验规则
const rules = {
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["arrivalnoticeRef"].validate((valid: boolean) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.noticeCode = undefined;
      }
      form.value.wmArrivalNoticeLineList = wmArrivalNoticeLineList.value
      if (form.value.noticeId != null) {
        updateArrivalnotice(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          router.push('/erp/wm/arrivalnotice')
        })
      } else {
        addArrivalnotice(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          router.push('/erp/wm/arrivalnotice')
        })
      }
    }
  })
}

// 取消按钮
//自动生成编号
function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    previewCode('WM_ARRIVAL_NOTICE_CODE').then((response: any) => {
      form.value.noticeCode = response;
    });
  } else {
    form.value.noticeCode = null;
  }
}

function cancel() {
  const obj = { path: '/erp/wm/arrivalnotice' }
  proxy.$tab.closeOpenPage(obj)
}

// ========== 流程相关方法 ==========

/** 加载可用审批流程列表 */
function loadFlows() {
  loadingFlow.value = true
  getProcesss({ serviceName: 'wmArrivalNoticeService' }).then((res: any) => {
    flowOptions.value = res?.data || []
    tryAutoSelect()
  }).catch(() => {
    flowOptions.value = []
  }).finally(() => {
    loadingFlow.value = false
  })
}

/** 如果只有一个可用流程，自动选中 */
function tryAutoSelect() {
  if (flowOptions.value.length === 1 && !form.value.selectFlowId) {
    form.value.selectFlowId = flowOptions.value[0].id
  }
}

/** 提交审批 */
function handleSubmitFlow() {
  if (!form.value.noticeId) {
    proxy.$modal.msgWarning("请先保存单据")
    return
  }
  if (!form.value.selectFlowId) {
    proxy.$modal.msgWarning("请先选择审批流程并保存")
    return
  }
  proxy.$modal.confirm('确认提交到货通知审批？').then(() => {
    submitArrivalnotice(String(form.value.noticeId), String(form.value.selectFlowId)).then(() => {
      proxy.$modal.msgSuccess("提交审批成功")
      getInfo(form.value.noticeId)
    })
  })
}

/** 查看流程 */
function onViewFlow() {
  if (!form.value.noticeId) return
  getArrivalnoticeProcId(String(form.value.noticeId)).then((res: any) => {
    const procInsId = res.data
    if (!procInsId) {
      proxy.$modal.msgWarning("暂无流程信息")
      return
    }
    router.push({
      path: '/workflow/process/detail/' + procInsId,
      query: { processed: 'false' }
    })
  })
}

// 物料选择相关方法
/** 打开物料选择对话框 */
function openMaterialSelect(rowIndex: number) {
  currentMaterialRowIndex.value = rowIndex
  materialSelectOpen.value = true
  getMaterialList()
}

/** 查询物料列表 */
function getMaterialList() {
  materialLoading.value = true
  listMaterial(materialQueryParams.value).then((response: any) => {
    materialList.value = response.rows
    materialTotal.value = response.total
    materialLoading.value = false
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
  handleMaterialQuery()
}

/** 选择物料 */
function selectMaterial(row: any) {
  if (currentMaterialRowIndex.value >= 0 && wmArrivalNoticeLineList.value[currentMaterialRowIndex.value]) {
    wmArrivalNoticeLineList.value[currentMaterialRowIndex.value].materialId = row.materialId
    wmArrivalNoticeLineList.value[currentMaterialRowIndex.value].materialCode = row.materialCode
    wmArrivalNoticeLineList.value[currentMaterialRowIndex.value].materialName = row.materialName
    wmArrivalNoticeLineList.value[currentMaterialRowIndex.value].materialSpec = row.materialSpec

    // 获取计量单位名称
    if (row.baseUomId) {
      wmArrivalNoticeLineList.value[currentMaterialRowIndex.value].baseUomId = row.baseUomId

      // 保存当前行索引的引用，防止异步回调时索引已变化
      const rowIndex = currentMaterialRowIndex.value;

      getMeasure(row.baseUomId).then((response: any) => {
        // 使用保存的索引引用而不是 currentMaterialRowIndex.value
        if (rowIndex >= 0 && wmArrivalNoticeLineList.value[rowIndex]) {
          // 根据您提供的响应数据，字段访问是正确的
          wmArrivalNoticeLineList.value[rowIndex].baseUomName = response.data.uomName
        }
      }).catch((error: any) => {
        console.error('获取计量单位信息失败:', error);
        // 同样使用保存的索引引用
        if (rowIndex >= 0 && wmArrivalNoticeLineList.value[rowIndex]) {
          wmArrivalNoticeLineList.value[rowIndex].baseUomName = ''
        }
      })
    } else {
      wmArrivalNoticeLineList.value[currentMaterialRowIndex.value].baseUomId = ''
      wmArrivalNoticeLineList.value[currentMaterialRowIndex.value].baseUomName = ''
    }

    // 可以根据需要添加更多字段赋值
  }
  materialSelectOpen.value = false
  currentMaterialRowIndex.value = -1
}

/** 到货通知单行序号 */
function rowWmArrivalNoticeLineIndex({ row, rowIndex }: any) {
  row.index = rowIndex + 1
}

/** 到货通知单行添加按钮操作 */
function handleAddWmArrivalNoticeLine() {
  let obj: any = {}
  obj.materialId = ""
  obj.materialCode = ""
  obj.materialName = ""
  obj.materialSpec = ""
  obj.baseUomId = ""
  obj.baseUomName = ""
  obj.quantityArrival = ""
  obj.quantityQuanlified = ""
  obj.iqcCheck = ""
  obj.iqcId = ""
  obj.iqcCode = ""
  obj.remark = ""
  wmArrivalNoticeLineList.value.push(obj)
}

/** 到货通知单行删除按钮操作 */
function handleDeleteWmArrivalNoticeLine() {
  if (checkedWmArrivalNoticeLine.value.length == 0) {
    proxy.$modal.msgError("请先选择要删除的到货通知单行数据")
  } else {
    const wmArrivalNoticeLines = wmArrivalNoticeLineList.value
    const checkedWmArrivalNoticeLines = checkedWmArrivalNoticeLine.value
    wmArrivalNoticeLineList.value = wmArrivalNoticeLines.filter(function (item: any) {
      return checkedWmArrivalNoticeLines.indexOf(item.index) == -1
    })
  }
}

/** 复选框选中数据 */
function handleWmArrivalNoticeLineSelectionChange(selection: any[]) {
  checkedWmArrivalNoticeLine.value = selection.map((item: any) => item.index)
}

/** 获取字典标签 */
function getDictLabel(dictOptions: any, value: any) {
  const dict = dictOptions.find((item: any) => item.value === value)
  return dict ? dict.label : value
}

// 获取物料分类树
function getCategoryTree() {
  // 确保分类树也只获取启用状态的分类
  listCategory({ status: 1 }).then((response: any) => {
    // 处理分类树数据，确保数据结构符合 el-tree 要求
    const data = response.data || response.rows || []
    const filteredData = Array.isArray(data) ? data.filter((item: any) => item !== null && item !== undefined) : []
    const treeData = proxy.handleTree(filteredData, "categoryId", "parentId");
    categoryTree.value = treeData;
  }).catch((error: any) => {
    console.error('获取物料分类失败:', error)
    proxy.$modal.msgError("获取物料分类失败")
  })
}

// 处理物料分类节点点击 - 使用getAllChildrenIds接口返回的物料数据
function handleCategoryNodeClick(data: any) {
  const categoryId = Number(data.id || data.categoryId)

  if (isNaN(categoryId) || categoryId <= 0) {
    console.error('无效的分类ID:', categoryId)
    proxy.$modal.msgError("无效的分类ID")
    return
  }

  materialLoading.value = true
  getAllChildrenIds(categoryId).then((response: any) => {
    if (response.code === 200) {
      let allMaterials: any[] = [];

      // 处理不同可能的数据结构
      if (response.data && Array.isArray(response.data)) {
        // 直接是物料数组
        allMaterials = response.data;
      } else if (response.data && response.data.materials && Array.isArray(response.data.materials)) {
        // 包含在materials字段中
        allMaterials = response.data.materials;
      }

      // 过滤启用状态的物料（更宽松的比较）
      const enabledMaterials = allMaterials.filter((material: any) => {
        return String(material.status) === '1' || material.status === 1;
      });

      materialList.value = enabledMaterials
      materialTotal.value = enabledMaterials.length
      materialLoading.value = false
    } else {
      proxy.$modal.msgError("获取分类信息失败: " + response.msg)
      materialLoading.value = false
    }
  }).catch((error: any) => {
    console.error('获取分类信息异常:', error)
    proxy.$modal.msgError("请求出现异常: " + error.message)
    materialLoading.value = false
  })
}


// 添加分类树过滤方法
function filterCategoryNode(value: string, data: any) {
  if (!value) return true
  return data.categoryName && data.categoryName.includes(value)
}

// 添加处理分类过滤输入
function handleCategoryFilter(val: string) {
  if (categoryTreeRef.value) {
    categoryTreeRef.value.filter(val)
  }
}

// 添加防抖处理分类过滤
const debouncedHandleCategoryFilter = debounce((val: string) => {
  handleCategoryFilter(val)
}, 300)

// 添加组件销毁时的清理逻辑
onUnmounted(() => {
  // 取消防抖函数的执行
  if (debouncedHandleCategoryFilter && typeof debouncedHandleCategoryFilter.cancel === 'function') {
    debouncedHandleCategoryFilter.cancel()
  }
})

</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.card-actions {
  display: flex;
  gap: 10px;
}

.mb8 {
  margin-bottom: 8px;
}

:deep(.el-divider__text) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}

:deep(.el-form-item) {
  margin-bottom: 18px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
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
