<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px" class="query-form">
                  <el-form-item label="客户编码" prop="clientCode">
                    <el-input
                        v-model="queryParams.clientCode"
                        placeholder="请输入客户编码"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="客户名称" prop="clientName">
                    <el-input
                        v-model="queryParams.clientName"
                        placeholder="请输入客户名称"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="客户类型" prop="clientType">
                    <el-select v-model="queryParams.clientType" placeholder="请选择" clearable>
                      <el-option
                          v-for="dict in bd_client_type"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="客户电话" prop="tel">
                    <el-input
                        v-model="queryParams.tel"
                        placeholder="请输入客户电话"
                        clearable
                        @keyup.enter="handleQuery"
                    />
                  </el-form-item>
                  <el-form-item label="统一代码" prop="creditCode">
                    <el-input
                        v-model="queryParams.creditCode"
                        placeholder="请输入统一社会信用代码"
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
            v-hasPermi="['core:bd:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="single"
            @click="handleUpdate"
            v-hasPermi="['core:bd:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['core:bd:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="warning"
            plain
            icon="Download"
            @click="handleExport"
            v-hasPermi="['core:bd:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="bdList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
              <el-table-column label="序号" type = "index" width="50" align="center" prop="clientId" />
              <el-table-column label="客户编码" align="center" prop="clientCode" :show-overflow-tooltip="true" />
              <el-table-column label="客户名称" align="center" prop="clientName" :show-overflow-tooltip="true" />
              <el-table-column label="客户类型" align="center" prop="clientType" width="120">
                <template #default="scope">
                      <dict-tag :options="bd_client_type" :value="scope.row.clientType"/>
                </template>
              </el-table-column>
              <el-table-column label="客户电话" align="center" prop="tel" :show-overflow-tooltip="true" />
              <el-table-column label="客户LOGO" align="center" prop="clientLogo" width="100">
                <template #default="scope">
                  <el-image 
                    v-if="scope.row.clientLogo" 
                    :src="scope.row.clientLogo" 
                    :preview-src-list="[scope.row.clientLogo]"
                    preview-teleported
                    fit="cover"
                    class="logo-image"
                  />
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
                <template #default="scope">
                  <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['core:bd:edit']">修改</el-button>
                  <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['core:bd:remove']">删除</el-button>
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

    <!-- 添加或修改客户信息对话框 -->
    <el-dialog :title="title" v-model="open" @close="cancel" width="960px" append-to-body>
      <el-form ref="bdRef" :model="form" :rules="rules" label-width="120px">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基本信息" name="basic">
            <el-row>
              <el-col :span="8">
                <el-form-item label="客户编码" prop="clientCode">
                  <el-input v-model="form.clientCode" placeholder="请输入客户编码" :disabled="autoGenFlag" />
                </el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label-width="80">
                  <el-switch v-model="autoGenFlag"
                      active-color="#13ce66"
                      active-text="自动生成"
                      @change="handleAutoGenChange">
                  </el-switch>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户名称" prop="clientName">
                  <el-input v-model="form.clientName" placeholder="请输入客户名称" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="8">
                <el-form-item label="客户简称" prop="clientNick">
                  <el-input v-model="form.clientNick" placeholder="请输入客户简称" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="客户英文名称" prop="clientEn">
                  <el-input v-model="form.clientEn" placeholder="请输入客户英文名称" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="客户类型" prop="clientType">
                  <el-select v-model="form.clientType" placeholder="请选择客户类型" style="width: 100%">
                    <el-option
                        v-for="dict in bd_client_type"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value">
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="送货地点" prop="address">
                  <el-input v-model="form.address" type="textarea" placeholder="请输入内容" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="社会信用代码" prop="creditCode">
                  <el-input v-model="form.creditCode" placeholder="请输入统一社会信用代码" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24">
                <el-form-item label="客户简介" prop="clientDesc">
                  <el-input v-model="form.clientDesc" type="textarea" placeholder="请输入内容" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
          
          <el-tab-pane label="联系方式" name="contact">
            <el-row>
              <el-col :span="12">
                <el-form-item label="客户官网地址" prop="webSite">
                  <el-input v-model="form.webSite" placeholder="请输入客户官网地址" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户邮箱地址" prop="email">
                  <el-input v-model="form.email" placeholder="请输入客户邮箱地址" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="客户电话" prop="tel">
                  <el-input v-model="form.tel" placeholder="请输入客户电话" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户LOGO" prop="clientLogo">
                  <div class="logo-upload-container">
                    <el-upload
                      class="upload-demo"
                      drag
                      action="/dev-api/core/bd/uploadLogo"
                      multiple>
                      <i class="el-icon-upload"></i>
                      <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
                      <div class="el-upload__tip" slot="tip">只能上传jpg/png文件，且不超过500kb</div>
                    </el-upload>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="8">
                <el-form-item label="联系人1" prop="contact1">
                  <el-input v-model="form.contact1" placeholder="请输入联系人1" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="联系人1-电话" prop="contact1Tel">
                  <el-input v-model="form.contact1Tel" placeholder="请输入联系人1-电话" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="联系人1-邮箱" prop="contact1Emal">
                  <el-input v-model="form.contact1Emal" placeholder="请输入联系人1-邮箱" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="8">
                <el-form-item label="联系人2" prop="contact2">
                  <el-input v-model="form.contact2" placeholder="请输入联系人2" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="联系人2-电话" prop="contact2Tel">
                  <el-input v-model="form.contact2Tel" placeholder="请输入联系人2-电话" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="联系人2-邮箱" prop="contact2Emal">
                  <el-input v-model="form.contact2Emal" placeholder="请输入联系人2-邮箱" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24">
                <el-form-item label="备注" prop="remark">
                  <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
          
        </el-tabs>
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

<script setup name="Bd">
  import { listBd, getBd, delBd, addBd, updateBd } from "@/api/core/bd"
  import { genCode } from "@/api/system/autocode/rule"

  const { proxy } = getCurrentInstance()
      const { bd_client_type } = proxy.useDict('bd_client_type')
const bdList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const autoGenFlag = ref(false)
const activeTab = ref("basic")

  const data = reactive({
    form: {},
    queryParams: {
      pageNum: 1,
      pageSize: 10,
                    clientCode: null,
                    clientName: null,
                    clientEn: null,
                    clientType: null,
                    tel: null,
                    creditCode: null,
    },
    rules: {
                    clientCode: [
                { required: true, message: "客户编码不能为空", trigger: "blur" }
              ],
                    clientName: [
                { required: true, message: "客户名称不能为空", trigger: "blur" }
              ],
                    clientDesc: [
                { required: true, message: "客户简介不能为空", trigger: "blur" }
              ],
                    clientType: [
                { required: true, message: "客户类型不能为空", trigger: "change" }
              ],
                    address: [
                { required: true, message: "客户默认送货地点不能为空", trigger: "blur" }
              ],
                    email: [
                { required: true, message: "客户邮箱不能为空", trigger: "blur" }
              ],
                    tel: [
                { required: true, message: "客户电话不能为空", trigger: "blur" }
              ],
                    contact1: [
                { required: true, message: "联系人1不能为空", trigger: "blur" }
              ],
                    contact1Tel: [
                { required: true, message: "联系人1电话不能为空", trigger: "blur" }
              ],
  }
})

const { queryParams, form, rules } = toRefs(data)

// 添加图片预览相关数据
const dialogImageUrl = ref('')
const dialogVisible = ref(false)

/** 查询客户信息列表 */
function getList() {
    loading.value = true
    listBd(queryParams.value).then(response => {
            bdList.value = response.rows
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
                    clientId: null,
                    clientCode: null,
                    clientName: null,
                    clientNick: null,
                    clientEn: null,
                    clientDesc: null,
                    clientType: null,
                    clientLogo: null,
                    address: null,
                    webSite: null,
                    email: null,
                    tel: null,
                    contact1: null,
                    contact1Tel: null,
                    contact1Emal: null,
                    contact2: null,
                    contact2Tel: null,
                    contact2Emal: null,
                    creditCode: null,
                    remark: null,
                    attr1: null,
                    attr2: null,
                    attr3: null,
                    attr4: null,
                    createBy: null,
                    createTime: null,
                    updateBy: null,
                    updateTime: null
    }
    proxy.resetForm("bdRef")
    activeTab.value = "basic"
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
    ids.value = selection.map(item => item.clientId)
    single.value = selection.length != 1
    multiple.value = !selection.length
  }

  /** 新增按钮操作 */
  function handleAdd() {
    reset()
    open.value = true
    title.value = "添加客户信息"
  }

  /** 修改按钮操作 */
  function handleUpdate(row) {
    reset()
    const _clientId = row.clientId || ids.value
    getBd(_clientId).then(response => {
      form.value = response.data
      open.value = true
      title.value = "修改客户信息"
    })
  }

  // 添加图片处理方法
  function handleRemove(file, fileList) {
    console.log(file, fileList)
  }

  function handlePictureCardPreview(file) {
    dialogImageUrl.value = file.url
    dialogVisible.value = true
  }

  /** 提交按钮 */
  function submitForm() {
    proxy.$refs["bdRef"].validate(valid => {
      if (valid) {
        if (form.value.clientId != null) {
          updateBd(form.value).then(response => {
            proxy.$modal.msgSuccess("修改成功")
            open.value = false
            getList()
          })
        } else {
          addBd(form.value).then(response => {
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
    const _clientIds = row.clientId || ids.value
    proxy.$modal.confirm('是否确认删除客户信息编号为"' + _clientIds + '"的数据项？').then(function() {
      return delBd(_clientIds)
    }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    }).catch(() => {})
  }

  /** 导出按钮操作 */
  function handleExport() {
    proxy.download('core/bd/export', {
      ...queryParams.value
    }, `bd_${new Date().getTime()}.xlsx`)
  }

  //自动生成编码
  function handleAutoGenChange(autoGenFlag){
    debugger;
    if(autoGenFlag){
      genCode('CLIENT_CODE').then(response =>{
        form.value.clientCode = response;
      });
    }else{
      form.value.clientCode = null;
    }
  }

  getList()
</script>

<style scoped>
.query-form .el-form-item {
  margin-bottom: 12px;
}
.logo-image {
  width: 40px;
  height: 40px;
  border-radius: 4px;
}
</style>