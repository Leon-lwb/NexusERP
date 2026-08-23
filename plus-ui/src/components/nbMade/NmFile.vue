<template>
  <file-upload
    :disabled="disabled"
    v-model="localValue"
    @change="commonmomponent"
    @remove="handleRemove"
    @preview="handlePreview"
    @exceed="handleExceed"
    :before-remove="beforeRemove"
  ></file-upload>
</template>

<script>
import { ref, watch, onMounted } from 'vue';
import FileUpload from '@/components/FileUpload/index.vue';

export default {
  name: 'nmFile',
  components: {
    FileUpload
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
  },
  methods: {
    handleRemove(file, fileList) {},
    handlePreview(file) {},
    handleExceed(files, fileList) {
      this.$message.warning(`当前限制选择 3 个文件，本次选择了 ${files.length} 个文件，共选择了 ${files.length + fileList.length} 个文件`);
    },
    beforeRemove(file, fileList) {
      return this.$confirm(`确定移除 ${file.name}？`);
    }
  }
};
</script>

<style scoped></style>
