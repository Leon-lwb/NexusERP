<template>
  <div v-loading="loading">
    <el-alert v-if="!attrList.length" title="该物料所属分类暂未定义扩展属性" type="info" :closable="false" show-icon />
    <el-form v-else label-width="130px" :disabled="readonly">
      <el-form-item v-for="attr in mergedList" :key="attr.attrId" :label="attr.attrName + (attr.requiredFlag === 1 ? ' *' : '')">
        <el-input v-if="attr.dataType === 'STRING'" v-model="attr.textValue" placeholder="请输入" style="max-width: 320px" />
        <template v-else-if="attr.dataType === 'NUMBER'">
          <el-input-number v-model="attr.numericValue" controls-position="right" style="width: 200px" />
          <span v-if="attr.unit" style="margin-left: 8px; color: #909399">{{ attr.unit }}</span>
        </template>
        <el-select v-else-if="attr.dataType === 'ENUM'" v-model="attr.textValue" clearable style="width: 200px">
          <el-option v-for="opt in enumOptions(attr.enumValues)" :key="opt" :label="opt" :value="opt" />
        </el-select>
        <el-date-picker
          v-else-if="attr.dataType === 'DATE'"
          v-model="attr.dateValue"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 200px"
        />
        <el-switch v-else-if="attr.dataType === 'BOOLEAN'" v-model="attr.boolValue" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>
    <div v-if="attrList.length && !readonly" style="text-align: right; margin-top: 8px">
      <el-button type="primary" @click="handleSave">保存属性</el-button>
    </div>
  </div>
</template>

<script setup lang="ts" name="MaterialAttrValueEditor">
import { listCategoryAttr } from '@/api/erp/bd/category-attribute';
import { listMaterialAttrValue, saveMaterialAttrValue } from '@/api/erp/bd/material-attribute-value';
import type { CategoryAttributeVO } from '@/api/erp/bd/category-attribute/types';
import type { MaterialAttributeValueVO } from '@/api/erp/bd/material-attribute-value/types';

const props = defineProps<{ materialId: number | string; categoryId: number | string; readonly?: boolean }>();
const { proxy } = getCurrentInstance() as any;

const loading = ref(false);
const attrList = ref<CategoryAttributeVO[]>([]);
// 合并后的列表：属性模板 + 物料既有值（按 attrId 合并），值字段直接绑定到表单项
const mergedList = ref<any[]>([]);

function enumOptions(s?: string): string[] {
  return (s || '')
    .split(',')
    .map((x) => x.trim())
    .filter(Boolean);
}

async function load() {
  if (!props.categoryId) {
    attrList.value = [];
    mergedList.value = [];
    return;
  }
  loading.value = true;
  try {
    const [attrRes, valRes] = await Promise.all([
      listCategoryAttr(props.categoryId),
      props.materialId ? listMaterialAttrValue(props.materialId) : Promise.resolve({ data: [] as MaterialAttributeValueVO[] })
    ]);
    attrList.value = attrRes.data || [];
    const valMap: Record<string, MaterialAttributeValueVO> = {};
    (valRes.data || []).forEach((v) => (valMap[v.attrId] = v));
    mergedList.value = attrList.value.map((a) => ({
      ...a,
      textValue: valMap[a.attrId]?.textValue ?? '',
      numericValue: valMap[a.attrId]?.numericValue ?? undefined,
      boolValue: valMap[a.attrId]?.boolValue ?? undefined,
      dateValue: valMap[a.attrId]?.dateValue ?? undefined
    }));
  } finally {
    loading.value = false;
  }
}

function handleSave() {
  const values = mergedList.value.map((m) => ({
    attrId: m.attrId,
    textValue: m.textValue || undefined,
    numericValue: m.numericValue ?? undefined,
    boolValue: m.boolValue ?? undefined,
    dateValue: m.dateValue || undefined
  }));
  saveMaterialAttrValue(props.materialId, values)
    .then(() => proxy.$modal.msgSuccess('属性保存成功'))
    .catch(() => load());
}

watch(
  () => [props.categoryId, props.materialId],
  () => load(),
  { immediate: true }
);
</script>
