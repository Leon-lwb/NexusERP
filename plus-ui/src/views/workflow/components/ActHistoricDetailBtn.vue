<style lang="less"></style>
<template>
  <span>
    <el-button :type="btnType" @click="history()">{{ text }}</el-button>
    <el-dialog title="审批历史" v-model="modalLsVisible" width="70%" append-to-body>
      <div v-if="modalLsVisible" style="max-height: 550px; overflow-y: scroll">
        <HistoricDetail ref="historicDetail" :data-id="dataId"></HistoricDetail>
      </div>
    </el-dialog>
  </span>
</template>

<script setup lang="ts">
import HistoricDetail from './HistoricDetail.vue';

defineOptions({ name: 'ActHistoricDetailBtn' });
const props = defineProps({
  btnType: {
    type: String as PropType<'primary' | 'success' | 'warning' | 'danger' | 'info' | 'text' | 'default' | ''>,
    default: 'primary',
    required: false
  },
  dataId: {
    type: String,
    default: '',
    required: true
  },
  text: {
    type: String,
    default: '审批历史',
    required: false
  }
});
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const modalLsVisible = ref(false);
const history = () => {
  if (!props.dataId) {
    proxy?.$modal.msgError('流程实例ID不存在');
    return;
  }
  modalLsVisible.value = true;
};
</script>
