<style lang="less">
</style>
<template>
  <span>
      <a-button :type="btnType"  @click="history()" >{{text}}</a-button>
      <a-modal title="审批历史" v-model:visible="modalLsVisible" :mask-closable="true" :width="'70%'" :footer="null">
          <div v-if="modalLsVisible" style="max-height: 550px; overflow-y: scroll;">
              <HistoricDetail ref="historicDetail" :data-id="dataId"></HistoricDetail>
          </div>
      </a-modal>
  </span>
</template>

<script setup lang="ts">
  import HistoricDetail from './HistoricDetail';

  defineOptions({ name: 'ActHistoricDetailBtn' })
  const props = defineProps({
    btnType: {
      type: String,
      default: 'primary',
      required: false ,
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
  })
  const { proxy } = getCurrentInstance() as ComponentInternalInstance;
  const modalLsVisible = ref(false)
  const history = () => {
    if (!props.dataId) {
        proxy?.$modal.msgError('流程实例ID不存在');
        return;
    }
    modalLsVisible.value = true;
  }

</script>
