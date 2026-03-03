import { defineComponent, h } from 'vue'
import { makeMap } from '@/utils/index'

const isAttr = makeMap(
  'accept,accept-charset,accesskey,action,align,alt,async,autocomplete,' +
  'autofocus,autosave,bgcolor,border,buffered,challenge,charset,' +
  'checked,cite,class,code,codebase,color,cols,colspan,content,http-equiv,' +
  'name,contenteditable,contextmenu,controls,coords,data,datetime,default,' +
  'defer,dir,dirname,disabled,download,draggable,dropzone,enctype,method,for,' +
  'form,formaction,headers,height,hidden,high,href,hreflang,http-equiv,' +
  'icon,id,ismap,itemprop,keytype,kind,label,lang,language,list,loop,low,' +
  'manifest,max,maxlength,media,method,GET,POST,min,multiple,email,file,' +
  'muted,name,novalidate,open,optimum,pattern,ping,placeholder,poster,' +
  'preload,radiogroup,readonly,rel,required,reversed,rows,rowspan,sandbox,' +
  'scope,scoped,seamless,selected,shape,size,type,text,password,sizes,span,' +
  'spellcheck,src,srcdoc,srclang,srcset,start,step,style,summary,tabindex,' +
  'target,title,type,usemap,value,width,wrap' + 'prefix-icon'
)

const isNotProps = makeMap(
  'layout,prepend,regList,tag,document,changeTag,defaultValue'
)

function useVModel(props: any, emit: any) {
  return {
    modelValue: props.defaultValue,
    'onUpdate:modelValue': (val: any) => emit('update:modelValue', val),
  }
}

const componentChild: Record<string, any> = {
  'el-button': {
    default(h: any, conf: any, key: string) {
      return conf[key]
    },
  },
  'el-select': {
    options(h: any, conf: any, key: string) {
      return conf.options.map((item: any) => h(resolveComponent('el-option'), {
        label: item.label,
        value: item.value,
      }))
    }
  },
  'el-radio-group': {
    options(h: any, conf: any, key: string) {
      return conf.optionType === 'button' ? conf.options.map((item: any) => h(resolveComponent('el-radio-button'), {
        label: item.value,
      }, () => item.label)) : conf.options.map((item: any) => h(resolveComponent('el-radio'), {
        label: item.value,
        border: conf.border,
      }, () => item.label))
    }
  },
  'el-checkbox-group': {
    options(h: any, conf: any, key: string) {
      return conf.optionType === 'button' ? conf.options.map((item: any) => h(resolveComponent('el-checkbox-button'), {
        label: item.value,
      }, () => item.label)) : conf.options.map((item: any) => h(resolveComponent('el-checkbox'), {
        label: item.value,
        border: conf.border,
      }, () => item.label))
    }
  },
  'el-upload': {
    'list-type': (h: any, conf: any, key: string) => {
      const option: any = {}
      if (conf['list-type'] === 'picture-card') {
        return h(resolveComponent('el-icon'), option, () => h(resolveComponent('Plus')))
      } else {
        option.type = "primary"
        option.icon = "Upload"
        return h(resolveComponent('el-button'), option, () => conf.buttonText)
      }
    },
  }
}

const componentSlot: Record<string, any> = {
  'el-upload': {
    'tip': (h: any, conf: any, key: string) => {
      if (conf.showTip) {
        return () => h('div', {
          class: "el-upload__tip"
        }, '只能上传不超过' + conf.fileSize + conf.sizeUnit + '的' + conf.accept + '文件')
      }
    },
  }
}

export default defineComponent({
  render() {
    const dataObject: any = {
      attrs: {},
      props: {},
      on: {},
      style: {}
    }
    const confClone = JSON.parse(JSON.stringify(this.conf))
    const children: any[] = []
    const slot: any = {}
    const childObjs = componentChild[confClone.tag]
    if (childObjs) {
      Object.keys(childObjs).forEach((key) => {
        const childFunc = childObjs[key]
        if (confClone[key]) {
          children.push(childFunc(h, confClone, key))
        }
      })
    }
    const slotObjs = componentSlot[confClone.tag]
    if (slotObjs) {
      Object.keys(slotObjs).forEach((key) => {
        const childFunc = slotObjs[key]
        if (confClone[key]) {
          slot[key] = childFunc(h, confClone, key)
        }
      })
    }
    Object.keys(confClone).forEach((key) => {
      const val = confClone[key]
      if (dataObject[key]) {
        dataObject[key] = val
      } else if (isAttr(key)) {
        dataObject.attrs[key] = val
      } else if (!isNotProps(key)) {
        dataObject.props[key] = val
      }
    })
    if(children.length > 0){
      slot.default = () => children
    }
    
    return h(resolveComponent(this.conf.tag),
      {
        modelValue: this.$attrs.modelValue,
        ...dataObject.props,
        ...dataObject.attrs,
        style: {
          ...dataObject.style
        },
      }
      , slot ?? null)
  },
  props: {
    conf: {
      type: Object,
      required: true,
    },
  }
})
