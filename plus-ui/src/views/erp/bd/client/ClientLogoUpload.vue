<template>
  <div class="client-logo-upload">
    <!-- 查看模式：仅展示静态图 -->
    <div v-if="disabled" class="logo-view">
      <el-image v-if="displayUrl" :src="displayUrl" :preview-src-list="[displayUrl]" preview-teleported fit="cover" class="logo-img" />
      <div v-else class="logo-empty">暂无LOGO</div>
    </div>
    <!-- 编辑模式：点击打开裁剪弹窗 -->
    <template v-else>
      <div class="logo-head" @click="editCropper()">
        <img v-if="displayUrl" :src="displayUrl" title="点击修改LOGO" class="logo-img" />
        <div v-else class="logo-img logo-placeholder">
          <el-icon><Plus /></el-icon>
          <span>上传LOGO</span>
        </div>
      </div>
      <el-button v-if="displayUrl" type="danger" icon="Delete" size="small" circle class="remove-btn" @click.stop="removeLogo" />
    </template>

    <!-- 裁剪弹窗 -->
    <el-dialog v-model="open" :title="title" width="800px" append-to-body @opened="modalOpened" @close="closeDialog">
      <el-row>
        <el-col :xs="24" :md="12" :style="{ height: '350px' }">
          <vue-cropper
            v-if="visible"
            ref="cropper"
            :img="options.img"
            :info="true"
            :auto-crop="options.autoCrop"
            :auto-crop-width="options.autoCropWidth"
            :auto-crop-height="options.autoCropHeight"
            :fixed-box="options.fixedBox"
            :output-type="options.outputType"
            @real-time="realTime"
          />
        </el-col>
        <el-col :xs="24" :md="12" :style="{ height: '350px' }">
          <div class="logo-upload-preview">
            <img :src="options.previews.url" :style="options.previews.img" />
          </div>
        </el-col>
      </el-row>
      <br />
      <el-row>
        <el-col :lg="2" :md="2">
          <el-upload action="#" :http-request="requestUpload" :show-file-list="false" :before-upload="beforeUpload">
            <el-button>
              选择
              <el-icon class="el-icon--right">
                <Upload />
              </el-icon>
            </el-button>
          </el-upload>
        </el-col>
        <el-col :lg="{ span: 1, offset: 2 }" :md="2">
          <el-button icon="Plus" @click="changeScale(1)"></el-button>
        </el-col>
        <el-col :lg="{ span: 1, offset: 1 }" :md="2">
          <el-button icon="Minus" @click="changeScale(-1)"></el-button>
        </el-col>
        <el-col :lg="{ span: 1, offset: 1 }" :md="2">
          <el-button icon="RefreshLeft" @click="rotateLeft()"></el-button>
        </el-col>
        <el-col :lg="{ span: 1, offset: 1 }" :md="2">
          <el-button icon="RefreshRight" @click="rotateRight()"></el-button>
        </el-col>
        <el-col :lg="{ span: 2, offset: 6 }" :md="2">
          <el-button type="primary" :loading="uploading" @click="uploadImg()">提 交</el-button>
        </el-col>
      </el-row>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import 'vue-cropper/dist/index.css';
import { VueCropper } from 'vue-cropper';
import { UploadRawFile } from 'element-plus';
import { uploadOss } from '@/api/system/oss';

interface Options {
  img: string | any; // 裁剪图片的地址
  autoCrop: boolean; // 是否默认生成截图框
  autoCropWidth: number; // 默认生成截图框宽度
  autoCropHeight: number; // 默认生成截图框高度
  fixedBox: boolean; // 固定截图框大小 不允许改变
  fileName: string;
  previews: any; // 预览数据
  outputType: string;
  visible: boolean;
}

const props = defineProps({
  // 绑定值为 OSS 对象ID（写入库的 client_logo）
  modelValue: {
    type: String,
    default: ''
  },
  // 展示用访问地址（由后端 BdClientVo.clientLogoUrl 翻译提供，私有桶为临时签名URL）
  url: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:modelValue']);
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const open = ref(false);
const visible = ref(false);
const uploading = ref(false);
const title = computed(() => (props.modelValue ? '修改客户LOGO' : '上传客户LOGO'));

// 展示用 URL：来自后端翻译或上传后即时回填
const displayUrl = ref(props.url);
watch(
  () => props.url,
  (val) => {
    // 弹窗打开期间不覆盖（避免覆盖用户正在裁剪的图）
    if (!open.value) {
      displayUrl.value = val;
    }
  }
);

const cropper = ref<any>({});
// 图片裁剪数据
const options = reactive<Options>({
  img: '',
  autoCrop: true,
  autoCropWidth: 200,
  autoCropHeight: 200,
  fixedBox: true,
  outputType: 'png',
  fileName: '',
  previews: {},
  visible: false
});

/** 打开裁剪弹窗 */
const editCropper = () => {
  options.img = displayUrl.value;
  open.value = true;
};
/** 弹窗打开动画结束时渲染 cropper */
const modalOpened = () => {
  visible.value = true;
};
/** 覆盖默认上传行为（仅本地读取，不真正上传） */
const requestUpload = (): any => {};
/** 向左旋转 */
const rotateLeft = () => {
  cropper.value.rotateLeft();
};
/** 向右旋转 */
const rotateRight = () => {
  cropper.value.rotateRight();
};
/** 图片缩放 */
const changeScale = (num: number) => {
  num = num || 1;
  cropper.value.changeScale(num);
};
/** 上传预处理：校验类型/大小，并读取为 base64 喂给 cropper */
const beforeUpload = (file: UploadRawFile): any => {
  if (file.type !== 'image/jpeg' && file.type !== 'image/png') {
    proxy?.$modal.msgError('上传图片只能是 JPG/PNG 格式!');
    return false;
  }
  if (file.size / 1024 / 1024 >= 5) {
    proxy?.$modal.msgError('上传图片大小不能超过 5MB!');
    return false;
  }
  const reader = new FileReader();
  reader.readAsDataURL(file);
  reader.onload = () => {
    options.img = reader.result;
    options.fileName = file.name;
  };
  return false;
};
/** 提交：裁剪为 blob 后上传到通用 OSS 接口，回填 ossId */
const uploadImg = () => {
  if (!options.img) {
    proxy?.$modal.msgWarning('请先选择图片');
    return;
  }
  uploading.value = true;
  cropper.value.getCropBlob(async (data: any) => {
    try {
      const file = new File([data], options.fileName || 'client-logo.png', { type: data.type || 'image/png' });
      const res = await uploadOss(file);
      // 写入库的是 ossId；展示用返回的 URL 即时回填
      emit('update:modelValue', String(res.data.ossId));
      displayUrl.value = res.data.url;
      open.value = false;
      visible.value = false;
      proxy?.$modal.msgSuccess('上传成功');
    } catch (e) {
      proxy?.$modal.msgError('上传失败');
    } finally {
      uploading.value = false;
    }
  });
};
/** 实时预览 */
const realTime = (data: any) => {
  options.previews = data;
};
/** 删除 LOGO */
const removeLogo = () => {
  emit('update:modelValue', '');
  displayUrl.value = '';
};
/** 关闭弹窗：恢复初始图 */
const closeDialog = () => {
  options.img = displayUrl.value;
  options.visible = false;
  visible.value = false;
};
</script>

<style lang="scss" scoped>
.client-logo-upload {
  position: relative;
  display: inline-block;
}

.logo-img {
  display: block;
  width: 120px;
  height: 120px;
  border-radius: 6px;
  object-fit: cover;
}

.logo-view .logo-img {
  border: 1px solid #dcdfe6;
}

.logo-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 120px;
  border-radius: 6px;
  border: 1px solid #dcdfe6;
  background-color: #f5f7fa;
  color: #909399;
  font-size: 14px;
}

.logo-head {
  position: relative;
  display: inline-block;
  cursor: pointer;
}

.logo-head:hover:after {
  content: '+';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  color: #eee;
  background: rgba(0, 0, 0, 0.5);
  font-size: 24px;
  font-style: normal;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  cursor: pointer;
  line-height: 110px;
  text-align: center;
  border-radius: 6px;
}

.logo-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px dashed #d9d9d9;
  background-color: #fafafa;
  color: #8c939d;
  font-size: 12px;
  gap: 6px;

  .el-icon {
    font-size: 24px;
  }
}

.logo-head:hover .logo-placeholder {
  border-color: #409eff;
  color: #409eff;
}

.remove-btn {
  position: absolute;
  top: -10px;
  left: 110px;
}

.logo-upload-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background-color: #f5f7fa;
  overflow: hidden;

  img {
    max-width: 100%;
    max-height: 100%;
  }
}
</style>
