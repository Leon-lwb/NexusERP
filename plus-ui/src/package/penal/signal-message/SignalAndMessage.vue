<template>
  <div class="panel-tab__content">
    <div class="panel-tab__content--title">
      <span
        ><el-icon style="margin-right: 8px; color: #555555"><Menu /></el-icon>消息列表</span
      >
      <el-button size="small" type="primary" :icon="Plus" @click="openModel('message')">创建新消息</el-button>
    </div>
    <el-table :data="messageList" size="small" border>
      <el-table-column type="index" label="序号" width="60px" />
      <el-table-column label="消息ID" prop="id" max-width="300px" show-overflow-tooltip />
      <el-table-column label="消息名称" prop="name" max-width="300px" show-overflow-tooltip />
    </el-table>
    <div class="panel-tab__content--title" style="padding-top: 8px; margin-top: 8px; border-top: 1px solid #eeeeee">
      <span
        ><el-icon style="margin-right: 8px; color: #555555"><Menu /></el-icon>信号列表</span
      >
      <el-button size="small" type="primary" :icon="Plus" @click="openModel('signal')">创建新信号</el-button>
    </div>
    <el-table :data="signalList" size="small" border>
      <el-table-column type="index" label="序号" width="60px" />
      <el-table-column label="信号ID" prop="id" max-width="300px" show-overflow-tooltip />
      <el-table-column label="信号名称" prop="name" max-width="300px" show-overflow-tooltip />
    </el-table>

    <el-dialog v-model="dialogVisible" :title="modelConfig.title" :close-on-click-modal="false" width="400px" append-to-body destroy-on-close>
      <el-form :model="modelObjectForm" size="small" label-width="90px">
        <el-form-item :label="modelConfig.idLabel">
          <el-input v-model="modelObjectForm.id" clearable />
        </el-form-item>
        <el-form-item :label="modelConfig.nameLabel">
          <el-input v-model="modelObjectForm.name" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialogVisible = false">取 消</el-button>
        <el-button size="small" type="primary" @click="addNewObject">保 存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { Plus } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

defineOptions({ name: 'SignalAndMassage' });

const signalList = ref<any[]>([]);
const messageList = ref<any[]>([]);
const dialogVisible = ref(false);
const modelType = ref('');
const modelObjectForm = ref<any>({});
const rootElements = ref();
const messageIdMap = ref();
const signalIdMap = ref();
const modelConfig = computed(() => {
  if (modelType.value === 'message') {
    return { title: '创建消息', idLabel: '消息ID', nameLabel: '消息名称' };
  } else {
    return { title: '创建信号', idLabel: '信号ID', nameLabel: '信号名称' };
  }
});
const bpmnInstances = () => (window as any)?.bpmnInstances;

const initDataList = () => {
  rootElements.value = bpmnInstances().modeler.getDefinitions().rootElements;
  messageIdMap.value = {};
  signalIdMap.value = {};
  messageList.value = [];
  signalList.value = [];
  rootElements.value.forEach((el) => {
    if (el.$type === 'bpmn:Message') {
      messageIdMap.value[el.id] = true;
      messageList.value.push({ ...el });
    }
    if (el.$type === 'bpmn:Signal') {
      signalIdMap.value[el.id] = true;
      signalList.value.push({ ...el });
    }
  });
};
const openModel = (type) => {
  modelType.value = type;
  modelObjectForm.value = {};
  dialogVisible.value = true;
};
const addNewObject = () => {
  if (modelType.value === 'message') {
    if (messageIdMap.value[modelObjectForm.value.id]) {
      ElMessage.error('该消息已存在，请修改id后重新保存');
    }
    const messageRef = bpmnInstances().moddle.create('bpmn:Message', modelObjectForm.value);
    rootElements.value.push(messageRef);
  } else {
    if (signalIdMap.value[modelObjectForm.value.id]) {
      ElMessage.error('该信号已存在，请修改id后重新保存');
    }
    const signalRef = bpmnInstances().moddle.create('bpmn:Signal', modelObjectForm.value);
    rootElements.value.push(signalRef);
  }
  dialogVisible.value = false;
  initDataList();
};

onMounted(() => {
  initDataList();
});
</script>
