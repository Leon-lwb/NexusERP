<template>
  <div class="app-container">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <!-- 操作按钮区域 - 放在最上方 -->
      <el-row :gutter="20" class="mb20">
        <el-col :span="24">
          <el-button type="primary" @click="handleSave">保存</el-button>
          <el-button @click="handleClose">关闭</el-button>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="父项物料信息" prop="parentMaterialName">
            <el-input v-model="form.parentMaterialName" placeholder="父项物料信息" readonly />
            <el-input v-model="form.parentMaterialId" style="display: none" />
          </el-form-item>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="BOM版本号" prop="bomVersion">
            <el-input v-model="form.bomVersion" placeholder="请输入BOM版本号" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="BOM名称信息" prop="bomName">
            <el-input v-model="form.bomName" placeholder="请输入BOM名称信息" />
          </el-form-item>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="生效日期" prop="effectiveDate">
            <el-date-picker clearable
                            v-model="form.effectiveDate"
                            type="date"
                            value-format="YYYY-MM-DD"
                            placeholder="请选择生效日期"
                            style="width: 100%">
            </el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="失效日期" prop="expiryDate">
            <el-date-picker clearable
                            v-model="form.expiryDate"
                            type="date"
                            value-format="YYYY-MM-DD"
                            placeholder="请选择失效日期"
                            style="width: 100%">
            </el-date-picker>
          </el-form-item>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="是否当前版本" prop="isCurrent">
            <el-radio-group v-model="form.isCurrent">
              <el-radio
                  v-for="dict in sys_true_or_false"
                  :key="dict.value"
                  :label="parseInt(dict.value)"
              >{{dict.label}}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态信息" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio
                  v-for="dict in sys_core_status"
                  :key="dict.value"
                  :label="parseInt(dict.value)"
              >{{dict.label}}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="备注信息" prop="remark">
            <el-input v-model="form.remark" type="textarea" placeholder="请输入备注信息" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    
    <!-- 物料选择对话框 -->
    <el-dialog :title="materialDialogTitle" v-model="materialSelectOpen" width="1000px" append-to-body>
      <el-form :model="materialQueryParams" ref="materialQueryRef" :inline="true" label-width="68px">
        <el-form-item label="物料编码" prop="materialCode">
          <el-input
            v-model="materialQueryParams.materialCode"
            placeholder="请输入物料编码"
            clearable
            @keyup.enter="handleMaterialQuery"
          />
        </el-form-item>
        <el-form-item label="物料名称" prop="materialName">
          <el-input
            v-model="materialQueryParams.materialName"
            placeholder="请输入物料名称"
            clearable
            @keyup.enter="handleMaterialQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleMaterialQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetMaterialQuery">重置</el-button>
        </el-form-item>
      </el-form>
      
      <el-table 
        v-loading="materialLoading" 
        :data="materialList" 
        @row-dblclick="selectMaterial"
        highlight-current-row
        style="width: 100%"
      >
        <el-table-column type="index" width="50" align="center" />
        <el-table-column label="物料编码" align="center" prop="materialCode" />
        <el-table-column label="物料名称" align="center" prop="materialName" />
        <el-table-column label="规格型号" align="center" prop="materialSpec" />
        <el-table-column label="物料类型" align="center" prop="materialType">
          <template #default="scope">
            <dict-tag :options="bd_material_type" :value="scope.row.materialType"/>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status">
          <template #default="scope">
            <dict-tag :options="sys_true_or_false" :value="scope.row.status"/>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectMaterial(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <pagination
        v-show="materialTotal > 0"
        :total="materialTotal"
        v-model:page="materialQueryParams.pageNum"
        v-model:limit="materialQueryParams.pageSize"
        @pagination="getMaterialList"
      />
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="materialSelectOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="BomForm">
import { getBom, addBom, updateBom } from "@/api/core/bd/bom"
import { listMaterial, getMaterial } from "@/api/core/bd/material"
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()
const { proxy } = getCurrentInstance()
const { sys_true_or_false, sys_core_status, bd_material_type } = proxy.useDict('sys_true_or_false', 'sys_core_status', 'bd_material_type')

// 表单相关
const formRef = ref(null)
const form = ref({
  bomId: null,
  parentMaterialId: null,
  parentMaterialName: null,
  bomVersion: "1.0",
  bomName: null,
  effectiveDate: null,
  expiryDate: null,
  isCurrent: null,
  status: null,
  remark: null
})

const rules = ref({
  parentMaterialName: [
    { required: true, message: "父项物料不能为空", trigger: "change" }
  ],
  bomVersion: [
    { required: true, message: "BOM版本号不能为空", trigger: "blur" }
  ],
  bomName: [
    { required: true, message: "BOM名称不能为空", trigger: "blur" }
  ]
})

// 物料选择相关
const materialSelectOpen = ref(false)
const materialList = ref([])
const materialLoading = ref(false)
const materialTotal = ref(0)
const materialDialogTitle = ref("选择物料")

const materialQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  materialCode: undefined,
  materialName: undefined
})

// 页面加载时初始化数据
onMounted(() => {
  const id = route.query.id
  const action = route.query.action
  
  if (id) {
    getBomDetails(id)
  }
})

// 获取BOM详情
function getBomDetails(id) {
  getBom(id).then(response => {
    console.log('BOM详情数据:', response.data);
    form.value = response.data;
    
    // 如果有 parentMaterialId 但没有 parentMaterialName，则查询物料信息
    if (form.value.parentMaterialId) {
      getMaterialNameById(form.value.parentMaterialId);
    }
  })
}

// 新增方法：通过ID获取物料名称
function getMaterialNameById(materialId) {
  getMaterial(materialId).then(response => {
    form.value.parentMaterialName = response.data.materialName;
  }).catch(() => {
    console.warn('获取物料名称失败');
  })
}

// 查询物料列表
function getMaterialList() {
  materialLoading.value = true
  listMaterial(materialQueryParams.value).then(response => {
    materialList.value = response.rows
    materialTotal.value = response.total
    materialLoading.value = false
  }).catch(() => {
    materialLoading.value = false
  })
}

// 搜索物料
function handleMaterialQuery() {
  materialQueryParams.value.pageNum = 1
  getMaterialList()
}

// 重置物料查询
function resetMaterialQuery() {
  proxy.resetForm("materialQueryRef")
  handleMaterialQuery()
}

// 打开物料选择对话框
function openMaterialSelect() {
  materialSelectOpen.value = true
  materialDialogTitle.value = "选择物料"
  getMaterialList()
}

// 选择物料
function selectMaterial(row) {
  form.value.parentMaterialId = row.materialId
  form.value.parentMaterialName = row.materialName
  materialSelectOpen.value = false
}

// 保存按钮
function handleSave() {
  formRef.value.validate(valid => {
    if (valid) {
      if (form.value.bomId) {
        updateBom(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          handleClose()
        })
      } else {
        addBom(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          handleClose()
        })
      }
    }
  })
}

// 关闭按钮
function handleClose() {
  router.push('/core/bd/bom')
}
</script>

<style scoped>
.mb20 {
  margin-bottom: 20px;
}
</style>