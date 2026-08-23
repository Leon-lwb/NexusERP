<template>
  <div class="process-panel__container" :style="{ width: `${width}px` }">
    <el-collapse v-model="activeTab">
      <el-collapse-item name="base">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><info-filled /></el-icon>常规
          </div>
        </template>
        <element-base-info :id-edit-disabled="idEditDisabled" :business-object="elementBusinessObject" :appType="appType" :type="elementType" />
      </el-collapse-item>
      <el-collapse-item name="condition" v-if="elementType === 'Process'" key="message">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><comment /></el-icon>消息与信号
          </div>
        </template>
        <signal-and-massage />
      </el-collapse-item>
      <el-collapse-item name="condition" v-if="conditionFormVisible" key="condition">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><promotion /></el-icon>流转条件
          </div>
        </template>
        <flow-condition :business-object="elementBusinessObject" :type="elementType" />
      </el-collapse-item>
      <el-collapse-item name="condition" v-if="formVisible" key="form">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><list /></el-icon>表单
          </div>
        </template>
        <element-form :id="elementId" :appType="appType" :type="elementType" />
      </el-collapse-item>
      <el-collapse-item name="task" v-if="elementType.indexOf('Task') !== -1" key="task">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><checked /></el-icon>任务
          </div>
        </template>
        <element-task :id="elementId" :type="elementType" />
      </el-collapse-item>
      <el-collapse-item name="catchEvent" v-if="elementType.indexOf('IntermediateCatchEvent') !== -1" key="catchEvent">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><help-filled /></el-icon>定时捕获事件
          </div>
        </template>
        <catch-event :business-object="elementBusinessObject" :type="elementType" />
      </el-collapse-item>
      <el-collapse-item name="boundaryEvent" v-if="elementType.indexOf('BoundaryEvent') !== -1" key="boundaryEvent">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><help-filled /></el-icon>定时边界事件
          </div>
        </template>
        <boundary-event :business-object="elementBusinessObject" :type="elementType" />
      </el-collapse-item>
      <el-collapse-item name="multiInstance" v-if="elementType.indexOf('Task') !== -1 && elementType !== 'UserTask'" key="multiInstance">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><help-filled /></el-icon>多实例
          </div>
        </template>
        <element-multi-instance :business-object="elementBusinessObject" :type="elementType" />
      </el-collapse-item>
      <el-collapse-item name="listeners" key="listeners">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><bell-filled /></el-icon>执行监听器
          </div>
        </template>
        <element-listeners :id="elementId" :type="elementType" />
      </el-collapse-item>
      <el-collapse-item name="taskListeners" v-if="elementType === 'UserTask'" key="taskListeners">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><bell-filled /></el-icon>任务监听器
          </div>
        </template>
        <user-task-listeners :id="elementId" :type="elementType" />
      </el-collapse-item>
      <el-collapse-item name="extensions" key="extensions">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><circle-plus /></el-icon>扩展属性
          </div>
        </template>
        <element-properties :id="elementId" :type="elementType" />
      </el-collapse-item>
      <el-collapse-item name="other" key="other">
        <template #title>
          <div class="panel-tab__title">
            <el-icon><promotion /></el-icon>其他
          </div>
        </template>
        <element-other-config :id="elementId" />
      </el-collapse-item>
    </el-collapse>
  </div>
</template>
<script lang="ts" setup>
import ElementBaseInfo from './base/ElementBaseInfo';
import ElementOtherConfig from './other/ElementOtherConfig';
import ElementTask from './task/ElementTask';
import ElementMultiInstance from './multi-instance/ElementMultiInstance';
import FlowCondition from './flow-condition/FlowCondition';
import SignalAndMassage from './signal-message/SignalAndMessage';
import ElementListeners from './listeners/ElementListeners';
import ElementProperties from './properties/ElementProperties';
import ElementForm from './form/ElementForm';
import UserTaskListeners from './listeners/UserTaskListeners';
// add by wanghuan 2022-12-16
import BoundaryEvent from './boundary-events/BoundaryEvent';
import CatchEvent from './catch-events/CatchEvent';
/**
 * 侧边栏
 * @Author MiyueFE wanghuan
 * @Home https://github.com/miyuesc
 * @Date 2021年3月31日18:57:51
 */
defineOptions({ name: 'MyPropertiesPanel' });

const props = defineProps({
  bpmnModeler: {
    type: Object,
    default: () => {}
  },
  prefix: {
    type: String,
    default: 'flowable'
  },
  appType: {
    type: Array,
    default: () => []
  },
  width: {
    type: Number,
    default: 480
  },
  idEditDisabled: {
    type: Boolean,
    default: false
  },
  model: Object // 流程模型的数据
});

provide('prefix', props.prefix);
provide('width', props.width);

const activeTab = ref('base');
const elementId = ref('');
const elementType = ref('');
const elementBusinessObject = ref<any>({}); // 元素 businessObject 镜像，提供给需要做判断的组件使用
const conditionFormVisible = ref(false); // 流转条件设置
const formVisible = ref(false); // 表单配置
const bpmnElement = ref();

const bpmnInstances = () => (window as any)?.bpmnInstances;

const getActiveElement = () => {
  // 初始第一个选中元素 bpmn:Process
  initFormOnChanged(null);
  props.bpmnModeler.on('import.done', (e) => {
    initFormOnChanged(null);
  });
  // 监听选择事件，修改当前激活的元素以及表单
  props.bpmnModeler.on('selection.changed', ({ newSelection }) => {
    initFormOnChanged(newSelection ? newSelection[0] : null);
  });
  props.bpmnModeler.on('element.changed', ({ element }) => {
    // 保证 修改 "默认流转路径" 类似需要修改多个元素的事件发生的时候，更新表单的元素与原选中元素不一致。
    if (element && element.id === elementId.value) {
      initFormOnChanged(element);
    }
  });
};
// 初始化数据
const initFormOnChanged = (element) => {
  let activatedElement = element;
  if (!activatedElement) {
    activatedElement =
      bpmnInstances().elementRegistry.find((el) => el.type === 'bpmn:Process') ??
      bpmnInstances().elementRegistry.find((el) => el.type === 'bpmn:Collaboration');
  }
  if (!activatedElement) return;
  console.log(`
              ----------
     select element changed:
                id:  ${activatedElement.id}
              type:  ${activatedElement.businessObject.$type}
              ----------
              `);
  bpmnInstances().bpmnElement = activatedElement;
  bpmnElement.value = activatedElement;
  elementId.value = activatedElement.id;
  elementType.value = activatedElement.type.split(':')[1] || '';
  elementBusinessObject.value = JSON.parse(JSON.stringify(activatedElement.businessObject));
  conditionFormVisible.value = !!(
    elementType.value === 'SequenceFlow' &&
    activatedElement.source &&
    activatedElement.source.type.indexOf('StartEvent') === -1
  );
  const appType = JSON.parse(JSON.stringify(props.appType));
  formVisible.value = (elementType.value === 'UserTask' && appType[0].id != 'ZDYYW') || elementType.value === 'StartEvent';
};

// 监听 props.bpmnModeler 然后 initModels
const watchBpmn = watch(
  () => props.bpmnModeler,
  () => {
    // 避免加载时 流程图 并未加载完成
    if (!props.bpmnModeler) {
      return;
    }
    const w = window as any;
    w.bpmnInstances = {
      modeler: props.bpmnModeler,
      modeling: props.bpmnModeler.get('modeling'),
      moddle: props.bpmnModeler.get('moddle'),
      eventBus: props.bpmnModeler.get('eventBus'),
      bpmnFactory: props.bpmnModeler.get('bpmnFactory'),
      elementFactory: props.bpmnModeler.get('elementFactory'),
      elementRegistry: props.bpmnModeler.get('elementRegistry'),
      replace: props.bpmnModeler.get('replace'),
      selection: props.bpmnModeler.get('selection')
    };
    getActiveElement();
    watchBpmn();
  },
  {
    immediate: true
  }
);

watch(
  () => elementId.value,
  () => {
    activeTab.value = 'base';
  }
);

onBeforeUnmount(() => {
  const w = window as any;
  w.bpmnInstances = null;
});
</script>
