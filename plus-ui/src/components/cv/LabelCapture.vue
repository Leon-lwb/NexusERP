<template>
  <!--
    标签拍照识别组件（Web 端，测试用；最终 OCR 落地 App/PAD，本组件为同构先例）
    用法：识别成功后 emit('recognized', payload) 由调用方回填表单；识别结果可编辑/可放弃，不强制。
  -->
  <el-dialog v-model="visible" title="扫描物料标签 (OCR)" width="500px" append-to-body>
    <!-- 第一步：拍照/上传 -->
    <div v-if="!previewUrl" class="capture-upload">
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        accept="image/*"
        capture="environment"
        :on-change="onFileChange"
        :on-remove="onFileRemove"
        style="width: 100%"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拍照或上传料件标签<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">拍摄标签正面照,清晰包含 料号/批次/SN;浏览器需授权摄像头(HTTPS)</div>
        </template>
      </el-upload>
    </div>

    <!-- 拍照预览 + 识别 -->
    <div v-else class="capture-preview">
      <img :src="previewUrl" alt="标签照片" />
      <div class="capture-actions">
        <el-button @click="reset">重拍</el-button>
        <el-button type="primary" :loading="loading" @click="handleRecognize">识别</el-button>
      </div>
    </div>

    <!-- 识别结果 -->
    <div v-if="result" class="capture-result">
      <el-alert
        :type="result.matched ? 'success' : 'warning'"
        :title="result.matched ? '料号匹配物料主数据' : '未匹配物料主数据(识别结果可编辑)'"
        :closable="false"
        class="mb8"
      />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="料号">
          {{ result.materialCode || '未识别' }}
          <el-tag v-if="result.matchedMaterial" type="success" size="small" class="ml8">
            {{ result.matchedMaterial.materialName }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="批次号">{{ result.batchCode || '未识别' }}</el-descriptions-item>
        <el-descriptions-item label="序列号">{{ result.snCode || '未识别' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!result" @click="handleApply">使用此结果</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import type { UploadFile } from 'element-plus';
import { detectProduct } from '@/api/cv/detect';
import type { LabelParseResult } from '@/api/cv/detect';

/** 识别结果回填载荷（供调用方填表单字段） */
export interface LabelCapturePayload {
  materialCode?: string;
  materialName?: string;
  batchCode?: string;
  snCode?: string;
}

const emit = defineEmits<{
  (e: 'recognized', payload: LabelCapturePayload): void;
}>();

const visible = ref(false);
const file = ref<File | undefined>();
const previewUrl = ref('');
const loading = ref(false);
const result = ref<LabelParseResult | null>(null);

const onFileChange = (uploadFile: UploadFile) => {
  file.value = uploadFile.raw;
  previewUrl.value = uploadFile.raw ? URL.createObjectURL(uploadFile.raw) : '';
  result.value = null;
};

const onFileRemove = () => {
  file.value = undefined;
  previewUrl.value = '';
  result.value = null;
};

const reset = () => {
  file.value = undefined;
  previewUrl.value = '';
  result.value = null;
};

/** 调后端 detectProduct(enableLabelParse=true)，OCR 识别标签字段 */
const handleRecognize = async () => {
  if (!file.value) {
    ElMessage.warning('请先选择标签照片');
    return;
  }
  loading.value = true;
  try {
    const res = await detectProduct(file.value, true, true);
    const data = res.data;
    if (!data.label) {
      ElMessage.warning('未识别到标签内容');
      return;
    }
    result.value = data.label;
    if (!data.label.materialCode && !data.label.batchCode && !data.label.snCode) {
      ElMessage.warning('未提取到 料号/批次/SN,可换一张更清晰的照片');
    }
  } finally {
    loading.value = false;
  }
};

/** 回填表单并关闭 */
const handleApply = () => {
  if (!result.value) return;
  emit('recognized', {
    materialCode: result.value.materialCode,
    materialName: result.value.matchedMaterial?.materialName,
    batchCode: result.value.batchCode,
    snCode: result.value.snCode
  });
  visible.value = false;
  reset();
};

defineExpose({
  open: () => {
    visible.value = true;
    reset();
  }
});
</script>

<style scoped>
.capture-upload {
  display: flex;
  justify-content: center;
}
.capture-preview {
  text-align: center;
}
.capture-preview img {
  max-width: 100%;
  max-height: 320px;
  border-radius: 8px;
}
.capture-actions {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 8px;
}
.capture-result {
  margin-top: 12px;
}
.ml8 {
  margin-left: 8px;
}
</style>
