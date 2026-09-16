<template>
  <!--
    二级认证弹窗：签署前的身份二次确认。
    本期只支持「登录密码」：短信方式未开放（ruoyi-common-sign 取不到当前登录用户手机号，
    后端 SignAuthMethod.SUPPORTED 不含 SMS，也没有下发验证码的接口），故不提供短信选项。
    用法：<SignAuthDialog ref="authRef" v-model="authVisible" @confirmed="onSignConfirmed" />
  -->
  <el-dialog v-model="visible" title="签署身份验证" width="440px" append-to-body :close-on-click-modal="false">
    <el-alert class="mb3" type="warning" :closable="false" show-icon title="签署行为将永久记录（签署人 / 时间 / IP / 哈希链），请确认是本人操作。" />

    <el-form ref="authFormRef" :model="form" :rules="rules" label-width="90px" @submit.prevent>
      <el-form-item label="认证方式">
        <el-tag type="info">登录密码</el-tag>
      </el-form-item>
      <el-form-item label="登录密码" prop="credential">
        <el-input
          v-model="form.credential"
          type="password"
          show-password
          autocomplete="off"
          placeholder="请输入登录密码"
          @keyup.enter="handleConfirm"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :loading="submitLoading" @click="handleConfirm">确认签署</el-button>
        <el-button @click="visible = false">取消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { FormInstance } from 'element-plus';

/** 弹窗可见性（v-model） */
const visible = defineModel<boolean>({ default: false });

const emit = defineEmits<{
  (e: 'confirmed', payload: { authMethod: string; credential: string }): void;
}>();

/** 本期唯一可用的二级认证方式（后端 SignAuthMethod.PASSWORD；SMS 未开放） */
const AUTH_METHOD = 'PASSWORD';

const authFormRef = ref<FormInstance>();
/** 提交中状态由父组件通过 setLoading 控制（后端拒绝签署时需保持弹窗打开重试） */
const submitLoading = ref(false);
/** 凭证只存组件内存：不写 Pinia、不落 localStorage、不进 URL */
const form = ref({ credential: '' });

const rules: ElFormRules = {
  credential: [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
};

/** 开、关都清空凭证与校验状态，避免密码在内存中多留一轮（题目要求的 watch 清理点） */
const reset = () => {
  form.value.credential = '';
  submitLoading.value = false;
  authFormRef.value?.clearValidate();
};

watch(visible, () => reset());

/** 本地校验通过后才 emit 凭证；是否关闭、何时关闭由父组件按后端结果决定 */
const handleConfirm = () => {
  authFormRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    emit('confirmed', { authMethod: AUTH_METHOD, credential: form.value.credential });
  });
};

defineExpose({
  /** 由父组件控制「确认签署」按钮的提交中状态 */
  setLoading: (value: boolean) => {
    submitLoading.value = value;
  }
});
</script>

<style scoped>
.mb3 {
  margin-bottom: 12px;
}
</style>
