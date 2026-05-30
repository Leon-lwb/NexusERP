<template>
  <div class="panel-tab__content">
    <el-form size="small" label-width="90px" @submit.native.prevent>
      <el-form-item label="表单" prop="formKey">
        <el-select v-model="formKey" v-if = "appType[0].id === 'OA'" placeholder="请选择表单" @change="updateElementFormKey" clearable>
          <el-option v-for="item in formOptions" :key="item.formId" :label="item.formName" :value="`key_${item.formId}`" />
        </el-select>
        <el-select v-model="formKey" v-if = "appType[0].id === 'ZDYYW'" placeholder="请选择自定义业务表单" @change="updateElementFormKey" clearable>
          <el-option v-for="item in formOptions" :key="item.id" :label="item.businessName" :value="`key_${item.id}`" />
        </el-select>
      </el-form-item>
      <el-form-item prop="localScope">
        <span slot="label">
          <el-tooltip content="若为节点表单，则表单信息仅在此节点可用，默认为全局表单，表单信息在整个流程实例中可用" placement="top-start">
            <i class="header-icon el-icon-info"></i>
          </el-tooltip>
          <span>节点表单</span>
        </span>
        <el-switch :disabled="type === 'StartEvent'" v-model="localScope" active-text="是" inactive-text="否" @change="updateElementFormScope()" />
      </el-form-item>
    </el-form>

    <el-dialog :visible.sync="fieldOptionModelVisible" :title="optionModelTitle" width="600px" append-to-body destroy-on-close>
      <el-form :model="fieldOptionForm" size="small" label-width="96px" @submit.native.prevent>
        <el-form-item label="编号/ID" v-if="fieldOptionType !== 'constraint'" key="option-id">
          <el-input v-model="fieldOptionForm.id" clearable />
        </el-form-item>
        <el-form-item label="名称" v-if="fieldOptionType !== 'property'" key="option-name">
          <el-input v-model="fieldOptionForm.name" clearable />
        </el-form-item>
        <el-form-item label="配置" v-if="fieldOptionType === 'constraint'" key="option-config">
          <el-input v-model="fieldOptionForm.config" clearable />
        </el-form-item>
        <el-form-item label="值" v-if="fieldOptionType === 'property'" key="option-value">
          <el-input v-model="fieldOptionForm.value" clearable />
        </el-form-item>
      </el-form>
      <template slot="footer">
        <el-button size="small" @click="fieldOptionModelVisible = false">取 消</el-button>
        <el-button size="small" type="primary" @click="saveFieldOption">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
  import { listNform } from "@/api/workflow/nform";
  import { listCustomForm } from "@/api/workflow/custom-form";

  defineOptions({ name: 'ElementForm' })
  const props = defineProps({
    id: String,
    type: String,
    appType: {
        type: Array,
        default: () => []
    }
  })

  const prefix = inject('prefix')
  //const width = inject('width')

  const formOptions = ref<any[]>([])
  const formKey = ref('')
  const localScope= ref(false)
  const businessKey = ref('')
  const optionModelTitle = ref('')
  const fieldList = ref<any[]>([])
  const formFieldForm = ref<any>({})
  const fieldType = ref({
    long: '长整型',
    string: '字符串',
    boolean: '布尔类',
    date: '日期类',
    enum: '枚举类',
    custom: '自定义类型'
  })

  const formFieldIndex = ref(-1) // 编辑中的字段， -1 为新增
  const formFieldOptionIndex = ref(-1) // 编辑中的字段配置项， -1 为新增
  const fieldModelVisible = ref(false)
  const fieldOptionModelVisible = ref(false)
  const fieldOptionForm = ref<any>({}) // 当前激活的字段配置项数据
  const fieldOptionType = ref('') // 当前激活的字段配置项弹窗 类型
  const fieldEnumList = ref<any[]>([]) // 枚举值列表
  const fieldConstraintsList = ref<any[]>([]) // 约束条件列表
  const fieldPropertiesList = ref<any[]>([]) // 绑定属性列表
  const bpmnELement = ref()
  const elExtensionElements = ref()
  const formData = ref()
  const otherExtensions = ref()

  /** 查询表单列表 */
  const getFormList = () => {
    console.log("getFormList appType",props.appType);
    const appType = toRaw(props.appType);
    console.log("getFormList appType",appType);
    if(appType && appType[0].id === 'OA' ) {
      listNform().then(response => {
        formOptions.value = response.rows;
        console.log("listNform formOptions=",formOptions);
      });
    }
    else if(appType && appType[0].id === 'ZDYYW') {
      listCustomForm().then(response => {
        formOptions.value = response.rows;
        console.log("listCustomForm formOptions=",formOptions);
      });
    }
  }

  const bpmnInstances = () => (window as any)?.bpmnInstances
  const resetFormList = () => {
    bpmnELement.value = bpmnInstances().bpmnElement
    formKey.value = bpmnELement.value.businessObject.formKey
    localScope.value = bpmnELement.value.businessObject.localScope;
    // 获取元素扩展属性 或者 创建扩展属性
    elExtensionElements.value =
      bpmnELement.value.businessObject.get('extensionElements') ||
      bpmnInstances().moddle.create('bpmn:ExtensionElements', { values: [] })
    // 获取元素表单配置 或者 创建新的表单配置
    // formData.value =
    //   elExtensionElements.value.values?.filter((ex) => ex.$type === `${prefix}:FormData`)?.[0] ||
    //   bpmnInstances().moddle.create(`${prefix}:FormData`, { fields: [] })

    // 业务标识 businessKey， 绑定在 formData 中
    // businessKey.value = formData.value.businessKey

    // 保留剩余扩展元素，便于后面更新该元素对应属性
    otherExtensions.value = elExtensionElements.value.values?.filter(
      (ex) => ex.$type !== `${prefix}:FormData`
    )

    // 复制原始值，填充表格
    //fieldList.value = JSON.parse(JSON.stringify(formData.value.fields || []))

    // 更新元素扩展属性，避免后续报错
    //updateElementExtensions()
  }
  const updateElementFormKey = () => {
    bpmnInstances().modeling.updateProperties(toRaw(bpmnELement.value), {
      formKey: formKey.value
    })
    if(props.type != 'StartEvent') {
      localScope.value = true;
      bpmnInstances().modeling.updateProperties(toRaw(bpmnELement.value), { localScope: localScope.value });
    }
  }

  const updateElementFormScope = () => {
        bpmnInstances().modeling.updateModdleProperties(toRaw(bpmnELement.value), { localScope: localScope.value });
  }

  const updateElementBusinessKey = () => {
    bpmnInstances().modeling.updateModdleProperties(toRaw(bpmnELement.value), formData.value, {
      businessKey: businessKey.value
    })
  }
  // 根据类型调整字段type
  const changeFieldTypeType = (type) => {
    // this.$set(this.formFieldForm, "type", type === "custom" ? "" : type);
    formFieldForm.value['type'] = type === 'custom' ? '' : type
  }

  // 打开字段详情侧边栏
  const openFieldForm = (field, index) => {
    formFieldIndex.value = index
    if (index !== -1) {
      const FieldObject = formData.value.fields[index]
      formFieldForm.value = JSON.parse(JSON.stringify(field))
      // 设置自定义类型
      // this.$set(this.formFieldForm, "typeType", !this.fieldType[field.type] ? "custom" : field.type);
      formFieldForm.value['typeType'] = !fieldType.value[field.type] ? 'custom' : field.type
      // 初始化枚举值列表
      field.type === 'enum' &&
        (fieldEnumList.value = JSON.parse(JSON.stringify(FieldObject?.values || [])))
      // 初始化约束条件列表
      fieldConstraintsList.value = JSON.parse(
        JSON.stringify(FieldObject?.validation?.constraints || [])
      )
      // 初始化自定义属性列表
      fieldPropertiesList.value = JSON.parse(JSON.stringify(FieldObject?.properties?.values || []))
    } else {
      formFieldForm.value = {}
      // 初始化枚举值列表
      fieldEnumList.value = []
      // 初始化约束条件列表
      fieldConstraintsList.value = []
      // 初始化自定义属性列表
      fieldPropertiesList.value = []
    }
    fieldModelVisible.value = true
  }
  // 打开字段 某个 配置项 弹窗
  const openFieldOptionForm = (option, index, type) => {
    fieldOptionModelVisible.value = true
    fieldOptionType.value = type
    formFieldOptionIndex.value = index
    if (type === 'property') {
      fieldOptionForm.value = option ? JSON.parse(JSON.stringify(option)) : {}
      return (optionModelTitle.value = '属性配置')
    }
    if (type === 'enum') {
      fieldOptionForm.value = option ? JSON.parse(JSON.stringify(option)) : {}
      return (optionModelTitle.value = '枚举值配置')
    }
    fieldOptionForm.value = option ? JSON.parse(JSON.stringify(option)) : {}
    return (optionModelTitle.value = '约束条件配置')
  }

  // 保存字段 某个 配置项
  const saveFieldOption = () => {
    if (formFieldOptionIndex.value === -1) {
      if (fieldOptionType.value === 'property') {
        fieldPropertiesList.value.push(fieldOptionForm.value)
      }
      if (fieldOptionType.value === 'constraint') {
        fieldConstraintsList.value.push(fieldOptionForm.value)
      }
      if (fieldOptionType.value === 'enum') {
        fieldEnumList.value.push(fieldOptionForm.value)
      }
    } else {
      fieldOptionType.value === 'property' &&
        fieldPropertiesList.value.splice(formFieldOptionIndex.value, 1, fieldOptionForm.value)
      fieldOptionType.value === 'constraint' &&
        fieldConstraintsList.value.splice(formFieldOptionIndex.value, 1, fieldOptionForm.value)
      fieldOptionType.value === 'enum' &&
        fieldEnumList.value.splice(formFieldOptionIndex.value, 1, fieldOptionForm.value)
    }
    fieldOptionModelVisible.value = false
    fieldOptionForm.value = {}
  }
  // 保存字段配置
  const saveField = () => {
    const { id, type, label, defaultValue, datePattern } = formFieldForm.value
    const Field = bpmnInstances().moddle.create(`${prefix}:FormField`, { id, type, label })
    defaultValue && (Field.defaultValue = defaultValue)
    datePattern && (Field.datePattern = datePattern)
    // 构建属性
    if (fieldPropertiesList.value && fieldPropertiesList.value.length) {
      const fieldPropertyList = fieldPropertiesList.value.map((fp) => {
        return bpmnInstances().moddle.create(`${prefix}:Property`, {
          id: fp.id,
          value: fp.value
        })
      })
      Field.properties = bpmnInstances().moddle.create(`${prefix}:Properties`, {
        values: fieldPropertyList
      })
    }
    // 构建校验规则
    if (fieldConstraintsList.value && fieldConstraintsList.value.length) {
      const fieldConstraintList = fieldConstraintsList.value.map((fc) => {
        return bpmnInstances().moddle.create(`${prefix}:Constraint`, {
          name: fc.name,
          config: fc.config
        })
      })
      Field.validation = bpmnInstances().moddle.create(`${prefix}:Validation`, {
        constraints: fieldConstraintList
      })
    }
    // 构建枚举值
    if (fieldEnumList.value && fieldEnumList.value.length) {
      Field.values = fieldEnumList.value.map((fe) => {
        return bpmnInstances().moddle.create(`${prefix}:Value`, { name: fe.name, id: fe.id })
      })
    }
    // 更新数组 与 表单配置实例
    if (formFieldIndex.value === -1) {
      fieldList.value.push(formFieldForm.value)
      formData.value.fields.push(Field)
    } else {
      fieldList.value.splice(formFieldIndex.value, 1, formFieldForm.value)
      formData.value.fields.splice(formFieldIndex.value, 1, Field)
    }
    updateElementExtensions()
    fieldModelVisible.value = false
  }

  // 移除某个 字段的 配置项
  const removeFieldOptionItem = (option, index, type) => {
    console.log(option, 'option')
    if (type === 'property') {
      fieldPropertiesList.value.splice(index, 1)
      return
    }
    if (type === 'enum') {
      fieldEnumList.value.splice(index, 1)
      return
    }
    fieldConstraintsList.value.splice(index, 1)
  }
  // 移除 字段
  const removeField = (field, index) => {
    fieldList.value.splice(index, 1)
    formData.value.fields.splice(index, 1)
    updateElementExtensions()
  }

  const updateElementExtensions = () => {
    // 更新回扩展元素
    const newElExtensionElements = bpmnInstances().moddle.create(`bpmn:ExtensionElements`, {
      values: otherExtensions.value?.concat(formData.value)
    })
    // 更新到元素上
    bpmnInstances().modeling.updateProperties(toRaw(bpmnELement.value), {
      extensionElements: newElExtensionElements
    })
  }

  watch(
    () => props.id,
    (val) => {
      val &&
        val.length &&
        nextTick(() => {
          resetFormList()
        })
    },
    { immediate: true }
  )

  onMounted(() => {
    /** 查询流程分类列表 */
    getFormList();
  })

</script>
