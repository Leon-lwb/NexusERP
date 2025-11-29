<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div>
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="requirementRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料需求单编号" prop="requirementNo">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.requirementNo" placeholder="请输入物料需求单编号" :disabled="autoGenFlag || isViewMode"
                  style="flex: 1;" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="自动生成" @change="handleAutoGenChange"
                  :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="生产计划ID" prop="planId">
              <el-input v-model="form.planId" placeholder="请输入生产计划ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生产计划编号" prop="planNo">
              <el-input v-model="form.planNo" placeholder="请输入生产计划编号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="BOM ID" prop="bomId">
              <el-input v-model="form.bomId" placeholder="请输入BOM ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需求日期" prop="requirementDate">
              <el-date-picker clearable v-model="form.requirementDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择需求日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料种类总数" prop="totalMaterialTypes">
              <el-input v-model="form.totalMaterialTypes" placeholder="请输入物料种类总数" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需求总量" prop="totalRequiredQuantity">
              <el-input v-model="form.totalRequiredQuantity" placeholder="请输入需求总量" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态">
                <el-option v-for="dict in prd_requirement_status" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划员ID" prop="plannerId">
              <el-input v-model="form.plannerId" placeholder="请输入计划员ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计划员" prop="plannerName">
              <el-input v-model="form.plannerName" placeholder="请输入计划员" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门ID" prop="departmentId">
              <el-input v-model="form.departmentId" placeholder="请输入部门ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="部门" prop="departmentName">
              <el-input v-model="form.departmentName" placeholder="请输入部门" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="确认时间" prop="confirmedTime">
              <el-date-picker clearable v-model="form.confirmedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择确认时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="发料时间" prop="issuedTime">
              <el-date-picker clearable v-model="form.issuedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择发料时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关闭时间" prop="closedTime">
              <el-date-picker clearable v-model="form.closedTime" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择关闭时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
          <el-col :span="12"></el-col>
        </el-row>

        <!-- 子表部分开始 -->
        <el-divider content-position="center">物料需求单明细信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddPrdMaterialRequirementDetail"
              v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeletePrdMaterialRequirementDetail"
              v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="prdMaterialRequirementDetailList" :row-class-name="rowPrdMaterialRequirementDetailIndex"
          @selection-change="handlePrdMaterialRequirementDetailSelectionChange" ref="prdMaterialRequirementDetail">
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="物料ID" prop="materialId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialId" placeholder="请输入物料ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料编码" prop="materialCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请输入物料编码" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="请输入物料名称" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料规格" prop="materialSpec" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialSpec" placeholder="请输入物料规格" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialSpec }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单位" prop="unit" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.unit" placeholder="请输入单位" v-if="!isViewMode" />
              <span v-else>{{ scope.row.unit }}</span>
            </template>
          </el-table-column>
          <el-table-column label="BOM标准用量" prop="bomQuantity" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.bomQuantity" placeholder="请输入BOM标准用量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.bomQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="损耗率(%)" prop="lossRate" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.lossRate" placeholder="请输入损耗率(%)" v-if="!isViewMode" />
              <span v-else>{{ scope.row.lossRate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="需求数量" prop="requiredQuantity" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.requiredQuantity" placeholder="请输入需求数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.requiredQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已发料数量" prop="issuedQuantity" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.issuedQuantity" placeholder="请输入已发料数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.issuedQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="当前库存数量" prop="stockQuantity" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.stockQuantity" placeholder="请输入当前库存数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.stockQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="缺料数量" prop="shortageQuantity" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.shortageQuantity" placeholder="请输入缺料数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.shortageQuantity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="工序步骤" prop="operationStep" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.operationStep" placeholder="请输入工序步骤" v-if="!isViewMode" />
              <span v-else>{{ scope.row.operationStep }}</span>
            </template>
          </el-table-column>
          <el-table-column label="工作中心ID" prop="workstationId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.workstationId" placeholder="请输入工作中心ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.workstationId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="工作中心名称" prop="workstationName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.workstationName" placeholder="请输入工作中心名称" v-if="!isViewMode" />
              <span v-else>{{ scope.row.workstationName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="提前期(小时)" prop="leadTime" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.leadTime" placeholder="请输入提前期(小时)" v-if="!isViewMode" />
              <span v-else>{{ scope.row.leadTime }}</span>
            </template>
          </el-table-column>
          <el-table-column label="需求日期" prop="requiredDate" width="240">
            <template #default="scope">
              <el-date-picker clearable v-model="scope.row.requiredDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择需求日期" v-if="!isViewMode">
              </el-date-picker>
              <span v-else>{{ scope.row.requiredDate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" prop="status" width="150">
            <template #default="scope">
              <el-select v-model="scope.row.status" placeholder="请选择状态" v-if="!isViewMode">
                <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
              <span v-else>{{ getDictLabel(sys_core_status, scope.row.status) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <!-- 子表部分结束 -->
      </el-form>
    </el-card>
  </div>
</template>

<script setup name="RequirementForm">
import { getRequirement, addRequirement, updateRequirement } from "@/api/core/prd/requirement"
import { genCode } from "@/api/system/autocode/rule"

const { proxy } = getCurrentInstance()
const { sys_core_status, prd_requirement_status } = proxy.useDict('sys_core_status', 'prd_requirement_status')
const route = useRoute()
const router = useRouter()
const autoGenFlag = ref(false) // 新增：控制编号自动生成开关

const form = ref({})
const isViewMode = ref(false)
const title = ref("")
const prdMaterialRequirementDetailList = ref([])
const checkedPrdMaterialRequirementDetail = ref([])

onMounted(() => {
  const id = route.query.id
  const action = route.query.action
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
    title.value = "查看物料清单"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'edit') {
    // 编辑模式
    isViewMode.value = false
    title.value = "修改物料清单"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'add') {
    // 新增模式
    isViewMode.value = false
    title.value = "新增物料清单"
    reset()
  }
}

// 获取详细信息
function getInfo(id) {
  getRequirement(id).then(response => {
    form.value = response.data
    prdMaterialRequirementDetailList.value = response.data.prdMaterialRequirementDetailList
  })
}

// 表单重置
function reset() {
  autoGenFlag.value = false
  form.value = {
    requirementId: null,
    requirementNo: null,
    planId: null,
    planNo: null,
    bomId: null,
    requirementDate: null,
    totalMaterialTypes: null,
    totalRequiredQuantity: null,
    status: null,
    plannerId: null,
    plannerName: null,
    departmentId: null,
    departmentName: null,
    confirmedTime: null,
    issuedTime: null,
    closedTime: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  prdMaterialRequirementDetailList.value = []
  proxy.resetForm("requirementRef")
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["requirementRef"].validate(valid => {
    if (valid) {
      form.value.prdMaterialRequirementDetailList = prdMaterialRequirementDetailList.value
      if (form.value.requirementId != null) {
        updateRequirement(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          router.push('/core/prd/requirement')
        })
      } else {
        addRequirement(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          router.push('/core/prd/requirement')
        })
      }
    }
  })
}

// 取消按钮
function cancel() {
  const obj = { path: '/core/prd/requirement' }
  proxy.$tab.closeOpenPage(obj)
}

/** 物料需求单明细序号 */
function rowPrdMaterialRequirementDetailIndex({ row, rowIndex }) {
  row.index = rowIndex + 1
}

/** 物料需求单明细添加按钮操作 */
function handleAddPrdMaterialRequirementDetail() {
  let obj = {}
  obj.materialId = ""
  obj.materialCode = ""
  obj.materialName = ""
  obj.materialSpec = ""
  obj.unit = ""
  obj.bomQuantity = ""
  obj.lossRate = ""
  obj.requiredQuantity = ""
  obj.issuedQuantity = ""
  obj.stockQuantity = ""
  obj.shortageQuantity = ""
  obj.operationStep = ""
  obj.workstationId = ""
  obj.workstationName = ""
  obj.leadTime = ""
  obj.requiredDate = ""
  obj.status = ""
  obj.remark = ""
  prdMaterialRequirementDetailList.value.push(obj)
}

/** 物料需求单明细删除按钮操作 */
function handleDeletePrdMaterialRequirementDetail() {
  if (checkedPrdMaterialRequirementDetail.value.length == 0) {
    proxy.$modal.msgError("请先选择要删除的物料需求单明细数据")
  } else {
    const prdMaterialRequirementDetails = prdMaterialRequirementDetailList.value
    const checkedPrdMaterialRequirementDetails = checkedPrdMaterialRequirementDetail.value
    prdMaterialRequirementDetailList.value = prdMaterialRequirementDetails.filter(function (item) {
      return checkedPrdMaterialRequirementDetails.indexOf(item.index) == -1
    })
  }
}

/** 复选框选中数据 */
function handlePrdMaterialRequirementDetailSelectionChange(selection) {
  checkedPrdMaterialRequirementDetail.value = selection.map(item => item.index)
}

/** 获取字典标签 */
function getDictLabel(dictOptions, value) {
  const dict = dictOptions.find(item => item.value === value)
  return dict ? dict.label : value
}

// 自动生产编号
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    genCode('PRD_MATERIAL_MBOM').then(response => {
      form.value.requirementNo = response;
    });
  } else {
    form.value.requirementNo = null;
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