<template>
  <div class="table-design-list">
    <div class="page-header">
      <div class="header-left">
        <h2>表设计器</h2>
        <p class="page-description">管理数据库表设计，支持图形化设计和代码生成</p>
      </div>
      <div class="header-right">
        <el-button type="primary" icon="Plus" @click="handleCreate"> 新建设计 </el-button>
      </div>
    </div>

    <div class="page-content">
      <!-- 搜索区域 -->
      <div class="search-container">
        <el-form :model="searchForm" inline>
          <el-form-item label="设计名称">
            <el-input v-model="searchForm.designName" placeholder="请输入设计名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="表名">
            <el-input v-model="searchForm.tableName" placeholder="请输入表名" clearable style="width: 200px" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleSearch"> 搜索 </el-button>
            <el-button icon="Refresh" @click="handleReset"> 重置 </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 表格区域 -->
      <div class="table-container">
        <el-table v-loading="loading" :data="tableData" stripe style="width: 100%" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" />
          <el-table-column prop="designName" label="设计名称" min-width="150">
            <template #default="{ row }">
              <el-link type="primary" @click="handleView(row)">
                {{ row.designName }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column prop="tableName" label="表名" min-width="120">
            <template #default="{ row }">
              <div class="table-names">
                <el-tag v-for="(table, index) in getTableNames(row.tableName)" :key="index" size="small" class="table-tag">
                  {{ table }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="tableComment" label="表备注" min-width="150">
            <template #default="{ row }">
              <div class="table-comments">
                <el-tag v-for="(comment, index) in getTableComments(row.tableComment)" :key="index" size="small" type="info" class="comment-tag">
                  {{ comment }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.updateTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="handleEdit(row)"> 编辑 </el-button>
              <el-button type="success" size="small" @click="handlePreview(row)"> 预览 </el-button>
              <el-button type="warning" size="small" @click="handleGenerateSql(row)"> SQL </el-button>
              <el-button type="info" size="small" @click="handleImportToGen(row)"> 导入 </el-button>
              <el-button type="danger" size="small" @click="handleDelete(row)"> 删除 </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :page-sizes="[10, 20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </div>
    </div>

    <!-- 预览对话框 -->
    <el-dialog v-model="previewVisible" title="表结构预览" width="80%" top="5vh" :close-on-click-modal="false" :show-close="true">
      <div class="preview-dialog-body">
        <PreviewPanel v-if="previewData" :design-data="previewData" @close="previewVisible = false" />
      </div>
    </el-dialog>

    <!-- SQL预览对话框 -->
    <el-dialog v-model="sqlVisible" title="SQL预览" width="80%" :close-on-click-modal="false" top="5vh">
      <div class="sql-dialog-body">
        <SqlPreviewPanel v-if="sqlData" :design-data="sqlData" ref="sqlPreviewRef" @cancel="sqlVisible = false" @createTable="onCreateTableFromSql" />
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="sqlVisible = false">取消</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 创建表三按钮确认对话框 -->
    <el-dialog v-model="createConfirm.visible" title="创建表确认" width="500px" :close-on-click-modal="false">
      <div>
        选择操作：
        <br />
        - 创建：直接执行建表（存在同名表会失败）
        <br />
        - 覆盖创建：先删除已有表，再创建新表
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createConfirm.visible = false">取消</el-button>
          <el-button type="primary" plain @click="createWithoutOverwrite">创建</el-button>
          <el-button type="primary" @click="createWithOverwrite">覆盖创建</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TableDesignList" lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getDesignList, deleteDesign, getDesignDetail, importToGenTable, createTable } from '@/api/online/db';
import PreviewPanel from './components/PreviewPanel.vue';
import SqlPreviewPanel from './components/SqlPreviewPanel.vue';

const router = useRouter();

// 搜索表单
const searchForm = reactive({
  designName: '',
  tableName: ''
});

// 表格数据
const tableData = ref<any[]>([]);
const loading = ref(false);
const selectedRows = ref<any[]>([]);

// 分页
const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
});

// 对话框
const previewVisible = ref(false);
const previewData = ref<any | null>(null);
const sqlVisible = ref(false);
const sqlData = ref<any | null>(null);
const sqlPreviewRef = ref();

// 创建表确认对话框
const createConfirm = reactive({
  visible: false,
  sql: ''
});

// 获取列表数据
const getList = async () => {
  try {
    loading.value = true;
    const params = {
      pageNum: pagination.current,
      pageSize: pagination.size,
      designName: searchForm.designName,
      tableName: searchForm.tableName
    };

    const response = await getDesignList(params);
    if (response.code === 200) {
      tableData.value = response.rows || response.data || [];
      pagination.total = response.total || 0;
    } else {
      ElMessage.error(response.msg || '获取列表失败');
    }
  } catch (error) {
    console.error('获取列表失败:', error);
    ElMessage.error('获取列表失败');
  } finally {
    loading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  pagination.current = 1;
  getList();
};

// 重置
const handleReset = () => {
  searchForm.designName = '';
  searchForm.tableName = '';
  pagination.current = 1;
  getList();
};

// 新建
const handleCreate = () => {
  router.push('/online/db');
};

// 查看
const handleView = (row: any) => {
  router.push({
    path: '/online/db',
    query: { id: row.designId || row.id }
  });
};

// 编辑
const handleEdit = (row: any) => {
  router.push({
    path: '/online/db',
    query: { id: row.designId || row.id }
  });
};

// 预览
const handlePreview = async (row: any) => {
  try {
    const response = await getDesignDetail(row.designId || row.id);
    if (response.code === 200) {
      const entity = response.data;
      const contentStr = entity?.content;
      let designData: any = {};
      try {
        designData = contentStr ? JSON.parse(contentStr) : {};
      } catch {
        designData = {};
      }
      // 确保数据结构完整，和 SQL 预览一致
      designData.designId = entity.designId || entity.id || designData.designId;
      designData.id = designData.designId;
      designData.designName = entity.designName || designData.designName;
      designData.tableName = entity.tableName || designData.tableName;
      designData.tableComment = entity.tableComment || designData.tableComment;
      if (!Array.isArray(designData.tables)) designData.tables = [];
      if (!Array.isArray(designData.relations)) designData.relations = [];

      previewData.value = designData;
      previewVisible.value = true;
    } else {
      ElMessage.error(response.msg || '获取设计详情失败');
    }
  } catch (error) {
    console.error('获取设计详情失败:', error);
    ElMessage.error('获取设计详情失败');
  }
};

// 生成SQL
const handleGenerateSql = async (row: any) => {
  try {
    const response = await getDesignDetail(row.designId || row.id);
    if (response.code === 200) {
      const entity = response.data;
      const contentStr = entity?.content;
      let designData: any = {};

      try {
        designData = contentStr ? JSON.parse(contentStr) : {};
      } catch {
        designData = {};
      }

      // 确保数据结构完整
      designData.designId = entity.designId || entity.id || designData.designId;
      designData.id = designData.designId;
      designData.designName = entity.designName || designData.designName;
      designData.tableName = entity.tableName || designData.tableName;
      designData.tableComment = entity.tableComment || designData.tableComment;
      if (!Array.isArray(designData.tables)) designData.tables = [];
      if (!Array.isArray(designData.relations)) designData.relations = [];

      sqlData.value = designData;
      sqlVisible.value = true;
    } else {
      ElMessage.error(response.msg || '获取设计详情失败');
    }
  } catch (error) {
    console.error('获取设计详情失败:', error);
    ElMessage.error('获取设计详情失败');
  }
};

// 从 SQL 预览发起创建表
const onCreateTableFromSql = async (sql: string) => {
  if (!sql) return;
  createConfirm.sql = sql;
  createConfirm.visible = true;
};

const createWithoutOverwrite = async () => {
  try {
    if (!createConfirm.sql) return;
    await createTable({ sql: createConfirm.sql, overwriteIfExists: false });
    ElMessage.success('表创建成功');
  } catch (e: any) {
    ElMessage.error('表已经存在，创建失败！');
  } finally {
    createConfirm.visible = false;
  }
};

const createWithOverwrite = async () => {
  try {
    if (!createConfirm.sql) return;
    await createTable({ sql: createConfirm.sql, overwriteIfExists: true });
    ElMessage.success('表创建成功');
  } catch (e) {
    ElMessage.error('创建表失败');
  } finally {
    createConfirm.visible = false;
  }
};

// 导入到代码生成
const handleImportToGen = async (row: any) => {
  try {
    await ElMessageBox.confirm(`将设计 "${row.designName}" 导入到代码生成。可能会覆盖已存在的同名表，是否继续？`, '导入确认', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });

    const response = await getDesignDetail(row.designId || row.id);
    if (response.code === 200) {
      const entity = response.data;
      const contentStr = entity?.content;

      if (!contentStr) {
        ElMessage.error('设计内容为空，无法导入');
        return;
      }

      // 解析content并重新序列化，确保数据结构正确
      let designData;
      try {
        designData = JSON.parse(contentStr);
      } catch (e) {
        ElMessage.error('设计数据格式错误');
        return;
      }

      // 与表设计器保持一致：传递JSON.stringify(designData)
      const importResponse = await importToGenTable({
        content: JSON.stringify(designData),
        dataName: 'master',
        overwriteIfExists: true
      });

      if (importResponse.code === 200) {
        ElMessage.success('导入成功');
      } else {
        ElMessage.error(importResponse.msg || '导入失败');
      }
    } else {
      ElMessage.error(response.msg || '获取设计详情失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('导入失败:', error);
      ElMessage.error('导入失败');
    }
  }
};

// 删除
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除设计 "${row.designName}" 吗？此操作不可恢复！`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });

    const response = await deleteDesign(row.designId || row.id);
    if (response.code === 200) {
      ElMessage.success('删除成功');
      getList();
    } else {
      ElMessage.error(response.msg || '删除失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error('删除失败');
    }
  }
};

// 选择变化
const handleSelectionChange = (selection: any[]) => {
  selectedRows.value = selection;
};

// 分页大小变化
const handleSizeChange = (size: number) => {
  pagination.size = size;
  pagination.current = 1;
  getList();
};

// 当前页变化
const handleCurrentChange = (current: number) => {
  pagination.current = current;
  getList();
};

// 工具函数
const getTableNames = (tableName: string) => {
  if (!tableName) return [];
  return tableName
    .split(',')
    .map((name) => name.trim())
    .filter((name) => name);
};

const getTableComments = (tableComment: string) => {
  if (!tableComment) return [];
  return tableComment
    .split(',')
    .map((comment) => comment.trim())
    .filter((comment) => comment);
};

const formatDate = (date: string) => {
  if (!date) return '-';
  return new Date(date).toLocaleString('zh-CN');
};

// 初始化
onMounted(() => {
  getList();
});
</script>

<style scoped>
/* 让 SQL 对话框内容有高度限制并可滚动，跟设计器一致 */
.sql-dialog-body {
  max-height: 70vh;
  overflow: hidden;
}
.sql-dialog-body :deep(.sql-preview-panel) {
  height: 60vh;
}

.table-design-list {
  padding: 16px;
  background: #f5f5f5;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.header-left h2 {
  margin: 0 0 8px 0;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
}

.page-description {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.page-content {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.search-container {
  padding: 20px;
  border-bottom: 1px solid #ebeef5;
}

.table-container {
  padding: 0 20px 20px;
}

.table-names {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.table-tag {
  margin: 0;
}

.table-comments {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.comment-tag {
  margin: 0;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

:deep(.el-table) {
  border-radius: 8px;
}

:deep(.el-table th) {
  background: #fafafa;
  color: #606266;
  font-weight: 600;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: #fafafa;
}
</style>
