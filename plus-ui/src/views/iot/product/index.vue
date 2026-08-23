<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="产品编码" prop="productCode">
            <el-input v-model="queryParams.productCode" placeholder="请输入产品编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="产品名称" prop="productName">
            <el-input v-model="queryParams.productName" placeholder="请输入产品名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="产品类型" prop="productType">
            <el-select v-model="queryParams.productType" placeholder="请选择产品类型" clearable style="width: 100%">
              <el-option v-for="dict in product_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="协议类型" prop="protocolType">
            <el-select v-model="queryParams.protocolType" placeholder="请选择协议类型" clearable style="width: 100%">
              <el-option v-for="dict in iot_protocol_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in iot_device_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['iot:product:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['iot:product:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['iot:product:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['iot:product:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['iot:product:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="productList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="产品编码" align="center" prop="productCode" />
      <el-table-column label="产品名称" align="center" prop="productName" min-width="120" show-overflow-tooltip />
      <el-table-column label="产品类型" align="center" prop="productType">
        <template #default="scope">
          <dict-tag :options="product_type" :value="scope.row.productType" />
        </template>
      </el-table-column>
      <el-table-column label="协议类型" align="center" prop="protocolType">
        <template #default="scope">
          <dict-tag :options="iot_protocol_type" :value="scope.row.protocolType" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="iot_device_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['iot:product:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['iot:product:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改产品对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="productRef" :model="form" :rules="rules" label-width="90px" :disabled="isViewMode">
        <el-form-item label="产品编码" prop="productCode">
          <el-input v-model="form.productCode" placeholder="请输入产品编码" />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="form.productName" placeholder="请输入产品名称" />
        </el-form-item>
        <el-form-item label="产品类型" prop="productType">
          <el-select v-model="form.productType" placeholder="请选择产品类型" style="width: 100%">
            <el-option v-for="dict in product_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="协议类型" prop="protocolType">
          <el-select v-model="form.protocolType" placeholder="请选择协议类型" style="width: 100%">
            <el-option v-for="dict in iot_protocol_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in iot_device_status" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" placeholder="请输入内容" />
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

<script setup lang="ts" name="IotProduct">
import { listProduct, getProduct, delProduct, addProduct, updateProduct } from '@/api/iot/product';
import type { ProductVO, ProductQuery, ProductForm } from '@/api/iot/product/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { product_type, iot_protocol_type, iot_device_status } = toRefs<any>(proxy.useDict('product_type', 'iot_protocol_type', 'iot_device_status'));

const productList = ref<ProductVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);

const data = reactive<{
  form: ProductForm;
  queryParams: ProductQuery;
  rules: ElFormRules;
}>({
  form: {} as ProductForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    productCode: null,
    productName: null,
    productType: null,
    protocolType: null,
    status: null
  },
  rules: {
    productCode: [{ required: true, message: '产品编码不能为空', trigger: 'blur' }],
    productName: [{ required: true, message: '产品名称不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询产品列表 */
function getList() {
  loading.value = true;
  listProduct(queryParams.value).then((response) => {
    productList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    productId: null,
    productCode: null,
    productName: null,
    productType: 'DEVICE',
    protocolType: 'MQTT',
    description: null,
    status: 'ACTIVE',
    remark: null
  };
  proxy.resetForm('productRef');
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

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.productId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '添加产品';
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  isViewMode.value = false;
  const _productId = row.productId || ids.value;
  getProduct(_productId).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '修改产品';
  });
}

/** 查看按钮操作 */
function handleView(row) {
  reset();
  isViewMode.value = true;
  const _productId = row.productId || ids.value;
  getProduct(_productId).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '查看产品';
  });
}

/** 提交按钮 */
function submitForm() {
  (proxy.$refs['productRef'] as ElFormInstance).validate((valid) => {
    if (valid) {
      if (form.value.productId != null) {
        updateProduct(form.value).then((response) => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addProduct(form.value).then((response) => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _productIds = row.productId || ids.value;
  proxy.$modal
    .confirm('是否确认删除产品编号为"' + _productIds + '"的数据项？')
    .then(function () {
      return delProduct(_productIds);
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
    'iot/product/export',
    {
      ...queryParams.value
    },
    `iot_product_${new Date().getTime()}.xlsx`
  );
}

getList();
</script>
