<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
                  <el-form-item label="班次编码" prop="shiftCode">
                    <el-input
                        v-model="queryParams.shiftCode"
                        placeholder="请输入班次编码"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="班次名称" prop="shiftName">
                    <el-input
                        v-model="queryParams.shiftName"
                        placeholder="请输入班次名称"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="排班计划ID" prop="planId">
                    <el-input
                        v-model="queryParams.planId"
                        placeholder="请输入排班计划ID"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="开始时间" prop="startTime">
                    <el-date-picker clearable
                                    v-model="queryParams.startTime"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择开始时间">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="结束时间" prop="endTime">
                    <el-date-picker clearable
                                    v-model="queryParams.endTime"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择结束时间">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="工时(小时)" prop="workHours">
                    <el-input
                        v-model="queryParams.workHours"
                        placeholder="请输入工时(小时)"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="是否跨天(0否1是)" prop="isCrossDay">
                    <el-select v-model="queryParams.isCrossDay" placeholder="请选择是否跨天(0否1是)" clearable>
                      <el-option
                          v-for="dict in sys_true_or_false"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="休息开始时间" prop="restStartTime">
                    <el-date-picker clearable
                                    v-model="queryParams.restStartTime"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择休息开始时间">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="休息结束时间" prop="restEndTime">
                    <el-date-picker clearable
                                    v-model="queryParams.restEndTime"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择休息结束时间">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="迟到阈值(分钟)" prop="lateThreshold">
                    <el-input
                        v-model="queryParams.lateThreshold"
                        placeholder="请输入迟到阈值(分钟)"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="早退阈值(分钟)" prop="earlyLeaveThreshold">
                    <el-input
                        v-model="queryParams.earlyLeaveThreshold"
                        placeholder="请输入早退阈值(分钟)"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="班次类别" prop="shiftCategory">
                    <el-select v-model="queryParams.shiftCategory" placeholder="请选择班次类别" clearable>
                      <el-option
                          v-for="dict in sch_shift_category"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="班次显示颜色" prop="color">
                    <el-input
                        v-model="queryParams.color"
                        placeholder="请输入班次显示颜色"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="排序" prop="sortOrder">
                    <el-input
                        v-model="queryParams.sortOrder"
                        placeholder="请输入排序"
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
            v-hasPermi="['sch:shift:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="single"
            @click="handleUpdate"
            v-hasPermi="['sch:shift:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['sch:shift:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="info"
            plain
            icon="View"
            :disabled="single"
            @click="handleView"
            v-hasPermi="['sch:shift:query']"
        >查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="warning"
            plain
            icon="Download"
            @click="handleExport"
            v-hasPermi="['sch:shift:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="shiftList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="班次ID" align="center" prop="shiftId" />
              <el-table-column label="班次编码" align="center" prop="shiftCode" />
              <el-table-column label="班次名称" align="center" prop="shiftName" />
              <el-table-column label="排班计划ID" align="center" prop="planId" />
              <el-table-column label="开始时间" align="center" prop="startTime" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.startTime, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="结束时间" align="center" prop="endTime" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.endTime, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="工时(小时)" align="center" prop="workHours" />
              <el-table-column label="是否跨天(0否1是)" align="center" prop="isCrossDay">
                <template #default="scope">
                      <dict-tag :options="sys_true_or_false" :value="scope.row.isCrossDay"/>
                </template>
              </el-table-column>
              <el-table-column label="休息开始时间" align="center" prop="restStartTime" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.restStartTime, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="休息结束时间" align="center" prop="restEndTime" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.restEndTime, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="迟到阈值(分钟)" align="center" prop="lateThreshold" />
              <el-table-column label="早退阈值(分钟)" align="center" prop="earlyLeaveThreshold" />
              <el-table-column label="班次类别" align="center" prop="shiftCategory">
                <template #default="scope">
                      <dict-tag :options="sch_shift_category" :value="scope.row.shiftCategory"/>
                </template>
              </el-table-column>
              <el-table-column label="班次显示颜色" align="center" prop="color" />
              <el-table-column label="排序" align="center" prop="sortOrder" />
              <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sch:shift:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sch:shift:remove']">删除</el-button>
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

    <!-- 添加或修改班次定义对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="shiftRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
                        <el-form-item label="班次编码" prop="shiftCode">
                          <el-input v-model="form.shiftCode" placeholder="请输入班次编码" />
                        </el-form-item>
                        <el-form-item label="班次名称" prop="shiftName">
                          <el-input v-model="form.shiftName" placeholder="请输入班次名称" />
                        </el-form-item>
                        <el-form-item label="排班计划ID" prop="planId">
                          <el-input v-model="form.planId" placeholder="请输入排班计划ID" />
                        </el-form-item>
                        <el-form-item label="开始时间" prop="startTime">
                          <el-date-picker clearable
                                          v-model="form.startTime"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择开始时间">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="结束时间" prop="endTime">
                          <el-date-picker clearable
                                          v-model="form.endTime"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择结束时间">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="工时(小时)" prop="workHours">
                          <el-input v-model="form.workHours" placeholder="请输入工时(小时)" />
                        </el-form-item>
                        <el-form-item label="是否跨天(0否1是)" prop="isCrossDay">
                          <el-radio-group v-model="form.isCrossDay">
                            <el-radio
                                v-for="dict in sys_true_or_false"
                                :key="dict.value"
                                :label="parseInt(dict.value)"
                            >{{dict.label}}</el-radio>
                          </el-radio-group>
                        </el-form-item>
                        <el-form-item label="休息开始时间" prop="restStartTime">
                          <el-date-picker clearable
                                          v-model="form.restStartTime"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择休息开始时间">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="休息结束时间" prop="restEndTime">
                          <el-date-picker clearable
                                          v-model="form.restEndTime"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择休息结束时间">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="迟到阈值(分钟)" prop="lateThreshold">
                          <el-input v-model="form.lateThreshold" placeholder="请输入迟到阈值(分钟)" />
                        </el-form-item>
                        <el-form-item label="早退阈值(分钟)" prop="earlyLeaveThreshold">
                          <el-input v-model="form.earlyLeaveThreshold" placeholder="请输入早退阈值(分钟)" />
                        </el-form-item>
                        <el-form-item label="班次类别" prop="shiftCategory">
                          <el-select v-model="form.shiftCategory" placeholder="请选择班次类别">
                            <el-option
                                v-for="dict in sch_shift_category"
                                :key="dict.value"
                                :label="dict.label"
                                :value="dict.value"
                            ></el-option>
                          </el-select>
                        </el-form-item>
                        <el-form-item label="班次显示颜色" prop="color">
                          <el-input v-model="form.color" placeholder="请输入班次显示颜色" />
                        </el-form-item>
                        <el-form-item label="排序" prop="sortOrder">
                          <el-input v-model="form.sortOrder" placeholder="请输入排序" />
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

<script setup name="Shift">
  import { listShift, getShift, delShift, addShift, updateShift } from "@/api/core/sch/shift"

  const { proxy } = getCurrentInstance()
      const { sys_true_or_false, sch_shift_category } = proxy.useDict('sys_true_or_false', 'sch_shift_category')

  const shiftList = ref([])
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
                    shiftCode: null,
                    shiftName: null,
                    planId: null,
                    startTime: null,
                    endTime: null,
                    workHours: null,
                    isCrossDay: null,
                    restStartTime: null,
                    restEndTime: null,
                    lateThreshold: null,
                    earlyLeaveThreshold: null,
                    shiftCategory: null,
                    color: null,
                    sortOrder: null,
    },
    rules: {
                    shiftCode: [
                { required: true, message: "班次编码不能为空", trigger: "blur" }
              ],
                    shiftName: [
                { required: true, message: "班次名称不能为空", trigger: "blur" }
              ],
                    planId: [
                { required: true, message: "排班计划ID不能为空", trigger: "blur" }
              ],
                    startTime: [
                { required: true, message: "开始时间不能为空", trigger: "blur" }
              ],
                    endTime: [
                { required: true, message: "结束时间不能为空", trigger: "blur" }
              ],
                    workHours: [
                { required: true, message: "工时(小时)不能为空", trigger: "blur" }
              ],
    }
  })

  const { queryParams, form, rules } = toRefs(data)

  /** 查询班次定义列表 */
  function getList() {
    loading.value = true
    listShift(queryParams.value).then(response => {
            shiftList.value = response.rows
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
                    shiftId: null,
                    shiftCode: null,
                    shiftName: null,
                    planId: null,
                    startTime: null,
                    endTime: null,
                    workHours: null,
                    isCrossDay: null,
                    restStartTime: null,
                    restEndTime: null,
                    lateThreshold: null,
                    earlyLeaveThreshold: null,
                    shiftCategory: null,
                    color: null,
                    sortOrder: null,
                    remark: null,
                    createBy: null,
                    createTime: null,
                    updateBy: null,
                    updateTime: null
    }
    proxy.resetForm("shiftRef")
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
    ids.value = selection.map(item => item.shiftId)
    single.value = selection.length != 1
    multiple.value = !selection.length
  }

  /** 新增按钮操作 */
  function handleAdd() {
    reset()
    isViewMode.value = false // 设置为编辑模式
    open.value = true
    title.value = "添加班次定义"
  }

  /** 修改按钮操作 */
  function handleUpdate(row) {
    reset()
    isViewMode.value = false // 设置为编辑模式
    const _shiftId = row.shiftId || ids.value
    getShift(_shiftId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "修改班次定义"
    })
  }

  /** 查看按钮操作 */
  function handleView(row) {
    reset()
    isViewMode.value = true // 设置为查看模式
    const _shiftId = row.shiftId || ids.value
    getShift(_shiftId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "查看班次定义"
    })
  }

  /** 提交按钮 */
  function submitForm() {
    proxy.$refs["shiftRef"].validate(valid => {
      if (valid) {
        if (form.value.shiftId != null) {
          updateShift(form.value).then(response => {
            proxy.$modal.msgSuccess("修改成功")
            open.value = false
            getList()
          })
        } else {
          addShift(form.value).then(response => {
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
    const _shiftIds = row.shiftId || ids.value
    proxy.$modal.confirm('是否确认删除班次定义编号为"' + _shiftIds + '"的数据项？').then(function() {
      return delShift(_shiftIds)
    }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    }).catch(() => {})
  }

  /** 导出按钮操作 */
  function handleExport() {
    proxy.download('sch/shift/export', {
      ...queryParams.value
    }, `shift_${new Date().getTime()}.xlsx`)
  }

  getList()
</script>