<template>
  <div class="my-process-designer">
    <div class="my-process-designer__header">
      <slot name="control-header"></slot>
      <template v-if="!$slots['control-header']">
        <el-button-group key="file-control">
          <el-button :size="headerButtonSize" :type="headerButtonType" icon="el-icon-edit-outline" @click="onSave">保存流程</el-button>
          <el-button :size="headerButtonSize" :type="headerButtonType" :icon="FolderOpened" @click="refFile.click()">打开文件</el-button>
          <el-tooltip effect="light">
            <template #content>
              <el-button :size="headerButtonSize" type="primary" @click="downloadProcessAsXml()">下载为XML文件</el-button>
              <br />
              <el-button :size="headerButtonSize" type="primary" @click="downloadProcessAsSvg()">下载为SVG文件</el-button>
              <br />
              <el-button :size="headerButtonSize" type="primary" @click="downloadProcessAsBpmn()">下载为BPMN文件</el-button>
            </template>
            <el-button :size="headerButtonSize" :type="headerButtonType" :icon="Download">下载文件</el-button>
          </el-tooltip>
          <el-tooltip effect="light">
            <template #content>
              <el-button :size="headerButtonSize" type="primary" @click="previewProcessXML">预览XML</el-button>
              <br />
              <el-button :size="headerButtonSize" type="primary" @click="previewProcessJson">预览JSON</el-button>
            </template>
            <el-button :size="headerButtonSize" :type="headerButtonType" :icon="View">预览</el-button>
          </el-tooltip>
          <el-tooltip v-if="simulation" effect="light" :content="simulationStatus ? '退出模拟' : '开启模拟'">
            <el-button :size="headerButtonSize" :type="headerButtonType" :icon="Cpu" @click="processSimulation">
              模拟
            </el-button>
          </el-tooltip>
        </el-button-group>
        <el-button-group key="align-control">
          <el-tooltip effect="light" content="向左对齐">
            <el-button :size="headerButtonSize" class="align align-left" :icon="Histogram" @click="elementsAlign('left')" />
          </el-tooltip>
          <el-tooltip effect="light" content="向右对齐">
            <el-button :size="headerButtonSize" class="align align-right" :icon="Histogram" @click="elementsAlign('right')" />
          </el-tooltip>
          <el-tooltip effect="light" content="向上对齐">
            <el-button :size="headerButtonSize" class="align align-top" :icon="Histogram" @click="elementsAlign('top')" />
          </el-tooltip>
          <el-tooltip effect="light" content="向下对齐">
            <el-button :size="headerButtonSize" class="align align-bottom" :icon="Histogram" @click="elementsAlign('bottom')" />
          </el-tooltip>
          <el-tooltip effect="light" content="水平居中">
            <el-button :size="headerButtonSize" class="align align-center" :icon="Histogram" @click="elementsAlign('center')" />
          </el-tooltip>
          <el-tooltip effect="light" content="垂直居中">
            <el-button :size="headerButtonSize" class="align align-middle" :icon="Histogram" @click="elementsAlign('middle')" />
          </el-tooltip>
        </el-button-group>
        <el-button-group key="scale-control">
          <el-tooltip effect="light" content="缩小视图">
            <el-button :size="headerButtonSize" :disabled="defaultZoom < 0.2" :icon="ZoomOut" @click="processZoomOut()" />
          </el-tooltip>
          <el-button :size="headerButtonSize">{{ Math.floor(defaultZoom * 10 * 10) + "%" }}</el-button>
          <el-tooltip effect="light" content="放大视图">
            <el-button :size="headerButtonSize" :disabled="defaultZoom > 4" :icon="ZoomIn" @click="processZoomIn()" />
          </el-tooltip>
          <el-tooltip effect="light" content="重置视图并居中">
            <el-button :size="headerButtonSize" :icon="ScaleToOriginal" @click="processReZoom()" />
          </el-tooltip>
        </el-button-group>
        <el-button-group key="stack-control">
          <el-tooltip effect="light" content="撤销">
            <el-button :size="headerButtonSize" :disabled="!revocable" :icon="RefreshLeft" @click="processUndo()" />
          </el-tooltip>
          <el-tooltip effect="light" content="恢复">
            <el-button :size="headerButtonSize" :disabled="!recoverable" :icon="RefreshRight" @click="processRedo()" />
          </el-tooltip>
          <el-tooltip effect="light" content="重新绘制">
            <el-button :size="headerButtonSize" :icon="Refresh" @click="processRestart" />
          </el-tooltip>
        </el-button-group>
      </template>
      <!-- 用于打开本地文件-->
      <input type="file" id="files" ref="refFile" style="display: none" accept=".xml, .bpmn" @change="importLocalFile" />
    </div>
    <div class="my-process-designer__container">
      <div class="my-process-designer__canvas" id = "bpmnCanvas" ref="bpmnCanvas"></div>
    </div>
    <el-dialog title="预览" width="60%" v-model="previewModelVisible" append-to-body destroy-on-close>
      <highlightjs :language="previewType" :code="previewResult" style="height: 80vh" />
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { Histogram, Cpu, Refresh, RefreshLeft, RefreshRight, ZoomOut, ZoomIn, View, Download, FolderOpened, ScaleToOriginal } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BpmnModeler from "bpmn-js/lib/Modeler";
import DefaultEmptyXML from "./plugins/defaultEmpty";
// 翻译方法
import customTranslate from "./plugins/translate/customTranslate";
import translationsCN from "./plugins/translate/zh";
// 模拟流转流程
import tokenSimulation from "bpmn-js-token-simulation";
// 标签解析构建器
// import bpmnPropertiesProvider from "bpmn-js-properties-panel/lib/provider/bpmn";
// 标签解析 Moddle
import camundaModdleDescriptor from './plugins/descriptor/camundaDescriptor.json';
import activitiModdleDescriptor from './plugins/descriptor/activitiDescriptor.json';
import flowableModdleDescriptor from './plugins/descriptor/flowableDescriptor.json';
// 标签解析 Extension
import camundaModdleExtension from './plugins/extension-moddle/camunda';
import activitiModdleExtension from './plugins/extension-moddle/activiti';
import flowableModdleExtension from './plugins/extension-moddle/flowable';
// 引入json转换与高亮
// import X2JS from "x2js";
import convert from "xml-js";

defineOptions({ name: 'MyProcessDesigner' })

const refFile = ref()
const emit = defineEmits([
  'destroy',
  'init-finished',
  'commandStack-changed',
  'update:modelValue',
  'change',
  'canvas-viewbox-changed',
  'element-click'
])

const props = defineProps({
  modelValue: String, // xml 字符串
  processId: String, // 流程 key 标识
  processName: String, // 流程 name 名字
  formId: Number, // 流程 form 表单编号
  translations: {
    // 自定义的翻译文件
    type: Object,
    default: () => {}
  },
  options: {
    type: Object,
    default: () => ({})
  }, // 自定义的翻译文件
  additionalModel: [Object, Array], // 自定义model
  moddleExtension: {
    // 自定义moddle
    type: Object,
    default: () => {}
  },
  onlyCustomizeAddi: {
    type: Boolean,
    default: false
  },
  onlyCustomizeModdle: {
    type: Boolean,
    default: false
  },
  simulation: {
    type: Boolean,
    default: true
  },
  keyboard: {
    type: Boolean,
    default: true
  },
  prefix: {
    type: String,
    default: 'flowable'
  },
  events: {
    type: Array,
    default: () => ['element.click']
  },
  headerButtonSize: {
    type: String,
    default: 'small',
    validator: (value: string) => ['default', 'medium', 'small', 'mini'].indexOf(value) !== -1
  },
  headerButtonType: {
    type: String,
    default: 'primary',
    validator: (value: string) =>
      ['default', 'primary', 'success', 'warning', 'danger', 'info'].indexOf(value) !== -1
  }
})

let bpmnModeler: any = null
const defaultZoom = ref(1)
const previewModelVisible = ref(false)
const simulationStatus = ref(false)
const previewResult = ref('')
const previewType = ref('xml')
const recoverable = ref(false)
const revocable = ref(false)

const additionalModules = computed(() => {
  const Modules: any[] = []
  // 仅保留用户自定义扩展模块
  if (props.onlyCustomizeAddi) {
    if (Object.prototype.toString.call(props.additionalModel) == '[object Array]') {
      return props.additionalModel || []
    }
    return [props.additionalModel]
  }

  // 插入用户自定义扩展模块
  if (Object.prototype.toString.call(props.additionalModel) == '[object Array]') {
    Modules.push(...(props.additionalModel as any[]))
  } else {
    props.additionalModel && Modules.push(props.additionalModel)
  }

  // 翻译模块
  const TranslateModule = {
    translate: ['value', customTranslate(props.translations || translationsCN)]
  }
  Modules.push(TranslateModule)

  // 模拟流转模块
  if (props.simulation) {
    Modules.push(tokenSimulation)
  }

  // 根据需要的流程类型设置扩展元素构建模块
  // if (this.prefix === "bpmn") {
  //   Modules.push(bpmnModdleExtension);
  // }
  if (props.prefix === 'camunda') {
    Modules.push(camundaModdleExtension)
  }
  if (props.prefix === 'flowable') {
    Modules.push(flowableModdleExtension)
  }
  if (props.prefix === 'activiti') {
    Modules.push(activitiModdleExtension)
  }

  return Modules
})

const moddleExtensions = computed(() => {
  const Extensions: any = {}
  // 仅使用用户自定义模块
  if (props.onlyCustomizeModdle) {
    return props.moddleExtension || null
  }

  // 插入用户自定义模块
  if (props.moddleExtension) {
    for (let key in props.moddleExtension) {
      Extensions[key] = props.moddleExtension[key]
    }
  }

  // 根据需要的 "流程类型" 设置 对应的解析文件
  if (props.prefix === 'activiti') {
    Extensions.activiti = activitiModdleDescriptor
  }
  if (props.prefix === 'flowable') {
    Extensions.flowable = flowableModdleDescriptor
  }
  if (props.prefix === 'camunda') {
    Extensions.camunda = camundaModdleDescriptor
  }
  return Extensions
})

const onSave = async () => {
      return new Promise((resolve, reject) => {
        if (bpmnModeler == null) {
          reject();
        }
        bpmnModeler.saveXML({ format: true }).then(({ xml }) => {
          // 触发 save 事件
          emit('save', xml)
          resolve(xml);
        });
      })
}

const initBpmnModeler = () => {
  if (bpmnModeler) return
  let container = document.getElementById('bpmnCanvas')

  bpmnModeler = new BpmnModeler({
    container: container,
    keyboard: props.keyboard ? { bindTo: document } : null,
    additionalModules: additionalModules.value,
    moddleExtensions: moddleExtensions.value,
    ...props.options
  })
  console.log("initBpmnModeler bpmnModeler",bpmnModeler)
  emit('init-finished', bpmnModeler)
  initModelListeners()
}

const initModelListeners = () => {
  const EventBus = bpmnModeler.get('eventBus')
  // 注册需要的监听事件, 将. 替换为 - , 避免解析异常
  props.events.forEach((event: any) => {
    EventBus.on(event, function (eventObj) {
      let eventName = event.replace(/\./g, '-')
      let element = eventObj ? eventObj.element : null
      emit('element-click', element, eventObj)
      // emit(eventName, element, eventObj)
    })
  })
  // 监听图形改变返回xml
  EventBus.on('commandStack.changed', async (event) => {
    try {
      recoverable.value = bpmnModeler.get('commandStack').canRedo()
      revocable.value = bpmnModeler.get('commandStack').canUndo()
      let { xml } = await bpmnModeler.saveXML({ format: true })
      emit('commandStack-changed', event)
      emit('update:modelValue', xml)
      emit('change', xml)
    } catch (e: any) {
      console.error(`[Process Designer Warn]: ${e.message || e}`)
    }
  })
  // 监听视图缩放变化
  bpmnModeler.on('canvas.viewbox.changed', ({ viewbox }) => {
    emit('canvas-viewbox-changed', { viewbox })
    const { scale } = viewbox
    defaultZoom.value = Math.floor(scale * 100) / 100
  })
}
/* 创建新的流程图 */
const createNewDiagram = async (xml) => {
  // 将字符串转换成图显示出来
  let newId = props.processId || `Process_${new Date().getTime()}`
  let newName = props.processName || `业务流程_${new Date().getTime()}`
  let xmlString = xml || DefaultEmptyXML(newId, newName, props.prefix)
  try {
    let { warnings } = await bpmnModeler.importXML(xmlString)
    if (warnings && warnings.length) {
      warnings.forEach((warn) => console.warn(warn))
    }
  } catch (e: any) {
    console.error(`[Process Designer Warn]: ${e.message || e}`)
  }
}

// 下载流程图到本地
    /**
     * @param {string} type
     * @param {*} name
     */
const downloadProcess = async (type, name) => {
  try {
    // 按需要类型创建文件并下载
    if (type === 'xml' || type === 'bpmn') {
      const { err, xml } = await bpmnModeler.saveXML()
      // 读取异常时抛出异常
      if (err) {
        console.error(`[Process Designer Warn ]: ${err.message || err}`)
      }
      let { href, filename } = setEncoded(type.toUpperCase(), name, xml)
      downloadFunc(href, filename)
    } else {
      const { err, svg } = await bpmnModeler.saveSVG()
      // 读取异常时抛出异常
      if (err) {
        return console.error(err)
      }
      let { href, filename } = setEncoded('SVG', name, svg)
      downloadFunc(href, filename)
    }
  } catch (e: any) {
    console.error(`[Process Designer Warn ]: ${e.message || e}`)
  }
  // 文件下载方法
  function downloadFunc(href, filename) {
    if (href && filename) {
      let a = document.createElement('a')
      a.download = filename //指定下载的文件名
      a.href = href //  URL对象
      a.click() // 模拟点击
      URL.revokeObjectURL(a.href) // 释放URL 对象
    }
  }
}
// 根据所需类型进行转码并返回下载地址
const setEncoded = (type, filename = 'diagram', data) => {
  const encodedData = encodeURIComponent(data)
  return {
    filename: `${filename}.${type}`,
    href: `data:application/${type === "svg" ? "text/xml" : "bpmn20-xml"};charset=UTF-8,${encodedData}`,
    data: data
  }
}

// 加载本地文件
const importLocalFile = () => {
  const file = refFile.value.files[0]
  const reader = new FileReader()
  reader.readAsText(file)
  reader.onload = function () {
    let xmlStr = this.result
    createNewDiagram(xmlStr)
  }
}
/* ------------------------------------------------ refs methods ------------------------------------------------------ */
const downloadProcessAsXml = () => {
  downloadProcess('xml')
}
const downloadProcessAsBpmn = () => {
  downloadProcess('bpmn')
}
const downloadProcessAsSvg = () => {
  downloadProcess('svg')
}
const processSimulation = () => {
  simulationStatus.value = !simulationStatus.value
  props.simulation && bpmnModeler.get('toggleMode').toggleMode()
}
const processRedo = () => {
  bpmnModeler.get('commandStack').redo()
}
const processUndo = () => {
  bpmnModeler.get('commandStack').undo()
}
const processZoomIn = (zoomStep = 0.1) => {
  let newZoom = Math.floor(defaultZoom.value * 100 + zoomStep * 100) / 100
  if (newZoom > 4) {
    throw new Error('[Process Designer Warn ]: The zoom ratio cannot be greater than 4')
  }
  defaultZoom.value = newZoom
  bpmnModeler.get('canvas').zoom(defaultZoom.value)
}
const processZoomOut = (zoomStep = 0.1) => {
  let newZoom = Math.floor(defaultZoom.value * 100 - zoomStep * 100) / 100
  if (newZoom < 0.2) {
    throw new Error('[Process Designer Warn ]: The zoom ratio cannot be less than 0.2')
  }
  defaultZoom.value = newZoom
  bpmnModeler.get('canvas').zoom(defaultZoom.value)
}
const processZoomTo = (newZoom = 1) => {
      if (newZoom < 0.2) {
        throw new Error("[Process Designer Warn ]: The zoom ratio cannot be less than 0.2");
      }
      if (newZoom > 4) {
        throw new Error("[Process Designer Warn ]: The zoom ratio cannot be greater than 4");
      }
      defaultZoom.value = newZoom;
      bpmnModeler.get("canvas").zoom(newZoom);
}

const processReZoom = () => {
  defaultZoom.value = 1
  bpmnModeler.get('canvas').zoom('fit-viewport', 'auto')
}

const processRestart = () => {
  recoverable.value = false
  revocable.value = false
  createNewDiagram(null)
}
const elementsAlign = (align) => {
  const Align = bpmnModeler.get('alignElements')
  const Selection = bpmnModeler.get('selection')
  const SelectedElements = Selection.get()
  if (!SelectedElements || SelectedElements.length <= 1) {
    ElMessage.warning('请按住 Shift 键选择多个元素对齐')
    return
  }
  ElMessageBox.confirm('自动对齐可能造成图形变形，是否继续？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    Align.trigger(SelectedElements, align)
  })
}
/*-----------------------------    方法结束     ---------------------------------*/
const previewProcessXML = () => {
  bpmnModeler.saveXML({ format: true }).then(({ xml }) => {
    previewResult.value = xml
    previewType.value = 'xml'
    previewModelVisible.value = true
  })
}
const previewProcessJson = () => {
  bpmnModeler.saveXML({ format: true }).then(({ xml }) => {
    previewResult.value = convert.xml2json(xml, { spaces: 2 });
    previewType.value = "json";
    previewModelVisible.value = true;
  })
}

onMounted(() => {
  initBpmnModeler()
  createNewDiagram(props.modelValue)
})

onBeforeUnmount(() => {
  if (bpmnModeler) bpmnModeler.destroy()
  emit('destroy', bpmnModeler)
  bpmnModeler = null
})
</script>
