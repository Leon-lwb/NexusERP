<template>
  <span>
      <a-button :type="btnType" @click="cancel()" >{{text}}</a-button>
      <a-modal title="确认撤回" v-model="modalCancelVisible" :mask-closable="false" :width="500">
            <a-form ref="delForm" v-model="cancelForm" :label-width="70" v-if="modalCancelVisible">
                <a-form-item label="撤回原因" prop="reason">
                    <a-input type="textarea" v-model="cancelForm.reason" :rows="4" />
                </a-form-item>
            </a-form>
            <div slot="footer">
                <a-button type="primary" @click="modalCancelVisible = false">取消</a-button>
                <a-button type="primary" :disabled="submitLoading" @click="handelSubmitCancel">提交</a-button>
            </div>
        </a-modal>
  </span>
</template>

<script setup lang="ts">
  //import {deleteByDataId} from "@/api/workflow/process";

  defineOptions({ name: 'ActCancelBtn' })
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
    instanceId: {
        type: String,
        default: '',
        required: false
    },
    text: {
        type: String,
        default: '撤回',
        required: false
    }
  })

  const emit = defineEmits([
    'success'
  ])
  const { proxy } = getCurrentInstance() as ComponentInternalInstance;
  const modalCancelVisible = ref(false)
  const cancelForm = ref({
      reason: ''
  })
  const submitLoading = ref(false)

  const cancel = () => {
    modalCancelVisible.value = true;
  }
  const handelSubmitCancel = () => {
   /* submitLoading.value = true;
    deleteByDataId(props.instanceId, cancelForm.value.reason,props.dataId)
      .then(res => {
          if (res.success) {
              proxy?.$modal.msgSuccess('操作成功');
              modalCancelVisible.value = false;
              emit('success');
          } else {
              proxy?.$modal.msgError(res.message);
          }
      })
      .finally(() => (submitLoading.value = false));*/
  }
</script>
