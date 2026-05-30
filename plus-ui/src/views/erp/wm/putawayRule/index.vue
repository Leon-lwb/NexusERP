<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="规则编码" prop="ruleCode">
        <el-input v-model="queryParams.ruleCode" placeholder="请输入规则编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="规则名称" prop="ruleName">
        <el-input v-model="queryParams.ruleName" placeholder="请输入规则名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="策略类型" prop="strategyType">
        <el-select v-model="queryParams.strategyType" placeholder="请选择策略类型" clearable>
          <el-option v-for="dict in wm_putaway_strategy_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:putawayRule:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['wm:putawayRule:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['wm:putawayRule:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['wm:putawayRule:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="putawayRuleList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" width="50" align="center" prop="ruleId" />
      <el-table-column label="规则编码" align="center" prop="ruleCode" />
      <el-table-column label="规则名称" align="center" prop="ruleName" />
      <el-table-column label="仓库" align="center" prop="warehouseName" />
      <el-table-column label="物料类别" align="center" prop="categoryName" />
      <el-table-column label="策略类型" align="center" prop="strategyType">
        <template #default="scope">
          <dict-tag :options="wm_putaway_strategy_type" :value="scope.row.strategyType" />
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="priority" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'danger'">{{ scope.row.status === 0 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180px">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['wm:putawayRule:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['wm:putawayRule:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="putawayRuleRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="form.ruleCode" placeholder="请输入规则编码" />
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="form.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="item in warehouseOptions" :key="item.warehouseId" :label="item.warehouseName" :value="item.warehouseId" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料类别" prop="categoryId">
          <el-tree-select v-model="form.categoryId" :data="categoryOptions" :props="{ value: 'id', label: 'label', children: 'children' }"
            placeholder="请选择物料类别" check-strictly style="width: 100%" />
        </el-form-item>
        <el-form-item label="策略类型" prop="strategyType">
          <el-select v-model="form.strategyType" placeholder="请选择策略类型" style="width: 100%">
            <el-option v-for="dict in wm_putaway_strategy_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="form.priority" :min="1" :max="100" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">启用</el-radio>
            <el-radio :value="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :rows="3" />
        </el-form-item>
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

<script setup lang="ts" name="Putawayrule">
import { listPutawayRule, getPutawayRule, delPutawayRule, addPutawayRule, updatePutawayRule } from "@/api/erp/wm/putawayrule"
import { listWarehouse } from "@/api/erp/wm/warehouse"

const { proxy } = getCurrentInstance() as any
const { wm_putaway_strategy_type } = toRefs<any>(proxy.useDict('wm_putaway_strategy_type'))

const putawayRuleList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const warehouseOptions = ref([])
const categoryOptions = ref([])

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    ruleCode: null,
    ruleName: null,
    strategyType: null
  },
  rules: {
    ruleCode: [
      { required: true, message: "规则编码不能为空", trigger: "blur" }
    ],
    ruleName: [
      { required: true, message: "规则名称不能为空", trigger: "blur" }
    ],
    warehouseId: [
      { required: true, message: "仓库不能为空", trigger: "change" }
    ],
    strategyType: [
      { required: true, message: "策略类型不能为空", trigger: "change" }
    ],
    priority: [
      { required: true, message: "优先级不能为空", trigger: "blur" }
    ],
    status: [
      { required: true, message: "状态不能为空", trigger: "change" }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listPutawayRule(queryParams.value).then((response: any) => {
    putawayRuleList.value = response.rows
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
    ruleId: null,
    ruleCode: null,
    ruleName: null,
    warehouseId: null,
    categoryId: null,
    strategyType: null,
    priority: 1,
    status: 0,
    remark: null
  }
  proxy.resetForm("putawayRuleRef")
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
  ids.value = selection.map((item: any) => item.ruleId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "添加上架规则"
  getWarehouseOptions()
}

function handleUpdate(row: any) {
  reset()
  const _ruleId = row.ruleId || ids.value
  getPutawayRule(_ruleId).then((response: any) => {
    form.value = response.data
    open.value = true
    title.value = "修改上架规则"
    getWarehouseOptions()
  })
}

function submitForm() {
  proxy.$refs["putawayRuleRef"].validate((valid: boolean) => {
    if (valid) {
      if (form.value.ruleId != null) {
        updatePutawayRule(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addPutawayRule(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row: any) {
  const _ruleIds = row.ruleId || ids.value
  proxy.$modal.confirm('是否确认删除上架规则编号为"' + _ruleIds + '"的数据项？').then(function () {
    return delPutawayRule(_ruleIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

function handleExport() {
  proxy.download('/erp/wm/putawayrule/export', {
    ...queryParams.value
  }, `putawayrule_${new Date().getTime()}.xlsx`)
}

function getWarehouseOptions() {
  listWarehouse({ pageSize: 100, pageNum: 1 }).then((response: any) => {
    warehouseOptions.value = response.rows
  })
}

getList()
</script>
