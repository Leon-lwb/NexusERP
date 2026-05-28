<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="规则编码" prop="ruleCode">
              <el-input v-model="form.ruleCode" placeholder="请输入规则编码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="策略类型" prop="strategyType">
              <el-select v-model="form.strategyType" placeholder="请选择策略类型" style="width: 100%">
                <el-option v-for="dict in wm_putaway_strategy_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="请选择仓库" clearable style="width: 100%">
                <el-option v-for="item in warehouseOptions" :key="item.warehouseId" :label="item.warehouseName" :value="item.warehouseId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="物料类别" prop="categoryId">
              <el-tree-select v-model="form.categoryId" :data="categoryOptions"
                :props="{ value: 'id', label: 'label', children: 'children' }"
                placeholder="请选择物料类别" check-strictly style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="form.priority" :min="1" :max="999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="0">启用</el-radio>
                <el-radio :value="1">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="PutawayRuleForm">
import { getPutawayRule, addPutawayRule, updatePutawayRule } from '@/api/erp/wm/putawayrule'
import { listWarehouse } from '@/api/erp/wm/warehouse'
import { useRoute, useRouter } from 'vue-router'

const { proxy } = getCurrentInstance() as any
const { wm_putaway_strategy_type } = toRefs<any>(proxy.useDict('wm_putaway_strategy_type'))

const route = useRoute()
const router = useRouter()

const title = ref('')
const isViewMode = ref(false)
const warehouseOptions = ref<any[]>([])
const categoryOptions = ref<any[]>([])

const form = ref<any>({
  ruleId: null,
  ruleCode: null,
  ruleName: null,
  warehouseId: null,
  categoryId: null,
  strategyType: null,
  priority: 100,
  status: 0,
  remark: null
})

const rules = {
  ruleCode: [{ required: true, message: '规则编码不能为空', trigger: 'blur' }],
  ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
  strategyType: [{ required: true, message: '策略类型不能为空', trigger: 'change' }],
  priority: [{ required: true, message: '优先级不能为空', trigger: 'blur' }]
}

onMounted(() => {
  const id = route.query.id as any
  const action = route.query.action as string

  isViewMode.value = action === 'view'
  title.value = action === 'add' ? '新增上架规则' : action === 'edit' ? '修改上架规则' : '查看上架规则'

  loadWarehouseOptions()

  if (action === 'add') {
    reset()
  } else if (id) {
    getInfo(id)
  }
})

function loadWarehouseOptions() {
  listWarehouse({ pageSize: 100, pageNum: 1 }).then((res: any) => {
    warehouseOptions.value = res.rows || []
  })
}

function getInfo(id: any) {
  getPutawayRule(id).then((res: any) => {
    form.value = res.data
  })
}

function reset() {
  form.value = {
    ruleId: null,
    ruleCode: null,
    ruleName: null,
    warehouseId: null,
    categoryId: null,
    strategyType: null,
    priority: 100,
    status: 0,
    remark: null
  }
}

function submitForm() {
  const formRef = proxy.$refs['formRef']
  formRef.validate((valid: boolean) => {
    if (valid) {
      const isEdit = route.query.action === 'edit' || form.value.ruleId != null
      ;(isEdit ? updatePutawayRule(form.value) : addPutawayRule(form.value)).then(() => {
        proxy.$modal.msgSuccess('保存成功')
        router.push('/erp/wm/putawayRule')
      })
    }
  })
}

function cancel() {
  router.push('/erp/wm/putawayRule')
}
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
:deep(.el-divider__text) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #606266;
}
:deep(.el-form-item) {
  margin-bottom: 18px;
}
</style>
