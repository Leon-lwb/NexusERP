<template>
  <div class="design-canvas">
    <div class="canvas-header">
      <div class="title">表单设计</div>
      <div class="actions">
        <el-button type="primary" @click="handleSave">保存</el-button>
        <el-button @click="handlePreview">预览</el-button>
      </div>
    </div>
    <div class="canvas-content" @dragover.prevent @drop="handleDrop">
      <el-form
        ref="formRef"
        :model="formData"
        :label-width="formConfig.labelWidth"
        :label-position="formConfig.labelPosition"
        :size="formConfig.size"
      >
        <template v-for="(component, index) in formComponents" :key="component.id">
          <component
            :is="component.component"
            v-bind="component.props"
            v-model="formData[component.field]"
            :label="component.label"
            :required="component.required"
            :is-master="component.isMaster"
            :sub-table-columns="component.subTableColumns"
            @delete="handleDeleteComponent(index)"
            @copy="handleCopyComponent(index)"
          />
        </template>
      </el-form>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive } from 'vue';
import type { FormComponent, FormConfig, FormContent } from '../../types';
import { ElMessage } from 'element-plus';

const props = defineProps<{
  formContent: FormContent;
}>();

const emit = defineEmits(['update:formContent', 'save', 'preview']);

const formRef = ref();
const formData = reactive({});
const formConfig = ref<FormConfig>(props.formContent.config);
const formComponents = ref<FormComponent[]>(props.formContent.components);

const handleDrop = (e: DragEvent) => {
  const component = JSON.parse(e.dataTransfer?.getData('component') || '{}');
  if (component) {
    const newComponent: FormComponent = {
      id: Date.now().toString(),
      type: component.type,
      label: component.label,
      field: `field_${Date.now()}`,
      component: component.component,
      props: { ...component.props },
      required: false,
      isMaster: false
    };
    formComponents.value.push(newComponent);
  }
};

const handleDeleteComponent = (index: number) => {
  formComponents.value.splice(index, 1);
};

const handleCopyComponent = (index: number) => {
  const component = formComponents.value[index];
  const newComponent: FormComponent = {
    ...component,
    id: Date.now().toString(),
    field: `field_${Date.now()}`
  };
  formComponents.value.splice(index + 1, 0, newComponent);
};

const handleSave = () => {
  const formContent: FormContent = {
    config: formConfig.value,
    components: formComponents.value
  };
  emit('update:formContent', formContent);
  emit('save', formContent);
  ElMessage.success('保存成功');
};

const handlePreview = () => {
  emit('preview', {
    config: formConfig.value,
    components: formComponents.value
  });
};
</script>

<style lang="scss" scoped>
.design-canvas {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;

  .canvas-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 20px;
    border-bottom: 1px solid #dcdfe6;

    .title {
      font-size: 16px;
      font-weight: bold;
    }

    .actions {
      display: flex;
      gap: 10px;
    }
  }

  .canvas-content {
    flex: 1;
    padding: 20px;
    overflow-y: auto;

    :deep(.el-form) {
      max-width: 800px;
      margin: 0 auto;
    }
  }
}
</style>
