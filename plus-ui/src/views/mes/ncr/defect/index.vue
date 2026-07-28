<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="NCR编号" prop="ncrId">
              <el-input v-model="queryParams.ncrId" placeholder="请输入NCR编号" clearable @keyup.enter="handleQuery" />
            </el-form-item>
          </el-col>
          <el-col :span="8" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:ncr:defect:add']">补录缺陷</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="handleQuery"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
        <el-table-column label="缺陷代码" prop="defectCode" width="120" />
        <el-table-column label="缺陷名称" prop="defectName" min-width="140" />
        <el-table-column label="数量" prop="defectQty" align="right" width="80" />
        <el-table-column label="原因分类" prop="defectCause" width="120">
          <template #default="scope"><dict-tag :options="mes_defect_cause" :value="scope.row.defectCause" /></template>
        </el-table-column>
        <el-table-column label="根因描述" prop="rootCauseDesc" min-width="200" show-overflow-tooltip />
        <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="handleQuery"
      />
    </el-card>

    <!-- 补录缺陷对话框 -->
    <el-dialog title="补录缺陷" v-model="dialog.visible" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="NCR编号" prop="ncrId" required>
          <el-input v-model.number="form.ncrId" placeholder="请输入NCR编号" />
        </el-form-item>
        <el-form-item label="缺陷代码" prop="defectCodeId" required>
          <el-select v-model="form.defectCodeId" placeholder="请选择缺陷代码" style="width: 100%" filterable>
            <el-option v-for="item in defectCodeList" :key="item.codeId" :label="item.code + ' - ' + item.name" :value="item.codeId" />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="缺陷数量" prop="defectQty">
              <el-input-number v-model="form.defectQty" :min="1" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原因分类" prop="defectCause">
              <el-select v-model="form.defectCause" placeholder="请选择原因分类" clearable style="width: 100%">
                <el-option v-for="dict in mes_defect_cause" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="根因描述" prop="rootCauseDesc">
          <el-input v-model="form.rootCauseDesc" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="dialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="NcrDefect">
import { listDefect, addDefect } from '@/api/mes/ncr/defect';
import type { NcrDefectVO, NcrDefectForm } from '@/api/mes/ncr/defect';
import { listDefectCode } from '@/api/mes/ncr/defect-code';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_defect_cause } = toRefs<any>(proxy?.useDict('mes_defect_cause'));
const dataList = ref<NcrDefectVO[]>([]);
const total = ref(0);
const loading = ref(false);
const queryParams = reactive({ pageNum: 1, pageSize: 10, ncrId: undefined as number | undefined } as any);
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();
const formRef = ref<ElFormInstance>();
const dialog = reactive({ visible: false, title: '' });
const form = reactive<NcrDefectForm>({ ncrId: 0, defectQty: 1 } as NcrDefectForm);
const defectCodeList = ref<any[]>([]);

const rules = {
  ncrId: [{ required: true, message: 'NCR编号不能为空', trigger: 'blur' }],
  defectCodeId: [{ required: true, message: '请选择缺陷代码', trigger: 'change' }],
  defectQty: [{ required: true, message: '请输入缺陷数量', trigger: 'blur' }]
};

const handleQuery = async () => {
  if (!queryParams.ncrId) {
    dataList.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const res = await listDefect(queryParams.ncrId);
    dataList.value = res.data || [];
    total.value = res.data?.length || 0;
  } finally {
    loading.value = false;
  }
};
const resetQuery = () => {
  proxy!.resetForm('queryFormRef');
  handleQuery();
};

const handleAdd = async () => {
  form.ncrId = 0;
  form.defectCodeId = undefined;
  form.defectQty = 1;
  form.defectCause = '';
  form.rootCauseDesc = '';
  form.remark = '';
  dialog.visible = true;
  const res = await listDefectCode({ pageNum: 1, pageSize: 9999 });
  defectCodeList.value = res.data || [];
};

const submitForm = () => {
  formRef.value?.validate(async (valid) => {
    if (!valid) return;
    await addDefect(form);
    proxy!.$modal.msgSuccess('补录成功');
    dialog.visible = false;
    handleQuery();
  });
};

onMounted(() => handleQuery());
</script>
