<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="SN ID" prop="snId">
              <el-input-number v-model="queryParams.snId" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="批次ID" prop="lotId">
              <el-input-number v-model="queryParams.lotId" :min="1" controls-position="right" style="width: 100%" />
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
          <el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:lt:binding:add']">新增绑定</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="!ids.length" @click="handleDelete" v-hasPermi="['mes:lt:binding:remove']">删除</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="SN编码" prop="snCode" min-width="160" />
        <el-table-column label="批次编码" prop="lotCode" min-width="160" />
        <el-table-column label="物料ID" prop="materialId" align="right" width="100" />
        <el-table-column label="绑定时间" prop="boundTime" width="160" />
        <el-table-column label="绑定人ID" prop="boundBy" align="right" width="100" />
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="480px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="SN ID" prop="snId"><el-input-number v-model="form.snId" :min="1" controls-position="right" style="width: 100%" /></el-form-item>
        <el-form-item label="批次ID" prop="lotId"><el-input-number v-model="form.lotId" :min="1" controls-position="right" style="width: 100%" /></el-form-item>
        <el-form-item label="物料ID" prop="materialId"><el-input-number v-model="form.materialId" :min="1" controls-position="right" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="dialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="LtSnBinding">
import { listBinding, delBinding, addBinding } from '@/api/mes/lt/binding';
import type { LtSnBindingVO, LtSnBindingForm, LtSnBindingQuery } from '@/api/mes/lt/binding';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const dataList = ref<LtSnBindingVO[]>([]);
const total = ref(0);
const loading = ref(true);
const ids = ref<number[]>([]);
const queryParams = reactive<LtSnBindingQuery>({ pageNum: 1, pageSize: 10 } as any);
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();
const dialog = reactive({ visible: false, title: '' });
const form = reactive<LtSnBindingForm>({ snId: 0, lotId: 0, materialId: 0 } as LtSnBindingForm);
const rules: ElFormRules = {
  snId: [{ required: true, message: 'SN ID不能为空', trigger: 'blur' }],
  lotId: [{ required: true, message: '批次ID不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '物料ID不能为空', trigger: 'blur' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listBinding(queryParams);
    dataList.value = res.rows;
    total.value = res.total;
  } finally { loading.value = false; }
};

const handleQuery = () => { queryParams.pageNum = 1; getList(); };
const resetQuery = () => { proxy!.resetForm('queryFormRef'); handleQuery(); };
const handleSelectionChange = (selection: LtSnBindingVO[]) => { ids.value = selection.map(i => i.bindingId); };

const handleAdd = () => {
  Object.assign(form, { bindingId: undefined, snId: 0, lotId: 0, materialId: 0 });
  formRef.value?.resetFields();
  dialog.title = '新增SN-批次绑定'; dialog.visible = true;
};

const submitForm = () => {
  formRef.value?.validate(async (valid) => {
    if (!valid) return;
    await addBinding(form);
    proxy!.$modal.msgSuccess('绑定成功');
    dialog.visible = false; getList();
  });
};

const handleDelete = async () => {
  await proxy!.$modal.confirm('确认删除选中绑定关系?');
  await delBinding(ids.value);
  proxy!.$modal.msgSuccess('删除成功'); getList();
};

onMounted(() => getList());
</script>
