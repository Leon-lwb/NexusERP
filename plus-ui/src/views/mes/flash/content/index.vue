<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="配置名称" prop="contentName">
            <el-input v-model="queryParams.contentName" placeholder="请输入" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100%">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已发布" value="RELEASED" />
              <el-option label="已归档" value="ARCHIVED" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:flash:content:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:flash:content:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="名称" align="center" prop="contentName" min-width="140" />
      <el-table-column label="版本" align="center" prop="version" width="100" />
      <el-table-column label="文件MD5" align="center" prop="fileMd5" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'RELEASED' ? 'success' : scope.row.status === 'DRAFT' ? 'info' : 'warning'">
            {{ { DRAFT: '草稿', RELEASED: '已发布', ARCHIVED: '已归档' }[scope.row.status] || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" align="center" prop="publishTime" width="160" />
      <el-table-column label="操作" fixed="right" align="center" width="240" class-name="small-padding fixed-width" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['mes:flash:content:edit']"
            v-if="scope.row.status === 'DRAFT'"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            icon="Promotion"
            @click="handleRelease(scope.row)"
            v-hasPermi="['mes:flash:content:release']"
            v-if="scope.row.status === 'DRAFT'"
            >发布</el-button
          >
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['mes:flash:content:remove']"
            v-if="scope.row.status === 'DRAFT'"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加/修改配置文件对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="contentRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="配置名称" prop="contentName">
          <el-input v-model="form.contentName" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="适用物料" prop="materialId">
          <el-input-number
            :model-value="Number(form.materialId)"
            :min="1"
            controls-position="right"
            style="width: 100%"
            placeholder="物料ID"
            @update:model-value="(v?: number) => (form.materialId = v ?? 0)"
          />
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="form.version" placeholder="如 1.0.0" />
        </el-form-item>
        <el-form-item label="配置文件" prop="ossId">
          <file-upload v-model="form.ossId" :limit="1" @update:modelValue="handleFileChange" />
          <div v-if="form.fileMd5" style="color: #909399; font-size: 12px; margin-top: 4px">MD5: {{ form.fileMd5 }} ({{ form.fileSize }}字节)</div>
        </el-form-item>
        <el-form-item label="发布说明">
          <el-input v-model="form.releaseNote" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesFlashContent">
import { listContent, getContent, addContent, updateContent, delContent, releaseContent } from '@/api/mes/flash/content';
import type { MesContentFileVO, MesContentFileForm, MesContentFileQuery } from '@/api/mes/flash/content';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const contentRef = ref<FormInstance>();

const queryParams = ref<MesContentFileQuery>({
  pageNum: 1,
  pageSize: 10,
  contentName: undefined,
  status: undefined
});

const rules: ElFormRules = {
  contentName: [{ required: true, message: '配置名称不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '适用物料不能为空', trigger: 'blur' }],
  version: [{ required: true, message: '版本号不能为空', trigger: 'blur' }]
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
  handleDelete,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud({
  apis: { list: listContent, get: getContent, add: addContent, update: updateContent, del: delContent },
  idField: 'contentFileId',
  entityName: '配置文件',
  formRef: contentRef,
  queryParams,
  initForm: () =>
    ({
      contentFileId: undefined,
      contentName: '',
      materialId: 0,
      version: '',
      ossId: undefined,
      fileMd5: undefined,
      fileSize: undefined,
      releaseNote: undefined
    }) as MesContentFileForm
});

function handleAdd() {
  rawHandleAdd();
}
function handleUpdate(row?: any) {
  return rawHandleUpdate(row);
}

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

/** file-upload 返回 ossId; MD5/size 简化处理(实际可调固件上传接口或 OSS 元数据) */
const handleFileChange = (val: any) => {
  if (val && val.length) {
    form.value.ossId = val[0].ossId;
  }
};

/** 发布: DRAFT -> RELEASED */
const handleRelease = async (row: MesContentFileVO) => {
  await proxy?.$modal.confirm(`确认发布「${row.contentName} v${row.version}」? 发布后可用于刷写。`);
  await releaseContent(row.contentFileId);
  proxy?.$modal.msgSuccess('发布成功');
  getList();
};

getList();
</script>
