<template>
  <div class="preview-panel">
    <div class="preview-header">
      <h3>预览</h3>
      <div>
        <el-button type="primary" size="small" @click="handleRefresh">刷新</el-button>
        <el-button size="small" @click="emit('close')">关闭</el-button>
      </div>
    </div>
    <div class="preview-content">
      <div v-if="!previewData" class="empty-preview">
        <el-empty description="暂无预览数据" />
      </div>
      <div v-else class="preview-data">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="设计名称">
            {{ previewData.designName || '未命名' }}
          </el-descriptions-item>
          <el-descriptions-item label="表名">
            {{ previewData.tableName || '未设置' }}
          </el-descriptions-item>
          <el-descriptions-item label="表注释">
            {{ previewData.tableComment || '无' }}
          </el-descriptions-item>
          <el-descriptions-item label="表数量">
            {{ previewData.tables?.length || 0 }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="previewData.tables?.length" class="tables-section">
          <h4>表结构预览</h4>
          <div class="tables-scroll">
            <div v-for="(table, index) in previewData.tables" :key="index" class="table-preview">
              <el-card shadow="hover">
                <template #header>
                  <div class="table-header">
                    <span class="table-name">{{ table.name }}</span>
                    <el-tag type="primary" size="small">{{ table.engine || 'InnoDB' }}</el-tag>
                  </div>
                </template>
                <div class="table-comment">{{ table.comment || '无注释' }}</div>
                <div class="fields-preview">
                  <el-table :data="table.fields" size="small" border>
                    <el-table-column prop="name" label="字段名" width="120" />
                    <el-table-column prop="type" label="类型" width="100" />
                    <el-table-column prop="length" label="长度" width="80" />
                    <el-table-column prop="precision" label="精度" width="80" />
                    <el-table-column prop="scale" label="小数位" width="80" />
                    <el-table-column prop="defaultValue" label="默认值" width="100" />
                    <el-table-column prop="comment" label="注释" min-width="120" />
                    <el-table-column label="约束" width="120">
                      <template #default="{ row }">
                        <el-tag v-if="row.isPrimaryKey" type="danger" size="small">PK</el-tag>
                        <el-tag v-if="row.isNotNull" type="warning" size="small">NN</el-tag>
                        <el-tag v-if="row.isUnique" type="success" size="small">UQ</el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </el-card>
            </div>
          </div>
        </div>

        <div v-if="previewData.relations?.length" class="relations-preview">
          <h4>关系预览</h4>
          <div v-for="(relation, index) in previewData.relations" :key="index" class="relation-preview">
            <el-tag type="info"> {{ relation.fromTable }}.{{ relation.fromField }} → {{ relation.toTable }}.{{ relation.toField }} </el-tag>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup name="PreviewPanel" lang="ts">
import { ref, watch, nextTick } from 'vue';
const emit = defineEmits(['close']);

interface PreviewData {
  designName?: string;
  tableName?: string;
  tableComment?: string;
  tables?: Array<{
    name: string;
    comment?: string;
    engine?: string;
    charset?: string;
    fields: Array<{
      name: string;
      type: string;
      length?: number;
      precision?: number;
      scale?: number;
      defaultValue?: string;
      comment?: string;
      isPrimaryKey?: boolean;
      isNotNull?: boolean;
      isUnique?: boolean;
    }>;
  }>;
  relations?: Array<{
    fromTable: string;
    fromField: string;
    toTable: string;
    toField: string;
  }>;
}

const props = defineProps<{
  designData?: any;
}>();

const previewData = ref<PreviewData | null>(null);

// 监听设计数据变化，更新预览
watch(
  () => props.designData,
  (newData) => {
    if (newData) {
      nextTick(() => {
        updatePreview(newData);
      });
    } else {
      previewData.value = null;
    }
  },
  { immediate: true, deep: true }
);

const updatePreview = (data: any) => {
  try {
    if (!data) {
      previewData.value = null;
      return;
    }

    previewData.value = {
      designName: data.designName || '',
      tableName: data.tableName || '',
      tableComment: data.tableComment || '',
      tables: (data.tables || []).map((table: any) => ({
        name: table.name || '',
        comment: table.comment || '',
        engine: table.engine || 'InnoDB',
        charset: table.charset || 'utf8mb4',
        fields: (table.fields || []).map((field: any) => ({
          name: field.name || '',
          type: field.type || '',
          length: field.length,
          precision: field.precision,
          scale: field.scale,
          defaultValue: field.defaultValue || '',
          comment: field.comment || '',
          isPrimaryKey: field.isPrimaryKey || false,
          isNotNull: field.isNotNull || false,
          isUnique: field.isUnique || false
        }))
      })),
      relations: (data.relations || []).map((relation: any) => ({
        fromTable: relation.fromTable || '',
        fromField: relation.fromField || '',
        toTable: relation.toTable || '',
        toField: relation.toField || ''
      }))
    };
  } catch (error) {
    console.error('更新预览数据失败:', error);
    previewData.value = null;
  }
};

const handleRefresh = () => {
  if (props.designData) {
    updatePreview(props.designData);
  }
};

// 暴露方法给父组件
defineExpose({
  updatePreview,
  refresh: handleRefresh
});
</script>

<style lang="scss" scoped>
.preview-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: white;
  border-bottom: 1px solid #e4e7ed;

  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 500;
  }
}

.preview-content {
  flex: 1;
  padding: 16px;
}

.empty-preview {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

.preview-data {
  .tables-section {
    margin-top: 20px;

    h4 {
      margin: 0 0 12px 0;
      font-size: 14px;
      font-weight: 500;
      color: #606266;
    }

    .tables-scroll {
      max-height: 40vh;
      overflow: auto;
    }

    .tables-scroll .table-preview {
      margin-bottom: 16px;

      .table-header {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .table-name {
          font-weight: 500;
          color: #303133;
        }
      }

      .table-comment {
        margin: 8px 0;
        color: #909399;
        font-size: 12px;
      }

      .fields-preview {
        margin-top: 12px;
      }
    }
  }

  .relations-preview {
    margin-top: 20px;

    h4 {
      margin: 0 0 16px 0;
      font-size: 14px;
      font-weight: 500;
      color: #606266;
    }

    .relation-preview {
      margin-bottom: 8px;
    }
  }
}

:deep(.el-descriptions) {
  .el-descriptions__label {
    font-weight: 500;
  }
}

:deep(.el-table) {
  .el-table__header {
    th {
      background: #fafafa;
      font-weight: 500;
    }
  }
}
</style>
