<template>
  <div class="panel-tab__content">
    <el-form size="small" label-width="90px" @submit.prevent>
      <el-form-item label="ID">
        <el-input
          v-model="elementBaseInfo.id"
          :disabled="idEditDisabled || elementBaseInfo.$type === 'bpmn:Process'"
          clearable
          @change="updateBaseInfo('id')"
        />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="elementBaseInfo.name" clearable @change="updateBaseInfo('name')" />
      </el-form-item>
      <!--流程的基础属性-->
      <template v-if="elementBaseInfo.$type === 'bpmn:Process'">
        <el-form-item label="应用类型">
         <el-select v-model="elementBaseInfo.processAppType">
            <el-option
               v-for="item in appType"
               :key="item.id"
               :label="item.name"
               :value="item.id">
             </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="版本标签">
          <el-input v-model="elementBaseInfo.versionTag" clearable @change="updateBaseInfo('versionTag')" />
        </el-form-item>
        <el-form-item label="可执行">
          <el-switch v-model="elementBaseInfo.isExecutable" active-text="是" inactive-text="否" @change="updateBaseInfo('isExecutable')" />
        </el-form-item>
      </template>
      <el-form-item v-if="elementBaseInfo.$type === 'bpmn:SubProcess'" label="状态">
        <el-switch v-model="elementBaseInfo.isExpanded" active-text="展开" inactive-text="折叠" @change="updateBaseInfo('isExpanded')" />
      </el-form-item>
    </el-form>
  </div>
</template>
<script lang="ts" setup>
  defineOptions({ name: 'ElementBaseInfo' })
  const props = defineProps({
    businessObject: {
      type: Object,
      default: () => {}
    },
    appType: {
        type: Array,
        default: () => []
    },
    type: String,
    idEditDisabled: {
        type: Boolean,
        default: true
    }
  })

  const bpmnElement = ref()
  const elementBaseInfo = ref<any>({})
  const bpmnInstances = () => (window as any)?.bpmnInstances

  const resetBaseInfo = () => {
    bpmnElement.value = bpmnInstances()?.bpmnElement || {}
    elementBaseInfo.value = bpmnElement.value.businessObject
    elementBaseInfo.value.processAppType = props.appType[0];//显示流程应用类型
    if (elementBaseInfo.value && elementBaseInfo.value.$type === "bpmn:SubProcess") {
      elementBaseInfo.value["isExpanded"] = elementBaseInfo.value.di?.isExpanded
    }
  }

  const updateBaseInfo = (key: string) => {
    if (key === "id") {
      bpmnInstances().modeling.updateProperties(toRaw(bpmnElement.value), {
          id: elementBaseInfo.value[key],
          di: { id: `${elementBaseInfo.value[key]}_di` }
        });
        return;
      }
    if (key === "isExpanded") {
        bpmnInstances().modeling.modeling.toggleCollapse(toRaw(bpmnElement.value));
        return;
    }
    const attrObj = Object.create(null);
    attrObj[key] = elementBaseInfo.value[key]
    bpmnInstances().modeling.updateProperties(toRaw(bpmnElement.value), attrObj);
  }

  watch(
    () => props.businessObject,
    (val) => {
      if (val) {
        resetBaseInfo()
      }
    }
  )

  onBeforeUnmount(() => {
    bpmnElement.value = null
  })

</script>
