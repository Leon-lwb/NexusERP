<template>
  <div>
    <el-form-item label="执行类型" key="executeType">
      <el-select v-model="serviceTaskForm.executeType">
        <el-option label="Java类" value="class" />
        <el-option label="表达式" value="expression" />
        <el-option label="代理表达式" value="delegateExpression" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="serviceTaskForm.executeType === 'class'" label="Java类" prop="class" key="execute-class">
      <el-input v-model="serviceTaskForm.class" clearable @change="updateElementTask" />
    </el-form-item>
    <el-form-item v-if="serviceTaskForm.executeType === 'expression'" label="表达式" prop="expression" key="execute-expression">
      <el-input v-model="serviceTaskForm.expression" clearable @change="updateElementTask" />
    </el-form-item>
    <el-form-item v-if="serviceTaskForm.executeType === 'expression'" label="结果变量">
      <el-input v-model="serviceTaskForm.resultVariable" clearable @change="updateElementTask" />
    </el-form-item>
    <el-form-item v-if="serviceTaskForm.executeType === 'delegateExpression'" label="代理表达式" prop="delegateExpression" key="execute-delegate">
      <el-input v-model="serviceTaskForm.delegateExpression" clearable @change="updateElementTask" />
    </el-form-item>
  </div>
</template>

<script lang="ts" setup>
defineOptions({ name: 'ServiceTask' });
const props = defineProps({
  id: String,
  type: String
});

const defaultTaskForm = ref({
  executeType: '',
  class: '',
  expression: '',
  resultVariable: '',
  delegateExpression: ''
});
const serviceTaskForm = ref<any>({});
const bpmnElement = ref();

const bpmnInstances = () => (window as any)?.bpmnInstances;

const resetTaskForm = () => {
  for (const key in defaultTaskForm.value) {
    const value = bpmnElement.value?.businessObject[key] || defaultTaskForm.value[key];
    if (value) serviceTaskForm.value['executeType'] = key;
    serviceTaskForm.value[key] = value;
  }
};

const updateElementTask = () => {
  const taskAttr = Object.create(null);
  const type = serviceTaskForm.value['executeType'];
  for (const key in serviceTaskForm.value) {
    if (key !== 'executeType' && key !== type) taskAttr[key] = null;
  }
  taskAttr[type] = serviceTaskForm.value[type] || '';
  bpmnInstances().modeling.updateProperties(toRaw(bpmnElement.value), taskAttr);
};

watch(
  () => props.id,
  () => {
    bpmnElement.value = bpmnInstances().bpmnElement;
    nextTick(() => {
      resetTaskForm();
    });
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  bpmnElement.value = null;
});
</script>
