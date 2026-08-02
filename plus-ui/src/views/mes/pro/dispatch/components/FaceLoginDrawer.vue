<template>
  <el-drawer v-model="visible" title="操作台人脸验证" size="440px" append-to-body>
    <!-- 第一步: 拍照/上传 -->
    <div v-if="!photo" class="capture-step">
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        accept="image/*"
        capture="user"
        :on-change="onFileChange"
        :on-remove="() => (file = undefined)"
        style="width: 100%"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拍照或上传人脸照片<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">使用摄像头拍摄本人正面照;浏览器需授权摄像头(HTTPS)</div>
        </template>
      </el-upload>
    </div>

    <!-- 拍照预览 -->
    <div v-else class="photo-preview">
      <img :src="photo" alt="人脸照片" />
      <div class="photo-actions">
        <el-button @click="resetPhoto">重新拍摄</el-button>
        <el-button type="primary" :loading="identifying" @click="handleIdentify">开始识别</el-button>
      </div>
    </div>

    <!-- 候选选择(1:N top-3 → 手选 → 1:1 确认) -->
    <div v-if="candidates.length" class="candidates">
      <el-divider content-position="left">识别到 {{ candidates.length }} 位候选,请选择本人</el-divider>
      <div
        v-for="c in candidates"
        :key="c.subject"
        class="candidate-item"
        :class="{ active: selected?.subject === c.subject }"
        @click="selectCandidate(c)"
      >
        <div class="candidate-main">
          <div class="candidate-name">{{ c.userName || c.employeeId || c.subject }}</div>
          <div class="candidate-sub">
            {{ c.subject }}<span v-if="c.workshopName"> / {{ c.workshopName }}</span>
          </div>
        </div>
        <div class="candidate-sim">{{ (c.similarity * 100).toFixed(1) }}%</div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="selected" type="primary" :loading="confirming" :disabled="confirmDisabled" @click="handleConfirm"> 确认身份(1:1) </el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import type { UploadFile } from 'element-plus';
import { opsIdentify } from '@/api/cv/detect';
import { verifyFace, FaceMatchItem } from '@/api/cv/face';
import type { OpsCandidate } from '@/api/cv/detect';

const emit = defineEmits<{
  (e: 'verified', payload: { subject: string; workerId?: string | null; userName?: string; similarity: number }): void;
}>();

const visible = ref(false);
const file = ref<File | undefined>();
const photo = ref('');
const identifying = ref(false);
const confirming = ref(false);
const candidates = ref<OpsCandidate[]>([]);
const selected = ref<OpsCandidate | null>(null);
const confirmDisabled = ref(false);

const onFileChange = (uploadFile: UploadFile) => {
  file.value = uploadFile.raw;
  photo.value = uploadFile.raw ? URL.createObjectURL(uploadFile.raw) : '';
  candidates.value = [];
  selected.value = null;
  confirmDisabled.value = false;
};

const resetPhoto = () => {
  file.value = undefined;
  photo.value = '';
  candidates.value = [];
  selected.value = null;
  confirmDisabled.value = false;
};

const handleIdentify = async () => {
  if (!file.value) {
    ElMessage.warning('请先拍摄或上传人脸照片');
    return;
  }
  identifying.value = true;
  try {
    const res = await opsIdentify(file.value, 3);
    candidates.value = res.data || [];
    selected.value = candidates.value.length === 1 ? candidates.value[0] : null;
    if (!candidates.value.length) {
      ElMessage.warning('未识别到可用候选,请调整光线重试');
    }
  } finally {
    identifying.value = false;
  }
};

const selectCandidate = (c: OpsCandidate) => {
  selected.value = c;
};

/**
 * 1:1 确认:手选候选后用同一照片做 verify,相似度 ≥ 服务端阈值(0.5)且 matched=true 才通过。
 * 不单凭 1:N 结果签发身份(防照片/双胞胎攻击)。
 */
const handleConfirm = async () => {
  if (!file.value || !selected.value) return;
  confirming.value = true;
  try {
    const res = await verifyFace(file.value, selected.value.subject);
    const v = res.data;
    if (v.matched && v.similarity >= 0.5) {
      ElMessage.success(`验证通过 ${selected.value.userName || selected.value.subject}(相似度 ${(v.similarity * 100).toFixed(1)}%)`);
      emit('verified', {
        subject: selected.value.subject,
        workerId: selected.value.workerId,
        userName: selected.value.userName,
        similarity: v.similarity
      });
      visible.value = false;
      resetPhoto();
    } else {
      confirmDisabled.value = true;
      ElMessage.error(`验证未通过(相似度 ${(v.similarity * 100).toFixed(1)}%,阈值 50%)`);
    }
  } finally {
    confirming.value = false;
  }
};

defineExpose({
  open: () => {
    visible.value = true;
    resetPhoto();
  }
});
</script>

<style scoped>
.capture-step {
  display: flex;
  justify-content: center;
}
.photo-preview {
  text-align: center;
}
.photo-preview img {
  max-width: 100%;
  max-height: 340px;
  border-radius: 8px;
}
.photo-actions {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 8px;
}
.candidates {
  margin-top: 12px;
}
.candidate-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.candidate-item:hover {
  border-color: var(--el-color-primary);
}
.candidate-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.candidate-name {
  font-weight: 600;
}
.candidate-sub {
  font-size: 12px;
  color: #909399;
}
.candidate-sim {
  font-weight: 600;
  color: var(--el-color-success);
}
</style>
