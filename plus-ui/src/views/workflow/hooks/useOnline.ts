import { NmRule } from '@/components/nbMade/NmCommon.js';
import NmInput from '@/components/nbMade/NmInput.vue';
import NmPassword from '@/components/nbMade/NmPassword.vue';
import NmSwitch from '@/components/nbMade/NmSwitch.vue';
import NmRadio from '@/components/nbMade/NmRadio.vue';
import NmCheckbox from '@/components/nbMade/NmCheckbox.vue';
import NmTextArea from '@/components/nbMade/NmTextArea.vue';
import NmDate from '@/components/nbMade/NmDate.vue';
import NmDateTime from '@/components/nbMade/NmDateTime.vue';
import NmTime from '@/components/nbMade/NmTime.vue';
import NmImage from '@/components/nbMade/NmImage.vue';
import NmFile from '@/components/nbMade/NmFile.vue';
import NmCatTree from '@/components/nbMade/NmCatTree.vue';
import NmLinkDown from '@/components/nbMade/NmLinkDown.vue';
import NmEditor from '@/components/nbMade/NmEditor.vue';
import NmSelDict from '@/components/nbMade/NmSelDict.vue';
import NmPopup from '@/components/nbMade/NmPopup.vue';

// 根据组件名称动态设置组件（导出为具名函数，方便直接使用）
export const getComponentType = (type: string) => {
  switch (type) {
    case 'input':
      return NmInput;
    case 'password':
      return NmPassword;
    case 'switch':
      return NmSwitch;
    case 'radio':
      return NmRadio;
    case 'textarea':
      return NmTextArea;
    case 'date':
      return NmDate;
    case 'datetime':
      return NmDateTime;
    case 'time':
      return NmTime;
    case 'image':
      return NmImage;
    case 'file':
      return NmFile;
    case 'checkbox':
      return NmCheckbox;
    case 'editor':
      return NmEditor;
    case 'select':
      return NmSelDict;
    case 'popup':
      return NmPopup;
    default:
      console.warn(`${type} is not supported`);
  }
};

export const useOnline = () => {
  return {
    getComponentType
  };
};
