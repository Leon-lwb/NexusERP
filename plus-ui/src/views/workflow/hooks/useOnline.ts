import FlowInput from '@/components/Flow/FlowInput.vue';
import FlowPassword from '@/components/Flow/FlowPassword.vue';
import FlowSwitch from '@/components/Flow/FlowSwitch.vue';
import FlowRadio from '@/components/Flow/FlowRadio.vue';
import FlowCheckbox from '@/components/Flow/FlowCheckbox.vue';
import FlowTextArea from '@/components/Flow/FlowTextArea.vue';
import FlowDate from '@/components/Flow/FlowDate.vue';
import FlowDateTime from '@/components/Flow/FlowDateTime.vue';
import FlowTime from '@/components/Flow/FlowTime.vue';
import FlowImage from '@/components/Flow/FlowImage.vue';
import FlowFile from '@/components/Flow/FlowFile.vue';
import FlowCatTree from '@/components/Flow/FlowCatTree.vue';
import FlowLinkDown from '@/components/Flow/FlowLinkDown.vue';
import FlowEditor from '@/components/Flow/FlowEditor.vue';
import FlowSelDict from '@/components/Flow/FlowSelDict.vue';
import FlowPopup from '@/components/Flow/FlowPopup.vue';

// 根据组件名称动态设置组件（导出为具名函数，方便直接使用）
export const getComponentType = (type: string) => {
  switch (type) {
    case 'input':
      return FlowInput;
    case 'password':
      return FlowPassword;
    case 'switch':
      return FlowSwitch;
    case 'radio':
      return FlowRadio;
    case 'textarea':
      return FlowTextArea;
    case 'date':
      return FlowDate;
    case 'datetime':
      return FlowDateTime;
    case 'time':
      return FlowTime;
    case 'image':
      return FlowImage;
    case 'file':
      return FlowFile;
    case 'checkbox':
      return FlowCheckbox;
    case 'editor':
      return FlowEditor;
    case 'select':
      return FlowSelDict;
    case 'popup':
      return FlowPopup;
    default:
      console.warn(`${type} is not supported`);
  }
};

export const useOnline = () => {
  return {
    getComponentType
  };
};
