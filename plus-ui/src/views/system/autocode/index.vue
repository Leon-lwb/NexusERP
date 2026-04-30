<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryFormRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="规则编码" prop="ruleCode">
        <el-input
          v-model="queryParams.ruleCode"
          placeholder="请输入规则编码"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="规则名称" prop="ruleName">
        <el-input
          v-model="queryParams.ruleName"
          placeholder="请输入规则名称"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="启用状态" prop="enableFlag">
        <el-select
          v-model="queryParams.enableFlag"
          placeholder="启用状态"
          clearable
          style="width: 240px"
        >
          <el-option
            v-for="dict in sys_yes_no"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" size="small" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" size="small" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          size="small"
          @click="handleAdd"
          v-hasPermi="['system:autocode:rule:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          size="small"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['system:autocode:rule:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          size="small"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['system:autocode:rule:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="ruleList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="规则编码" align="center" width="210px" prop="ruleCode" >
        <template #default="scope">
          <router-link :to="'/system/autocodePart/index/' + scope.row.ruleId" class="link-type">
            <span>{{ scope.row.ruleCode }}</span>
          </router-link>
        </template>
      </el-table-column>
      <el-table-column label="规则名称" align="center" prop="ruleName" :show-overflow-tooltip="true" />
      <el-table-column label="最大长度" align="center" prop="maxLength" />
      <el-table-column label="是否补齐" align="center" prop="isPadded" />
      <el-table-column label="是否可用" align="center" prop="enableFlag">
        <template #default="scope">
          <dict-tag :options="sys_yes_no" :value="scope.row.enableFlag"/>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ proxy.parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="修改" placement="top">
            <el-button
              link
              type="primary"
              icon="Edit"
              @click="handleUpdate(scope.row)"
              v-hasPermi="['system:autocode:rule:edit']"
            ></el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button
              link
              type="primary"
              icon="Delete"
              @click="handleDelete(scope.row)"
              v-hasPermi="['system:autocode:rule:remove']"
            ></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog v-model="open" :title="title" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="form.ruleCode" placeholder="请输入规则编码" />
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="描述" prop="ruleDesc">
          <el-input v-model="form.ruleDesc" placeholder="请输入描述信息" />
        </el-form-item>
        <el-form-item label="最大长度" prop="maxLength">
          <el-input-number v-model="form.maxLength" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="是否补齐" prop="isPadded">
          <el-radio-group v-model="form.isPadded">
            <el-radio label="Y">是</el-radio>
            <el-radio label="N">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="补齐字符" prop="paddedChar" v-if="form.isPadded == 'Y'">
          <el-input v-model="form.paddedChar" placeholder="请输入补齐字符" />
        </el-form-item>
        <el-form-item label="补齐方式" prop="paddedMethod" v-if="form.isPadded == 'Y'">
          <el-radio-group v-model="form.paddedMethod">
            <el-radio label="L">左补齐</el-radio>
            <el-radio label="R">右补齐</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否启用" prop="enableFlag">
          <el-radio-group v-model="form.enableFlag">
            <el-radio
              v-for="dict in sys_yes_no"
              :key="dict.value"
              :label="dict.value"
            >{{dict.label}}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"></el-input>
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

<script setup name="autoCodeRule">
import { ref, reactive, onMounted, toRefs, getCurrentInstance } from 'vue'
import { listRule, getRule, delRule, addRule, updateRule } from "@/api/system/autocode/rule"

const { proxy } = getCurrentInstance()
const { sys_yes_no } = proxy.useDict("sys_yes_no")

const ruleList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const open = ref(false)
const dateRange = ref([])
const queryFormRef = ref()
const formRef = ref()

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    ruleCode: undefined,
    ruleName: undefined,
    enableFlag: 'Y'
  },
  rules: {
    ruleCode: [
      { required: true, message: "规则编码不能为空", trigger: "blur" }
    ],
    ruleName: [
      { required: true, message: "规则名称不能为空", trigger: "blur" }
    ],
    remark: [
      { max: 250, message: '长度必须小于250个字符', trigger: 'blur' }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listRule(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    ruleList.value = response.rows
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
    ruleId: undefined,
    ruleCode: undefined,
    ruleName: undefined,
    ruleDesc: undefined,
    maxLength: undefined,
    isPadded: 'N',
    enableFlag: "Y",
    remark: undefined
  }
  proxy.resetForm("formRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  dateRange.value = []
  proxy.resetForm("queryFormRef")
  handleQuery()
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "添加编码规则"
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.ruleId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleUpdate(row) {
  reset()
  const ruleId = row.ruleId || ids.value
  getRule(ruleId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改编码规则"
  })
}

function submitForm() {
  proxy.$refs["formRef"].validate(valid => {
    if (valid) {
      if (form.value.ruleId != undefined) {
        updateRule(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addRule(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row) {
  const ruleIds = row.ruleId || ids.value
  proxy.$modal.confirm('是否确认删除编码规则为"' + ruleIds + '"的数据项？').then(function() {
    return delRule(ruleIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

onMounted(() => {
  getList()
})
</script>
