<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="物料编码" prop="materialCode">
        <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="物料名称" prop="materialName">
        <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="物料类型" prop="materialType">
        <el-select v-model="queryParams.materialType" placeholder="请选择物料类型" clearable>
          <el-option v-for="dict in bd_material_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源类型" prop="sourceType">
        <el-select v-model="queryParams.sourceType" placeholder="请选择来源类型" clearable>
          <el-option v-for="dict in bd_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否批次管理" prop="isBatchManage">
        <el-select v-model="queryParams.isBatchManage" placeholder="请选择是否批次管理" clearable>
          <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否序列号管理" prop="isSerialManage">
        <el-select v-model="queryParams.isSerialManage" placeholder="请选择是否序列号管理" clearable>
          <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="质量状态" prop="qualityStatus">
        <el-select v-model="queryParams.qualityStatus" placeholder="请选择质量状态" clearable>
          <el-option v-for="dict in bd_quality_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['bd:material:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['bd:material:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['bd:material:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['bd:material:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['bd:material:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="materialList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="物料ID" align="center" prop="materialId" />
      <el-table-column label="物料编码" align="center" prop="materialCode" />
      <el-table-column label="物料名称" align="center" prop="materialName" />
      <el-table-column label="规格型号" align="center" prop="materialSpec" />
      <el-table-column label="物料类型" align="center" prop="materialType">
        <template #default="scope">
          <dict-tag :options="bd_material_type" :value="scope.row.materialType" />
        </template>
      </el-table-column>
      <el-table-column label="来源类型" align="center" prop="sourceType">
        <template #default="scope">
          <dict-tag :options="bd_source_type" :value="scope.row.sourceType" />
        </template>
      </el-table-column>
      <el-table-column label="是否批次管理" align="center" prop="isBatchManage">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isBatchManage" />
        </template>
      </el-table-column>
      <el-table-column label="是否序列号管理" align="center" prop="isSerialManage">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isSerialManage" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="质量状态" align="center" prop="qualityStatus">
        <template #default="scope">
          <dict-tag :options="bd_quality_status" :value="scope.row.qualityStatus" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['bd:material:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['bd:material:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改物料产品主对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="materialRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请输入物料编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规格型号" prop="materialSpec">
              <el-input v-model="form.materialSpec" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料描述" prop="materialDesc">
              <el-input v-model="form.materialDesc" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料分类ID" prop="categoryId">
              <el-input-number v-model="form.categoryId" placeholder="请输入物料分类ID" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="基本计量单位ID" prop="baseUomId">
              <el-input-number v-model="form.baseUomId" placeholder="请输入基本计量单位ID" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="采购单位ID" prop="purchaseUomId">
              <el-input-number v-model="form.purchaseUomId" placeholder="请输入采购单位ID" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存单位ID" prop="inventoryUomId">
              <el-input-number v-model="form.inventoryUomId" placeholder="请输入库存单位ID" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="销售单位ID" prop="saleUomId">
              <el-input-number v-model="form.saleUomId" placeholder="请输入销售单位ID" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料类型" prop="materialType">
              <el-select v-model="form.materialType" placeholder="请选择物料类型">
                <el-option v-for="dict in bd_material_type" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型">
                <el-option v-for="dict in bd_source_type" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否批次管理" prop="isBatchManage">
              <el-radio-group v-model="form.isBatchManage">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否序列号管理" prop="isSerialManage">
              <el-radio-group v-model="form.isSerialManage">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安全库存" prop="safetyStock">
              <el-input-number v-model="form.safetyStock" placeholder="请输入安全库存" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最低库存" prop="minStock">
              <el-input-number v-model="form.minStock" placeholder="请输入最低库存" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最高库存" prop="maxStock">
              <el-input-number v-model="form.maxStock" placeholder="请输入最高库存" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="标准成本" prop="standardCost">
              <el-input-number v-model="form.standardCost" placeholder="请输入标准成本" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参考价格" prop="referencePrice">
              <el-input-number v-model="form.referencePrice" placeholder="请输入参考价格" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                  :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质量状态" prop="qualityStatus">
              <el-select v-model="form.qualityStatus" placeholder="请选择质量状态">
                <el-option v-for="dict in bd_quality_status" :key="dict.value" :label="dict.label"
                  :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="保质期(天)" prop="shelfLife">
              <el-input-number v-model="form.shelfLife" placeholder="请输入保质期(天)" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重量(kg)" prop="weight">
              <el-input-number v-model="form.weight" placeholder="请输入重量(kg)" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="体积(m³)" prop="volume">
              <el-input-number v-model="form.volume" placeholder="请输入体积(m³)" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
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

<script setup name="Material">
import { listMaterial, getMaterial, delMaterial, addMaterial, updateMaterial } from "@/api/core/bd/material"

const { proxy } = getCurrentInstance()
const { sys_true_or_false, bd_source_type, bd_material_type, bd_quality_status } = proxy.useDict('sys_true_or_false', 'bd_source_type', 'bd_material_type', 'bd_quality_status')

const materialList = ref([])
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
    materialCode: null,
    materialName: null,
    materialSpec: null,
    materialType: null,
    sourceType: null,
    isBatchManage: null,
    isSerialManage: null,
    status: null,
    qualityStatus: null,
  },
  rules: {
    materialCode: [
      { required: true, message: "物料编码不能为空", trigger: "blur" }
    ],
    materialName: [
      { required: true, message: "物料名称不能为空", trigger: "blur" }
    ],
    categoryId: [
      { required: true, message: "物料分类ID不能为空", trigger: "blur" }
    ],
    materialType: [
      { required: true, message: "物料类型不能为空", trigger: "change" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询物料产品主列表 */
function getList() {
  loading.value = true
  listMaterial(queryParams.value).then(response => {
    materialList.value = response.rows
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
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    materialDesc: null,
    categoryId: null,
    baseUomId: null,
    purchaseUomId: null,
    inventoryUomId: null,
    saleUomId: null,
    materialType: null,
    sourceType: null,
    isBatchManage: 0,
    isSerialManage: 0,
    safetyStock: 0,
    minStock: 0,
    maxStock: 0,
    standardCost: 0,
    referencePrice: 0,
    status: 1,
    qualityStatus: 1,
    shelfLife: 0,
    weight: 0,
    volume: 0,
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
  proxy.resetForm("materialRef")
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
  ids.value = selection.map(item => item.materialId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  open.value = true
  title.value = "添加物料产品主"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  isViewMode.value = false // 设置为编辑模式
  const _materialId = row.materialId || ids.value
  getMaterial(_materialId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改物料产品主"
  })
}

/** 查看按钮操作 */
function handleView(row) {
  reset()
  isViewMode.value = true // 设置为查看模式
  const _materialId = row.materialId || ids.value
  getMaterial(_materialId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "查看物料产品主"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["materialRef"].validate(valid => {
    if (valid) {
      if (form.value.materialId != null) {
        updateMaterial(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addMaterial(form.value).then(response => {
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
  const _materialIds = row.materialId || ids.value
  proxy.$modal.confirm('是否确认删除物料产品主编号为"' + _materialIds + '"的数据项？').then(function () {
    return delMaterial(_materialIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('bd/material/export', {
    ...queryParams.value
  }, `material_${new Date().getTime()}.xlsx`)
}

getList()
</script>