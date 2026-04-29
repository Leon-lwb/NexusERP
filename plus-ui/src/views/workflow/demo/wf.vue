<template>
  <div class="app-container">
  <!-- 显示DEMO数据 -->
    <el-form ref="formRef" :model="form" label-width="80px" :disabled="disabled">
      <el-form-item label="用户账号" prop="userName">
        <el-input v-model="form.userName" placeholder="" />
      </el-form-item>
      <el-form-item label="用户昵称" prop="nickName">
        <el-input v-model="form.nickName" placeholder="" />
      </el-form-item>
      <el-form-item label="用户邮箱" prop="email">
        <el-input v-model="form.email" placeholder="" />
      </el-form-item>
      <el-form-item label="头像地址" prop="avatar">
        <el-input v-model="form.avatar" placeholder="" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" placeholder="" />
      </el-form-item>
    </el-form>
    <!--<div slot="footer" class="dialog-footer">
      <el-button :loading="buttonLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="cancel">取 消</el-button>
    </div> -->
  </div>
</template>

<script setup name="wfDemo" lang="ts">
  import {getDemo, delDemo, addDemo, updateDemo } from "@/api/workflow/demo";

  const props = defineProps({
    //表单禁用
    disabled: {
      type: Boolean,
      default: false,
      required: false
    },
    /*流程自定义表单数据*/
    customFormData:{
       type:Object,
       default:()=>{return {}},
       required:false
    },
  })
  const { proxy } = getCurrentInstance() as ComponentInternalInstance;
  // 按钮loading
  const buttonLoading = ref(false)
  // 弹出层标题
  const title = ref("")
  // 是否显示弹出层
  const open = ref(true)
  // 表单参数
  const form = ref<any>({})
  const formRef = ref(null)

  // 取消按钮
  const cancel = () => {
    open.value = false;
    reset();
  }
  // 表单重置
  const reset = () => {
    form.value = {
      demoId: undefined,
      userName: undefined,
      nickName: undefined,
      email: undefined,
      avatar: undefined,
      status: undefined,
      delFlag: undefined,
      createBy: undefined,
      createTime: undefined,
      updateBy: undefined,
      updateTime: undefined,
      remark: undefined
    };
    formRef.value?.resetFields();
  }
  /** 新增按钮操作 */
  const handleAdd = () => {
    reset();
    open.value = true;
    title.value = "添加DEMO";
  }
  /** 查看按钮操作 */
  const handleView = (row) => {
    loading.value = true;
    reset();
    const demoId = row.demoId
    getDemo(demoId).then(response => {
      loading.value = false;
      form.value = response.data;
      open.value = true;
      title.value = "DEMO详情";
    });
  }
  /** 修改按钮操作 */
  const handleUpdate = (row) => {
    loading.value = true;
    reset();
    const demoId = row.demoId
    getDemo(demoId).then(response => {
      loading.value = false;
      form.value = response.data;
      open.value = true;
      title.value = "修改DEMO";
    });
  }
  /** 提交按钮 */
  const submitForm = () => {
    formRef.validate(valid => {
      if (valid) {
        buttonLoading.value = true;
        if (form.value.demoId != null) {
          updateDemo(form.value).then(response => {
            proxy?.$modal.msgSuccess("修改成功");
            open.value = false;
            getList();
          }).finally(() => {
            buttonLoading.value = false;
          });
        } else {
          addDemo(form.value).then(response => {
            proxy?.$modal.msgSuccess("新增成功");
            open.value = false;
            getList();
          }).finally(() => {
            buttonLoading.value = false;
          });
        }
      }
    });
  }

  onMounted(() => {
    //流程调用自定义表单的传入参数
    if(props.customFormData != null) {
      console.log("props.customFormData=",props.customFormData);
      form.value = props.customFormData;
    }
  })

</script>

<style>
</style>
