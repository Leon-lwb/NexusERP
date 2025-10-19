<template>
  <div class="app-container">
    <el-page-header @back="goBack" title="返回" content="BOM信息" />
    <el-form ref="formRef" :model="formData" :rules="rules" size="default" label-width="100px">
      <el-row gutter="15">
        <el-col :span="8">
          <el-form-item label="父项物料" prop="field103">
            <el-input v-model="formData.field103" type="text" placeholder="请输入父项物料" readonly clearable
              :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="BOM版本号" prop="field104">
            <el-input v-model="formData.field104" type="text" placeholder="请输入BOM版本号" clearable
              :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="BOM名称" prop="field105">
            <el-input v-model="formData.field105" type="text" placeholder="请输入BOM名称" clearable
              :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row gutter="15">
        <el-col :span="6">
          <el-form-item label="生效日期" prop="field107">
            <el-date-picker v-model="formData.field107" format="YYYY-MM-DD" value-format="YYYY-MM-DD"
              :style="{width: '100%'}" placeholder="请选择生效日期" clearable></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="失效日期" prop="field108">
            <el-time-picker v-model="formData.field108" format="HH:mm:ss" value-format="HH:mm:ss"
              :style="{width: '100%'}" placeholder="请选择失效日期" clearable></el-time-picker>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="当前版本" prop="field110">
            <el-radio-group v-model="formData.field110" size="default">
              <el-radio v-for="(item, index) in field110Options" :key="index" :value="item.value"
                :disabled="item.disabled">{{item.label}}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态信息" prop="field111">
            <el-radio-group v-model="formData.field111" size="default">
              <el-radio v-for="(item, index) in field111Options" :key="index" :value="item.value"
                :disabled="item.disabled">{{item.label}}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row gutter="15">
        <el-form-item label="备注信息" prop="field113">
          <el-input v-model="formData.field113" type="textarea" placeholder="请输入备注信息"
            :autosize="{minRows: 4, maxRows: 4}" :style="{width: '100%'}"></el-input>
        </el-form-item>
      </el-row>
      <el-row gutter="15">
        <el-form-item label="新增物料" prop="field117">
          <el-button type="primary" icon="Search" size="default"> 主要按钮 </el-button>
        </el-form-item>
      </el-row>
      <el-row gutter="15">
      </el-row>
      <el-form-item>
        <el-button type="primary" @click="submitForm">提交</el-button>
        <el-button @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>
<script setup>
import { ref, reactive, toRefs, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getBom, addBom, updateBom } from "@/api/core/bd/bom"
const {
  proxy
} = getCurrentInstance()
const formRef = ref()
const route = useRoute();
const router = useRouter();

const data = reactive({
  action: '', // 'view', 'edit', or 'add'
  formData: {
    field103: undefined,
    field104: undefined,
    field105: undefined,
    field107: null,
    field108: null,
    field110: 1,
    field111: 1,
    field113: undefined,
    field117: undefined,
  },
  rules: {
    field103: [{
      required: true,
      message: '请输入父项物料',
      trigger: 'blur'
    }],
    field104: [{
      required: true,
      message: '请输入BOM版本号',
      trigger: 'blur'
    }],
    field105: [{
      required: true,
      message: '请输入BOM名称',
      trigger: 'blur'
    }],
    field107: [{
      required: true,
      message: '请选择生效日期',
      trigger: 'change'
    }],
    field108: [{
      required: true,
      message: '请选择失效日期',
      trigger: 'change'
    }],
    field110: [{
      required: true,
      message: '当前版本不能为空',
      trigger: 'change'
    }],
    field111: [{
      required: true,
      message: '状态信息不能为空',
      trigger: 'change'
    }],
    field113: [{
      required: true,
      message: '请输入备注信息',
      trigger: 'blur'
    }],
  }
})
const {
  formData,
  rules
} = toRefs(data)
const field110Options = ref([{
  "label": "是",
  "value": 1
}, {
  "label": "否",
  "value": 0
}])
const field111Options = ref([{
  "label": "启用",
  "value": 1
}, {
  "label": "停用",
  "value": 0
}])
/**
 * @name: 返回上一页
 * @description: 返回上一页方法
 * @return {*}
 */
function goBack() {
  router.back();
}

/**
 * @name: 表单提交
 * @description: 表单提交方法
 * @return {*}
 */
function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) return
    // 根据action执行新增或修改操作
    if (route.query.id) {
      // 修改操作
      updateBom(formData.value).then(response => {
        proxy.$modal.msgSuccess("修改成功");
        goBack();
      }).catch(() => {});
    } else {
      // 新增操作
      addBom(formData.value).then(response => {
        proxy.$modal.msgSuccess("新增成功");
        goBack();
      }).catch(() => {});
    }
  })
}

/**
 * @name: 表单重置
 * @description: 表单重置方法
 * @return {*}
 */
function resetForm() {
  formRef.value.resetFields()
}

// 初始化页面
function initFormData() {
  const id = route.query.id;
  const action = route.query.action || 'view';
  
  data.action = action;
  
  if (id) {
    // 编辑或查看模式，获取BOM详情
    getBom(id).then(response => {
      const bomData = response.data;
      data.formData = {
        field103: bomData.parentMaterialId ? ('物料ID: ' + bomData.parentMaterialId) : '',
        field104: bomData.bomVersion || '',
        field105: bomData.bomName || '',
        field107: bomData.effectiveDate || null,
        field108: bomData.expiryDate || null,
        field110: bomData.isCurrent !== undefined ? parseInt(bomData.isCurrent) : 1,
        field111: bomData.status !== undefined ? parseInt(bomData.status) : 1,
        field113: bomData.remark || '',
        field117: undefined,
      };
      
      // 如果是查看模式，禁用表单
      if (action === 'view') {
        document.querySelectorAll('input, select, textarea, button').forEach(el => {
          el.disabled = true;
        });
        // 启用返回按钮
        const backBtn = document.querySelector('.el-page-header__left');
        if (backBtn) backBtn.style.pointerEvents = 'auto';
      }
    }).catch(error => {
      proxy.$modal.msgError('获取BOM详情失败');
      goBack();
    });
  } else {
    // 新增模式，默认值
    data.formData.field104 = "1.0";
    data.formData.field110 = 1;
    data.formData.field111 = 1;
  }
}

// 页面加载时初始化
onMounted(() => {
  initFormData();
});

</script>
<style>
</style>
