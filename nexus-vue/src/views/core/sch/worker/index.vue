<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
                  <el-form-item label="关联的系统用户ID" prop="userId">
                    <el-input
                        v-model="queryParams.userId"
                        placeholder="请输入关联的系统用户ID"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="员工工号" prop="employeeId">
                    <el-input
                        v-model="queryParams.employeeId"
                        placeholder="请输入员工工号"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="工种" prop="workCategory">
                    <el-select v-model="queryParams.workCategory" placeholder="请选择工种" clearable>
                      <el-option
                          v-for="dict in sch_work_category"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="技能等级" prop="skillLevel">
                    <el-select v-model="queryParams.skillLevel" placeholder="请选择技能等级" clearable>
                      <el-option
                          v-for="dict in sch_skill_level"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="在岗状态" prop="workStatus">
                    <el-select v-model="queryParams.workStatus" placeholder="请选择在岗状态" clearable>
                      <el-option
                          v-for="dict in sch_work_status"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="入职日期" prop="entryDate">
                    <el-date-picker clearable
                                    v-model="queryParams.entryDate"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择入职日期">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="转正日期" prop="regularizationDate">
                    <el-date-picker clearable
                                    v-model="queryParams.regularizationDate"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择转正日期">
                    </el-date-picker>
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
            v-hasPermi="['sch:worker:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="single"
            @click="handleUpdate"
            v-hasPermi="['sch:worker:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['sch:worker:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="info"
            plain
            icon="View"
            :disabled="single"
            @click="handleView"
            v-hasPermi="['sch:worker:query']"
        >查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="warning"
            plain
            icon="Download"
            @click="handleExport"
            v-hasPermi="['sch:worker:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="workerList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="工人档案ID" align="center" prop="workerId" />
              <el-table-column label="关联的系统用户ID" align="center" prop="userId" />
              <el-table-column label="员工工号" align="center" prop="employeeId" />
              <el-table-column label="工种" align="center" prop="workCategory">
                <template #default="scope">
                      <dict-tag :options="sch_work_category" :value="scope.row.workCategory"/>
                </template>
              </el-table-column>
              <el-table-column label="技能等级" align="center" prop="skillLevel">
                <template #default="scope">
                      <dict-tag :options="sch_skill_level" :value="scope.row.skillLevel"/>
                </template>
              </el-table-column>
              <el-table-column label="在岗状态" align="center" prop="workStatus">
                <template #default="scope">
                      <dict-tag :options="sch_work_status" :value="scope.row.workStatus"/>
                </template>
              </el-table-column>
              <el-table-column label="入职日期" align="center" prop="entryDate" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.entryDate, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="转正日期" align="center" prop="regularizationDate" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.regularizationDate, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sch:worker:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sch:worker:remove']">删除</el-button>
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

    <!-- 添加或修改生产工人档案对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="workerRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
                        <el-form-item label="关联的系统用户ID" prop="userId">
                          <el-input v-model="form.userId" placeholder="请输入关联的系统用户ID" />
                        </el-form-item>
                        <el-form-item label="员工工号" prop="employeeId">
                          <el-input v-model="form.employeeId" placeholder="请输入员工工号" />
                        </el-form-item>
                        <el-form-item label="工种" prop="workCategory">
                          <el-select v-model="form.workCategory" placeholder="请选择工种">
                            <el-option
                                v-for="dict in sch_work_category"
                                :key="dict.value"
                                :label="dict.label"
                                :value="dict.value"
                            ></el-option>
                          </el-select>
                        </el-form-item>
                        <el-form-item label="技能等级" prop="skillLevel">
                          <el-select v-model="form.skillLevel" placeholder="请选择技能等级">
                            <el-option
                                v-for="dict in sch_skill_level"
                                :key="dict.value"
                                :label="dict.label"
                                :value="dict.value"
                            ></el-option>
                          </el-select>
                        </el-form-item>
                        <el-form-item label="在岗状态" prop="workStatus">
                          <el-select v-model="form.workStatus" placeholder="请选择在岗状态">
                            <el-option
                                v-for="dict in sch_work_status"
                                :key="dict.value"
                                :label="dict.label"
                                :value="dict.value"
                            ></el-option>
                          </el-select>
                        </el-form-item>
                        <el-form-item label="入职日期" prop="entryDate">
                          <el-date-picker clearable
                                          v-model="form.entryDate"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择入职日期">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="转正日期" prop="regularizationDate">
                          <el-date-picker clearable
                                          v-model="form.regularizationDate"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择转正日期">
                          </el-date-picker>
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

<script setup name="Worker">
  import { listWorker, getWorker, delWorker, addWorker, updateWorker } from "@/api/core/sch/worker"

  const { proxy } = getCurrentInstance()
      const { sch_skill_level, sch_work_category, sch_work_status } = proxy.useDict('sch_skill_level', 'sch_work_category', 'sch_work_status')

  const workerList = ref([])
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
                    userId: null,
                    employeeId: null,
                    workCategory: null,
                    skillLevel: null,
                    workStatus: null,
                    entryDate: null,
                    regularizationDate: null,
    },
    rules: {
                    userId: [
                { required: true, message: "关联的系统用户ID不能为空", trigger: "blur" }
              ],
                    employeeId: [
                { required: true, message: "员工工号不能为空", trigger: "blur" }
              ],
    }
  })

  const { queryParams, form, rules } = toRefs(data)

  /** 查询生产工人档案列表 */
  function getList() {
    loading.value = true
    listWorker(queryParams.value).then(response => {
            workerList.value = response.rows
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
                    workerId: null,
                    userId: null,
                    employeeId: null,
                    workCategory: null,
                    skillLevel: null,
                    workStatus: null,
                    entryDate: null,
                    regularizationDate: null,
                    createTime: null,
                    updateTime: null
    }
    proxy.resetForm("workerRef")
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
    ids.value = selection.map(item => item.workerId)
    single.value = selection.length != 1
    multiple.value = !selection.length
  }

  /** 新增按钮操作 */
  function handleAdd() {
    reset()
    isViewMode.value = false // 设置为编辑模式
    open.value = true
    title.value = "添加生产工人档案"
  }

  /** 修改按钮操作 */
  function handleUpdate(row) {
    reset()
    isViewMode.value = false // 设置为编辑模式
    const _workerId = row.workerId || ids.value
    getWorker(_workerId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "修改生产工人档案"
    })
  }

  /** 查看按钮操作 */
  function handleView(row) {
    reset()
    isViewMode.value = true // 设置为查看模式
    const _workerId = row.workerId || ids.value
    getWorker(_workerId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "查看生产工人档案"
    })
  }

  /** 提交按钮 */
  function submitForm() {
    proxy.$refs["workerRef"].validate(valid => {
      if (valid) {
        if (form.value.workerId != null) {
          updateWorker(form.value).then(response => {
            proxy.$modal.msgSuccess("修改成功")
            open.value = false
            getList()
          })
        } else {
          addWorker(form.value).then(response => {
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
    const _workerIds = row.workerId || ids.value
    proxy.$modal.confirm('是否确认删除生产工人档案编号为"' + _workerIds + '"的数据项？').then(function() {
      return delWorker(_workerIds)
    }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    }).catch(() => {})
  }

  /** 导出按钮操作 */
  function handleExport() {
    proxy.download('sch/worker/export', {
      ...queryParams.value
    }, `worker_${new Date().getTime()}.xlsx`)
  }

  getList()
</script>