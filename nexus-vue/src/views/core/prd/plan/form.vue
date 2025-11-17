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

      <el-form ref="planRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-form-item label="生产计划编号" prop="planNo">
          <el-input v-model="form.planNo" placeholder="请输入生产计划编号" />
        </el-form-item>
        <el-form-item label="计划名称" prop="planName">
          <el-input v-model="form.planName" placeholder="请输入计划名称" />
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="form.version" placeholder="请输入版本号" />
        </el-form-item>
        <el-form-item label="原始计划ID" prop="originalPlanId">
          <el-input v-model="form.originalPlanId" placeholder="请输入原始计划ID" />
        </el-form-item>
        <el-form-item label="参考单据号" prop="referenceNo">
          <el-input v-model="form.referenceNo" placeholder="请输入参考单据号" />
        </el-form-item>
        <el-form-item label="计划类型" prop="planType">
          <el-select v-model="form.planType" placeholder="请选择计划类型">
            <el-option v-for="dict in prd_plan_type" :key="dict.value" :label="dict.label"
              :value="dict.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期" prop="planDate">
          <el-date-picker clearable v-model="form.planDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择计划日期">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="计划开始日期" prop="startDate">
          <el-date-picker clearable v-model="form.startDate" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择计划开始日期">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="计划结束日期" prop="endDate">
          <el-date-picker clearable v-model="form.endDate" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择计划结束日期">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in prd_plan_status" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="form.priority" placeholder="请选择优先级">
            <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label"
              :value="parseInt(dict.value)"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="计划员ID" prop="plannerId">
          <el-input v-model="form.plannerId" placeholder="请输入计划员ID" />
        </el-form-item>
        <el-form-item label="计划员" prop="plannerName">
          <el-input v-model="form.plannerName" placeholder="请输入计划员" />
        </el-form-item>
        <el-form-item label="生产部门ID" prop="departmentId">
          <el-input v-model="form.departmentId" placeholder="请输入生产部门ID" />
        </el-form-item>
        <el-form-item label="生产部门" prop="departmentName">
          <el-input v-model="form.departmentName" placeholder="请输入生产部门" />
        </el-form-item>
        <el-form-item label="计划总数量" prop="totalQuantity">
          <el-input v-model="form.totalQuantity" placeholder="请输入计划总数量" />
        </el-form-item>
        <el-form-item label="确认时间" prop="confirmedTime">
          <el-date-picker clearable v-model="form.confirmedTime" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择确认时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="下达时间" prop="releasedTime">
          <el-date-picker clearable v-model="form.releasedTime" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择下达时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="关闭时间" prop="closedTime">
          <el-date-picker clearable v-model="form.closedTime" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择关闭时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="预留字段1" prop="attr1">
          <el-input v-model="form.attr1" placeholder="请输入预留字段1" />
        </el-form-item>
        <el-form-item label="预留字段2" prop="attr2">
          <el-input v-model="form.attr2" placeholder="请输入预留字段2" />
        </el-form-item>
        <el-form-item label="预留字段3" prop="attr3">
          <el-input v-model="form.attr3" placeholder="请输入预留字段3" />
        </el-form-item>
        <el-form-item label="预留字段4" prop="attr4">
          <el-input v-model="form.attr4" placeholder="请输入预留字段4" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>


      </el-form>
    </el-card>
  </div>
</template>

<script setup name="PlanForm">
import { getPlan, addPlan, updatePlan } from "@/api/core/prd/plan"

const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()

const form = ref({})
const isViewMode = ref(false)
const title = ref("")

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
    title.value = "查看生产计划"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'edit') {
    // 编辑模式
    isViewMode.value = false
    title.value = "修改生产计划"
    if (id) {
      getInfo(id)
    }
  } else if (action === 'add') {
    // 新增模式
    isViewMode.value = false
    title.value = "新增生产计划"
    reset()
  }
}

// 获取详细信息
function getInfo(id) {
  getPlan(id).then(response => {
    form.value = response.data

  })
}

// 表单重置
function reset() {
  form.value = {
    planId: null,
    planNo: null,
    planName: null,
    version: null,
    originalPlanId: null,
    referenceNo: null,
    planType: null,
    planDate: null,
    startDate: null,
    endDate: null,
    status: null,
    priority: null,
    plannerId: null,
    plannerName: null,
    departmentId: null,
    departmentName: null,
    totalQuantity: null,
    confirmedTime: null,
    releasedTime: null,
    closedTime: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  proxy.resetForm("planRef")
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["planRef"].validate(valid => {
    if (valid) {

      if (form.value.planId != null) {
        updatePlan(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          router.push('/prd/plan')
        })
      } else {
        addPlan(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          router.push('/prd/plan')
        })
      }
    }
  })
}

// 取消按钮
function cancel() {
  const obj = { path: '/core/prd/plan' }
  proxy.$tab.closeOpenPage(obj)
}

/** 获取字典标签 */
function getDictLabel(dictOptions, value) {
  const dict = dictOptions.find(item => item.value === value)
  return dict ? dict.label : value
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>