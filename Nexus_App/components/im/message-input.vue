<template>
  <view class="message-input">
    <view class="input-toolbar">
      <view class="toolbar-item" @click="handleChooseImage">
        <text class="iconfont icon-image">📷</text>
      </view>
      <view class="toolbar-item" @click="handleChooseFile">
        <text class="iconfont icon-file">📎</text>
      </view>
    </view>
    
    <view class="input-wrapper">
      <textarea 
        v-model="inputContent"
        class="input-area"
        :placeholder="placeholder"
        :maxlength="maxLength"
        auto-height
        :show-confirm-bar="false"
        adjust-position
        @confirm="handleSend"
        @focus="handleFocus"
        @blur="handleBlur"
      />
      
      <button 
        class="send-btn" 
        :disabled="!canSend"
        @click="handleSend"
      >
        发送
      </button>
    </view>
  </view>
</template>

<script>
import { ref, computed } from 'vue'
import { chooseAndUploadImage, chooseAndUploadFile } from '@/utils/im-upload'

export default {
  name: 'MessageInput',
  props: {
    placeholder: {
      type: String,
      default: '输入消息...'
    },
    maxLength: {
      type: Number,
      default: 500
    }
  },
  emits: ['send', 'focus', 'blur'],
  setup(props, { emit }) {
    const inputContent = ref('')
    const uploading = ref(false)

    const canSend = computed(() => {
      return inputContent.value.trim().length > 0 && !uploading.value
    })

    const handleSend = () => {
      if (!canSend.value) return
      
      const content = inputContent.value.trim()
      emit('send', {
        content,
        messageType: 'text'
      })
      
      inputContent.value = ''
    }

    const handleChooseImage = async () => {
      if (uploading.value) return
      
      try {
        uploading.value = true
        const result = await chooseAndUploadImage()
        
        emit('send', {
          content: result.url,
          messageType: 'image',
          localPath: result.localPath
        })
      } catch (error) {
        console.error('Choose and upload image failed:', error)
        uni.showToast({
          title: '图片上传失败',
          icon: 'none'
        })
      } finally {
        uploading.value = false
      }
    }

    const handleChooseFile = async () => {
      if (uploading.value) return
      
      try {
        uploading.value = true
        const result = await chooseAndUploadFile()
        
        emit('send', {
          content: result.url,
          messageType: 'file',
          fileName: result.fileName,
          fileSize: result.fileSize,
          localPath: result.localPath
        })
      } catch (error) {
        console.error('Choose and upload file failed:', error)
        uni.showToast({
          title: '文件上传失败',
          icon: 'none'
        })
      } finally {
        uploading.value = false
      }
    }

    const handleFocus = () => {
      emit('focus')
    }

    const handleBlur = () => {
      emit('blur')
    }

    return {
      inputContent,
      uploading,
      canSend,
      handleSend,
      handleChooseImage,
      handleChooseFile,
      handleFocus,
      handleBlur
    }
  }
}
</script>

<style scoped lang="scss">
.message-input {
  background-color: #fff;
  border-top: 1rpx solid #e8e8e8;
}

.input-toolbar {
  display: flex;
  padding: 16rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.toolbar-item {
  padding: 8rpx 16rpx;
  margin-right: 24rpx;
  
  &:active {
    opacity: 0.6;
  }
  
  .iconfont {
    font-size: 40rpx;
  }
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  padding: 16rpx 32rpx;
}

.input-area {
  flex: 1;
  min-height: 72rpx;
  max-height: 240rpx;
  padding: 16rpx;
  background-color: #f5f5f5;
  border-radius: 12rpx;
  font-size: 28rpx;
  line-height: 1.5;
}

.send-btn {
  margin-left: 24rpx;
  padding: 0 32rpx;
  height: 72rpx;
  line-height: 72rpx;
  background-color: #1890ff;
  color: #fff;
  border: none;
  border-radius: 12rpx;
  font-size: 28rpx;
  
  &:disabled {
    background-color: #d9d9d9;
    color: #999;
  }
  
  &:active:not(:disabled) {
    opacity: 0.8;
  }
}
</style>
