<template>
  <div class="img">
    <image-upload :disabled="disabled" v-model="localValue" @change="commonmomponent"></image-upload>
  </div>
</template>

<script lang="ts">
import { ref, watch, onMounted } from 'vue';
import ImageUpload from '@/components/ImageUpload/index.vue';

export default {
  name: 'nmImage',
  components: {
    ImageUpload
  },
  props: {
    nm: {
      type: Object,
      required: true
    },
    value: {
      type: [String, Array],
      required: false
    },
    disabled: {
      type: Boolean,
      default: false
    }
  },
  setup(props, { emit }) {
    const localValue = ref(props.value);

    // 监听 props.value 的变化，确保 localValue 与 props.value 保持同步
    watch(
      () => props.value,
      (newValue) => {
        localValue.value = newValue;
      }
    );

    // 监听 localValue 的变化，触发 update:value 事件
    watch(localValue, (newValue) => {
      emit('update:value', newValue);
    });

    // 处理 change 事件
    const commonmomponent = (nmInfo) => {
      localValue.value = nmInfo;
      emit('commonmomponent', { ...props.nm, commonDataInfo: localValue.value });
    };

    // 初始化时设置 localValue
    onMounted(() => {
      localValue.value = props.value;
    });

    return {
      localValue,
      commonmomponent
    };
  }
};
</script>

<style scoped>
.img {
  /* 样式可以根据需要调整 */
}
</style>
