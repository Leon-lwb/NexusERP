<template>
  <div class="table-designer-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" icon="Plus" @click="handleNew">新建表</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" icon="Document" @click="handleSave" :disabled="!currentDesign">保存设计</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="info" icon="View" @click="handlePreview" :disabled="!currentDesign">预览</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" icon="DocumentCopy" @click="handleGenerateSql" :disabled="!currentDesign">生成SQL</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" icon="MagicStick" @click="handleCreateTableUnified" :disabled="!currentDesign">创建表</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Upload" @click="handleImportToGenTable" :disabled="!currentDesign">导入到online设计</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button icon="FolderOpened" @click="handleOpenDesign">打开设计</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 设计器主体 -->
    <div class="designer-main" v-if="currentDesign">
      <TableDesigner 
        ref="designerRef"
        :design-data="currentDesign"
        @design-change="handleDesignChange"
        @field-select="handleFieldSelect"
        @table-select="handleTableSelect"
      />
    </div>

    <!-- 空状态 -->
    <div class="empty-state" v-else>
      <el-empty description="请新建表设计或打开已有设计">
        <el-button type="primary" @click="handleNew">新建表设计</el-button>
      </el-empty>
    </div>

    <!-- 预览对话框 -->
    <el-dialog 
      v-model="previewDialog.visible" 
      title="表结构预览" 
      width="80%" 
      top="5vh"
      :close-on-click-modal="false"
      :show-close="true"
    >
      <div class="preview-dialog-body">
        <PreviewPanel :design-data="currentDesign" @close="previewDialog.visible = false" />
      </div>
    </el-dialog>

    <!-- SQL预览对话框 -->
    <el-dialog 
      v-model="sqlDialog.visible" 
      title="生成的SQL" 
      width="80%" 
      top="5vh"
      :close-on-click-modal="false"
      :destroy-on-close="false"
    >
      <div class="sql-dialog-body">
        <SqlPreviewPanel 
          :design-data="currentDesign" 
          ref="sqlPreviewRef"
          @cancel="sqlDialog.visible = false"
          @createTable="onCreateTableFromSql"
        />
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="sqlDialog.visible = false">取消</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 创建表三按钮确认对话框 -->
    <el-dialog 
      v-model="createConfirm.visible"
      title="创建表确认"
      width="500px"
      :close-on-click-modal="false"
    >
      <div>
        检测到可能已存在同名表，选择操作：
        <br />
        - 创建：直接执行建表语句（存在同名表会失败）
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

    <!-- 打开设计对话框 -->
    <el-dialog 
      v-model="openDialog.visible" 
      title="打开表设计" 
      width="60%"
    >
      <OpenDesignPanel @open="handleDesignSelected" />
    </el-dialog>
  </div>
</template>

<script setup name="TableDesigner" lang="ts">
import { ref, reactive, onMounted, getCurrentInstance, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ComponentInternalInstance } from 'vue'
import TableDesigner from './components/TableDesigner.vue'
import PreviewPanel from './components/PreviewPanel.vue'
import SqlPreviewPanel from './components/SqlPreviewPanel.vue'
import OpenDesignPanel from './components/OpenDesignPanel.vue'
import { saveTableDesign, generateSql, createTable, importToGenTable, getDesignList, getDesignDetail } from '@/api/online/db'
import { useRoute } from 'vue-router'

const { proxy } = getCurrentInstance() as ComponentInternalInstance

// 当前设计数据
const currentDesign = ref<any>(null)
const designerRef = ref()
const route = useRoute()

// 对话框状态
const previewDialog = reactive({
  visible: false
})

const sqlDialog = reactive({
  visible: false
})

const openDialog = reactive({
  visible: false
})

// 生成的SQL
const generatedSql = ref('')
const sqlPreviewRef = ref()

// 创建表三按钮确认对话框状态
const createConfirm = reactive({
  visible: false,
  sql: ''
})

// 处理从 SQL 预览创建表（统一走三按钮确认对话框）
const onCreateTableFromSql = async (sql: string) => {
  if (!sql) return
  createConfirm.sql = sql
  createConfirm.visible = true
}

const createWithoutOverwrite = async () => {
  try {
    if (!createConfirm.sql) return
    await createTable({ sql: createConfirm.sql, overwriteIfExists: false })
    ElMessage.success('表创建成功')
  } catch (e: any) {
    const msg = (e && (e.msg || e.message)) ? String(e.msg || e.message).toLowerCase() : ''
    // 更友好的提示：默认认为失败多为已存在表导致
    if (msg.includes('exists') || msg.includes('already') || msg.includes('duplicate')) {
      ElMessage.error('表已经存在，创建失败！')
    } else {
      ElMessage.error('表已经存在，创建失败！')
    }
  } finally {
    createConfirm.visible = false
  }
}

const createWithOverwrite = async () => {
  try {
    if (!createConfirm.sql) return
    await createTable({ sql: createConfirm.sql, overwriteIfExists: true })
    ElMessage.success('表创建成功')
  } catch (e) {
    ElMessage.error('创建表失败')
  } finally {
    createConfirm.visible = false
  }
}

// 新建表设计
const handleNew = () => {
  currentDesign.value = {
    designName: '',
    tableName: '',
    tableComment: '',
    tables: [],
    relations: [],
    createTime: new Date().toISOString()
  }
}

// 保存设计
const handleSave = async () => {
  if (!currentDesign.value) return
  try {
    const { value } = await ElMessageBox.prompt('请输入设计名称', '保存表设计', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: currentDesign.value.designName || '',
      inputValidator: (value) => {
        if (!value || value.trim() === '') {
          return '设计名称不能为空'
        }
        return true
      }
    })

    currentDesign.value.designName = value

    // 若顶层未填写表名/备注，则从第一个表同步
    if ((!currentDesign.value.tableName || !currentDesign.value.tableName.trim()) && Array.isArray(currentDesign.value.tables) && currentDesign.value.tables.length > 0) {
      currentDesign.value.tableName = currentDesign.value.tables[0].name || ''
    }
    if ((!currentDesign.value.tableComment || !currentDesign.value.tableComment.trim()) && Array.isArray(currentDesign.value.tables) && currentDesign.value.tables.length > 0) {
      currentDesign.value.tableComment = currentDesign.value.tables[0].comment || ''
    }

    const payload = {
      id: currentDesign.value.id || currentDesign.value.designId,
      designName: currentDesign.value.designName,
      tableName: (Array.isArray(currentDesign.value.tables) ? currentDesign.value.tables.map((t: any) => t?.name).filter((n: any) => !!n && String(n).trim() !== '').join(',') : currentDesign.value.tableName),
      tableComment: (Array.isArray(currentDesign.value.tables) ? currentDesign.value.tables.map((t: any) => t?.comment).filter((c: any) => !!c && String(c).trim() !== '').join(',') : currentDesign.value.tableComment),
      content: JSON.stringify(currentDesign.value)
    }

    const res: any = await saveTableDesign(payload)
    const newId = res?.data ?? res?.id
    if (newId) {
      currentDesign.value.designId = newId
      currentDesign.value.id = newId
    }
    ElMessage.success('保存成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('保存失败')
    }
  }
}

// 预览
const handlePreview = () => {
  previewDialog.visible = true
}

// 生成SQL
const handleGenerateSql = async () => {
  sqlDialog.visible = true
  // 等待对话框渲染后刷新子组件的SQL
  setTimeout(() => {
    sqlPreviewRef.value?.refresh && sqlPreviewRef.value.refresh()
  }, 0)
}

// 创建表
const handleCreateTable = async () => {
  try {
    await ElMessageBox.confirm('确定要创建表吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    // 后端要求传入 sql
    const genResp: any = await generateSql({ content: JSON.stringify(currentDesign.value) })
    const fullSql = genResp?.data?.fullSql || genResp?.fullSql
    if (!fullSql) throw new Error('SQL 生成失败')

    await createTable({ sql: fullSql })
    ElMessage.success('表创建成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('创建表失败')
    }
  }
}

// 导入到代码生成(online设计)
const handleImportToGenTable = async () => {
  try {
    const { value } = await ElMessageBox.confirm('确定要导入到online设计吗？可能会覆盖已存在的同名表，是否继续？', '导入确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await importToGenTable({ content: JSON.stringify(currentDesign.value), dataName: 'master', overwriteIfExists: true })
    ElMessage.success('导入成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('导入失败')
    }
  }
}

// 打开设计
const handleOpenDesign = () => {
  openDialog.visible = true
}

// 设计变更
const handleDesignChange = (design: any) => {
  currentDesign.value = design
}

// 字段选择
const handleFieldSelect = (field: any) => {
}

// 表选择
const handleTableSelect = (table: any) => {
}

// 设计选择（从打开面板）
const handleDesignSelected = async (design: any) => {
  try {
    const id = design?.id || design?.designId
    if (!id) return
    const resp: any = await getDesignDetail(id)
    const entity = resp?.data || resp
    const contentStr = entity?.content
    let data: any = {}
    try {
      data = contentStr ? JSON.parse(contentStr) : {}
    } catch {
      data = {}
    }
    // 兜底顶层元数据
    data.designId = entity.designId || entity.id || data.designId
    data.id = data.designId
    data.designName = entity.designName || data.designName
    data.tableName = entity.tableName || data.tableName
    data.tableComment = entity.tableComment || data.tableComment
    if (!Array.isArray(data.tables)) data.tables = []
    if (!Array.isArray(data.relations)) data.relations = []

    currentDesign.value = data
    openDialog.visible = false
  } catch (e) {
    ElMessage.error('打开设计失败')
  }
}

// 根据路由参数加载设计
const loadDesignByRoute = async () => {
  const id = route.query?.id as string
  if (!id) return
  try {
    const resp: any = await getDesignDetail(id)
    const entity = resp?.data || resp
    const contentStr = entity?.content
    let data: any = {}
    try {
      data = contentStr ? JSON.parse(contentStr) : {}
    } catch {
      data = {}
    }
    data.designId = entity.designId || entity.id || data.designId
    data.id = data.designId
    data.designName = entity.designName || data.designName
    data.tableName = entity.tableName || data.tableName
    data.tableComment = entity.tableComment || data.tableComment
    if (!Array.isArray(data.tables)) data.tables = []
    if (!Array.isArray(data.relations)) data.relations = []
    currentDesign.value = data
  } catch (e) {
    ElMessage.error('加载设计失败')
  }
}

// 当 SQL 对话框打开且设计变化时，自动刷新 SQL 预览
watch(currentDesign, () => {
  if (sqlDialog.visible) {
    nextTick(() => {
      sqlPreviewRef.value?.refresh && sqlPreviewRef.value.refresh()
    })
  }
}, { deep: true })

// 监听路由 id 变化
watch(() => route.query.id, () => {
  loadDesignByRoute()
})

onMounted(() => {
  // 初始化：如果带了 id，则加载设计
  loadDesignByRoute()
})

// 统一的创建表：复用 SQL 预览的完整 SQL 生成规则
const handleCreateTableUnified = async () => {
  if (!currentDesign.value) return
  try {
    // 打开 SQL 对话框以确保生成最新 SQL，然后直接取子组件 fullSql
    sqlDialog.visible = true
    await nextTick()
    await nextTick()
    const fullSql = sqlPreviewRef.value?.getFullSql?.() || ''
    if (!fullSql) {
      // 强制刷新再取一次
      sqlPreviewRef.value?.refresh?.()
      await nextTick()
      await nextTick()
      const fullSql2 = sqlPreviewRef.value?.getFullSql?.() || ''
      if (!fullSql2) {
        ElMessage.error('SQL 生成失败')
        return
      }
      // 弹出三按钮对话框
      createConfirm.sql = fullSql2
      createConfirm.visible = true
      return
    }
    // 弹出三按钮对话框
    createConfirm.sql = fullSql
    createConfirm.visible = true
  } catch (e) {
    ElMessage.error('创建表失败')
  }
}
</script>

<style lang="scss" scoped>
.table-designer-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.toolbar {
  background: white;
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.designer-main {
  flex: 1;
  overflow: hidden;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  margin: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.mb8 {
  margin-bottom: 8px;
}

/* 限制 SQL 对话框高度并支持滚动 */
.sql-dialog-body {
  max-height: 70vh;
  overflow: hidden;
}
.sql-dialog-body :deep(.sql-preview-panel) {
  height: 60vh;
}

/* 预览对话框主体不滚动，滚动交由内部表结构区域控制 */
.preview-dialog-body {}
.preview-dialog-body :deep(.preview-panel) {}
</style>
