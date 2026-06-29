<template>
  <div class="app-container">
    <!-- 生成区 -->
    <el-card class="gen-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>图片生成</span>
          <el-tag v-if="currentModelName" type="info" size="small">模型：{{ currentModelName }}</el-tag>
        </div>
      </template>
      <el-form label-width="80px">
        <el-form-item label="提示词">
          <el-input
            v-model="form.prompt"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="描述你想要生成的图片，例如：一只在月光下的猫，写实风格"
          />
        </el-form-item>
        <el-form-item label="尺寸">
          <el-select v-model="form.size" style="width: 240px">
            <el-option
              v-for="opt in sizeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="generating" @click="handleGenerate">
            <el-icon><Picture /></el-icon>
            生成图片
          </el-button>
          <el-button v-if="lastUrl" @click="handleDownload">下载</el-button>
        </el-form-item>
      </el-form>

      <!-- 结果展示 -->
      <div v-if="lastUrl" class="result-area">
        <el-divider content-position="left">生成结果</el-divider>
        <div class="result-meta">提示词：{{ lastPrompt }}</div>
        <ImageRenderer :url="lastUrl" width="100%" height="420px" />
      </div>
    </el-card>

    <!-- 历史区 -->
    <el-card class="history-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>生成历史</span>
          <el-button link type="primary" @click="getList">刷新</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="historyList" empty-text="暂无生成记录">
        <el-table-column label="缩略图" width="100" align="center">
          <template #default="{ row }">
            <el-image
              :src="row.imageUrl"
              :preview-src-list="[row.imageUrl]"
              preview-teleported
              fit="cover"
              style="width: 72px; height: 72px; border-radius: 4px"
            />
          </template>
        </el-table-column>
        <el-table-column label="提示词" prop="prompt" show-overflow-tooltip min-width="200" />
        <el-table-column label="尺寸" prop="size" width="120" align="center" />
        <el-table-column label="模型" prop="modelName" width="140" align="center" />
        <el-table-column label="生成时间" prop="createTime" width="180" align="center" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts" name="AiImageGen">
import { ref, reactive, onMounted } from 'vue'
import { Picture } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImageRenderer from '@/components/ai/ImageRenderer.vue'
import { generateImage, listImage, delImage, type AiImageRecord } from '@/api/ai/image'

defineOptions({ name: 'AiImageGen' })

const generating = ref(false)
const loading = ref(false)
const lastUrl = ref('')
const lastPrompt = ref('')
const currentModelName = ref('')
const historyList = ref<AiImageRecord[]>([])
const total = ref(0)
const queryParams = reactive({ pageNum: 1, pageSize: 10 })

const form = reactive({
  prompt: '',
  size: '1280*1280'
})

const sizeOptions = [
  { label: '1024 × 1024（方形）', value: '1024*1024' },
  { label: '1280 × 1280（方形）', value: '1280*1280' },
  { label: '720 × 1280（竖版）', value: '720*1280' },
  { label: '1280 × 720（横版）', value: '1280*720' }
]

const handleGenerate = async () => {
  if (!form.prompt.trim()) {
    ElMessage.warning('请输入提示词')
    return
  }
  generating.value = true
  try {
    const res: any = await generateImage({ prompt: form.prompt, size: form.size })
    if (res.code === 200 && res.data) {
      lastUrl.value = res.data.imageUrl
      lastPrompt.value = res.data.prompt || form.prompt
      currentModelName.value = res.data.modelName || ''
      ElMessage.success('图片生成成功')
      getList()
    } else {
      ElMessage.error(res.msg || '图片生成失败')
    }
  } catch (e: any) {
    ElMessage.error('图片生成失败：' + (e.message || '未知错误'))
  } finally {
    generating.value = false
  }
}

const getList = async () => {
  loading.value = true
  try {
    const res: any = await listImage(queryParams)
    historyList.value = res.rows || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const handleView = (row: AiImageRecord) => {
  lastUrl.value = row.imageUrl
  lastPrompt.value = row.prompt
  if (row.modelName) currentModelName.value = row.modelName
}

const handleDelete = async (row: AiImageRecord) => {
  await ElMessageBox.confirm('确认删除该生成记录？删除后不可恢复。', '提示', {
    type: 'warning'
  })
  await delImage([row.id as number])
  ElMessage.success('删除成功')
  getList()
}

const handleDownload = () => {
  if (!lastUrl.value) return
  const link = document.createElement('a')
  link.href = lastUrl.value
  link.download = `ai-image-${Date.now()}.png`
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

onMounted(() => getList())
</script>

<style scoped>
.gen-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.result-area {
  margin-top: 8px;
}

.result-meta {
  margin-bottom: 8px;
  color: #606266;
  font-size: 13px;
}

.history-card {
  margin-top: 16px;
}
</style>
