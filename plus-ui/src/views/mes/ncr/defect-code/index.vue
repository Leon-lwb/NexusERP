<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="缺陷代码" prop="code">
              <el-input v-model="queryParams.code" placeholder="请输入缺陷代码" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="缺陷名称" prop="name">
              <el-input v-model="queryParams.name" placeholder="请输入缺陷名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="启用" prop="enabled">
              <el-select v-model="queryParams.enabled" placeholder="请选择" clearable style="width: 100%">
                <el-option label="启用" value="Y" />
                <el-option label="停用" value="N" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:ncr:defectcode:add']">新增缺陷代码</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="!ids.length"
            @click="() => handleDelete()"
            v-hasPermi="['mes:ncr:defectcode:remove']"
            >删除</el-button
          >
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table
        v-loading="loading"
        :data="dataList"
        row-key="codeId"
        default-expand-all
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        show-overflow-tooltip
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="缺陷代码" prop="code" min-width="140" />
        <el-table-column label="缺陷名称" prop="name" min-width="160" />
        <el-table-column label="层级" prop="level" align="center" width="80" />
        <el-table-column label="排序" prop="sortOrder" align="center" width="80" />
        <el-table-column label="启用" prop="enabled" align="center" width="80">
          <template #default="scope">
            <el-switch v-model="scope.row.enabled" :active-value="'Y'" :inactive-value="'N'" disabled />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['mes:ncr:defectcode:edit']">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:ncr:defectcode:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="父级节点" prop="parentId">
              <el-tree-select
                v-model="form.parentId"
                :data="treeData"
                node-key="codeId"
                :props="{ label: 'name', children: 'children' }"
                placeholder="请选择父级节点(可选)"
                clearable
                check-strictly
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="层级" prop="level">
              <el-input v-model="form.level" placeholder="如: 大类/中类/小类" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="缺陷代码" prop="code">
              <el-input v-model="form.code" placeholder="请输入缺陷代码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="缺陷名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入缺陷名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用" prop="enabled">
              <el-radio-group v-model="form.enabled">
                <el-radio value="Y">启用</el-radio>
                <el-radio value="N">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="dialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="NcrDefectCode">
import { listDefectCode, listDefectCodeTree, getDefectCode, addDefectCode, updateDefectCode, delDefectCode } from '@/api/mes/ncr/defect-code';
import type { NcrDefectCodeVO, NcrDefectCodeForm, NcrDefectCodeQuery } from '@/api/mes/ncr/defect-code';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const dataList = ref<NcrDefectCodeVO[]>([]);
const treeData = ref<NcrDefectCodeVO[]>([]);
const total = ref(0);
const loading = ref(true);
const ids = ref<number[]>([]);
const queryParams = reactive<NcrDefectCodeQuery>({ pageNum: 1, pageSize: 10 } as any);
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();
const dialog = reactive({ visible: false, title: '' });
const form = reactive<NcrDefectCodeForm>({ code: '', name: '', enabled: 'Y', sortOrder: 0 } as NcrDefectCodeForm);

const rules: ElFormRules = {
  code: [{ required: true, message: '缺陷代码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '缺陷名称不能为空', trigger: 'blur' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listDefectCode(queryParams);
    dataList.value = res.data || [];
    total.value = dataList.value.length;
  } finally {
    loading.value = false;
  }
};

const loadTree = async () => {
  try {
    const res = await listDefectCodeTree();
    treeData.value = res.data || [];
  } catch {
    treeData.value = [];
  }
};

const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};
const resetQuery = () => {
  proxy!.resetForm('queryFormRef');
  handleQuery();
};
const handleSelectionChange = (selection: NcrDefectCodeVO[]) => {
  ids.value = selection.map((i) => i.codeId);
};

const resetForm = () => {
  Object.assign(form, {
    codeId: undefined,
    parentId: undefined,
    code: '',
    name: '',
    level: '',
    enabled: 'Y',
    sortOrder: 0,
    remark: ''
  });
  formRef.value?.resetFields();
};

const handleAdd = () => {
  resetForm();
  dialog.title = '新增缺陷代码';
  dialog.visible = true;
  loadTree();
};
const handleEdit = async (row: NcrDefectCodeVO) => {
  resetForm();
  const res = await getDefectCode(row.codeId);
  Object.assign(form, res.data);
  dialog.title = '编辑缺陷代码';
  dialog.visible = true;
  loadTree();
};

const submitForm = () => {
  formRef.value?.validate(async (valid) => {
    if (!valid) return;
    if (form.codeId) {
      await updateDefectCode(form);
      proxy!.$modal.msgSuccess('修改成功');
    } else {
      await addDefectCode(form);
      proxy!.$modal.msgSuccess('新增成功');
    }
    dialog.visible = false;
    getList();
  });
};

const handleDelete = async (row?: NcrDefectCodeVO) => {
  let delIds: number[];
  if (row) {
    delIds = [row.codeId];
  } else {
    if (!ids.value.length) {
      proxy!.$modal.msgWarning('请选择要删除的缺陷代码');
      return;
    }
    delIds = ids.value;
  }
  await proxy!.$modal.confirm('确认删除选中的缺陷代码?');
  await delDefectCode(delIds);
  proxy!.$modal.msgSuccess('删除成功');
  getList();
};

onMounted(() => getList());
</script>
