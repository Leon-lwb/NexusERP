<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
                  <el-form-item label="排班计划ID" prop="planId">
                    <el-input
                        v-model="queryParams.planId"
                        placeholder="请输入排班计划ID"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="班组ID" prop="teamId">
                    <el-input
                        v-model="queryParams.teamId"
                        placeholder="请输入班组ID"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="班组名称" prop="teamName">
                    <el-input
                        v-model="queryParams.teamName"
                        placeholder="请输入班组名称"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
            type="primary"
            plain
            icon="Plus"
            @click="handleAdd"
            v-hasPermi="['sch:planteam:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="single"
            @click="handleUpdate"
            v-hasPermi="['sch:planteam:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['sch:planteam:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="info"
            plain
            icon="View"
            :disabled="single"
            @click="handleView"
            v-hasPermi="['sch:planteam:query']"
        >查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="warning"
            plain
            icon="Download"
            @click="handleExport"
            v-hasPermi="['sch:planteam:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="planteamList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="记录ID" align="center" prop="recordId" />
              <el-table-column label="排班计划ID" align="center" prop="planId" />
              <el-table-column label="班组ID" align="center" prop="teamId" />
              <el-table-column label="班组名称" align="center" prop="teamName" />
              <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sch:planteam:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sch:planteam:remove']">删除</el-button>
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

    <!-- 添加或修改计划班组关联对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="planteamRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
                        <el-form-item label="排班计划ID" prop="planId">
                          <el-input v-model="form.planId" placeholder="请输入排班计划ID" />
                        </el-form-item>
                        <el-form-item label="班组ID" prop="teamId">
                          <el-input v-model="form.teamId" placeholder="请输入班组ID" />
                        </el-form-item>
                        <el-form-item label="班组名称" prop="teamName">
                          <el-input v-model="form.teamName" placeholder="请输入班组名称" />
                        </el-form-item>
                        <el-form-item label="备注" prop="remark">
                          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
                        </el-form-item>
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

<script setup name="Planteam">
  import { listPlanteam, getPlanteam, delPlanteam, addPlanteam, updatePlanteam } from "@/api/core/sch/planteam"

  const { proxy } = getCurrentInstance()

  const planteamList = ref([])
  const open = ref(false)
  const loading = ref(true)
  const showSearch = ref(true)
  const ids = ref([])
  const single = ref(true)
  const multiple = ref(true)
  const total = ref(0)
  const title = ref("")
  const isViewMode = ref(false) // 新增：标识是否为查看模式

  const data = reactive({
    form: {},
    queryParams: {
      pageNum: 1,
      pageSize: 10,
                    planId: null,
                    teamId: null,
                    teamName: null,
    },
    rules: {
                    planId: [
                { required: true, message: "排班计划ID不能为空", trigger: "blur" }
              ],
                    teamId: [
                { required: true, message: "班组ID不能为空", trigger: "blur" }
              ],
    }
  })

  const { queryParams, form, rules } = toRefs(data)

  /** 查询计划班组关联列表 */
  function getList() {
    loading.value = true
    listPlanteam(queryParams.value).then(response => {
            planteamList.value = response.rows
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
                    recordId: null,
                    planId: null,
                    teamId: null,
                    teamName: null,
                    remark: null,
                    createBy: null,
                    createTime: null,
                    updateBy: null,
                    updateTime: null
    }
    proxy.resetForm("planteamRef")
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
    ids.value = selection.map(item => item.recordId)
    single.value = selection.length != 1
    multiple.value = !selection.length
  }

  /** 新增按钮操作 */
  function handleAdd() {
    reset()
    isViewMode.value = false // 设置为编辑模式
    open.value = true
    title.value = "添加计划班组关联"
  }

  /** 修改按钮操作 */
  function handleUpdate(row) {
    reset()
    isViewMode.value = false // 设置为编辑模式
    const _recordId = row.recordId || ids.value
    getPlanteam(_recordId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "修改计划班组关联"
    })
  }

  /** 查看按钮操作 */
  function handleView(row) {
    reset()
    isViewMode.value = true // 设置为查看模式
    const _recordId = row.recordId || ids.value
    getPlanteam(_recordId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "查看计划班组关联"
    })
  }

  /** 提交按钮 */
  function submitForm() {
    proxy.$refs["planteamRef"].validate(valid => {
      if (valid) {
        if (form.value.recordId != null) {
          updatePlanteam(form.value).then(response => {
            proxy.$modal.msgSuccess("修改成功")
            open.value = false
            getList()
          })
        } else {
          addPlanteam(form.value).then(response => {
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
    const _recordIds = row.recordId || ids.value
    proxy.$modal.confirm('是否确认删除计划班组关联编号为"' + _recordIds + '"的数据项？').then(function() {
      return delPlanteam(_recordIds)
    }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    }).catch(() => {})
  }

  /** 导出按钮操作 */
  function handleExport() {
    proxy.download('sch/planteam/export', {
      ...queryParams.value
    }, `planteam_${new Date().getTime()}.xlsx`)
  }

  getList()
</script>