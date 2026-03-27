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

      <el-form ref="workRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <!-- 基本信息 -->
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="工单编号" prop="woNo">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.woNo" placeholder="请输入工单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1;" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="生成" @change="handleAutoGenChange" :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="来源计划ID" prop="planId">
              <el-input v-model="form.planId" placeholder="请输入来源计划ID" readonly>
                <template #append>
                  <el-button icon="Search" @click="openPlanSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划名称" prop="planName">
              <el-input v-model="form.planName" placeholder="请输入计划名称" readonly>
                <template #append>
                  <el-button icon="Search" @click="openPlanSelect" :disabled="isViewMode" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型">
                <el-option v-for="dict in prd_source_type" :key="dict.value" :label="dict.label"
                  :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="来源单据ID" prop="sourceId">
              <el-input v-model="form.sourceId" placeholder="请输入来源单据ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="销售订单编号" prop="sourceNo">
              <el-input v-model="form.sourceNo" placeholder="请输入销售订单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="来源单据行ID" prop="sourceLineId">
              <el-input v-model="form.sourceLineId" placeholder="请输入来源单据行ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="产品物料ID" prop="materialId">
              <el-input v-model="form.materialId" placeholder="请输入产品物料ID" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="产品编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请输入产品编码" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="产品名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入产品名称" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划数量" prop="plannedQty">
              <el-input v-model="form.plannedQty" placeholder="请输入计划数量" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="已完成数量" prop="completedQty">
              <el-input v-model="form.completedQty" placeholder="请输入已完成数量" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="报废数量" prop="scrapQty">
              <el-input v-model="form.scrapQty" placeholder="请输入报废数量" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="单位id" prop="unitId">
              <el-input v-model="form.unitId" placeholder="请输入单位id" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="单位名称" prop="unitName">
              <el-input v-model="form.unitName" placeholder="请输入单位名称" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority" placeholder="请选择优先级">
                <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="bomName" prop="bomName">
              <el-input v-model="form.bomName" placeholder="请输入bomName" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="BOM版本" prop="bomVersion">
              <el-input v-model="form.bomVersion" placeholder="请输入BOM版本" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="车间ID" prop="workshopId">
              <el-input v-model="form.workshopId" placeholder="请输入车间ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="车间名称" prop="workshopName">
              <el-input v-model="form.workshopName" placeholder="请输入车间名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="工作站ID" prop="workstationId">
              <el-input v-model="form.workstationId" placeholder="请输入工作站ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="工作站名称" prop="workstationName">
              <el-input v-model="form.workstationName" placeholder="请输入工作站名称" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="生产线ID" prop="productionLineId">
              <el-input v-model="form.productionLineId" placeholder="请输入生产线ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="负责人ID" prop="responsibleId">
              <el-input v-model="form.responsibleId" placeholder="请输入负责人ID" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="负责人" prop="responsibleName">
              <el-input v-model="form.responsibleName" placeholder="请输入负责人" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="班组ID" prop="teamId">
              <el-input v-model="form.teamId" placeholder="请输入班组ID" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="班组" prop="teamName">
              <el-input v-model="form.teamName" placeholder="请输入班组" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="工单状态" prop="woStatus">
              <el-radio-group v-model="form.woStatus">
                <el-radio v-for="dict in prd_wo_status" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 时间信息 -->
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="计划开始日期" prop="planStartDate">
              <el-date-picker clearable v-model="form.planStartDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划开始日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="计划结束日期" prop="planEndDate">
              <el-date-picker clearable v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择计划结束日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="实际开始时间" prop="actualStartDate">
              <el-date-picker clearable v-model="form.actualStartDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择实际开始时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="实际结束时间" prop="actualEndDate">
              <el-date-picker clearable v-model="form.actualEndDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择实际结束时间">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 长字段单独行显示 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="产品规格" prop="materialSpec">
              <el-input v-model="form.materialSpec" type="textarea" placeholder="请输入内容" />
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
        
        <!-- 预留字段 -->
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="预留字段1" prop="bomName">
              <el-input v-model="form.bomName" placeholder="请输入bomName" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="预留字段2" prop="attr2">
              <el-input v-model="form.attr2" placeholder="请输入预留字段2" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="预留字段3" prop="attr3">
              <el-input v-model="form.attr3" placeholder="请输入预留字段3" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="预留字段4" prop="attr4">
              <el-input v-model="form.attr4" placeholder="请输入预留字段4" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
    
    <!-- 生产计划选择对话框 -->
    <el-dialog title="选择生产计划" v-model="planSelectOpen" width="1000px" append-to-body>
      <el-form :model="planQueryParams" :inline="true">
        <el-form-item label="计划编号" prop="planNo">
          <el-input v-model="planQueryParams.planNo" placeholder="请输入计划编号" clearable @keyup.enter="getPlanList" />
        </el-form-item>
        <el-form-item label="计划名称" prop="planName">
          <el-input v-model="planQueryParams.planName" placeholder="请输入计划名称" clearable @keyup.enter="getPlanList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getPlanList">搜索</el-button>
          <el-button icon="Refresh" @click="resetPlanQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="planLoading" :data="planList" @row-dblclick="selectPlan">
        <el-table-column label="计划编号" align="center" prop="planNo" />
        <el-table-column label="计划名称" align="center" prop="planName" />
        <el-table-column label="计划类型" align="center" prop="planType">
          <template #default="scope">
            <dict-tag :options="prd_plan_type" :value="scope.row.planType" />
          </template>
        </el-table-column>
        <el-table-column label="计划日期" align="center" prop="planDate">
          <template #default="scope">
            <span>{{ parseTime(scope.row.planDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划开始日期" align="center" prop="startDate">
          <template #default="scope">
            <span>{{ parseTime(scope.row.startDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划结束日期" align="center" prop="endDate">
          <template #default="scope">
            <span>{{ parseTime(scope.row.endDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status">
          <template #default="scope">
            <dict-tag :options="prd_plan_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectPlan(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="planTotal > 0" :total="planTotal" v-model:page="planQueryParams.pageNum"
        v-model:limit="planQueryParams.pageSize" @pagination="getPlanList" />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="planSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WorkForm">
import { getWork, addWork, updateWork } from "@/api/core/prd/work"
import { listPlan } from "@/api/core/prd/plan"
import { genCode } from "@/api/system/autocode/rule"

const { proxy } = getCurrentInstance()
const { prd_source_type, so_order_priority, prd_wo_status, prd_plan_type, prd_plan_status } = proxy.useDict('prd_source_type', 'so_order_priority', 'prd_wo_status', 'prd_plan_type', 'prd_plan_status')
const route = useRoute()
const router = useRouter()

const form = ref({})
const isViewMode = ref(false)
const title = ref("")
const autoGenFlag = ref(false)

// 添加表单验证规则
const rules = ref({
  woNo: [{ required: true, message: "工单编号不能为空", trigger: "blur" }],
  materialName: [{ required: true, message: "产品名称不能为空", trigger: "blur" }],
  plannedQty: [{ required: true, message: "计划数量不能为空", trigger: "blur" }]
})

// 生产计划选择相关变量
const planSelectOpen = ref(false)
const planList = ref([])
const planLoading = ref(false)
const planTotal = ref(0)
const planQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  planNo: null,
  planName: null,
  isCurrent: 1,      // 只显示当前版本
  status: 'CONFIRMED' // 只显示已确认状态
})

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
    title.value = "查看生产工单"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'edit') {
    // 编辑模式
    isViewMode.value = false
    title.value = "修改生产工单"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'add') {
    // 新增模式
    isViewMode.value = false
    title.value = "新增生产工单"
    reset()
  }
}

// 获取详细信息
function getInfo(id) {
  getWork(id).then(response => {
    form.value = response.data
  })
}

// 表单重置
function reset() {
  autoGenFlag.value = false
  form.value = {
    woId: null,
    woNo: null,
    planId: null,
    planName: null,
    sourceType: null,
    sourceId: null,
    sourceNo: null,
    sourceLineId: null,
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    plannedQty: null,
    completedQty: null,
    scrapQty: null,
    unitId: null,
    unitName: null,
    planStartDate: null,
    planEndDate: null,
    actualStartDate: null,
    actualEndDate: null,
    woStatus: null,
    workshopId: null,
    workshopName: null,
    workstationId: null,
    workstationName: null,
    productionLineId: null,
    responsibleId: null,
    responsibleName: null,
    teamId: null,
    teamName: null,
    priority: null,
    bomId: null,
    bomVersion: null,
    remark: null,
    bomName: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  proxy.resetForm("workRef")
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["workRef"].validate(valid => {
    if (valid) {
      if (form.value.woId != null) {
        updateWork(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          router.push('/core/prd/prdwork')
        })
      } else {
        addWork(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          router.push('/core/prd/prdwork')
        })
      }
    }
  })
}

// 取消按钮
function cancel() {
  const obj = { path: '/core/prd/prdwork' }
  proxy.$tab.closeOpenPage(obj)
}

/** 获取字典标签 */
function getDictLabel(dictOptions, value) {
  const dict = dictOptions.find(item => item.value === value)
  return dict ? dict.label : value
}

// 打开生产计划选择对话框
function openPlanSelect() {
  planSelectOpen.value = true
  resetPlanQuery()
  getPlanList()
}

// 查询生产计划列表
function getPlanList() {
  planLoading.value = true
  listPlan(planQueryParams.value).then(response => {
    planList.value = response.rows
    planTotal.value = response.total
    planLoading.value = false
  })
}

// 重置生产计划查询条件
function resetPlanQuery() {
  proxy.resetForm("planQueryForm")
  planQueryParams.value.pageNum = 1
  planQueryParams.value.isCurrent = 1
  planQueryParams.value.status = 'CONFIRMED'
}

// 选择生产计划
function selectPlan(row) {
  // 计划基本信息
  form.value.planId = row.planId
  form.value.planName = row.planName
  
  // 来源信息
  form.value.sourceType = 'PLAN' // 设置来源类型为计划
  form.value.sourceId = row.planId // 来源单据ID（使用计划ID）
  form.value.sourceNo = row.referenceNo // 销售订单编号/参考单据号
  
  // 数量信息
  form.value.plannedQty = row.totalQuantity // 计划总数量
  
  // 日期信息
  form.value.planStartDate = row.startDate // 计划开始日期
  form.value.planEndDate = row.endDate // 计划结束日期
  
  // 其他信息
  form.value.priority = row.priority // 优先级
  form.value.remark = row.remark // 备注
  
  // BOM信息（如果计划中有）
  if (row.bomId) {
    form.value.bomId = row.bomId
  }
  if (row.bomVersion) {
    form.value.bomVersion = row.bomVersion
  }
  if (row.bomName) {
    form.value.bomName = row.bomName
  }
  
  // TODO: 以下字段需要从其他途径获取
  // 1. 物料信息（materialId, materialCode, materialName, unitId, unitName）
  //    建议方案：根据referenceNo查询销售订单获取物料信息，或根据bomId查询BOM获取物料
  // 2. 车间/工作站信息（workshopId, workshopName, workstationId, workstationName）
  //    建议方案：根据departmentId查询部门信息映射到车间
  
  planSelectOpen.value = false
  
  // 提示用户需要补充的信息
  proxy.$modal.msgInfo("已选择计划，请补充完善物料、车间等信息")
}

// 自动生成编号
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    genCode('PRD_WORK_ORDER_CODE').then(response => {
      form.value.woNo = response;
    });
  } else {
    form.value.woNo = null;
  }
}

// 解析时间格式
function parseTime(time, pattern) {
  if (arguments.length === 0 || !time) {
    return null
  }
  const format = pattern || '{y}-{m}-{d} {h}:{i}:{s}'
  let date
  if (typeof time === 'object') {
    date = time
  } else {
    if ((typeof time === 'string') && (/^[0-9]+$/.test(time))) {
      time = parseInt(time)
    } else if (typeof time === 'string') {
      time = time.replace(new RegExp(/-/gm), '/').replace('T', ' ').replace(new RegExp(/\.[\d]{3}/gm), '')
    }
    if ((typeof time === 'number') && (time.toString().length === 10)) {
      time = time * 1000
    }
    date = new Date(time)
  }
  const formatObj = {
    y: date.getFullYear(),
    m: date.getMonth() + 1,
    d: date.getDate(),
    h: date.getHours(),
    i: date.getMinutes(),
    s: date.getSeconds(),
    a: date.getDay()
  }
  const time_str = format.replace(/{(y|m|d|h|i|s|a)+}/g, (result, key) => {
    let value = formatObj[key]
    // Note: getDay() returns 0 on Sunday
    if (key === 'a') { return ['日', '一', '二', '三', '四', '五', '六'][value] }
    if (result.length > 0 && value < 10) {
      value = '0' + value
    }
    return value || 0
  })
  return time_str
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>