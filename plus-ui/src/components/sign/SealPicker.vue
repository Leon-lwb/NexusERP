<template>
  <!--
    选章组件：列出「当前用户在指定业务场景下可用」的印章。
    可用性由后端实时判定（个人章仅归属本人 + 公司章按授权范围/有效期/状态筛选），前端只做选择。
    用法：<SealPicker v-model="sealId" biz-type="APPROVAL" />；父组件可在授权配置变更后调用 reload() 刷新。
  -->
  <div v-loading="loading" class="seal-picker">
    <el-radio-group v-if="seals.length" v-model="selected" class="seal-options">
      <el-radio v-for="seal in seals" :key="seal.sealId" :value="String(seal.sealId)" border class="seal-option">
        <div class="seal-option__body">
          <!--
            只渲染后端下发的预览图地址：该字段由 @Translation(OSS_ID_TO_URL) 按 previewOssId 翻译，
            而 P1 尚未生成水印预览图（previewOssId 通常为 null），此时直接不展示图片，
            不额外调用预览接口（印章原图无任何对外接口），仅展示名称 + 类型。
          -->
          <el-image
            v-if="seal.previewUrl"
            :src="seal.previewUrl"
            :preview-src-list="[seal.previewUrl]"
            preview-teleported
            fit="contain"
            class="seal-option__img"
          />
          <div class="seal-option__meta">
            <div class="seal-option__name">{{ seal.sealName }}</div>
            <el-tag size="small" :type="sealTypeTag(seal.sealType)">{{ sealTypeText(seal.sealType) }}</el-tag>
          </div>
        </div>
      </el-radio>
    </el-radio-group>
    <el-empty v-else-if="!loading" :image-size="60" description="当前用户在该场景下没有可用印章" />
  </div>
</template>

<script setup lang="ts">
import { listAvailableSeal, type AvailableSealVO } from '@/api/system/sign/seal';

const props = defineProps<{
  /** 业务场景（后端 SignBizType，如 APPROVAL / SO_CONTRACT） */
  bizType: string;
}>();

/** 选中印章 ID（字符串：后端雪花 ID 序列化为字符串） */
const selected = defineModel<string | undefined>({ default: undefined });

const seals = ref<AvailableSealVO[]>([]);
const loading = ref(false);

/** 印章类型（后端 SealType） */
const SEAL_TYPE_TEXT: Record<string, string> = { '1': '个人章', '2': '公司章' };
const sealTypeText = (value?: string) => SEAL_TYPE_TEXT[value ?? ''] ?? '-';
/** 公司章需授权才可用，风险级高亮；个人章仅本人可用，信息级 */
const sealTypeTag = (value?: string): ElTagType => (value === '2' ? 'danger' : 'info');

/** 拉取当前用户可用印章（仅一个时自动选中，省掉一次无意义点击；原选中项不可用时清空） */
const reload = async () => {
  loading.value = true;
  try {
    const { data } = await listAvailableSeal(props.bizType);
    const list = data ?? [];
    seals.value = list;
    if (list.length === 1) {
      selected.value = String(list[0].sealId);
    } else if (selected.value && !list.some((seal) => String(seal.sealId) === selected.value)) {
      // 切换业务场景后原有选择可能已不在可用范围，必须清空，避免用出无权的章
      selected.value = undefined;
    }
  } finally {
    loading.value = false;
  }
};

watch(() => props.bizType, reload, { immediate: true });

defineExpose({ reload });
</script>

<style scoped>
.seal-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.seal-option {
  height: auto;
  margin-right: 0;
  padding: 8px 12px;
}
.seal-option__body {
  display: flex;
  align-items: center;
  gap: 8px;
}
.seal-option__img {
  width: 48px;
  height: 48px;
}
.seal-option__name {
  font-weight: 600;
}
</style>
