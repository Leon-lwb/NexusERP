<template>
  <span>
    <el-button :type="btnType" @click="handle()">{{ text }}</el-button>
    <el-dialog :title="modalTaskTitle" v-model="modalTaskVisible" width="500px" append-to-body>
      <div v-if="modalTaskVisible">
        <el-form ref="formRef" :model="form" label-width="85px">
          <el-form-item label="处理意见" prop="reason">
            <el-input type="textarea" v-model="form.comment" :rows="4" />
          </el-form-item>
          <div v-show="type == 2">
            <el-form-item label="退回节点" prop="targetKey" v-if="returnTaskList.length">
              <el-radio-group v-model="form.targetKey">
                <el-radio-button v-for="item in returnTaskList" :key="item.id" :value="item.id">{{ item.name }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <span v-else>无可退回节点！</span>
          </div>
        </el-form>
      </div>
      <template #footer>
        <div>
          <el-button @click="modalTaskVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handelSubmit">提交</el-button>
        </div>
      </template>
    </el-dialog>
  </span>
</template>

<script setup lang="ts">
import { rejectTask, returnList, returnTask } from '@/api/workflow/work/task';

defineOptions({ name: 'ActHandleBtn' });
const props = defineProps({
  btnType: {
    type: String,
    default: 'primary',
    required: false
  },
  /* handleType 0通过 1驳回 2退回  */
  type: {
    type: [String, Number],
    default: '0',
    required: true
  },
  dataId: {
    type: String,
    default: '',
    required: true
  },
  /*流程变量*/
  variables: {
    type: Object,
    default: () => {}
  },
  text: {
    type: String,
    default: '处理',
    required: false
  }
});

const emit = defineEmits(['success']);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const handleType = ref({
  // 通过
  pass: 0,
  // 驳回
  back: 1,
  // 退回
  return: 2,
  //
  delegate: 3
});
const returnTaskList = ref<any>([]);
const modalTaskVisible = ref(false);
const submitLoading = ref(false);
const form = ref({
  comment: '',
  targetKey: ''
});
const modalTaskTitle = ref('');

const handle = () => {
  // type 可能以字符串('0')或数字(0)传入，统一按宽松比较（与下方 handelSubmit 口径一致）
  if (props.type == handleType.value.delegate) {
    // delegateTask();
  } else if (props.type == handleType.value.pass) {
    passTask();
  } else if (props.type == handleType.value.back) {
    backTask();
  } else if (props.type == handleType.value.return) {
    returnBakcTask();
  } else {
    proxy?.$modal.msgWarning('未知类型type，参见 handleType');
  }
};
const passTask = () => {
  modalTaskTitle.value = '审批通过';
  modalTaskVisible.value = true;
};
const backTask = () => {
  modalTaskTitle.value = '审批驳回';
  modalTaskVisible.value = true;
};
const returnBakcTask = () => {
  modalTaskTitle.value = '审批退回';
  modalTaskVisible.value = true;
  returnList({ dataId: props.dataId }).then((res) => {
    returnTaskList.value = res.data || [];
  });
};

const handelSubmit = () => {
  submitLoading.value = true;
  const formData = Object.assign(
    {
      dataId: props.dataId,
      values: Object.assign({ dataId: props.dataId }, props.variables)
    },
    form.value
  );
  if (!formData.comment) {
    proxy?.$modal.msgError('请输入审批意见！');
    submitLoading.value = false;
    return;
  }
  if (props.type == handleType.value.pass) {
    // 通过
    /*completeTask(formData).then(res => {
            submitLoading.value = false;
            if (res.success) {
                proxy?.$modal.msgSuccess('操作成功');
                modalTaskVisible.value = false;
                emit('success');
            } else {
                proxy?.$modal.msgError('操作失败');
            }
        }).finally(()=>{submitLoading.value=false});*/
  } else if (props.type == handleType.value.back) {
    // 驳回
    rejectTask(formData)
      .then((res) => {
        submitLoading.value = false;
        if (res.code == 200) {
          proxy?.$modal.msgSuccess('操作成功');
          modalTaskVisible.value = false;
          emit('success');
        } else {
          proxy?.$modal.msgError('操作失败');
        }
      })
      .finally(() => {
        submitLoading.value = false;
      });
  } else if (props.type == handleType.value.return) {
    if (!formData.targetKey) {
      proxy?.$modal.msgError('请选择退回节点！');
      submitLoading.value = false;
      return;
    }
    //退回
    returnTask(formData)
      .then((res) => {
        submitLoading.value = false;
        if (res.code == 200) {
          proxy?.$modal.msgSuccess('操作成功');
          modalTaskVisible.value = false;
          emit('success');
        } else {
          proxy?.$modal.msgError('操作失败');
        }
      })
      .finally(() => {
        submitLoading.value = false;
      });
  }
};
</script>
