<template>
  <div class="field-panel">
    <div class="panel-header">
      <h3>字段库</h3>
    </div>
    <div class="field-categories">
      <div class="category">
        <h4>基础字段</h4>
        <div class="field-list">
          <div v-for="field in basicFields" :key="field.type" class="field-item" draggable="true" @dragstart="handleDragStart(field)">
            <i :class="field.icon"></i>
            <span>{{ field.label }}</span>
          </div>
        </div>
      </div>

      <div class="category">
        <h4>高级字段</h4>
        <div class="field-list">
          <div v-for="field in advancedFields" :key="field.type" class="field-item" draggable="true" @dragstart="handleDragStart(field)">
            <i :class="field.icon"></i>
            <span>{{ field.label }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup name="FieldPanel" lang="ts">
const emit = defineEmits<{
  addField: [fieldType: string];
}>();

interface FieldType {
  type: string;
  label: string;
  icon: string;
}

const basicFields: FieldType[] = [
  { type: 'varchar', label: '文本', icon: 'el-icon-document' },
  { type: 'int', label: '整数', icon: 'el-icon-s-grid' },
  { type: 'bigint', label: '长整数(bigint)', icon: 'el-icon-s-grid' },
  { type: 'decimal', label: '小数', icon: 'el-icon-money' },
  { type: 'datetime', label: '日期时间', icon: 'el-icon-time' },
  { type: 'text', label: '长文本', icon: 'el-icon-document-copy' }
];

const advancedFields: FieldType[] = [
  { type: 'json', label: 'JSON', icon: 'el-icon-connection' },
  { type: 'enum', label: '枚举', icon: 'el-icon-menu' },
  { type: 'foreign', label: '外键', icon: 'el-icon-link' }
];

const handleDragStart = (field: FieldType) => {
  // 拖拽开始
  emit('addField', field.type);
};
</script>

<style lang="scss" scoped>
.field-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;

  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 500;
  }
}

.field-categories {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.category {
  margin-bottom: 24px;

  h4 {
    margin: 0 0 12px 0;
    font-size: 14px;
    font-weight: 500;
    color: #606266;
  }
}

.field-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 4px;
  cursor: grab;
  transition: all 0.2s ease;

  &:hover {
    background: #e3f2fd;
    border-color: #2196f3;
  }

  &:active {
    cursor: grabbing;
  }

  i {
    font-size: 16px;
    color: #666;
  }

  span {
    font-size: 14px;
    color: #333;
  }
}
</style>
