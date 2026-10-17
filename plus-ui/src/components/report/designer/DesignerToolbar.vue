<template>
  <div class="designer-toolbar" role="toolbar" aria-label="设计器主操作">
    <div class="toolbar-group">
      <el-button aria-label="返回报表中心" @click="emit('back')">返回</el-button>
      <el-button :disabled="!canUndo || busy || jsonDirty || gesture" title="撤销；最多保留50个设计状态" @click="emit('undo')">撤销</el-button>
      <el-button :disabled="!canRedo || busy || jsonDirty || gesture" title="重做；新的编辑会清除重做记录" @click="emit('redo')">重做</el-button>
    </div>
    <div class="toolbar-group">
      <el-button :disabled="!hasDesign || busy || gesture" :loading="previewing" @click="emit('preview')">数据预览</el-button>
      <el-button
        v-if="hasDesign"
        :key="existing ? 'edit' : 'add'"
        v-hasPermi="[existing ? 'report:definition:edit' : 'report:definition:add']"
        type="primary"
        :disabled="publishing || applying || gesture"
        :loading="saving"
        @click="emit('save')"
        >保存草稿</el-button
      >
      <span
        v-if="hasDesign"
        :key="existing ? 'publish-edit' : 'publish-add'"
        v-hasPermi="[existing ? 'report:definition:edit' : 'report:definition:add']"
        ><el-button
          v-hasPermi="['report:definition:publish']"
          type="success"
          :disabled="saving || applying || gesture"
          :loading="publishing"
          @click="emit('publish')"
          >保存并发布</el-button
        ></span
      >
    </div>
    <div class="toolbar-group">
      <el-button v-if="hasDesign" :aria-pressed="focusMode" @click="emit('toggle-focus')">{{ focusMode ? '退出专注编辑' : '专注编辑' }}</el-button>
      <el-dropdown trigger="click" @command="more">
        <el-button aria-label="设计器更多操作" :disabled="busy || gesture">更多<span class="i-ep-arrow-down" aria-hidden="true" /></el-button>
        <template #dropdown
          ><el-dropdown-menu>
            <el-dropdown-item command="validate" :disabled="!hasDesign || checking">校验定义</el-dropdown-item>
            <el-dropdown-item command="export-package" :disabled="!existing">导出模板包</el-dropdown-item>
            <el-dropdown-item command="export-dsl" :disabled="!hasDesign">下载定义</el-dropdown-item>
          </el-dropdown-menu></template
        >
      </el-dropdown>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
/** 常用操作固定展示，低频操作收起；权限与发布流程由父级原入口处理。 */
const props = defineProps<{
  hasDesign: boolean;
  existing: boolean;
  canUndo: boolean;
  canRedo: boolean;
  jsonDirty: boolean;
  saving: boolean;
  publishing: boolean;
  applying: boolean;
  checking: boolean;
  previewing: boolean;
  gesture: boolean;
  focusMode: boolean;
}>();
/** 工具栏只报告意图，不复制草稿保存或权限判断逻辑。 */
const emit = defineEmits<{
  back: [];
  undo: [];
  redo: [];
  preview: [];
  save: [];
  publish: [];
  validate: [];
  'export-package': [];
  'export-dsl': [];
  'toggle-focus': [];
}>();
/** 保存、发布与JSON应用期间禁止冲突操作。 */
const busy = computed(() => props.saving || props.publishing || props.applying);
/** 低频操作映射回既有父级处理入口。 */
function more(command: string) {
  if (command === 'validate') emit('validate');
  if (command === 'export-package') emit('export-package');
  if (command === 'export-dsl') emit('export-dsl');
}
</script>
<style scoped>
.designer-toolbar,
.toolbar-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  max-width: 100%;
}
.designer-toolbar {
  justify-content: flex-end;
  row-gap: 10px;
}
.designer-toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}
@media (max-width: 1023px) {
  .designer-toolbar {
    justify-content: flex-start;
  }
}
</style>
