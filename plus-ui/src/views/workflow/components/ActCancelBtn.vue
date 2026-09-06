<template>
  <span>
    <el-button :type="btnType" @click="cancel()">{{ text }}</el-button>
    <el-dialog title="确认撤回" v-model="modalCancelVisible" width="500px" append-to-body>
      <el-form ref="delForm" :model="cancelForm" label-width="70px" v-if="modalCancelVisible">
        <el-form-item label="撤回原因" prop="reason">
          <el-input type="textarea" v-model="cancelForm.reason" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div>
          <el-button @click="modalCancelVisible = false">取消</el-button>
          <el-button type="primary" :disabled="submitLoading" @click="handelSubmitCancel">提交</el-button>
        </div>
      </template>
    </el-dialog>
  </span>
</template>

<script setup lang="ts">
//import {deleteByDataId} from '@/api/workflow/process/index';

defineOptions({ name: 'ActCancelBtn' });
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
});

const emit = defineEmits(['success']);
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const modalCancelVisible = ref(false);
const cancelForm = ref({
  reason: ''
});
const submitLoading = ref(false);

const cancel = () => {
  modalCancelVisible.value = true;
};
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
};
</script>
