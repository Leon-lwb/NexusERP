<template>
  <div class="canvas-panel">
    <div class="canvas-toolbar">
      <el-button type="primary" size="small" @click="handleAddTable"> 添加表 </el-button>
      <el-button :type="isAddingRelation ? 'success' : 'default'" size="small" @click="toggleAddRelation">
        {{ isAddingRelation ? '完成关系' : '添加关系' }}
      </el-button>
      <span v-if="isAddingRelation" class="hint">点击选择两个字段以建立关系</span>
    </div>

    <div class="canvas-content" ref="canvasRef">
      <div class="tables-container">
        <div
          v-for="table in tables"
          :key="table.id"
          class="table-card"
          :class="{ active: selectedTable?.id === table.id }"
          @click="handleSelectTable(table)"
        >
          <div class="table-header">
            <div class="table-name">{{ table.name }}</div>
            <div class="table-actions">
              <el-button type="text" size="small" @click.stop="handleEditTable(table)"> 编辑 </el-button>
              <el-button type="text" size="small" @click.stop="handleDeleteTable(table)"> 删除 </el-button>
            </div>
          </div>

          <div class="table-comment">{{ table.comment || '无注释' }}</div>

          <div class="fields-list">
            <div
              v-for="field in table.fields"
              :key="field.id"
              class="field-item"
              :id="getFieldDomId(table.id, field.id)"
              :class="{ active: selectedField?.id === field.id, linking: isAddingRelation && isSelectedInLink(table.id, field.id) }"
              @click.stop="handleFieldClick(table, field)"
              draggable="true"
              @dragstart="onFieldDragStart(table, field)"
              @dragover.prevent
              @drop="onFieldDrop(table, field)"
            >
              <div class="field-name">{{ field.name }}</div>
              <div class="field-type">{{ field.type }}</div>
              <div class="field-constraints">
                <el-tag v-if="field.isPrimaryKey" type="danger" size="small">PK</el-tag>
                <el-tag v-if="field.isNotNull" type="warning" size="small">NN</el-tag>
                <el-tag v-if="field.isUnique" type="success" size="small">UQ</el-tag>
              </div>
              <div class="field-actions">
                <el-button type="text" size="small" @click.stop="deleteField(table, field)">删除</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 关系线 -->
      <svg class="relations-svg" v-if="relationLines.length">
        <defs>
          <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#409eff" />
          </marker>
        </defs>
        <line
          v-for="line in relationLines"
          :key="line.id"
          :x1="line.x1"
          :y1="line.y1"
          :x2="line.x2"
          :y2="line.y2"
          stroke="#409eff"
          stroke-width="2"
          marker-end="url(#arrowhead)"
          style="cursor: pointer; pointer-events: visibleStroke"
          @click.stop="onLineClick(line.id)"
        />
      </svg>
    </div>
  </div>
</template>

<script setup name="CanvasPanel" lang="ts">
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

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

const props = defineProps<{
  tables: Table[];
  relations: Relation[];
  selectedTable?: Table | null;
  selectedField?: Field | null;
}>();

const emit = defineEmits<{
  selectTable: [table: Table];
  selectField: [field: Field];
  updateTable: [tableId: string, updates: Partial<Table>];
  updateField: [fieldId: string, updates: Partial<Field>];
  addRelation: [relation: Omit<Relation, 'id'>];
  removeRelation: [relationId: string];
  addTable: [table: Table];
  removeTable: [tableId: string];
}>();

const canvasRef = ref<HTMLElement>();
const isAddingRelation = ref(false);
const linkFrom = ref<{ tableId: string; fieldId: string } | null>(null);

const relationLines = ref<{ id: string; x1: number; y1: number; x2: number; y2: number }[]>([]);

const getFieldDomId = (tableId: string, fieldId: string) => `field-${tableId}-${fieldId}`;

const updateRelationLines = () => {
  const container = canvasRef.value;
  if (!container) return;
  const containerRect = container.getBoundingClientRect();
  const lines: typeof relationLines.value = [];

  props.relations.forEach((rel) => {
    const fromEl = document.getElementById(getFieldDomId(rel.fromTable, rel.fromField));
    const toEl = document.getElementById(getFieldDomId(rel.toTable, rel.toField));
    if (!fromEl || !toEl) return;
    const fromRect = fromEl.getBoundingClientRect();
    const toRect = toEl.getBoundingClientRect();

    const x1 = fromRect.left - containerRect.left + fromRect.width;
    const y1 = fromRect.top - containerRect.top + fromRect.height / 2;
    const x2 = toRect.left - containerRect.left;
    const y2 = toRect.top - containerRect.top + toRect.height / 2;

    lines.push({ id: rel.id, x1, y1, x2, y2 });
  });

  relationLines.value = lines;
};

const scheduleUpdateLines = () => nextTick(() => updateRelationLines());

watch(() => props.relations, scheduleUpdateLines, { deep: true });
watch(() => props.tables, scheduleUpdateLines, { deep: true });

onMounted(() => {
  scheduleUpdateLines();
  window.addEventListener('resize', scheduleUpdateLines);
  canvasRef.value?.addEventListener('scroll', scheduleUpdateLines, { passive: true } as any);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', scheduleUpdateLines);
  canvasRef.value?.removeEventListener('scroll', scheduleUpdateLines as any);
});

const handleAddTable = () => {
  const newTable: Table = {
    id: `table_${Date.now()}`,
    name: `table_${props.tables.length + 1}`,
    comment: '',
    engine: 'InnoDB',
    charset: 'utf8mb4',
    fields: [
      {
        id: `field_${Date.now()}_id`,
        name: 'id',
        type: 'bigint',
        comment: '主键',
        isPrimaryKey: true,
        isNotNull: true,
        isUnique: true
      }
    ]
  };
  emit('addTable', newTable);
  ElMessage.success('已添加新表');
  scheduleUpdateLines();
};

const toggleAddRelation = () => {
  isAddingRelation.value = !isAddingRelation.value;
  if (!isAddingRelation.value) linkFrom.value = null;
};

const handleSelectTable = (table: Table) => {
  emit('selectTable', table);
};

const handleFieldClick = (table: Table, field: Field) => {
  emit('selectField', field);

  if (!isAddingRelation.value) return;

  if (!linkFrom.value) {
    linkFrom.value = { tableId: table.id, fieldId: field.id };
    ElMessage.info(`已选择起点: ${table.name}.${field.name}`);
    return;
  }

  if (linkFrom.value.tableId === table.id && linkFrom.value.fieldId === field.id) {
    ElMessage.warning('请再选择另一个表的字段作为终点');
    return;
  }

  const relation = {
    fromTable: linkFrom.value.tableId,
    fromField: linkFrom.value.fieldId,
    toTable: table.id,
    toField: field.id
  };
  emit('addRelation', relation);
  ElMessage.success('已添加关系');

  linkFrom.value = null;
  isAddingRelation.value = false;
  scheduleUpdateLines();
};

const handleEditTable = (table: Table) => {
  ElMessage.info(`编辑表: ${table.name}`);
};

const handleDeleteTable = async (table: Table) => {
  try {
    await ElMessageBox.confirm(`确定要删除表 ${table.name} 吗？`, '删除表', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    });
    emit('removeTable', table.id);
    ElMessage.success('已删除表');
    scheduleUpdateLines();
  } catch (e) {
    // canceled
  }
};

const handleSelectField = (field: Field) => {
  emit('selectField', field);
};

const getRelationX1 = (relation: Relation) => 100;
const getRelationY1 = (relation: Relation) => 100;
const getRelationX2 = (relation: Relation) => 200;
const getRelationY2 = (relation: Relation) => 200;

const isSelectedInLink = (tableId: string, fieldId: string) => {
  return !!(linkFrom.value && linkFrom.value.tableId === tableId && linkFrom.value.fieldId === fieldId);
};

const dragState = ref<{ fromTableId: string; fromFieldId: string } | null>(null);

const onFieldDragStart = (table: Table, field: Field) => {
  dragState.value = { fromTableId: table.id, fromFieldId: field.id };
};

const onFieldDrop = (toTable: Table, toField: Field) => {
  if (!dragState.value) return;
  const { fromTableId, fromFieldId } = dragState.value;
  dragState.value = null;

  if (fromTableId !== toTable.id) return;

  const fields = Array.isArray(toTable.fields) ? [...toTable.fields] : [];
  const fromIndex = fields.findIndex((f) => f.id === fromFieldId);
  const toIndex = fields.findIndex((f) => f.id === toField.id);
  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return;

  const [moved] = fields.splice(fromIndex, 1);
  fields.splice(toIndex, 0, moved);
  emit('updateTable', toTable.id, { fields });
  scheduleUpdateLines();
};

const deleteField = (table: Table, field: Field) => {
  const next = (table.fields || []).filter((f) => f.id !== field.id);
  emit('updateTable', table.id, { fields: next });
  scheduleUpdateLines();
};

const onLineClick = async (relationId: string) => {
  try {
    await ElMessageBox.confirm('确定要删除这条关系吗？', '删除关系', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    });
    emit('removeRelation', relationId);
    ElMessage.success('已删除关系');
    scheduleUpdateLines();
  } catch (e) {
    // canceled
  }
};
</script>

<style lang="scss" scoped>
.canvas-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.canvas-toolbar {
  padding: 16px;
  background: white;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  gap: 8px;
  .hint {
    color: #909399;
    font-size: 12px;
  }
}

.canvas-content {
  flex: 1;
  position: relative;
  overflow: auto;
  background: #fafafa;
}

.tables-container {
  padding: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.table-card {
  background: white;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  min-width: 250px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: #409eff;
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.1);
  }

  &.active {
    border-color: #409eff;
    background: #f0f9ff;
  }

  .table-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;

    .table-name {
      font-size: 16px;
      font-weight: 500;
      color: #303133;
    }

    .table-actions {
      display: flex;
      gap: 4px;
      opacity: 0;
      transition: opacity 0.3s ease;
    }
  }

  &:hover .table-actions {
    opacity: 1;
  }

  .table-comment {
    font-size: 12px;
    color: #909399;
    margin-bottom: 12px;
  }

  .fields-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .field-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 8px;
    border-radius: 4px;
    cursor: pointer;
    transition: background 0.2s ease;

    &:hover {
      background: #f5f7fa;
    }

    &.active {
      background: #e3f2fd;
    }

    &.linking {
      outline: 1px dashed #67c23a;
      background: #f0f9eb;
    }

    .field-name {
      font-size: 14px;
      color: #303133;
      flex: 1;
    }

    .field-type {
      font-size: 12px;
      color: #909399;
      min-width: 60px;
    }

    .field-constraints {
      display: flex;
      gap: 2px;
    }

    .field-actions {
      margin-left: 8px;
      .el-button {
        padding: 0 4px;
      }
    }
  }
}

.relations-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}
</style>
