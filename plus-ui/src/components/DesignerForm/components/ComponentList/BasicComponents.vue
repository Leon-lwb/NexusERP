<template>
  <div class="component-group">
    <div class="group-title">基础组件</div>
    <div class="group-content">
      <div
        v-for="item in basicComponents"
        :key="item.type"
        class="component-item"
        draggable="true"
        @dragstart="handleDragStart($event, item)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onMounted } from 'vue'
import {
  Document,
  Edit,
  Select,
  Switch,
  Slider,
  Calendar,
  Timer,
  Picture,
  Upload,
  Operation,
  Plus,
  Minus,
  Link,
  Star,
  Brush
} from '@element-plus/icons-vue'
import { listDept } from '@/api/system/dept'
import { listUser } from '@/api/system/user'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['dragstart'])

const basicComponents = [
  {
    type: 'input',
    label: '单行文本',
    icon: 'Edit',
    props: {
      placeholder: '请输入',
      clearable: true
    }
  },
  {
    type: 'textarea',
    label: '多行文本',
    icon: 'Document',
    props: {
      placeholder: '请输入',
      rows: 3
    }
  },
  {
    type: 'select',
    label: '下拉选择',
    icon: 'Select',
    props: {
      placeholder: '请选择',
      clearable: true,
      options: [
        { label: '选项1', value: '1' },
        { label: '选项2', value: '2' }
      ]
    }
  },
  {
    type: 'radio',
    label: '单选框组',
    icon: 'Operation',
    props: {
      options: [
        { label: '选项1', value: '1' },
        { label: '选项2', value: '2' }
      ]
    }
  },
  {
    type: 'checkbox',
    label: '多选框组',
    icon: 'Operation',
    props: {
      options: [
        { label: '选项1', value: '1' },
        { label: '选项2', value: '2' }
      ]
    }
  },
  {
    type: 'switch',
    label: '开关',
    icon: 'Switch',
    props: {
      activeText: '是',
      inactiveText: '否'
    }
  },
  {
    type: 'slider',
    label: '滑块',
    icon: 'Slider',
    props: {
      min: 0,
      max: 100
    }
  },
  {
    type: 'date',
    label: '日期选择',
    icon: 'Calendar',
    props: {
      placeholder: '请选择日期',
      type: 'date'
    }
  },
  {
    type: 'time',
    label: '时间选择',
    icon: 'Timer',
    props: {
      placeholder: '请选择时间'
    }
  },
  {
    type: 'datetime',
    label: '日期时间',
    icon: 'Calendar',
    props: {
      placeholder: '请选择日期时间',
      type: 'datetime'
    }
  },
  {
    type: 'image-upload',
    label: '图片上传',
    icon: 'Picture',
    props: {
      action: '/api/upload',
      listType: 'picture-card',
      limit: 5,
      accept: 'image/*',
      multiple: true,
      showFileList: true,
      drag: true,
      name: 'file'
    }
  },
  {
    type: 'file-upload',
    label: '文件上传',
    icon: 'Upload',
    props: {
      action: '/api/upload',
      multiple: true,
      limit: 5,
      showFileList: true,
      drag: true,
      name: 'file'
    }
  },
  {
    type: 'button',
    label: '按钮',
    icon: 'Plus',
    props: {
      type: 'primary',
      text: '按钮',
      size: 'default'
    }
  },
  {
    type: 'divider',
    label: '分隔线',
    icon: 'Minus',
    props: {
      contentPosition: 'center',
      content: '分隔线'
    }
  },
  {
    type: 'rate',
    label: '评分',
    icon: 'Star',
    props: {
      max: 5,
      allowHalf: true,
      showText: true,
      texts: ['很差', '差', '一般', '好', '很好']
    }
  },
  {
    type: 'color-picker',
    label: '颜色选择器',
    icon: 'Brush',
    props: {
      showAlpha: true,
      colorFormat: 'hex',
      defaultColor: '#409EFF',
      predefine: [
        '#ff4500',
        '#ff8c00',
        '#ffd700',
        '#90ee90',
        '#00ced1',
        '#1e90ff',
        '#c71585',
        'rgba(255, 69, 0, 0.68)',
        'rgb(255, 120, 0)',
        'hsv(51, 100, 98)',
        'hsva(120, 40, 94, 0.5)',
        'hsl(181, 100%, 37%)',
        'hsla(209, 100%, 56%, 0.73)',
        '#c7158577'
      ]
    }
  }
]

const handleDragStart = (e: DragEvent, item: any) => {
  emit('dragstart', e, item)
}

const loadDeptOptions = async () => {
  try {
    const query = {
      pageNum: 1,
      pageSize: 1000
    }
    const response = await listDept(query)
    console.log('部门数据:', response)
    if (response && response.rows) {
      const deptComponent = basicComponents.find(c => c.type === 'dept-select')
      if (deptComponent) {
        deptComponent.props.options = response.rows.map((dept: any) => ({
          label: dept.deptName,
          value: dept.deptId
        }))
      }
    }
  } catch (error) {
    console.error('加载部门选项失败:', error)
    ElMessage.error('加载部门选项失败')
  }
}

const loadUserOptions = async () => {
  try {
    const query = {
      pageNum: 1,
      pageSize: 1000
    }
    const response = await listUser(query)
    console.log('用户数据:', response)
    if (response && response.rows) {
      const userComponent = basicComponents.find(c => c.type === 'user-select')
      if (userComponent) {
        userComponent.props.options = response.rows.map((user: any) => ({
          label: user.userName,
          value: user.userId
        }))
      }
    }
  } catch (error) {
    console.error('加载用户选项失败:', error)
    ElMessage.error('加载用户选项失败')
  }
}

onMounted(() => {
  console.log('组件挂载，开始加载数据')
  loadDeptOptions()
  loadUserOptions()
})
</script>

<style lang="scss" scoped>
.component-group {
  margin-bottom: 16px;

  .group-title {
    font-size: 14px;
    color: #606266;
    margin-bottom: 8px;
    padding-left: 8px;
  }

  .group-content {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;

    .component-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px;
      border: 1px solid #dcdfe6;
      border-radius: 4px;
      cursor: move;
      transition: all 0.3s;

      &:hover {
        border-color: #409eff;
        color: #409eff;
      }

      .el-icon {
        font-size: 16px;
      }

      span {
        font-size: 12px;
      }
    }
  }
}
</style> 