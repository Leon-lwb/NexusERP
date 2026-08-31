<template>
  <div class="sql-preview-panel">
    <div class="panel-header">
      <h3>SQL 预览</h3>
      <div class="header-actions">
        <el-button size="small" @click="handleCopy">复制</el-button>
        <el-button type="primary" size="small" @click="handleRefresh">刷新</el-button>
      </div>
    </div>

    <div class="panel-content">
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="10" animated />
      </div>

      <div v-else-if="!sqlContent" class="empty-container">
        <el-empty description="暂无 SQL 内容" />
      </div>

      <div v-else class="sql-container">
        <div class="sql-tabs">
          <el-tabs v-model="activeTab" @tab-change="handleTabChange">
            <el-tab-pane label="建表语句" name="create" />
            <el-tab-pane label="索引语句" name="index" />
            <el-tab-pane label="外键语句" name="foreign" />
            <el-tab-pane label="完整 SQL" name="full" />
          </el-tabs>
        </div>

        <div class="sql-content">
          <div class="sql-editor">
            <pre><code class="sql">{{ currentSqlContent }}</code></pre>
          </div>

          <div class="sql-stats">
            <el-descriptions :column="4" size="small">
              <el-descriptions-item label="表数量">
                {{ sqlStats.tableCount }}
              </el-descriptions-item>
              <el-descriptions-item label="字段数量">
                {{ sqlStats.fieldCount }}
              </el-descriptions-item>
              <el-descriptions-item label="索引数量">
                {{ sqlStats.indexCount }}
              </el-descriptions-item>
              <el-descriptions-item label="外键数量">
                {{ sqlStats.foreignKeyCount }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
      </div>
    </div>

    <div class="panel-footer">
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="success" @click="handleExport">导出 SQL</el-button>
      <el-button type="primary" @click="handleCreateTable">创建表</el-button>
    </div>
  </div>
</template>

<script setup name="SqlPreviewPanel" lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

interface SqlContent {
  createTables: string;
  createIndexes: string;
  createForeignKeys: string;
  fullSql: string;
}

interface SqlStats {
  tableCount: number;
  fieldCount: number;
  indexCount: number;
  foreignKeyCount: number;
}

const emit = defineEmits<{
  cancel: [];
  export: [sql: string];
  createTable: [sql: string];
}>();

const props = defineProps<{
  designData?: any;
}>();

const loading = ref(false);
const activeTab = ref('create');
const sqlContent = ref<SqlContent | null>(null);

// 当前显示的 SQL 内容
const currentSqlContent = computed(() => {
  if (!sqlContent.value) return '';

  switch (activeTab.value) {
    case 'create':
      return sqlContent.value.createTables;
    case 'index':
      return sqlContent.value.createIndexes;
    case 'foreign':
      return sqlContent.value.createForeignKeys;
    case 'full':
      return sqlContent.value.fullSql;
    default:
      return '';
  }
});

// SQL 统计信息
const sqlStats = computed<SqlStats>(() => {
  if (!props.designData) {
    return {
      tableCount: 0,
      fieldCount: 0,
      indexCount: 0,
      foreignKeyCount: 0
    };
  }

  const tables = props.designData.tables || [];
  const relations = props.designData.relations || [];

  let fieldCount = 0;
  let indexCount = 0;

  tables.forEach((table: any) => {
    if (table.fields) {
      fieldCount += table.fields.length;
      // 计算索引数量（主键 + 唯一键）
      table.fields.forEach((field: any) => {
        if (field.isPrimaryKey || field.isUnique) {
          indexCount++;
        }
      });
    }
  });

  return {
    tableCount: tables.length,
    fieldCount,
    indexCount,
    foreignKeyCount: relations.length
  };
});

// 生成模拟SQL（用于测试）
const generateMockSql = (designData: any) => {
  const tables = Array.isArray(designData?.tables) ? designData.tables : [];

  // 工具：判断是否为数值类型（默认值是否需要引号）
  const isNumericType = (type: string) => {
    const t = (type || '').toLowerCase();
    return ['int', 'integer', 'bigint', 'smallint', 'tinyint', 'mediumint', 'decimal', 'double', 'float', 'real'].some((k) => t.startsWith(k));
  };

  // 工具：判断是否允许长度（varchar(255)）
  const supportsLength = (type: string) => {
    const t = (type || '').toLowerCase();
    return ['char', 'varchar', 'binary', 'varbinary'].some((k) => t.startsWith(k));
  };

  // 工具：判断是否为定点小数（decimal(10,2)）
  const isDecimalType = (type: string) => {
    const t = (type || '').toLowerCase();
    return t.startsWith('decimal') || t.startsWith('numeric');
  };

  // 构建单表 SQL
  const buildCreateTableSql = (table: any) => {
    const tableName = table?.name || 'unknown_table';
    const engine = table?.engine || 'InnoDB';
    const charset = table?.charset || 'utf8mb4';
    const tableComment = table?.comment ? String(table.comment).replace(/'/g, "''") : '';

    const fields = Array.isArray(table?.fields) ? table.fields : [];

    const columnDefs: string[] = [];
    const primaryKeys: string[] = [];
    const uniqueKeys: string[] = [];

    fields.forEach((field: any) => {
      const name = field?.name || 'unnamed_col';
      const type = (field?.type || 'varchar').toLowerCase();
      const length = field?.length;
      const precision = field?.precision;
      const scale = field?.scale;
      const notNull = !!field?.isNotNull;
      const isPk = !!field?.isPrimaryKey;
      const isUnique = !!field?.isUnique;
      const defVal = field?.defaultValue;
      const comment = field?.comment;

      let typeDef = type;
      if (supportsLength(type)) {
        const effLen = length ? Number(length) : 255;
        typeDef += `(${effLen})`;
      } else if (isDecimalType(type) && (precision || scale)) {
        const p = Number(precision) || 10;
        const s = Number(scale) || 0;
        typeDef += `(${p},${s})`;
      }

      let col = `  \`${name}\` ${typeDef}`;
      if (notNull) col += ' NOT NULL';

      if (defVal !== undefined && defVal !== null && defVal !== '') {
        if (isNumericType(type)) {
          col += ` DEFAULT ${defVal}`;
        } else if (String(defVal).toUpperCase() === 'CURRENT_TIMESTAMP') {
          col += ' DEFAULT CURRENT_TIMESTAMP';
        } else {
          const safe = String(defVal).replace(/'/g, "''");
          col += ` DEFAULT '${safe}'`;
        }
      }

      if (comment) {
        const safeC = String(comment).replace(/'/g, "''");
        col += ` COMMENT '${safeC}'`;
      }

      columnDefs.push(col);

      if (isPk) primaryKeys.push(name);
      if (isUnique && !isPk) uniqueKeys.push(name);
    });

    // 组合主键
    if (primaryKeys.length > 0) {
      columnDefs.push(`  PRIMARY KEY (${primaryKeys.map((n) => `\`${n}\``).join(', ')})`);
    }

    // 唯一键（逐列唯一）
    uniqueKeys.forEach((n) => {
      const idxName = `uk_${tableName}_${n}`;
      columnDefs.push(`  UNIQUE KEY \`${idxName}\` (\`${n}\`)`);
    });

    let createSql = `-- 创建表 ${tableName}\n`;
    createSql += `CREATE TABLE \`${tableName}\` (\n`;
    createSql += columnDefs.join(',\n');
    createSql += `\n) ENGINE=${engine} DEFAULT CHARSET=${charset}`;
    if (tableComment) createSql += ` COMMENT='${tableComment}'`;
    createSql += ';\n\n';

    return createSql;
  };

  let createTables = '';
  const createIndexes = '';
  let createForeignKeys = '';

  tables.forEach((table: any) => {
    createTables += buildCreateTableSql(table);
  });

  // 外键：根据 relations 生成
  const relations = Array.isArray(designData?.relations) ? designData.relations : [];

  // 辅助：按表ID和字段ID查找
  const tableIdMap: Record<string, any> = {};
  const fieldIdMap: Record<string, any> = {};

  tables.forEach((t: any) => {
    tableIdMap[t?.id] = t;
    if (Array.isArray(t.fields)) {
      t.fields.forEach((f: any) => {
        fieldIdMap[f?.id] = f;
      });
    }
  });

  const getTableById = (tableId: string) => tableIdMap[tableId];
  const getFieldById = (fieldId: string) => fieldIdMap[fieldId];

  const normalizeType = (type: string) => {
    if (!type) return '';
    const lower = String(type).toLowerCase();
    return lower.split('(')[0].trim();
  };

  relations.forEach((rel: any, i: number) => {
    const fromTableId = rel?.fromTable;
    const fromFieldId = rel?.fromField;
    const toTableId = rel?.toTable;
    const toFieldId = rel?.toField;

    if (fromTableId && fromFieldId && toTableId && toFieldId) {
      const fromTable = getTableById(fromTableId);
      const fromField = getFieldById(fromFieldId);
      const toTable = getTableById(toTableId);
      const toField = getFieldById(toFieldId);

      if (!fromTable || !fromField || !toTable || !toField) {
        return;
      }

      const fromBase = normalizeType(fromField.type || 'varchar');
      const toBase = normalizeType(toField.type || 'varchar');
      if (fromBase !== toBase) {
        return;
      }

      const fkName = `fk_${fromTable.name}_${fromField.name}_${i}`;
      createForeignKeys += `ALTER TABLE \`${fromTable.name}\` ADD CONSTRAINT \`${fkName}\` FOREIGN KEY (\`${fromField.name}\`) REFERENCES \`${toTable.name}\`(\`${toField.name}\`);\n`;
    }
  });
  if (createForeignKeys) createForeignKeys += '\n';

  const fullSql = createTables + createIndexes + createForeignKeys;

  return {
    createTables,
    createIndexes,
    createForeignKeys,
    fullSql
  };
};

// 生成 SQL 内容
const generateSqlContent = async (designData: any) => {
  if (!designData || !designData.tables?.length) {
    sqlContent.value = null;
    return;
  }

  try {
    loading.value = true;
    // 直接生成模拟SQL，避免API调用问题
    sqlContent.value = generateMockSql(designData);
  } catch (error) {
    console.error('生成 SQL 失败:', error);
    sqlContent.value = null;
  } finally {
    loading.value = false;
  }
};

// 监听设计数据变化，生成 SQL
watch(
  () => props.designData,
  (newData) => {
    if (newData) {
      nextTick(() => {
        generateSqlContent(newData);
      });
    } else {
      sqlContent.value = null;
    }
  },
  { immediate: true }
);

// 切换标签页
const handleTabChange = (tabName: string) => {
  activeTab.value = tabName;
};

// 复制 SQL
const handleCopy = async () => {
  if (!currentSqlContent.value) {
    ElMessage.warning('没有可复制的内容');
    return;
  }

  try {
    await navigator.clipboard.writeText(currentSqlContent.value);
    ElMessage.success('复制成功');
  } catch (error) {
    console.error('复制失败:', error);
    ElMessage.error('复制失败');
  }
};

// 刷新 SQL
const handleRefresh = () => {
  if (props.designData) {
    generateSqlContent(props.designData);
  }
};

// 导出 SQL（弹出文件名对话框并下载）
const handleExport = async () => {
  if (!currentSqlContent.value && !sqlContent.value?.fullSql) {
    ElMessage.warning('没有可导出的内容');
    return;
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入导出的文件名（例如：schema.sql）', '导出 SQL', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: 'schema.sql',
      inputPattern: /.+\.(sql|txt)$/i,
      inputErrorMessage: '文件名需以 .sql 或 .txt 结尾'
    });
    const filename = value || 'schema.sql';
    const data = activeTab.value === 'full' ? sqlContent.value?.fullSql || '' : currentSqlContent.value;
    const blob = new Blob([data || ''], { type: 'text/sql;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    ElMessage.success('导出成功');
  } catch (e) {
    // 取消不提示错误
  }
};

// 创建表（始终使用完整 SQL 执行）
const handleCreateTable = () => {
  const sql = sqlContent.value?.fullSql || currentSqlContent.value;
  if (!sql) {
    ElMessage.warning('没有可执行的 SQL');
    return;
  }
  emit('createTable', sql);
};

// 取消
const handleCancel = () => {
  emit('cancel');
};

// 暴露方法给父组件
defineExpose({
  generateSqlContent,
  refresh: handleRefresh,
  getFullSql: () => sqlContent.value?.fullSql || ''
});
</script>

<style lang="scss" scoped>
.sql-preview-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.panel-header {
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

  .header-actions {
    display: flex;
    gap: 8px;
  }
}

.panel-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.loading-container {
  padding: 20px;
}

.empty-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

.sql-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sql-tabs {
  background: white;
  border-bottom: 1px solid #e4e7ed;

  :deep(.el-tabs__header) {
    margin: 0;
    padding: 0 16px;
  }

  :deep(.el-tabs__nav-wrap) {
    padding: 0;
  }
}

.sql-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sql-editor {
  flex: 1;
  background: #1e1e1e;
  color: #d4d4d4;
  overflow: auto;
  padding: 16px;

  pre {
    margin: 0;
    white-space: pre-wrap;
    word-wrap: break-word;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 13px;
    line-height: 1.5;
  }

  .sql {
    color: #d4d4d4;
  }
}

.sql-stats {
  background: white;
  padding: 16px;
  border-top: 1px solid #e4e7ed;

  :deep(.el-descriptions) {
    .el-descriptions__label {
      font-weight: 500;
      color: #606266;
    }

    .el-descriptions__content {
      color: #303133;
    }
  }
}

.panel-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px;
  background: white;
  border-top: 1px solid #e4e7ed;
}

:deep(.el-skeleton) {
  .el-skeleton__item {
    margin-bottom: 12px;
  }
}
</style>
