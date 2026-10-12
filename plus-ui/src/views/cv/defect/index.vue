<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="defect-header">
          <span>工业缺陷检测</span>
          <el-tag>{{ selectedScenario.label }}</el-tag>
        </div>
      </template>

      <el-row :gutter="24">
        <el-col :xs="24" :lg="12">
          <el-form label-position="top">
            <el-form-item label="检测场景">
              <el-radio-group v-model="scenario" :disabled="loading">
                <el-radio-button v-for="option in defectScenarios" :key="option.value" :value="option.value">
                  {{ option.label }}
                </el-radio-button>
              </el-radio-group>
              <div class="field-hint">{{ selectedScenario.description }}</div>
            </el-form-item>
            <el-form-item label="本地图片">
              <el-upload
                ref="uploadRef"
                drag
                class="image-upload"
                :auto-upload="false"
                :show-file-list="false"
                :disabled="loading"
                accept="image/jpeg,image/png,image/bmp,image/webp"
                :on-change="onFileChange"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">拖拽单张图片到此处或<em>点击选择</em></div>
                <template #tip>
                  <div class="el-upload__tip">支持 JPEG、PNG、BMP、WebP，单张不超过 10 MB。选择后点击「开始检测」。</div>
                </template>
              </el-upload>
            </el-form-item>
            <div v-if="file" class="file-info">
              <span class="file-name">{{ file.name }}</span>
              <span v-if="imageNatural.width">原图 {{ imageNatural.width }} × {{ imageNatural.height }} 像素</span>
              <el-button link type="danger" @click="clearImage">移除图片</el-button>
            </div>
            <el-form-item>
              <el-button
                v-hasPermi="['cv:detect:query']"
                type="primary"
                icon="Search"
                :loading="loading"
                :disabled="!file || !imageNatural.width"
                @click="handleDetect"
              >
                {{ loading ? '正在检测' : '开始检测' }}
              </el-button>
            </el-form-item>
          </el-form>

          <el-alert v-if="errorMessage" class="result-alert" :title="errorMessage" type="error" show-icon :closable="false" />
          <div v-if="previewUrl" class="preview-frame" v-loading="loading" element-loading-text="正在进行缺陷推理…">
            <div class="image-stage">
              <img :key="previewUrl" ref="imageRef" :src="previewUrl" :alt="file?.name || '待检测图片'" @load="onImageLoad" @error="onImageError" />
              <div
                v-for="(item, index) in result?.detections || []"
                :key="index"
                class="defect-box"
                :style="defectBoxStyle(item, imageNatural)"
                :title="`${defectClassLabel(item.className, scenario)} ${(item.confidence * 100).toFixed(1)}% · 原图坐标 [${item.bbox.join(', ')}]`"
              >
                <span class="defect-box-label"
                  >{{ index + 1 }}. {{ defectClassLabel(item.className, scenario) }} {{ (item.confidence * 100).toFixed(1) }}%</span
                >
              </div>
            </div>
          </div>
          <el-empty v-else description="请选择与检测场景对应的本地图片" />
          <p v-if="previewUrl" class="field-hint">检测框基于原图像素坐标显示，随图片等比缩放。悬停检测框可查看原始坐标。</p>
        </el-col>

        <el-col :xs="24" :lg="12">
          <div class="result-heading">检测结果</div>
          <template v-if="result">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="检测场景">{{ selectedScenario.label }}</el-descriptions-item>
              <el-descriptions-item label="检测图片">{{ file?.name }}</el-descriptions-item>
              <el-descriptions-item label="缺陷总数">{{ result.count }}</el-descriptions-item>
              <el-descriptions-item label="推理耗时">{{ result.processTimeMs.toFixed(2) }} ms</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ completedAt }}</el-descriptions-item>
            </el-descriptions>
            <el-alert
              class="result-alert"
              :type="result.count > 0 ? 'warning' : 'info'"
              :title="result.count > 0 ? `本次共检出 ${result.count} 处缺陷，请结合原图复核` : '本次模型未检出缺陷'"
              :closable="false"
              show-icon
            />
            <el-table :data="summaryRows" border empty-text="本次模型未检出缺陷">
              <el-table-column label="缺陷类别" min-width="155">
                <template #default="{ row }">
                  <div>{{ row.classLabel }}</div>
                  <div v-if="row.classLabel !== row.className" class="class-code">{{ row.className }}</div>
                </template>
              </el-table-column>
              <el-table-column label="最高置信度" width="110" align="right">
                <template #default="{ row }">{{ (row.confidence * 100).toFixed(1) }}%</template>
              </el-table-column>
              <el-table-column label="数量" prop="count" width="75" align="right" />
              <el-table-column label="本次推理耗时 (ms)" min-width="155" align="right">
                <template #default="{ row }">{{ row.processTimeMs.toFixed(2) }}</template>
              </el-table-column>
            </el-table>
            <p class="field-hint">按模型返回的类别汇总，置信度为同类检测框的最高值；耗时为本次完整推理的总耗时。</p>
          </template>
          <el-empty v-else :description="loading ? `正在检测${selectedScenario.label}，请稍候` : '上传图片并开始检测后查看结果'" />
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="CvDefectDetect">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { UploadFilled } from '@element-plus/icons-vue';
import type { UploadFile, UploadInstance } from 'element-plus';
import { detectDefect, defectScenarios } from '@/api/cv/defect';
import type { DefectDetectResult, DefectScenario } from '@/api/cv/defect';
import { defectBoxStyle, defectClassLabel, detectionErrorMessage, summarizeDefects, validateDefectResult } from './defectViewModel';
import type { ImageSize } from './defectViewModel';

const scenario = ref<DefectScenario>('defect_metal');
const selectedScenario = computed(() => defectScenarios.find((item) => item.value === scenario.value) || defectScenarios[0]);
const file = ref<File>();
const uploadRef = ref<UploadInstance>();
const imageRef = ref<HTMLImageElement>();
const imageNatural = ref<ImageSize>({ width: 0, height: 0 });
const previewUrl = ref('');
const result = ref<DefectDetectResult>();
const summaryRows = computed(() => summarizeDefects(result.value));
const loading = ref(false);
const errorMessage = ref('');
const completedAt = ref('');
let requestVersion = 0;

function resetResult() {
  requestVersion++;
  result.value = undefined;
  completedAt.value = '';
  errorMessage.value = '';
  loading.value = false;
}

function clearImage() {
  resetResult();
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
  file.value = undefined;
  imageNatural.value = { width: 0, height: 0 };
  uploadRef.value?.clearFiles();
}

function onFileChange(uploadFile: UploadFile) {
  const raw = uploadFile.raw;
  clearImage();
  if (!raw) return;
  if (!['image/jpeg', 'image/png', 'image/bmp', 'image/webp'].includes(raw.type)) {
    errorMessage.value = '请选择 JPEG、PNG、BMP 或 WebP 图片文件';
    return;
  }
  if (raw.size === 0 || raw.size > 10 * 1024 * 1024) {
    errorMessage.value = '图片文件不能为空，且单张不能超过 10 MB';
    return;
  }
  file.value = raw;
  previewUrl.value = URL.createObjectURL(raw);
}

function onImageLoad() {
  imageNatural.value = {
    width: imageRef.value?.naturalWidth || 0,
    height: imageRef.value?.naturalHeight || 0
  };
}

function onImageError() {
  clearImage();
  errorMessage.value = '图片无法解码，请选择有效的图片文件';
}

async function handleDetect() {
  if (loading.value) return;
  if (!file.value || !imageNatural.value.width || !imageNatural.value.height) {
    errorMessage.value = '请先选择图片并等待图片加载完成';
    return;
  }
  resetResult();
  const version = requestVersion;
  const selectedFile = file.value;
  const selected = scenario.value;
  loading.value = true;
  try {
    const response = await detectDefect(selectedFile, selected);
    if (version !== requestVersion) return;
    validateDefectResult(response.data, selected);
    result.value = response.data;
    completedAt.value = new Date().toLocaleString('zh-CN', { hour12: false });
  } catch (error) {
    if (version === requestVersion) errorMessage.value = detectionErrorMessage(error);
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}

watch(scenario, resetResult, { flush: 'sync' });
onBeforeUnmount(clearImage);
</script>

<style scoped>
.defect-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.image-upload {
  width: 100%;
}
.field-hint {
  width: 100%;
  margin: 8px 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.file-info {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  font-size: 13px;
}
.file-name {
  overflow-wrap: anywhere;
}
.preview-frame {
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}
.image-stage {
  position: relative;
  width: 100%;
}
.image-stage img {
  display: block;
  width: 100%;
  height: auto;
}
.defect-box {
  position: absolute;
  box-sizing: border-box;
  border: 2px solid var(--el-color-danger);
}
.defect-box-label {
  position: absolute;
  top: 0;
  left: 0;
  padding: 1px 4px;
  color: #fff;
  background: var(--el-color-danger);
  font-size: 11px;
  line-height: 16px;
  white-space: nowrap;
}
.result-heading {
  margin-bottom: 16px;
  font-weight: 600;
}
.result-alert {
  margin-bottom: 16px;
  margin-top: 16px;
}
.class-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
