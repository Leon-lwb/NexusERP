<template>
  <div class="main-sub-form">
    <!-- 主表 -->
    <el-form :model="formData.main" label-width="120px">
      <el-row v-for="field in mainFields" :key="field.columnName">
        <el-form-item :label="field.columnComment" :required="field.isRequired === '1'">
          <component :is="getComponent(field)" v-model="formData.main[field.columnName]" :options="field.dictData" />
        </el-form-item>
      </el-row>
    </el-form>

    <!-- 子表 -->
    <el-divider>子表信息</el-divider>
    <div v-for="(sub, index) in formData.subs" :key="index">
      <el-button @click="removeSub(index)">删除</el-button>
      <el-form :model="sub" label-width="120px">
        <el-row v-for="field in subFields" :key="field.columnName">
          <el-form-item :label="field.columnComment" :required="field.isRequired === '1'">
            <component :is="getComponent(field)" v-model="sub[field.columnName]" :options="field.dictData" />
          </el-form-item>
        </el-row>
      </el-form>
    </div>
    <el-button @click="addSub">添加子项</el-button>
  </div>
</template>

<script setup lang="ts">
import type { OnlFieldVo } from '@/api/online/types';
import { getComponentType } from '@/views/workflow/hooks/useOnline';

const props = defineProps({
  mainFields: Array as PropType<OnlFieldVo[]>,
  subFields: Array as PropType<OnlFieldVo[]>,
  initialData: Object
});

const formData = reactive({
  main: props.initialData?.main || {},
  subs: props.initialData?.subs?.length ? [...props.initialData.subs] : [{}]
});

// 动态组件注册表（getComponentType 返回 nbMade 组件集合），组件 props 各不相同，返回 any 交由运行时处理
const getComponent = (field: OnlFieldVo): any => {
  return getComponentType(field.htmlType);
};

const addSub = () => {
  formData.subs.push({});
};

const removeSub = (index: number) => {
  if (formData.subs.length > 1) {
    formData.subs.splice(index, 1);
  }
};
</script>
