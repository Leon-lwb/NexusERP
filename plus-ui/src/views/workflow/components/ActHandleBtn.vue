<template>
  <span>
    <el-button :type="btnType" @click="handle()">{{ text }}</el-button>
    <el-dialog :title="modalTaskTitle" v-model="modalTaskVisible" width="500px" append-to-body>
      <div v-if="modalTaskVisible">
        <el-form ref="formRef" :model="form" label-width="85px">
          <el-form-item label="处理意见" prop="reason">
            <el-input type="textarea" v-model="form.comment" :rows="4" />
          </el-form-item>
          <!--
            签章入口：仅当流程变量 needSign 为真（布尔 true 或字符串 "true"）时渲染。
            未配置签章的存量流程既不产生额外字段也不发生布局变化。
            印章列表由后端按当前用户在该业务场景下的可用性实时判定，前端只负责选择。
          -->
          <template v-if="signRequired">
            <el-form-item label="签署印章">
              <SealPicker v-model="sealId" biz-type="APPROVAL" />
            </el-form-item>
            <el-form-item label="签署含义">
              <el-input v-model="signMeaning" maxlength="128" show-word-limit placeholder="请输入签署含义" />
            </el-form-item>
          </template>
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
    <!-- 二级认证弹窗：签署前二次确认身份；只在要求签章时挂载，未配置签章的流程零额外节点 -->
    <SignAuthDialog v-if="signRequired" ref="signAuthRef" v-model="signAuthVisible" @confirmed="handleSignConfirmed" />
  </span>
</template>

<script setup lang="ts">
import { complete, rejectTask, returnList, returnTask } from '@/api/workflow/work/task';
import SealPicker from '@/components/sign/SealPicker.vue';
import SignAuthDialog from '@/components/sign/SignAuthDialog.vue';

defineOptions({ name: 'ActHandleBtn' });
const props = defineProps({
  btnType: {
    type: String as PropType<'primary' | 'success' | 'warning' | 'danger' | 'info' | 'text' | 'default' | ''>,
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

/* ==================== 电子签章 ==================== */
/**
 * 节点是否要求签章：流程变量 needSign 为布尔 true 或字符串 "true"（忽略大小写）。
 * 为假时不渲染任何签章字段、不额外发起任何请求，存量流程行为与插桩前完全一致。
 */
const signRequired = computed(() => {
  const value = (props.variables as any)?.needSign;
  return (
    value === true ||
    String(value ?? '')
      .trim()
      .toLowerCase() === 'true'
  );
});
/** 所选印章ID（空串 = 仅逻辑签名不盖章） */
const sealId = ref<string | undefined>(undefined);
/** 签署含义（后端缺省「审批签批」，最长 128） */
const signMeaning = ref('审批签批');
const signAuthVisible = ref(false);
const signAuthRef = ref<InstanceType<typeof SignAuthDialog>>();
/**
 * 等待二级认证的提交载荷（本组件既有的 formData 结构）。
 * 其中不含凭证：signCredential 只在 handleSignConfirmed 的局部变量里短暂存在，
 * 不写入 Pinia / localStorage / URL，也不会回填进本变量。
 */
const pendingSignPayload = ref<any>(null);

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
  // 节点要求签章：不能直接提交，先校验签署含义并过二级认证；认证通过后才带着签名变量提交。
  // 未配置 needSign 的流程不会进入本分支，其后的提交逻辑与插桩前逐字相同。
  if (signRequired.value && props.type == handleType.value.pass) {
    if (!signMeaning.value || !signMeaning.value.trim()) {
      proxy?.$modal.msgError('请输入签署含义！');
      submitLoading.value = false;
      return;
    }
    pendingSignPayload.value = formData;
    submitLoading.value = false;
    signAuthVisible.value = true;
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

/**
 * 二级认证通过后提交审批。
 * 签名变量同时写入 values（沿用本组件既有的变量结构）与 variables（后端 WfTaskBo 的实际字段名，
 * 否则 complete() 取不到 needSign 与签章参数，签章关卡不会生效）。
 * 后端拒签时会抛出 ServiceException，经 axios 拦截器转成 Error，弹窗保持打开并把原因原样提示给用户。
 */
const handleSignConfirmed = async (auth: { authMethod: string; credential: string }) => {
  if (!pendingSignPayload.value) {
    return;
  }
  signAuthRef.value?.setLoading(true);
  submitLoading.value = true;
  // 基础变量仍取自既有载荷；凭证只在这里参与一次请求体的构造
  const baseVariables = { ...(pendingSignPayload.value.values || {}) };
  const signVariables = {
    needSign: true,
    signSealId: sealId.value ?? '',
    signMeaning: signMeaning.value,
    signAuthMethod: auth.authMethod,
    signCredential: auth.credential
  };
  try {
    const res = await complete({
      ...pendingSignPayload.value,
      values: { ...baseVariables, ...signVariables },
      variables: { ...baseVariables, ...signVariables }
    });
    if (res.code == 200) {
      proxy?.$modal.msgSuccess('操作成功');
      signAuthVisible.value = false;
      modalTaskVisible.value = false;
      pendingSignPayload.value = null;
      emit('success');
    } else {
      // 后端拒签（密码错误 / 无授权 / 印章不可用）：保持认证弹窗打开以便重试
      signAuthRef.value?.setLoading(false);
      proxy?.$modal.msgError(res.msg || '签署失败');
    }
  } catch (error: any) {
    signAuthRef.value?.setLoading(false);
    proxy?.$modal.msgError(error?.message || '签署失败');
  } finally {
    submitLoading.value = false;
  }
};
</script>
