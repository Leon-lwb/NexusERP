<template>
  <div>
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-if="!readonly">新增属性</el-button>
      </el-col>
    </el-row>
    <el-table :data="attrList" v-loading="loading" size="small" border>
      <el-table-column label="属性编码" prop="attrCode" />
      <el-table-column label="属性名称" prop="attrName" />
      <el-table-column label="数据类型" prop="dataType" width="90">
        <template #default="scope">
          <dict-tag :options="bd_attr_data_type" :value="scope.row.dataType" />
        </template>
      </el-table-column>
      <el-table-column label="枚举值/单位" min-width="120" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="scope.row.dataType === 'ENUM'">{{ scope.row.enumValues }}</span>
          <span v-else-if="scope.row.dataType === 'NUMBER'">{{ scope.row.unit }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="必填" prop="requiredFlag" width="70" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.requiredFlag === 1" type="danger">必填</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" width="70" align="center" />
      <el-table-column label="操作" width="120" align="center" v-if="!readonly">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="attrTitle" v-model="attrOpen" width="640px" append-to-body>
      <el-form ref="attrRef" :model="attrForm" :rules="attrRules" label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="属性编码" prop="attrCode">
              <el-input v-model="attrForm.attrCode" placeholder="如 material_grade" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="属性名称" prop="attrName">
              <el-input v-model="attrForm.attrName" placeholder="如 材质" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="数据类型" prop="dataType">
              <el-select v-model="attrForm.dataType" placeholder="请选择" style="width: 100%">
                <el-option v-for="d in bd_attr_data_type" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="sortOrder">
              <el-input-number v-model="attrForm.sortOrder" controls-position="right" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="枚举可选项" prop="enumValues" v-if="attrForm.dataType === 'ENUM'">
          <el-input v-model="attrForm.enumValues" placeholder="逗号分隔，如 Q235,Q345,304" />
        </el-form-item>
        <el-form-item label="单位" prop="unit" v-if="attrForm.dataType === 'NUMBER'">
          <el-input v-model="attrForm.unit" placeholder="如 kg,mm" />
        </el-form-item>
        <el-form-item label="是否必填" prop="requiredFlag">
          <el-switch v-model="attrForm.requiredFlag" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="attrForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="attrOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="CategoryAttrManager">
import { listCategoryAttr, addCategoryAttr, updateCategoryAttr, delCategoryAttr } from '@/api/erp/bd/category-attribute';
import type { CategoryAttributeVO, CategoryAttributeForm } from '@/api/erp/bd/category-attribute/types';

const props = defineProps<{ categoryId: number | string; readonly?: boolean }>();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { bd_attr_data_type } = toRefs<any>(proxy.useDict('bd_attr_data_type'));

const attrList = ref<CategoryAttributeVO[]>([]);
const loading = ref(false);
const attrOpen = ref(false);
const attrTitle = ref('');
const attrRef = ref<any>(null);
const attrForm = ref<CategoryAttributeForm>(resetAttrForm());

const attrRules = {
  attrCode: [{ required: true, message: '属性编码不能为空', trigger: 'blur' }],
  attrName: [{ required: true, message: '属性名称不能为空', trigger: 'blur' }],
  dataType: [{ required: true, message: '数据类型不能为空', trigger: 'change' }]
};

function resetAttrForm(): CategoryAttributeForm {
  return { attrCode: '', attrName: '', dataType: 'STRING', enumValues: '', unit: '', requiredFlag: 0, sortOrder: 0, remark: '' };
}

function loadAttrs() {
  if (!props.categoryId) {
    attrList.value = [];
    return;
  }
  loading.value = true;
  listCategoryAttr(props.categoryId)
    .then((res) => {
      attrList.value = res.data || [];
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleAdd() {
  attrForm.value = resetAttrForm();
  attrOpen.value = true;
  attrTitle.value = '新增分类属性';
}

function handleEdit(row: CategoryAttributeVO) {
  attrForm.value = { ...row };
  attrOpen.value = true;
  attrTitle.value = '修改分类属性';
}

function handleDelete(row: CategoryAttributeVO) {
  proxy.$modal
    .confirm('是否确认删除属性"' + row.attrName + '"？')
    .then(() => delCategoryAttr(row.attrId))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功');
      loadAttrs();
    })
    .catch(() => {});
}

function submitForm() {
  attrRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    const data = { ...attrForm.value, categoryId: props.categoryId };
    const api = data.attrId ? updateCategoryAttr(data) : addCategoryAttr(data);
    api.then(() => {
      proxy.$modal.msgSuccess(data.attrId ? '修改成功' : '新增成功');
      attrOpen.value = false;
      loadAttrs();
    });
  });
}

watch(
  () => props.categoryId,
  () => loadAttrs(),
  { immediate: true }
);
</script>
