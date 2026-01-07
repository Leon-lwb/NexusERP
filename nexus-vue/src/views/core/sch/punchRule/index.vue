<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
                  <el-form-item label="规则名称" prop="ruleName">
                    <el-input
                        v-model="queryParams.ruleName"
                        placeholder="请输入规则名称"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="适用班次ID" prop="shiftId">
                    <el-input
                        v-model="queryParams.shiftId"
                        placeholder="请输入适用班次ID"
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
                  <el-form-item label="上班时间" prop="workStartTime">
                    <el-date-picker clearable
                                    v-model="queryParams.workStartTime"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择上班时间">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="下班时间" prop="workEndTime">
                    <el-date-picker clearable
                                    v-model="queryParams.workEndTime"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择下班时间">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="允许上班打卡开始时间" prop="allowClockInStart">
                    <el-date-picker clearable
                                    v-model="queryParams.allowClockInStart"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择允许上班打卡开始时间">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="允许上班打卡结束时间" prop="allowClockInEnd">
                    <el-date-picker clearable
                                    v-model="queryParams.allowClockInEnd"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择允许上班打卡结束时间">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="允许下班打卡开始时间" prop="allowClockOutStart">
                    <el-date-picker clearable
                                    v-model="queryParams.allowClockOutStart"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择允许下班打卡开始时间">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="允许下班打卡结束时间" prop="allowClockOutEnd">
                    <el-date-picker clearable
                                    v-model="queryParams.allowClockOutEnd"
                                    type="date"
                                    value-format="YYYY-MM-DD"
                                    placeholder="请选择允许下班打卡结束时间">
                    </el-date-picker>
                  </el-form-item>
                  <el-form-item label="打卡中心点纬度" prop="locationLat">
                    <el-input
                        v-model="queryParams.locationLat"
                        placeholder="请输入打卡中心点纬度"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="打卡中心点经度" prop="locationLng">
                    <el-input
                        v-model="queryParams.locationLng"
                        placeholder="请输入打卡中心点经度"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="打卡半径" prop="locationRadius">
                    <el-input
                        v-model="queryParams.locationRadius"
                        placeholder="请输入打卡半径"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="是否允许外勤打卡" prop="allowOutdoorPunch">
                    <el-select v-model="queryParams.allowOutdoorPunch" placeholder="请选择是否允许外勤打卡" clearable>
                      <el-option
                          v-for="dict in sys_true_or_false"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="外勤打卡是否要求拍照" prop="outdoorRequirePhoto">
                    <el-select v-model="queryParams.outdoorRequirePhoto" placeholder="请选择外勤打卡是否要求拍照" clearable>
                      <el-option
                          v-for="dict in sys_true_or_false"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="是否允许补卡" prop="allowSupplement">
                    <el-select v-model="queryParams.allowSupplement" placeholder="请选择是否允许补卡" clearable>
                      <el-option
                          v-for="dict in sys_true_or_false"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="补卡申请截止天数" prop="supplementDeadline">
                    <el-input
                        v-model="queryParams.supplementDeadline"
                        placeholder="请输入补卡申请截止天数"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="迟到阈值" prop="lateThreshold">
                    <el-input
                        v-model="queryParams.lateThreshold"
                        placeholder="请输入迟到阈值"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="早退阈值" prop="earlyLeaveThreshold">
                    <el-input
                        v-model="queryParams.earlyLeaveThreshold"
                        placeholder="请输入早退阈值"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="旷工阈值" prop="absentThreshold">
                    <el-input
                        v-model="queryParams.absentThreshold"
                        placeholder="请输入旷工阈值"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="是否启用" prop="isEnabled">
                    <el-select v-model="queryParams.isEnabled" placeholder="请选择是否启用" clearable>
                      <el-option
                          v-for="dict in sys_true_or_false"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
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
            v-hasPermi="['sch:punchRule:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="single"
            @click="handleUpdate"
            v-hasPermi="['sch:punchRule:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['sch:punchRule:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="info"
            plain
            icon="View"
            :disabled="single"
            @click="handleView"
            v-hasPermi="['sch:punchRule:query']"
        >查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="warning"
            plain
            icon="Download"
            @click="handleExport"
            v-hasPermi="['sch:punchRule:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="punchRuleList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="规则ID" align="center" prop="ruleId" />
              <el-table-column label="规则名称" align="center" prop="ruleName" />
              <el-table-column label="适用班次ID" align="center" prop="shiftId" />
              <el-table-column label="班次名称" align="center" prop="shiftName" />
              <el-table-column label="上班时间" align="center" prop="workStartTime" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.workStartTime, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="下班时间" align="center" prop="workEndTime" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.workEndTime, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="允许上班打卡开始时间" align="center" prop="allowClockInStart" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.allowClockInStart, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="允许上班打卡结束时间" align="center" prop="allowClockInEnd" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.allowClockInEnd, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="允许下班打卡开始时间" align="center" prop="allowClockOutStart" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.allowClockOutStart, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="允许下班打卡结束时间" align="center" prop="allowClockOutEnd" width="180">
                <template #default="scope">
                  <span>{{ parseTime(scope.row.allowClockOutEnd, '{y}-{m}-{d}') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="打卡中心点纬度" align="center" prop="locationLat" />
              <el-table-column label="打卡中心点经度" align="center" prop="locationLng" />
              <el-table-column label="打卡半径" align="center" prop="locationRadius" />
              <el-table-column label="打卡地点描述" align="center" prop="locationAddress" />
              <el-table-column label="允许的Wi-Fi网络SSID列表" align="center" prop="allowWifiSsids" />
              <el-table-column label="允许的蓝牙设备列表" align="center" prop="allowBluetoothDevices" />
              <el-table-column label="是否允许外勤打卡" align="center" prop="allowOutdoorPunch">
                <template #default="scope">
                      <dict-tag :options="sys_true_or_false" :value="scope.row.allowOutdoorPunch"/>
                </template>
              </el-table-column>
              <el-table-column label="外勤打卡是否要求拍照" align="center" prop="outdoorRequirePhoto">
                <template #default="scope">
                      <dict-tag :options="sys_true_or_false" :value="scope.row.outdoorRequirePhoto"/>
                </template>
              </el-table-column>
              <el-table-column label="是否允许补卡" align="center" prop="allowSupplement">
                <template #default="scope">
                      <dict-tag :options="sys_true_or_false" :value="scope.row.allowSupplement"/>
                </template>
              </el-table-column>
              <el-table-column label="补卡申请截止天数" align="center" prop="supplementDeadline" />
              <el-table-column label="迟到阈值" align="center" prop="lateThreshold" />
              <el-table-column label="早退阈值" align="center" prop="earlyLeaveThreshold" />
              <el-table-column label="旷工阈值" align="center" prop="absentThreshold" />
              <el-table-column label="是否启用" align="center" prop="isEnabled">
                <template #default="scope">
                      <dict-tag :options="sys_true_or_false" :value="scope.row.isEnabled"/>
                </template>
              </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['sch:punchRule:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['sch:punchRule:remove']">删除</el-button>
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

    <!-- 添加或修改打卡规则对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="punchRuleRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
                        <el-form-item label="规则名称" prop="ruleName">
                          <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
                        </el-form-item>
                        <el-form-item label="适用班次ID" prop="shiftId">
                          <el-input v-model="form.shiftId" placeholder="请输入适用班次ID" />
                        </el-form-item>
                        <el-form-item label="班次名称" prop="shiftName">
                          <el-input v-model="form.shiftName" placeholder="请输入班次名称" />
                        </el-form-item>
                        <el-form-item label="上班时间" prop="workStartTime">
                          <el-date-picker clearable
                                          v-model="form.workStartTime"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择上班时间">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="下班时间" prop="workEndTime">
                          <el-date-picker clearable
                                          v-model="form.workEndTime"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择下班时间">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="允许上班打卡开始时间" prop="allowClockInStart">
                          <el-date-picker clearable
                                          v-model="form.allowClockInStart"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择允许上班打卡开始时间">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="允许上班打卡结束时间" prop="allowClockInEnd">
                          <el-date-picker clearable
                                          v-model="form.allowClockInEnd"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择允许上班打卡结束时间">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="允许下班打卡开始时间" prop="allowClockOutStart">
                          <el-date-picker clearable
                                          v-model="form.allowClockOutStart"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择允许下班打卡开始时间">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="允许下班打卡结束时间" prop="allowClockOutEnd">
                          <el-date-picker clearable
                                          v-model="form.allowClockOutEnd"
                                          type="date"
                                          value-format="YYYY-MM-DD"
                                          placeholder="请选择允许下班打卡结束时间">
                          </el-date-picker>
                        </el-form-item>
                        <el-form-item label="打卡中心点纬度" prop="locationLat">
                          <el-input v-model="form.locationLat" placeholder="请输入打卡中心点纬度" />
                        </el-form-item>
                        <el-form-item label="打卡中心点经度" prop="locationLng">
                          <el-input v-model="form.locationLng" placeholder="请输入打卡中心点经度" />
                        </el-form-item>
                        <el-form-item label="打卡半径" prop="locationRadius">
                          <el-input v-model="form.locationRadius" placeholder="请输入打卡半径" />
                        </el-form-item>
                        <el-form-item label="打卡地点描述" prop="locationAddress">
                          <el-input v-model="form.locationAddress" type="textarea" placeholder="请输入内容" />
                        </el-form-item>
                        <el-form-item label="允许的Wi-Fi网络SSID列表" prop="allowWifiSsids">
                          <el-input v-model="form.allowWifiSsids" type="textarea" placeholder="请输入内容" />
                        </el-form-item>
                        <el-form-item label="允许的蓝牙设备列表" prop="allowBluetoothDevices">
                          <el-input v-model="form.allowBluetoothDevices" type="textarea" placeholder="请输入内容" />
                        </el-form-item>
                        <el-form-item label="是否允许外勤打卡" prop="allowOutdoorPunch">
                          <el-radio-group v-model="form.allowOutdoorPunch">
                            <el-radio
                                v-for="dict in sys_true_or_false"
                                :key="dict.value"
                                :label="parseInt(dict.value)"
                            >{{dict.label}}</el-radio>
                          </el-radio-group>
                        </el-form-item>
                        <el-form-item label="外勤打卡是否要求拍照" prop="outdoorRequirePhoto">
                          <el-radio-group v-model="form.outdoorRequirePhoto">
                            <el-radio
                                v-for="dict in sys_true_or_false"
                                :key="dict.value"
                                :label="parseInt(dict.value)"
                            >{{dict.label}}</el-radio>
                          </el-radio-group>
                        </el-form-item>
                        <el-form-item label="是否允许补卡" prop="allowSupplement">
                          <el-radio-group v-model="form.allowSupplement">
                            <el-radio
                                v-for="dict in sys_true_or_false"
                                :key="dict.value"
                                :label="parseInt(dict.value)"
                            >{{dict.label}}</el-radio>
                          </el-radio-group>
                        </el-form-item>
                        <el-form-item label="补卡申请截止天数" prop="supplementDeadline">
                          <el-input v-model="form.supplementDeadline" placeholder="请输入补卡申请截止天数" />
                        </el-form-item>
                        <el-form-item label="迟到阈值" prop="lateThreshold">
                          <el-input v-model="form.lateThreshold" placeholder="请输入迟到阈值" />
                        </el-form-item>
                        <el-form-item label="早退阈值" prop="earlyLeaveThreshold">
                          <el-input v-model="form.earlyLeaveThreshold" placeholder="请输入早退阈值" />
                        </el-form-item>
                        <el-form-item label="旷工阈值" prop="absentThreshold">
                          <el-input v-model="form.absentThreshold" placeholder="请输入旷工阈值" />
                        </el-form-item>
                        <el-form-item label="是否启用" prop="isEnabled">
                          <el-radio-group v-model="form.isEnabled">
                            <el-radio
                                v-for="dict in sys_true_or_false"
                                :key="dict.value"
                                :label="parseInt(dict.value)"
                            >{{dict.label}}</el-radio>
                          </el-radio-group>
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

<script setup name="PunchRule">
  import { listPunchRule, getPunchRule, delPunchRule, addPunchRule, updatePunchRule } from "@/api/core/sch/punchRule"

  const { proxy } = getCurrentInstance()
      const { sys_true_or_false } = proxy.useDict('sys_true_or_false')

  const punchRuleList = ref([])
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
                    ruleName: null,
                    shiftId: null,
                    shiftName: null,
                    workStartTime: null,
                    workEndTime: null,
                    allowClockInStart: null,
                    allowClockInEnd: null,
                    allowClockOutStart: null,
                    allowClockOutEnd: null,
                    locationLat: null,
                    locationLng: null,
                    locationRadius: null,
                    locationAddress: null,
                    allowWifiSsids: null,
                    allowBluetoothDevices: null,
                    allowOutdoorPunch: null,
                    outdoorRequirePhoto: null,
                    allowSupplement: null,
                    supplementDeadline: null,
                    lateThreshold: null,
                    earlyLeaveThreshold: null,
                    absentThreshold: null,
                    isEnabled: null,
    },
    rules: {
                    ruleName: [
                { required: true, message: "规则名称不能为空", trigger: "blur" }
              ],
                    workStartTime: [
                { required: true, message: "上班时间不能为空", trigger: "blur" }
              ],
                    workEndTime: [
                { required: true, message: "下班时间不能为空", trigger: "blur" }
              ],
                    allowClockInStart: [
                { required: true, message: "允许上班打卡开始时间不能为空", trigger: "blur" }
              ],
                    allowClockInEnd: [
                { required: true, message: "允许上班打卡结束时间不能为空", trigger: "blur" }
              ],
                    allowClockOutStart: [
                { required: true, message: "允许下班打卡开始时间不能为空", trigger: "blur" }
              ],
                    allowClockOutEnd: [
                { required: true, message: "允许下班打卡结束时间不能为空", trigger: "blur" }
              ],
    }
  })

  const { queryParams, form, rules } = toRefs(data)

  /** 查询打卡规则列表 */
  function getList() {
    loading.value = true
    listPunchRule(queryParams.value).then(response => {
            punchRuleList.value = response.rows
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
                    ruleId: null,
                    ruleName: null,
                    shiftId: null,
                    shiftName: null,
                    workStartTime: null,
                    workEndTime: null,
                    allowClockInStart: null,
                    allowClockInEnd: null,
                    allowClockOutStart: null,
                    allowClockOutEnd: null,
                    locationLat: null,
                    locationLng: null,
                    locationRadius: null,
                    locationAddress: null,
                    allowWifiSsids: null,
                    allowBluetoothDevices: null,
                    allowOutdoorPunch: null,
                    outdoorRequirePhoto: null,
                    allowSupplement: null,
                    supplementDeadline: null,
                    lateThreshold: null,
                    earlyLeaveThreshold: null,
                    absentThreshold: null,
                    isEnabled: null,
                    remark: null,
                    createBy: null,
                    createTime: null,
                    updateBy: null,
                    updateTime: null
    }
    proxy.resetForm("punchRuleRef")
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
    ids.value = selection.map(item => item.ruleId)
    single.value = selection.length != 1
    multiple.value = !selection.length
  }

  /** 新增按钮操作 */
  function handleAdd() {
    reset()
    isViewMode.value = false // 设置为编辑模式
    open.value = true
    title.value = "添加打卡规则"
  }

  /** 修改按钮操作 */
  function handleUpdate(row) {
    reset()
    isViewMode.value = false // 设置为编辑模式
    const _ruleId = row.ruleId || ids.value
    getPunchRule(_ruleId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "修改打卡规则"
    })
  }

  /** 查看按钮操作 */
  function handleView(row) {
    reset()
    isViewMode.value = true // 设置为查看模式
    const _ruleId = row.ruleId || ids.value
    getPunchRule(_ruleId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "查看打卡规则"
    })
  }

  /** 提交按钮 */
  function submitForm() {
    proxy.$refs["punchRuleRef"].validate(valid => {
      if (valid) {
        if (form.value.ruleId != null) {
          updatePunchRule(form.value).then(response => {
            proxy.$modal.msgSuccess("修改成功")
            open.value = false
            getList()
          })
        } else {
          addPunchRule(form.value).then(response => {
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
    const _ruleIds = row.ruleId || ids.value
    proxy.$modal.confirm('是否确认删除打卡规则编号为"' + _ruleIds + '"的数据项？').then(function() {
      return delPunchRule(_ruleIds)
    }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    }).catch(() => {})
  }

  /** 导出按钮操作 */
  function handleExport() {
    proxy.download('sch/punchRule/export', {
      ...queryParams.value
    }, `punchRule_${new Date().getTime()}.xlsx`)
  }

  getList()
</script>