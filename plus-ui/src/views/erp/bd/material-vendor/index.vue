<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <!-- 技术债：状态下拉使用硬编码 el-option，缺对应字典，待补字典后改 useDict -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="物料" prop="materialId">
            <el-select
              v-model="queryParams.materialId"
              placeholder="请选择物料"
              clearable
              filterable
              remote
              :remote-method="searchMaterial"
              :loading="materialLoading"
              style="width: 100%"
            >
              <el-option
                v-for="item in materialOptions"
                :key="item.materialId"
                :label="item.materialCode + ' - ' + item.materialName"
                :value="item.materialId"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="供应商编码" prop="vendorCode">
            <el-input v-model="queryParams.vendorCode" placeholder="请输入供应商编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="供应商名称" prop="vendorName">
            <el-input v-model="queryParams.vendorName" placeholder="请输入供应商名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 操作按钮 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['bd:materialVendor:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['bd:materialVendor:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['bd:materialVendor:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['bd:materialVendor:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <!-- 技术债：默认供应商/状态列使用硬编码 el-tag，缺对应字典，待补字典后改 dict-tag -->
    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="物料编码" align="center" prop="materialCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="120" show-overflow-tooltip />
      <el-table-column label="供应商编码" align="center" prop="vendorCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="供应商名称" align="center" prop="vendorName" min-width="120" show-overflow-tooltip />
      <el-table-column label="单价" align="center" prop="price" />
      <el-table-column label="币种" align="center" prop="currency" />
      <el-table-column label="供货周期(天)" align="center" prop="leadTime" />
      <el-table-column label="最小订货量" align="center" prop="minOrderQty" />
      <el-table-column label="优先级" align="center" prop="priority" />
      <el-table-column label="默认供应商" align="center" prop="isDefault">
        <template #default="scope">
          <el-tag :type="scope.row.isDefault === 1 ? 'success' : 'info'">
            {{ scope.row.isDefault === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生效日期" align="center" prop="effectiveDate" />
      <el-table-column label="失效日期" align="center" prop="expiryDate" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['bd:materialVendor:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['bd:materialVendor:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改对话框 -->
    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料" prop="materialId">
              <el-select
                v-model="form.materialId"
                placeholder="请选择物料"
                filterable
                remote
                :remote-method="searchMaterial"
                :loading="materialLoading"
                style="width: 100%"
                @change="handleMaterialChange"
              >
                <el-option
                  v-for="item in materialOptions"
                  :key="item.materialId"
                  :label="item.materialCode + ' - ' + item.materialName"
                  :value="item.materialId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="vendorId">
              <el-select
                v-model="form.vendorId"
                placeholder="请选择供应商"
                filterable
                remote
                :remote-method="searchVendor"
                :loading="vendorLoading"
                style="width: 100%"
                @change="handleVendorChange"
              >
                <el-option
                  v-for="item in vendorOptions"
                  :key="item.vendorId"
                  :label="item.vendorCode + ' - ' + item.vendorName"
                  :value="item.vendorId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="单价" prop="price">
              <el-input-number v-model="form.price" placeholder="请输入单价" controls-position="right" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="币种" prop="currency">
              <el-select v-model="form.currency" placeholder="请选择币种" style="width: 100%">
                <el-option label="人民币(CNY)" value="CNY" />
                <el-option label="美元(USD)" value="USD" />
                <el-option label="欧元(EUR)" value="EUR" />
                <el-option label="日元(JPY)" value="JPY" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供货周期(天)" prop="leadTime">
              <el-input-number v-model="form.leadTime" placeholder="请输入供货周期" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最小订货量" prop="minOrderQty">
              <el-input-number
                v-model="form.minOrderQty"
                placeholder="请输入最小订货量"
                controls-position="right"
                :min="0"
                :precision="4"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="form.priority" placeholder="请输入优先级" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否默认供应商" prop="isDefault">
              <el-radio-group v-model="form.isDefault">
                <el-radio :label="0">否</el-radio>
                <el-radio :label="1">是</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker v-model="form.effectiveDate" type="date" placeholder="请选择生效日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效日期" prop="expiryDate">
              <el-date-picker v-model="form.expiryDate" type="date" placeholder="请选择失效日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :label="1">启用</el-radio>
                <el-radio :label="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
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

<script setup lang="ts" name="MaterialVendor">
import { listMaterialVendor, getMaterialVendor, delMaterialVendor, addMaterialVendor, updateMaterialVendor } from '@/api/erp/bd/material-vendor';
import { listMaterial } from '@/api/erp/bd/material';
import { listVendor } from '@/api/erp/bd/vendor';
import { MaterialVendorVO, MaterialVendorForm } from '@/api/erp/bd/material-vendor/types';

const { proxy } = getCurrentInstance();

// 物料-供应商关系列表
const dataList = ref<MaterialVendorVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<number[]>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');

// 物料和供应商选项
const materialOptions = ref<any[]>([]);
const vendorOptions = ref<any[]>([]);
const materialLoading = ref(false);
const vendorLoading = ref(false);

const data = reactive({
  form: {} as MaterialVendorForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    materialId: undefined,
    vendorCode: undefined,
    vendorName: undefined,
    status: undefined
  },
  rules: {
    materialId: [{ required: true, message: '物料不能为空', trigger: 'change' }],
    vendorId: [{ required: true, message: '供应商不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询物料-供应商关系列表 */
function getList() {
  loading.value = true;
  listMaterialVendor(queryParams.value).then((response) => {
    dataList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 取消按钮 */
function cancel() {
  open.value = false;
  reset();
}

/** 表单重置 */
function reset() {
  form.value = {
    relationId: undefined,
    materialId: undefined,
    vendorId: undefined,
    vendorCode: undefined,
    vendorName: undefined,
    leadTime: 0,
    minOrderQty: 0,
    price: 0,
    currency: 'CNY',
    isDefault: 0,
    priority: 0,
    effectiveDate: undefined,
    expiryDate: undefined,
    status: 1,
    remark: undefined
  };
  proxy.resetForm('formRef');
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 多选框选中数据 */
function handleSelectionChange(selection: MaterialVendorVO[]) {
  ids.value = selection.map((item) => item.relationId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

/** 搜索物料 */
function searchMaterial(query: string) {
  if (query) {
    materialLoading.value = true;
    listMaterial({ materialName: query, pageNum: 1, pageSize: 20 }).then((response) => {
      materialOptions.value = response.rows;
      materialLoading.value = false;
    });
  } else {
    materialOptions.value = [];
  }
}

/** 搜索供应商 */
function searchVendor(query: string) {
  if (query) {
    vendorLoading.value = true;
    listVendor({ vendorName: query, pageNum: 1, pageSize: 20 }).then((response) => {
      vendorOptions.value = response.rows;
      vendorLoading.value = false;
    });
  } else {
    vendorOptions.value = [];
  }
}

/** 物料选择变化 */
function handleMaterialChange(val: number) {
  // 物料选择后可扩展联动逻辑
}

/** 供应商选择变化 */
function handleVendorChange(val: number) {
  const vendor = vendorOptions.value.find((item) => item.vendorId === val);
  if (vendor) {
    form.value.vendorCode = vendor.vendorCode;
    form.value.vendorName = vendor.vendorName;
  }
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = '添加物料-供应商关系';
}

/** 修改按钮操作 */
function handleUpdate(row?: MaterialVendorVO) {
  reset();
  const relationId = row?.relationId || ids.value[0];
  getMaterialVendor(relationId).then((response) => {
    form.value = response.data;
    // 用详情返回的物料/供应商信息回填下拉选项，确保修改时显示正确标签
    // （searchMaterial/searchVendor 是按名称远程搜索，不能用 ID 当搜索词）
    if (form.value.materialId) {
      materialOptions.value = [
        {
          materialId: form.value.materialId,
          materialCode: form.value.materialCode,
          materialName: form.value.materialName
        }
      ];
    }
    if (form.value.vendorId) {
      vendorOptions.value = [
        {
          vendorId: form.value.vendorId,
          vendorCode: form.value.vendorCode,
          vendorName: form.value.vendorName
        }
      ];
    }
    open.value = true;
    title.value = '修改物料-供应商关系';
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (valid) {
      if (form.value.relationId != null) {
        updateMaterialVendor(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addMaterialVendor(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row?: MaterialVendorVO) {
  const relationIds = row?.relationId || ids.value;
  proxy.$modal
    .confirm('是否确认删除物料-供应商关系编号为"' + relationIds + '"的数据项？')
    .then(function () {
      return delMaterialVendor(relationIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download(
    'erp/bd/materialVendor/export',
    {
      ...queryParams.value
    },
    `material_vendor_${new Date().getTime()}.xlsx`
  );
}

getList();
</script>

<style scoped lang="scss">
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
