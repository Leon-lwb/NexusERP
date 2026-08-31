<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="分类名称" prop="categoryName">
            <el-input v-model="queryParams.categoryName" placeholder="请输入分类名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="分类编码" prop="code">
            <el-input v-model="queryParams.code" placeholder="请输入分类编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['workflow:category:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['workflow:category:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['workflow:category:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['workflow:category:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="分类编号" align="center" prop="categoryId" />
      <el-table-column label="分类名称" align="center" prop="categoryName" :show-overflow-tooltip="true" />
      <el-table-column label="分类编码" align="center" prop="code" :show-overflow-tooltip="true" />
      <el-table-column label="应用类型" align="center" prop="appType" width="100">
        <template #default="scope">
          <dict-tag :options="wf_app_type" :value="scope.row.appType" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="修改" placement="top">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['workflow:category:edit']"></el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['workflow:category:remove']"></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改流程分类对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="categoryRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="分类编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入分类编码" />
        </el-form-item>
        <el-form-item label="应用类型" prop="appType">
          <el-select v-model="form.appType" placeholder="请选择应用类型" style="width: 100%">
            <el-option v-for="dict in wf_app_type" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
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

<script setup name="Category" lang="ts">
import { listCategory, getCategory, delCategory, addCategory, updateCategory } from '@/api/workflow/category';
import { CategoryForm, CategoryQuery, CategoryVO } from '@/api/workflow/category/types';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wf_app_type } = toRefs<any>(proxy?.useDict('wf_app_type'));

const categoryRef = ref<ElFormInstance>();

const queryParams = ref<CategoryQuery>({
  pageNum: 1,
  pageSize: 10,
  categoryName: '',
  code: ''
});

const rules = {
  categoryName: [{ required: true, message: '分类名称不能为空', trigger: 'blur' }],
  code: [{ required: true, message: '分类编码不能为空', trigger: 'blur' }],
  appType: [{ required: true, message: '应用类型不能为空', trigger: 'blur' }]
};

const {
  loading,
  list,
  total,
  open,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  cancel,
  submitForm,
  handleAdd,
  handleDelete,
  handleExport,
  handleSelectionChange,
  handleUpdate: rawHandleUpdate
} = useCrud<CategoryVO, CategoryQuery>({
  apis: { list: listCategory, get: getCategory, add: addCategory, update: updateCategory, del: delCategory },
  idField: 'categoryId',
  entityName: '流程分类',
  formRef: categoryRef,
  queryParams,
  initForm: (): CategoryForm => ({
    categoryId: undefined,
    categoryName: '',
    appType: '',
    code: '',
    remark: ''
  }),
  exportUrl: 'workflow/category/export',
  exportFileName: 'category'
});

/** 修改按钮操作 */
function handleUpdate(row?: CategoryVO) {
  return rawHandleUpdate(row ?? { categoryId: ids.value[0] });
}

/** 重置按钮操作 */
function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
