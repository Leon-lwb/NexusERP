<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="90px">
      <el-form-item label="配置名称" prop="contentName">
        <el-input v-model="queryParams.contentName" placeholder="请输入" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="RELEASED" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:flash:content:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="!ids.length" @click="handleDelete()" v-hasPermi="['mes:flash:content:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="配置编码" align="center" prop="contentCode" min-width="150" />
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
      <el-table-column label="操作" align="center" width="240" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleEdit(scope.row)"
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="title" v-model="open" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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
import {
  listContent,
  addContent,
  updateContent,
  delContent,
  releaseContent,
  type MesContentFileVO,
  type MesContentFileForm,
  type MesContentFileQuery
} from '@/api/mes/flash/content';

const { proxy } = getCurrentInstance() as any;
const list = ref<MesContentFileVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const total = ref(0);
const open = ref(false);
const title = ref('');
const form = ref<MesContentFileForm>({ contentName: '', materialId: 0, version: '' });
const queryParams = ref<MesContentFileQuery>({ pageNum: 1, pageSize: 10 });
const rules = {
  contentName: [{ required: true, message: '配置名称不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '适用物料不能为空', trigger: 'blur' }],
  version: [{ required: true, message: '版本号不能为空', trigger: 'blur' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listContent(queryParams.value);
    list.value = res.rows;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
const resetQuery = () => {
  proxy?.resetForm('queryRef');
  handleQuery();
};
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((i) => i.contentFileId);
};
const handleAdd = () => {
  form.value = { contentName: '', materialId: 0, version: '' };
  title.value = '新增配置文件';
  open.value = true;
};
const handleEdit = (row: MesContentFileVO) => {
  form.value = {
    contentFileId: row.contentFileId,
    contentName: row.contentName,
    materialId: row.materialId,
    version: row.version,
    ossId: row.ossId,
    fileMd5: row.fileMd5,
    fileSize: row.fileSize,
    releaseNote: row.releaseNote
  };
  title.value = '编辑配置文件';
  open.value = true;
};
const handleFileChange = (val: any) => {
  // file-upload 返回 ossId; MD5/size 简化处理(实际可调固件上传接口或 OSS 元数据)
  if (val && val.length) {
    form.value.ossId = val[0].ossId;
  }
};
const submitForm = () => {
  proxy?.$refs.formRef.validate(async (valid: boolean) => {
    if (!valid) return;
    if (form.value.contentFileId) {
      await updateContent(form.value);
      proxy?.$modal.msgSuccess('修改成功');
    } else {
      await addContent(form.value);
      proxy?.$modal.msgSuccess('新增成功');
    }
    open.value = false;
    getList();
  });
};
const cancel = () => {
  open.value = false;
};
const handleRelease = async (row: MesContentFileVO) => {
  await proxy?.$modal.confirm(`确认发布「${row.contentName} v${row.version}」? 发布后可用于刷写。`);
  await releaseContent(row.contentFileId);
  proxy?.$modal.msgSuccess('发布成功');
  getList();
};
const handleDelete = async (row?: MesContentFileVO) => {
  const delIds = row ? [row.contentFileId] : ids.value;
  await proxy?.$modal.confirm(row ? `确认删除「${row.contentName}」?` : `确认删除选中的 ${ids.value.length} 项?`);
  await delContent(delIds);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

getList();
</script>
