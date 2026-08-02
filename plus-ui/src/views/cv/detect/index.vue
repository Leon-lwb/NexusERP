<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="clearfix">
          <span>物料检测调试 - OCR / YOLO</span>
          <el-button type="primary" plain style="float: right" @click="handleMaterialDetect" :loading="loading">YOLO 通用检测</el-button>
        </div>
      </template>

      <el-row :gutter="16">
        <!-- 左侧: 上传 + 图片预览 + 检测框 -->
        <el-col :span="12">
          <el-form label-width="120px">
            <el-form-item label="标签照片">
              <el-upload drag :auto-upload="false" :limit="1" accept="image/*" :on-change="onFileChange" :on-remove="() => (file = undefined)">
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">拖拽照片到此处或<em>点击选择</em></div>
                <template #tip>
                  <div class="el-upload__tip">料件标签/条码照片,JPEG/PNG,≤10MB</div>
                </template>
              </el-upload>
            </el-form-item>
            <el-form-item label="识别选项">
              <el-checkbox v-model="enableOcr">启用 OCR 文本</el-checkbox>
              <el-checkbox v-model="enableLabelParse">解析料号/批次/SN(交叉校验物料主数据)</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleDetect" :loading="loading" :disabled="!file">识别</el-button>
              <span v-if="processTimeMs" style="margin-left: 8px; color: #909399; font-size: 12px">耗时 {{ processTimeMs }}ms</span>
            </el-form-item>
          </el-form>

          <!-- 图片 + YOLO 检测框叠加 -->
          <div v-if="previewUrl" class="preview-box" :class="{ loading: loading }">
            <img :src="previewUrl" ref="imageRef" @load="onImageLoad" alt="标签照片" />
            <div
              v-for="(d, i) in drawBoxes"
              :key="i"
              class="det-box"
              :style="boxStyle(d)"
              :title="`${d.className} ${(d.confidence * 100).toFixed(1)}%`"
            >
              <span class="det-label">{{ d.className }} {{ (d.confidence * 100).toFixed(1) }}%</span>
            </div>
            <div v-if="loading" class="preview-mask">
              <el-icon class="is-loading"><Loading /></el-icon>
            </div>
          </div>
          <el-empty v-else description="请先选择标签照片" />
        </el-col>

        <!-- 右侧: 识别结果 -->
        <el-col :span="12">
          <el-alert
            v-if="labelParse"
            :type="labelParse.matched ? 'success' : 'warning'"
            :title="labelParse.matched ? '料号匹配物料主数据' : '未匹配物料主数据'"
            :closable="false"
            class="mb8"
          />
          <el-tabs v-model="activeTab">
            <!-- 标签解析 -->
            <el-tab-pane label="标签解析" name="label">
              <el-descriptions v-if="labelParse" :column="1" border>
                <el-descriptions-item label="料号">
                  <template v-if="labelParse.materialCode">
                    {{ labelParse.materialCode }}
                    <el-tag v-if="labelParse.matchedMaterial" type="success" size="small" class="ml8">
                      {{ labelParse.matchedMaterial.materialName }}
                    </el-tag>
                    <el-tag v-else type="warning" size="small" class="ml8">主数据未找到</el-tag>
                  </template>
                  <span v-else class="text-muted">未提取</span>
                </el-descriptions-item>
                <el-descriptions-item label="批次号">{{ labelParse.batchCode || '未提取' }}</el-descriptions-item>
                <el-descriptions-item label="序列号">{{ labelParse.snCode || '未提取' }}</el-descriptions-item>
                <el-descriptions-item label="OCR 原文">
                  <ul class="ocr-lines">
                    <li v-for="(t, i) in labelParse.rawTexts" :key="i">{{ t }}</li>
                  </ul>
                </el-descriptions-item>
              </el-descriptions>
              <el-empty v-else description="请上传照片识别,并勾选「解析料号/批次/SN」" />
            </el-tab-pane>

            <!-- OCR 文本 -->
            <el-tab-pane label="OCR 文本" name="ocr">
              <el-table v-if="ocrResults && ocrResults.length" :data="ocrResults" size="small" border>
                <el-table-column label="文本" prop="text" min-width="200" show-overflow-tooltip />
                <el-table-column label="置信度" width="100" align="right">
                  <template #default="scope">
                    <span :style="{ color: confidenceColor(scope.row.confidence) }"> {{ (scope.row.confidence * 100).toFixed(1) }}% </span>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="未识别到文本(未勾选 OCR 或照片无文字)" />
            </el-tab-pane>

            <!-- YOLO 检测 -->
            <el-tab-pane label="YOLO 检测" name="det">
              <el-table v-if="detections && detections.length" :data="detections" size="small" border>
                <el-table-column label="类别" prop="className" min-width="120" />
                <el-table-column label="置信度" width="100" align="right">
                  <template #default="scope">
                    <span :style="{ color: confidenceColor(scope.row.confidence) }"> {{ (scope.row.confidence * 100).toFixed(1) }}% </span>
                  </template>
                </el-table-column>
                <el-table-column label="边界框" width="160">
                  <template #default="scope">{{ scope.row.bbox?.join(', ') }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="未检测到目标" />
            </el-tab-pane>

            <!-- 原始 JSON -->
            <el-tab-pane label="原始 JSON" name="raw">
              <pre class="raw-json">{{ rawJson }}</pre>
            </el-tab-pane>
          </el-tabs>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { UploadFilled, Loading } from '@element-plus/icons-vue';
import type { UploadFile } from 'element-plus';
import { detectProduct, detectMaterial, ProductDetectResult, MaterialDetectResult, DetectionItem, OcrItem, LabelParseResult } from '@/api/cv/detect';

const file = ref<File | undefined>();
const previewUrl = ref('');
const loading = ref(false);
const enableOcr = ref(true);
const enableLabelParse = ref(true);
const processTimeMs = ref<number>(0);
const imageRef = ref<HTMLImageElement | null>(null);
const imageNatural = ref({ width: 0, height: 0 });
const activeTab = ref('label');
const detections = ref<DetectionItem[]>([]);
const ocrResults = ref<OcrItem[]>([]);
const labelParse = ref<LabelParseResult | null>(null);
const rawJson = ref('');

const drawBoxes = computed(() => detections.value || []);

function onFileChange(uploadFile: UploadFile) {
  file.value = uploadFile.raw;
  if (uploadFile.raw) {
    previewUrl.value = URL.createObjectURL(uploadFile.raw);
  }
  // 清空上次结果
  resetResult();
}

function onImageLoad() {
  if (imageRef.value) {
    imageNatural.value = {
      width: imageRef.value.naturalWidth,
      height: imageRef.value.naturalHeight
    };
  }
}

function boxStyle(d: DetectionItem) {
  const { width, height } = imageNatural.value;
  if (!width || !height || !d.bbox || d.bbox.length < 4) {
    return { display: 'none' };
  }
  const [x1, y1, x2, y2] = d.bbox;
  return {
    left: (x1 / width) * 100 + '%',
    top: (y1 / height) * 100 + '%',
    width: ((x2 - x1) / width) * 100 + '%',
    height: ((y2 - y1) / height) * 100 + '%',
    borderColor: d.confidence >= 0.5 ? '#67c23a' : '#e6a23c'
  };
}

function confidenceColor(v: number | undefined) {
  if (v == null) return '';
  return v >= 0.5 ? '#67c23a' : '#e6a23c';
}

function resetResult() {
  detections.value = [];
  ocrResults.value = [];
  labelParse.value = null;
  rawJson.value = '';
  processTimeMs.value = 0;
  activeTab.value = 'label';
}

function handleDetect() {
  if (!file.value) {
    ElMessage.warning('请先选择标签照片');
    return;
  }
  loading.value = true;
  detectProduct(file.value, enableOcr.value, enableLabelParse.value)
    .then((res) => {
      const data = res.data;
      detections.value = data.detections || [];
      ocrResults.value = data.ocrResults || [];
      labelParse.value = data.label || null;
      processTimeMs.value = data.processTimeMs || 0;
      rawJson.value = JSON.stringify(data, null, 2);
      if (!data.success) {
        ElMessage.warning('识别未成功');
      }
    })
    .finally(() => (loading.value = false));
}

function handleMaterialDetect() {
  if (!file.value) {
    ElMessage.warning('请先选择标签照片');
    return;
  }
  loading.value = true;
  detectMaterial(file.value)
    .then((res) => {
      const data = res.data as MaterialDetectResult;
      detections.value = data.detections || [];
      ocrResults.value = [];
      labelParse.value = null;
      processTimeMs.value = data.processTimeMs || 0;
      rawJson.value = JSON.stringify(data, null, 2);
    })
    .finally(() => (loading.value = false));
}
</script>

<style scoped>
.preview-box {
  position: relative;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;
}
.preview-box img {
  display: block;
  width: 100%;
}
.preview-box.loading {
  opacity: 0.6;
}
.preview-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.5);
  font-size: 32px;
  color: var(--el-color-primary);
}
.det-box {
  position: absolute;
  border: 2px solid #67c23a;
  box-sizing: border-box;
}
.det-label {
  position: absolute;
  top: -20px;
  left: -2px;
  background: #67c23a;
  color: #fff;
  font-size: 11px;
  line-height: 16px;
  padding: 0 4px;
  white-space: nowrap;
  border-radius: 2px;
}
.text-muted {
  color: #909399;
}
.ml8 {
  margin-left: 8px;
}
.ocr-lines {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
}
.raw-json {
  max-height: 420px;
  overflow: auto;
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  margin: 0;
}
</style>
