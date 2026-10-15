<template>
  <el-dialog v-model="open" title="导入模板包" width="min(560px, calc(100vw - 32px))"
    ><el-form label-position="top" :disabled="saving"
      ><el-form-item label="模板包"
        ><input
          :key="session"
          :disabled="saving"
          type="file"
          accept=".json,.reportpkg"
          aria-label="选择报表模板包"
          style="max-width: 100%"
          @change="read" /></el-form-item
      ><el-form-item label="目标契约"
        ><el-select v-model="version" style="width: 100%"
          ><el-option
            v-for="dataset in datasets"
            :key="dataset.dataset.id"
            :label="dataset.dataset.name"
            :value="dataset.dataset.publishedVersion" /></el-select></el-form-item
      ><el-form-item label="新编码"><el-input v-model="code" /></el-form-item
      ><el-form-item label="名称"><el-input v-model="title" /></el-form-item></el-form
    ><el-alert title="导入后为草稿，依赖契约由服务端校验，业务对象默认值需要重新选择。关闭弹窗不会撤销已提交的导入。" :closable="false" /><template
      #footer
      ><el-button @click="open = false">取消</el-button
      ><el-button type="primary" :disabled="!pack || !version || !code || !title" :loading="saving" @click="submit">导入草稿</el-button></template
    ></el-dialog
  >
</template>
<script setup lang="ts">
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, watch, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { importTemplatePackage } from '@/api/report';
import { RequestGate } from '@/views/report/model';
import type { DatasetView } from '@/types/report';
/**
 * 保存当前页面或测试的open状态。
 */
const open = defineModel<boolean>({ default: false });
defineProps<{
  /** 保存当前 `datasets` 值。 */
  datasets: DatasetView[];
}>();
/**
 * 保存当前页面或测试的emit状态。
 */
const emit = defineEmits<{
  /** 保存当前 `imported` 值。 */
  imported: [string];
}>();
/**
 * 保存当前页面或测试的pack状态。
 */
const pack = ref<unknown>();
/**
 * 保存当前页面或测试的version状态。
 */
const version = ref('');
/**
 * 保存当前页面或测试的code状态。
 */
const code = ref('');
/**
 * 保存当前页面或测试的title状态。
 */
const title = ref('');
/**
 * 保存当前页面或测试的saving状态。
 */
const saving = ref(false);
/**
 * 保存当前页面或测试的session状态。
 */
const session = ref(0);
/**
 * 保存当前页面或测试的gate状态。
 */
const gate = new RequestGate();
watch(
  open,
  () => {
    gate.invalidate();
    session.value++;
    pack.value = undefined;
    version.value = code.value = title.value = '';
    saving.value = false;
  },
  { flush: 'sync' }
);
/** 读取并检查当前租户私有产物。 */
async function read(event: Event) {
  const ticket = gate.begin();
  const file = (event.target as HTMLInputElement).files?.[0];
  pack.value = undefined;
  if (!file) return;
  if (file.size > 2000000) {
    ElMessage.warning('模板包最多2MB');
    return;
  }
  try {
    const value = JSON.parse(await file.text());
    if (gate.current(ticket)) pack.value = value;
  } catch {
    if (gate.current(ticket)) ElMessage.warning('模板包格式无效');
  }
}
/** 处理当前报表的 `submit` 操作。 */
async function submit() {
  if (saving.value || !open.value || !pack.value || !version.value || !code.value || !title.value) return;
  const ticket = gate.begin();
  saving.value = true;
  try {
    const response = await importTemplatePackage({
      templatePackage: pack.value,
      datasetVersionId: version.value,
      code: code.value,
      title: title.value,
      catalogId: null
    });
    if (!gate.current(ticket)) return;
    open.value = false;
    emit('imported', response.data.id);
  } catch {
  } finally {
    if (gate.current(ticket)) saving.value = false;
  }
}
onUnmounted(() => gate.invalidate());
</script>
