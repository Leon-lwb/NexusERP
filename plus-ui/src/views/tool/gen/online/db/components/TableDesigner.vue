<template>
  <div class="table-designer">
    <div class="designer-layout">
      <!-- 左侧字段面板 -->
      <div class="left-panel">
        <FieldPanel @add-field="handleAddField" />
      </div>

      <!-- 中间画布面板 -->
      <div class="center-panel">
        <CanvasPanel
          :tables="designDataLocal.tables"
          :relations="designDataLocal.relations"
          :selected-table="selectedTable"
          :selected-field="selectedField"
          @select-table="handleSelectTable"
          @select-field="handleSelectField"
          @update-table="handleUpdateTable"
          @update-field="handleUpdateField"
          @add-relation="handleAddRelation"
          @remove-relation="handleRemoveRelation"
          @add-table="handleAddTableFromCanvas"
          @remove-table="handleRemoveTable"
        />
      </div>

      <!-- 右侧属性面板 -->
      <div class="right-panel">
        <DbPropertyPanel
          :selected-table="selectedTable"
          :selected-field="selectedField"
          @update-table="handleUpdateTable"
          @update-field="handleUpdateField"
        />
      </div>
    </div>
  </div>
</template>

<script setup name="TableDesigner" lang="ts">
import { ref, reactive, watch, nextTick } from 'vue';
import FieldPanel from './FieldPanel.vue';
import CanvasPanel from './CanvasPanel.vue';
import DbPropertyPanel from './DbPropertyPanel.vue';

interface Table {
  id: string;
  name: string;
  comment: string;
  engine: string;
  charset: string;
  fields: Field[];
}

interface Field {
  id: string;
  name: string;
  type: string;
  length?: number;
  precision?: number;
  scale?: number;
  defaultValue?: string;
  comment: string;
  isPrimaryKey: boolean;
  isNotNull: boolean;
  isUnique: boolean;
}

interface Relation {
  id: string;
  fromTable: string;
  fromField: string;
  toTable: string;
  toField: string;
}

interface DesignData {
  designName: string;
  tableName: string;
  tableComment: string;
  tables: Table[];
  relations: Relation[];
}

const props = defineProps<{
  designData?: DesignData;
}>();

const emit = defineEmits<{
  'design-change': [data: DesignData];
  'table-select': [table: Table | null];
  'field-select': [field: Field | null];
}>();

// 选中的表和字段
const selectedTable = ref<Table | null>(null);
const selectedField = ref<Field | null>(null);

// 设计数据
const designDataLocal = reactive<DesignData>({
  designName: '',
  tableName: '',
  tableComment: '',
  tables: [],
  relations: []
});

// 添加一个标志来避免删除操作时的数据覆盖
const isDeleting = ref(false);

// 监听外部传入的设计数据
watch(
  () => props.designData,
  (newData) => {
    if (newData && !isDeleting.value) {
      Object.assign(designDataLocal, newData);
    }
  },
  { immediate: true, deep: true }
);

// 监听设计数据变化，通知父组件
watch(
  designDataLocal,
  (newData) => {
    emit('design-change', { ...newData });
  },
  { deep: true }
);

// 画布添加表
const handleAddTableFromCanvas = (table: Table) => {
  designDataLocal.tables.push(table);
  selectedTable.value = table;
};

// 删除表
const handleRemoveTable = (tableId: string) => {
  // 设置删除标志
  isDeleting.value = true;

  // 删除表
  const idx = designDataLocal.tables.findIndex((t) => t.id === tableId);
  if (idx !== -1) {
    designDataLocal.tables.splice(idx, 1);
  }

  // 删除相关关系
  const beforeRelations = designDataLocal.relations.length;
  designDataLocal.relations = designDataLocal.relations.filter((r) => r.fromTable !== tableId && r.toTable !== tableId);
  const afterRelations = designDataLocal.relations.length;

  // 清理选择
  if (selectedTable.value?.id === tableId) selectedTable.value = null;
  if (selectedField.value && !designDataLocal.tables.some((t) => t.fields.some((f) => f.id === selectedField.value!.id))) {
    selectedField.value = null;
  }

  // 强制触发更新
  nextTick(() => {
    emit('design-change', designDataLocal);

    // 延迟重置删除标志，确保父组件数据更新完成
    setTimeout(() => {
      isDeleting.value = false;
    }, 100);
  });
};

// 添加字段
const handleAddField = (fieldType: string) => {
  if (!selectedTable.value) {
    const newTable: Table = {
      id: `table_${Date.now()}`,
      name: `table_${designDataLocal.tables.length + 1}`,
      comment: '',
      engine: 'InnoDB',
      charset: 'utf8mb4',
      fields: []
    };
    designDataLocal.tables.push(newTable);
    selectedTable.value = newTable;
  }

  const newField: Field = {
    id: `field_${Date.now()}`,
    name: `field_${selectedTable.value.fields.length + 1}`,
    type: fieldType,
    comment: '',
    isPrimaryKey: false,
    isNotNull: false,
    isUnique: false
  };

  selectedTable.value.fields.push(newField);
  selectedField.value = newField;
};

// 选择表
const handleSelectTable = (table: Table) => {
  selectedTable.value = table;
  selectedField.value = null;
  emit('table-select', table);
};

// 选择字段
const handleSelectField = (field: Field) => {
  selectedField.value = field;
  emit('field-select', field);
};

// 更新表
const handleUpdateTable = (tableId: string, updates: Partial<Table>) => {
  const table = designDataLocal.tables.find((t) => t.id === tableId);
  if (table) {
    Object.assign(table, updates);
  }
};

// 更新字段
const handleUpdateField = (fieldId: string, updates: Partial<Field>) => {
  const table = designDataLocal.tables.find((t) => t.fields.some((f) => f.id === fieldId));
  if (table) {
    const field = table.fields.find((f) => f.id === fieldId);
    if (field) {
      Object.assign(field, updates);
    }
  }
};

// 添加关系
const handleAddRelation = (relation: Omit<Relation, 'id'>) => {
  const newRelation: Relation = {
    id: `relation_${Date.now()}`,
    ...relation
  };
  designDataLocal.relations.push(newRelation);
};

// 删除关系
const handleRemoveRelation = (relationId: string) => {
  const index = designDataLocal.relations.findIndex((r) => r.id === relationId);
  if (index > -1) {
    designDataLocal.relations.splice(index, 1);
  }
};

// 暴露方法给父组件
defineExpose({
  getDesignData: () => designDataLocal,
  clearSelection: () => {
    selectedTable.value = null;
    selectedField.value = null;
  }
});
</script>

<style lang="scss" scoped>
.table-designer {
  height: 100%;
  background: #f5f5f5;
}

.designer-layout {
  display: flex;
  height: 100%;
}

.left-panel {
  width: 250px;
  background: white;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
}

.center-panel {
  flex: 1;
  background: #fafafa;
  overflow: hidden;
  position: relative;
}

.right-panel {
  width: 300px;
  background: white;
  border-left: 1px solid #e4e7ed;
  overflow-y: auto;
}
</style>
